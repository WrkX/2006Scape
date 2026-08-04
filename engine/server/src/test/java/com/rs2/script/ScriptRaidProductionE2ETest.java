package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
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
import com.rs2.net.packets.impl.Commands;
import com.rs2.script.raid.ScriptRaidRuntime;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.script.world.ScriptNpcService;
import com.rs2.util.Stream;
import com.rs2.world.clip.Region;
import com.rs2.world.clip.RegionFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Phase 5 WP5 production raid E2E.
 *
 * <p>The full flow crosses the compiled {@code content/dist} loader, real
 * command packet decoding for the create/invite/join/start lobby, real game
 * ticks, the embedded declarative boss (the compiled {@code dragon-king}
 * borrowing the raid's sole encounter), the production NPC death loop, the
 * roster-wide named reward commit for two distinct live players, the
 * reward-table roll as private ground deliveries, the once-only
 * {@code onComplete}, and zero residue on close.
 */
public class ScriptRaidProductionE2ETest {

	private static final int MIN_X = 2264;
	private static final int MIN_Y = 4688;
	private static final int MAX_X = 2287;
	private static final int MAX_Y = 4711;
	private static final int PLANE = 1;
	private static final int MUSTER_X = 2264;
	private static final int MUSTER_Y = 4688;
	private static final int REGION_ID = 9033;
	private static final long SEED = 0x0123456789abcdefL;
	private static final long RAID_SEED = 0xfedcba9876543210L;

	private final Player[] previousPlayers = PlayerHandler.players.clone();
	private final Npc[] previousNpcs = NpcHandler.npcs.clone();
	private final NpcList[] previousNpcList = NpcHandler.NpcList.clone();
	private final Region[] previousRegions;
	private final ArrayList<GroundItem> previousItems =
			new ArrayList<GroundItem>(GameEngine.itemHandler.items);
	private final String previousContentDir =
			System.getProperty("singlescape.contentDir");

	private RecordingPlayer owner;
	private RecordingPlayer invitee;

	public ScriptRaidProductionE2ETest() throws Exception {
		previousRegions = regions();
	}

	@Before
	public void setUp() throws Exception {
		ScriptRuntimeTestFixture.reset();
		ScriptEncounterService.installForTesting(SEED);
		ScriptEncounterService.getInstance().resetForTesting();
		ScriptRaidRuntime.installForTesting(RAID_SEED);
		Wp5PlayerSupport.ensureObjectDefinitions();
		Wp5PlayerSupport.ensureItemDefinitions();
		Wp5PlayerSupport.ensureNpcDefinitions();
		Arrays.fill(PlayerHandler.players, null);
		Arrays.fill(NpcHandler.npcs, null);
		setRegions(new Region[] {
				new Region(REGION_ID, false),
				new Region(Region.getRegionId(2830, 9630), false),
				new Region(Region.getRegionId(2870, 9670), false)
		});
		GameEngine.itemHandler.items.clear();
		GameEngine.itemHandler.resetProjectionsForTesting();
		CycleEventHandler.getSingleton().stopEvents(null);

		owner = lairPlayer(31, MUSTER_X, MUSTER_Y);
		invitee = lairPlayer(32, MUSTER_X + 6, MUSTER_Y + 2);
		loadCompiledContent();
	}

	@After
	public void tearDown() throws Exception {
		CycleEventHandler.getSingleton().stopEvents(owner);
		CycleEventHandler.getSingleton().stopEvents(invitee);
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
	public void twoPlayerRaidStartsThroughTheRealCommandRouteAndCommits()
			throws Exception {
		command(owner, "temple-of-zaros create");
		command(owner, "temple-of-zaros invite raid-player-32");
		command(invitee, "temple-of-zaros join raid-player-31");
		command(owner, "temple-of-zaros start");

		// One session with both members and one encounter reservation.
		assertEquals(1, ScriptRaidRuntime.getInstance().sessionCount());
		assertEquals(2, ScriptRaidRuntime.getInstance().membershipCount());
		assertEquals(1, ScriptEncounterService.getInstance()
				.encounterCountForTesting());
		assertEquals(2, ScriptEncounterService.getInstance()
				.participantCountForTesting());

		// The guardian room completes after three ticks and the crypt
		// embeds the compiled dragon-king boss on the raid's sole handle.
		for (int index = 0; index < 3; index++) {
			tick();
		}
		Npc boss = ownedNpc(54);
		assertNotNull("the embedded dragon-king must spawn", boss);

		// The boss dies through the real owned-death seam; the room
		// completes, the reward barrier commits the named reward to both
		// members, the reward table rolls private ground deliveries, and
		// the raid closes with zero residue.
		killBoss(boss);
		tick();
		tick();

		assertEquals(0, ScriptRaidRuntime.getInstance().sessionCount());
		assertEquals(0, ScriptRaidRuntime.getInstance().membershipCount());
		assertEquals(0, ScriptEncounterService.getInstance()
				.encounterCountForTesting());
		assertNull(ownedNpc(54));

		// Both members received the canonical zaros_raid_reward.
		assertEquals(50000, countItem(owner, 995));
		assertEquals(50000, countItem(invitee, 995));
		assertEquals(1, countItem(owner, 1127));
		assertEquals(1, countItem(invitee, 1127));
		assertEquals(1, countItem(owner, 1305));
		assertEquals(1, countItem(invitee, 1215));

		// The zaros_raid_loot table rolled as private detached ground
		// deliveries for both members.
		int privateGround = 0;
		for (GroundItem item : GameEngine.itemHandler.items) {
			if (item.isScriptPrivate() && item.isDetached()) {
				privateGround++;
			}
		}
		assertTrue("the reward table must deliver private ground items",
				privateGround >= 2);
	}

	// ─── Helpers ────────────────────────────────────────────────────────────

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

	private RecordingPlayer lairPlayer(int slot, int x, int y) {
		RecordingPlayer player = new RecordingPlayer(slot);
		player.playerName = "raid-player-" + slot;
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

	private void killBoss(Npc npc) {
		npc.HP = 0;
		npc.isDead = true;
		ScriptNpcService service = ScriptNpcService.getInstance();
		assertTrue(service.beginDeath(npc));
		service.dispatchDeath(npc, owner,
				new ScriptedPosition(npc.absX, npc.absY, npc.heightLevel));
		service.finishDeath(npc);
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

	private static int countItem(Player player, int itemId) {
		int count = 0;
		for (int index = 0; index < player.playerItems.length; index++) {
			if (player.playerItems[index] == itemId + 1) {
				count += player.playerItemsN[index];
			}
		}
		return count;
	}

	private static void loadCompiledContent() {
		File contentDir = compiledContent();
		assertTrue("Run pnpm build:content before Maven tests",
				contentDir.isDirectory());
		System.setProperty("singlescape.contentDir",
				contentDir.getAbsolutePath());
		ScriptHost.getInstance().reload();
		com.rs2.script.route.ExecutableRouteRecord raidRoute =
				ScriptHost.getInstance().readActiveRegistry(
						state -> com.rs2.script.registries
								.CommandHandlerRegistry.getRecord(state,
										"temple-of-zaros"));
		assertNotNull("temple-of-zaros must register an exact host route",
				raidRoute);
		assertTrue("the raid route must be a Java host consumer",
				!raidRoute.isGuest());
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
