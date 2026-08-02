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
import java.util.Arrays;

import org.apollo.cache.def.ItemDefinition;
import org.apollo.cache.def.ObjectDefinition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.npcs.NpcList;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.game.shops.ShopAssistant;
import com.rs2.script.area.ScriptAreaRuntime;
import com.rs2.script.shop.ScriptShopRuntime;
import com.rs2.world.clip.Region;
import com.rs2.world.clip.RegionFactory;

/**
 * Proves the scripted-shop runtime through real engine paths: the exact
 * allocation-bound NPC route opens the shop interface, buy/sell flow
 * through the production ShopAssistant packets with declared prices and
 * stock, the bounded restock policy ticks on the game cycle, and reload
 * closes every session. Legacy static shops stay untouched.
 */
public class ScriptShopRuntimeTest {

	private static final int SHOPKEEPER_X = 2830;
	private static final int SHOPKEEPER_Y = 9630;

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
		com.rs2.script.world.ScriptEncounterService.installForTesting(11L);
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
		player = Wp5PlayerSupport.player(93);
		Wp5PlayerSupport.ensureAreaRegions();
		player.absX = SHOPKEEPER_X;
		player.absY = SHOPKEEPER_Y;
		player.heightLevel = 0;
	}

	@After
	public void restore() throws Exception {
		if (player != null) {
			PlayerHandler.players[93] = null;
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
	public void exactShopkeeperRouteOpensTheScriptedShop() throws Exception {
		activate();
		Npc shopkeeper = npcAt(SHOPKEEPER_X, SHOPKEEPER_Y);
		assertNotNull(shopkeeper);
		player.npcClickIndex = shopkeeper.npcId;
		player.clickNpcType = 153;

		com.rs2.game.npcs.NpcActions actions = new com.rs2.game.npcs.NpcActions(
				player);
		actions.firstClickNpc(153);

		assertTrue("the exact allocation route must consume the click",
				actions.wasLastClickHandledByScript());
		assertTrue(player.isShopping);
		assertEquals("island_general", player.scriptShopId);
	}

	@Test
	public void buyAndSellFlowThroughTheProductionShopAssistant()
			throws Exception {
		activate();
		openShop();
		player.getItemAssistant().addItem(995, 10000);
		ShopAssistant assistant = player.getShopAssistant();

		// Buy 3 lobsters (379) at 150 coins each.
		assertTrue(assistant.buyItem(379, 0, 3));
		assertEquals(9550, player.getItemAssistant().getItemAmount(995));
		assertEquals(3, player.getItemAssistant().getItemAmount(379));

		// The shop sells back declared items at 85% when buys is true.
		assertTrue(assistant.sellItem(379, 0, 1));
		assertEquals(9677, player.getItemAssistant().getItemAmount(995));
		assertEquals(2, player.getItemAssistant().getItemAmount(379));
	}

	@Test
	public void buyRejectsInsufficientCoinsAndOutOfStockWithoutMutation()
			throws Exception {
		activate();
		openShop();
		ShopAssistant assistant = player.getShopAssistant();

		assertFalse(assistant.buyItem(379, 0, 1));
		assertEquals(0, player.getItemAssistant().getItemAmount(379));
		assertEquals(0, player.getItemAssistant().getItemAmount(995));

		// Unknown stock item is rejected.
		player.getItemAssistant().addItem(995, 10000);
		assertFalse(assistant.buyItem(995, 0, 1));
		assertEquals(10000, player.getItemAssistant().getItemAmount(995));
	}

	@Test
	public void stockCappedSellRejectsBeyondDeclaredAmount() throws Exception {
		activate();
		openShop();
		player.getItemAssistant().addItem(995, 100000);
		ShopAssistant assistant = player.getShopAssistant();

		// Free the declared stock first — sells are rejected at full stock.
		assertTrue(assistant.buyItem(379, 0, 5));
		assertEquals(5, player.getItemAssistant().getItemAmount(379));

		// Sell back up to the declared cap of 5.
		assertTrue(assistant.sellItem(379, 0, 3));
		assertEquals(2, player.getItemAssistant().getItemAmount(379));
		assertTrue(assistant.sellItem(379, 0, 2));
		assertEquals(0, player.getItemAssistant().getItemAmount(379));

		// Shop is at declared stock again; further sells must reject.
		assertTrue(player.getItemAssistant().addItem(379, 1));
		assertFalse(assistant.sellItem(379, 0, 1));
		assertEquals(1, player.getItemAssistant().getItemAmount(379));
	}

	@Test
	public void priceMessagesShowDeclaredPrices() throws Exception {
		activate();
		openShop();
		ShopAssistant assistant = player.getShopAssistant();
		assistant.buyFromShopPrice(379);
		assistant.sellToShopPrice(379, 0);
	}

	@Test
	public void restockTicksRestoreOneUnitPerInterval() throws Exception {
		activate();
		openShop();
		player.getItemAssistant().addItem(995, 10000);
		assertTrue(player.getShopAssistant().buyItem(379, 0, 3));

		ScriptShopRuntime.getInstance().processGameTick();
		ScriptShopRuntime.getInstance().processGameTick();
		ScriptShopRuntime.getInstance().processGameTick();
		// After restockTicks the item restores toward its declared amount.
		assertTrue(player.getShopAssistant().buyItem(379, 0, 2));
		assertTrue(player.getShopAssistant().buyItem(379, 0, 1));
		assertFalse(player.getShopAssistant().buyItem(379, 0, 1));
	}

	@Test
	public void reloadClosesEveryShopSession() throws Exception {
		File root = activate();
		openShop();

		Files.write(new File(root, "loader.js").toPath(),
				"onCommand('no-shops', function () {});"
						.getBytes(StandardCharsets.UTF_8));
		ScriptHost.getInstance().reload();

		assertNull(player.scriptShopId);
	}

	@Test
	public void shadowShopkeeperCannotOpenTheShopBeforeCommit()
			throws Exception {
		ScriptAreaRuntime.getInstance().setMidHandoffHookForTesting(() -> {
			// Fresh activation: the shadow shopkeeper is staged and live,
			// but its session is not selected before the commit line.
			Npc shadow = npcAt(SHOPKEEPER_X, SHOPKEEPER_Y);
			if (shadow == null) {
				return;
			}
			player.npcClickIndex = shadow.npcId;
			player.clickNpcType = 153;
			new com.rs2.game.npcs.NpcActions(player).firstClickNpc(153);
		});
		activate();

		assertFalse("a shadow shopkeeper must not open the shop before "
				+ "the selector swap", player.isShopping);
		assertNull(player.scriptShopId);
	}

	private void openShop() {
		Npc shopkeeper = npcAt(SHOPKEEPER_X, SHOPKEEPER_Y);
		assertNotNull(shopkeeper);
		player.npcClickIndex = shopkeeper.npcId;
		player.clickNpcType = 153;
		new com.rs2.game.npcs.NpcActions(player).firstClickNpc(153);
		assertTrue(player.isShopping);
		assertEquals("island_general", player.scriptShopId);
	}

	private File activate() throws Exception {
		Path root = Files.createTempDirectory("script-area-shop");
		Files.write(root.resolve("loader.js"), (
				"defineShop({id:'island_general',name:'Island Supplies',"
						+ "items:[{itemId:379,amount:5,price:150}],"
						+ "buys:true,restockTicks:3});"
						+ "defineArea({id:'shop-area',name:'Shop Area',"
						+ "bounds:{minX:2830,minY:9630,maxX:2850,"
						+ "maxY:9640,plane:0},"
						+ "npcs:[{key:'shopkeeper',npcId:153,x:2830,"
						+ "y:9630,openShop:'island_general'}],"
						+ "objects:[],shops:['island_general'],quests:[],"
						+ "bosses:[],raids:[]});")
						.getBytes(StandardCharsets.UTF_8));
		System.setProperty("singlescape.contentDir",
				root.toFile().getAbsolutePath());
		ScriptHost.getInstance().reload();
		long token = com.rs2.script.area.ScriptAreaRuntime.getInstance()
				.sessionToken("shop-area");
		assertTrue("the area must activate", token > 0L);
		return root.toFile();
	}

	private static Npc npcAt(int x, int y) {
		for (int i = 1; i < NpcHandler.MAX_NPCS; i++) {
			Npc npc = NpcHandler.npcs[i];
			if (npc != null && npc.npcType == 153 && npc.absX == x
					&& npc.absY == y) {
				return npc;
			}
		}
		return null;
	}

	private static void setDefinitions(Class<?> definitionType, Object value)
			throws Exception {
		Field field = definitionType.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, value);
	}

}
