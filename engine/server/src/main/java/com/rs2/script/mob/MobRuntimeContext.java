package com.rs2.script.mob;

import org.graalvm.polyglot.HostAccess;

import com.rs2.game.npcs.Npc;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.ScriptedPosition;

/**
 * Narrow runtime context for declarative world-mob callbacks.
 *
 * <p>Exposes identity, live vitals, position, optional killer (death only),
 * and the same face/animate/say primitives authors already use on encounter
 * NPC handles — without arena ownership.
 */
public final class MobRuntimeContext {

	private final MobDefinition definition;
	private final Npc npc;
	private final long allocationToken;
	private final long generation;
	private final ScriptedPlayer killer;
	private final ScriptedPosition deathPosition;

	MobRuntimeContext(MobDefinition definition, Npc npc, long generation,
			ScriptedPlayer killer, ScriptedPosition deathPosition) {
		this.definition = definition;
		this.npc = npc;
		this.allocationToken = npc == null ? 0L : npc.allocationToken();
		this.generation = generation;
		this.killer = killer;
		this.deathPosition = deathPosition;
	}

	@HostAccess.Export
	public String id() {
		return definition.id();
	}

	@HostAccess.Export
	public int npcId() {
		return definition.npcId();
	}

	@HostAccess.Export
	public ScriptedPosition position() {
		if (deathPosition != null) {
			return deathPosition;
		}
		Npc live = liveNpc();
		if (live == null) {
			return new ScriptedPosition(0, 0, 0);
		}
		return new ScriptedPosition(live.absX, live.absY, live.heightLevel);
	}

	@HostAccess.Export
	public int hp() {
		Npc live = liveNpc();
		return live == null ? 0 : Math.max(0, live.HP);
	}

	@HostAccess.Export
	public int maxHp() {
		Npc live = liveNpc();
		return live == null ? 0 : Math.max(0, live.MaxHP);
	}

	@HostAccess.Export
	public boolean alive() {
		Npc live = liveNpc();
		return live != null && !live.isDead && !live.applyDead && live.HP > 0;
	}

	/** Killer on death callbacks; {@code null} for spawn/tick. */
	@HostAccess.Export
	public ScriptedPlayer killer() {
		return killer;
	}

	@HostAccess.Export
	public boolean say(String text) {
		Npc live = liveNpc();
		if (live == null || text == null || text.length() < 1
				|| text.length() > 80) {
			return false;
		}
		live.forceChat(text);
		return true;
	}

	@HostAccess.Export
	public boolean face(double xValue, double yValue) {
		Integer x = integral(xValue, 0, 16383);
		Integer y = integral(yValue, 0, 16383);
		Npc live = liveNpc();
		if (x == null || y == null || live == null) {
			return false;
		}
		live.turnNpc(x.intValue(), y.intValue());
		return true;
	}

	@HostAccess.Export
	public boolean animate(double animationValue, double delayValue) {
		Integer animationId = integral(animationValue, -1, 65535);
		Integer delay = integral(delayValue, 0, 255);
		Npc live = liveNpc();
		if (animationId == null || delay == null || live == null) {
			return false;
		}
		live.animNumber = animationId.intValue();
		live.animDelay = delay.intValue();
		live.animUpdateRequired = true;
		live.updateRequired = true;
		return true;
	}

	long generation() {
		return generation;
	}

	private Npc liveNpc() {
		if (npc == null || allocationToken == 0L) {
			return null;
		}
		if (npc.allocationToken() != allocationToken) {
			return null;
		}
		return npc;
	}

	private static Integer integral(double value, int min, int max) {
		if (!Double.isFinite(value) || value != Math.rint(value)
				|| value < min || value > max) {
			return null;
		}
		return Integer.valueOf((int) value);
	}
}
