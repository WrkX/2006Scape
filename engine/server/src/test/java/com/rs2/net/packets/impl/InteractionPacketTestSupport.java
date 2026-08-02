package com.rs2.net.packets.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.apollo.cache.def.ItemDefinition;
import org.apollo.cache.def.ObjectDefinition;

import com.rs2.Constants;
import com.rs2.GameEngine;
import com.rs2.game.items.GroundItem;
import com.rs2.game.objects.Objects;
import com.rs2.game.players.Client;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.event.Event;
import com.rs2.util.Stream;
import org.apollo.util.security.IsaacRandom;
import com.rs2.world.clip.Region;
import com.rs2.world.clip.RegionFactory;
import com.rs2.world.WorldObjectService;

final class InteractionPacketTestSupport {

	static final int X = 3200;
	static final int Y = 3200;
	static final int ITEM = 1000;
	static final int GROUND_ITEM = 1001;
	static final int LOG = 1511;
	static final int OBJECT = 2000;
	static final int OTHER_OBJECT = 2001;

	private final ItemDefinition[] previousItems;
	private final ObjectDefinition[] previousObjects;
	private final Region[] previousRegions;
	private final List<GroundItem> previousGroundItems;
	private final List<Objects> previousGlobalObjects;
	private final List<com.rs2.game.objects.Object> previousTimedObjects;
	private final Player[] previousPlayers;

	InteractionPacketTestSupport() throws Exception {
		previousItems = ItemDefinition.getDefinitions();
		previousObjects = ObjectDefinition.getDefinitions();
		previousRegions = regions();
		previousGroundItems = new ArrayList<GroundItem>(
				GameEngine.itemHandler.items);
		previousGlobalObjects = new ArrayList<Objects>(
				GameEngine.objectHandler.globalObjects);
		previousTimedObjects =
				new ArrayList<com.rs2.game.objects.Object>(
						GameEngine.objectManager.objects);
		previousPlayers = PlayerHandler.players.clone();
		WorldObjectService.getInstance().resetForTesting();

		ItemDefinition[] items = new ItemDefinition[LOG + 1];
		for (int id : new int[] {590, 592, 962, ITEM, GROUND_ITEM, LOG}) {
			ItemDefinition definition = new ItemDefinition(id);
			definition.setName("wp2-item-" + id);
			items[id] = definition;
		}
		setDefinitions(ItemDefinition.class, items);

		ObjectDefinition[] objects = new ObjectDefinition[OBJECT + 2];
		for (int id : new int[] {OBJECT, OTHER_OBJECT}) {
			ObjectDefinition definition = new ObjectDefinition(id);
			definition.setName("wp2-object-" + id);
			objects[id] = definition;
		}
		setDefinitions(ObjectDefinition.class, objects);

		Region region = new Region(Region.getRegionId(X, Y), false);
		Field realObjects = Region.class.getDeclaredField("realObjects");
		realObjects.setAccessible(true);
		@SuppressWarnings("unchecked")
		List<Objects> cacheObjects = (List<Objects>) realObjects.get(region);
		cacheObjects.add(new Objects(OBJECT, X + 1, Y, 0, 0, 10, 0));
		setRegions(new Region[] {region});

		GameEngine.itemHandler.items.clear();
		GameEngine.objectHandler.globalObjects.clear();
		GameEngine.objectManager.objects.clear();
		for (int index = 0; index < PlayerHandler.players.length; index++) {
			PlayerHandler.players[index] = null;
		}
	}

	void restore() throws Exception {
		WorldObjectService.getInstance().resetForTesting();
		setDefinitions(ItemDefinition.class, previousItems);
		setDefinitions(ObjectDefinition.class, previousObjects);
		setRegions(previousRegions);
		GameEngine.itemHandler.items.clear();
		GameEngine.itemHandler.items.addAll(previousGroundItems);
		GameEngine.objectHandler.globalObjects.clear();
		GameEngine.objectHandler.globalObjects.addAll(previousGlobalObjects);
		GameEngine.objectManager.objects.clear();
		GameEngine.objectManager.objects.addAll(previousTimedObjects);
		for (int index = 0; index < PlayerHandler.players.length; index++) {
			PlayerHandler.players[index] = previousPlayers[index];
		}
	}

	TestPlayer livePlayer(int index) {
		TestPlayer player = new TestPlayer(index);
		player.playerName = "wp2-player-" + index;
		player.initialized = true;
		player.isActive = true;
		player.disconnected = false;
		player.isDead = false;
		player.respawnTimer = 0;
		player.isTeleporting = false;
		player.teleTimer = 0;
		player.teleportToX = -1;
		player.teleportToY = -1;
		player.absX = X;
		player.absY = Y;
		player.heightLevel = 0;
		player.tutorialProgress = 36;
		player.outStream = new Stream(new byte[Constants.BUFFER_SIZE]);
		player.outStream.packetEncryption = new IsaacRandom(new int[4]);
		PlayerHandler.players[index] = player;
		return player;
	}

	GroundItem addGroundItem(Player owner, int itemId, int x, int y,
			int hideTicks) {
		GroundItem item = new GroundItem(itemId, x, y, owner.heightLevel, 1,
				owner.playerId, hideTicks, owner.playerName, owner);
		GameEngine.itemHandler.addItem(item);
		return item;
	}

	private static Region[] regions() throws Exception {
		Field regions = RegionFactory.class.getDeclaredField("regions");
		regions.setAccessible(true);
		return (Region[]) regions.get(null);
	}

	private static void setRegions(Region[] value) throws Exception {
		Field regions = RegionFactory.class.getDeclaredField("regions");
		regions.setAccessible(true);
		regions.set(null, value);
	}

	private static void setDefinitions(Class<?> type, Object definitions)
			throws Exception {
		Field field = type.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, definitions);
	}

	static final class TestPlayer extends Client {
		int endedTasks;
		int postedEvents;
		private final List<Integer> flushedWords = new ArrayList<Integer>();

		TestPlayer(int id) {
			super(null, id);
		}

		@Override
		public void endCurrentTask() {
			endedTasks++;
		}

		@Override
		public <E extends Event> void post(E event) {
			postedEvents++;
		}

		boolean hasFaceUpdate() {
			return faceUpdateRequired;
		}

		boolean hasFlushedWord(int value) {
			return flushedWords.contains(value);
		}

		@Override
		public void flushOutStream() {
			if (outStream != null) {
				for (int index = 0; index + 1 < outStream.currentOffset;
						index++) {
					flushedWords.add(
							(outStream.buffer[index] & 0xff) << 8
							| outStream.buffer[index + 1] & 0xff);
				}
				outStream.currentOffset = 0;
			}
		}
	}

}
