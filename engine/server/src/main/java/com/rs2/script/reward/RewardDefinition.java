package com.rs2.script.reward;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable Java-owned named reward.
 *
 * <p>All member arrays are copied and bounded; no guest value survives into
 * the descriptor. The player-local transaction commits the complete reward
 * atomically or restores every component.
 */
public final class RewardDefinition {

	/** One copied item grant: {@code amount} of {@code itemId}. */
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

	/** One copied skill grant: {@code amount} XP for skill index. */
	public static final class ExperienceReward {
		private final int skillIndex;
		private final int amount;

		public ExperienceReward(int skillIndex, int amount) {
			this.skillIndex = skillIndex;
			this.amount = amount;
		}

		public int skillIndex() {
			return skillIndex;
		}

		public int amount() {
			return amount;
		}
	}

	/** One copied script-state mutation applied with the reward. */
	public static final class StateMutation {
		private final String namespace;
		private final String key;
		private final boolean booleanValue;
		private final double numberValue;
		private final String stringValue;
		private final int type; // 0 boolean, 1 number, 2 string

		public StateMutation(String namespace, String key, boolean value) {
			this(namespace, key, value, 0.0d, null, 0);
		}

		public StateMutation(String namespace, String key, double value) {
			this(namespace, key, false, value, null, 1);
		}

		public StateMutation(String namespace, String key, String value) {
			this(namespace, key, false, 0.0d, value, 2);
		}

		private StateMutation(String namespace, String key,
				boolean booleanValue, double numberValue, String stringValue,
				int type) {
			this.namespace = namespace;
			this.key = key;
			this.booleanValue = booleanValue;
			this.numberValue = numberValue;
			this.stringValue = stringValue;
			this.type = type;
		}

		public String namespace() {
			return namespace;
		}

		public String key() {
			return key;
		}

		public boolean isBoolean() {
			return type == 0;
		}

		public boolean isNumber() {
			return type == 1;
		}

		public boolean isString() {
			return type == 2;
		}

		public boolean booleanValue() {
			return booleanValue;
		}

		public double numberValue() {
			return numberValue;
		}

		public String stringValue() {
			return stringValue;
		}
	}

	private final String id;
	private final String source;
	private final int schemaVersion;
	private final List<ItemReward> items;
	private final List<ExperienceReward> experience;
	private final int questPoints;
	private final List<StateMutation> stateMutations;

	public RewardDefinition(String id, String source, int schemaVersion,
			List<ItemReward> items, List<ExperienceReward> experience,
			int questPoints, List<StateMutation> stateMutations) {
		this.id = id;
		this.source = source;
		this.schemaVersion = schemaVersion;
		this.items = Collections.unmodifiableList(
				new ArrayList<ItemReward>(items));
		this.experience = Collections.unmodifiableList(
				new ArrayList<ExperienceReward>(experience));
		this.questPoints = questPoints;
		this.stateMutations = Collections.unmodifiableList(
				new ArrayList<StateMutation>(stateMutations));
	}

	public String id() {
		return id;
	}

	/** Bounded logical source module, or the legacy-unscoped marker. */
	public String source() {
		return source;
	}

	public int schemaVersion() {
		return schemaVersion;
	}

	public List<ItemReward> items() {
		return items;
	}

	public List<ExperienceReward> experience() {
		return experience;
	}

	public int questPoints() {
		return questPoints;
	}

	public List<StateMutation> stateMutations() {
		return stateMutations;
	}

	@Override
	public String toString() {
		return "reward '" + id + "' (source: " + source + ", schema v"
				+ schemaVersion + ")";
	}

}
