package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.drop.DropRngTransactionOwner;
import com.rs2.script.reward.RewardDefinition;
import com.rs2.script.reward.RosterRewardTransaction;
import com.rs2.script.world.ScriptEncounterRng;

/**
 * Proves the roster-wide reward atomicity matrix: the fixed coordinator ->
 * RNG-owner -> ascending-player lock order, exact snapshots, fresh
 * per-attempt local planning, reverse-order rollback on an injected
 * second-player postcondition failure, discard-and-retry on stale versions,
 * the joint once-only RNG/award commit, duplicate no-ops, and departure
 * wipes.
 */
public class ScriptRaidRewardTest {

	private static final int X = Wp5PlayerSupport.X;
	private static final int Y = Wp5PlayerSupport.Y;
	private static final long INITIAL_RNG_STATE = 0x0123456789abcdefL;

	private Player first;
	private Player second;
	private TestRngOwner rng;
	private TestAwardCommit award;
	private RewardDefinition reward;

	@Before
	public void setUp() throws Exception {
		com.rs2.script.world.ScriptEncounterService.getInstance()
				.resetForTesting();
		Wp5PlayerSupport.ensureItemDefinitions();
		first = Wp5PlayerSupport.player(1);
		second = Wp5PlayerSupport.additionalPlayer(2);
		rng = new TestRngOwner(INITIAL_RNG_STATE);
		award = new TestAwardCommit();
		reward = reward(5, 100, 2, true);
	}

	@After
	public void tearDown() {
		Wp5PlayerSupport.cleanup(first);
		Wp5PlayerSupport.cleanup(second);
	}

	@Test
	public void commitsAllRewardsToBothMembersAtomically() {
		first.playerXP[0] = 1000;
		first.playerLevel[0] = 1;
		double firstWeight = first.weight;

		RosterRewardTransaction.Result result = attempt(roster(), reward);

		assertEquals(RosterRewardTransaction.Result.COMMITTED, result);
		assertEquals(5, countItem(first, 995));
		assertEquals(1, countItem(first, 1127));
		assertEquals(5, countItem(second, 995));
		assertEquals(1, countItem(second, 1127));
		assertEquals(1100, first.playerXP[0]);
		assertEquals(2, first.questPoints);
		assertEquals(2, second.questPoints);
		assertTrue(first.getScriptState().get("test-ns", "flag").asBoolean());
		assertTrue(second.getScriptState().get("test-ns", "flag").asBoolean());
		// The exact pre-attempt weight is replaced by the recalculated one
		// (both items are weightless, so it stays exact zero).
		assertEquals(0.0d, first.weight, 0.0001d);
		// Versions: rng advanced once, each player's reward state once.
		assertEquals(1, rng.version);
		assertEquals(1, version(first));
		assertEquals(1, version(second));
		// The once-only award id was recorded.
		assertTrue(award.awarded);
		assertEquals(77L, award.awardId);
		// The committed RNG state is exactly one gamma step past the
		// snapshot.
		assertEquals(INITIAL_RNG_STATE + 0x9E3779B97F4A7C15L, rng.state);
	}

	@Test
	public void fullInventoryOnAnyMemberRefusesWithoutMutation() {
		for (int slot = 0; slot < second.playerItems.length; slot++) {
			second.playerItems[slot] = 1128;
			second.playerItemsN[slot] = 1;
		}
		double secondWeight = second.weight;

		RosterRewardTransaction.Result result = attempt(roster(), reward);

		assertEquals(RosterRewardTransaction.Result.RETRYABLE, result);
		assertEquals(0, countItem(first, 995));
		assertEquals(0, countItem(second, 995));
		assertEquals(0, first.questPoints);
		assertEquals(0, second.questPoints);
		assertEquals(0, first.playerXP[0]);
		assertTrue(Double.compare(secondWeight, second.weight) == 0);
		assertEquals(0, rng.version);
		assertEquals(0, version(first));
		assertEquals(0, version(second));
		assertFalse(award.awarded);
	}

	@Test
	public void xpCapRefusesWithoutMutation() {
		second.playerXP[0] = 200000000;

		RosterRewardTransaction.Result result = attempt(roster(), reward);

		assertEquals(RosterRewardTransaction.Result.RETRYABLE, result);
		assertEquals(0, countItem(first, 995));
		assertEquals(200000000, second.playerXP[0]);
		assertEquals(0, rng.version);
		assertFalse(award.awarded);
	}

	@Test
	public void secondPlayerPostconditionFailureRestoresBothPlayers() {
		final boolean[] injected = new boolean[1];
		RosterRewardTransaction.Result result = RosterRewardTransaction
				.attempt(roster(), Arrays.asList(reward), rng, 77L, award,
						new RosterRewardTransaction.MutationHook() {
							@Override
							public void afterMutation(Player player,
									int rosterIndex) {
								if (player == second && !injected[0]) {
									injected[0] = true;
									// Corrupt the second member's
									// inventory after its arrays were
									// written: the postcondition verify
									// must fail and restore both members.
									player.playerItems[0] = 1;
								}
							}
						});

		assertTrue(injected[0]);
		assertEquals(RosterRewardTransaction.Result.FATAL, result);
		assertEquals(0, countItem(first, 995));
		assertEquals(0, countItem(second, 995));
		assertEquals(0, first.questPoints);
		assertEquals(0, second.questPoints);
		assertEquals(0, first.playerXP[0]);
		assertEquals(0.0d, first.weight, 0.0001d);
		assertEquals(0.0d, second.weight, 0.0001d);
		assertEquals(0, rng.version);
		assertEquals(0, version(first));
		assertEquals(0, version(second));
		assertFalse(award.awarded);
	}

	@Test
	public void staleRngVersionBetweenAttemptsDiscardsAndReplansFresh() {
		// First attempt is refused by the full inventory; while the locks
		// are released, the raid RNG owner advances.
		for (int slot = 0; slot < second.playerItems.length; slot++) {
			second.playerItems[slot] = 1128;
			second.playerItemsN[slot] = 1;
		}
		assertEquals(RosterRewardTransaction.Result.RETRYABLE,
				attempt(roster(), reward));
		long advanced = INITIAL_RNG_STATE ^ 0x5DEECE66DL;
		rng.publishState(advanced);
		assertEquals(1, rng.version);

		// The inventory is repaired; the next attempt must clone the
		// advanced owner state, not the discarded first snapshot.
		for (int slot = 0; slot < second.playerItems.length; slot++) {
			second.playerItems[slot] = 0;
			second.playerItemsN[slot] = 0;
		}
		RosterRewardTransaction.Result result = attempt(roster(), reward);

		assertEquals(RosterRewardTransaction.Result.COMMITTED, result);
		assertEquals(2, rng.version);
		assertEquals(advanced + 0x9E3779B97F4A7C15L, rng.state);
		assertEquals(5, countItem(second, 995));
	}

	@Test
	public void duplicateAwardIsANoOp() {
		award.awarded = true;

		RosterRewardTransaction.Result result = attempt(roster(), reward);

		assertEquals(RosterRewardTransaction.Result.COMMITTED, result);
		assertEquals(0, countItem(first, 995));
		assertEquals(0, countItem(second, 995));
		assertEquals(0, rng.version);
		assertEquals(0, version(first));
	}

	@Test
	public void departedMemberWipesWithoutMutation() {
		PlayerHandler.players[second.playerId] = null;

		RosterRewardTransaction.Result result = attempt(roster(), reward);

		assertEquals(RosterRewardTransaction.Result.WIPED, result);
		assertEquals(0, countItem(first, 995));
		assertEquals(0, rng.version);
		assertFalse(award.awarded);
	}

	@Test
	public void slotOrderLockingDoesNotReorderTheCommit() {
		// The roster arrives join-first; locks must still acquire ascending
		// by slot while the mutation order follows the frozen roster.
		List<Player> reversed = new ArrayList<Player>();
		reversed.add(second);
		reversed.add(first);

		RosterRewardTransaction.Result result = attempt(reversed, reward);

		assertEquals(RosterRewardTransaction.Result.COMMITTED, result);
		assertEquals(5, countItem(first, 995));
		assertEquals(5, countItem(second, 995));
		assertEquals(2, first.questPoints);
		assertEquals(2, second.questPoints);
		assertEquals(1, rng.version);
		assertEquals(1, version(first));
		assertEquals(1, version(second));
	}

	// ─── Helpers ────────────────────────────────────────────────────────────

	private RosterRewardTransaction.Result attempt(List<Player> roster,
			RewardDefinition reward) {
		return RosterRewardTransaction.attempt(roster,
				Arrays.asList(reward), rng, 77L, award);
	}

	private List<Player> roster() {
		List<Player> roster = new ArrayList<Player>();
		roster.add(first);
		roster.add(second);
		return roster;
	}

	private static RewardDefinition reward(int coins, int attackXp,
			int questPoints, boolean stateFlag) {
		List<RewardDefinition.ItemReward> items =
				new ArrayList<RewardDefinition.ItemReward>();
		items.add(new RewardDefinition.ItemReward(995, coins));
		items.add(new RewardDefinition.ItemReward(1127, 1));
		List<RewardDefinition.ExperienceReward> experience =
				new ArrayList<RewardDefinition.ExperienceReward>();
		experience.add(new RewardDefinition.ExperienceReward(0, attackXp));
		List<RewardDefinition.StateMutation> state =
				new ArrayList<RewardDefinition.StateMutation>();
		state.add(new RewardDefinition.StateMutation("test-ns", "flag",
				stateFlag));
		return new RewardDefinition("test-reward", "legacy-unscoped", 0,
				items, experience, questPoints, state);
	}

	private static long version(Player player) {
		return com.rs2.script.reward.PlayerRewardStateStore
				.ownerOf(player).version();
	}

	private static int countItem(Player player, int itemId) {
		int count = 0;
		for (int index = 0; index < player.playerItems.length; index++) {
			if (player.playerItems[index] == itemId + 1) {
				count += player.playerItemsN[index];
			}
		}
		return count;
	}

	/** Minimal versioned RNG owner standing in for the raid-session owner. */
	private static final class TestRngOwner implements DropRngTransactionOwner {
		private final Object mutex = new Object();
		private long version;
		private long state;

		TestRngOwner(long initialState) {
			this.state = initialState;
		}

		@Override
		public void lock() {
			synchronized (mutex) {
			}
		}

		@Override
		public void unlock() {
			synchronized (mutex) {
			}
		}

		@Override
		public long version() {
			synchronized (mutex) {
				return version;
			}
		}

		@Override
		public long state() {
			synchronized (mutex) {
				return state;
			}
		}

		@Override
		public void publishState(long nextState) {
			synchronized (mutex) {
				state = nextState;
				version++;
			}
		}
	}

	/** Records the once-only award id at the joint commit. */
	private static final class TestAwardCommit
			implements RosterRewardTransaction.AwardCommit {
		private boolean awarded;
		private long awardId;

		@Override
		public boolean isAwarded() {
			return awarded;
		}

		@Override
		public void markAwarded(long awardedId) {
			awarded = true;
			awardId = awardedId;
		}
	}

}
