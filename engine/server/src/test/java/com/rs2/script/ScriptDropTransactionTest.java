package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.rs2.game.items.GroundItem;
import com.rs2.game.players.Player;
import com.rs2.script.world.ScriptDropResult;
import com.rs2.script.world.ScriptEncounterHandle;
import com.rs2.script.world.ScriptEncounterRng;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.script.world.ScriptGroundItemHandle;
import com.rs2.GameEngine;

/**
 * WP6 drop transaction: deterministic selection and amounts, exact staging
 * with a final private detach, and complete rollback on parse, capacity,
 * staging, or detach failure without consuming encounter randomness.
 *
 * <p>The guest tables are real GraalJS array literals, the exact shape
 * TypeScript emits for {@code readonly ScriptDropEntry[]}.
 */
public class ScriptDropTransactionTest {

	private static final long SEED = 0x0123456789abcdefL;
	private static final int X = Wp5PlayerSupport.X;
	private static final int Y = Wp5PlayerSupport.Y;

	private static Context context;
	private ScriptEncounterService service;
	private Player owner;
	private ScriptEncounterHandle encounter;
	private ArrayList<GroundItem> previousItems;

	@BeforeClass
	public static void openContext() throws Exception {
		context = ScriptHost.buildContext(
				Files.createTempDirectory("wp6-drop-content").toFile());
	}

	@AfterClass
	public static void closeContext() {
		if (context != null) {
			context.close(true);
		}
	}

	@Before
	public void setUp() throws Exception {
		previousItems = new ArrayList<GroundItem>(GameEngine.itemHandler.items);
		GameEngine.itemHandler.items.clear();
		GameEngine.itemHandler.resetProjectionsForTesting();
		service = ScriptEncounterService.installForTesting(SEED);
		service.resetForTesting();
		service.onGenerationPublished(1L);
		owner = Wp5PlayerSupport.player(91);
		encounter = Wp5PlayerSupport.scripted(owner).beginEncounter(
				"drop-transaction", X - 1, Y - 1, X + 1, Y + 1, 0);
		assertNotNull(encounter);
	}

	@After
	public void tearDown() {
		Wp5PlayerSupport.cleanup(owner);
		GameEngine.itemHandler.items.clear();
		GameEngine.itemHandler.items.addAll(previousItems);
		GameEngine.itemHandler.resetProjectionsForTesting();
	}

	@Test
	public void commitStagesDetachesAndConsumesExactlyTheLocalRolls() {
		Value table = table("[{itemId:536,minAmount:1,maxAmount:1,weight:0,"
				+ "always:true},{itemId:995,minAmount:500,maxAmount:500,"
				+ "weight:100,always:false}]");
		long stateBefore = service.rngStateForTesting(encounter.token());
		assertEquals(ScriptEncounterRng.mix64(ScriptEncounterRng.material(
				SEED, 1L, 1L, 1L)), stateBefore);

		ScriptArray results = encounter.rollDrops(Wp5PlayerSupport.scripted(owner),
				X, Y, 0, 200, table);
		assertEquals(2, results.length());
		ScriptDropResult bones = (ScriptDropResult) results.get(0);
		assertEquals(536, bones.itemId());
		assertEquals(1, bones.amount());
		assertEquals(1, bones.groundItems().length());
		ScriptGroundItemHandle bonesHandle =
				(ScriptGroundItemHandle) bones.groundItems().get(0);
		assertEquals(1, bonesHandle.identityCount());
		ScriptDropResult coins = (ScriptDropResult) results.get(1);
		assertEquals(995, coins.itemId());
		assertEquals(500, coins.amount());
		ScriptGroundItemHandle coinsHandle =
				(ScriptGroundItemHandle) coins.groundItems().get(0);

		assertFalse(bonesHandle.isAttached());
		assertFalse(bonesHandle.isClaimed());
		assertFalse(coinsHandle.isAttached());
		assertFalse(coinsHandle.isClaimed());
		assertEquals(2, bonesHandle.identities().size() + coinsHandle.identities().size());
		for (GroundItem identity : bonesHandle.identities()) {
			assertTrue(GameEngine.itemHandler.containsExact(identity));
			assertTrue(identity.isScriptPrivate());
			assertTrue(identity.isDetached());
			assertEquals(536, identity.getItemId());
		}
		for (GroundItem identity : coinsHandle.identities()) {
			assertTrue(GameEngine.itemHandler.containsExact(identity));
			assertTrue(identity.isScriptPrivate());
			assertTrue(identity.isDetached());
			assertEquals(995, identity.getItemId());
			assertEquals(500, identity.getItemAmount());
		}

		ScriptEncounterRng expected = new ScriptEncounterRng(stateBefore);
		expected.nextInt(100);
		expected.nextInt(1);
		expected.nextInt(1);
		assertEquals(expected.state(),
				service.rngStateForTesting(encounter.token()));

		assertTrue(encounter.close());
		assertTrue(GameEngine.itemHandler.containsExact(
				bonesHandle.identities().get(0)));
		assertTrue(GameEngine.itemHandler.containsExact(
				coinsHandle.identities().get(0)));
	}

	@Test
	public void everyAlwaysEntryAndExactlyOneWeightedEntryAreSelected() {
		Value table = table("[{itemId:536,minAmount:1,maxAmount:1,weight:0,"
				+ "always:true},{itemId:995,minAmount:1,maxAmount:1,weight:0,"
				+ "always:true},{itemId:536,minAmount:1,maxAmount:1,"
				+ "weight:100,always:false}]");
		ScriptArray results = encounter.rollDrops(Wp5PlayerSupport.scripted(owner),
				X, Y, 0, 50, table);
		assertEquals(3, results.length());
		assertEquals(536, ((ScriptDropResult) results.get(0)).itemId());
		assertEquals(995, ((ScriptDropResult) results.get(1)).itemId());
		assertEquals(536, ((ScriptDropResult) results.get(2)).itemId());
		assertEquals(3, GameEngine.itemHandler.items.size());
	}

	@Test
	public void weightedSelectionIsCumulativeInInputOrderAndDeterministic() {
		Value table = table("[{itemId:995,minAmount:1,maxAmount:1,weight:60,"
				+ "always:false},{itemId:536,minAmount:1,maxAmount:1,"
				+ "weight:40,always:false}]");
		long stateBefore = service.rngStateForTesting(encounter.token());
		int expectedPick = new ScriptEncounterRng(stateBefore).nextInt(100);
		int expectedItem = expectedPick < 60 ? 995 : 536;

		ScriptArray results = encounter.rollDrops(Wp5PlayerSupport.scripted(owner),
				X, Y, 0, 50, table);
		assertEquals(1, results.length());
		assertEquals(expectedItem, ((ScriptDropResult) results.get(0)).itemId());
		assertEquals(1, GameEngine.itemHandler.items.size());

		ScriptEncounterRng expected = new ScriptEncounterRng(stateBefore);
		expected.nextInt(100);
		expected.nextInt(1);
		assertEquals(expected.state(),
				service.rngStateForTesting(encounter.token()));
	}

	@Test
	public void invalidTablesFailWithoutRngConsumptionOrStagedItems() {
		long stateBefore = service.rngStateForTesting(encounter.token());
		String[][] invalid = {
			{"non-array", "42"},
			{"empty-table", "[]"},
			{"more-than-64-entries", "Array.from({length:65},()=>({itemId:536,"
					+ "minAmount:1,maxAmount:1,weight:0,always:true}))"},
			{"non-object-entry", "[1,2]"},
			{"always-true-with-positive-weight", "[{itemId:536,minAmount:1,"
					+ "maxAmount:1,weight:1,always:true}]"},
			{"non-always-with-zero-weight", "[{itemId:536,minAmount:1,"
					+ "maxAmount:1,weight:0,always:false}]"},
			{"extra-member", "[{itemId:536,minAmount:1,maxAmount:1,weight:0,"
					+ "always:true,bonus:1}]"},
			{"missing-member", "[{itemId:536,minAmount:1,maxAmount:1,"
					+ "weight:0}]"},
			{"fractional-min-amount", "[{itemId:536,minAmount:1.5,maxAmount:1,"
					+ "weight:0,always:true}]"},
			{"fractional-max-amount", "[{itemId:536,minAmount:1,"
					+ "maxAmount:1.5,weight:0,always:true}]"},
			{"zero-min-amount", "[{itemId:536,minAmount:0,maxAmount:1,"
					+ "weight:0,always:true}]"},
			{"amount-above-range", "[{itemId:536,minAmount:1,"
					+ "maxAmount:1000001,weight:0,always:true}]"},
			{"min-above-max", "[{itemId:536,minAmount:5,maxAmount:2,"
					+ "weight:0,always:true}]"},
			{"undefined-item", "[{itemId:994,minAmount:1,maxAmount:1,"
					+ "weight:0,always:true}]"},
			{"zero-item", "[{itemId:0,minAmount:1,maxAmount:1,weight:0,"
					+ "always:true}]"},
			{"negative-item", "[{itemId:-1,minAmount:1,maxAmount:1,"
					+ "weight:0,always:true}]"},
			{"item-above-range", "[{itemId:15000,minAmount:1,maxAmount:1,"
					+ "weight:0,always:true}]"},
			{"negative-weight", "[{itemId:536,minAmount:1,maxAmount:1,"
					+ "weight:-1,always:false}]"},
			{"weight-above-range", "[{itemId:536,minAmount:1,maxAmount:1,"
					+ "weight:1000001,always:false}]"},
			{"fractional-weight", "[{itemId:536,minAmount:1,maxAmount:1,"
					+ "weight:1.5,always:false}]"},
			{"non-boolean-always", "[{itemId:536,minAmount:1,maxAmount:1,"
					+ "weight:0,always:'yes'}]"},
			{"weight-sum-overflow", "[{itemId:536,minAmount:1,maxAmount:1,"
					+ "weight:600000,always:false},{itemId:995,minAmount:1,"
					+ "maxAmount:1,weight:600000,always:false}]"}
		};
		for (String[] row : invalid) {
			reject(table(row[1]), stateBefore, row[0]);
		}
		assertTrue(GameEngine.itemHandler.items.isEmpty());
		assertEquals(stateBefore,
				service.rngStateForTesting(encounter.token()));
	}

	@Test
	public void identityBudgetPreflightRejectsWholeTableWithoutRngOrItems() {
		Value table = table("[{itemId:536,minAmount:100,maxAmount:100,"
				+ "weight:0,always:true},{itemId:536,minAmount:100,"
				+ "maxAmount:100,weight:0,always:true}]");
		long stateBefore = service.rngStateForTesting(encounter.token());
		assertEquals(0, encounter.rollDrops(Wp5PlayerSupport.scripted(owner),
				X, Y, 0, 50, table).length());
		assertEquals(stateBefore,
				service.rngStateForTesting(encounter.token()));
		assertTrue(GameEngine.itemHandler.items.isEmpty());
	}

	@Test
	public void tableFittingTheRemainingIdentityBudgetSucceedsAndExhaustsIt() {
		Value full = table("[{itemId:536,minAmount:128,maxAmount:128,"
				+ "weight:0,always:true}]");
		ScriptArray results = encounter.rollDrops(
				Wp5PlayerSupport.scripted(owner), X, Y, 0, 50, full);
		assertEquals(1, results.length());
		ScriptDropResult result = (ScriptDropResult) results.get(0);
		assertEquals(128, result.amount());
		assertEquals(128, ((ScriptGroundItemHandle) result.groundItems()
				.get(0)).identityCount());
		assertEquals(128, GameEngine.itemHandler.items.size());

		Value beyond = table("[{itemId:536,minAmount:2,maxAmount:2,"
				+ "weight:0,always:true}]");
		long stateBefore = service.rngStateForTesting(encounter.token());
		assertEquals(0, encounter.rollDrops(Wp5PlayerSupport.scripted(owner),
				X, Y, 0, 50, beyond).length());
		assertEquals(stateBefore,
				service.rngStateForTesting(encounter.token()));
		assertEquals(128, GameEngine.itemHandler.items.size());
	}

	@Test
	public void injectedStagingFailureRemovesEveryStagedIdentityAndPreservesRng() {
		Value table = table("[{itemId:536,minAmount:1,maxAmount:1,weight:0,"
				+ "always:true},{itemId:995,minAmount:1,maxAmount:1,"
				+ "weight:0,always:true}]");
		long stateBefore = service.rngStateForTesting(encounter.token());
		service.failStagingForTesting(1);
		assertEquals(0, encounter.rollDrops(Wp5PlayerSupport.scripted(owner),
				X, Y, 0, 50, table).length());
		assertEquals(stateBefore,
				service.rngStateForTesting(encounter.token()));
		assertTrue(GameEngine.itemHandler.items.isEmpty());
		service.failStagingForTesting(0);
		assertEquals(0, encounter.rollDrops(Wp5PlayerSupport.scripted(owner),
				X, Y, 0, 50, table).length());
		assertEquals(stateBefore,
				service.rngStateForTesting(encounter.token()));
		assertTrue(GameEngine.itemHandler.items.isEmpty());
	}

	@Test
	public void injectedDetachFailureRemovesEveryStagedIdentityAndPreservesRng() {
		Value table = table("[{itemId:536,minAmount:1,maxAmount:1,weight:0,"
				+ "always:true},{itemId:995,minAmount:1,maxAmount:1,"
				+ "weight:0,always:true}]");
		long stateBefore = service.rngStateForTesting(encounter.token());
		service.failDetachForTesting();
		assertEquals(0, encounter.rollDrops(Wp5PlayerSupport.scripted(owner),
				X, Y, 0, 50, table).length());
		assertEquals(stateBefore,
				service.rngStateForTesting(encounter.token()));
		assertTrue(GameEngine.itemHandler.items.isEmpty());
	}

	@Test
	public void rollDropsRequiresAnExactCurrentParticipantInArea() throws Exception {
		Player observer = Wp5PlayerSupport.additionalPlayer(90);
		try {
			Value table = table("[{itemId:536,minAmount:1,maxAmount:1,"
					+ "weight:0,always:true}]");
			long stateBefore = service.rngStateForTesting(encounter.token());
			assertEquals(0, encounter.rollDrops(
					Wp5PlayerSupport.scripted(observer), X, Y, 0, 50,
					table).length());
			assertEquals(0, encounter.rollDrops(
					Wp5PlayerSupport.scripted(owner), X + 10, Y, 0, 50,
					table).length());
			assertEquals(stateBefore,
					service.rngStateForTesting(encounter.token()));
			assertTrue(GameEngine.itemHandler.items.isEmpty());
		} finally {
			Wp5PlayerSupport.cleanup(observer);
		}
	}

	@Test
	public void handleRollsAdvanceTheSameEncounterRngAndRespectNoAdvanceRules() {
		long stateBefore = service.rngStateForTesting(encounter.token());
		ScriptEncounterRng expected = new ScriptEncounterRng(stateBefore);
		assertEquals(expected.nextInt(100), encounter.nextInt(100));
		assertEquals(expected.nextInt(1), encounter.nextInt(1));
		assertEquals(expected.state(),
				service.rngStateForTesting(encounter.token()));
		assertEquals(-1, encounter.nextInt(0));
		assertEquals(-1, encounter.nextInt(1000001));
		assertFalse(encounter.chance(11, 10));
		assertFalse(encounter.chance(0, 10));
		assertTrue(encounter.chance(10, 10));
		assertEquals(expected.state(),
				service.rngStateForTesting(encounter.token()));
	}

	@Test
	public void distanceIsChebyshevAndRejectsInvalidPositions() {
		assertEquals(3, encounter.distance(
				position(X, Y), position(X + 2, Y - 3)));
		assertEquals(0, encounter.distance(
				position(X, Y), position(X, Y)));
		assertEquals(-1, encounter.distance(
				position(X, Y), position(X, Y, 1)));
		assertEquals(-1, encounter.distance(null, position(X, Y)));
	}

	@Test
	public void spatialQueriesReflectAuthoritativeClippingAndReservation()
			throws Exception {
		assertNotNull(encounter.replaceObject(X, Y, 0, -1, -1, -1,
				2213, 10, 0));
		assertTrue(encounter.isWalkable(X - 1, Y, 0));
		assertFalse(encounter.isWalkable(X, Y, 0));
		assertFalse(encounter.isWalkable(X + 1, Y, 0));
		assertFalse(encounter.isWalkable(X + 5, Y, 0));
		assertTrue(encounter.hasProjectilePath(X - 1, Y, X - 1, Y + 1, 0));
		assertFalse(encounter.hasProjectilePath(X - 1, Y, X, Y, 0));
		assertFalse(encounter.hasProjectilePath(X, Y, X + 5, Y, 0));
	}

	private void reject(Value table, long stateBefore, String label) {
		assertEquals(label, 0, encounter.rollDrops(
				Wp5PlayerSupport.scripted(owner), X, Y, 0, 50,
				table).length());
		assertEquals(label, stateBefore,
				service.rngStateForTesting(encounter.token()));
		assertTrue(label, GameEngine.itemHandler.items.isEmpty());
	}

	private static Value table(String source) {
		return context.eval("js", "(" + source + ")");
	}

	private static ScriptedPosition position(int x, int y) {
		return position(x, y, 0);
	}

	private static ScriptedPosition position(int x, int y, int plane) {
		return new ScriptedPosition(x, y, plane);
	}
}
