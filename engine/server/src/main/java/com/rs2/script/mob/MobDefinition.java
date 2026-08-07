package com.rs2.script.mob;

import org.graalvm.polyglot.Value;

/**
 * Immutable Java-owned schema-v1 world mob descriptor.
 *
 * <p>Captures stat-driven AI for a cache NPC id that shares the open world:
 * aggression radius, combat style, attack speed, max hit, optional attack
 * animation override, and optional generation-owned {@code onSpawn}/
 * {@code onTick}/{@code onDeath} callbacks. Arena bosses stay on
 * {@code defineBoss}.
 */
public final class MobDefinition {

	private final String id;
	private final int npcId;
	private final String name;
	private final int aggression;
	private final MobCombatStyle combatStyle;
	private final int attackSpeed;
	private final int maxHit;
	private final int animation;
	private final Value onSpawn;
	private final Value onTick;
	private final Value onDeath;
	private final String source;
	private final int schemaVersion;

	public MobDefinition(String id, int npcId, String name, int aggression,
			MobCombatStyle combatStyle, int attackSpeed, int maxHit,
			int animation, Value onSpawn, Value onTick, Value onDeath,
			String source, int schemaVersion) {
		this.id = id;
		this.npcId = npcId;
		this.name = name;
		this.aggression = aggression;
		this.combatStyle = combatStyle;
		this.attackSpeed = attackSpeed;
		this.maxHit = maxHit;
		this.animation = animation;
		this.onSpawn = onSpawn;
		this.onTick = onTick;
		this.onDeath = onDeath;
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public String id() {
		return id;
	}

	/** Cache NPC type; duplicate key of the MOB registry for combat ownership. */
	public int npcId() {
		return npcId;
	}

	public String name() {
		return name;
	}

	/**
	 * Aggression radius in tiles. {@code 0} means the mob never auto-aggros
	 * and only fights when a player already put it in combat.
	 */
	public int aggression() {
		return aggression;
	}

	public MobCombatStyle combatStyle() {
		return combatStyle;
	}

	/** Ticks between attacks once in combat. */
	public int attackSpeed() {
		return attackSpeed;
	}

	public int maxHit() {
		return maxHit;
	}

	/** Attack animation override, or {@code -1} to use the cache emote. */
	public int animation() {
		return animation;
	}

	public Value onSpawn() {
		return onSpawn;
	}

	public Value onTick() {
		return onTick;
	}

	public Value onDeath() {
		return onDeath;
	}

	public String source() {
		return source;
	}

	public int schemaVersion() {
		return schemaVersion;
	}

	@Override
	public String toString() {
		return "mob '" + id + "' (npcId: " + npcId + ", source: " + source
				+ ", schema v" + schemaVersion + ")";
	}
}
