package com.rs2.script;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apollo.util.security.IsaacRandom;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.Constants;
import com.rs2.GameEngine;
import com.rs2.event.CycleEventHandler;
import com.rs2.game.items.GroundItem;
import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcActions;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.npcs.NpcList;
import com.rs2.game.objects.ObjectsActions;
import com.rs2.game.players.Client;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.game.players.PlayerSave;
import com.rs2.net.Packet;
import com.rs2.net.packets.impl.Commands;
import com.rs2.script.area.ScriptAreaRuntime;
import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.definition.DefinitionRegistry;
import com.rs2.script.raid.ScriptRaidRuntime;
import com.rs2.script.resource.GatheringResourceDefinition;
import com.rs2.script.resource.GatheringResourceRegistry;
import com.rs2.script.resource.ScriptResourceRuntime;
import com.rs2.script.quest.ScriptedQuest;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.script.world.ScriptNpcService;
import com.rs2.util.Stream;
import com.rs2.world.ResolvedWorldObject;
import com.rs2.world.WorldObjectService;
import com.rs2.world.clip.Region;
import com.rs2.world.clip.RegionFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Phase 5 WP10 vertical content E2E.
 *
 * <p>Loads the compiled {@code content/dist} loader and in one flow crosses
 * every shipped declarative family through real engine paths: the content
 * manifest (8 source-aware modules), the activated Dragon Island area (NPC
 * spawns and layered object projections), the scripted shop through the
 * production shop assistant, the gathering resource on an area-projected
 * tree (harvest/deplete/respawn), the standalone Dragon King boss (command
 * entry, named drops, private pickup), the scripted quest state, a rejected
 * reload that keeps everything live, a successful reload that re-activates
 * the area and closes stale world projections, and zero residue on cleanup.
 * This is the first test that drives two or more distinct definition families
 * in one method against the compiled loader.
 */
public class VerticalContentE2ETest {

	// Dragon Island area (Crandor plane 0).
	private static final int AREA_MIN_X = 2830;
	private static final int AREA_MIN_Y = 9630;
	private static final int AREA_MAX_X = 2870;
	private static final int AREA_MAX_Y = 9670;

	// Area-projected objects.
	private static final int TREE_X = 2858;
	private static final int TREE_Y = 9660;
	private static final int TREE_ID = 1276;
	private static final int STUMP_ID = 1341;
	private static final int CHEST_X = 2850;
	private static final int CHEST_Y = 9640;
	private static final int CHEST_ID = 2213;

	// Shopkeeper allocation.
	private static final int SHOPKEEPER_X = 2845;
	private static final int SHOPKEEPER_Y = 9640;
	private static final int SHOPKEEPER_NPC = 1;

	// Gathering resource.
	private static final int AXE_ID = 1351;
	private static final int LOG_ID = 1511;
	private static final int WOODCUTTING = Constants.WOODCUTTING;

	// Dragon King boss (Crandor plane 1).
	private static final int BOSS_MIN_X = 2264;
	private static final int BOSS_MIN_Y = 4688;
	private static final int BOSS_PLANE = 1;
	private static final int BOSS_NPC = 54;

	private static final long SEED = 0x0123456789abcdefL;
	private static final long RAID_SEED = 0xfedcba9876543210L;
	private static final long RESOURCE_SEED = 0xdeadbeefcafebabeL;

	private final Player[] previousPlayers = PlayerHandler.players.clone();
	private final Npc[] previousNpcs = NpcHandler.npcs.clone();
	private final NpcList[] previousNpcList = NpcHandler.NpcList.clone();
	private final Region[] previousRegions;
	private final ArrayList<GroundItem> previousItems =
			new ArrayList<GroundItem>(GameEngine.itemHandler.items);
	private final String previousContentDir =
			System.getProperty("singlescape.contentDir");

	private RecordingPlayer player;
	private NpcHandler npcHandler;

	public VerticalContentE2ETest() throws Exception {
		previousRegions = regions();
	}

	@Before
	public void setUp() throws Exception {
		ScriptRuntimeTestFixture.reset();
		ScriptEncounterService.installForTesting(SEED);
		ScriptEncounterService.getInstance().resetForTesting();
		ScriptRaidRuntime.installForTesting(RAID_SEED);
		ScriptResourceRuntime.installForTesting(RESOURCE_SEED);
		Wp5PlayerSupport.ensureObjectDefinitions();
		Wp5PlayerSupport.ensureItemDefinitions();
		Wp5PlayerSupport.ensureNpcDefinitions();
		Arrays.fill(PlayerHandler.players, null);
		npcHandler = new NpcHandler();
		Arrays.fill(NpcHandler.npcs, null);
		setRegions(new Region[] {
				new Region(Region.getRegionId(AREA_MIN_X, AREA_MIN_Y), false),
				new Region(Region.getRegionId(AREA_MAX_X, AREA_MAX_Y), false),
				new Region(Region.getRegionId(BOSS_MIN_X, BOSS_MIN_Y), false)
		});
		GameEngine.itemHandler.items.clear();
		GameEngine.itemHandler.resetProjectionsForTesting();
		CycleEventHandler.getSingleton().stopEvents(null);

		player = lairPlayer(41, AREA_MIN_X, AREA_MIN_Y);
		loadCompiledContent();
	}

	@After
	public void tearDown() throws Exception {
		CycleEventHandler.getSingleton().stopEvents(player);
		// Reset while the fixture regions still carry their collision ledger,
		// so the reset can verify and clean it.
		ScriptRuntimeTestFixture.reset();
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
		System.arraycopy(previousNpcs, 0, NpcHandler.npcs, 0,
				previousNpcs.length);
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
	public void verticalFlowCrossesEveryFamilyWithReloadAndSaveLoad()
			throws Exception {
		// ─── 1. The content manifest is source-aware. ─────────────────────
		assertManifestModules();

		// ─── 2. The Dragon Island area is activated: NPCs and projections. ─
		assertAreaActivated();
		ResolvedWorldObject tree = WorldObjectService.getInstance()
				.resolve(TREE_X, TREE_Y, 0);
		assertNotNull("the area must project a tree", tree);
		assertEquals(TREE_ID, tree.getObjectId());

		// ─── 3. The scripted shop opens through the exact allocation. ─────
		openShop();

		// ─── 4. The gathering resource harvests the area-projected tree. ──
		harvestTreeThroughAreaProjection();

		// ─── 5. The standalone boss enters, dies, and drops. ──────────────
		standaloneBossFlow();

		// ─── 6. The scripted quest is registered and projectable; a real
		// save/load roundtrip preserves the started quest and script state. ──
		ScriptedQuest quest = new ScriptedPlayer(player).quest("dragon-awakens");
		assertNotNull(quest);
		assertEquals("dragon-awakens", quest.id());
		assertEquals("not_started", quest.state());
		assertTrue("the quest must be startable",
				quest.start().changed());
		assertEquals("in_progress", quest.state());
		assertEquals(Integer.valueOf(0), quest.stage());
		new ScriptedPlayer(player).state("dragon-awakens")
				.setBoolean("bones-recovered", true);
		player = saveAndReload(player);
		ScriptedQuest reloaded = new ScriptedPlayer(player)
				.quest("dragon-awakens");
		assertNotNull(reloaded);
		assertEquals("in_progress", reloaded.state());
		assertEquals(Integer.valueOf(0), reloaded.stage());
		assertEquals(true, new ScriptedPlayer(player)
				.state("dragon-awakens").getBoolean("bones-recovered"));

		// ─── 7. A rejected reload keeps the area and world live. ──────────
		long generation = ScriptHost.getInstance().getActiveGeneration();
		Path broken = Files.createTempDirectory("vertical-broken-reload");
		Files.write(broken.resolve("loader.js"),
				"throw new Error('vertical candidate rejected');"
						.getBytes(StandardCharsets.UTF_8));
		System.setProperty("singlescape.contentDir", broken.toString());
		ScriptHost.getInstance().reload();
		assertEquals(generation, ScriptHost.getInstance().getActiveGeneration());
		assertAreaActivated();
		assertTrue(ScriptResourceRuntime.getInstance().sessionToken(player) == 0L);

		// ─── 8. A successful reload re-activates the area and closes stale
		// state (the boss session is closed; the area is re-projected). ────
		System.setProperty("singlescape.contentDir",
				compiledContent().getAbsolutePath());
		ScriptHost.getInstance().reload();
		assertTrue(ScriptHost.getInstance().getActiveGeneration() > generation);
		assertAreaActivated();
		assertNull(ownedNpc(BOSS_NPC));
		assertEquals(0, ScriptEncounterService.getInstance()
				.encounterCountForTesting());
	}

	private void assertManifestModules() {
		List<String> moduleIds = ScriptHost.getInstance().readActiveRegistry(
				state -> {
					List<String> ids = new ArrayList<String>();
					for (com.rs2.script.definition.ModuleRecord record
							: state.manifest) {
						ids.add(record.id());
					}
					java.util.Collections.sort(ids);
					return ids;
				});
		assertEquals(Arrays.asList(
				"cooking-guide",
				"cooking-skills",
				"custom-namespace-overlays",
				"dragon-awakens",
				"dragon-island",
				"dragon-island-drops",
				"dragon-island-shops",
				"dragon-king",
				"encounter-warden",
				"fishing-resources",
				"mining-resources",
				"temple-of-zaros",
				"woodcutting-resources",
				"world-mobs"), moduleIds);
		assertEquals(14, ScriptHost.getInstance().getRuntimeReport()
				.moduleCount());

		// A module-scoped definition carries its source and schema version.
		DefinitionRecord boss = DefinitionRegistry.get(DefinitionKind.BOSS,
				"54");
		assertNotNull(boss);
		assertEquals("dragon-king", boss.source());
		assertEquals(1, boss.schemaVersion());
		DefinitionRecord area = DefinitionRegistry.get(DefinitionKind.AREA,
				"dragon_island");
		assertNotNull(area);
		assertEquals("dragon-island", area.source());
		assertEquals(1, area.schemaVersion());
	}

	private void assertAreaActivated() {
		assertEquals(1, ScriptAreaRuntime.getInstance().selectedAreaCount());
		assertTrue("the area must spawn the shopkeeper",
				npcIndexAt(SHOPKEEPER_NPC, SHOPKEEPER_X, SHOPKEEPER_Y) > 0);
		ResolvedWorldObject chest = WorldObjectService.getInstance()
				.resolve(CHEST_X, CHEST_Y, 0);
		assertNotNull("the area must project the chest", chest);
		assertEquals(CHEST_ID, chest.getObjectId());
	}

	private void openShop() {
		int shopkeeperIndex = npcIndexAt(SHOPKEEPER_NPC, SHOPKEEPER_X,
				SHOPKEEPER_Y);
		assertTrue("the area must spawn the shopkeeper",
				shopkeeperIndex > 0);
		// The clicked NPC must resolve as an area allocation so the exact
		// allocation-bound shop route (not the plain Lumbridge man route)
		// consumes the click.
		String[] binding = ScriptNpcService.getInstance().areaSpawnOf(
				NpcHandler.npcs[shopkeeperIndex]);
		assertNotNull("the shopkeeper must be an area allocation", binding);
		assertEquals("dragon_island", binding[0]);
		assertEquals("villager-shopkeeper", binding[1]);
		com.rs2.script.route.ExecutableRouteRecord allocatedRoute =
				ScriptHost.getInstance().readActiveRegistry(state ->
						com.rs2.script.route.RouteRegistry.get(state,
								com.rs2.script.route.ExecutableRouteKey
										.npcAllocated(SHOPKEEPER_NPC, "first",
												"dragon_island",
												"villager-shopkeeper")));
		assertNotNull("the allocated shop route must be registered",
				allocatedRoute);
		player.npcClickIndex = shopkeeperIndex;
		player.clickNpcType = SHOPKEEPER_NPC;
		NpcActions actions = new NpcActions(player);
		actions.firstClickNpc(SHOPKEEPER_NPC);
		assertTrue("the exact allocation route must consume the click",
				actions.wasLastClickHandledByScript());
		assertTrue(player.isShopping);
		assertEquals("dragon_island_general", player.scriptShopId);
		// Buy one tinderbox (590) at its declared price. The shop's scripted
		// buy path charges coins (995).
		player.getItemAssistant().addItem(995, 10000);
		assertTrue(player.getShopAssistant().buyItem(590, 5, 1));
		assertEquals(1, countItem(player, 590));
	}

	private void harvestTreeThroughAreaProjection() throws Exception {
		giveToolAndLevel(WOODCUTTING, 1);
		// The resource session requires the player to stay within interaction
		// range of the tree for the whole harvest.
		player.absX = TREE_X;
		player.absY = TREE_Y - 1;
		player.heightLevel = 0;
		ResolvedWorldObject tree = WorldObjectService.getInstance()
				.resolve(TREE_X, TREE_Y, 0);
		ObjectsActions actions = new ObjectsActions(player);
		player.objectX = TREE_X;
		player.objectY = TREE_Y;
		actions.firstClickObject(TREE_ID, TREE_X, TREE_Y, tree.getObject());
		assertTrue("the resource route must consume the click",
				actions.wasLastClickHandledByScript());
		long token = ScriptResourceRuntime.getInstance().sessionToken(player);
		assertTrue("a resource session must open", token != 0L);
		// The compiled tree resource is a 3/4 success chance, so the first
		// roll may miss; drive bounded attempt ticks until the harvest
		// commits (the session closes on success).
		for (int attempt = 0; attempt < 8; attempt++) {
			tick();
			if (ScriptResourceRuntime.getInstance().sessionToken(player) == 0L) {
				break;
			}
		}
		assertEquals(1, countItem(player, LOG_ID));
		assertEquals(25, player.playerXP[WOODCUTTING]);
		assertEquals(0L, ScriptResourceRuntime.getInstance().sessionToken(player));
		assertTrue(ScriptResourceRuntime.getInstance().isDepleted(
				resourceDefinition("tree"), TREE_X, TREE_Y, 0));
		for (int index = 0; index < 5; index++) {
			GameEngine.objectManager.process();
		}
		ScriptResourceRuntime.getInstance().processGameTick();
		assertFalse("the resource must respawn after the interval",
				ScriptResourceRuntime.getInstance().isDepleted(
						resourceDefinition("tree"), TREE_X, TREE_Y, 0));
	}

	private void standaloneBossFlow() throws Exception {
		// Enter through the real command route on the plane-1 arena.
		player.absX = BOSS_MIN_X;
		player.absY = BOSS_MIN_Y;
		player.heightLevel = BOSS_PLANE;
		command(player, "dragon-king");
		Npc boss = ownedNpc(BOSS_NPC);
		assertNotNull("the Dragon King must spawn", boss);
		assertEquals(1, ScriptEncounterService.getInstance()
				.encounterCountForTesting());

		// Kill through the production owned-death seam.
		killBoss(boss);
		runDeath();

		assertNull(ownedNpc(BOSS_NPC));
		assertEquals(0, ScriptEncounterService.getInstance()
				.encounterCountForTesting());

		// The named table rolls private detached ground deliveries.
		GroundItem bones = groundItem(536);
		assertNotNull(bones);
		assertTrue(bones.isScriptPrivate());
		assertTrue(bones.isDetached());
	}

	private static GatheringResourceDefinition resourceDefinition(String id) {
		return GatheringResourceRegistry.get(id);
	}

	private void command(Player player, String command) {
		ByteBuf payload = Unpooled.buffer(command.length() + 1);
		payload.writeBytes(command.getBytes(StandardCharsets.UTF_8));
		payload.writeByte(10);
		new Commands().processPacket(player,
				new Packet(103, Packet.Type.FIXED, payload));
	}

	private static void tick() {
		ScriptLifecycleService.getInstance().processGameTick();
	}

	private void killBoss(Npc boss) {
		boss.HP = 0;
		boss.isDead = true;
		boss.applyDead = true;
		boss.needRespawn = false;
		boss.actionTimer = 0;
		boss.killedBy = player.playerId;
		boss.killerId = 0;
	}

	private void runDeath() {
		for (int tick = 0; tick < 10; tick++) {
			npcHandler.process();
			if (ownedNpc(BOSS_NPC) == null) {
				return;
			}
		}
		throw new AssertionError("boss death did not complete");
	}

	private void giveToolAndLevel(int skill, int level) {
		player.getItemAssistant().addItem(AXE_ID, 1);
		player.playerLevel[skill] = level;
		player.playerXP[skill] = 0;
	}

	private RecordingPlayer saveAndReload(RecordingPlayer current)
			throws Exception {
		String previousUserDir = System.getProperty("user.dir");
		Path root = Files.createTempDirectory("vertical-player-save");
		Files.createDirectories(root.resolve("data").resolve("characters"));
		System.setProperty("user.dir", root.toString());
		PlayerHandler.players[current.playerId] = current;
		assertTrue("the player must save", PlayerSave.saveGame(current));

		RecordingPlayer loaded = lairPlayer(current.playerId,
				current.absX, current.absY);
		assertTrue("the player must load",
				PlayerSave.loadPlayerInfo(loaded, current.playerName,
						"password", false) > 0);
		PlayerHandler.players[current.playerId] = loaded;
		System.setProperty("user.dir", previousUserDir);
		return loaded;
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

	private static int npcIndexAt(int npcType, int x, int y) {
		for (int index = 1; index < NpcHandler.MAX_NPCS; index++) {
			Npc npc = NpcHandler.npcs[index];
			if (npc != null && npc.npcType == npcType && npc.absX == x
					&& npc.absY == y) {
				return index;
			}
		}
		return -1;
	}

	private static GroundItem groundItem(int itemId) {
		for (GroundItem item : GameEngine.itemHandler.items) {
			if (item.getItemId() == itemId) {
				return item;
			}
		}
		return null;
	}

	private static int countItem(Player player, int itemId) {
		int total = 0;
		for (int index = 0; index < player.playerItems.length; index++) {
			if (player.playerItems[index] == itemId + 1) {
				total += player.playerItemsN[index];
			}
		}
		return total;
	}

	private RecordingPlayer lairPlayer(int slot, int x, int y) {
		RecordingPlayer player = new RecordingPlayer(slot);
		player.playerName = "vertical-player-" + slot;
		player.playerName2 = player.playerName;
		player.playerPass = "password";
		player.saveFile = true;
		player.saveCharacter = true;
		player.newPlayer = false;
		player.initialized = true;
		player.isActive = true;
		player.disconnected = false;
		player.isDead = false;
		player.respawnTimer = 0;
		player.tutorialProgress = 36;
		player.absX = x;
		player.absY = y;
		player.heightLevel = 0;
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
	}
}
