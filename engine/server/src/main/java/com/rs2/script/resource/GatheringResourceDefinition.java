package com.rs2.script.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable Java-owned schema-v1 gathering resource descriptor.
 *
 * <p>The descriptor carries copied canonical values only: a stable string id,
 * one canonical object or NPC id and ordinal action, the required skill and
 * level, ordered tool alternatives (each an inventory-or-equipped item id with
 * an optional per-success consumption), the animation id and tick interval,
 * the deterministic success chance as an exact numerator/denominator pair,
 * bounded item rewards and one experience grant, and optional depletion state
 * for object targets. All item ids are copied numeric ids resolved at
 * candidate load; no guest value survives into the descriptor.
 */
public final class GatheringResourceDefinition {

	/** One tool alternative: {@code itemId} in inventory or equipped. */
	public static final class Tool {
		private final int itemId;
		private final boolean consume;

		public Tool(int itemId, boolean consume) {
			this.itemId = itemId;
			this.consume = consume;
		}

		public int itemId() {
			return itemId;
		}

		/** Whether one tool item is consumed per successful harvest. */
		public boolean consume() {
			return consume;
		}
	}

	/** One item reward granted per successful harvest. */
	public static final class ItemReward {
		private final int itemId;
		private final int amount;

		public ItemReward(int itemId, int amount) {
			this.itemId = itemId;
			this.amount = amount;
		}

		public int itemId() {
			return itemId;
		}

		public int amount() {
			return amount;
		}
	}

	private final String id;
	private final String name;
	private final int objectId;
	private final int npcId;
	private final String action;
	private final int skill;
	private final int level;
	private final List<Tool> tools;
	private final int animation;
	private final int intervalTicks;
	private final int successNumerator;
	private final int successDenominator;
	private final List<ItemReward> rewards;
	private final int experience;
	private final boolean depletes;
	private final int depletedObjectId;
	private final int respawnTicks;
	private final String source;
	private final int schemaVersion;

	public GatheringResourceDefinition(String id, String name, int objectId,
			int npcId, String action, int skill, int level, List<Tool> tools,
			int animation, int intervalTicks, int successNumerator,
			int successDenominator, List<ItemReward> rewards, int experience,
			boolean depletes, int depletedObjectId, int respawnTicks,
			String source, int schemaVersion) {
		this.id = id;
		this.name = name;
		this.objectId = objectId;
		this.npcId = npcId;
		this.action = action;
		this.skill = skill;
		this.level = level;
		this.tools = Collections.unmodifiableList(new ArrayList<Tool>(tools));
		this.animation = animation;
		this.intervalTicks = intervalTicks;
		this.successNumerator = successNumerator;
		this.successDenominator = successDenominator;
		this.rewards = Collections.unmodifiableList(
				new ArrayList<ItemReward>(rewards));
		this.experience = experience;
		this.depletes = depletes;
		this.depletedObjectId = depletedObjectId;
		this.respawnTicks = respawnTicks;
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	/** The canonical object id the resource is gathered from, or {@code 0}. */
	public int objectId() {
		return objectId;
	}

	/** The canonical NPC id the resource is gathered from, or {@code 0}. */
	public int npcId() {
		return npcId;
	}

	/** The ordinal action (one of first, second, third, fourth). */
	public String action() {
		return action;
	}

	/** The legacy engine skill index of the required skill. */
	public int skill() {
		return skill;
	}

	/** The required skill level. */
	public int level() {
		return level;
	}

	/** Ordered tool alternatives; at least one must be present. */
	public List<Tool> tools() {
		return tools;
	}

	/** The harvest animation id shown on every attempt tick. */
	public int animation() {
		return animation;
	}

	/** Attempt ticks between success checks. */
	public int intervalTicks() {
		return intervalTicks;
	}

	public int successNumerator() {
		return successNumerator;
	}

	public int successDenominator() {
		return successDenominator;
	}

	/** Item rewards granted together on a successful harvest. */
	public List<ItemReward> rewards() {
		return rewards;
	}

	/** Skill XP granted on a successful harvest. */
	public int experience() {
		return experience;
	}

	/** Whether a successful harvest depletes the object and closes the session. */
	public boolean depletes() {
		return depletes;
	}

	/** The object id shown after the resource depletes, or {@code -1}. */
	public int depletedObjectId() {
		return depletedObjectId;
	}

	/** Game cycles until the resource respawns after depletion. */
	public int respawnTicks() {
		return respawnTicks;
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
		String target = npcId > 0
				? "npc " + npcId
				: "object " + objectId;
		return "resource '" + id + "' (" + target + "/" + action
				+ ", skill " + skill + " " + level + ", tools: " + tools.size()
				+ ", source: " + source + ", schema v" + schemaVersion + ")";
	}

}
