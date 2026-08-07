package com.rs2.script.processing;

/**
 * Immutable Java-owned schema-v1 processing skill descriptor.
 *
 * <p>Captures the cooking-proven loop: input item on a target object, tick
 * interval, skill/level gate, animation, optional sound, burn-style success
 * curve with optional gloves, success product + XP, and optional fail product.
 */
public final class ProcessingSkillDefinition {

	private final String id;
	private final String name;
	private final int skill;
	private final int level;
	private final int inputItemId;
	private final int objectId;
	private final int productItemId;
	private final int failProductItemId;
	private final int experience;
	private final int animation;
	private final int sound;
	private final int intervalTicks;
	private final int stopBurnLevel;
	private final int stopBurnLevelWithGloves;
	private final int glovesItemId;
	private final int burnBonus;
	private final String source;
	private final int schemaVersion;

	public ProcessingSkillDefinition(String id, String name, int skill,
			int level, int inputItemId, int objectId, int productItemId,
			int failProductItemId, int experience, int animation, int sound,
			int intervalTicks, int stopBurnLevel, int stopBurnLevelWithGloves,
			int glovesItemId, int burnBonus, String source, int schemaVersion) {
		this.id = id;
		this.name = name;
		this.skill = skill;
		this.level = level;
		this.inputItemId = inputItemId;
		this.objectId = objectId;
		this.productItemId = productItemId;
		this.failProductItemId = failProductItemId;
		this.experience = experience;
		this.animation = animation;
		this.sound = sound;
		this.intervalTicks = intervalTicks;
		this.stopBurnLevel = stopBurnLevel;
		this.stopBurnLevelWithGloves = stopBurnLevelWithGloves;
		this.glovesItemId = glovesItemId;
		this.burnBonus = burnBonus;
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public int skill() {
		return skill;
	}

	public int level() {
		return level;
	}

	public int inputItemId() {
		return inputItemId;
	}

	public int objectId() {
		return objectId;
	}

	public int productItemId() {
		return productItemId;
	}

	/** Fail/burn product id, or {@code -1} when absent. */
	public int failProductItemId() {
		return failProductItemId;
	}

	public int experience() {
		return experience;
	}

	public int animation() {
		return animation;
	}

	/** Success sound id, or {@code -1} when absent. */
	public int sound() {
		return sound;
	}

	public int intervalTicks() {
		return intervalTicks;
	}

	public int stopBurnLevel() {
		return stopBurnLevel;
	}

	/** Gloves stop-burn level, or {@code -1} when gloves are unused. */
	public int stopBurnLevelWithGloves() {
		return stopBurnLevelWithGloves;
	}

	/** Hands-slot gloves item id, or {@code -1} when unused. */
	public int glovesItemId() {
		return glovesItemId;
	}

	public int burnBonus() {
		return burnBonus;
	}

	public String source() {
		return source;
	}

	public int schemaVersion() {
		return schemaVersion;
	}

	@Override
	public String toString() {
		return "processing '" + id + "' (item " + inputItemId + " on object "
				+ objectId + ", skill " + skill + " " + level + ", source: "
				+ source + ", schema v" + schemaVersion + ")";
	}
}
