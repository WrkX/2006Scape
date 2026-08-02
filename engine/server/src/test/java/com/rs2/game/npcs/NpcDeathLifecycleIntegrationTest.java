package com.rs2.game.npcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Test;

import com.rs2.Constants;
import com.rs2.game.npcs.drops.NPCDropsHandler;
import com.rs2.game.players.Client;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.ScriptHost;
import com.rs2.util.NpcDrop;

public class NpcDeathLifecycleIntegrationTest {

	private static final int NPC_INDEX = 31;
	private static final int NPC_TYPE = 153;
	private static final int PLAYER_SLOT = 24;

	private String previousContentDir;
	private Npc[] previousNpcs;
	private Player previousPlayer;
	private NpcDrop[] previousDrops;
	private boolean previousCluesEnabled;

	@After
	public void cleanUp() {
		if (previousNpcs != null) {
			System.arraycopy(previousNpcs, 0, NpcHandler.npcs, 0, previousNpcs.length);
		}
		PlayerHandler.players[PLAYER_SLOT] = previousPlayer;
		if (previousDrops != null) {
			try {
				Field drops = NPCDropsHandler.class.getDeclaredField("npcDrops");
				drops.setAccessible(true);
				drops.set(null, previousDrops);
			} catch (Exception e) {
				throw new AssertionError(e);
			}
		}
		Constants.CLUES_ENABLED = previousCluesEnabled;
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
	}

	@Test
	public void realDeathStateMachineDispatchesOnceBeforeResetAndContinues()
			throws Exception {
		Context context = load(
				"globalThis.calls=0;globalThis.deathX=0;globalThis.deathY=0;"
				+ "globalThis.killer='';"
				+ "onNpcDeath(" + NPC_TYPE + ",c=>{calls++;deathX=c.position.x;"
				+ "deathY=c.position.y;killer=c.killer.getUsername();"
				+ "throw new Error('expected');});");
		NpcHandler handler = new NpcHandler();
		previousNpcs = NpcHandler.npcs.clone();
		Arrays.fill(NpcHandler.npcs, null);
		Field drops = NPCDropsHandler.class.getDeclaredField("npcDrops");
		drops.setAccessible(true);
		previousDrops = (NpcDrop[]) drops.get(null);
		drops.set(null, new NpcDrop[0]);
		previousCluesEnabled = Constants.CLUES_ENABLED;
		Constants.CLUES_ENABLED = false;
		previousPlayer = PlayerHandler.players[PLAYER_SLOT];
		Client killer = new Client(null, PLAYER_SLOT);
		killer.playerName = "npc-killer";
		killer.isActive = true;
		killer.initialized = true;
		killer.tutorialProgress = 36;
		PlayerHandler.players[PLAYER_SLOT] = killer;

		Npc npc = new Npc(NPC_INDEX, NPC_TYPE);
		npc.absX = 3300;
		npc.absY = 3301;
		npc.makeX = 3200;
		npc.makeY = 3201;
		npc.heightLevel = 0;
		npc.MaxHP = 20;
		npc.HP = 0;
		npc.killedBy = PLAYER_SLOT;
		npc.killerId = PLAYER_SLOT;
		npc.isDead = true;
		npc.applyDead = true;
		npc.needRespawn = false;
		npc.actionTimer = 0;
		npc.randomWalk = false;
		NpcHandler.npcs[NPC_INDEX] = npc;

		handler.process();
		assertEquals(1, context.eval("js", "calls").asInt());
		assertEquals(3300, context.eval("js", "deathX").asInt());
		assertEquals(3301, context.eval("js", "deathY").asInt());
		assertEquals("npc-killer", context.eval("js", "killer").asString());
		assertTrue(npc.needRespawn);
		assertEquals(3200, npc.absX);
		assertEquals(3201, npc.absY);
		assertEquals(20, npc.HP);

		handler.process();
		assertEquals(1, context.eval("js", "calls").asInt());
	}

	private Context load(String source) throws Exception {
		Path root = Files.createTempDirectory("npc-death-lifecycle");
		Files.write(root.resolve("loader.js"), source.getBytes(StandardCharsets.UTF_8));
		previousContentDir = System.getProperty("singlescape.contentDir");
		System.setProperty("singlescape.contentDir", root.toFile().getAbsolutePath());
		ScriptHost.getInstance().reload();
		return ScriptHost.getInstance().getContext();
	}
}
