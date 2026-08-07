package com.rs2.script;

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
import com.rs2.event.CycleEventHandler;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.net.packets.impl.ItemOnObject;
import com.rs2.script.registries.ItemHandlerRegistry;
import com.rs2.world.clip.Region;
import com.rs2.world.clip.RegionFactory;

/**
 * Phase 2 cooking proof port: raw shrimp on cooking range 114 via
 * {@code onItemOnObject}, before {@code defineProcessingSkill}.
 */
public class ScriptCookingPortE2ETest {

	private static final int RAW_SHRIMP = 317;
	private static final int COOKED_SHRIMP = 315;
	private static final int BURNT_SHRIMP = 7954;
	private static final int COOKING_RANGE = 114;
	private static final int OTHER_RANGE = 2728;
	private static final int COOKING = Constants.COOKING;
	private static final int RANGE_X = 3200;
	private static final int RANGE_Y = 3201;

	private String previousContentDir;
	private ItemDefinition[] previousItems;
	private ObjectDefinition[] previousObjects;
	private Region[] previousRegions;
	private Player player;

	@Before
	public void setUp() throws Exception {
		ScriptRuntimeTestFixture.reset();
		previousContentDir = System.getProperty("singlescape.contentDir");
		previousItems = ItemDefinition.getDefinitions();
		previousObjects = ObjectDefinition.getDefinitions();
		Field regions = RegionFactory.class.getDeclaredField("regions");
		regions.setAccessible(true);
		previousRegions = (Region[]) regions.get(null);
		Arrays.fill(PlayerHandler.players, null);
		Wp5PlayerSupport.ensureItemDefinitions();
		Wp5PlayerSupport.ensureObjectDefinitions();
		player = Wp5PlayerSupport.player(94);
		player.absX = RANGE_X;
		player.absY = RANGE_Y;
		player.heightLevel = 0;
		player.tutorialProgress = 36;
		player.playerLevel[COOKING] = 34;
		player.playerXP[COOKING] = 0;
		CycleEventHandler.getSingleton().stopEvents(null);
	}

	@After
	public void restore() throws Exception {
		CycleEventHandler.getSingleton().stopEvents(player);
		if (player != null) {
			ScriptLifecycleService.getInstance().onPlayerRemoved(player);
			PlayerHandler.players[94] = null;
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
	public void shrimpOnRangeCooksAfterIntervalAndSuppressesLegacyStart()
			throws Exception {
		activateCookingPort();
		player.getItemAssistant().addItem(RAW_SHRIMP, 2);
		int slot = inventorySlot(RAW_SHRIMP);
		assertTrue(slot >= 0);

		assertTrue("scripted shrimp+range route must consume the use",
				ItemOnObject.executeScriptItemOnObject(
						player, RAW_SHRIMP, slot, COOKING_RANGE,
						RANGE_X, RANGE_Y));
		assertEquals("legacy cooking UI must not open for the scripted pair",
				0, player.cookingItem);
		assertFalse(player.playerIsCooking);
		assertTrue("a processing session must open",
				com.rs2.script.processing.ScriptProcessingRuntime.getInstance()
						.sessionToken(player) != 0L);
		assertEquals(2, countItem(player, RAW_SHRIMP));
		assertEquals(0, countItem(player, COOKED_SHRIMP));

		for (int index = 0; index < 4; index++) {
			ScriptLifecycleService.getInstance().processGameTick();
		}

		assertEquals(1, countItem(player, RAW_SHRIMP));
		assertEquals(1, countItem(player, COOKED_SHRIMP));
		assertEquals(0, countItem(player, BURNT_SHRIMP));
		assertEquals(30, player.playerXP[COOKING]);

		for (int index = 0; index < 4; index++) {
			ScriptLifecycleService.getInstance().processGameTick();
		}

		assertEquals(0, countItem(player, RAW_SHRIMP));
		assertEquals(2, countItem(player, COOKED_SHRIMP));
		assertEquals(60, player.playerXP[COOKING]);
	}

	@Test
	public void unregisteredRangePairFallsThroughWithoutConsuming()
			throws Exception {
		activateCookingPort();
		player.getItemAssistant().addItem(RAW_SHRIMP, 1);
		int slot = inventorySlot(RAW_SHRIMP);

		assertFalse("shrimp on an unported range must not match the script route",
				ItemOnObject.executeScriptItemOnObject(
						player, RAW_SHRIMP, slot, OTHER_RANGE,
						RANGE_X, RANGE_Y));
		assertEquals(1, countItem(player, RAW_SHRIMP));
		assertEquals(0, countItem(player, COOKED_SHRIMP));
		assertEquals(0, player.playerXP[COOKING]);
	}

	@Test
	public void compiledContentRegistersShrimpCookingRoute() throws Exception {
		File contentDir = findCompiledContent();
		assertTrue("Run pnpm build:content before Maven tests",
				contentDir.isDirectory());
		Wp5PlayerSupport.ensureNpcDefinitions();
		Wp5PlayerSupport.ensureAreaRegions();
		System.setProperty("singlescape.contentDir",
				contentDir.getAbsolutePath());
		ScriptHost.getInstance().reload();
		assertNotNull("compiled cooking module must register shrimp on range 114",
				com.rs2.script.processing.ProcessingSkillRegistry
						.get("cook-shrimp-range"));
		assertNotNull(ScriptHost.getInstance().readActiveRegistry(
				state -> ItemHandlerRegistry.getItemOnObjectRecord(
						state, RAW_SHRIMP, COOKING_RANGE)));
	}

	private void activateCookingPort() throws Exception {
		Path root = Files.createTempDirectory("script-cooking-port");
		Files.write(root.resolve("loader.js"), (
				"defineProcessingSkill({"
				+ "id:'cook-shrimp-range',name:'shrimp',skill:'cooking',"
				+ "level:1,inputItemId:317,objectId:114,productItemId:315,"
				+ "failProductItemId:7954,experience:30,animation:896,"
				+ "sound:357,intervalTicks:4,stopBurnLevel:34,"
				+ "stopBurnLevelWithGloves:30,glovesItemId:775"
				+ "});")
				.getBytes(StandardCharsets.UTF_8));
		System.setProperty("singlescape.contentDir",
				root.toFile().getAbsolutePath());
		ScriptHost.getInstance().reload();
		assertNotNull(ScriptHost.getInstance().readActiveRegistry(
				state -> ItemHandlerRegistry.getItemOnObjectRecord(
						state, RAW_SHRIMP, COOKING_RANGE)));
	}

	private int inventorySlot(int itemId) {
		for (int index = 0; index < player.playerItems.length; index++) {
			if (player.playerItems[index] == itemId + 1) {
				return index;
			}
		}
		return -1;
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

	private static File findCompiledContent() {
		File fromWorkspace = new File(System.getProperty("user.dir"),
				"content/dist");
		if (fromWorkspace.isDirectory()) {
			return fromWorkspace;
		}
		return new File(System.getProperty("user.dir"), "../../content/dist");
	}

	private static void setDefinitions(Class<?> definitionType, Object value)
			throws Exception {
		Field field = definitionType.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, value);
	}
}
