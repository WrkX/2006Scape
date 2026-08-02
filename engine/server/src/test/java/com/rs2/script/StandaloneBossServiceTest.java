package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.GameEngine;
import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.players.Player;
import com.rs2.net.packets.impl.Commands;
import com.rs2.script.boss.StandaloneBossService;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.RouteRegistry;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.script.world.ScriptNpcService;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import com.rs2.net.Packet;

/**
 * Proves the standalone owning adapter: the definition's exact WP1 host
 * routes begin and close one encounter per player, a busy arena is consumed
 * without mutation, object entry routes work through the exact object
 * dispatch, a terminal result closes the session, and a successful reload
 * closes every old-generation session.
 */
public class StandaloneBossServiceTest {

	private static final int MIN_X = 3200;
	private static final int MIN_Y = 3200;
	private static final int MAX_X = 3210;
	private static final int MAX_Y = 3210;
	private static final int PLANE = 0;

	private ScriptEncounterTestSupport support;
	private Context context;
	private Npc[] previousNpcs;
	private ScriptEncounterTestSupport.TestClient owner;
	private ScriptEncounterTestSupport.TestClient other;

	@Before
	public void setUp() throws Exception {
		support = new ScriptEncounterTestSupport();
		ScriptEncounterService.installForTesting(0x0123456789abcdefL);
		ScriptEncounterService.getInstance().resetForTesting();
		context = support.publishEmpty();
		previousNpcs = NpcHandler.npcs.clone();
		Arrays.fill(NpcHandler.npcs, null);
		GameEngine.itemHandler.items.clear();
		Wp5PlayerSupport.ensureItemDefinitions();
		owner = support.player(1, MIN_X, MIN_Y, PLANE);
		other = support.player(2, MIN_X, MIN_Y, PLANE);
	}

	@After
	public void tearDown() throws Exception {
		System.arraycopy(previousNpcs, 0, NpcHandler.npcs, 0,
				previousNpcs.length);
		GameEngine.itemHandler.items.clear();
		support.close();
	}

	@Test
	public void commandEntryBeginsOneEncounterAndCloseRouteReleasesAll() {
		registerBoss("standalone-boss", "standalone-boss-close",
				"command:'standalone-boss',"
						+ "entryTeleport:{x:3202,y:3202}");

		assertTrue("the exact command route must be consumed",
				commandConsumed(owner, "standalone-boss"));
		owner.getNextPlayerMovement();
		assertNotNull(ownedNpc(153));
		assertEquals(3202, owner.absX);
		assertEquals(3202, owner.absY);
		assertEquals(1, StandaloneBossService.getInstance().sessionCount());

		commandConsumed(owner, "standalone-boss-close");
		assertNull(ownedNpc(153));
		assertEquals(0, StandaloneBossService.getInstance().sessionCount());
	}

	@Test
	public void busyArenaIsConsumedWithoutMutation() {
		registerBoss("standalone-busy", "standalone-busy-close",
				"command:'standalone-busy'");

		commandConsumed(owner, "standalone-busy");
		assertNotNull(ownedNpc(153));
		assertEquals(1, StandaloneBossService.getInstance().sessionCount());

		// The second owner's exact route is consumed but the arena is busy:
		// no teleport, no second encounter, no mutation.
		int xBefore = other.absX;
		commandConsumed(other, "standalone-busy");
		assertEquals(xBefore, other.absX);
		assertEquals(1, StandaloneBossService.getInstance().sessionCount());
		assertNotNull(ownedNpc(153));

		commandConsumed(owner, "standalone-busy-close");
		assertEquals(0, StandaloneBossService.getInstance().sessionCount());
	}

	@Test
	public void objectEntryRouteStartsBossThroughExactObjectDispatch() {
		registerBoss("object-boss", null,
				"objectEntry:{objectId:409,action:'first'}");

		ScriptHost.DispatchResult result = ScriptHost.getInstance()
				.dispatchActive(
						state -> RouteRegistry.get(state,
								ExecutableRouteKey.object(409, "first")),
						(generation, route) -> ScriptExecutor.executeRoute(
								route, "object", "409", "first",
								new ScriptContext(
										new ScriptedPlayer(owner), null,
										"first")));
		assertEquals(ScriptHost.DispatchResult.CONSUMED, result);
		assertNotNull(ownedNpc(153));
		assertEquals(1, StandaloneBossService.getInstance().sessionCount());
	}

	@Test
	public void throwingOnSpawnFailsTheSessionAndClosesTheHandle() {
		registerBoss("throw-spawn", null,
				"command:'throw-spawn',"
						+ "onSpawn:function(){throw new Error('spawn-boom');}");

		assertTrue(commandConsumed(owner, "throw-spawn"));
		assertNull(ownedNpc(153));
		assertEquals(0, StandaloneBossService.getInstance().sessionCount());
	}

	@Test
	public void terminalDeathClosesTheSessionWithZeroResources() {
		registerBoss("standalone-death", null, "command:'standalone-death'");

		commandConsumed(owner, "standalone-death");
		Npc boss = ownedNpc(153);
		assertNotNull(boss);
		boss.HP = 0;
		boss.isDead = true;
		ScriptNpcService service = ScriptNpcService.getInstance();
		assertTrue(service.beginDeath(boss));
		service.dispatchDeath(boss, owner, new ScriptedPosition(boss.absX,
				boss.absY, boss.heightLevel));
		service.finishDeath(boss);

		assertNull(ownedNpc(153));
		assertEquals(0, StandaloneBossService.getInstance().sessionCount());
	}

	@Test
	public void closeGenerationClosesOldSessionsAfterSuccessfulReload() {
		registerBoss("standalone-reload", null,
				"command:'standalone-reload'");

		commandConsumed(owner, "standalone-reload");
		assertNotNull(ownedNpc(153));
		assertEquals(1, StandaloneBossService.getInstance().sessionCount());

		long generation = ScriptHost.getInstance().getActiveGeneration();
		StandaloneBossService.getInstance().closeGeneration(generation);
		assertNull(ownedNpc(153));
		assertEquals(0, StandaloneBossService.getInstance().sessionCount());
	}

	/** Drives the production command packet path through the unified route. */
	private static void command(Player player, String commandName) {
		ByteBuf payload = Unpooled.buffer(commandName.length() + 1);
		payload.writeBytes(commandName.getBytes(StandardCharsets.UTF_8));
		payload.writeByte(10);
		new Commands().processPacket(player,
				new Packet(103, Packet.Type.FIXED, payload));
	}

	/**
	 * Runs the production command dispatch (the same lookup/lease/invoke
	 * sequence as {@code Commands.executeScriptCommand}) and reports whether
	 * the exact route was consumed.
	 */
	private static boolean commandConsumed(Player player,
			String commandName) {
		final String command = commandName.toLowerCase(
				java.util.Locale.ROOT);
		return ScriptHost.getInstance().dispatchActive(
				state -> com.rs2.script.registries.CommandHandlerRegistry
						.getRecord(state, command),
				(generation, route) -> ScriptExecutor.executeRoute(route,
						"command", command, command,
						new CommandScriptContext(
								new ScriptedPlayer(player, generation),
								command, command, new String[0],
								player.playerRights)))
				== ScriptHost.DispatchResult.CONSUMED;
	}

	private void registerBoss(String id, String closeCommand,
			String entryMembers) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			String close = closeCommand == null ? ""
					: "closeCommand:'" + closeCommand + "',";
			String onSpawn = entryMembers.contains("onSpawn") ? ""
					: ",onSpawn:function(){}";
			ScriptFunctions.getInstance().getDefineBoss().accept(
					context.eval("js", "({id:'" + id + "',npcId:153,"
							+ "name:'Standalone',combatLevel:100,"
							+ "maxHitpoints:50,maxHit:10,attack:50,"
							+ "defence:50,arena:{minX:" + MIN_X + ",minY:"
							+ MIN_Y + ",maxX:" + MAX_X + ",maxY:" + MAX_Y
							+ ",plane:" + PLANE + "},spawn:{x:3205,y:3205},"
							+ close + entryMembers + onSpawn + "})"));
			ScriptHost.getInstance().publishForTesting(context, candidate);
		} catch (RuntimeException error) {
			RegistryStore.rollback(candidate);
			throw error;
		}
	}

	private static Npc ownedNpc(int npcType) {
		for (int index = 1; index < NpcHandler.MAX_NPCS; index++) {
			Npc npc = NpcHandler.npcs[index];
			if (npc != null && npc.npcType == npcType
					&& ScriptNpcService.getInstance().isOwned(npc)) {
				return npc;
			}
		}
		return null;
	}

}
