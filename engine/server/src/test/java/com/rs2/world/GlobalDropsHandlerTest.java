package com.rs2.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apollo.cache.def.ItemDefinition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.graalvm.polyglot.Context;
import org.apollo.util.security.IsaacRandom;

import com.rs2.Constants;
import com.rs2.GameEngine;
import com.rs2.event.CycleEventHandler;
import com.rs2.game.items.GroundItem;
import com.rs2.game.players.Client;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.net.Packet;
import com.rs2.net.packets.impl.PickupItem;
import com.rs2.net.packets.impl.ChangeRegions;
import com.rs2.script.ScriptRuntimeTestFixture;
import com.rs2.script.registries.LifecycleRegistry;
import com.rs2.util.Stream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class GlobalDropsHandlerTest {

	private static final int ITEM_ID = 1000;
	private ItemDefinition[] previousDefinitions;
	private List<GlobalDropsHandler.GlobalDrop> previousDrops;
	private Set<GlobalDropsHandler.GlobalDrop> previousSpawned;
	private Player[] previousPlayers;
	private List<GroundItem> previousGroundItems;
	private Context context;
	private Client cyclePlayer;

	@Before
	@SuppressWarnings("unchecked")
	public void setUp() throws Exception {
		Field definitions = ItemDefinition.class.getDeclaredField("definitions");
		definitions.setAccessible(true);
		previousDefinitions = (ItemDefinition[]) definitions.get(null);
		ItemDefinition[] testDefinitions = new ItemDefinition[ITEM_ID + 1];
		ItemDefinition item = new ItemDefinition(ITEM_ID);
		item.setName("global-drop-test");
		item.setStackable(false);
		testDefinitions[ITEM_ID] = item;
		definitions.set(null, testDefinitions);

		Field drops = GlobalDropsHandler.class.getDeclaredField("globalDrops");
		drops.setAccessible(true);
		List<GlobalDropsHandler.GlobalDrop> values =
				(List<GlobalDropsHandler.GlobalDrop>) drops.get(null);
		previousDrops = new ArrayList<>(values);
		values.clear();

		Field spawned = GlobalDropsHandler.class.getDeclaredField("spawnedDrops");
		spawned.setAccessible(true);
		Set<GlobalDropsHandler.GlobalDrop> spawnedValues =
				(Set<GlobalDropsHandler.GlobalDrop>) spawned.get(null);
		previousSpawned = new HashSet<>(spawnedValues);
		spawnedValues.clear();

		previousPlayers = PlayerHandler.players.clone();
		java.util.Arrays.fill(PlayerHandler.players, null);
		previousGroundItems = new ArrayList<GroundItem>(
				GameEngine.itemHandler.items);
		GameEngine.itemHandler.items.clear();
		GameEngine.itemHandler.resetProjectionsForTesting();
		ScriptRuntimeTestFixture.reset();
	}

	@After
	@SuppressWarnings("unchecked")
	public void tearDown() throws Exception {
		Field definitions = ItemDefinition.class.getDeclaredField("definitions");
		definitions.setAccessible(true);
		definitions.set(null, previousDefinitions);

		Field drops = GlobalDropsHandler.class.getDeclaredField("globalDrops");
		drops.setAccessible(true);
		List<GlobalDropsHandler.GlobalDrop> values =
				(List<GlobalDropsHandler.GlobalDrop>) drops.get(null);
		values.clear();
		values.addAll(previousDrops);

		Field spawned = GlobalDropsHandler.class.getDeclaredField("spawnedDrops");
		spawned.setAccessible(true);
		Set<GlobalDropsHandler.GlobalDrop> spawnedValues =
				(Set<GlobalDropsHandler.GlobalDrop>) spawned.get(null);
		spawnedValues.clear();
		spawnedValues.addAll(previousSpawned);
		System.arraycopy(previousPlayers, 0, PlayerHandler.players, 0,
				previousPlayers.length);
		GameEngine.itemHandler.items.clear();
		GameEngine.itemHandler.items.addAll(previousGroundItems);
		GameEngine.itemHandler.resetProjectionsForTesting();
		if (cyclePlayer != null) {
			CycleEventHandler.getSingleton().stopEvents(cyclePlayer);
			CycleEventHandler.getSingleton().process();
		}
		ScriptRuntimeTestFixture.reset();
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void failedPartialTransferIsRolledBackAndDropRemains() throws Exception {
		GlobalDropsHandler.GlobalDrop drop = installDrop(2);
		Client player = player();
		for (int slot = 0; slot < player.playerItems.length - 1; slot++) {
			player.playerItems[slot] = 2;
			player.playerItemsN[slot] = 1;
		}

		assertEquals(0, GlobalDropsHandler.pickup(player, ITEM_ID, 3200, 3200));
		assertEquals(0, player.getItemAssistant().getItemAmount(ITEM_ID));
		assertFalse(drop.isTaken());
	}

	@Test
	public void successfulTransferReturnsExactAmountAndMarksTaken() throws Exception {
		GlobalDropsHandler.GlobalDrop drop = installDrop(2);
		Client player = player();
		PlayerHandler.players[26] = player;

		assertEquals(2, GlobalDropsHandler.pickup(player, ITEM_ID, 3200, 3200));
		assertEquals(2, player.getItemAssistant().getItemAmount(ITEM_ID));
		assertTrue(drop.isTaken());
	}

	@Test
	public void productionPickupUsesExactGlobalIdentityAndLifecycle()
			throws Exception {
		GlobalDropsHandler.GlobalDrop drop = installDrop(2);
		Client player = livePlayer();
		context = Context.create("js");
		ScriptRuntimeTestFixture.publish(context, () ->
				LifecycleRegistry.putItemPickup(ITEM_ID, context.eval("js",
						"c=>{globalThis.pickups=(globalThis.pickups||0)+1;"
								+ "globalThis.amount=c.amount}")));

		new PickupItem().processPacket(player, pickupPacket());
		CycleEventHandler.getSingleton().process();

		assertEquals(2, player.getItemAssistant().getItemAmount(ITEM_ID));
		assertTrue(drop.isTaken());
		assertEquals(1, context.eval("js", "pickups").asInt());
		assertEquals(2, context.eval("js", "amount").asInt());
	}

	@Test
	public void respawnedEqualDropCannotSatisfyScheduledStalePickup()
			throws Exception {
		GlobalDropsHandler.GlobalDrop drop = installDrop(2);
		Client player = livePlayer();
		long firstToken = drop.getCreationToken();

		new PickupItem().processPacket(player, pickupPacket());
		drop.markTaken();
		spawned().remove(drop);
		drop.respawn();
		spawned().add(drop);
		CycleEventHandler.getSingleton().process();

		assertTrue(drop.getCreationToken() != firstToken);
		assertFalse(drop.isTaken());
		assertEquals(0, player.getItemAssistant().getItemAmount(ITEM_ID));
	}

	@Test
	public void exactPrivateIdentityBeatsOlderConfiguredPublicDrop()
			throws Exception {
		installDrop(1);
		Client player = livePlayer();
		GroundItem privateItem = new GroundItem(ITEM_ID, 3200, 3200, 0, 1,
				player.playerId, 100, player.playerName, player);
		GameEngine.itemHandler.addItem(privateItem);

		GroundItemRef resolved = GameEngine.itemHandler
				.resolveVisibleGroundItem(player, ITEM_ID, 3200, 3200, 0);

		assertEquals(GroundItemRef.Source.ITEM_HANDLER, resolved.getSource());
		assertTrue(resolved.isPrivateToPlayer());
		assertEquals(privateItem.getCreationToken(), resolved.getToken());
	}

	@Test
	public void lowestPublicTokenWinsAcrossBothSources() throws Exception {
		Client player = livePlayer();
		GroundItem ordinary = new GroundItem(ITEM_ID, 3200, 3200, 0, 1,
				-1, 0, "");
		GameEngine.itemHandler.addItem(ordinary);
		installDrop(1);

		GroundItemRef resolved = GameEngine.itemHandler
				.resolveVisibleGroundItem(player, ITEM_ID, 3200, 3200, 0);

		assertEquals(GroundItemRef.Source.ITEM_HANDLER, resolved.getSource());
		assertEquals(ordinary.getCreationToken(), resolved.getToken());
	}

	@Test
	public void staleConfiguredReferenceNeverConsumesOrdinaryReplacement()
			throws Exception {
		GlobalDropsHandler.GlobalDrop drop = installDrop(1);
		Client player = livePlayer();

		new PickupItem().processPacket(player, pickupPacket());
		drop.markTaken();
		spawned().remove(drop);
		GroundItem replacement = new GroundItem(
				ITEM_ID, 3200, 3200, 0, 1, -1, 0, "");
		GameEngine.itemHandler.addItem(replacement);
		CycleEventHandler.getSingleton().process();

		assertTrue(GameEngine.itemHandler.items.contains(replacement));
		assertEquals(0, player.getItemAssistant().getItemAmount(ITEM_ID));
	}

	@Test
	public void opcode236ConfiguredClaimLosesToPrivateProjectionDuringDelay()
			throws Exception {
		GlobalDropsHandler.GlobalDrop configured = installDrop(2);
		Client player = livePlayer();
		GameEngine.itemHandler.reloadItems(player);

		new PickupItem().processPacket(player, pickupPacket());
		GroundItem privateItem = new GroundItem(ITEM_ID, 3200, 3200, 0, 1,
				player.playerId, 100, player.playerName, player);
		GameEngine.itemHandler.addItem(privateItem);
		CycleEventHandler.getSingleton().process();

		assertFalse(configured.isTaken());
		assertTrue(GameEngine.itemHandler.items.contains(privateItem));
		assertEquals(0, player.getItemAssistant().getItemAmount(ITEM_ID));
		GroundItemRef selected = GameEngine.itemHandler.resolveVisibleGroundItem(
				player, ITEM_ID, 3200, 3200, 0);
		assertEquals(GroundItemRef.Source.ITEM_HANDLER, selected.getSource());
		assertEquals(privateItem.getCreationToken(), selected.getToken());
	}

	@Test
	public void changeRegionsRebuildKeepsEqualPrivateProjectionIdentity()
			throws Exception {
		GlobalDropsHandler.GlobalDrop configured = installDrop(1);
		Client player = livePlayer();
		GameEngine.itemHandler.reloadItems(player);
		assertEquals(configured.getCreationToken(), GameEngine.itemHandler
				.projectedTokenForTesting(player, ITEM_ID, 3200, 3200, 0));

		GroundItem privateItem = new GroundItem(ITEM_ID, 3200, 3200, 0, 1,
				player.playerId, 100, player.playerName, player);
		GameEngine.itemHandler.addItem(privateItem);
		TestClient client = (TestClient) player;
		client.flushCount = 0;

		new ChangeRegions().processPacket(player,
				new Packet(121, Packet.Type.FIXED, Unpooled.buffer(0)));

		assertEquals(privateItem.getCreationToken(), GameEngine.itemHandler
				.projectedTokenForTesting(player, ITEM_ID, 3200, 3200, 0));
		assertTrue(client.flushCount >= 2);
	}

	@SuppressWarnings("unchecked")
	private static GlobalDropsHandler.GlobalDrop installDrop(int amount)
			throws Exception {
		GlobalDropsHandler.GlobalDrop drop =
				new GlobalDropsHandler.GlobalDrop(ITEM_ID, amount, 3200, 3200);
		Field drops = GlobalDropsHandler.class.getDeclaredField("globalDrops");
		drops.setAccessible(true);
		((List<GlobalDropsHandler.GlobalDrop>) drops.get(null)).add(drop);
		drop.spawn();
		spawned().add(drop);
		return drop;
	}

	@SuppressWarnings("unchecked")
	private static Set<GlobalDropsHandler.GlobalDrop> spawned()
			throws Exception {
		Field spawned = GlobalDropsHandler.class.getDeclaredField("spawnedDrops");
		spawned.setAccessible(true);
		return (Set<GlobalDropsHandler.GlobalDrop>) spawned.get(null);
	}

	private Client livePlayer() {
		Client player = player();
		player.initialized = true;
		player.isActive = true;
		player.outStream = new Stream(new byte[Constants.BUFFER_SIZE]);
		player.outStream.packetEncryption = new IsaacRandom(new int[4]);
		PlayerHandler.players[player.playerId] = player;
		cyclePlayer = player;
		return player;
	}

	private static Packet pickupPacket() {
		ByteBuf payload = Unpooled.buffer(6);
		payload.writeByte(3200);
		payload.writeByte(3200 >> 8);
		payload.writeByte(ITEM_ID >> 8);
		payload.writeByte(ITEM_ID);
		payload.writeByte(3200);
		payload.writeByte(3200 >> 8);
		return new Packet(236, Packet.Type.FIXED, payload);
	}

	private static Client player() {
		Client player = new TestClient(26);
		player.outStream = null;
		player.playerName = "global-drop-player";
		player.absX = 3200;
		player.absY = 3200;
		player.heightLevel = 0;
		return player;
	}

	private static final class TestClient extends Client {
		private int flushCount;

		private TestClient(int id) {
			super(null, id);
		}

		@Override
		public void flushOutStream() {
			if (outStream != null) {
				flushCount++;
				outStream.currentOffset = 0;
			}
		}
	}
}
