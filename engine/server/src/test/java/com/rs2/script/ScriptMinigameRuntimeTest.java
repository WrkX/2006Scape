package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.players.Player;
import com.rs2.net.Packet;
import com.rs2.net.packets.impl.Commands;
import com.rs2.script.minigame.ScriptMinigameRuntime;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.world.ScriptEncounterService;

import io.netty.buffer.Unpooled;

/**
 * Proves lobby join/start and wave advancement for defineMinigame.
 */
public class ScriptMinigameRuntimeTest {

	private static final int MIN_X = 3218;
	private static final int MIN_Y = 3218;
	private static final int PLANE = 0;
	private static final String COMMAND = "test-wave";

	private ScriptEncounterTestSupport support;
	private Context context;
	private Npc[] previousNpcs;
	private ScriptEncounterTestSupport.TestClient player;

	@Before
	public void setUp() throws Exception {
		support = new ScriptEncounterTestSupport();
		ScriptEncounterService.installForTesting(0xabcL);
		ScriptEncounterService.getInstance().resetForTesting();
		ScriptMinigameRuntime.installForTesting();
		context = support.publishEmpty();
		previousNpcs = NpcHandler.npcs.clone();
		java.util.Arrays.fill(NpcHandler.npcs, null);
		Wp5PlayerSupport.ensureNpcDefinitions();
		player = support.player(1, MIN_X, MIN_Y, PLANE);
		registerMinigame();
	}

	@After
	public void tearDown() throws Exception {
		if (previousNpcs != null) {
			System.arraycopy(previousNpcs, 0, NpcHandler.npcs, 0,
					previousNpcs.length);
		}
		ScriptMinigameRuntime.getInstance().resetForTesting();
		support.close();
	}

	@Test
	public void lobbyJoinStartAndWaveAdvancement() {
		minigameCommand(player, "join");
		assertEquals(1, ScriptMinigameRuntime.getInstance().membershipCount());
		minigameCommand(player, "start");
		assertEquals(0, ScriptMinigameRuntime.getInstance().lobbyCount());
		assertEquals(1, ScriptMinigameRuntime.getInstance().sessionCount());
		assertEquals(1, context.getBindings("js").getMember("starts").asInt());

		killWaveNpcs();
		ScriptLifecycleService.getInstance().processGameTick();
		assertEquals(1, context.getBindings("js").getMember("waveCompletes")
				.asInt());
		assertTrue(hasAliveWaveNpc());

		killWaveNpcs();
		ScriptLifecycleService.getInstance().processGameTick();
		assertEquals(2, context.getBindings("js").getMember("waveCompletes")
				.asInt());
		assertEquals(1, context.getBindings("js").getMember("completed")
				.asInt());
		assertEquals(0, ScriptMinigameRuntime.getInstance().sessionCount());
	}

	private boolean hasAliveWaveNpc() {
		for (Npc npc : NpcHandler.npcs) {
			if (npc != null && !npc.isDead && npc.HP > 0) {
				return true;
			}
		}
		return false;
	}

	private void killWaveNpcs() {
		for (Npc npc : NpcHandler.npcs) {
			if (npc != null) {
				npc.HP = 0;
				npc.isDead = true;
			}
		}
	}

	private void registerMinigame() throws Exception {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			context.eval("js", "globalThis.starts = 0; globalThis.waveCompletes = 0;"
					+ "globalThis.completed = 0;");
			ScriptFunctions.getInstance().getDefineArea().accept(context
					.eval("js", "({id:'test-wave-lobby',name:'Lobby',"
							+ "bounds:{minX:" + MIN_X + ",minY:" + MIN_Y
							+ ",maxX:3224,maxY:3224,plane:" + PLANE
							+ "},npcs:[],objects:[]})"));
			ScriptFunctions.getInstance().getDefineArea().accept(context
					.eval("js", "({id:'test-wave-arena',name:'Arena',"
							+ "bounds:{minX:" + MIN_X + ",minY:" + MIN_Y
							+ ",maxX:3230,maxY:3230,plane:" + PLANE
							+ "},npcs:[],objects:[]})"));
			ScriptFunctions.getInstance().getDefineMinigame().accept(context
					.eval("js", "({id:'test-wave',command:'" + COMMAND + "',"
							+ "lobbyAreaId:'test-wave-lobby',"
							+ "arenaAreaId:'test-wave-arena',"
							+ "entrance:{x:3220,y:3220,plane:" + PLANE + "},"
							+ "leave:{x:3218,y:3218,plane:" + PLANE + "},"
							+ "minPlayers:1,maxPlayers:5,lobbyWaitTicks:0,"
							+ "timeLimitTicks:600,"
							+ "onStart:function(){globalThis.starts++;},"
							+ "onWaveComplete:function(){globalThis.waveCompletes++;},"
							+ "onComplete:function(){globalThis.completed++;},"
							+ "waves:["
							+ "{id:'one',npcs:[{npcId:153,x:3220,y:3222}]},"
							+ "{id:'two',npcs:[{npcId:153,x:3222,y:3222}]}"
							+ "]})"));
			ScriptHost.getInstance().publishForTesting(context, candidate);
		} catch (RuntimeException error) {
			RegistryStore.rollback(candidate);
			throw error;
		}
	}

	private static void minigameCommand(Player player, String subcommand) {
		String payload = COMMAND + " " + subcommand + "\n";
		new Commands().processPacket(player, new Packet(103, Packet.Type.FIXED,
				Unpooled.copiedBuffer(payload.getBytes(
						java.nio.charset.StandardCharsets.UTF_8))));
	}
}
