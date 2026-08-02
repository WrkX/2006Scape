package com.rs2.script.quest;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apollo.cache.def.ItemDefinition;

import com.rs2.game.content.quests.QuestAssistant;
import com.rs2.game.items.ItemConstants;
import com.rs2.game.items.Weight;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerAssistant;
import com.rs2.script.quest.QuestDefinition.ExperienceReward;
import com.rs2.script.quest.QuestDefinition.ItemAmount;
import com.rs2.script.state.ScriptStateSnapshot;

/**
 * Simulates then atomically applies quest completion rewards.
 */
final class QuestRewardTransaction {

	private static final Logger LOGGER =
			Logger.getLogger(QuestRewardTransaction.class.getName());

	interface PostMutationHook {
		void run(Player player);
	}

	interface Presentation {
		void refreshInventory(Player player);
		void refreshWeight(Player player);
		void refreshSkill(Player player, int skill);
		void refreshQuestStages(Player player);
	}

	private static final int MAX_XP = 200000000;
	private static final int MAX_QUEST_POINTS = 10000;
	private static final PostMutationHook NOOP = player -> { };
	private static final Presentation LIVE_PRESENTATION = new Presentation() {
		@Override
		public void refreshInventory(Player player) {
			player.getItemAssistant().resetItems(3214);
		}

		@Override
		public void refreshWeight(Player player) {
			player.getPacketSender().writeWeight((int) player.weight);
		}

		@Override
		public void refreshSkill(Player player, int skill) {
			player.getPlayerAssistant().refreshSkill(skill);
		}

		@Override
		public void refreshQuestStages(Player player) {
			QuestAssistant.sendStages(player);
		}
	};

	private final PostMutationHook hook;
	private final Presentation presentation;

	QuestRewardTransaction() {
		this(NOOP, LIVE_PRESENTATION);
	}

	QuestRewardTransaction(PostMutationHook hook) {
		this(hook, LIVE_PRESENTATION);
	}

	QuestRewardTransaction(PostMutationHook hook, Presentation presentation) {
		this.hook = hook;
		this.presentation = presentation;
	}

	QuestResult complete(Player player, QuestDefinition definition) {
		int[] oldItems = player.playerItems.clone();
		int[] oldAmounts = player.playerItemsN.clone();
		double oldWeight = player.weight;
		int[] oldXp = player.playerXP.clone();
		int[] oldLevels = player.playerLevel.clone();
		int oldPoints = player.questPoints;
		ScriptStateSnapshot oldState = player.getScriptState().snapshot();

		int[] nextItems = oldItems.clone();
		int[] nextAmounts = oldAmounts.clone();
		int[] nextXp = oldXp.clone();
		int[] nextLevels = oldLevels.clone();
		int nextPoints;
		try {
			long points = (long) oldPoints
					+ definition.getRewards().getQuestPoints();
			if (points < 0 || points > MAX_QUEST_POINTS) {
				return QuestResult.unchanged(false,
						QuestResultCode.QUEST_POINTS_OVERFLOW);
			}
			nextPoints = (int) points;
			for (ItemAmount item : definition.getRewards().getItems()) {
				if (!addToCandidate(nextItems, nextAmounts, item)) {
					return QuestResult.unchanged(false,
							QuestResultCode.INVENTORY_FULL);
				}
			}
			long[] xpAwards = new long[nextXp.length];
			for (ExperienceReward reward
					: definition.getRewards().getExperience()) {
				int skill = reward.getSkill().getIndex();
				xpAwards[skill] += reward.getAmount();
				if (xpAwards[skill] > MAX_XP
						|| (long) oldXp[skill] + xpAwards[skill] > MAX_XP) {
					return QuestResult.unchanged(false, QuestResultCode.XP_CAP);
				}
			}
			for (int skill = 0; skill < xpAwards.length; skill++) {
				if (xpAwards[skill] == 0) {
					continue;
				}
				int oldBase = PlayerAssistant.getLevelForXP(oldXp[skill]);
				int delta = oldLevels[skill] - oldBase;
				nextXp[skill] = (int) ((long) oldXp[skill] + xpAwards[skill]);
				int newBase = PlayerAssistant.getLevelForXP(nextXp[skill]);
				nextLevels[skill] = Math.max(0, Math.min(255, newBase + delta));
			}

			System.arraycopy(nextItems, 0, player.playerItems, 0, nextItems.length);
			System.arraycopy(nextAmounts, 0, player.playerItemsN, 0,
					nextAmounts.length);
			System.arraycopy(nextXp, 0, player.playerXP, 0, nextXp.length);
			System.arraycopy(nextLevels, 0, player.playerLevel, 0,
					nextLevels.length);
			player.questPoints = nextPoints;
			QuestStateAccess.setStage(player, definition.getId(),
					definition.getFinalStage());
			QuestStateAccess.setState(player, definition.getId(),
					QuestState.COMPLETED);
			player.weight = Weight.calculateWeight(player.playerItems,
					player.playerEquipment);
			hook.run(player);
			verify(player, definition, nextItems, nextAmounts, nextXp,
					nextLevels, nextPoints);
		} catch (RuntimeException failure) {
			restore(player, oldItems, oldAmounts, oldWeight, oldXp, oldLevels,
					oldPoints, oldState);
			return QuestResult.unchanged(false, QuestResultCode.REWARD_FAILED);
		}

		refreshBestEffort(player, definition);
		return QuestResult.changed(QuestResultCode.COMPLETED);
	}

	private static boolean addToCandidate(int[] items, int[] amounts,
			ItemAmount reward) {
		ItemDefinition[] definitions = ItemDefinition.getDefinitions();
		int id = reward.getItemId();
		if (definitions == null || id < 0 || id >= definitions.length
				|| definitions[id] == null
				|| definitions[id].getId() != id) {
			throw new IllegalStateException(
					"Missing reward item definition: " + id);
		}
		if (definitions[id].isStackable()) {
			for (int i = 0; i < items.length; i++) {
				if (items[i] == id + 1) {
					long total = (long) amounts[i] + reward.getAmount();
					if (total > ItemConstants.MAX_ITEM_AMOUNT) {
						return false;
					}
					amounts[i] = (int) total;
					return true;
				}
			}
			int slot = freeSlot(items);
			if (slot < 0) {
				return false;
			}
			items[slot] = id + 1;
			amounts[slot] = reward.getAmount();
			return true;
		}
		if (reward.getAmount() > freeSlots(items)) {
			return false;
		}
		for (int remaining = reward.getAmount(); remaining > 0; remaining--) {
			int slot = freeSlot(items);
			items[slot] = id + 1;
			amounts[slot] = 1;
		}
		return true;
	}

	private static int freeSlot(int[] items) {
		for (int i = 0; i < items.length; i++) {
			if (items[i] <= 0) {
				return i;
			}
		}
		return -1;
	}

	private static int freeSlots(int[] items) {
		int count = 0;
		for (int item : items) {
			if (item <= 0) {
				count++;
			}
		}
		return count;
	}

	private static void verify(Player player, QuestDefinition definition,
			int[] items, int[] amounts, int[] xp, int[] levels, int points) {
		if (!Arrays.equals(items, player.playerItems)
				|| !Arrays.equals(amounts, player.playerItemsN)
				|| !Arrays.equals(xp, player.playerXP)
				|| !Arrays.equals(levels, player.playerLevel)
				|| player.questPoints != points
				|| QuestStateAccess.state(player, definition.getId())
						!= QuestState.COMPLETED
				|| QuestStateAccess.stage(player, definition.getId())
						!= definition.getFinalStage()
				|| Double.compare(player.weight, Weight.calculateWeight(
						player.playerItems, player.playerEquipment)) != 0) {
			throw new IllegalStateException("Quest reward postcondition failed");
		}
	}

	private static void restore(Player player, int[] items, int[] amounts,
			double weight, int[] xp, int[] levels, int points,
			ScriptStateSnapshot state) {
		System.arraycopy(items, 0, player.playerItems, 0, items.length);
		System.arraycopy(amounts, 0, player.playerItemsN, 0, amounts.length);
		System.arraycopy(xp, 0, player.playerXP, 0, xp.length);
		System.arraycopy(levels, 0, player.playerLevel, 0, levels.length);
		player.weight = weight;
		player.questPoints = points;
		player.getScriptState().replace(state);
	}

	private void refreshBestEffort(Player player, QuestDefinition definition) {
		runPresentation("inventory", () -> presentation.refreshInventory(player));
		runPresentation("weight", () -> presentation.refreshWeight(player));
		boolean[] refreshed = new boolean[player.playerXP.length];
		for (ExperienceReward reward : definition.getRewards().getExperience()) {
			final int skill = reward.getSkill().getIndex();
			if (!refreshed[skill]) {
				refreshed[skill] = true;
				runPresentation("skill " + skill,
						() -> presentation.refreshSkill(player, skill));
			}
		}
		runPresentation("quest stages",
				() -> presentation.refreshQuestStages(player));
	}

	private static void runPresentation(String operation, Runnable refresh) {
		try {
			refresh.run();
		} catch (RuntimeException failure) {
			LOGGER.log(Level.WARNING,
					"Quest reward committed but " + operation
							+ " presentation refresh failed",
					failure);
		}
	}
}
