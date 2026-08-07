package com.rs2.script;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.apollo.cache.def.ItemDefinition;
import org.apollo.cache.def.ObjectDefinition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.Constants;
import com.rs2.GameEngine;
import com.rs2.event.CycleEventHandler;
import com.rs2.game.objects.ObjectsActions;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.resource.ScriptResourceRuntime;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.world.clip.Region;
import com.rs2.world.clip.RegionFactory;

/**
 * Phase 5 WP8 production gathering E2E.
 *
 * <p>Proves the complete gathering loop through the real object-click route:
 * skill/tool validation, the bounded per-player tick session, deterministic
 * success on the Java-owned resource RNG, the atomic item + XP reward, the
 * depletion of the authoritative object to the declared empty id through the
 * timed-object path, respawn, movement-away/logout cancellation, and reload
 * cleanup — with zero residue on every stop path. Success is made
 * deterministic by an always-success resource (numerator == denominator, so
 * the resource RNG is never advanced by the roll itself).
 */
public class ScriptGatheringResourceE2ETest {

	private static final int TREE_X = 3200;
	private static final int TREE_Y = 3200;
	private static final int TREE_ID = 1276;
	private static final int STUMP_ID = 1341;
	private static final int AXE_ID = 1351;
	private static final int LOG_ID = 1511;
	private static final int WOODCUTTING = Constants.WOODCUTTING;
	private static final long SEED = 0x1234567890abcdefL;

	private String previousContentDir;
	private ItemDefinition[] previousItems;
	private ObjectDefinition[] previousObjects;
	private Region[] previousRegions;
	private Player player;

	@Before
	public void setUp() throws Exception {
		ScriptRuntimeTestFixture.reset();
		ScriptEncounterService.installForTesting(SEED);
		ScriptResourceRuntime.installForTesting(SEED);
		previousContentDir = System.getProperty("singlescape.contentDir");
		previousItems = ItemDefinition.getDefinitions();
		previousObjects = ObjectDefinition.getDefinitions();
		Field regions = RegionFactory.class.getDeclaredField("regions");
		regions.setAccessible(true);
		previousRegions = (Region[]) regions.get(null);
		Arrays.fill(PlayerHandler.players, null);
		Wp5PlayerSupport.ensureItemDefinitions();
		Wp5PlayerSupport.ensureObjectDefinitions();
		Wp5PlayerSupport.ensureAreaRegions();
		player = Wp5PlayerSupport.player(93);
		player.absX = TREE_X;
		player.absY = TREE_Y;
		player.heightLevel = 0;
		player.tutorialProgress = 36;
		GameEngine.itemHandler.items.clear();
		GameEngine.itemHandler.resetProjectionsForTesting();
		CycleEventHandler.getSingleton().stopEvents(null);
	}

	@After
	public void restore() throws Exception {
		CycleEventHandler.getSingleton().stopEvents(player);
		if (player != null) {
			PlayerHandler.players[93] = null;
		}
		ScriptRuntimeTestFixture.reset();
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
		setDefinitions(ItemDefinition.class, previousItems);
		setDefinitions(ObjectDefinition.class, previousObjects);
		Field regions = RegionFactory.class.getDeclaredField("regions");
		regions.setAccessible(true);
		regions.set(null, previousRegions);
	}

	@Test
	public void fullHarvestLoopCommitsItemsAndXpAndDepletesThenRespawns()
			throws Exception {
		activate("always-success", 4, 4, 4);
		giveToolAndLevel(WOODCUTTING, 1);

		ObjectsActions actions = new ObjectsActions(player);
		player.objectX = TREE_X;
		player.objectY = TREE_Y;
		actions.firstClickObject(TREE_ID, TREE_X, TREE_Y,
				com.rs2.world.WorldObjectService.getInstance()
						.resolve(TREE_X, TREE_Y, 0).getObject());

		assertTrue("the exact resource route must consume the click",
				actions.wasLastClickHandledByScript());
		long token = ScriptResourceRuntime.getInstance().sessionToken(player);
		assertTrue("a session must open", token != 0L);

		// The 4-tick interval means the first attempt fires after four game
		// cycles. Always-success rolls without advancing the resource RNG.
		for (int index = 0; index < 4; index++) {
			tick();
		}

		assertEquals("the harvest must commit exactly one log", 1,
				countItem(player, LOG_ID));
		assertEquals("the harvest must grant exactly 25 woodcutting XP",
				25, player.playerXP[WOODCUTTING]);
		assertTrue("the session closes after a successful harvest",
				ScriptResourceRuntime.getInstance().sessionToken(player) == 0L);
		assertTrue("the resource must deplete to the stump",
				ScriptResourceRuntime.getInstance().isDepleted(
						ScriptResourceRuntime.getInstance().resourceForTesting(),
						TREE_X, TREE_Y, 0));
		assertEquals(1, ScriptResourceRuntime.getInstance()
				.depletedObjectCount());

		// Advance past the respawn; ObjectManager restores the tree.
		for (int index = 0; index < 5; index++) {
			GameEngine.objectManager.process();
		}
		ScriptResourceRuntime.getInstance().processGameTick();
		assertFalse("the resource must respawn after the interval",
				ScriptResourceRuntime.getInstance().isDepleted(
						ScriptResourceRuntime.getInstance().resourceForTesting(),
						TREE_X, TREE_Y, 0));
	}

	@Test
	public void insufficientLevelAndMissingToolRejectWithoutOpeningASession()
			throws Exception {
		activate("always-success", 4, 4, 4);
		player.playerLevel[WOODCUTTING] = 1;

		ObjectsActions actions = new ObjectsActions(player);
		player.objectX = TREE_X;
		player.objectY = TREE_Y;
		actions.firstClickObject(TREE_ID, TREE_X, TREE_Y,
				com.rs2.world.WorldObjectService.getInstance()
						.resolve(TREE_X, TREE_Y, 0).getObject());
		assertTrue("the resource route is authoritative even on rejection",
				actions.wasLastClickHandledByScript());
		assertTrue("no session opens without a tool",
				ScriptResourceRuntime.getInstance().sessionToken(player) == 0L);

		// Tool but too low a level.
		player.getItemAssistant().addItem(AXE_ID, 1);
		player.playerLevel[WOODCUTTING] = 0;
		actions = new ObjectsActions(player);
		actions.firstClickObject(TREE_ID, TREE_X, TREE_Y,
				com.rs2.world.WorldObjectService.getInstance()
						.resolve(TREE_X, TREE_Y, 0).getObject());
		assertTrue(actions.wasLastClickHandledByScript());
		assertTrue("no session opens below the required level",
				ScriptResourceRuntime.getInstance().sessionToken(player) == 0L);
		assertEquals("no reward without a session", 0,
				countItem(player, LOG_ID));
	}

	@Test
	public void movementAwayCancelsTheSessionWithoutReward() throws Exception {
		activate("always-success", 4, 4, 4);
		giveToolAndLevel(WOODCUTTING, 1);
		openSession();

		// The player walks far away before the first attempt tick fires.
		player.absX = TREE_X + 100;
		player.absY = TREE_Y + 100;
		for (int index = 0; index < 4; index++) {
			tick();
		}

		assertTrue("movement away must cancel the session",
				ScriptResourceRuntime.getInstance().sessionToken(player) == 0L);
		assertEquals("no reward after movement-away", 0,
				countItem(player, LOG_ID));
		assertFalse("movement-away must not deplete the resource",
				ScriptResourceRuntime.getInstance().isDepleted(
						ScriptResourceRuntime.getInstance().resourceForTesting(),
						TREE_X, TREE_Y, 0));
	}

	@Test
	public void logoutCancelsTheSessionWithZeroResidue() throws Exception {
		activate("always-success", 4, 4, 4);
		giveToolAndLevel(WOODCUTTING, 1);
		openSession();
		assertEquals(1, ScriptResourceRuntime.getInstance().sessionCount());

		ScriptLifecycleService.getInstance().onPlayerRemoved(player);

		assertEquals(0, ScriptResourceRuntime.getInstance().sessionCount());
		assertTrue("logout must clear the player session token",
				player.scriptResourceSessionToken == 0L);
	}

	@Test
	public void depletedResourceRejectsFurtherClicksUntilRespawn()
			throws Exception {
		activate("always-success", 4, 4, 4);
		giveToolAndLevel(WOODCUTTING, 1);
		harvestToDepletion();
		assertTrue(ScriptResourceRuntime.getInstance().isDepleted(
				ScriptResourceRuntime.getInstance().resourceForTesting(), TREE_X,
				TREE_Y, 0));

		ObjectsActions actions = new ObjectsActions(player);
		player.objectX = TREE_X;
		player.objectY = TREE_Y;
		actions.firstClickObject(TREE_ID, TREE_X, TREE_Y,
				com.rs2.world.WorldObjectService.getInstance()
						.resolve(TREE_X, TREE_Y, 0).getObject());
		assertTrue("the resource route consumes the click while depleted",
				actions.wasLastClickHandledByScript());
		assertTrue("no session opens on a depleted resource",
				ScriptResourceRuntime.getInstance().sessionToken(player) == 0L);
		assertEquals("no second reward while depleted", 1,
				countItem(player, LOG_ID));
	}

	@Test
	public void reloadClosesGenerationSessionsAndKeepsRejectedReloadIntact()
			throws Exception {
		File root = activate("always-success", 4, 4, 4);
		giveToolAndLevel(WOODCUTTING, 1);
		openSession();
		assertEquals(1, ScriptResourceRuntime.getInstance().sessionCount());

		// Rejected reload keeps the active resource and its session.
		Files.write(new File(root, "loader.js").toPath(),
				"this is not valid javascript !!!"
						.getBytes(StandardCharsets.UTF_8));
		ScriptHost.getInstance().reload();
		assertEquals(1, ScriptResourceRuntime.getInstance().sessionCount());

		// Successful reload of an empty candidate closes the generation.
		Files.write(new File(root, "loader.js").toPath(),
				"onCommand('empty', function () {});"
						.getBytes(StandardCharsets.UTF_8));
		ScriptHost.getInstance().reload();
		assertEquals("a retired generation's sessions must close",
				0, ScriptResourceRuntime.getInstance().sessionCount());
	}

	@Test
	public void alwaysMissRollNeverAdvancesTheResourceRngAndKeepsTheSession()
			throws Exception {
		activate("always-miss", 4, 0, 4);
		giveToolAndLevel(WOODCUTTING, 1);
		openSession();
		long token = ScriptResourceRuntime.getInstance().sessionToken(player);
		long rngBefore = ScriptResourceRuntime.getInstance()
				.resourceRngStateForTesting(token);

		for (int index = 0; index < 4; index++) {
			tick();
		}

		assertTrue("the session stays open on a miss",
				ScriptResourceRuntime.getInstance().sessionToken(player) != 0L);
		assertEquals("no reward on a miss", 0, countItem(player, LOG_ID));
		assertEquals("a zero numerator must not advance the resource RNG",
				rngBefore, ScriptResourceRuntime.getInstance()
						.resourceRngStateForTesting(token));
		assertFalse("a miss must not deplete the resource",
				ScriptResourceRuntime.getInstance().isDepleted(
						ScriptResourceRuntime.getInstance().resourceForTesting(),
						TREE_X, TREE_Y, 0));
	}

	@Test
	public void completeObjectClickConsumesTheResourceWithoutLegacyWoodcutting()
			throws Exception {
		activate("always-success", 4, 4, 4);
		giveToolAndLevel(WOODCUTTING, 1);
		// The registered tree (1276) is also a legacy tree. Driving the real
		// ClickObject.completeObjectClick path (which runs the legacy
		// startWoodcutting pre-dispatch BEFORE the scripted dispatch) must
		// consume the resource route and NOT start legacy woodcutting.
		player.clickDelay = 0;
		player.objectX = TREE_X;
		player.objectY = TREE_Y;
		player.objectId = TREE_ID;
		player.heightLevel = 0;
		com.rs2.net.packets.impl.ClickObject click = new com.rs2.net.packets.impl.ClickObject();
		click.completeObjectClick(player, 1,
				com.rs2.world.WorldObjectService.getInstance()
						.resolve(TREE_X, TREE_Y, 0).getObject());

		assertTrue("the exact resource route must consume the click",
				ScriptResourceRuntime.getInstance().sessionToken(player) != 0L);
		assertFalse("legacy woodcutting must not start", player.isWoodcutting);
		assertEquals("no legacy log reward", 0, countItem(player, LOG_ID));
	}

	@Test
	public void unregisteredTreeIdKeepsLegacyWoodcuttingWithoutOpeningASession()
			throws Exception {
		activate("always-success", 4, 4, 4);
		giveToolAndLevel(WOODCUTTING, 1);
		// 1278 is another normal (level-1) tree id; only 1276 is registered.
		final int unregisteredTree = 1278;
		Region.addObject(unregisteredTree, TREE_X, TREE_Y + 1, 0, 10, 0, false);

		player.clickDelay = 0;
		player.objectX = TREE_X;
		player.objectY = TREE_Y + 1;
		player.objectId = unregisteredTree;
		player.heightLevel = 0;
		com.rs2.net.packets.impl.ClickObject click =
				new com.rs2.net.packets.impl.ClickObject();
		click.completeObjectClick(player, 1,
				com.rs2.world.WorldObjectService.getInstance()
						.resolve(TREE_X, TREE_Y + 1, 0).getObject());

		assertTrue("no gathering session opens for an unregistered object id",
				ScriptResourceRuntime.getInstance().sessionToken(player) == 0L);
		assertTrue("legacy woodcutting must start for the unregistered tree",
				player.isWoodcutting);
	}

	@Test
	public void inventoryFullRollsBackTheRewardAndClosesTheSession()
			throws Exception {
		activate("always-success", 4, 4, 4);
		giveToolAndLevel(WOODCUTTING, 1);
		// Fill the inventory so the log cannot be added.
		for (int slot = 0; slot < player.playerItems.length; slot++) {
			if (player.playerItems[slot] <= 0) {
				player.playerItems[slot] = AXE_ID + 1;
				player.playerItemsN[slot] = 1;
			}
		}
		int[] itemsBefore = player.playerItems.clone();
		int[] amountsBefore = player.playerItemsN.clone();
		double weightBefore = player.weight;
		int xpBefore = player.playerXP[WOODCUTTING];
		int levelBefore = player.playerLevel[WOODCUTTING];

		openSession();
		for (int index = 0; index < 4; index++) {
			tick();
		}

		assertTrue("the session must close after a failed reward",
				ScriptResourceRuntime.getInstance().sessionToken(player) == 0L);
		assertArrayEquals("items must be exactly restored", itemsBefore,
				player.playerItems);
		assertArrayEquals("amounts must be exactly restored", amountsBefore,
				player.playerItemsN);
		assertEquals("weight must be exactly restored", weightBefore,
				player.weight, 0.0d);
		assertEquals("XP must be exactly restored", xpBefore,
				player.playerXP[WOODCUTTING]);
		assertEquals("level must be exactly restored", levelBefore,
				player.playerLevel[WOODCUTTING]);
	}

	@Test
	public void deathCancelsTheSessionImmediatelyWithoutReward()
			throws Exception {
		activate("always-success", 4, 4, 4);
		giveToolAndLevel(WOODCUTTING, 1);
		openSession();
		assertEquals(1, ScriptResourceRuntime.getInstance().sessionCount());

		ScriptLifecycleService.getInstance().beginPlayerDeath(player);

		assertEquals("death must cancel the session immediately",
				0, ScriptResourceRuntime.getInstance().sessionCount());
		assertTrue("death must clear the player session token",
				player.scriptResourceSessionToken == 0L);
		assertEquals("no reward after death", 0, countItem(player, LOG_ID));
	}

	@Test
	public void objectReplacementMidSessionCancelsTheSession()
			throws Exception {
		activate("always-success", 4, 4, 4);
		giveToolAndLevel(WOODCUTTING, 1);
		openSession();

		// An external writer replaces the tile object mid-session (a legacy
		// timed stump, exactly the legacy depletion path). The session must
		// stop on the next attempt instead of granting from a non-resource.
		new com.rs2.game.objects.Object(STUMP_ID, TREE_X, TREE_Y, 0, 0, 10,
				TREE_ID, 100);
		GameEngine.objectManager.process();
		ScriptResourceRuntime.getInstance().processGameTick();

		for (int index = 0; index < 4; index++) {
			tick();
		}

		assertTrue("the session must close when the object is replaced",
				ScriptResourceRuntime.getInstance().sessionToken(player) == 0L);
		assertEquals("no reward from a replaced object", 0,
				countItem(player, LOG_ID));
	}

	@Test
	public void schedulerFailureCancelsTheSessionWithZeroResidue()
			throws Exception {
		activate("always-success", 4, 4, 4);
		giveToolAndLevel(WOODCUTTING, 1);
		openSession();
		long token = ScriptResourceRuntime.getInstance().sessionToken(player);
		assertEquals(1, ScriptResourceRuntime.getInstance().sessionCount());

		// Simulate a runtime failure: force the resource RNG into an invalid
		// state so the next attempt throws, which cancels the session through
		// the scheduler failure continuation.
		ScriptResourceRuntime.getInstance()
				.failNextAttemptForTesting(token);
		for (int index = 0; index < 4; index++) {
			tick();
		}

		assertEquals("a runtime failure must close the session",
				0, ScriptResourceRuntime.getInstance().sessionCount());
		assertTrue(player.scriptResourceSessionToken == 0L);
	}

	// ─── Helpers ────────────────────────────────────────────────────────────

	private File activate(String id, int interval, int numerator,
			int denominator) throws Exception {
		Path root = Files.createTempDirectory("script-gathering-resource");
		Files.write(root.resolve("loader.js"), (
				"defineGatheringResource({id:'" + id + "',name:'Tree',"
						+ "objectId:" + TREE_ID + ",action:'first',"
						+ "skill:'woodcutting',level:1,"
						+ "tools:[{itemId:" + AXE_ID + "}],"
						+ "animation:879,intervalTicks:" + interval + ","
						+ "successChance:{numerator:" + numerator
						+ ",denominator:" + denominator + "},"
						+ "rewards:[{itemId:" + LOG_ID + ",amount:1}],"
						+ "experience:25,depletedObjectId:" + STUMP_ID + ","
						+ "respawnTicks:4});")
						.getBytes(StandardCharsets.UTF_8));
		System.setProperty("singlescape.contentDir",
				root.toFile().getAbsolutePath());
		ScriptHost.getInstance().reload();
		com.rs2.script.route.ExecutableRouteRecord route =
				ScriptHost.getInstance().readActiveRegistry(
						state -> com.rs2.script.registries
								.ObjectHandlerRegistry.getRecord(state, TREE_ID,
										"first"));
		assertNotNull("tree must register an exact object route", route);
		assertTrue("the resource route must be a Java host consumer",
				!route.isGuest());
		Region.addObject(TREE_ID, TREE_X, TREE_Y, 0, 10, 0, false);
		return root.toFile();
	}

	private void giveToolAndLevel(int skill, int level) {
		player.getItemAssistant().addItem(AXE_ID, 1);
		player.playerLevel[skill] = level;
		player.playerXP[skill] = 0;
	}

	private void openSession() {
		ObjectsActions actions = new ObjectsActions(player);
		player.objectX = TREE_X;
		player.objectY = TREE_Y;
		actions.firstClickObject(TREE_ID, TREE_X, TREE_Y,
				com.rs2.world.WorldObjectService.getInstance()
						.resolve(TREE_X, TREE_Y, 0).getObject());
		assertTrue("the resource route must consume the click",
				actions.wasLastClickHandledByScript());
		assertTrue("a session must open",
				ScriptResourceRuntime.getInstance().sessionToken(player) != 0L);
	}

	private void harvestToDepletion() {
		openSession();
		for (int index = 0; index < 4; index++) {
			tick();
		}
	}

	private static void tick() {
		ScriptLifecycleService.getInstance().processGameTick();
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

	private static void setDefinitions(Class<?> definitionType, Object value)
			throws Exception {
		Field field = definitionType.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, value);
	}

}
