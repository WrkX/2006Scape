package com.rs2.script.quest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.apollo.cache.def.ItemDefinition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.Constants;
import com.rs2.game.items.Weight;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerAssistant;

public class QuestRewardTransactionTest {

	private ItemDefinition[] previousDefinitions;
	private boolean previousVariableRate;
	private double previousRate;

	@Before
	public void definitions() throws Exception {
		Field field = ItemDefinition.class.getDeclaredField("definitions");
		field.setAccessible(true);
		previousDefinitions = (ItemDefinition[]) field.get(null);
		ItemDefinition[] definitions = new ItemDefinition[1100];
		definitions[995] = definition(995, true);
		definitions[1000] = definition(1000, true);
		definitions[1001] = definition(1001, false);
		field.set(null, definitions);
		previousVariableRate = Constants.VARIABLE_XP_RATE;
		previousRate = Constants.XP_RATE;
	}

	@After
	public void restore() throws Exception {
		Field field = ItemDefinition.class.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, previousDefinitions);
		Constants.VARIABLE_XP_RATE = previousVariableRate;
		Constants.XP_RATE = previousRate;
	}

	@Test
	public void completionUsesFixedXpPreservesBoostAndUpdatesLegacyPoints() {
		Player player = new Player(-1) { };
		int skill = QuestSkill.MAGIC.getIndex();
		player.playerXP[skill] = PlayerAssistant.getXPForLevel(10);
		player.playerLevel[skill] = 15;
		player.questPoints = 7;
		Constants.VARIABLE_XP_RATE = false;
		Constants.XP_RATE = 100.0;
		QuestDefinition definition = rewarding("atomic-rewards", 1000, 2,
				QuestSkill.MAGIC, 1000);
		QuestService service = new QuestService(new QuestRewardTransaction());
		service.start(player, definition);

		int oldXp = player.playerXP[skill];
		assertTrue(service.complete(player, definition, 0).changed());
		assertEquals(oldXp + 1000, player.playerXP[skill]);
		assertEquals(PlayerAssistant.getLevelForXP(player.playerXP[skill]) + 5,
				player.playerLevel[skill]);
		assertEquals(9, player.questPoints);
		assertEquals(1000, player.playerItems[0] - 1);
		assertEquals(1, player.playerItemsN[0]);
		assertEquals(Weight.calculateWeight(player.playerItems,
				player.playerEquipment), player.weight, 0.0);
	}

	@Test
	public void fullInventoryAndMissingMetadataLeaveEverythingUntouched() {
		Player player = new Player(-1) { };
		Arrays.fill(player.playerItems, 1002);
		Arrays.fill(player.playerItemsN, 1);
		player.questPoints = 4;
		QuestDefinition definition = rewarding("full-inventory", 1001, 2,
				QuestSkill.MAGIC, 10);
		QuestService service = new QuestService(new QuestRewardTransaction());
		service.start(player, definition);
		int[] items = player.playerItems.clone();
		int[] xp = player.playerXP.clone();

		QuestResult result = service.complete(player, definition, 0);
		assertFalse(result.ok());
		assertFalse(result.changed());
		assertArrayEquals(items, player.playerItems);
		assertArrayEquals(xp, player.playerXP);
		assertEquals(4, player.questPoints);
		assertEquals(QuestState.IN_PROGRESS,
				service.state(player, definition.getId()));
		player.playerItems[0] = 0;
		player.playerItemsN[0] = 0;
		assertEquals("completed",
				service.complete(player, definition, 0).code());

		QuestDefinition missing = rewarding("missing-metadata", 1099, 1,
				QuestSkill.MAGIC, 10);
		Player other = new Player(-1) { };
		service.start(other, missing);
		assertEquals("reward_failed",
				service.complete(other, missing, 0).code());
		assertEquals(QuestState.IN_PROGRESS,
				service.state(other, missing.getId()));
		ItemDefinition.getDefinitions()[1099] = definition(1099, true);
		assertEquals("completed",
				service.complete(other, missing, 0).code());
	}

	@Test
	public void forcedPostconditionFailureRestoresEveryMutatedComponent() {
		Player player = new Player(-1) { };
		player.weight = 12.5;
		player.questPoints = 5;
		int[] items = player.playerItems.clone();
		int[] amounts = player.playerItemsN.clone();
		int[] xp = player.playerXP.clone();
		int[] levels = player.playerLevel.clone();
		QuestDefinition definition = rewarding("rollback-rewards", 1000, 3,
				QuestSkill.MAGIC, 500);
		QuestService service = new QuestService(new QuestRewardTransaction(
				mutated -> {
					throw new IllegalStateException("forced");
				}));
		service.start(player, definition);

		assertEquals("reward_failed",
				service.complete(player, definition, 0).code());
		assertArrayEquals(items, player.playerItems);
		assertArrayEquals(amounts, player.playerItemsN);
		assertArrayEquals(xp, player.playerXP);
		assertArrayEquals(levels, player.playerLevel);
		assertEquals(12.5, player.weight, 0.0);
		assertEquals(5, player.questPoints);
		assertEquals(QuestState.IN_PROGRESS,
				service.state(player, definition.getId()));
		assertEquals(Integer.valueOf(0),
				service.stage(player, definition.getId()));
	}

	@Test
	public void xpCapRejectsTheWholeTransactionWithoutClamping() {
		Player player = new Player(-1) { };
		int skill = QuestSkill.MAGIC.getIndex();
		player.playerXP[skill] = 199999500;
		player.playerLevel[skill] =
				PlayerAssistant.getLevelForXP(player.playerXP[skill]);
		player.questPoints = 4;
		QuestDefinition definition = rewarding("xp-cap", 1000, 2,
				QuestSkill.MAGIC, 501);
		QuestService service = new QuestService(new QuestRewardTransaction());
		service.start(player, definition);
		int[] items = player.playerItems.clone();
		int[] xp = player.playerXP.clone();

		QuestResult result = service.complete(player, definition, 0);
		assertEquals("xp_cap", result.code());
		assertFalse(result.ok());
		assertFalse(result.changed());
		assertArrayEquals(items, player.playerItems);
		assertArrayEquals(xp, player.playerXP);
		assertEquals(4, player.questPoints);
		assertEquals(QuestState.IN_PROGRESS,
				service.state(player, definition.getId()));

		player.playerXP[skill] = 199999000;
		player.playerLevel[skill] =
				PlayerAssistant.getLevelForXP(player.playerXP[skill]);
		assertEquals("completed",
				service.complete(player, definition, 0).code());

		Player exact = new Player(-1) { };
		exact.playerXP[skill] = 199999500;
		exact.playerLevel[skill] =
				PlayerAssistant.getLevelForXP(exact.playerXP[skill]);
		QuestDefinition exactDefinition = rewarding("xp-cap-exact", 1000, 0,
				QuestSkill.MAGIC, 500);
		service.start(exact, exactDefinition);
		assertEquals("completed",
				service.complete(exact, exactDefinition, 0).code());
		assertEquals(200000000, exact.playerXP[skill]);
	}

	@Test
	public void duplicateSkillRewardsAreAggregatedBeforeMutation() {
		Player player = new Player(-1) { };
		int skill = QuestSkill.MAGIC.getIndex();
		player.playerXP[skill] = 199999600;
		player.playerLevel[skill] =
				PlayerAssistant.getLevelForXP(player.playerXP[skill]);
		QuestDefinition definition = QuestServiceTest.definition(
				"aggregate-xp-cap", 0, QuestServiceTest.emptyRequirements(),
				new QuestDefinition.Rewards(0,
						Collections.<QuestDefinition.ItemAmount>emptyList(),
						Arrays.asList(
								new QuestDefinition.ExperienceReward(
										QuestSkill.MAGIC, 250),
								new QuestDefinition.ExperienceReward(
										QuestSkill.MAGIC, 250))));
		QuestService service = new QuestService(new QuestRewardTransaction());
		service.start(player, definition);

		assertEquals("xp_cap",
				service.complete(player, definition, 0).code());
		assertEquals(199999600, player.playerXP[skill]);
		assertEquals(QuestState.IN_PROGRESS,
				service.state(player, definition.getId()));
	}

	@Test
	public void questPointOverflowIsRetryableWithoutPartialRewards() {
		Player player = new Player(-1) { };
		player.questPoints = 9999;
		QuestDefinition definition = rewarding("point-cap", 1000, 2,
				QuestSkill.MAGIC, 100);
		QuestService service = new QuestService(new QuestRewardTransaction());
		service.start(player, definition);
		int[] items = player.playerItems.clone();
		int[] xp = player.playerXP.clone();

		assertEquals("quest_points_overflow",
				service.complete(player, definition, 0).code());
		assertArrayEquals(items, player.playerItems);
		assertArrayEquals(xp, player.playerXP);
		assertEquals(9999, player.questPoints);
		assertEquals(QuestState.IN_PROGRESS,
				service.state(player, definition.getId()));

		player.questPoints = 9998;
		assertEquals("completed",
				service.complete(player, definition, 0).code());
		assertEquals(10000, player.questPoints);
	}

	@Test
	public void drainedLevelDeltaIsPreserved() {
		Player player = new Player(-1) { };
		int skill = QuestSkill.MAGIC.getIndex();
		player.playerXP[skill] = PlayerAssistant.getXPForLevel(20);
		player.playerLevel[skill] = 13;
		QuestDefinition definition = rewarding("drained-level", 1000, 0,
				QuestSkill.MAGIC, 5000);
		QuestService service = new QuestService(new QuestRewardTransaction());
		int oldBase = PlayerAssistant.getLevelForXP(player.playerXP[skill]);
		service.start(player, definition);

		assertTrue(service.complete(player, definition, 0).changed());
		int newBase = PlayerAssistant.getLevelForXP(player.playerXP[skill]);
		assertEquals(newBase + (13 - oldBase), player.playerLevel[skill]);
	}

	@Test
	public void presentationFailuresAreLoggedAndNeverUndoCompletion() {
		Player player = new Player(-1) { };
		QuestDefinition definition = rewarding("presentation", 1000, 2,
				QuestSkill.MAGIC, 100);
		AtomicInteger attempted = new AtomicInteger();
		QuestRewardTransaction.Presentation failing =
				new QuestRewardTransaction.Presentation() {
					private void fail() {
						attempted.incrementAndGet();
						throw new IllegalStateException("presentation");
					}

					@Override
					public void refreshInventory(Player ignored) {
						fail();
					}

					@Override
					public void refreshWeight(Player ignored) {
						fail();
					}

					@Override
					public void refreshSkill(Player ignored, int skill) {
						fail();
					}

					@Override
					public void refreshQuestStages(Player ignored) {
						fail();
					}
				};
		QuestService service = new QuestService(new QuestRewardTransaction(
				mutated -> { }, failing));
		service.start(player, definition);

		QuestResult result = service.complete(player, definition, 0);
		assertEquals("completed", result.code());
		assertTrue(result.ok());
		assertTrue(result.changed());
		assertEquals(4, attempted.get());
		assertEquals(QuestState.COMPLETED,
				service.state(player, definition.getId()));
		assertEquals(2, player.questPoints);
		assertEquals(1000, player.playerItems[0] - 1);
	}

	private static QuestDefinition rewarding(String id, int itemId, int points,
			QuestSkill skill, int xp) {
		return QuestServiceTest.definition(id, 0,
				QuestServiceTest.emptyRequirements(),
				new QuestDefinition.Rewards(points,
						Arrays.asList(new QuestDefinition.ItemAmount(itemId, 1)),
						Arrays.asList(new QuestDefinition.ExperienceReward(
								skill, xp))));
	}

	private static ItemDefinition definition(int id, boolean stackable) {
		ItemDefinition definition = new ItemDefinition(id);
		definition.setName("Test " + id);
		definition.setDescription("Test");
		definition.setStackable(stackable);
		return definition;
	}
}
