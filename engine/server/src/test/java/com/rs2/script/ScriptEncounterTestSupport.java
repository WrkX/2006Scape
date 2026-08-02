package com.rs2.script;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import com.rs2.Constants;
import com.rs2.game.players.Client;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.npcs.NpcList;
import com.rs2.event.Event;
import com.rs2.script.world.ScriptEncounterHandle;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.script.registries.LifecycleRegistry;
import com.rs2.script.registries.QuestRegistry;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.quest.QuestDefinition;
import com.rs2.util.Stream;
import com.rs2.world.clip.Region;
import com.rs2.world.clip.RegionFactory;

import org.apollo.util.security.IsaacRandom;

final class ScriptEncounterTestSupport {

	static final int X = 3200;
	static final int Y = 3200;

	private final Player[] previousPlayers = PlayerHandler.players.clone();
	private final NpcList[] previousNpcList = NpcHandler.NpcList.clone();
	private final Region[] previousRegions;
	private final List<Context> contexts = new ArrayList<Context>();

	ScriptEncounterTestSupport() throws Exception {
		previousRegions = regions();
		ScriptRuntimeTestFixture.reset();
		for (int index = 0; index < PlayerHandler.players.length; index++) {
			PlayerHandler.players[index] = null;
		}
		for (int index = 0; index < NpcHandler.NpcList.length; index++) {
			NpcHandler.NpcList[index] = null;
		}
		NpcHandler.NpcList[0] = new NpcList(153);
		NpcHandler.NpcList[0].npcName = "test_npc";
		setRegions(new Region[] {
				new Region(Region.getRegionId(X, Y), false),
				new Region(Region.getRegionId(Constants.RESPAWN_X,
						Constants.RESPAWN_Y), false)
		});
		publishEmpty();
	}

	Context publishEmpty() throws Exception {
		Context context = ScriptHost.buildContext(
				Files.createTempDirectory("wp3-content").toFile());
		contexts.add(context);
		ScriptRuntimeTestFixture.publishEmpty(context);
		return context;
	}

	Context publishPlayerDeathHandler(ProxyExecutable callback) throws Exception {
		Context context = ScriptHost.buildContext(
				Files.createTempDirectory("wp3-death-content").toFile());
		contexts.add(context);
		Value handler = context.asValue(callback);
		ScriptRuntimeTestFixture.publish(context, new Runnable() {
			@Override
			public void run() {
				LifecycleRegistry.putPlayerDeath(handler);
			}
		});
		return context;
	}

	Context publishQuest(QuestDefinition definition) throws Exception {
		Context context = ScriptHost.buildContext(
				Files.createTempDirectory("wp3-quest-content").toFile());
		contexts.add(context);
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			QuestRegistry.put(definition.getId(), definition);
			QuestRegistry.validateCandidate(candidate);
			ScriptRuntimeTestFixture.publishCandidate(context, candidate);
		} catch (RuntimeException failure) {
			RegistryStore.rollback(candidate);
			throw failure;
		}
		return context;
	}

	TestClient player(int slot, int x, int y, int plane) {
		TestClient player = new TestClient(slot);
		player.playerName = "wp3-player-" + slot;
		player.initialized = true;
		player.isActive = true;
		player.disconnected = false;
		player.isDead = false;
		player.respawnTimer = 0;
		player.tutorialProgress = 36;
		player.canWalkTutorial = true;
		player.absX = x;
		player.absY = y;
		player.heightLevel = plane;
		player.mapRegionX = (x >> 3) - 6;
		player.mapRegionY = (y >> 3) - 6;
		player.currentX = x - player.mapRegionX * 8;
		player.currentY = y - player.mapRegionY * 8;
		player.teleportToX = -1;
		player.teleportToY = -1;
		player.outStream = new Stream(new byte[Constants.BUFFER_SIZE]);
		player.outStream.packetEncryption = new IsaacRandom(new int[4]);
		PlayerHandler.players[slot] = player;
		return player;
	}

	ScriptEncounterHandle encounter(Player owner, String id, int minX,
			int minY, int maxX, int maxY, int plane) {
		return new ScriptedPlayer(owner).beginEncounter(
				id, minX, minY, maxX, maxY, plane);
	}

	void close() throws Exception {
		ScriptRuntimeTestFixture.reset();
		for (Context context : contexts) {
			try {
				context.close(true);
			} catch (RuntimeException ignored) {
				// A test may already have replaced and closed the context.
			}
		}
		for (int index = 0; index < PlayerHandler.players.length; index++) {
			PlayerHandler.players[index] = previousPlayers[index];
		}
		for (int index = 0; index < NpcHandler.NpcList.length; index++) {
			NpcHandler.NpcList[index] = previousNpcList[index];
		}
		setRegions(previousRegions);
		ScriptEncounterService.getInstance().resetForTesting();
	}

	private static Region[] regions() throws Exception {
		Field field = RegionFactory.class.getDeclaredField("regions");
		field.setAccessible(true);
		return (Region[]) field.get(null);
	}

	private static void setRegions(Region[] value) throws Exception {
		Field field = RegionFactory.class.getDeclaredField("regions");
		field.setAccessible(true);
		field.set(null, value);
	}

	static final class TestClient extends Client {
		int endedTasks;
		int postedEvents;

		TestClient(int slot) {
			super(null, slot);
		}

		@Override
		public void endCurrentTask() {
			endedTasks++;
		}

		@Override
		public <E extends Event> void post(E event) {
			postedEvents++;
		}

		@Override
		public void flushOutStream() {
			if (outStream != null) {
				outStream.currentOffset = 0;
			}
		}
	}
}
