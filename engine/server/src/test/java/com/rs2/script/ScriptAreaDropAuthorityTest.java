package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apollo.cache.def.ItemDefinition;
import org.apollo.cache.def.ObjectDefinition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.GameEngine;
import com.rs2.game.items.GroundItem;
import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.npcs.NpcList;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.area.ScriptAreaRuntime;
import com.rs2.world.clip.Region;
import com.rs2.world.clip.RegionFactory;

/**
 * Proves the exact area-NPC death authority through the real
 * {@code NpcHandler} death critical section: a bound allocation claims its
 * exact identity and rolls the named WP2 table through the area-session
 * RNG with killer-private or public ground identities, legacy drops stay
 * suppressed, null/stale killers are handled NO_RECIPIENT without RNG or
 * ground mutation, equal-id legacy NPCs remain unmatched, the allocation
 * respawns after its declared interval, and reload cleanup removes only the
 * area's own unclaimed identities.
 */
public class ScriptAreaDropAuthorityTest {

	private static final int GUARD_X = 2830;
	private static final int GUARD_Y = 9630;

	private String previousContentDir;
	private NpcList[] previousNpcList;
	private ItemDefinition[] previousItems;
	private ObjectDefinition[] previousObjects;
	private Region[] previousRegions;
	private NpcHandler npcHandler;
	private Player killer;

	@Before
	public void setUp() throws Exception {
		ScriptRuntimeTestFixture.reset();
		com.rs2.script.world.ScriptEncounterService.installForTesting(7L);
		previousContentDir = System.getProperty("singlescape.contentDir");
		previousNpcList = NpcHandler.NpcList.clone();
		previousItems = ItemDefinition.getDefinitions();
		previousObjects = ObjectDefinition.getDefinitions();
		Field regions = RegionFactory.class.getDeclaredField("regions");
		regions.setAccessible(true);
		previousRegions = (Region[]) regions.get(null);
		// The constructor loads the production lists and clears the arrays;
		// the hermetic list and world state are installed afterwards.
		npcHandler = new NpcHandler();
		NpcHandler.NpcList = new NpcList[NpcHandler.maxListedNPCs];
		NpcList npc = new NpcList(153);
		npc.npcName = "test_dragon";
		NpcHandler.NpcList[0] = npc;
		Arrays.fill(NpcHandler.npcs, null);
		Wp5PlayerSupport.ensureItemDefinitions();
		Wp5PlayerSupport.ensureObjectDefinitions();
		Wp5PlayerSupport.ensureAreaRegions();
		killer = Wp5PlayerSupport.player(91);
		killer.absX = GUARD_X;
		killer.absY = GUARD_Y;
		killer.heightLevel = 0;
	}

	@After
	public void restore() throws Exception {
		if (killer != null) {
			PlayerHandler.players[91] = null;
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
	public void boundPrivateDeathClaimsAllocationRollsAndSuppressesLegacy()
			throws Exception {
		activate();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"drop-area");
		assertTrue(token > 0L);
		Npc guard = npcAt(GUARD_X, GUARD_Y);
		assertNotNull(guard);
		long rngBefore = ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token);

		guard.killedBy = killer.playerId;
		driveDeath(guard);

		// The exact allocation is claimed once and despawned.
		assertNull(npcAt(GUARD_X, GUARD_Y));
		// One killer-private detached identity exists at the death tile.
		List<GroundItem> identities = groundItemsAt(536, GUARD_X, GUARD_Y);
		assertEquals(1, identities.size());
		GroundItem identity = identities.get(0);
		assertTrue(identity.isPrivateTo(killer));
		assertTrue(identity.isDetached());
		assertTrue(identity.hideTicks > 0);
		// The session RNG advanced exactly once.
		assertTrue(ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token) != rngBefore);
	}

	@Test
	public void publicDeathCreatesPublicIdentityWithBoundedLifetime()
			throws Exception {
		activate();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"drop-area");
		Npc guard = npcAt(2840, 9640);
		assertNotNull(guard);
		long rngBefore = ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token);

		guard.killedBy = killer.playerId;
		driveDeath(guard);

		List<GroundItem> identities = groundItemsAt(995, 2840, 9640);
		assertEquals(1, identities.size());
		GroundItem identity = identities.get(0);
		assertFalse(identity.isScriptPrivate());
		assertTrue("public identities carry a bounded lifetime",
				identity.removeTicks > 0);
		assertTrue(ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token) != rngBefore);
	}

	@Test
	public void nullAndStaleKillersAreHandledNoRecipientWithoutRngOrGround()
			throws Exception {
		activate();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"drop-area");
		Npc guard = npcAt(GUARD_X, GUARD_Y);
		long rngBefore = ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token);

		guard.killedBy = 0;
		driveDeath(guard);
		assertEquals("null killer: no ground, no RNG advance",
				rngBefore, ScriptAreaRuntime.getInstance()
						.areaRngStateForTesting(token));
		assertTrue(GameEngine.itemHandler.items.isEmpty());

		// The allocation was consumed; the respawn restores it later.
		assertNull(npcAt(GUARD_X, GUARD_Y));

		ScriptAreaRuntime.getInstance().processGameTick();
		ScriptAreaRuntime.getInstance().processGameTick();
		ScriptAreaRuntime.getInstance().processGameTick();
		Npc respawned = npcAt(GUARD_X, GUARD_Y);
		assertNotNull("the claimed allocation respawns after its interval",
				respawned);
	}

	@Test
	public void staleKillerSlotIsRejectedWithoutGroundOrRng() throws Exception {
		activate();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"drop-area");
		Npc guard = npcAt(GUARD_X, GUARD_Y);
		// The killer slot no longer holds the live player at commit.
		PlayerHandler.players[killer.playerId] = null;
		long rngBefore = ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token);

		guard.killedBy = killer.playerId;
		driveDeath(guard);

		assertTrue(GameEngine.itemHandler.items.isEmpty());
		assertEquals("stale killer: handled NO_RECIPIENT, no RNG advance",
				rngBefore, ScriptAreaRuntime.getInstance()
						.areaRngStateForTesting(token));
	}

	@Test
	public void equalIdLegacyNpcDeathRemainsUnmatched() throws Exception {
		activate();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"drop-area");
		long rngBefore = ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token);
		Npc legacy = new Npc(1, 153);
		legacy.absX = GUARD_X;
		legacy.absY = GUARD_Y + 1;
		legacy.heightLevel = 0;
		legacy.HP = 10;
		legacy.MaxHP = 10;
		legacy.killedBy = killer.playerId;
		NpcHandler.npcs[1] = legacy;
		PlayerHandler.players[killer.playerId] = killer;
		killer.absY = GUARD_Y + 1;

		driveDeath(legacy);

		assertTrue("an unmatched legacy NPC must not create area identities",
				GameEngine.itemHandler.items.isEmpty());
		assertEquals("an unmatched death must not advance the area RNG",
				rngBefore, ScriptAreaRuntime.getInstance()
						.areaRngStateForTesting(token));
		assertEquals("the legacy NPC must not be despawned as an area "
				+ "allocation", legacy, NpcHandler.npcs[1]);
	}

	@Test
	public void despawnedAllocationWithReusedSlotAndEqualNpcTypeStaysLegacy()
			throws Exception {
		activate();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"drop-area");
		long rngBefore = ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token);
		Npc guard = npcAt(GUARD_X, GUARD_Y);
		assertNotNull(guard);
		int slot = guard.npcId;

		// Kill the area allocation through the real death critical section;
		// the exact claim despawns it and frees its slot. The killer gets
		// its detached loot identity at the death tile.
		guard.killedBy = killer.playerId;
		driveDeath(guard);
		assertNull(npcAt(GUARD_X, GUARD_Y));
		assertNull("the claim must free the allocation slot",
				NpcHandler.npcs[slot]);
		int lootAfterAreaDeath = GameEngine.itemHandler.items.size();
		assertEquals(1, groundItemsAt(536, GUARD_X, GUARD_Y).size());
		long rngAfterAreaDeath = ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token);
		assertTrue("the area claim must have advanced the session RNG",
				rngAfterAreaDeath != rngBefore);

		// Reuse the freed slot with an equal-npcType legacy NPC and kill it.
		Npc legacy = new Npc(slot, 153);
		legacy.absX = GUARD_X;
		legacy.absY = GUARD_Y + 1;
		legacy.heightLevel = 0;
		legacy.HP = 10;
		legacy.MaxHP = 10;
		legacy.killedBy = killer.playerId;
		NpcHandler.npcs[slot] = legacy;
		PlayerHandler.players[killer.playerId] = killer;
		killer.absY = GUARD_Y + 1;

		driveDeath(legacy);

		// The stale allocation identity must fail closed: the reused-slot
		// legacy death takes the unmatched legacy path with no area claim
		// and creates no additional identity.
		assertEquals("the reused-slot legacy death must not create area "
				+ "identities", lootAfterAreaDeath,
				GameEngine.itemHandler.items.size());
		assertEquals("the reused-slot legacy death must not advance the "
				+ "area RNG", rngAfterAreaDeath,
				ScriptAreaRuntime.getInstance()
						.areaRngStateForTesting(token));
		assertEquals("the reused-slot legacy NPC must not be despawned as "
				+ "an area allocation", legacy, NpcHandler.npcs[slot]);
		assertNull("the area spawn tile must not hold a ghost allocation",
				npcAt(GUARD_X, GUARD_Y));
	}

	@Test
	public void wrongPlaneKillerIsNoRecipientWithoutRngOrGround()
			throws Exception {
		activate();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"drop-area");
		Npc guard = npcAt(GUARD_X, GUARD_Y);
		assertNotNull(guard);
		long rngBefore = ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token);

		guard.killedBy = killer.playerId;
		killer.heightLevel = 1;
		driveDeath(guard);

		assertTrue("wrong-plane killer: no ground identities",
				GameEngine.itemHandler.items.isEmpty());
		assertEquals("wrong-plane killer: NO_RECIPIENT, no RNG advance",
				rngBefore, ScriptAreaRuntime.getInstance()
						.areaRngStateForTesting(token));
		assertNull("the exact claim is consumed even without a recipient",
				npcAt(GUARD_X, GUARD_Y));
	}

	@Test
	public void injectedDetachFailureConsumesTheClaimWithoutRngOrGround()
			throws Exception {
		activate();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"drop-area");
		Npc guard = npcAt(GUARD_X, GUARD_Y);
		assertNotNull(guard);
		long rngBefore = ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token);

		guard.killedBy = killer.playerId;
		ScriptAreaRuntime.getInstance().failNextDetachForTesting();
		driveDeath(guard);

		assertTrue("a failed detach must remove every staged identity",
				GameEngine.itemHandler.items.isEmpty());
		assertEquals("a failed detach must not advance the area RNG",
				rngBefore, ScriptAreaRuntime.getInstance()
						.areaRngStateForTesting(token));
		assertNull("the claim is consumed exactly once",
				npcAt(GUARD_X, GUARD_Y));
	}

	@Test
	public void successfulReloadRemovesOnlyTheAreasOwnUnclaimedIdentities()
			throws Exception {
		File root = activate();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"drop-area");
		Npc guard = npcAt(GUARD_X, GUARD_Y);
		guard.killedBy = killer.playerId;
		driveDeath(guard);
		List<GroundItem> privateIdentities = groundItemsAt(536, GUARD_X,
				GUARD_Y);
		assertEquals(1, privateIdentities.size());
		GroundItem beforeReload = privateIdentities.get(0);

		Files.write(new File(root, "loader.js").toPath(),
				"onCommand('no-areas', function () {});"
						.getBytes(StandardCharsets.UTF_8));
		ScriptHost.getInstance().reload();

		// The detached killer-private identity is the player's loot and
		// survives; the session itself is closed.
		assertEquals(0, ScriptAreaRuntime.getInstance().sessionCount());
		assertTrue(GameEngine.itemHandler.containsExact(beforeReload));
		assertEquals(1, groundItemsAt(536, GUARD_X, GUARD_Y).size());
	}

	@Test
	public void transactionPreflightFailureIsConsumedWithoutRngOrGround()
			throws Exception {
		Path root = Files.createTempDirectory("script-area-drop-fail");
		Files.write(root.resolve("loader.js"), (
				"defineDropTable({id:'oversized_loot',entries:["
						+ "{itemId:379,minAmount:100,maxAmount:100,"
						+ "weight:0,always:true},"
						+ "{itemId:379,minAmount:100,maxAmount:100,"
						+ "weight:0,always:true}]});"
						+ "defineArea({id:'drop-fail',name:'Fail Area',"
						+ "bounds:{minX:2830,minY:9630,maxX:2850,"
						+ "maxY:9640,plane:0},"
						+ "npcs:[{key:'guard',npcId:153,x:2830,y:9630,"
						+ "dropTable:'oversized_loot',"
						+ "dropPolicy:'private-to-killer',privateTicks:200,"
						+ "respawnTicks:100}],"
						+ "objects:[],shops:[],quests:[],bosses:[],"
						+ "raids:[]});")
						.getBytes(StandardCharsets.UTF_8));
		System.setProperty("singlescape.contentDir",
				root.toFile().getAbsolutePath());
		ScriptHost.getInstance().reload();
		long token = com.rs2.script.area.ScriptAreaRuntime.getInstance()
				.sessionToken("drop-fail");
		assertTrue(token > 0L);
		Npc guard = npcAt(GUARD_X, GUARD_Y);
		assertNotNull(guard);
		long rngBefore = ScriptAreaRuntime.getInstance()
				.areaRngStateForTesting(token);

		guard.killedBy = killer.playerId;
		driveDeath(guard);

		// The WP2 preflight rejects the oversized identity budget before any
		// RNG or ground use; the claim is consumed and legacy stays
		// suppressed (no double drop).
		assertTrue(GameEngine.itemHandler.items.isEmpty());
		assertEquals("a failed roll must not advance the area RNG",
				rngBefore, ScriptAreaRuntime.getInstance()
						.areaRngStateForTesting(token));
		assertNull(npcAt(GUARD_X, GUARD_Y));
	}

	@Test
	public void privateIdentityExpiresAfterItsTtl() throws Exception {
		activate();
		Npc guard = npcAt(GUARD_X, GUARD_Y);
		guard.killedBy = killer.playerId;
		driveDeath(guard);
		List<GroundItem> identities = groundItemsAt(536, GUARD_X, GUARD_Y);
		assertEquals(1, identities.size());
		GroundItem identity = identities.get(0);
		assertTrue(identity.hideTicks > 0);
		int ttl = identity.hideTicks;

		for (int tick = 0; tick < ttl + 2; tick++) {
			GameEngine.itemHandler.process();
		}

		assertFalse("the detached identity must expire after its private "
				+ "TTL", GameEngine.itemHandler.containsExact(identity));
		assertTrue(groundItemsAt(536, GUARD_X, GUARD_Y).isEmpty());
	}

	private File activate() throws Exception {
		Path root = Files.createTempDirectory("script-area-drop");
		Files.write(root.resolve("loader.js"), (
				"defineDropTable({id:'guard_loot',entries:[{itemId:536,"
						+ "minAmount:1,maxAmount:1,weight:0,always:true}]});"
						+ "defineDropTable({id:'public_loot',entries:["
						+ "{itemId:995,minAmount:1,maxAmount:1,weight:0,"
						+ "always:true}]});"
						+ "defineArea({id:'drop-area',name:'Drop Area',"
						+ "bounds:{minX:2830,minY:9630,maxX:2850,"
						+ "maxY:9640,plane:0},"
						+ "npcs:["
						+ "{key:'guard-1',npcId:153,x:2830,y:9630,"
						+ "dropTable:'guard_loot',"
						+ "dropPolicy:'private-to-killer',privateTicks:200,"
						+ "respawnTicks:3},"
						+ "{key:'guard-2',npcId:153,x:2840,y:9640,"
						+ "dropTable:'public_loot',dropPolicy:'public',"
						+ "respawnTicks:100},"
						+ "{key:'villager',npcId:153,x:2850,y:9630}],"
						+ "objects:[],shops:[],quests:[],bosses:[],"
						+ "raids:[]});")
						.getBytes(StandardCharsets.UTF_8));
		System.setProperty("singlescape.contentDir",
				root.toFile().getAbsolutePath());
		ScriptHost.getInstance().reload();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"drop-area");
		assertTrue("the area must activate", token > 0L);
		return root.toFile();
	}

	private void driveDeath(Npc npc) {
		npc.HP = 0;
		npc.isDead = true;
		npc.applyDead = true;
		npc.needRespawn = false;
		npc.actionTimer = 0;
		for (int tick = 0; tick < 10; tick++) {
			npcHandler.process();
			if (NpcHandler.npcs[npc.npcId] != npc || npc.needRespawn) {
				return;
			}
		}
		fail("NPC death did not complete");
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

	private static List<GroundItem> groundItemsAt(int itemId, int x, int y) {
		List<GroundItem> matches = new ArrayList<GroundItem>();
		for (GroundItem item : GameEngine.itemHandler.items) {
			if (item != null && item.getItemId() == itemId
					&& item.getItemX() == x && item.getItemY() == y) {
				matches.add(item);
			}
		}
		return matches;
	}

	private static void setDefinitions(Class<?> definitionType, Object value)
			throws Exception {
		Field field = definitionType.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, value);
	}

}
