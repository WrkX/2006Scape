package com.rs2.script.reward;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.rs2.game.items.Weight;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.drop.DropRngTransactionOwner;
import com.rs2.script.state.ScriptStateSnapshot;
import com.rs2.script.world.ScriptEncounterRng;

/**
 * Roster-wide atomic reward transaction.
 *
 * <p>One bounded synchronous attempt acquires locks in the fixed global
 * order: the shared roster-reward coordinator, the raid-session RNG owner,
 * then every per-player reward-state owner mutex in ascending
 * {@link PlayerHandler} slot order. With the full set held it captures the
 * RNG owner token/version/state and each player's reward-state version and
 * complete snapshot, clones a local WP6 RNG, and resolves the named rewards
 * into immutable plans in the frozen roster order.
 *
 * <p>Preflight covers exact connected identity, item definitions, aggregate
 * stack/slot capacity, item/amount overflow, resulting XP cap/current
 * levels, quest points, state conditions, and the finite exact current
 * weight against the captured inventory plus the captured equipment. Any
 * preflight refusal or stale-version mismatch applies no mutation, discards
 * the local plan, and leaves the RNG owner and every reward-state version
 * unchanged ({@link Result#RETRYABLE}). Mutation follows the roster order
 * and the shared player-local protocol; an exception or injected
 * postcondition failure restores every mutated member in reverse order using
 * the exact {@code QuestRewardTransaction} ordering and verifies each
 * captured version remains unchanged ({@link Result#FATAL}, never reported
 * as a clean retry). With every postcondition verified, one no-fail commit
 * advances the RNG owner state/version, publishes the new per-player
 * reward-state versions, and records the once-only award id together
 * ({@link Result#COMMITTED}).
 */
public final class RosterRewardTransaction {

	/** Terminal result of one roster-wide reward attempt. */
	public enum Result {
		/** Every plan committed; the award id is recorded once. */
		COMMITTED,
		/**
		 * Preflight refusal, stale version, or award already recorded:
		 * nothing mutated; the caller may retry once with a fresh plan.
		 */
		RETRYABLE,
		/** A frozen member is no longer the exact live identity. */
		WIPED,
		/** Rollback failure or invariant violation; never retried. */
		FATAL
	}

	/** Session callback invoked inside the no-throw commit block. */
	public interface AwardCommit {
		/** {@code true} when this session already recorded a once-only award. */
		boolean isAwarded();

		/** Records the award id together with the joint commit. */
		void markAwarded(long awardId);
	}

	/**
	 * Test seam invoked after each member's arrays are written and before
	 * that member's postcondition verification. Injected corruption here
	 * proves the reverse-order rollback of every already-mutated member.
	 * Never used by production callers.
	 */
	public interface MutationHook {
		void afterMutation(Player player, int rosterIndex);
	}

	private static final int MAX_XP = 200000000;
	private static final int MAX_QUEST_POINTS = 10000;

	/** Global roster-reward coordinator; serializes every raid reward attempt. */
	private static final Object COORDINATOR = new Object();

	private RosterRewardTransaction() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}

	/**
	 * Attempts one roster-wide reward commit.
	 *
	 * @param roster the frozen eligible roster (owner-first then join FIFO);
	 *            every member must still be the exact live identity
	 * @param rewards the named reward definitions to apply to every member
	 * @param rngOwner the raid-session RNG owner; its state advances exactly
	 *            once on commit
	 * @param awardId the stable once-only award transaction id
	 * @param commit the session award callback
	 */
	public static Result attempt(List<Player> roster,
			List<RewardDefinition> rewards, DropRngTransactionOwner rngOwner,
			long awardId, AwardCommit commit) {
		return attempt(roster, rewards, rngOwner, awardId, commit, null);
	}

	/**
	 * {@link #attempt(List, List, DropRngTransactionOwner, long, AwardCommit)}
	 * with the test-only mutation hook.
	 */
	public static Result attempt(List<Player> roster,
			List<RewardDefinition> rewards, DropRngTransactionOwner rngOwner,
			long awardId, AwardCommit commit, MutationHook hook) {
		if (roster == null || roster.isEmpty() || rewards == null
				|| rewards.isEmpty() || rngOwner == null || commit == null) {
			return Result.WIPED;
		}
		List<Player> ordered = new ArrayList<Player>(roster);
		Collections.sort(ordered, new Comparator<Player>() {
			@Override
			public int compare(Player first, Player second) {
				return Integer.compare(first.playerId, second.playerId);
			}
		});
		synchronized (COORDINATOR) {
			rngOwner.lock();
			try {
				// Locks acquire in ascending player slot order; planning
				// and mutation follow the frozen roster order.
				Map<Player, PlayerRewardStateOwner> owners =
						new java.util.IdentityHashMap<Player, PlayerRewardStateOwner>();
				Map<Player, Snapshot> snapshots =
						new java.util.IdentityHashMap<Player, Snapshot>();
				Map<Player, Plan> plans =
						new java.util.IdentityHashMap<Player, Plan>();
				for (Player player : ordered) {
					if (!isLiveIdentity(player)) {
						return Result.WIPED;
					}
					owners.put(player, PlayerRewardStateStore.ownerOf(player));
				}
				// Acquire every per-player reward mutex in ascending slot
				// order; all locks stay held for the complete attempt.
				for (Player player : ordered) {
					synchronized (owners.get(player).mutex()) {
						// Held until the attempt returns.
					}
				}
				if (commit.isAwarded()) {
					return Result.COMMITTED;
				}
				long rngVersion = rngOwner.version();
				ScriptEncounterRng local = new ScriptEncounterRng(
						rngOwner.state());
				for (Player player : roster) {
					PlayerRewardStateOwner owner = owners.get(player);
					Snapshot snapshot = Snapshot.capture(player, owner);
					if (Double.compare(snapshot.weight,
							Weight.calculateWeight(snapshot.items,
									player.playerEquipment)) != 0) {
						return Result.RETRYABLE;
					}
					snapshots.put(player, snapshot);
					Plan plan = Plan.build(snapshot, rewards);
					if (plan == null) {
						return Result.RETRYABLE;
					}
					plans.put(player, plan);
				}
				// Revalidate the RNG owner version immediately before the
				// first player mutation; a mismatch discards the plan
				// without mutation and consumes one bounded retry.
				if (rngOwner.version() != rngVersion) {
					return Result.RETRYABLE;
				}
				int rosterIndex = 0;
				for (Player player : roster) {
					PlayerRewardStateOwner owner = owners.get(player);
					Snapshot old = snapshots.get(player);
					Plan plan = plans.get(player);
					try {
						System.arraycopy(plan.items, 0, player.playerItems,
								0, plan.items.length);
						System.arraycopy(plan.amounts, 0, player.playerItemsN,
								0, plan.amounts.length);
						System.arraycopy(plan.xp, 0, player.playerXP, 0,
								plan.xp.length);
						System.arraycopy(plan.levels, 0, player.playerLevel,
								0, plan.levels.length);
						player.questPoints = plan.points;
						for (RewardDefinition.StateMutation mutation
								: plan.mutations) {
							PlayerRewardTransaction.applyState(player,
									mutation);
						}
						player.weight = Weight.calculateWeight(
								player.playerItems, player.playerEquipment);
						if (hook != null) {
							hook.afterMutation(player, rosterIndex);
						}
						PlayerRewardTransaction.verify(player, owner,
								old.ownerVersion, plan.items, plan.amounts,
								plan.xp, plan.levels, plan.points);
					} catch (RuntimeException failure) {
						List<Player> mutated = new ArrayList<Player>();
						for (int restoreIndex = 0;
								restoreIndex <= rosterIndex; restoreIndex++) {
							mutated.add(roster.get(restoreIndex));
						}
						Collections.reverse(mutated);
						for (Player victim : mutated) {
							Snapshot oldSnapshot = snapshots.get(victim);
							PlayerRewardTransaction.restore(victim,
									oldSnapshot.items, oldSnapshot.amounts,
									oldSnapshot.weight, oldSnapshot.xp,
									oldSnapshot.levels, oldSnapshot.points,
									oldSnapshot.state);
							if (PlayerRewardStateStore.ownerOf(victim)
									.version() != oldSnapshot.ownerVersion) {
								return Result.FATAL;
							}
						}
						return Result.FATAL;
					}
					rosterIndex++;
				}
				// No-fail joint commit: advance the raid RNG state once,
				// publish the new player reward-state versions, and record
				// the once-only award id together.
				local.nextLong();
				rngOwner.publishState(local.state());
				for (Player player : ordered) {
					owners.get(player).commit();
				}
				commit.markAwarded(awardId);
				return Result.COMMITTED;
			} finally {
				rngOwner.unlock();
			}
		}
	}

	private static boolean isLiveIdentity(Player player) {
		return player != null && player.playerId >= 0
				&& player.playerId < PlayerHandler.players.length
				&& PlayerHandler.players[player.playerId] == player
				&& player.isActive && !player.disconnected
				&& player.initialized;
	}

	/** Complete immutable snapshot of one member before any mutation. */
	private static final class Snapshot {
		private final int[] items;
		private final int[] amounts;
		private final double weight;
		private final int[] xp;
		private final int[] levels;
		private final int points;
		private final ScriptStateSnapshot state;
		private final long ownerVersion;

		private Snapshot(int[] items, int[] amounts, double weight,
				int[] xp, int[] levels, int points,
				ScriptStateSnapshot state, long ownerVersion) {
			this.items = items;
			this.amounts = amounts;
			this.weight = weight;
			this.xp = xp;
			this.levels = levels;
			this.points = points;
			this.state = state;
			this.ownerVersion = ownerVersion;
		}

		static Snapshot capture(Player player, PlayerRewardStateOwner owner) {
			return new Snapshot(player.playerItems.clone(),
					player.playerItemsN.clone(), player.weight,
					player.playerXP.clone(), player.playerLevel.clone(),
					player.questPoints, player.getScriptState().snapshot(),
					owner.version());
		}
	}

	/** Immutable candidate arrays of one member's combined reward plan. */
	private static final class Plan {
		private final int[] items;
		private final int[] amounts;
		private final int[] xp;
		private final int[] levels;
		private final int points;
		private final List<RewardDefinition.StateMutation> mutations;

		private Plan(int[] items, int[] amounts, int[] xp, int[] levels,
				int points, List<RewardDefinition.StateMutation> mutations) {
			this.items = items;
			this.amounts = amounts;
			this.xp = xp;
			this.levels = levels;
			this.points = points;
			this.mutations = mutations;
		}

		/** Returns {@code null} when any preflight refusal applies. */
		static Plan build(Snapshot snapshot,
				List<RewardDefinition> rewards) {
			int[] items = snapshot.items.clone();
			int[] amounts = snapshot.amounts.clone();
			int[] xp = snapshot.xp.clone();
			int[] levels = snapshot.levels.clone();
			long points = snapshot.points;
			long[] xpAwards = new long[xp.length];
			List<RewardDefinition.StateMutation> mutations =
					new ArrayList<RewardDefinition.StateMutation>();
			for (RewardDefinition reward : rewards) {
				long nextPoints = points + reward.questPoints();
				if (nextPoints < 0 || nextPoints > MAX_QUEST_POINTS) {
					return null;
				}
				points = nextPoints;
				for (RewardDefinition.ItemReward item : reward.items()) {
					if (!PlayerRewardTransaction.addToCandidate(items,
							amounts, item)) {
						return null;
					}
				}
				for (RewardDefinition.ExperienceReward grant
						: reward.experience()) {
					int skill = grant.skillIndex();
					if (skill < 0 || skill >= xpAwards.length) {
						return null;
					}
					xpAwards[skill] += grant.amount();
					if (xpAwards[skill] > MAX_XP) {
						return null;
					}
				}
				mutations.addAll(reward.stateMutations());
			}
			if (!PlayerRewardTransaction.planExperience(xp, levels,
					xpAwards)) {
				return null;
			}
			return new Plan(items, amounts, xp, levels, (int) points,
					mutations);
		}
	}

}
