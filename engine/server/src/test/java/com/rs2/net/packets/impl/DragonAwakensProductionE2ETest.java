package com.rs2.net.packets.impl;

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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apollo.cache.IndexedFileSystem;
import org.apollo.cache.decoder.ItemDefinitionDecoder;
import org.apollo.cache.decoder.ObjectDefinitionDecoder;
import org.apollo.cache.def.ItemDefinition;
import org.apollo.cache.def.ObjectDefinition;
import org.apollo.util.security.IsaacRandom;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rs2.Constants;
import com.rs2.GameEngine;
import com.rs2.event.CycleEventHandler;
import com.rs2.game.dialogues.Dialogue;
import com.rs2.game.items.GroundItem;
import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.npcs.drops.ItemDrop;
import com.rs2.game.npcs.drops.NPCDropsHandler;
import com.rs2.game.players.Client;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.game.players.PlayerSave;
import com.rs2.script.DialogueChain;
import com.rs2.script.ScriptHost;
import com.rs2.script.ScriptRuntimeTestFixture;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.quest.ScriptedQuest;
import com.rs2.util.NpcDrop;
import com.rs2.world.clip.Region;
import com.rs2.world.clip.RegionFactory;

/**
 * Full production-path proof for the shipped Dragon Awakens content.
 */
public class DragonAwakensProductionE2ETest {

	private static final int PLAYER_SLOT = 24;
	private static final int CHRONOZON = 667;
	private static final int GREEN_DRAGON = 941;
	private static final int DRAGON_BONES = 536;
	private static final int ALTAR = 409;
	private static final int ALTAR_X = 3243;
	private static final int ALTAR_Y = 3207;

	private String previousContentDir;
	private String previousUserDir;
	private Player previousPlayer;
	private Npc[] previousNpcs;
	private NpcDrop[] previousDrops;
	private List<GroundItem> previousGroundItems;
	private ObjectDefinition[] previousObjectDefinitions;
	private ItemDefinition[] previousItemDefinitions;
	private Region[] previousRegions;
	private boolean previousCluesEnabled;
	private Path dropLog;
	private Path pickupLog;
	private byte[] previousDropLog;
	private byte[] previousPickupLog;
	private Client player;
	private NpcHandler npcHandler;
	private File compiledContent;

	@Before
	public void setUp() throws Exception {
		previousContentDir = System.getProperty("singlescape.contentDir");
		previousUserDir = System.getProperty("user.dir");
		previousPlayer = PlayerHandler.players[PLAYER_SLOT];
		previousNpcs = NpcHandler.npcs.clone();
		previousDrops = getStaticField(NPCDropsHandler.class, "npcDrops");
		previousGroundItems = new ArrayList<>(GameEngine.itemHandler.items);
		previousObjectDefinitions =
				getStaticField(ObjectDefinition.class, "definitions");
		previousItemDefinitions =
				getStaticField(ItemDefinition.class, "definitions");
		previousRegions = getStaticField(RegionFactory.class, "regions");
		previousCluesEnabled = Constants.CLUES_ENABLED;
		dropLog = Paths.get(Constants.SERVER_LOG_DIR, "dropitem",
				"dragon-e2e.txt");
		pickupLog = Paths.get(Constants.SERVER_LOG_DIR, "pickupitem",
				"dragon-e2e.txt");
		previousDropLog = Files.exists(dropLog)
				? Files.readAllBytes(dropLog) : null;
		previousPickupLog = Files.exists(pickupLog)
				? Files.readAllBytes(pickupLog) : null;
		Constants.CLUES_ENABLED = false;
		GameEngine.itemHandler.items.clear();

		IndexedFileSystem cache =
				new IndexedFileSystem(Paths.get("data", "cache"), true);
		new ObjectDefinitionDecoder(cache).run();
		new ItemDefinitionDecoder(cache).run();
		RegionFactory.load(cache);
		assertTrue("Bundled cache must contain the production altar",
				Region.objectExists(ALTAR, ALTAR_X, ALTAR_Y, 0));

		npcHandler = new NpcHandler();
		assertProductionWorldFiles();
		int giverIndex = findNpc(CHRONOZON, 3156, 3704);
		int firstDragon = findNpc(GREEN_DRAGON, 3150, 3704);
		int secondDragon = findNpc(GREEN_DRAGON, 3149, 3695);
		assertTrue(giverIndex > 0);
		assertTrue(firstDragon > 0);
		assertTrue(secondDragon > 0);
		for (int i = 0; i < NpcHandler.npcs.length; i++) {
			if (i != giverIndex && i != firstDragon && i != secondDragon) {
				NpcHandler.npcs[i] = null;
			}
		}

		compiledContent = compiledContent();
		assertTrue(compiledContent.isDirectory());
		System.setProperty("singlescape.contentDir",
				compiledContent.getAbsolutePath());
		ScriptHost.getInstance().reload();

		player = savableClient("dragon-e2e");
		player.absX = 3156;
		player.absY = 3707;
		player.heightLevel = 0;
		PlayerHandler.players[PLAYER_SLOT] = player;
	}

	@After
	public void tearDown() throws Exception {
		if (player != null) {
			CycleEventHandler.getSingleton().stopEvents(player);
			CycleEventHandler.getSingleton().process();
		}
		// The compiled reload activated the Dragon Island area in the real
		// regions; reset the runtime while those regions still carry its
		// collision ledger so the next class starts from a clean world.
		ScriptRuntimeTestFixture.reset();
		PlayerHandler.players[PLAYER_SLOT] = previousPlayer;
		System.arraycopy(previousNpcs, 0, NpcHandler.npcs, 0,
				previousNpcs.length);
		setStaticField(NPCDropsHandler.class, "npcDrops", previousDrops);
		GameEngine.itemHandler.items.clear();
		GameEngine.itemHandler.items.addAll(previousGroundItems);
		setStaticField(ObjectDefinition.class, "definitions",
				previousObjectDefinitions);
		setStaticField(ItemDefinition.class, "definitions",
				previousItemDefinitions);
		setStaticField(RegionFactory.class, "regions", previousRegions);
		Constants.CLUES_ENABLED = previousCluesEnabled;
		restoreFile(dropLog, previousDropLog);
		restoreFile(pickupLog, previousPickupLog);
		System.setProperty("user.dir", previousUserDir);
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
	}

	@Test
	public void packetLifecycleDialogueAndRealSaveReloadCompleteRetryably()
			throws Exception {
		int giverIndex = findNpc(CHRONOZON, 3156, 3704);
		int firstDragon = findNpc(GREEN_DRAGON, 3150, 3704);
		int secondDragon = findNpc(GREEN_DRAGON, 3149, 3695);

		new ClickNPC().processPacket(player,
				PacketFixtures.npcFirstClick(giverIndex));
		assertEquals(1, player.clickNpcType);
		player.absY = 3705;
		CycleEventHandler.getSingleton().process();
		assertEquals(DialogueChain.CHAIN_SENTINEL, player.nextChat);
		new Dialogue().processPacket(player, PacketFixtures.dialogueContinue());
		assertNotNull(player.pendingScriptOption);
		assertEquals(2, player.pendingOptionCount);
		new ClickingButtons().processPacket(player,
				PacketFixtures.actionButton(9157));
		assertNull(player.pendingScriptOption);
		assertEquals(Integer.valueOf(0), quest(player).stage());

		clickNpc(player, giverIndex);
		assertEquals(Integer.valueOf(1), quest(player).stage());

		killThroughProductionStateMachine(firstDragon, 2);
		assertTrue(GameEngine.itemHandler.itemExists(
				DRAGON_BONES, 3150, 3704));
		player.absX = 3150;
		player.absY = 3704;
		new PickupItem().processPacket(player,
				PacketFixtures.pickup(DRAGON_BONES, 3150, 3704));
		CycleEventHandler.getSingleton().process();
		assertEquals(1, player.getItemAssistant().getItemAmount(DRAGON_BONES));
		assertEquals(Integer.valueOf(2), quest(player).stage());

		player = saveAndReload(player);
		assertEquals(Integer.valueOf(2), quest(player).stage());
		assertEquals(1, player.getItemAssistant().getItemAmount(DRAGON_BONES));

		long generation = ScriptHost.getInstance().getActiveGeneration();
		Path rejected = Files.createTempDirectory("dragon-rejected-content");
		Files.write(rejected.resolve("loader.js"),
				"throw new Error('candidate rejected');"
						.getBytes(StandardCharsets.UTF_8));
		System.setProperty("singlescape.contentDir", rejected.toString());
		ScriptHost.getInstance().reload();
		assertEquals(generation,
				ScriptHost.getInstance().getActiveGeneration());
		assertEquals(Integer.valueOf(2), quest(player).stage());
		System.setProperty("singlescape.contentDir",
				compiledContent.getAbsolutePath());
		ScriptHost.getInstance().reload();

		player.absX = ALTAR_X;
		player.absY = ALTAR_Y + 1;
		int bonesSlot = inventorySlot(player, DRAGON_BONES);
		new ItemOnObject().processPacket(player, PacketFixtures.itemOnObject(
				DRAGON_BONES, bonesSlot, ALTAR, ALTAR_X, ALTAR_Y));
		assertEquals(0, player.getItemAssistant().getItemAmount(DRAGON_BONES));
		assertEquals(Integer.valueOf(3), quest(player).stage());

		killThroughProductionStateMachine(secondDragon, 4);
		assertEquals(Integer.valueOf(4), quest(player).stage());
		assertEquals("in_progress", quest(player).state());

		Arrays.fill(player.playerItems, 2);
		Arrays.fill(player.playerItemsN, 1);
		player.absX = 3156;
		player.absY = 3705;
		clickNpc(player, giverIndex);
		assertEquals("in_progress", quest(player).state());
		assertEquals(Integer.valueOf(4), quest(player).stage());
		assertEquals(0, player.questPoints);
		assertEquals(0, player.getItemAssistant().getItemAmount(995));

		player.playerItems[0] = 0;
		player.playerItemsN[0] = 0;
		clickNpc(player, giverIndex);
		assertEquals("completed", quest(player).state());
		assertEquals(3, player.questPoints);
		assertEquals(1000, player.getItemAssistant().getItemAmount(995));

		player = saveAndReload(player);
		assertEquals("completed", quest(player).state());
		assertEquals(Integer.valueOf(4), quest(player).stage());
		assertEquals(3, player.questPoints);
		assertEquals(1000, player.getItemAssistant().getItemAmount(995));
		assertEquals("dragon-rite-sealed", new ScriptedPlayer(player)
				.state("dragon-awakens").getString("ending"));
	}

	private void assertProductionWorldFiles() throws Exception {
		JsonArray spawns = JsonParser.parseString(new String(Files.readAllBytes(
				Paths.get("data", "cfg", "spawns.json")),
				StandardCharsets.UTF_8)).getAsJsonArray();
		boolean giver = false;
		boolean dragon = false;
		for (JsonElement element : spawns) {
			JsonObject spawn = element.getAsJsonObject();
			int id = spawn.get("id").getAsInt();
			if (id == CHRONOZON
					&& spawn.get("x").getAsInt() == 3156
					&& spawn.get("y").getAsInt() == 3704
					&& spawn.get("height").getAsInt() == 0) {
				giver = true;
			}
			if (id == GREEN_DRAGON) {
				dragon = true;
			}
		}
		assertTrue("Chronozon production spawn missing", giver);
		assertTrue("Green dragon production spawn missing", dragon);

		boolean guaranteedBones = false;
		for (ItemDrop drop : NPCDropsHandler.getNpcDrops(
				"green_dragon", GREEN_DRAGON)) {
			if (drop.getItemID() == DRAGON_BONES && drop.getChance() == 0) {
				guaranteedBones = true;
			}
		}
		assertTrue("Green dragon must guarantee dragon bones",
				guaranteedBones);
	}

	private void killThroughProductionStateMachine(int npcIndex,
			int expectedStage) {
		Npc npc = NpcHandler.npcs[npcIndex];
		assertNotNull(npc);
		player.absX = npc.absX;
		player.absY = npc.absY;
		player.heightLevel = npc.heightLevel;
		player.lastNpcAttacked = npcIndex;
		player.totalDamageDealt = Math.max(1, npc.MaxHP);
		npc.HP = 0;
		npc.isDead = true;
		npc.applyDead = false;
		npc.needRespawn = false;
		npc.actionTimer = 0;
		for (int cycle = 0; cycle < 8; cycle++) {
			npcHandler.process();
			Integer stage = quest(player).stage();
			if (expectedStage == 2
					&& GameEngine.itemHandler.itemExists(
							DRAGON_BONES, npc.makeX, npc.makeY)) {
				return;
			}
			if (stage != null && stage == expectedStage) {
				return;
			}
		}
		throw new AssertionError(
				"NPC death did not reach quest stage " + expectedStage);
	}

	private static void clickNpc(Client player, int npcIndex) {
		new ClickNPC().processPacket(player,
				PacketFixtures.npcFirstClick(npcIndex));
	}

	private Client saveAndReload(Client current) throws Exception {
		Path root = Files.createTempDirectory("dragon-player-save");
		Files.createDirectories(root.resolve("data").resolve("characters"));
		System.setProperty("user.dir", root.toString());
		PlayerHandler.players[PLAYER_SLOT] = current;
		assertTrue(PlayerSave.saveGame(current));

		Client loaded = savableClient("dragon-e2e");
		assertTrue(PlayerSave.loadPlayerInfo(
				loaded, "dragon-e2e", "password", false) > 0);
		PlayerHandler.players[PLAYER_SLOT] = loaded;
		System.setProperty("user.dir", previousUserDir);
		return loaded;
	}

	private static ScriptedQuest quest(Player player) {
		ScriptedQuest quest = new ScriptedPlayer(player)
				.quest("dragon-awakens");
		assertNotNull(quest);
		return quest;
	}

	private static int inventorySlot(Player player, int itemId) {
		for (int slot = 0; slot < player.playerItems.length; slot++) {
			if (player.playerItems[slot] == itemId + 1) {
				return slot;
			}
		}
		throw new AssertionError("Missing inventory item " + itemId);
	}

	private static int findNpc(int type, int x, int y) {
		for (int i = 1; i < NpcHandler.npcs.length; i++) {
			Npc npc = NpcHandler.npcs[i];
			if (npc != null && npc.npcType == type
					&& npc.makeX == x && npc.makeY == y
					&& npc.heightLevel == 0) {
				return i;
			}
		}
		return -1;
	}

	private static Client savableClient(String name) {
		Client player = new TestClient();
		player.playerName = name;
		player.playerName2 = name;
		player.playerPass = "password";
		player.saveFile = true;
		player.saveCharacter = true;
		player.newPlayer = false;
		player.isActive = true;
		player.initialized = true;
		player.tutorialProgress = 36;
		player.outStream.packetEncryption = new IsaacRandom(new int[4]);
		return player;
	}

	private static final class TestClient extends Client {
		private TestClient() {
			super(null, PLAYER_SLOT);
		}

		@Override
		public void flushOutStream() {
			if (outStream != null) {
				outStream.currentOffset = 0;
			}
		}
	}

	private static File compiledContent() {
		File direct = new File(System.getProperty("user.dir"), "content/dist");
		return direct.isDirectory() ? direct : new File(
				System.getProperty("user.dir"), "../../content/dist");
	}

	@SuppressWarnings("unchecked")
	private static <T> T getStaticField(Class<?> owner, String name)
			throws Exception {
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		return (T) field.get(null);
	}

	private static void setStaticField(Class<?> owner, String name, Object value)
			throws Exception {
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		field.set(null, value);
	}

	private static void restoreFile(Path path, byte[] previous)
			throws Exception {
		if (previous == null) {
			Files.deleteIfExists(path);
		} else {
			Files.write(path, previous);
		}
	}
}
