package com.rs2.script.boss;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.HostAccess;

import com.rs2.script.ScriptArray;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.ScriptedPosition;
import com.rs2.script.world.ScriptEncounterHandle;
import com.rs2.script.world.ScriptNpcHandle;

/**
 * Narrow runtime context passed to every executable boss callback.
 *
 * <p>The context is composed only of accepted wrappers and handles: the
 * spawned boss NPC, the borrowed encounter handle, the owner and participant
 * view, and the live boss position/HP. There is deliberately no rich domain
 * {@code Player}, no registry access, and no raw engine object; combat and
 * pathfinding stay in the engine and are reached through the accepted
 * capability handles.
 *
 * <p>{@code boss}, {@code encounter}, and {@code owner} are public final
 * fields: GraalJS exposes a zero-argument Java method member as the method
 * object rather than its result, so guest code reads them as plain
 * properties (the same pattern as {@code ScriptContext.player}). Every
 * behavior is a method.
 */
public final class BossRuntimeContext {

	private final BossDefinition definition;
	private final BossController controller;
	private final List<ScriptedPlayer> participants;

	/** The spawned boss NPC handle. */
	@HostAccess.Export
	public final ScriptEncounterHandle encounter;

	/** The encounter owner. */
	@HostAccess.Export
	public final ScriptedPlayer owner;

	/**
	 * The spawned boss NPC handle. Non-final only because the spawn happens
	 * in {@code BossController.begin()} after context construction; guest
	 * code reads it as a plain property.
	 */
	@HostAccess.Export
	public ScriptNpcHandle boss;

	BossRuntimeContext(BossDefinition definition, BossController controller,
			ScriptEncounterHandle encounter, ScriptNpcHandle boss,
			ScriptedPlayer owner, List<ScriptedPlayer> participants) {
		this.definition = definition;
		this.controller = controller;
		this.encounter = encounter;
		this.boss = boss;
		this.owner = owner;
		this.participants = new ArrayList<ScriptedPlayer>(participants);
	}

	/** Attaches the spawned boss after a successful spawn. */
	void attachBoss(ScriptNpcHandle boss) {
		this.boss = boss;
	}

	@HostAccess.Export
	public String id() {
		return definition.id();
	}

	/** Immutable participant view supplied by the owning adapter. */
	@HostAccess.Export
	public ScriptArray participants() {
		return new ScriptArray(participants.toArray());
	}

	@HostAccess.Export
	public ScriptedPosition position() {
		return boss.position();
	}

	/** Current HP fraction {@code 0..1} of the spawned boss. */
	@HostAccess.Export
	public double hpPercent() {
		int hp = Math.max(0, boss.hp());
		return definition.maxHitpoints() <= 0 ? 0.0
				: (double) hp / (double) definition.maxHitpoints();
	}

	@HostAccess.Export
	public boolean alive() {
		return boss.isAlive();
	}

	/** Broadcasts a forced chat through the boss NPC. */
	@HostAccess.Export
	public boolean say(String text) {
		return boss.forcedChat(text);
	}

	/**
	 * Arms one declared named special. Once armed, the special fires first
	 * after its declared cooldown and then every cooldown game cycles while
	 * the boss is alive. Arming is idempotent; unknown names return
	 * {@code false}.
	 */
	@HostAccess.Export
	public boolean useSpecial(String name) {
		return controller.armSpecial(name);
	}

}
