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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.apollo.util.security.IsaacRandom;

import com.rs2.Constants;
import com.rs2.GameEngine;
import com.rs2.event.CycleEventHandler;
import com.rs2.game.items.GroundItem;
import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.npcs.NpcList;
import com.rs2.game.players.Client;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.net.Packet;
import com.rs2.net.packets.impl.ClickNPC;
import com.rs2.net.packets.impl.Commands;
import com.rs2.net.packets.impl.PickupItem;
import com.rs2.net.packets.impl.Walking;
import com.rs2.script.capability.ScriptCameraSession;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.script.world.ScriptNpcService;
import com.rs2.util.Stream;
import com.rs2.world.clip.Region;
import com.rs2.world.clip.RegionFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Phase 4 WP7 production boss E2E.
 *
 * <p>The full flow crosses the compiled {@code content/dist} loader, real
 * command/pickup/walking/click packet decoding, the script scheduler, the
 * production NPC death loop, exact owned-drop staging, private pickup, and
 * every close path (explicit, death, logout, callback throw, reload).
 */
public class ScriptBossProductionE2ETest {

	private static final int MIN_X = 2264;
	private static final int MIN_Y = 4688;
	private static final int MAX_X = 2287;
	private static final int MAX_Y = 4711;
	private static final int PLANE = 1;
	private static final int OUTSIDE_X = 2263;
	private static final int OUTSIDE_Y = 4696;
	private static final int OWNER_X = 2271;
	private static final int OWNER_Y = 4696;
	private static final int BOSS_X = 2271;
	private static final int BOSS_Y = 4698;
	private static final int BARRIER_X = 2275;
	private static final int BARRIER_Y = 4698;
	private static final int REGION_ID = 9033;
	private static final long SEED = 0x0123456789abcdefL;

	private final Player[] previousPlayers = PlayerHandler.players.clone();
	private final Npc[] previousNpcs = NpcHandler.npcs.clone();
	private final NpcList[] previousNpcList = NpcHandler.NpcList.clone();
	private final Region[] previousRegions;
	private final ArrayList<GroundItem> previousItems =
			new ArrayList<GroundItem>(GameEngine.itemHandler.items);
	private final String previousContentDir =
			System.getProperty("singlescape.contentDir");

	private RecordingPlayer owner;
	private RecordingPlayer observer;
	private NpcHandler npcHandler;

	public ScriptBossProductionE2ETest() throws Exception {
		previousRegions = regions();
	}

	@Before
	public void setUp() throws Exception {
		ScriptRuntimeTestFixture.reset();
		ScriptEncounterService.installForTesting(SEED);
		ScriptEncounterService.getInstance().resetForTesting();
		Wp5PlayerSupport.ensureObjectDefinitions();
		Wp5PlayerSupport.ensureItemDefinitions();
		Arrays.fill(PlayerHandler.players, null);
		npcHandler = new NpcHandler();
		Arrays.fill(NpcHandler.npcs, null);
		setRegions(new Region[] { new Region(REGION_ID, false) });
		GameEngine.itemHandler.items.clear();
		GameEngine.itemHandler.resetProjectionsForTesting();
		CycleEventHandler.getSingleton().stopEvents(null);

		owner = lairPlayer(31, OUTSIDE_X, OUTSIDE_Y);
		observer = lairPlayer(32, OUTSIDE_X, OUTSIDE_Y);
		loadCompiledContent();
	}

	@After
	public void tearDown() throws Exception {
		CycleEventHandler.getSingleton().stopEvents(owner);
		CycleEventHandler.getSingleton().stopEvents(observer);
		// Close any still-open encounters while the fixture regions are loaded,
		// so collision restoration can verify its ledger cells.
		ScriptRuntimeTestFixture.reset();
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
		System.arraycopy(previousNpcs, 0, NpcHandler.npcs, 0, previousNpcs.length);
		System.arraycopy(previousNpcList, 0, NpcHandler.NpcList, 0,
				previousNpcList.length);
		System.arraycopy(previousPlayers, 0, PlayerHandler.players, 0,
				previousPlayers.length);
		setRegions(previousRegions);
		GameEngine.itemHandler.items.clear();
		GameEngine.itemHandler.items.addAll(previousItems);
		GameEngine.itemHandler.resetProjectionsForTesting();
	}

	@Test
	public void entryReservesTeleportsLocksBarrierAndExplicitCloseReleasesAll()
			throws Exception {
		command(owner, "encounter-warden");
		assertTrue("entry encounter must lock",
				ScriptEncounterService.getInstance().isActionLocked(owner));
		owner.getNextPlayerMovement();
		assertEquals(OWNER_X, owner.absX);
		assertEquals(OWNER_Y, owner.absY);
		assertEquals(PLANE, owner.heightLevel);
		assertTrue(ScriptEncounterService.getInstance().isActionLocked(owner));
		assertTrue(ScriptEncounterService.getInstance().isMovementLocked(owner));
		assertFalse(cameraFree());
		Npc boss = ownedNpc(50);
		assertNotNull(boss);
		assertEquals(BOSS_X, boss.absX);
		assertEquals(BOSS_Y, boss.absY);
		assertTrue(barrierClipping(BARRIER_X) != 0);
		assertTrue(barrierClipping(BARRIER_X + 1) != 0);

		// Observer: competing entry is arena-busy with no teleport/mutation.
		command(observer, "encounter-warden");
		observer.getNextPlayerMovement();
		assertEquals(OUTSIDE_X, observer.absX);
		assertEquals(OUTSIDE_Y, observer.absY);
		assertFalse(ScriptEncounterService.getInstance().isActionLocked(observer));

		// Observer: walking east into the reserved rectangle is refused.
		new Walking().processPacket(observer, walkingPacket(MIN_X, OUTSIDE_Y));
		assertEquals(OUTSIDE_X, observer.absX);
		assertEquals(OUTSIDE_Y, observer.absY);

		// Observer: the NPC update predicate excludes the owned boss.
		assertFalse(ScriptNpcService.getInstance().canAct(boss, observer));
		assertTrue(ScriptNpcService.getInstance().canAct(boss, owner));

		// Observer: forged attack, magic, and click packets and a direct
		// combat continuation change no HP.
		int bossHp = boss.HP;
		int bossSlot = slotOf(boss);
		observer.getCombatAssistant().attackNpc(bossSlot);
		new ClickNPC().processPacket(observer, attackPacket(bossSlot));
		new ClickNPC().processPacket(observer, magePacket(bossSlot, 51));
		new ClickNPC().processPacket(observer, attackPacket(bossSlot));
		assertEquals(bossHp, boss.HP);
		assertEquals(0, observer.npcIndex);
		assertEquals(0, observer.oldNpcIndex);
		assertEquals(0, observer.followNpcId);

		// Entry locks expire on tick 4; the entry camera expires on tick 6.
		for (int tick = 1; tick <= 3; tick++) {
			tick();
		}
		assertTrue(ScriptEncounterService.getInstance().isActionLocked(owner));
		assertTrue(ScriptEncounterService.getInstance().isMovementLocked(owner));
		assertFalse(cameraFree());
		tick();
		assertFalse(ScriptEncounterService.getInstance().isActionLocked(owner));
		assertFalse(ScriptEncounterService.getInstance().isMovementLocked(owner));
		assertFalse(cameraFree());
		tick();
		tick();
		assertTrue(cameraFree());

		// Explicit close releases NPCs, barrier, camera, and reservation.
		command(owner, "encounter-warden-close");
		assertNull(ownedNpc(50));
		assertNull(ownedNpc(90));
		assertEquals(0, barrierClipping(BARRIER_X));
		assertEquals(0, barrierClipping(BARRIER_X + 1));
		assertTrue(cameraFree());
		assertFalse(ScriptEncounterService.getInstance().isActionLocked(owner));
		command(observer, "encounter-warden");
		observer.getNextPlayerMovement();
		assertEquals(OWNER_X, observer.absX);
		command(observer, "encounter-warden-close");
	}

	@Test
	public void phaseThresholdRunsOnceWithExactLockCameraAndProjectileOrder()
			throws Exception {
		command(owner, "encounter-warden");
		owner.getNextPlayerMovement();
		Npc boss = ownedNpc(50);
		assertNotNull(boss);

		// Entry locks (4) and the entry camera (6) expire first.
		for (int tick = 1; tick <= 6; tick++) {
			tick();
		}
		assertFalse(ScriptEncounterService.getInstance().isActionLocked(owner));
		assertFalse(ScriptEncounterService.getInstance().isMovementLocked(owner));
		assertTrue(cameraFree());

		boss.HP = 100;
		tick(); // currentTick 7: poll fires the phase once
		assertEquals(2, countOwned(90));
		for (int index = 1; index < NpcHandler.MAX_NPCS; index++) {
			Npc npc = NpcHandler.npcs[index];
			if (npc != null && npc.npcType == 90) {
				assertFalse(ScriptNpcService.getInstance().canAct(npc, observer));
			}
		}
		assertEquals(1590, boss.animNumber);
		assertEquals(246, boss.mask80var1);
		assertTrue(ScriptEncounterService.getInstance().isActionLocked(owner));
		assertFalse(cameraFree());

		tick();
		tick(); // currentTick 9: the 2-tick phase locks expire
		assertFalse(ScriptEncounterService.getInstance().isActionLocked(owner));
		assertFalse(cameraFree());

		tick();
		tick(); // currentTick 11: projectile fires, phase camera resets
		assertEquals(5, owner.playerLevel[Constants.HITPOINTS]);
		assertTrue(cameraFree());

		assertEquals(2, countOwned(90));
		assertTrue(cameraFree());
		assertFalse(ScriptEncounterService.getInstance().isActionLocked(owner));
	}

	@Test
	public void ownedDeathRollsExactRewardsAndPrivatePickupCompletes()
			throws Exception {
		command(owner, "encounter-warden");
		owner.getNextPlayerMovement();
		Npc boss = ownedNpc(50);
		assertNotNull(boss);
		killBoss(boss);
		runDeath();

		assertNull(ownedNpc(50));
		assertNull(ownedNpc(90));
		assertEquals(0, barrierClipping(BARRIER_X));
		assertEquals(0, barrierClipping(BARRIER_X + 1));

		GroundItem bones = groundItem(536);
		GroundItem coins = groundItem(995);
		assertNotNull(bones);
		assertNotNull(coins);
		assertTrue(bones.isScriptPrivate());
		assertTrue(bones.isDetached());
		assertEquals(1, bones.getItemAmount());
		assertTrue(coins.isScriptPrivate());
		assertTrue(coins.isDetached());
		assertEquals(500, coins.getItemAmount());

		// Observer cannot resolve or pick up the private rewards.
		int observerTotal = inventoryCount(observer, 536)
				+ inventoryCount(observer, 995);
		new PickupItem().processPacket(observer,
				pickupPacket(536, BOSS_X, BOSS_Y));
		CycleEventHandler.getSingleton().process();
		assertEquals(observerTotal, inventoryCount(observer, 536)
				+ inventoryCount(observer, 995));
		assertTrue(GameEngine.itemHandler.containsExact(bones));

		// Owner picks up both through real opcode 236.
		owner.getPlayerAssistant().movePlayer(OWNER_X, OWNER_Y + 1, PLANE);
		owner.getNextPlayerMovement();
		new PickupItem().processPacket(owner,
				pickupPacket(536, BOSS_X, BOSS_Y));
		CycleEventHandler.getSingleton().process();
		assertEquals(1, inventoryCount(owner, 536));
		new PickupItem().processPacket(owner,
				pickupPacket(995, BOSS_X, BOSS_Y));
		CycleEventHandler.getSingleton().process();
		assertEquals(500, inventoryCount(owner, 995));
		assertTrue(GameEngine.itemHandler.items.isEmpty());

		// Reservation was released: the observer can now take the arena.
		command(observer, "encounter-warden");
		observer.getNextPlayerMovement();
		assertEquals(OWNER_X, observer.absX);
		command(observer, "encounter-warden-close");
	}

	@Test
	public void ownerDeathAndLogoutCloseTheEncounter() throws Exception {
		command(owner, "encounter-warden");
		owner.getNextPlayerMovement();
		assertNotNull(ownedNpc(50));
		owner.isDead = true;
		owner.respawnTimer = -6;
		assertNotNull(ScriptEncounterService.getInstance()
				.beginPlayerDeath(owner));
		owner.isDead = false;
		owner.respawnTimer = 0;
		assertNull(ownedNpc(50));
		assertNull(ownedNpc(90));
		assertEquals(0, barrierClipping(BARRIER_X));
		assertEquals(0, barrierClipping(BARRIER_X + 1));

		command(observer, "encounter-warden");
		observer.getNextPlayerMovement();
		assertEquals(OWNER_X, observer.absX);
		ScriptEncounterService.getInstance().onPlayerLogout(observer);
		assertNull(ownedNpc(50));
		assertEquals(0, barrierClipping(BARRIER_X));
	}

	@Test
	public void rejectedReloadKeepsEncounterLiveAndSuccessfulReloadClosesIt()
			throws Exception {
		command(owner, "encounter-warden");
		owner.getNextPlayerMovement();
		Npc boss = ownedNpc(50);
		assertNotNull(boss);

		Path broken = Files.createTempDirectory("warden-broken-reload");
		Files.write(broken.resolve("loader.js"),
				"this is not valid javascript !!!"
						.getBytes(StandardCharsets.UTF_8));
		System.setProperty("singlescape.contentDir",
				broken.toFile().getAbsolutePath());
		ScriptHost.getInstance().reload();
		assertEquals(boss, ownedNpc(50));
		assertTrue(ScriptEncounterService.getInstance().isActionLocked(owner));
		assertTrue(barrierClipping(BARRIER_X) != 0);

		System.setProperty("singlescape.contentDir",
				compiledContent().getAbsolutePath());
		ScriptHost.getInstance().reload();
		assertNull(ownedNpc(50));
		assertEquals(0, barrierClipping(BARRIER_X));
		command(observer, "encounter-warden");
		observer.getNextPlayerMovement();
		assertEquals(OWNER_X, observer.absX);
		command(observer, "encounter-warden-close");
	}

	@Test
	public void throwingDeathCallbackClosesTheEncounter() throws Exception {
		Path root = Files.createTempDirectory("warden-throw");
		Files.write(root.resolve("loader.js"), (
				"onCommand('warden-throw', c => {"
				+ "const e = c.player.beginEncounter('throw-warden',"
				+ "2264,4688,2287,4711,1);"
				+ "if (e === null) return;"
				+ "c.player.teleport(2271,4696,1);"
				+ "const boss = e.spawnNpc(50,2271,4698,1,240,30,350,350);"
				+ "e.onNpcDeath(boss, d => { throw new Error('expected-throw'); });"
				+ "});").getBytes(StandardCharsets.UTF_8));
		System.setProperty("singlescape.contentDir",
				root.toFile().getAbsolutePath());
		ScriptHost.getInstance().reload();

		command(owner, "warden-throw");
		owner.getNextPlayerMovement();
		Npc boss = ownedNpc(50);
		assertNotNull(boss);
		killBoss(boss);
		runDeath();
		assertNull(ownedNpc(50));
		command(observer, "warden-throw");
		observer.getNextPlayerMovement();
		assertEquals(OWNER_X, observer.absX);
	}

	private void killBoss(Npc boss) {
		boss.HP = 0;
		boss.isDead = true;
		boss.applyDead = true;
		boss.needRespawn = false;
		boss.actionTimer = 0;
		boss.killedBy = owner.playerId;
		boss.killerId = 0;
	}

	private void runDeath() {
		for (int tick = 0; tick < 10; tick++) {
			npcHandler.process();
			if (ownedNpc(50) == null) {
				return;
			}
		}
		fail("boss death did not complete");
	}

	private static void tick() {
		ScriptLifecycleService.getInstance().processGameTick();
	}

	private boolean cameraFree() {
		long generation = ScriptHost.getInstance().getActiveGeneration();
		long epoch = new ScriptedPlayer(owner).facadeEpoch();
		ScriptCameraSession probe = ScriptEncounterService.getInstance()
				.beginCamera(owner, generation, epoch, 1);
		if (probe == null) {
			return false;
		}
		ScriptEncounterService.getInstance().releaseCamera(probe);
		return true;
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

	private static int countOwned(int npcType) {
		int count = 0;
		for (int index = 1; index < NpcHandler.MAX_NPCS; index++) {
			Npc npc = NpcHandler.npcs[index];
			if (npc != null && npc.npcType == npcType
					&& ScriptNpcService.getInstance().isOwned(npc)) {
				count++;
			}
		}
		return count;
	}

	private static int slotOf(Npc npc) {
		for (int index = 1; index < NpcHandler.MAX_NPCS; index++) {
			if (NpcHandler.npcs[index] == npc) {
				return index;
			}
		}
		return -1;
	}

	private static int barrierClipping(int x) {
		return Region.getClipping(x, BARRIER_Y, PLANE);
	}

	private static GroundItem groundItem(int itemId) {
		for (GroundItem item : GameEngine.itemHandler.items) {
			if (item.getItemId() == itemId) {
				return item;
			}
		}
		return null;
	}

	private static int inventoryCount(Player player, int itemId) {
		int total = 0;
		for (int index = 0; index < player.playerItems.length; index++) {
			if (player.playerItems[index] == itemId + 1) {
				total += Math.max(0, player.playerItemsN[index]);
			}
		}
		return total;
	}

	private void command(Player player, String command) {
		ByteBuf payload = Unpooled.buffer(command.length() + 1);
		payload.writeBytes(command.getBytes(StandardCharsets.UTF_8));
		payload.writeByte(10);
		new Commands().processPacket(player,
				new Packet(103, Packet.Type.FIXED, payload));
	}

	private static Packet walkingPacket(int x, int y) {
		ByteBuf payload = Unpooled.buffer(5);
		payload.writeByte(x + 128);
		payload.writeByte(x >> 8);
		payload.writeByte(y);
		payload.writeByte(y >> 8);
		payload.writeByte(0);
		return new Packet(164, Packet.Type.FIXED, payload);
	}

	private static Packet attackPacket(int npcIndex) {
		ByteBuf payload = Unpooled.buffer(2);
		payload.writeByte(npcIndex >> 8);
		payload.writeByte(npcIndex + 128);
		return new Packet(ClickNPC.ATTACK_NPC, Packet.Type.FIXED, payload);
	}

	private static Packet magePacket(int npcIndex, int spellId) {
		ByteBuf payload = Unpooled.buffer(4);
		payload.writeByte(npcIndex + 128);
		payload.writeByte(npcIndex >> 8);
		payload.writeByte(spellId >> 8);
		payload.writeByte(spellId + 128);
		return new Packet(ClickNPC.MAGE_NPC, Packet.Type.FIXED, payload);
	}

	private static Packet pickupPacket(int itemId, int x, int y) {
		ByteBuf payload = Unpooled.buffer(6);
		payload.writeByte(y);
		payload.writeByte(y >> 8);
		payload.writeByte(itemId >> 8);
		payload.writeByte(itemId);
		payload.writeByte(x);
		payload.writeByte(x >> 8);
		return new Packet(236, Packet.Type.FIXED, payload);
	}

	private RecordingPlayer lairPlayer(int slot, int x, int y) {
		RecordingPlayer player = new RecordingPlayer(slot);
		player.playerName = "warden-player-" + slot;
		player.initialized = true;
		player.isActive = true;
		player.disconnected = false;
		player.isDead = false;
		player.respawnTimer = 0;
		player.tutorialProgress = 36;
		player.absX = x;
		player.absY = y;
		player.heightLevel = PLANE;
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

	private static void loadCompiledContent() {
		File contentDir = compiledContent();
		assertTrue("Run pnpm build:content before Maven tests",
				contentDir.isDirectory());
		System.setProperty("singlescape.contentDir",
				contentDir.getAbsolutePath());
		ScriptHost.getInstance().reload();
		assertNotNull("encounter-warden must register",
				com.rs2.script.registries.CommandHandlerRegistry
						.get("encounter-warden"));
	}

	private static File compiledContent() {
		File fromWorkspace = new File(
				System.getProperty("user.dir"), "content/dist");
		if (fromWorkspace.isDirectory()) {
			return fromWorkspace;
		}
		return new File(System.getProperty("user.dir"), "../../content/dist");
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

	private static final class RecordingPlayer extends Client {
		RecordingPlayer(int slot) {
			super(null, slot);
		}

		@Override
		public void flushOutStream() {
			if (outStream != null) {
				outStream.currentOffset = 0;
			}
		}

		@Override
		public void updateWalkEntities() {
		}
	}
}
