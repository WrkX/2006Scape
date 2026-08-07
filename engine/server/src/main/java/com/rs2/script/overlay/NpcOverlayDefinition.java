package com.rs2.script.overlay;

/**
 * Immutable Java-owned schema-v1 NPC overlay descriptor.
 *
 * <p>Merges optional name, combat level, and hitpoints over an existing
 * cache NPC definition at script activation.
 */
public final class NpcOverlayDefinition {

	private final String id;
	private final int npcId;
	private final String name;
	private final Integer combatLevel;
	private final Integer hitpoints;
	private final String source;
	private final int schemaVersion;

	public NpcOverlayDefinition(String id, int npcId, String name,
			Integer combatLevel, Integer hitpoints, String source,
			int schemaVersion) {
		this.id = id;
		this.npcId = npcId;
		this.name = name;
		this.combatLevel = combatLevel;
		this.hitpoints = hitpoints;
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public String id() {
		return id;
	}

	public int npcId() {
		return npcId;
	}

	public String name() {
		return name;
	}

	public Integer combatLevel() {
		return combatLevel;
	}

	public Integer hitpoints() {
		return hitpoints;
	}

	public String source() {
		return source;
	}

	public int schemaVersion() {
		return schemaVersion;
	}

	@Override
	public String toString() {
		return "npc-overlay '" + id + "' (npcId: " + npcId + ", source: "
				+ source + ", schema v" + schemaVersion + ")";
	}
}
