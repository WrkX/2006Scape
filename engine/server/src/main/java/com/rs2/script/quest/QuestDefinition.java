package com.rs2.script.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable Java-owned quest descriptor. It intentionally contains no guest
 * language values and remains safe after a Graal context is closed.
 */
public final class QuestDefinition {

	public static final class Stage {
		private final int stage;
		private final String objective;

		public Stage(int stage, String objective) {
			this.stage = stage;
			this.objective = objective;
		}

		public int getStage() {
			return stage;
		}

		public String getObjective() {
			return objective;
		}
	}

	public static final class SkillRequirement {
		private final QuestSkill skill;
		private final int level;

		public SkillRequirement(QuestSkill skill, int level) {
			this.skill = skill;
			this.level = level;
		}

		public QuestSkill getSkill() {
			return skill;
		}

		public int getLevel() {
			return level;
		}
	}

	public static final class ItemAmount {
		private final int itemId;
		private final int amount;

		public ItemAmount(int itemId, int amount) {
			this.itemId = itemId;
			this.amount = amount;
		}

		public int getItemId() {
			return itemId;
		}

		public int getAmount() {
			return amount;
		}
	}

	public static final class ExperienceReward {
		private final QuestSkill skill;
		private final int amount;

		public ExperienceReward(QuestSkill skill, int amount) {
			this.skill = skill;
			this.amount = amount;
		}

		public QuestSkill getSkill() {
			return skill;
		}

		public int getAmount() {
			return amount;
		}
	}

	public static final class Requirements {
		private final int questPoints;
		private final List<String> completedQuests;
		private final List<SkillRequirement> skills;
		private final List<ItemAmount> items;

		public Requirements(int questPoints, List<String> completedQuests,
				List<SkillRequirement> skills, List<ItemAmount> items) {
			this.questPoints = questPoints;
			this.completedQuests = immutable(completedQuests);
			this.skills = immutable(skills);
			this.items = immutable(items);
		}

		public int getQuestPoints() {
			return questPoints;
		}

		public List<String> getCompletedQuests() {
			return completedQuests;
		}

		public List<SkillRequirement> getSkills() {
			return skills;
		}

		public List<ItemAmount> getItems() {
			return items;
		}
	}

	public static final class Rewards {
		private final int questPoints;
		private final List<ItemAmount> items;
		private final List<ExperienceReward> experience;

		public Rewards(int questPoints, List<ItemAmount> items,
				List<ExperienceReward> experience) {
			this.questPoints = questPoints;
			this.items = immutable(items);
			this.experience = immutable(experience);
		}

		public int getQuestPoints() {
			return questPoints;
		}

		public List<ItemAmount> getItems() {
			return items;
		}

		public List<ExperienceReward> getExperience() {
			return experience;
		}
	}

	private final String id;
	private final String name;
	private final String summary;
	private final List<Stage> stages;
	private final Requirements requirements;
	private final Rewards rewards;

	public QuestDefinition(String id, String name, String summary,
			List<Stage> stages, Requirements requirements, Rewards rewards) {
		this.id = id;
		this.name = name;
		this.summary = summary;
		this.stages = immutable(stages);
		this.requirements = requirements;
		this.rewards = rewards;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getSummary() {
		return summary;
	}

	public List<Stage> getStages() {
		return stages;
	}

	public Requirements getRequirements() {
		return requirements;
	}

	public Rewards getRewards() {
		return rewards;
	}

	public int getFinalStage() {
		return stages.size() - 1;
	}

	private static <T> List<T> immutable(List<T> source) {
		return Collections.unmodifiableList(new ArrayList<>(source));
	}
}
