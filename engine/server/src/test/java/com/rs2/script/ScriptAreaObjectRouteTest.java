package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apollo.cache.def.ItemDefinition;
import org.apollo.cache.def.ObjectDefinition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.GameEngine;
import com.rs2.game.items.GroundItem;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.npcs.NpcList;
import com.rs2.game.objects.Objects;
import com.rs2.game.objects.ObjectsActions;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.area.ScriptAreaRuntime;
import com.rs2.world.ResolvedWorldObject;
import com.rs2.world.WorldObjectService;
import com.rs2.world.clip.Region;
import com.rs2.world.clip.RegionFactory;

/**
 * Proves the exact tile-position object-drop routes: the acting player's
 * click on the exact area projection rolls the named table once through the
 * area RNG, the one-shot claim consumes the binding, an equal-id cache
 * object at the same tile after the area closed has no owner-route key and
 * falls through, and an equal-id legacy object at another tile never
 * triggers the area route.
 */
public class ScriptAreaObjectRouteTest {

	private static final int CHEST_X = 2835;
	private static final int CHEST_Y = 9640;
	private static final int LEGACY_X = 2838;
	private static final int LEGACY_Y = 9640;

	private String previousContentDir;
	private NpcList[] previousNpcList;
	private ItemDefinition[] previousItems;
	private ObjectDefinition[] previousObjects;
	private Region[] previousRegions;
	private Player player;
	private NpcHandler npcHandler;

	@Before
	public void setUp() throws Exception {
		ScriptRuntimeTestFixture.reset();
		com.rs2.script.world.ScriptEncounterService.installForTesting(9L);
		previousContentDir = System.getProperty("singlescape.contentDir");
		previousNpcList = NpcHandler.NpcList.clone();
		previousItems = ItemDefinition.getDefinitions();
		previousObjects = ObjectDefinition.getDefinitions();
		Field regions = RegionFactory.class.getDeclaredField("regions");
		regions.setAccessible(true);
		previousRegions = (Region[]) regions.get(null);
		npcHandler = new NpcHandler();
		NpcHandler.NpcList = new NpcList[NpcHandler.maxListedNPCs];
		NpcList npc = new NpcList(153);
		npc.npcName = "test_dragon";
		NpcHandler.NpcList[0] = npc;
		Arrays.fill(NpcHandler.npcs, null);
		Wp5PlayerSupport.ensureItemDefinitions();
		Wp5PlayerSupport.ensureObjectDefinitions();
		// player() replaces the region table; the Crandor regions must be
		// appended afterwards so the layered object transaction can write.
		player = Wp5PlayerSupport.player(92);
		Wp5PlayerSupport.ensureAreaRegions();
		player.absX = CHEST_X;
		player.absY = CHEST_Y;
		player.heightLevel = 0;
	}

	@After
	public void restore() throws Exception {
		if (player != null) {
			PlayerHandler.players[92] = null;
		}
		ScriptRuntimeTestFixture.reset();
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
		System.arraycopy(previousNpcList, 0, NpcHandler.NpcList, 0,
				previousNpcList.length);
		setDefinitions(ItemDefinition.class, previousItems);
		setDefinitions(ObjectDefinition.class, previousObjects);
		Field regions = RegionFactory.class.getDeclaredField("regions");
		regions.setAccessible(true);
		regions.set(null, previousRegions);
	}

	@Test
	public void exactProjectionClickRollsOnceAndConsumesTheBinding()
			throws Exception {
		File root = activate();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"object-area");
		long rngBefore = ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token);
		ResolvedWorldObject chest = WorldObjectService.getInstance()
				.resolve(CHEST_X, CHEST_Y, 0);
		assertNotNull(chest);
		assertEquals(2213, chest.getObjectId());

		ObjectsActions actions = new ObjectsActions(player);
		player.objectX = CHEST_X;
		player.objectY = CHEST_Y;
		actions.firstClickObject(2213, CHEST_X, CHEST_Y,
				chest.getObject());
		assertTrue("the exact route must consume the click",
				actions.wasLastClickHandledByScript());
		assertEquals(1, groundItemsAt(995, CHEST_X, CHEST_Y).size());
		assertTrue(ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token) != rngBefore);

		// One-shot: the second click is consumed without a second roll.
		ResolvedWorldObject stillThere = WorldObjectService.getInstance()
				.resolve(CHEST_X, CHEST_Y, 0);
		assertNotNull(stillThere);
		long afterFirst = ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token);
		actions.firstClickObject(2213, CHEST_X, CHEST_Y,
				stillThere.getObject());
		assertTrue("the claimed route remains consumed",
				actions.wasLastClickHandledByScript());
		assertEquals(1, groundItemsAt(995, CHEST_X, CHEST_Y).size());
		assertEquals(afterFirst, ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token));
	}

	@Test
	public void equalIdLegacyObjectAtAnotherTileHasNoOwnerRouteKey()
			throws Exception {
		activate();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"object-area");
		long rngBefore = ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token);
		Region.addObject(2213, LEGACY_X, LEGACY_Y, 0, 10, 0, false);
		Objects legacy = Region.getObjectAt(LEGACY_X, LEGACY_Y, 0, 10);
		assertNotNull(legacy);
		player.absX = LEGACY_X;
		player.absY = LEGACY_Y;
		player.objectX = LEGACY_X;
		player.objectY = LEGACY_Y;

		ObjectsActions actions = new ObjectsActions(player);
		actions.firstClickObject(2213, LEGACY_X, LEGACY_Y, legacy);

		assertFalse("a legacy equal-id object has no owner-route key",
				actions.wasLastClickHandledByScript());
		assertTrue(GameEngine.itemHandler.items.isEmpty());
		assertEquals("the legacy click must not touch the area RNG",
				rngBefore, ScriptAreaRuntime.getInstance()
						.areaRngStateForTesting(token));
	}

	@Test
	public void staleTileRouteFallsThroughAfterTheAreaCloses()
			throws Exception {
		File root = activate();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"object-area");
		ResolvedWorldObject chest = WorldObjectService.getInstance()
				.resolve(CHEST_X, CHEST_Y, 0);
		assertNotNull(chest);

		Files.write(new File(root, "loader.js").toPath(),
				"onCommand('no-areas', function () {});"
						.getBytes(StandardCharsets.UTF_8));
		ScriptHost.getInstance().reload();
		assertEquals(0, ScriptAreaRuntime.getInstance().sessionCount());

		ObjectsActions actions = new ObjectsActions(player);
		actions.firstClickObject(2213, CHEST_X, CHEST_Y,
				chest.getObject());
		assertFalse("the retired projection has no live owner-route key",
				actions.wasLastClickHandledByScript());
		assertTrue(GameEngine.itemHandler.items.isEmpty());
	}

	@Test
	public void clickAtAnotherTileWithTheProjectedIdFallsThrough()
			throws Exception {
		activate();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"object-area");
		long rngBefore = ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token);
		ResolvedWorldObject chest = WorldObjectService.getInstance()
				.resolve(CHEST_X, CHEST_Y, 0);
		assertNotNull(chest);
		player.absX = LEGACY_X;
		player.absY = LEGACY_Y;
		player.objectX = LEGACY_X;
		player.objectY = LEGACY_Y;

		ObjectsActions actions = new ObjectsActions(player);
		actions.firstClickObject(2213, LEGACY_X, LEGACY_Y,
				chest.getObject());

		assertFalse("a click off the exact tile has no owner-route key",
				actions.wasLastClickHandledByScript());
		assertTrue(GameEngine.itemHandler.items.isEmpty());
		assertEquals("the off-tile click must not touch the area RNG",
				rngBefore, ScriptAreaRuntime.getInstance()
						.areaRngStateForTesting(token));
	}

	@Test
	public void clickWithWrongIdAtTheExactTileFallsThrough() throws Exception {
		activate();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"object-area");
		long rngBefore = ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token);
		ResolvedWorldObject chest = WorldObjectService.getInstance()
				.resolve(CHEST_X, CHEST_Y, 0);
		assertNotNull(chest);
		player.objectX = CHEST_X;
		player.objectY = CHEST_Y;

		ObjectsActions actions = new ObjectsActions(player);
		actions.firstClickObject(409, CHEST_X, CHEST_Y, chest.getObject());

		assertFalse("a wrong-id click has no owner-route key",
				actions.wasLastClickHandledByScript());
		assertTrue(GameEngine.itemHandler.items.isEmpty());
		assertEquals("the wrong-id click must not touch the area RNG",
				rngBefore, ScriptAreaRuntime.getInstance()
						.areaRngStateForTesting(token));
	}

	@Test
	public void missingDropTableConsumesTheClaimWithoutDoubleDrop()
			throws Exception {
		activate();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"object-area");
		long rngBefore = ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token);
		ResolvedWorldObject chest = WorldObjectService.getInstance()
				.resolve(CHEST_X, CHEST_Y, 0);
		assertNotNull(chest);
		player.objectX = CHEST_X;
		player.objectY = CHEST_Y;
		ScriptAreaRuntime.getInstance().failNextDropTableLookupForTesting();

		ObjectsActions actions = new ObjectsActions(player);
		actions.firstClickObject(2213, CHEST_X, CHEST_Y,
				chest.getObject());

		assertTrue("a missing table still consumes the exact claim",
				actions.wasLastClickHandledByScript());
		assertTrue(GameEngine.itemHandler.items.isEmpty());
		assertEquals("a missing table must not advance the area RNG",
				rngBefore, ScriptAreaRuntime.getInstance()
						.areaRngStateForTesting(token));

		// One-shot: the second click cannot double-drop.
		ResolvedWorldObject stillThere = WorldObjectService.getInstance()
				.resolve(CHEST_X, CHEST_Y, 0);
		assertNotNull(stillThere);
		actions.firstClickObject(2213, CHEST_X, CHEST_Y,
				stillThere.getObject());
		assertTrue("the claimed route remains consumed",
				actions.wasLastClickHandledByScript());
		assertTrue(GameEngine.itemHandler.items.isEmpty());
		assertEquals(rngBefore, ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token));
	}

	@Test
	public void injectedDetachFailureConsumesTheClaimAndRemovesStaged()
			throws Exception {
		activate();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"object-area");
		long rngBefore = ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token);
		ResolvedWorldObject chest = WorldObjectService.getInstance()
				.resolve(CHEST_X, CHEST_Y, 0);
		assertNotNull(chest);
		player.objectX = CHEST_X;
		player.objectY = CHEST_Y;
		ScriptAreaRuntime.getInstance().failNextDetachForTesting();

		ObjectsActions actions = new ObjectsActions(player);
		actions.firstClickObject(2213, CHEST_X, CHEST_Y,
				chest.getObject());

		assertTrue("a failed detach still consumes the exact claim",
				actions.wasLastClickHandledByScript());
		assertTrue("a failed detach must remove every staged identity",
				GameEngine.itemHandler.items.isEmpty());
		assertEquals("a failed detach must not advance the area RNG",
				rngBefore, ScriptAreaRuntime.getInstance()
						.areaRngStateForTesting(token));

		// One-shot: the second click cannot double-drop.
		ResolvedWorldObject stillThere = WorldObjectService.getInstance()
				.resolve(CHEST_X, CHEST_Y, 0);
		assertNotNull(stillThere);
		actions.firstClickObject(2213, CHEST_X, CHEST_Y,
				stillThere.getObject());
		assertTrue("the claimed route remains consumed",
				actions.wasLastClickHandledByScript());
		assertTrue(GameEngine.itemHandler.items.isEmpty());
		assertEquals(rngBefore, ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token));
	}

	private File activate() throws Exception {
		Path root = Files.createTempDirectory("script-area-object");
		Files.write(root.resolve("loader.js"), (
				"defineDropTable({id:'chest_loot',entries:[{itemId:995,"
						+ "minAmount:1,maxAmount:1,weight:0,always:true}]});"
						+ "defineArea({id:'object-area',name:'Object Area',"
						+ "bounds:{minX:2830,minY:9630,maxX:2850,"
						+ "maxY:9640,plane:0},"
						+ "npcs:[],"
						+ "objects:[{key:'chest',objectId:2213,x:2835,"
						+ "y:9640,drops:[{action:'first',"
						+ "dropTable:'chest_loot',dropPolicy:'public'}]}],"
						+ "shops:[],quests:[],bosses:[],raids:[]});")
						.getBytes(StandardCharsets.UTF_8));
		System.setProperty("singlescape.contentDir",
				root.toFile().getAbsolutePath());
		ScriptHost.getInstance().reload();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"object-area");
		assertTrue("the area must activate", token > 0L);
		return root.toFile();
	}

	private static List<GroundItem> groundItemsAt(int itemId, int x, int y) {
		List<GroundItem> matches = new ArrayList<GroundItem>();
		for (GroundItem item : GameEngine.itemHandler.items) {
			if (item != null && item.getItemId() == itemId
					&& item.getItemX() == x && item.getItemY() == y) {
				matches.add(item);
			}
		}
		return matches;
	}

	private static void setDefinitions(Class<?> definitionType, Object value)
			throws Exception {
		Field field = definitionType.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, value);
	}

}
