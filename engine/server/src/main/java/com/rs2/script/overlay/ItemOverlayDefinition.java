package com.rs2.script.overlay;

/**
 * Immutable Java-owned schema-v1 item overlay descriptor.
 *
 * <p>Merges optional name, examine, stackability, equip slot, level
 * requirements, and combat bonuses over an existing cache item definition at
 * script activation.
 */
public final class ItemOverlayDefinition {

	private final String id;
	private final int itemId;
	private final String name;
	private final String examine;
	private final Boolean stackable;
	private final String equipSlot;
	private final int[] requirements;
	private final boolean[] requirementPresence;
	private final int[] bonuses;
	private final String source;
	private final int schemaVersion;

	public ItemOverlayDefinition(String id, int itemId, String name,
			String examine, Boolean stackable, String equipSlot,
			int[] requirements, boolean[] requirementPresence, int[] bonuses,
			String source, int schemaVersion) {
		this.id = id;
		this.itemId = itemId;
		this.name = name;
		this.examine = examine;
		this.stackable = stackable;
		this.equipSlot = equipSlot;
		this.requirements = requirements;
		this.requirementPresence = requirementPresence;
		this.bonuses = bonuses;
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public String id() {
		return id;
	}

	public int itemId() {
		return itemId;
	}

	public String name() {
		return name;
	}

	public String examine() {
		return examine;
	}

	public Boolean stackable() {
		return stackable;
	}

	public String equipSlot() {
		return equipSlot;
	}

	/**
	 * Skill requirements in attack, strength, defence, hitpoints, ranged,
	 * prayer, magic order, or {@code null} when unset. Absent skills carry
	 * {@code 0} and are merged over the existing cache definition.
	 */
	public int[] requirements() {
		return requirements;
	}

	/**
	 * Per-slot presence mask for {@link #requirements()}: {@code true} where
	 * the overlay declares that skill, or {@code null} when requirements are
	 * unset. Absent skills are not written to the cache item.
	 */
	public boolean[] requirementPresence() {
		return requirementPresence;
	}

	/**
	 * Twelve combat bonuses in legacy order, or {@code null} when unset.
	 */
	public int[] bonuses() {
		return bonuses;
	}

	public String source() {
		return source;
	}

	public int schemaVersion() {
		return schemaVersion;
	}

	@Override
	public String toString() {
		return "item-overlay '" + id + "' (itemId: " + itemId + ", source: "
				+ source + ", schema v" + schemaVersion + ")";
	}
}
