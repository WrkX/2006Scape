package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerAssistant;
import com.rs2.script.reward.PlayerRewardStateOwner;
import com.rs2.script.reward.PlayerRewardStateStore;
import com.rs2.script.reward.RewardGrantResult;
import com.rs2.script.registries.RegistryStore;

/**
 * Proves the named-reward consumer: one named reward commits through a real
 * player transaction (items, recalculated weight, XP, quest points, and
 * state), every failure path restores the complete snapshot and leaves the
 * reward-state version unchanged, and the version increments exactly once on
 * commit.
 */
public class RewardTransactionTest {

	private Context context;
	private Player player;
	private PlayerRewardStateOwner owner;

	@Before
	public void setUp() throws Exception {
		Wp5PlayerSupport.ensureItemDefinitions();
		context = Context.create("js");
		player = new Player(-1) { };
		owner = PlayerRewardStateStore.ownerOf(player);
		registerReward(
				"{id:'starter-pack',items:[{id:995,amount:100}],"
						+ "questPoints:2,"
						+ "experience:[{skill:'magic',amount:100}],"
						+ "state:[{namespace:'demo',key:'started',value:true}]}");
	}

	@After
	public void tearDown() {
		ScriptRuntimeTestFixture.reset();
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void namedRewardCommitsItemsXpPointsWeightAndState() {
		RewardGrantResult result = new ScriptedPlayer(player).grantReward(
				"starter-pack");

		assertEquals("rewarded", result.code());
		assertEquals("starter-pack", result.rewardId());
		assertTrue(hasItem(player, 995, 100));
		assertEquals(2, player.questPoints);
		assertEquals(100, player.playerXP[6]);
		assertEquals(PlayerAssistant.getLevelForXP(100),
				player.playerLevel[6]);
		assertTrue(player.getScriptState().get("demo", "started").asBoolean());
		assertEquals(0.0d, player.weight, 0.0d);
		assertEquals(com.rs2.game.items.Weight.calculateWeight(
				player.playerItems, player.playerEquipment), player.weight,
				0.0d);
		assertEquals(1L, owner.version());
	}

	@Test
	public void unknownRewardReturnsNotFoundWithoutMutation() {
		RewardGrantResult result = new ScriptedPlayer(player).grantReward(
				"no-such-reward");

		assertEquals("not_found", result.code());
		assertEquals(0L, owner.version());
		assertFalse(hasItem(player, 995, 1));
		assertEquals(0, player.questPoints);
	}

	@Test
	public void inventoryFullRejectsTheWholeReward() {
		fillInventory(player, 526);
		player.questPoints = 5;
		player.playerXP[6] = 50;

		RewardGrantResult result = new ScriptedPlayer(player).grantReward(
				"starter-pack");

		assertEquals("inventory_full", result.code());
		assertEquals(0L, owner.version());
		assertFalse(hasItem(player, 995, 100));
		assertEquals(5, player.questPoints);
		assertEquals(50, player.playerXP[6]);
	}

	@Test
	public void xpCapRejectsTheWholeRewardWithoutClamping() {
		player.playerXP[6] = 199999950;
		player.playerLevel[6] = PlayerAssistant.getLevelForXP(
				player.playerXP[6]);

		RewardGrantResult result = new ScriptedPlayer(player).grantReward(
				"starter-pack");

		assertEquals("xp_cap", result.code());
		assertEquals(0L, owner.version());
		assertFalse(hasItem(player, 995, 100));
		assertEquals(199999950, player.playerXP[6]);
		assertEquals(0, player.questPoints);
	}

	@Test
	public void questPointsOverflowRejectsTheWholeReward() {
		player.questPoints = 9999;

		RewardGrantResult result = new ScriptedPlayer(player).grantReward(
				"starter-pack");

		assertEquals("quest_points_overflow", result.code());
		assertEquals(0L, owner.version());
		assertFalse(hasItem(player, 995, 100));
		assertEquals(9999, player.questPoints);
	}

	@Test
	public void stateApplicationFailureRollsBackEveryComponent() {
		// Fill the namespace to its per-namespace limit so the reward's
		// state mutation throws and rolls the whole transaction back.
		for (int index = 0; index < 256; index++) {
			player.getScriptState().set("demo", "key" + index,
					com.rs2.script.state.ScriptStateValue.of((double) index));
		}
		player.questPoints = 4;
		player.playerXP[6] = 25;
		player.playerLevel[6] = PlayerAssistant.getLevelForXP(25);

		RewardGrantResult result = new ScriptedPlayer(player).grantReward(
				"starter-pack");

		assertEquals("reward_failed", result.code());
		assertEquals(0L, owner.version());
		assertFalse(hasItem(player, 995, 100));
		assertEquals(4, player.questPoints);
		assertEquals(25, player.playerXP[6]);
		assertFalse(player.getScriptState().has("demo", "started"));
		assertEquals(0.0d, player.weight, 0.0d);
	}

	@Test
	public void repeatedGrantsCommitIndependently() {
		new ScriptedPlayer(player).grantReward("starter-pack");
		new ScriptedPlayer(player).grantReward("starter-pack");

		assertTrue(hasItem(player, 995, 200));
		assertEquals(4, player.questPoints);
		assertEquals(2L, owner.version());
	}

	private void registerReward(String source) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineReward().accept(
					context.eval("js", "(" + source + ")"));
			ScriptHost.getInstance().publishForTesting(context, candidate);
		} catch (RuntimeException error) {
			RegistryStore.rollback(candidate);
			throw error;
		}
	}

	private static boolean hasItem(Player player, int id, int amount) {
		for (int slot = 0; slot < player.playerItems.length; slot++) {
			if (player.playerItems[slot] == id + 1
					&& player.playerItemsN[slot] >= amount) {
				return true;
			}
		}
		return false;
	}

	private static void fillInventory(Player player, int itemId) {
		for (int slot = 0; slot < player.playerItems.length; slot++) {
			player.playerItems[slot] = itemId + 1;
			player.playerItemsN[slot] = 1;
		}
	}

}
