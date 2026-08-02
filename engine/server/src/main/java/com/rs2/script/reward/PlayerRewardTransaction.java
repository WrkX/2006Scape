package com.rs2.script.reward;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apollo.cache.def.ItemDefinition;

import com.rs2.game.items.ItemConstants;
import com.rs2.game.items.Weight;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerAssistant;
import com.rs2.script.state.ScriptStateSnapshot;
import com.rs2.script.state.ScriptStateValue;

/**
 * Shared player-local reward transaction.
 *
 * <p>Simulates then atomically applies one {@link RewardDefinition}:
 * snapshots the exact inventory, derived {@code player.weight}, skill XP and
 * current levels, quest points, and script state under the player's
 * reward-state owner; preflights the candidate; mutates; recalculates and
 * verifies the derived weight with every other postcondition; and commits
 * the reward-state version exactly once. Any preflight refusal or mutation
 * exception restores the complete snapshot, leaves the reward-state version
 * unchanged, and mutates neither items, weight, XP, points, nor state.
 * Presentation refresh stays post-commit best effort in inventory, weight,
 * skill, then quest/state order.
 */
public final class PlayerRewardTransaction {

	/** Terminal result of one player-local reward attempt. */
	public enum Result {
		/** Every component committed. */
		OK,
		/** Item grants cannot all fit with valid definitions and stack limits. */
		INVENTORY_FULL,
		/** An XP grant would exceed the 200,000,000 cap. */
		XP_CAP,
		/** The net quest-point total would leave 0..10000. */
		QUEST_POINTS_OVERFLOW,
		/** A mutation, postcondition, or state application failed; restored. */
		MUTATION_FAILED
	}

	/** Runs inside the mutation block after the core arrays are written. */
	public interface MutationHook {
		void run(Player player);
	}

	/** Post-commit best-effort presentation order. */
	public interface Presentation {
		void refreshInventory(Player player);
		void refreshWeight(Player player);
		void refreshSkill(Player player, int skill);
		void refreshQuestStages(Player player);
	}

	private static final Logger LOGGER =
			Logger.getLogger(PlayerRewardTransaction.class.getName());
	private static final int MAX_XP = 200000000;
	private static final int MAX_QUEST_POINTS = 10000;

	/**
	 * Applies {@code reward} to {@code player} under the player's reward-state
	 * owner mutex.
	 *
	 * @param hook mutation-block extension (quest stage/state, side effects)
	 * @param presentation post-commit best-effort refreshes; may be
	 *            {@code null} to skip presentation
	 */
	public static Result apply(Player player, RewardDefinition reward,
			MutationHook hook, Presentation presentation) {
		if (player == null || reward == null) {
			return Result.MUTATION_FAILED;
		}
		PlayerRewardStateOwner owner = PlayerRewardStateStore.ownerOf(player);
		synchronized (owner.mutex()) {
			long ownerVersion = owner.version();
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
				long points = (long) oldPoints + reward.questPoints();
				if (points < 0 || points > MAX_QUEST_POINTS) {
					return Result.QUEST_POINTS_OVERFLOW;
				}
				nextPoints = (int) points;
				for (RewardDefinition.ItemReward item : reward.items()) {
					if (!addToCandidate(nextItems, nextAmounts, item)) {
						return Result.INVENTORY_FULL;
					}
				}
				long[] xpAwards = new long[nextXp.length];
				for (RewardDefinition.ExperienceReward grant
						: reward.experience()) {
					int skill = grant.skillIndex();
					if (skill < 0 || skill >= nextXp.length) {
						return Result.MUTATION_FAILED;
					}
					xpAwards[skill] += grant.amount();
					if (xpAwards[skill] > MAX_XP
							|| (long) oldXp[skill] + xpAwards[skill]
									> MAX_XP) {
						return Result.XP_CAP;
					}
				}
				for (int skill = 0; skill < xpAwards.length; skill++) {
					if (xpAwards[skill] == 0) {
						continue;
					}
					int oldBase = PlayerAssistant.getLevelForXP(oldXp[skill]);
					int delta = oldLevels[skill] - oldBase;
					nextXp[skill] = (int) ((long) oldXp[skill]
							+ xpAwards[skill]);
					int newBase = PlayerAssistant.getLevelForXP(nextXp[skill]);
					nextLevels[skill] = Math.max(0, Math.min(255,
							newBase + delta));
				}

				System.arraycopy(nextItems, 0, player.playerItems, 0,
						nextItems.length);
				System.arraycopy(nextAmounts, 0, player.playerItemsN, 0,
						nextAmounts.length);
				System.arraycopy(nextXp, 0, player.playerXP, 0, nextXp.length);
				System.arraycopy(nextLevels, 0, player.playerLevel, 0,
						nextLevels.length);
				player.questPoints = nextPoints;
				for (RewardDefinition.StateMutation mutation
						: reward.stateMutations()) {
					applyState(player, mutation);
				}
				player.weight = Weight.calculateWeight(player.playerItems,
						player.playerEquipment);
				if (hook != null) {
					hook.run(player);
				}
				verify(player, owner, ownerVersion, nextItems, nextAmounts,
						nextXp, nextLevels, nextPoints);
			} catch (RuntimeException failure) {
				restore(player, oldItems, oldAmounts, oldWeight, oldXp,
						oldLevels, oldPoints, oldState);
				LOGGER.log(Level.WARNING,
						"Player reward failed and was rolled back", failure);
				return Result.MUTATION_FAILED;
			}

			owner.commit();
			if (presentation != null) {
				refreshBestEffort(player, reward, presentation);
			}
			return Result.OK;
		}
	}

	private static void applyState(Player player,
			RewardDefinition.StateMutation mutation) {
		// The store's set() returns false only for an idempotent no-change;
		// validation and limit violations throw ScriptStateException.
		if (mutation.isBoolean()) {
			player.getScriptState().set(mutation.namespace(),
					mutation.key(), ScriptStateValue.of(mutation.booleanValue()));
		} else if (mutation.isNumber()) {
			player.getScriptState().set(mutation.namespace(),
					mutation.key(), ScriptStateValue.of(mutation.numberValue()));
		} else {
			player.getScriptState().set(mutation.namespace(),
					mutation.key(), ScriptStateValue.of(mutation.stringValue()));
		}
	}

	private static boolean addToCandidate(int[] items, int[] amounts,
			RewardDefinition.ItemReward reward) {
		ItemDefinition[] definitions = ItemDefinition.getDefinitions();
		int id = reward.itemId();
		if (definitions == null || id < 0 || id >= definitions.length
				|| definitions[id] == null
				|| definitions[id].getId() != id) {
			throw new IllegalStateException(
					"Missing reward item definition: " + id);
		}
		if (definitions[id].isStackable()) {
			for (int i = 0; i < items.length; i++) {
				if (items[i] == id + 1) {
					long total = (long) amounts[i] + reward.amount();
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
			amounts[slot] = reward.amount();
			return true;
		}
		if (reward.amount() > freeSlots(items)) {
			return false;
		}
		for (int remaining = reward.amount(); remaining > 0; remaining--) {
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

	private static void verify(Player player, PlayerRewardStateOwner owner,
			long ownerVersion, int[] items, int[] amounts, int[] xp,
			int[] levels, int points) {
		if (owner.version() != ownerVersion) {
			throw new IllegalStateException(
					"Player reward-state version changed during the transaction");
		}
		if (!Arrays.equals(items, player.playerItems)
				|| !Arrays.equals(amounts, player.playerItemsN)
				|| !Arrays.equals(xp, player.playerXP)
				|| !Arrays.equals(levels, player.playerLevel)
				|| player.questPoints != points
				|| Double.compare(player.weight, Weight.calculateWeight(
						player.playerItems, player.playerEquipment)) != 0) {
			throw new IllegalStateException(
					"Player reward postcondition failed");
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

	private static void refreshBestEffort(Player player,
			RewardDefinition reward, Presentation presentation) {
		runPresentation("inventory",
				() -> presentation.refreshInventory(player));
		runPresentation("weight", () -> presentation.refreshWeight(player));
		boolean[] refreshed = new boolean[player.playerXP.length];
		for (RewardDefinition.ExperienceReward grant : reward.experience()) {
			final int skill = grant.skillIndex();
			if (skill >= 0 && skill < refreshed.length && !refreshed[skill]) {
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
					"Reward committed but " + operation
							+ " presentation refresh failed",
					failure);
		}
	}

	private PlayerRewardTransaction() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
