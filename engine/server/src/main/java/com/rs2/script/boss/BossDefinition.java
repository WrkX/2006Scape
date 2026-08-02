package com.rs2.script.boss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Value;

/**
 * Immutable Java-owned schema-v1 declarative boss descriptor.
 *
 * <p>The descriptor carries copied canonical values only: a stable string
 * id, the numeric npc id that remains the duplicate registry key for combat
 * ownership, definition-backed stats, a bounded arena and spawn point, an
 * explicit command or object entry route, ordered phases, named specials
 * with cooldowns, an optional named WP2 drop table with its private TTL,
 * and the cleanup policy. The {@code onSpawn}/{@code onTick}/{@code onDeath}
 * callbacks and the phase/special handlers are generation-owned guest
 * {@link Value}s; they are valid only while the registering context is
 * active.
 */
public final class BossDefinition {

	private final String id;
	private final int npcId;
	private final String name;
	private final int combatLevel;
	private final int maxHitpoints;
	private final int maxHit;
	private final int attack;
	private final int defence;
	private final BossArena arena;
	private final int spawnX;
	private final int spawnY;
	private final String command;
	private final String closeCommand;
	private final int objectEntryId;
	private final String objectEntryAction;
	private final boolean hasObjectEntry;
	private final int entryTeleportX;
	private final int entryTeleportY;
	private final boolean hasEntryTeleport;
	private final Value onSpawn;
	private final Value onTick;
	private final Value onDeath;
	private final List<BossPhaseDefinition> phases;
	private final Map<String, BossSpecialDefinition> specials;
	private final String dropTable;
	private final int privateTicks;
	private final boolean hasDropTable;
	private final BossCleanupPolicy cleanupPolicy;
	private final String source;
	private final int schemaVersion;

	public BossDefinition(String id, int npcId, String name, int combatLevel,
			int maxHitpoints, int maxHit, int attack, int defence,
			BossArena arena, int spawnX, int spawnY, String command,
			String closeCommand, int objectEntryId, String objectEntryAction,
			boolean hasObjectEntry, int entryTeleportX, int entryTeleportY,
			boolean hasEntryTeleport, Value onSpawn, Value onTick,
			Value onDeath, List<BossPhaseDefinition> phases,
			Map<String, BossSpecialDefinition> specials, String dropTable,
			int privateTicks, boolean hasDropTable,
			BossCleanupPolicy cleanupPolicy, String source,
			int schemaVersion) {
		this.id = id;
		this.npcId = npcId;
		this.name = name;
		this.combatLevel = combatLevel;
		this.maxHitpoints = maxHitpoints;
		this.maxHit = maxHit;
		this.attack = attack;
		this.defence = defence;
		this.arena = arena;
		this.spawnX = spawnX;
		this.spawnY = spawnY;
		this.command = command;
		this.closeCommand = closeCommand;
		this.objectEntryId = objectEntryId;
		this.objectEntryAction = objectEntryAction;
		this.hasObjectEntry = hasObjectEntry;
		this.entryTeleportX = entryTeleportX;
		this.entryTeleportY = entryTeleportY;
		this.hasEntryTeleport = hasEntryTeleport;
		this.onSpawn = onSpawn;
		this.onTick = onTick;
		this.onDeath = onDeath;
		this.phases = Collections.unmodifiableList(
				new ArrayList<BossPhaseDefinition>(phases));
		this.specials = Collections.unmodifiableMap(
				new LinkedHashMap<String, BossSpecialDefinition>(specials));
		this.dropTable = dropTable;
		this.privateTicks = privateTicks;
		this.hasDropTable = hasDropTable;
		this.cleanupPolicy = cleanupPolicy;
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	/** Stable string id referenced by raids and diagnostics. */
	public String id() {
		return id;
	}

	/** Numeric npc id; duplicate key of the BOSS registry for combat ownership. */
	public int npcId() {
		return npcId;
	}

	public String name() {
		return name;
	}

	public int combatLevel() {
		return combatLevel;
	}

	public int maxHitpoints() {
		return maxHitpoints;
	}

	public int maxHit() {
		return maxHit;
	}

	public int attack() {
		return attack;
	}

	public int defence() {
		return defence;
	}

	public BossArena arena() {
		return arena;
	}

	public int spawnX() {
		return spawnX;
	}

	public int spawnY() {
		return spawnY;
	}

	/** Exact WP1 host command route name, or {@code null} for object entry. */
	public String command() {
		return command;
	}

	/** Optional explicit-close host command route name, or {@code null}. */
	public String closeCommand() {
		return closeCommand;
	}

	public boolean hasObjectEntry() {
		return hasObjectEntry;
	}

	/** Object id of the object-entry route; valid only with {@link #hasObjectEntry()}. */
	public int objectEntryId() {
		return objectEntryId;
	}

	/** Ordinal object action of the object-entry route, or {@code null}. */
	public String objectEntryAction() {
		return objectEntryAction;
	}

	public boolean hasEntryTeleport() {
		return hasEntryTeleport;
	}

	public int entryTeleportX() {
		return entryTeleportX;
	}

	public int entryTeleportY() {
		return entryTeleportY;
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

	/** Immutable phases in strictly descending threshold order. */
	public List<BossPhaseDefinition> phases() {
		return phases;
	}

	/** Immutable named specials with cooldown ticks. */
	public Map<String, BossSpecialDefinition> specials() {
		return specials;
	}

	public boolean hasDropTable() {
		return hasDropTable;
	}

	/** Named WP2 drop table rolled on boss death; valid only with {@link #hasDropTable()}. */
	public String dropTable() {
		return dropTable;
	}

	public int privateTicks() {
		return privateTicks;
	}

	public BossCleanupPolicy cleanupPolicy() {
		return cleanupPolicy;
	}

	/** Bounded logical source module, or the legacy-unscoped marker. */
	public String source() {
		return source;
	}

	public int schemaVersion() {
		return schemaVersion;
	}

	@Override
	public String toString() {
		return "boss '" + id + "' (npcId: " + npcId + ", source: " + source
				+ ", schema v" + schemaVersion + ")";
	}

}
