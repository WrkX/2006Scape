package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.GameEngine;
import com.rs2.game.items.GroundItem;
import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.net.Packet;
import com.rs2.net.packets.impl.Commands;
import com.rs2.script.raid.ScriptRaidRuntime;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.script.world.ScriptNpcHandle;
import com.rs2.script.world.ScriptNpcService;
import com.rs2.util.Stream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Proves the declarative raid runtime: the exact create/invite/join/leave/
 * start lobby contract, the single frozen owner-first/join-FIFO roster, one
 * encounter per started raid, embedded boss rooms borrowing that sole
 * handle, room advancement, the reward barrier with the once-only award and
 * reward-table roll, deterministic wipe paths (timeout, owner departure,
 * zero active members, boss failure, barrier departure, grace expiry),
 * rejected reload preservation, accepted-reload cleanup, and zero residue.
 */
public class ScriptRaidRuntimeTest {

	private static final long SEED = 0x0123456789abcdefL;
	private static final long RAID_SEED = 0xfedcba9876543210L;
	private static final int MIN_X = 3200;
	private static final int MIN_Y = 3200;
	private static final int MAX_X = 3210;
	private static final int MAX_Y = 3210;
	private static final int PLANE = 0;
	private static final String RAID_ID = "test-raid";
	private static final String COMMAND = "test-raid";

	private ScriptEncounterTestSupport support;
	private Context context;
	private Npc[] previousNpcs;
	private ArrayList<GroundItem> previousItems;
	private ScriptEncounterTestSupport.TestClient owner;
	private ScriptEncounterTestSupport.TestClient invitee;
	private String previousContentDir;

	@Before
	public void setUp() throws Exception {
		previousContentDir = System.getProperty("singlescape.contentDir");
		support = new ScriptEncounterTestSupport();
		ScriptEncounterService.installForTesting(SEED);
		ScriptEncounterService.getInstance().resetForTesting();
		ScriptRaidRuntime.installForTesting(RAID_SEED);
		context = support.publishEmpty();
		previousNpcs = NpcHandler.npcs.clone();
		Arrays.fill(NpcHandler.npcs, null);
		previousItems = new ArrayList<GroundItem>(GameEngine.itemHandler.items);
		GameEngine.itemHandler.items.clear();
		Wp5PlayerSupport.ensureItemDefinitions();
		owner = support.player(1, MIN_X, MIN_Y, PLANE);
		invitee = support.player(2, MIN_X, MIN_Y, PLANE);
		registerRaid();
	}

	@After
	public void tearDown() throws Exception {
		System.arraycopy(previousNpcs, 0, NpcHandler.npcs, 0,
				previousNpcs.length);
		GameEngine.itemHandler.items.clear();
		GameEngine.itemHandler.items.addAll(previousItems);
		ScriptRaidRuntime.getInstance().resetForTesting();
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
		support.close();
	}

	@Test
	public void lobbyRejectsUnauthorizedAndDuplicateOperations() {
		raidCommand(owner, "create");
		assertEquals(1, ScriptRaidRuntime.getInstance().membershipCount());

		// Join without an invitation.
		raidCommand(invitee, "join " + owner.playerName);
		assertEquals(1, ScriptRaidRuntime.getInstance().membershipCount());

		// Invitation by a non-owner.
		raidCommand(invitee, "invite " + owner.playerName);
		assertEquals(1, ScriptRaidRuntime.getInstance().membershipCount());

		// Non-owner start.
		raidCommand(invitee, "start");
		assertEquals(1, ScriptRaidRuntime.getInstance().lobbyCount());

		// A second lobby for the same raid instance is rejected.
		raidCommand(invitee, "create");
		assertEquals(1, ScriptRaidRuntime.getInstance().lobbyCount());

		// Owner invites itself and a missing player.
		raidCommand(owner, "invite " + owner.playerName);
		raidCommand(owner, "invite no-such-player");
		raidCommand(owner, "invite " + invitee.playerName);
		raidCommand(owner, "invite " + invitee.playerName);
		assertEquals(1, ScriptRaidRuntime.getInstance().membershipCount());

		// Joining the wrong owner's lobby is rejected.
		raidCommand(invitee, "join wrong-owner");
		assertEquals(1, ScriptRaidRuntime.getInstance().membershipCount());

		// The invitee opts in explicitly; a duplicate join is a no-op.
		raidCommand(invitee, "join " + owner.playerName);
		raidCommand(invitee, "join " + owner.playerName);
		assertEquals(2, ScriptRaidRuntime.getInstance().membershipCount());
		assertEquals(1, ScriptRaidRuntime.getInstance().lobbyCount());

		// An unregistered raid command is unmatched and unconsumed: the
		// lobby and membership stay exact.
		ByteBuf payload = Unpooled.buffer(64);
		payload.writeBytes("unregistered-raid create"
				.getBytes(StandardCharsets.UTF_8));
		payload.writeByte(10);
		new Commands().processPacket(invitee,
				new Packet(103, Packet.Type.FIXED, payload));
		assertEquals(2, ScriptRaidRuntime.getInstance().membershipCount());
		assertEquals(1, ScriptRaidRuntime.getInstance().lobbyCount());
	}

	@Test
	public void startRejectsBelowMinimumAndWrongMuster() {
		raidCommand(owner, "create");
		raidCommand(owner, "invite " + invitee.playerName);
		raidCommand(invitee, "join " + owner.playerName);

		// The invitee is outside the muster area.
		invitee.absX = MAX_X + 5;
		invitee.absY = MAX_Y + 5;
		raidCommand(owner, "start");
		assertEquals(1, ScriptRaidRuntime.getInstance().lobbyCount());

		// Back inside the muster: below minimum is rejected when only the
		// owner is opted in (the invitee leaves first).
		invitee.absX = MIN_X;
		invitee.absY = MIN_Y;
		raidCommand(invitee, "leave");
		raidCommand(owner, "start");
		assertEquals(1, ScriptRaidRuntime.getInstance().lobbyCount());

		// The invitee re-joins and both muster; start succeeds.
		raidCommand(owner, "invite " + invitee.playerName);
		raidCommand(invitee, "join " + owner.playerName);
		raidCommand(owner, "start");
		assertEquals(0, ScriptRaidRuntime.getInstance().lobbyCount());
		assertEquals(1, ScriptRaidRuntime.getInstance().sessionCount());
		assertEquals(2, ScriptRaidRuntime.getInstance().membershipCount());
	}

	@Test
	public void startBeginsOneEncounterWithFrozenRosterAndTeleports()
			throws Exception {
		raidCommand(owner, "create");
		raidCommand(owner, "invite " + invitee.playerName);
		raidCommand(invitee, "join " + owner.playerName);
		raidCommand(owner, "start");

		// Exactly one encounter exists and both members are participants;
		// the owner cannot begin a second one.
		assertEquals(1, ScriptRaidRuntime.getInstance().sessionCount());
		assertNull("a second overlapping reservation must be rejected",
				support.encounter(owner, "second", MIN_X, MIN_Y, MAX_X,
						MAX_Y, PLANE));
		assertEquals(2, ScriptEncounterService.getInstance()
				.participantCountForTesting());

		// Both members were teleported to the entrance; the pending move
		// lands on the next movement processing.
		owner.getNextPlayerMovement();
		invitee.getNextPlayerMovement();
		assertEquals(3203, owner.absX);
		assertEquals(3203, owner.absY);
		assertEquals(3203, invitee.absX);
		assertEquals(3203, invitee.absY);

		// No late join or second lobby while the session runs.
		raidCommand(owner, "create");
		assertEquals(1, ScriptRaidRuntime.getInstance().sessionCount());
		raidCommand(invitee, "join " + owner.playerName);
	}

	@Test
	public void roomsAdvanceAndTheEmbeddedBossCompletesTheFinalRoom()
			throws Exception {
		context.eval("js", "globalThis.starts=0;globalThis.enters=[];"
				+ "globalThis.completed=0;globalThis.completeRooms=[];");
		raidCommand(owner, "create");
		raidCommand(owner, "invite " + invitee.playerName);
		raidCommand(invitee, "join " + owner.playerName);
		raidCommand(owner, "start");
		assertEquals(1, context.eval("js", "starts").asInt());
		assertEquals(Arrays.asList("room-one"),
				jsStringArray("enters"));

		// Room one completes after two ticks and the boss room enters.
		tick();
		tick();
		assertEquals(Arrays.asList("room-one", "boss-room"),
				jsStringArray("enters"));
		assertNotNull("the embedded boss must spawn",
				ownedNpc(153));

		// The boss dies through the real owned-death seam; the room
		// completes on the next tick, then the reward barrier commits both
		// members, the reward table rolls private ground drops, and
		// onComplete runs once.
		ScriptNpcHandle boss = ownedNpcHandle(153);
		killBoss(boss);
		tick();
		assertEquals(Arrays.asList("room-one", "boss-room"),
				jsStringArray("completeRooms"));
		tick();
		assertEquals(1, context.eval("js", "completed").asInt());

		// Both members received the named reward (coins 5).
		assertEquals(5, countItem(owner, 995));
		assertEquals(5, countItem(invitee, 995));

		// The reward table rolled private detached ground deliveries.
		int privateGround = 0;
		for (GroundItem item : GameEngine.itemHandler.items) {
			if (item.getItemId() == 995 && item.isScriptPrivate()
					&& item.isDetached()) {
				privateGround++;
			}
		}
		assertEquals(2, privateGround);

		// Zero residue: session, memberships, encounter, and owned NPCs.
		assertEquals(0, ScriptRaidRuntime.getInstance().sessionCount());
		assertEquals(0, ScriptRaidRuntime.getInstance().membershipCount());
		assertEquals(0, ScriptEncounterService.getInstance()
				.encounterCountForTesting());
		assertNull(ownedNpc(153));
	}

	@Test
	public void ownerDepartureBeforeStartClosesTheLobby() {
		raidCommand(owner, "create");
		raidCommand(owner, "invite " + invitee.playerName);
		raidCommand(invitee, "join " + owner.playerName);
		assertEquals(2, ScriptRaidRuntime.getInstance().membershipCount());

		ScriptRaidRuntime.getInstance().onPlayerRemoved(owner);
		assertEquals(0, ScriptRaidRuntime.getInstance().lobbyCount());
		assertEquals(0, ScriptRaidRuntime.getInstance().membershipCount());
	}

	@Test
	public void ownerDepartureAfterStartWipesTheRaid() throws Exception {
		startRaid();
		tick();
		assertEquals(1, ScriptRaidRuntime.getInstance().sessionCount());

		ScriptRaidRuntime.getInstance().onPlayerRemoved(owner);
		tick();
		assertEquals(0, ScriptRaidRuntime.getInstance().sessionCount());
		assertEquals(0, ScriptRaidRuntime.getInstance().membershipCount());
		assertEquals(0, ScriptEncounterService.getInstance()
				.encounterCountForTesting());
	}

	@Test
	public void nonOwnerDepartureMarksDepartedAndTheRaidContinues()
			throws Exception {
		context.eval("js", "globalThis.completed=0;globalThis.wiped=0;");
		startRaid();
		tick();
		// Let room one complete and the boss room enter.
		tick();

		ScriptRaidRuntime.getInstance().onPlayerRemoved(invitee);
		tick();

		ScriptNpcHandle boss = ownedNpcHandle(153);
		killBoss(boss);
		tick();
		tick();

		assertEquals(1, context.eval("js", "completed").asInt());
		assertEquals(0, context.eval("js", "wiped").asInt());
		assertEquals(5, countItem(owner, 995));
		assertEquals(0, countItem(invitee, 995));
		assertEquals(0, ScriptRaidRuntime.getInstance().sessionCount());
		assertEquals(0, ScriptRaidRuntime.getInstance().membershipCount());
	}

	@Test
	public void barrierDepartureWipesWithNoAwards() throws Exception {
		context.eval("js", "globalThis.completed=0;globalThis.wiped=0;");
		startRaid();
		// Advance room one, enter the boss room, and kill the boss so the
		// raid enters the reward barrier.
		tick();
		tick();
		killBoss(ownedNpcHandle(153));
		tick();
		assertEquals(0, context.eval("js", "completed").asInt());

		// A frozen member departs during the barrier: wipe, nobody awarded.
		ScriptRaidRuntime.getInstance().onPlayerRemoved(invitee);
		tick();
		assertEquals(1, context.eval("js", "wiped").asInt());
		assertEquals(0, context.eval("js", "completed").asInt());
		assertEquals(0, countItem(owner, 995));
		assertEquals(0, countItem(invitee, 995));
		assertEquals(0, ScriptRaidRuntime.getInstance().sessionCount());
		assertEquals(0, ScriptRaidRuntime.getInstance().membershipCount());
	}

	@Test
	public void fullInventoryRefusesTheBarrierUntilGraceExpiryWipes()
			throws Exception {
		context.eval("js", "globalThis.completed=0;globalThis.wiped=0;");
		// Fill the invitee's inventory with a non-stackable item so the
		// roster preflight refuses (no free slots).
		for (int slot = 0; slot < invitee.playerItems.length; slot++) {
			invitee.playerItems[slot] = 1128;
			invitee.playerItemsN[slot] = 1;
		}
		startRaid();
		tick();
		tick();
		killBoss(ownedNpcHandle(153));
		tick();
		assertEquals(0, context.eval("js", "completed").asInt());

		// Every barrier tick is a bounded retry until the grace expires.
		for (int index = 0; index < 30; index++) {
			tick();
		}
		assertEquals(1, context.eval("js", "wiped").asInt());
		assertEquals(0, context.eval("js", "completed").asInt());
		assertEquals(0, ScriptRaidRuntime.getInstance().sessionCount());
		assertEquals(0, ScriptRaidRuntime.getInstance().membershipCount());
		assertEquals(0, ScriptEncounterService.getInstance()
				.encounterCountForTesting());
	}

	@Test
	public void bossFailureWipesTheRaid() throws Exception {
		// A second raid whose boss phase throws synchronously on the first
		// poll fails the controller and wipes the raid.
		context.eval("js", "globalThis.completed=0;globalThis.wiped=0;");
		registerThrowingBossRaid();
		throwingRaidCommand(owner, "create");
		throwingRaidCommand(owner, "invite " + invitee.playerName);
		throwingRaidCommand(invitee, "join " + owner.playerName);
		throwingRaidCommand(owner, "start");
		tick();
		tick();
		tick();
		assertEquals(1, context.eval("js", "wiped").asInt());
		assertEquals(0, ScriptRaidRuntime.getInstance().sessionCount());
		assertEquals(0, ScriptEncounterService.getInstance()
				.encounterCountForTesting());
	}

	@Test
	public void rejectedReloadKeepsTheLobbyAndSession() throws Exception {
		startRaid();
		tick();
		assertEquals(1, ScriptRaidRuntime.getInstance().sessionCount());

		Path root = Files.createTempDirectory("raid-rejected-reload");
		System.setProperty("singlescape.contentDir",
				root.toAbsolutePath().toString());
		Files.write(root.resolve("loader.js"), "onItem(-1,'first',function(){});"
				.getBytes(StandardCharsets.UTF_8));
		ScriptHost.getInstance().reload();

		assertEquals(1, ScriptRaidRuntime.getInstance().sessionCount());
		assertEquals(2, ScriptRaidRuntime.getInstance().membershipCount());
	}

	@Test
	public void acceptedReloadClosesOldLobbyAndSession() throws Exception {
		startRaid();
		tick();
		assertEquals(1, ScriptRaidRuntime.getInstance().sessionCount());

		context = support.publishEmpty();
		assertEquals(0, ScriptRaidRuntime.getInstance().sessionCount());
		assertEquals(0, ScriptRaidRuntime.getInstance().membershipCount());
		assertEquals(0, ScriptEncounterService.getInstance()
				.encounterCountForTesting());
	}

	@Test
	public void entryLimitsRejectOverflowLateJoinReplacementAndWrongPlane()
			throws Exception {
		ScriptEncounterTestSupport.TestClient third =
				support.player(3, MIN_X, MIN_Y, PLANE);
		ScriptEncounterTestSupport.TestClient fourth =
				support.player(4, MIN_X, MIN_Y, PLANE);
		raidCommand(owner, "create");
		raidCommand(owner, "invite " + invitee.playerName);
		raidCommand(invitee, "join " + owner.playerName);
		raidCommand(owner, "invite " + third.playerName);
		raidCommand(third, "join " + owner.playerName);
		raidCommand(owner, "invite " + fourth.playerName);
		raidCommand(fourth, "join " + owner.playerName);

		// Max capacity (4): a fifth invitation is refused and the fifth
		// player cannot join, so the membership count stays exact.
		ScriptEncounterTestSupport.TestClient fifth =
				support.player(5, MIN_X, MIN_Y, PLANE);
		raidCommand(owner, "invite " + fifth.playerName);
		raidCommand(fifth, "join " + owner.playerName);
		assertEquals(4, ScriptRaidRuntime.getInstance().membershipCount());
		// The lobby is unchanged and still startable.
		assertEquals(1, ScriptRaidRuntime.getInstance().lobbyCount());

		// Wrong plane: one member musters on another plane; start rejects
		// and the lobby stays retryable.
		fourth.heightLevel = 1;
		raidCommand(owner, "start");
		assertEquals(1, ScriptRaidRuntime.getInstance().lobbyCount());
		fourth.heightLevel = PLANE;

		// Start succeeds with the bounded party.
		raidCommand(owner, "start");
		assertEquals(1, ScriptRaidRuntime.getInstance().sessionCount());
		assertEquals(4, ScriptRaidRuntime.getInstance().membershipCount());

		// Late join after start: the new player has no lobby to join.
		raidCommand(fifth, "join " + owner.playerName);
		assertEquals(4, ScriptRaidRuntime.getInstance().membershipCount());

		// Replaced identity: a player object with the invitee's name that
		// is not the exact invited object cannot join.
		PlayerHandler.players[invitee.playerId] = null;
		ScriptEncounterTestSupport.TestClient replacement =
				support.player(2, MIN_X, MIN_Y, PLANE);
		replacement.playerName = invitee.playerName;
		raidCommand(owner, "create");
		raidCommand(owner, "invite " + replacement.playerName);
		raidCommand(replacement, "join " + owner.playerName);
		assertEquals(4, ScriptRaidRuntime.getInstance().membershipCount());
		assertEquals(1, ScriptRaidRuntime.getInstance().sessionCount());
	}

	@Test
	public void atomicParticipantAddFailureLeavesTheLobbyRetryable()
			throws Exception {
		// The invitee already participates in another encounter on another
		// plane, so the raid reservation begins but the atomic participant
		// add must fail and close the partial handle while the lobby stays
		// retryable.
		support.encounter(invitee, "other", MIN_X, MIN_Y, MAX_X, MAX_Y, 1);
		raidCommand(owner, "create");
		raidCommand(owner, "invite " + invitee.playerName);
		raidCommand(invitee, "join " + owner.playerName);
		raidCommand(owner, "start");

		assertEquals(1, ScriptRaidRuntime.getInstance().lobbyCount());
		assertEquals(0, ScriptRaidRuntime.getInstance().sessionCount());
		// The raid's partial encounter was closed; only the invitee's own
		// other-plane encounter remains.
		assertEquals(1, ScriptEncounterService.getInstance()
				.encounterCountForTesting());
		assertEquals(2, ScriptRaidRuntime.getInstance().membershipCount());
	}

	@Test
	public void repeatedDepartureDoesNotWipeWhileTheOwnerRemainsActive()
			throws Exception {
		// A member who dies and then logs out must be recorded departed
		// exactly once; the zero-active check must not falsely trip while
		// the owner is still live.
		context.eval("js", "globalThis.completed=0;globalThis.wiped=0;");
		startRaid();
		tick();
		tick();

		ScriptRaidRuntime.getInstance().onPlayerDeath(invitee);
		ScriptRaidRuntime.getInstance().onPlayerLogout(invitee);
		tick();
		assertEquals(1, ScriptRaidRuntime.getInstance().sessionCount());
		assertEquals(0, context.eval("js", "wiped").asInt());
		assertEquals(1, ScriptRaidRuntime.getInstance().membershipCount());

		ScriptNpcHandle boss = ownedNpcHandle(153);
		killBoss(boss);
		tick();
		tick();
		assertEquals(1, context.eval("js", "completed").asInt());
		assertEquals(0, context.eval("js", "wiped").asInt());
		assertEquals(5, countItem(owner, 995));
		assertEquals(0, ScriptRaidRuntime.getInstance().sessionCount());
		assertEquals(0, ScriptRaidRuntime.getInstance().membershipCount());
	}

	@Test
	public void nonOwnerLobbyDepartureKeepsTheLobby() {
		raidCommand(owner, "create");
		raidCommand(owner, "invite " + invitee.playerName);
		raidCommand(invitee, "join " + owner.playerName);
		assertEquals(1, ScriptRaidRuntime.getInstance().lobbyCount());

		// The invitee dies in the lobby: only its opt-in and membership
		// are removed; the owner's lobby stays open.
		ScriptRaidRuntime.getInstance().onPlayerDeath(invitee);
		assertEquals(1, ScriptRaidRuntime.getInstance().lobbyCount());
		assertEquals(1, ScriptRaidRuntime.getInstance().membershipCount());

		// The owner can invite a replacement.
		ScriptEncounterTestSupport.TestClient third =
				support.player(3, MIN_X, MIN_Y, PLANE);
		raidCommand(owner, "invite " + third.playerName);
		raidCommand(third, "join " + owner.playerName);
		assertEquals(2, ScriptRaidRuntime.getInstance().membershipCount());
	}

	@Test
	public void throwingRoomEnterAndTickCallbacksWipeTheRaid()
			throws Exception {
		context.eval("js", "globalThis.completed=0;globalThis.wiped=0;");
		registerThrowingRoomRaid();
		throwingRoomRaidCommand(owner, "create");
		throwingRoomRaidCommand(owner, "invite " + invitee.playerName);
		throwingRoomRaidCommand(invitee, "join " + owner.playerName);
		throwingRoomRaidCommand(owner, "start");
		// The room-one onEnter throws: the raid wipes immediately.
		assertEquals(1, context.eval("js", "wiped").asInt());
		assertEquals(0, context.eval("js", "completed").asInt());
		assertEquals(0, ScriptRaidRuntime.getInstance().sessionCount());
		assertEquals(0, ScriptEncounterService.getInstance()
				.encounterCountForTesting());

		// A second instance whose room tick throws wipes on the first tick.
		context.eval("js", "globalThis.wiped=0;");
		registerThrowingTickRaid();
		throwingTickRaidCommand(owner, "create");
		throwingTickRaidCommand(owner, "invite " + invitee.playerName);
		throwingTickRaidCommand(invitee, "join " + owner.playerName);
		throwingTickRaidCommand(owner, "start");
		tick();
		assertEquals(1, context.eval("js", "wiped").asInt());
		assertEquals(0, ScriptRaidRuntime.getInstance().sessionCount());
		assertEquals(0, ScriptEncounterService.getInstance()
				.encounterCountForTesting());
	}

	@Test
	public void explicitLeaveClosesTheLobbyOrRemovesTheOptIn() {
		raidCommand(owner, "create");
		raidCommand(owner, "invite " + invitee.playerName);
		raidCommand(invitee, "join " + owner.playerName);

		raidCommand(invitee, "leave");
		assertEquals(1, ScriptRaidRuntime.getInstance().membershipCount());
		assertEquals(1, ScriptRaidRuntime.getInstance().lobbyCount());

		raidCommand(owner, "leave");
		assertEquals(0, ScriptRaidRuntime.getInstance().lobbyCount());
		assertEquals(0, ScriptRaidRuntime.getInstance().membershipCount());
	}

	// ─── Helpers ────────────────────────────────────────────────────────────

	private void startRaid() {
		raidCommand(owner, "create");
		raidCommand(owner, "invite " + invitee.playerName);
		raidCommand(invitee, "join " + owner.playerName);
		raidCommand(owner, "start");
	}

	private void raidCommand(Player player, String subcommand) {
		commandPacket(player, COMMAND, subcommand);
	}

	private void throwingRaidCommand(Player player, String subcommand) {
		commandPacket(player, "throwing-raid", subcommand);
	}

	private void throwingRoomRaidCommand(Player player, String subcommand) {
		commandPacket(player, "throwing-room-raid", subcommand);
	}

	private void throwingTickRaidCommand(Player player, String subcommand) {
		commandPacket(player, "throwing-tick-raid", subcommand);
	}

	private void commandPacket(Player player, String command,
			String subcommand) {
		ByteBuf payload = Unpooled.buffer(
				command.length() + subcommand.length() + 2);
		payload.writeBytes((command + " " + subcommand)
				.getBytes(StandardCharsets.UTF_8));
		payload.writeByte(10);
		new Commands().processPacket(player,
				new Packet(103, Packet.Type.FIXED, payload));
	}

	private static void tick() {
		ScriptLifecycleService.getInstance().processGameTick();
	}

	/** Kills an owned NPC through the real death critical section. */
	private void killBoss(ScriptNpcHandle boss) {
		Npc npc = npcOf(boss);
		assertNotNull(npc);
		npc.HP = 0;
		npc.isDead = true;
		ScriptNpcService service = ScriptNpcService.getInstance();
		assertTrue(service.beginDeath(npc));
		service.dispatchDeath(npc, owner,
				new ScriptedPosition(npc.absX, npc.absY, npc.heightLevel));
		service.finishDeath(npc);
	}

	private static Npc npcOf(ScriptNpcHandle handle) {
		for (int index = 1; index < NpcHandler.MAX_NPCS; index++) {
			Npc npc = NpcHandler.npcs[index];
			if (npc != null && Long.toString(npc.allocationToken())
					.equals(handle.token())) {
				return npc;
			}
		}
		return null;
	}

	private static ScriptNpcHandle ownedNpcHandle(int npcType) {
		for (int index = 1; index < NpcHandler.MAX_NPCS; index++) {
			Npc npc = NpcHandler.npcs[index];
			if (npc != null && npc.npcType == npcType
					&& ScriptNpcService.getInstance().isOwned(npc)) {
				return ScriptNpcService.getInstance().handleForTesting(npc);
			}
		}
		return null;
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

	private List<String> jsStringArray(String expression) {
		org.graalvm.polyglot.Value value = context.eval("js", expression);
		List<String> result = new ArrayList<String>();
		for (int index = 0; index < value.getArraySize(); index++) {
			result.add(value.getArrayElement(index).asString());
		}
		return result;
	}

	/** Registers the canonical test raid through the production path. */
	private void registerRaid() throws Exception {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineBoss().accept(context
					.eval("js", "({id:'test-boss',npcId:153,name:'Test "
							+ "Boss',combatLevel:100,maxHitpoints:100,"
							+ "maxHit:10,attack:50,defence:50,"
							+ "arena:{minX:" + MIN_X + ",minY:" + MIN_Y
							+ ",maxX:" + MAX_X + ",maxY:" + MAX_Y
							+ ",plane:" + PLANE + "},"
							+ "spawn:{x:3205,y:3205},command:'test-boss',"
							+ "onSpawn:function(){}})"));
			ScriptFunctions.getInstance().getDefineReward().accept(context
					.eval("js", "({id:'test-reward',items:[{id:995,"
							+ "amount:5}],experience:[],questPoints:0,"
							+ "state:[]})"));
			ScriptFunctions.getInstance().getDefineDropTable().accept(context
					.eval("js", "({id:'test-table',entries:[{itemId:995,"
							+ "minAmount:1,maxAmount:1,weight:0,always:true}]})"));
			ScriptFunctions.getInstance().getDefineRaid().accept(context
					.eval("js", "({id:'" + RAID_ID + "',command:'" + COMMAND
							+ "',bounds:{minX:" + MIN_X + ",minY:" + MIN_Y
							+ ",maxX:" + MAX_X + ",maxY:" + MAX_Y
							+ ",plane:" + PLANE + "},"
							+ "muster:{minX:" + MIN_X + ",minY:" + MIN_Y
							+ ",maxX:3204,maxY:3204},"
							+ "entrance:{x:3203,y:3203,plane:" + PLANE + "},"
							+ "minPlayers:2,maxPlayers:4,timeLimitTicks:100,"
							+ "rewards:['test-reward'],"
							+ "rewardTable:'test-table',privateTicks:200,"
							+ "onStart:function(){globalThis.starts++;},"
							+ "onComplete:function(){globalThis.completed++;},"
							+ "onWipe:function(){globalThis.wiped++;},"
							+ "rooms:["
							+ "{id:'room-one',name:'Room One',"
							+ "bounds:{minX:" + MIN_X + ",minY:" + MIN_Y
							+ ",maxX:3204,maxY:" + MAX_Y + ",plane:" + PLANE
							+ "},"
							+ "onEnter:function(){globalThis.enters=(globalThis.enters"
							+ "||[]);globalThis.enters.push('room-one');},"
							+ "onTick:function(ctx){return ctx.elapsedTicks()"
							+ " >= 2 ? {status:'completed'} : {status:'in_progress'};},"
							+ "onComplete:function(){globalThis.completeRooms="
							+ "(globalThis.completeRooms||[]);globalThis."
							+ "completeRooms.push('room-one');}},"
							+ "{id:'boss-room',name:'Boss Room',"
							+ "bounds:{minX:3205,minY:" + MIN_Y + ",maxX:"
							+ MAX_X + ",maxY:" + MAX_Y + ",plane:" + PLANE
							+ "},"
							+ "onEnter:function(){globalThis.enters=(globalThis.enters"
							+ "||[]);globalThis.enters.push('boss-room');},"
							+ "onTick:function(){return {status:'in_progress'};},"
							+ "onComplete:function(){globalThis.completeRooms="
							+ "(globalThis.completeRooms||[]);globalThis."
							+ "completeRooms.push('boss-room');},"
							+ "boss:{bossId:'test-boss'}}]})"));
			ScriptHost.getInstance().publishForTesting(context, candidate);
		} catch (RuntimeException error) {
			RegistryStore.rollback(candidate);
			throw error;
		}
	}

	/** Registers a second raid whose boss phase throws on the first poll. */
	private void registerThrowingBossRaid() throws Exception {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineBoss().accept(context
					.eval("js", "({id:'throwing-boss',npcId:153,name:'Test "
							+ "Boss',combatLevel:100,maxHitpoints:100,"
							+ "maxHit:10,attack:50,defence:50,"
							+ "arena:{minX:" + MIN_X + ",minY:" + MIN_Y
							+ ",maxX:" + MAX_X + ",maxY:" + MAX_Y
							+ ",plane:" + PLANE + "},"
							+ "spawn:{x:3205,y:3205},command:'throwing-boss',"
							+ "onSpawn:function(){},"
							+ "phases:[{name:'Boom',hpPercentThreshold:100,"
							+ "onEnter:function(){throw new Error('boom');}}]})"));
			ScriptFunctions.getInstance().getDefineReward().accept(context
					.eval("js", "({id:'test-reward',items:[{id:995,"
							+ "amount:5}],experience:[],questPoints:0,"
							+ "state:[]})"));
			ScriptFunctions.getInstance().getDefineRaid().accept(context
					.eval("js", "({id:'throwing-raid',command:'throwing-raid',"
							+ "bounds:{minX:" + MIN_X + ",minY:" + MIN_Y
							+ ",maxX:" + MAX_X + ",maxY:" + MAX_Y
							+ ",plane:" + PLANE + "},"
							+ "muster:{minX:" + MIN_X + ",minY:" + MIN_Y
							+ ",maxX:3204,maxY:3204},"
							+ "entrance:{x:3203,y:3203,plane:" + PLANE + "},"
							+ "minPlayers:2,maxPlayers:4,timeLimitTicks:100,"
							+ "rewards:['test-reward'],"
							+ "onStart:function(){},"
							+ "onComplete:function(){globalThis.completed++;},"
							+ "onWipe:function(){globalThis.wiped++;},"
							+ "rooms:["
							+ "{id:'boss-room',name:'Boss Room',"
							+ "bounds:{minX:" + MIN_X + ",minY:" + MIN_Y
							+ ",maxX:" + MAX_X + ",maxY:" + MAX_Y
							+ ",plane:" + PLANE + "},"
							+ "onEnter:function(){globalThis.enters.push('boss-room');},"
							+ "onTick:function(){return {status:'in_progress'};},"
							+ "onComplete:function(){globalThis.completeRooms.push('boss-room');},"
							+ "boss:{bossId:'throwing-boss'}}]})"));
			ScriptHost.getInstance().publishForTesting(context, candidate);
		} catch (RuntimeException error) {
			RegistryStore.rollback(candidate);
			throw error;
		}
	}

	/** Registers a raid whose room-one onEnter throws on entry. */
	private void registerThrowingRoomRaid() throws Exception {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineReward().accept(context
					.eval("js", "({id:'test-reward',items:[{id:995,"
							+ "amount:5}],experience:[],questPoints:0,"
							+ "state:[]})"));
			ScriptFunctions.getInstance().getDefineRaid().accept(context
					.eval("js", "({id:'throwing-room-raid',"
							+ "command:'throwing-room-raid',"
							+ "bounds:{minX:" + MIN_X + ",minY:" + MIN_Y
							+ ",maxX:" + MAX_X + ",maxY:" + MAX_Y
							+ ",plane:" + PLANE + "},"
							+ "muster:{minX:" + MIN_X + ",minY:" + MIN_Y
							+ ",maxX:3204,maxY:3204},"
							+ "entrance:{x:3203,y:3203,plane:" + PLANE + "},"
							+ "minPlayers:2,maxPlayers:4,timeLimitTicks:100,"
							+ "rewards:['test-reward'],"
							+ "onStart:function(){},"
							+ "onComplete:function(){globalThis.completed++;},"
							+ "onWipe:function(){globalThis.wiped++;},"
							+ "rooms:["
							+ "{id:'room-one',name:'Room One',"
							+ "bounds:{minX:" + MIN_X + ",minY:" + MIN_Y
							+ ",maxX:" + MAX_X + ",maxY:" + MAX_Y
							+ ",plane:" + PLANE + "},"
							+ "onEnter:function(){throw new Error('enter-boom');},"
							+ "onTick:function(){return {status:'in_progress'};},"
							+ "onComplete:function(){}}]})"));
			ScriptHost.getInstance().publishForTesting(context, candidate);
		} catch (RuntimeException error) {
			RegistryStore.rollback(candidate);
			throw error;
		}
	}

	/** Registers a raid whose room-one onTick throws on the first poll. */
	private void registerThrowingTickRaid() throws Exception {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineReward().accept(context
					.eval("js", "({id:'test-reward',items:[{id:995,"
							+ "amount:5}],experience:[],questPoints:0,"
							+ "state:[]})"));
			ScriptFunctions.getInstance().getDefineRaid().accept(context
					.eval("js", "({id:'throwing-tick-raid',"
							+ "command:'throwing-tick-raid',"
							+ "bounds:{minX:" + MIN_X + ",minY:" + MIN_Y
							+ ",maxX:" + MAX_X + ",maxY:" + MAX_Y
							+ ",plane:" + PLANE + "},"
							+ "muster:{minX:" + MIN_X + ",minY:" + MIN_Y
							+ ",maxX:3204,maxY:3204},"
							+ "entrance:{x:3203,y:3203,plane:" + PLANE + "},"
							+ "minPlayers:2,maxPlayers:4,timeLimitTicks:100,"
							+ "rewards:['test-reward'],"
							+ "onStart:function(){},"
							+ "onComplete:function(){globalThis.completed++;},"
							+ "onWipe:function(){globalThis.wiped++;},"
							+ "rooms:["
							+ "{id:'room-one',name:'Room One',"
							+ "bounds:{minX:" + MIN_X + ",minY:" + MIN_Y
							+ ",maxX:" + MAX_X + ",maxY:" + MAX_Y
							+ ",plane:" + PLANE + "},"
							+ "onEnter:function(){},"
							+ "onTick:function(){throw new Error('tick-boom');},"
							+ "onComplete:function(){}}]})"));
			ScriptHost.getInstance().publishForTesting(context, candidate);
		} catch (RuntimeException error) {
			RegistryStore.rollback(candidate);
			throw error;
		}
	}

}
