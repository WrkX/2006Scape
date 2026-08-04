package com.rs2.script.boss;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.graalvm.polyglot.Value;

import com.rs2.script.ScriptArray;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.ScriptedPosition;
import com.rs2.script.ScriptExecutor;
import com.rs2.script.scheduler.ScriptTaskHandle;
import com.rs2.script.world.ScriptEncounterHandle;
import com.rs2.script.world.ScriptNpcHandle;
import com.rs2.script.world.ScriptNpcService;
import com.rs2.script.world.ScriptDropEntry;
import com.rs2.util.LoggerUtils;

/**
 * Encounter-agnostic declarative boss runtime.
 *
 * <p>The controller borrows a supplied encounter handle, arena slice, and
 * participant view; it never calls {@code beginEncounter}, never adds or
 * removes encounter membership, and never closes the supplied handle. It
 * owns only its spawned boss NPC, the boss death listener, the 1-tick poll
 * task, and the phase/special state. It reports {@link Status#RUNNING},
 * {@link Status#DEFEATED}, or {@link Status#FAILED} to its caller, which
 * owns the terminal close policy (standalone adapter closes; a raid applies
 * its own wipe policy without a nested close).
 *
 * <p>Every guest callback runs under the active generation lease and is
 * exception-contained; a throwing callback fails the controller. Phases run
 * exactly once in strictly descending threshold order, armed specials fire
 * first after their cooldown and then every cooldown game cycles, and the
 * optional {@code onTick} runs after phases and specials on every poll while
 * the boss is alive. Named drops are rolled through the WP2 drop transaction
 * on boss death at the death position for the exact killer (or the owner);
 * a failed roll fails the controller just like a throwing callback, so a
 * kill that does not complete its declared reward contract is never
 * reported as a reward-less completion.
 */
public final class BossController {

	/** Terminal result reported exactly once to the owning adapter. */
	public enum Status {
		RUNNING,
		DEFEATED,
		FAILED
	}

	/** Adapter callback invoked exactly once when the controller terminates. */
	public interface TerminalListener {
		void onTerminal(BossController controller, Status status,
				ScriptedPosition deathPosition, ScriptedPlayer killer);
	}

	private static final Logger logger = LoggerUtils.getLogger(
			BossController.class);

	private final BossDefinition definition;
	private final ScriptEncounterHandle handle;
	private final BossArena arena;
	private final ScriptedPlayer owner;
	private final List<ScriptedPlayer> participants;
	private final TerminalListener terminalListener;
	private final BossRuntimeContext context;
	private final Map<String, SpecialState> specials =
			new LinkedHashMap<String, SpecialState>();

	private Status status = Status.RUNNING;
	private ScriptNpcHandle boss;
	private ScriptTaskHandle pollTask;
	private int phaseIndex;
	private long pollCount;
	private boolean terminalNotified;

	private BossController(BossDefinition definition,
			ScriptEncounterHandle handle, BossArena arena,
			ScriptedPlayer owner, List<ScriptedPlayer> participants,
			TerminalListener terminalListener) {
		this.definition = definition;
		this.handle = handle;
		this.arena = arena;
		this.owner = owner;
		this.participants = new ArrayList<ScriptedPlayer>(participants);
		this.terminalListener = terminalListener;
		this.context = new BossRuntimeContext(definition, this, handle, null,
				owner, participants);
		for (String name : definition.specials().keySet()) {
			specials.put(name, new SpecialState());
		}
	}

	/**
	 * Starts the controller on a borrowed encounter. The supplied handle is
	 * never begun, joined, or closed by this controller.
	 *
	 * @param participants the caller-owned participant view; the first entry
	 *            is the encounter owner
	 * @return the started controller; check {@link #status()} for a
	 *         synchronous spawn/onSpawn failure
	 */
	public static BossController start(BossDefinition definition,
			ScriptEncounterHandle handle, BossArena arena,
			ScriptedPlayer owner, List<ScriptedPlayer> participants,
			TerminalListener terminalListener) {
		BossController controller = new BossController(definition, handle,
				arena, owner, participants, terminalListener);
		controller.begin();
		return controller;
	}

	public Status status() {
		return status;
	}

	public BossRuntimeContext context() {
		return context;
	}

	/**
	 * Arms a declared named special. Once armed, the special fires first
	 * after its cooldown and then every cooldown game cycles. Arming is
	 * idempotent; unknown names and terminal controllers return
	 * {@code false}.
	 */
	boolean armSpecial(String name) {
		if (terminalNotified) {
			return false;
		}
		BossSpecialDefinition special = definition.specials().get(name);
		if (special == null) {
			return false;
		}
		SpecialState state = specials.get(name);
		if (state.armed) {
			return true;
		}
		state.armed = true;
		state.nextFirePoll = pollCount + special.cooldownTicks();
		return true;
	}

	private void begin() {
		if (!handle.isOpen()) {
			fail("encounter is not open");
			return;
		}
		boss = handle.spawnNpc(definition.npcId(), definition.spawnX(),
				definition.spawnY(), arena.plane(),
				definition.maxHitpoints(), definition.maxHit(),
				definition.attack(), definition.defence());
		if (boss == null) {
			fail("boss NPC spawn failed");
			return;
		}
		context.attachBoss(boss);
		if (!handle.onNpcDeath(boss, deathListener)) {
			fail("boss death listener registration failed");
			return;
		}
		ScriptTaskHandle pollTask = handle.everyJava(1.0,
				new Runnable() {
					@Override
					public void run() {
						poll();
					}
				},
				new Runnable() {
					@Override
					public void run() {
						// Engine-owned safety net: encounter close cancels
						// the poll; the terminal listener already owns the
						// close decision.
					}
				});
		if (pollTask == null) {
			fail("boss poll task registration failed");
			return;
		}
		this.pollTask = pollTask;
		execute(definition.onSpawn(), "onSpawn");
	}

	private void poll() {
		if (terminalNotified || boss == null || !boss.isAlive()) {
			return;
		}
		pollCount++;
		if (!triggerPhases()) {
			return;
		}
		if (!fireSpecials()) {
			return;
		}
		execute(definition.onTick(), "onTick");
	}

	private boolean triggerPhases() {
		while (phaseIndex < definition.phases().size()) {
			BossPhaseDefinition phase = definition.phases().get(phaseIndex);
			if (boss.hp() > thresholdHp(phase.hpPercentThreshold())) {
				break;
			}
			phaseIndex++;
			if (!execute(phase.onEnter(),
					"phase '" + phase.name() + "'")) {
				return false;
			}
		}
		return true;
	}

	private boolean fireSpecials() {
		for (BossSpecialDefinition special : definition.specials().values()) {
			SpecialState state = specials.get(special.name());
			if (!state.armed || pollCount < state.nextFirePoll) {
				continue;
			}
			state.nextFirePoll = pollCount + special.cooldownTicks();
			if (!execute(special.handler(),
					"special '" + special.name() + "'")) {
				return false;
			}
		}
		return true;
	}

	private int thresholdHp(int hpPercentThreshold) {
		return (int) ((long) definition.maxHitpoints()
				* (long) hpPercentThreshold / 100L);
	}

	private final ScriptNpcService.EncounterDeathListener deathListener =
			new ScriptNpcService.EncounterDeathListener() {
				@Override
				public void onDeath(ScriptNpcHandle deadBoss,
						ScriptedPlayer killer, ScriptedPosition position,
						ScriptEncounterHandle encounter) {
					if (terminalNotified) {
						return;
					}
					if (!execute(definition.onDeath(), "onDeath")) {
						return;
					}
					// A failed named roll fails the controller: the kill did
					// not complete its declared reward contract, so the
					// caller applies its failure policy (standalone closes;
					// a raid wipes) instead of reporting a reward-less
					// completion.
					if (!rollDrops(encounter, killer, position)) {
						return;
					}
					notifyTerminal(Status.DEFEATED, position, killer);
				}
			};

	private boolean rollDrops(ScriptEncounterHandle encounter,
			ScriptedPlayer killer, ScriptedPosition position) {
		if (!definition.hasDropTable()) {
			return true;
		}
		com.rs2.script.drop.DropTableDefinition table =
				com.rs2.script.drop.DropTableRegistry.get(
						definition.dropTable());
		if (table == null) {
			fail("named drop table '" + definition.dropTable()
					+ "' is not active");
			return false;
		}
		ScriptedPlayer recipient = killer != null ? killer : owner;
		ScriptArray results = encounter.rollDrops(recipient, position.x,
				position.y, position.plane, definition.privateTicks(),
				table.entries());
		if (results.length() == 0) {
			fail("drop roll failed for table '"
					+ definition.dropTable() + "'");
			return false;
		}
		return true;
	}

	private boolean execute(Value callback, String label) {
		if (callback == null) {
			return true;
		}
		boolean completed = ScriptExecutor.executeChecked(callback, "boss",
				definition.id(), label, context);
		if (!completed) {
			fail("callback threw: " + label);
			return false;
		}
		return true;
	}

	private void fail(String reason) {
		if (terminalNotified) {
			return;
		}
		logger.log(Level.WARNING, "Boss '" + definition.id()
				+ "' failed: " + reason);
		status = Status.FAILED;
		notifyTerminal(Status.FAILED,
				boss == null ? null : boss.position(), null);
	}

	private void notifyTerminal(Status terminalStatus,
			ScriptedPosition deathPosition, ScriptedPlayer killer) {
		if (terminalNotified) {
			return;
		}
		terminalNotified = true;
		status = terminalStatus;
		// The poll task idles once the boss is dead; cancel it at the
		// terminal so no stale poll can run after the result is known.
		if (pollTask != null) {
			try {
				pollTask.cancel();
			} catch (RuntimeException cancelFailure) {
				logger.log(Level.WARNING, "Boss '" + definition.id()
						+ "' poll cancellation failed; encounter close "
						+ "remains the safety net", cancelFailure);
			}
		}
		try {
			terminalListener.onTerminal(this, terminalStatus, deathPosition,
					killer);
		} catch (RuntimeException listenerFailure) {
			logger.log(Level.SEVERE, "Boss '" + definition.id()
					+ "' terminal listener threw", listenerFailure);
		}
	}

	private static final class SpecialState {
		private boolean armed;
		private long nextFirePoll;
	}

}
