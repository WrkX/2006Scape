package com.rs2.net.packets.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apollo.cache.def.ItemDefinition;
import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.GameEngine;
import com.rs2.event.CycleEventHandler;
import com.rs2.game.items.GroundItem;
import com.rs2.game.players.Client;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.ScriptHost;
import com.rs2.Constants;
import com.rs2.util.Stream;
import org.apollo.util.security.IsaacRandom;

public class PickupLifecycleIntegrationTest {

	private static final int ITEM_ID = 1000;
	private static final int X = 3200;
	private static final int Y = 3200;

	private ItemDefinition[] previousDefinitions;
	private String previousContentDir;
	private List<GroundItem> previousGroundItems;
	private RecordingPlayer player;
	private Player previousPlayer;

	@Before
	public void setUp() throws Exception {
		Field definitions = ItemDefinition.class.getDeclaredField("definitions");
		definitions.setAccessible(true);
		previousDefinitions = (ItemDefinition[]) definitions.get(null);
		ItemDefinition[] testDefinitions = new ItemDefinition[ITEM_ID + 1];
		ItemDefinition item = new ItemDefinition(ITEM_ID);
		item.setName("pickup-test");
		testDefinitions[ITEM_ID] = item;
		definitions.set(null, testDefinitions);

		previousGroundItems = new ArrayList<>(GameEngine.itemHandler.items);
		GameEngine.itemHandler.items.clear();
		player = new RecordingPlayer();
		previousPlayer = PlayerHandler.players[player.playerId];
		PlayerHandler.players[player.playerId] = player;
	}

	@After
	public void tearDown() throws Exception {
		CycleEventHandler.getSingleton().stopEvents(player);
		CycleEventHandler.getSingleton().process();
		GameEngine.itemHandler.items.clear();
		GameEngine.itemHandler.items.addAll(previousGroundItems);
		PlayerHandler.players[player.playerId] = previousPlayer;
		Field definitions = ItemDefinition.class.getDeclaredField("definitions");
		definitions.setAccessible(true);
		definitions.set(null, previousDefinitions);
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
	}

	@Test
	public void realPacketAndCycleEmitExactSuccessfulTransfer() throws Exception {
		Context context = load(
				"globalThis.pickups=0;globalThis.amount=0;"
				+ "onItemPickup(1000,c=>{pickups++;amount=c.amount;});");
		addGroundItem(3);

		new PickupItem().processPacket(player, PacketFixtures.pickup(ITEM_ID, X, Y));
		CycleEventHandler.getSingleton().process();

		assertEquals(1, context.eval("js", "pickups").asInt());
		assertEquals(3, context.eval("js", "amount").asInt());
		assertEquals(3, player.getItemAssistant().getItemAmount(ITEM_ID));
		assertFalse(GameEngine.itemHandler.itemExists(ITEM_ID, X, Y));
		assertTrue(player.soundDone);
	}

	@Test
	public void fullInventoryRetainsItemAndEmitsNothing() throws Exception {
		Context context = load("globalThis.pickups=0;onItemPickup(1000,c=>pickups++);");
		for (int slot = 0; slot < player.playerItems.length; slot++) {
			player.playerItems[slot] = 2;
			player.playerItemsN[slot] = 1;
		}
		addGroundItem(2);

		new PickupItem().processPacket(player, PacketFixtures.pickup(ITEM_ID, X, Y));
		CycleEventHandler.getSingleton().process();

		assertEquals(0, context.eval("js", "pickups").asInt());
		assertTrue(GameEngine.itemHandler.itemExists(ITEM_ID, X, Y));
	}

	@Test
	public void throwingObserverDoesNotRollBackTransfer() throws Exception {
		load("onItemPickup(1000,c=>{throw new Error('expected');});");
		addGroundItem(2);

		new PickupItem().processPacket(player, PacketFixtures.pickup(ITEM_ID, X, Y));
		CycleEventHandler.getSingleton().process();

		assertEquals(2, player.getItemAssistant().getItemAmount(ITEM_ID));
		assertFalse(GameEngine.itemHandler.itemExists(ITEM_ID, X, Y));
		assertTrue(player.soundDone);
	}

	private Context load(String source) throws Exception {
		Path root = Files.createTempDirectory("pickup-lifecycle");
		Files.write(root.resolve("loader.js"), source.getBytes(StandardCharsets.UTF_8));
		previousContentDir = System.getProperty("singlescape.contentDir");
		System.setProperty("singlescape.contentDir", root.toFile().getAbsolutePath());
		ScriptHost.getInstance().reload();
		return ScriptHost.getInstance().getContext();
	}

	private void addGroundItem(int amount) {
		GameEngine.itemHandler.addItem(new GroundItem(
				ITEM_ID, X, Y, 0, amount, player.playerId, 100,
				player.playerName, player));
	}

	private static final class RecordingPlayer extends Client {
		private RecordingPlayer() {
			super(null, 1);
			playerName = "pickup-test-player";
			absX = X;
			absY = Y;
			heightLevel = 0;
			tutorialProgress = 36;
			initialized = true;
			isActive = true;
			outStream = new Stream(new byte[Constants.BUFFER_SIZE]);
			outStream.packetEncryption = new IsaacRandom(new int[4]);
		}

		@Override
		public void flushOutStream() {
			if (outStream != null) {
				outStream.currentOffset = 0;
			}
		}
	}
}
