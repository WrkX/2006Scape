package com.rs2.script.raid;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.graalvm.polyglot.Value;

import com.rs2.game.items.GroundItem;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.CommandScriptContext;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.ScriptedPosition;
import com.rs2.script.ScriptExecutor;
import com.rs2.script.boss.BossArena;
import com.rs2.script.boss.BossController;
import com.rs2.script.drop.DropRngTransactionOwner;
import com.rs2.script.drop.DropTableDefinition;
import com.rs2.script.drop.DropTableRegistry;
import com.rs2.script.drop.DropTransaction;
import com.rs2.script.drop.GroundDeliveryPolicy;
import com.rs2.script.reward.RosterRewardTransaction;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.HostRoute;
import com.rs2.script.route.RouteRegistry;
import com.rs2.script.world.ScriptDropResult;
import com.rs2.script.world.ScriptEncounterHandle;
import com.rs2.script.world.ScriptEncounterRng;
import com.rs2.script.world.ScriptGroundItemHandle;
import com.rs2.util.LoggerUtils;
import com.rs2.GameEngine;

/**
 * Declarative raid runtime.
 *
 * <p>For every registered {@code defineRaid} the service registers the
 * definition's exact WP1 host command route. The bounded subcommands are
 * {@code create}, {@code invite <player>}, {@code join <owner>},
 * {@code leave}, and {@code start}. A created lobby is owned by the exact
 * live inviter identity; the invitee explicitly opts in with {@code join};
 * only the owner may start with every opted-in identity live on the
 * entrance plane inside the bounded muster area. Start freezes an immutable
 * roster (owner first, then join FIFO), begins exactly one encounter with
 * that reservation, atomically adds every roster identity, and teleports
 * the members to the entrance before any room callback runs.
 *
 * <p>Rooms advance in declared order. A room with a boss reference embeds a
 * WP3 {@link BossController} that borrows the raid's sole encounter handle
 * (it never begins or closes a second encounter); controller
 * {@code DEFEATED} completes the room and {@code FAILED} wipes the raid.
 * The final room's completion enters the reward barrier, freezes the
 * surviving active roster, and commits the named rewards roster-wide and
 * atomically through {@link RosterRewardTransaction} with the raid-session
 * RNG owner. The optional reward table is rolled once after the roster
 * commit as private ground deliveries. {@code onComplete} runs once after
 * the joint commit; owner departure, zero active members, timeout, boss or
 * callback failure, barrier departure, or grace expiry invoke
 * {@code onWipe} once and award nobody.
 *
 * <p>Every guest callback is generation-owned and exception-contained.
 * Guest callbacks and the roster transaction never run while this service's
 * monitor is held; the monitor guards only the lobby/session state machines
 * and membership indexes.
 */
public final class ScriptRaidRuntime {

	private static final int MAX_GROUND_IDENTITIES_PER_RAID = 128;
	private static final int BARRIER_GRACE_TICKS = 25;
	private static final int PHASE_RUNNING = 0;
	private static final int PHASE_BARRIER = 1;
	private static final int PHASE_COMPLETED = 2;
	private static final int PHASE_WIPED = 3;
	private static final int RESULT_NONE = 0;
	private static final int RESULT_DEFEATED = 1;
	private static final int RESULT_FAILED = 2;

	/** Sets the session to wipe with a bounded reason. Caller holds the monitor. */
	private static void wipe(Session session, String reason) {
		session.phase = PHASE_WIPED;
		session.wipeReason = reason;
	}

	private static volatile ScriptRaidRuntime INSTANCE = new ScriptRaidRuntime();

	private static final Logger logger = LoggerUtils
			.getLogger(ScriptRaidRuntime.class);

	private final long processSeed;
	private long nextOwnerToken = 1L;
	private long nextSessionOrdinal = 1L;
	private final Map<String, Lobby> lobbies = new LinkedHashMap<String, Lobby>();
	private final Map<String, Session> sessions = new LinkedHashMap<String, Session>();
	private final Map<Player, String> memberships =
			new IdentityHashMap<Player, String>();

	private ScriptRaidRuntime() {
		this(new SecureRandom().nextLong());
	}

	ScriptRaidRuntime(long processSeed) {
		this.processSeed = processSeed;
	}

	public static ScriptRaidRuntime getInstance() {
		return INSTANCE;
	}

	/** Test-only installation with a deterministic process seed. */
	public static ScriptRaidRuntime installForTesting(long processSeed) {
		ScriptRaidRuntime runtime = new ScriptRaidRuntime(processSeed);
		INSTANCE = runtime;
		return runtime;
	}

	/**
	 * Registers the definition's exact WP1 host command route in the loading
	 * candidate. Duplicate keys and reserved admin aliases reject the whole
	 * candidate through the unified route registry.
	 */
	public void registerRoutes(RaidDefinition definition) {
		RouteRegistry.putHost(ExecutableRouteKey.command(
				definition.command()), commandRoute(definition));
	}

	// ─── Lifecycle hooks (game-cycle owner) ────────────────────────────────

	/** Player removal, logout, or death: lobby/session membership cleanup. */
	public void onPlayerRemoved(Player player) {
		handleDeparture(player);
	}

	public void onPlayerLogout(Player player) {
		handleDeparture(player);
	}

	public void onPlayerDeath(Player player) {
		handleDeparture(player);
	}

	/**
	 * Ticks every open lobby-free session of the active generation. Called
	 * from the game cycle under the active-generation lease, so guest room
	 * callbacks run generation-owned; callbacks and the roster transaction
	 * never run while this service's monitor is held.
	 */
	public void processGameTick(long generation) {
		List<Session> current;
		synchronized (this) {
			current = new ArrayList<Session>(sessions.values());
		}
		for (Session session : current) {
			if (session.generation != generation) {
				continue;
			}
			tickSession(session, generation);
		}
	}

	/**
	 * Closes every lobby and session of one generation. Called by the script
	 * host after a successful reload before the old context closes.
	 */
	public void closeGeneration(long generation) {
		List<Lobby> closingLobbies;
		List<Session> closingSessions;
		synchronized (this) {
			closingLobbies = new ArrayList<Lobby>();
			for (Lobby lobby : lobbies.values()) {
				if (lobby.generation == generation) {
					closingLobbies.add(lobby);
				}
			}
			closingSessions = new ArrayList<Session>();
			for (Session session : sessions.values()) {
				if (session.generation == generation) {
					closingSessions.add(session);
				}
			}
		}
		for (Lobby lobby : closingLobbies) {
			synchronized (this) {
				if (lobbies.get(lobby.definition.id()) == lobby) {
					closeLobbyLocked(lobby);
				}
			}
		}
		for (Session session : closingSessions) {
			closeSession(session);
		}
	}

	/** Closes every lobby and session. Test-only lifecycle reset. */
	public synchronized void resetForTesting() {
		for (Lobby lobby : new ArrayList<Lobby>(lobbies.values())) {
			closeLobbyLocked(lobby);
		}
		for (Session session : new ArrayList<Session>(sessions.values())) {
			closeSession(session);
		}
		lobbies.clear();
		sessions.clear();
		memberships.clear();
	}

	/** Engine-visible counts for tests; never exported to guest code. */
	public synchronized int lobbyCount() {
		return lobbies.size();
	}

	public synchronized int sessionCount() {
		return sessions.size();
	}

	/** Engine-visible membership count for tests. */
	public synchronized int membershipCount() {
		return memberships.size();
	}

	// ─── Command route ─────────────────────────────────────────────────────

	private HostRoute commandRoute(final RaidDefinition definition) {
		return new HostRoute() {
			@Override
			public void invoke(Object... arguments) {
				if (arguments == null || arguments.length < 1
						|| !(arguments[0] instanceof CommandScriptContext)) {
					return;
				}
				CommandScriptContext context =
						(CommandScriptContext) arguments[0];
				Player player = context.player.backingPlayer();
				if (player == null) {
					return;
				}
				String[] args = context.getArguments();
				String subcommand = args.length == 0 ? "" : args[0];
				if ("create".equals(subcommand)) {
					create(definition, player, context.player.generation());
				} else if ("invite".equals(subcommand) && args.length >= 2) {
					invite(definition, player, args[1]);
				} else if ("join".equals(subcommand) && args.length >= 2) {
					join(definition, player, args[1]);
				} else if ("leave".equals(subcommand)) {
					leave(definition, player);
				} else if ("start".equals(subcommand)) {
					start(definition, player);
				} else {
					player.getPacketSender().sendMessage(
							"Raid usage: ::" + definition.command()
									+ " create|invite <player>|join <owner>"
									+ "|leave|start");
				}
			}
		};
	}

	// ─── Lobby operations ──────────────────────────────────────────────────

	private void create(RaidDefinition definition, Player player,
			long generation) {
		synchronized (this) {
			if (sessions.containsKey(definition.id())) {
				message(player, "A raid of this instance is already active.");
				return;
			}
			if (memberships.containsKey(player)) {
				message(player,
						"You are already in another raid lobby or session.");
				return;
			}
			if (lobbies.containsKey(definition.id())) {
				message(player, "A raid lobby already exists.");
				return;
			}
			Lobby lobby = new Lobby(definition, generation, player);
			lobbies.put(definition.id(), lobby);
			memberships.put(player, definition.id());
		}
		message(player, "Raid lobby created. Invite players with ::" 
				+ definition.command() + " invite <player>.");
	}

	private void invite(RaidDefinition definition, Player player,
			String inviteeName) {
		synchronized (this) {
			Lobby lobby = lobbies.get(definition.id());
			if (lobby == null || lobby.owner != player) {
				message(player, "Only the lobby owner may invite players.");
				return;
			}
			if (lobby.optIns.size() >= definition.maxPlayers() - 1) {
				message(player, "The raid lobby is full.");
				return;
			}
			Player invitee = findLivePlayer(inviteeName);
			if (invitee == null) {
				message(player, "No such player is online.");
				return;
			}
			if (invitee == player) {
				message(player, "You cannot invite yourself.");
				return;
			}
			if (memberships.containsKey(invitee)) {
				message(player,
						"That player is already in another raid lobby or session.");
				return;
			}
			if (lobby.invites.containsKey(
					invitee.playerName.toLowerCase(Locale.ROOT))) {
				message(player, "That player is already invited.");
				return;
			}
			lobby.invites.put(invitee.playerName.toLowerCase(Locale.ROOT),
					invitee);
		}
		message(player, "Invitation sent to " + inviteeName + ".");
	}

	private void join(RaidDefinition definition, Player player,
			String ownerName) {
		synchronized (this) {
			Lobby lobby = lobbies.get(definition.id());
			if (lobby == null) {
				message(player, "No raid lobby is open for this instance.");
				return;
			}
			if (lobby.owner == player) {
				message(player, "You already own this raid lobby.");
				return;
			}
			if (!lobby.owner.playerName.equalsIgnoreCase(ownerName)) {
				message(player,
						"The lobby is not owned by " + ownerName + ".");
				return;
			}
			if (memberships.containsKey(player)) {
				message(player,
						"You are already in another raid lobby or session.");
				return;
			}
			Player invited = lobby.invites.get(
					player.playerName.toLowerCase(Locale.ROOT));
			if (invited != player) {
				message(player, "You have no invitation to this raid lobby.");
				return;
			}
			if (lobby.optIns.size() >= definition.maxPlayers() - 1) {
				message(player, "The raid lobby is full.");
				return;
			}
			lobby.invites.remove(player.playerName.toLowerCase(Locale.ROOT));
			lobby.optIns.put(player, player.playerName);
			memberships.put(player, definition.id());
		}
		message(player, "You joined the raid lobby. Wait for the owner to start.");
	}

	private void leave(RaidDefinition definition, Player player) {
		Session session;
		boolean removeParticipation = false;
		synchronized (this) {
			Lobby lobby = lobbies.get(definition.id());
			if (lobby != null) {
				if (lobby.owner == player) {
					closeLobbyLocked(lobby);
					message(player, "The raid lobby was closed.");
					return;
				}
				lobby.invites.remove(player.playerName.toLowerCase(Locale.ROOT));
				if (lobby.optIns.remove(player) != null) {
					memberships.remove(player);
					message(player, "You left the raid lobby.");
					return;
				}
				message(player, "You are not part of this raid lobby.");
				return;
			}
			session = sessions.get(definition.id());
			if (session == null) {
				message(player, "No raid lobby or session is active.");
				return;
			}
			if (player == session.owner) {
				wipe(session, "the raid owner abandoned the raid");
				message(player, "You abandoned the raid; it will wipe.");
			} else if (session.phase == PHASE_RUNNING) {
				session.departed.add(player);
				memberships.remove(player);
				removeParticipation = true;
				message(player, "You left the raid.");
			} else if (session.phase == PHASE_BARRIER) {
				wipe(session,
						"a member left during the reward barrier");
				message(player, "You left the raid during its reward barrier.");
			} else {
				message(player, "The raid is no longer active.");
			}
		}
		if (removeParticipation && session != null
				&& session.handle.isOpen()) {
			session.handle.removeParticipant(
					new ScriptedPlayer(player, session.generation));
		}
	}

	private void start(RaidDefinition definition, Player player) {
		Lobby lobby;
		synchronized (this) {
			lobby = lobbies.get(definition.id());
			if (lobby == null) {
				message(player, "No raid lobby is open for this instance.");
				return;
			}
			if (lobby.owner != player) {
				message(player, "Only the raid lobby owner may start.");
				return;
			}
		}
		List<Player> roster = new ArrayList<Player>();
		roster.add(lobby.owner);
		roster.addAll(lobby.optIns.keySet());
		if (roster.size() < definition.minPlayers()
				|| roster.size() > definition.maxPlayers()) {
			message(player, "The raid requires " + definition.minPlayers()
					+ ".." + definition.maxPlayers()
					+ " opted-in players to start.");
			return;
		}
		for (Player member : roster) {
			if (!isLive(member)) {
				message(player, "A raid member is no longer available.");
				return;
			}
			if (!definition.muster().contains(member.absX, member.absY,
					member.heightLevel)) {
				message(player, member.playerName
						+ " is outside the raid muster area.");
				return;
			}
		}
		long generation = lobby.generation;
		ScriptedPlayer scriptedOwner = new ScriptedPlayer(player, generation);
		ScriptEncounterHandle handle = scriptedOwner.beginEncounter(
				definition.id(), definition.bounds().minX(),
				definition.bounds().minY(), definition.bounds().maxX(),
				definition.bounds().maxY(), definition.bounds().plane());
		if (handle == null) {
			message(player, "The raid area is busy; the lobby is still open.");
			return;
		}
		for (int index = 1; index < roster.size(); index++) {
			Player member = roster.get(index);
			if (!handle.addParticipant(
					new ScriptedPlayer(member, generation))) {
				handle.close();
				message(player,
						"The raid could not admit every member; the lobby is still open.");
				return;
			}
		}
		for (Player member : roster) {
			new ScriptedPlayer(member, generation).teleport(
					definition.entranceX(), definition.entranceY(),
					definition.entrancePlane());
		}
		Session session;
		synchronized (this) {
			if (lobbies.get(definition.id()) != lobby) {
				handle.close();
				return;
			}
			session = new Session(definition, generation, player,
					nextOwnerToken(), nextSessionOrdinal(), handle, roster);
			sessions.put(definition.id(), session);
			lobbies.remove(definition.id());
		}
		runRaidCallback(definition.onStart(),
				newRaidContext(session, -1, 0), "onStart");
		enterRoom(session, 0);
	}

	// ─── Session ticking ───────────────────────────────────────────────────

	private void tickSession(final Session session, long generation) {
		RoomAction action = null;
		RaidRoomDefinition roomToTick = null;
		boolean barrierPhase = false;
		int roomIndex = -1;
		synchronized (this) {
			if (sessions.get(session.definition.id()) != session
					|| session.generation != generation) {
				return;
			}
			switch (session.phase) {
			case PHASE_RUNNING:
				session.sessionTicks++;
				session.roomTicks++;
				if (session.sessionTicks >= session.definition
						.timeLimitTicks()) {
					wipe(session, "the time limit expired");
					action = wipeAction(session);
					break;
				}
				if (!isLive(session.owner) || !session.handle.isOpen()) {
					wipe(session, "the raid owner departed");
					action = wipeAction(session);
					break;
				}
				for (Player member : session.roster) {
					if (member == session.owner
							|| session.departed.contains(member)) {
						continue;
					}
					if (!isLive(member)) {
						session.departed.add(member);
					}
				}
				if (session.roster.size() - session.departed.size() == 0) {
					wipe(session, "no active members remain");
					action = wipeAction(session);
					break;
				}
				if (session.bossResult == RESULT_FAILED) {
					wipe(session, "the boss encounter failed");
					action = wipeAction(session);
					break;
				}
				if (session.bossResult == RESULT_DEFEATED) {
					session.bossResult = RESULT_NONE;
					final int completed = session.roomIndex;
					action = new RoomAction() {
						@Override
						public void run() {
							completeRoomOutside(session, completed);
						}
					};
					break;
				}
				RaidRoomDefinition room = session.definition.rooms()
						.get(session.roomIndex);
				if (room.boss() != null) {
					// Boss rooms complete only through the controller.
					return;
				}
				roomToTick = room;
				roomIndex = session.roomIndex;
				break;
			case PHASE_BARRIER:
				if (session.awarded) {
					return;
				}
				for (Player member : session.eligibleRoster) {
					if (!isLive(member)) {
						wipe(session,
								"a member departed before the rewards were committed");
						action = wipeAction(session);
						break;
					}
				}
				barrierPhase = action == null;
				break;
			case PHASE_WIPED:
				if (!session.wipeInvoked) {
					session.wipeInvoked = true;
					action = wipeAction(session);
				}
				break;
			default:
				return;
			}
		}
		if (action != null) {
			action.run();
			return;
		}
		if (roomToTick != null) {
			RoomStatus status = runRoomTick(roomToTick.onTick(), session,
					roomIndex);
			boolean completed = false;
			boolean wiped = false;
			String wipeReason = null;
			int completedIndex = -1;
			synchronized (this) {
				if (sessions.get(session.definition.id()) != session
						|| session.phase != PHASE_RUNNING) {
					return;
				}
				if (status == RoomStatus.COMPLETED) {
					completed = true;
					completedIndex = session.roomIndex;
				} else if (status.wiped) {
					wipe(session, status.reason);
					wiped = true;
					wipeReason = session.wipeReason;
				}
			}
			if (completed) {
				completeRoomOutside(session, completedIndex);
			} else if (wiped) {
				invokeWipe(session, wipeReason);
			}
			return;
		}
		if (!barrierPhase) {
			return;
		}
		// PHASE_BARRIER: one bounded roster-wide attempt.
		RosterRewardTransaction.Result result = RosterRewardTransaction
				.attempt(session.eligibleRoster, session.definition.rewards(),
						session.rng, session.awardId(), session);
		synchronized (this) {
			if (sessions.get(session.definition.id()) != session) {
				return;
			}
			switch (result) {
			case COMMITTED:
				session.phase = PHASE_COMPLETED;
				break;
			case WIPED:
				wipe(session,
						"a member departed before the rewards were committed");
				break;
			case FATAL:
				wipe(session, "the reward transaction failed");
				break;
			case RETRYABLE:
				session.barrierTicksLeft--;
				if (session.barrierTicksLeft <= 0) {
					wipe(session, "the reward grace period expired");
				}
				break;
			default:
				break;
			}
		}
		if (result == RosterRewardTransaction.Result.RETRYABLE) {
			if (session.phase == PHASE_WIPED) {
				invokeWipe(session, session.wipeReason);
			}
			return;
		}
		if (result == RosterRewardTransaction.Result.COMMITTED) {
			rollRewardTable(session);
			runRaidCallback(session.definition.onComplete(),
					newRaidContext(session, -1, 0), "onComplete");
			closeSession(session);
			return;
		}
		invokeWipe(session, session.wipeReason);
	}

	private void completeRoomTransition(Session session) {
		// Called with the monitor held; transitions only, no guest code.
		session.roomIndex++;
		if (session.roomIndex >= session.definition.rooms().size()) {
			session.phase = PHASE_BARRIER;
			session.barrierTicksLeft = BARRIER_GRACE_TICKS;
			session.eligibleRoster = new ArrayList<Player>(session.roster);
			session.eligibleRoster.removeAll(session.departed);
			if (session.eligibleRoster.isEmpty()) {
				wipe(session, "no surviving members remain");
			}
		}
	}

	private void completeRoomOutside(Session session, int completedIndex) {
		RaidRoomDefinition completed = session.definition.rooms()
				.get(completedIndex);
		if (!runRoomCallback(completed.onComplete(), session, completedIndex,
				"onComplete")) {
			failSession(session, "the room complete callback threw");
			return;
		}
		boolean noSurvivors = false;
		synchronized (this) {
			if (sessions.get(session.definition.id()) != session
					|| session.phase != PHASE_RUNNING
					|| session.roomIndex != completedIndex) {
				return;
			}
			completeRoomTransition(session);
			noSurvivors = session.phase == PHASE_WIPED;
		}
		if (noSurvivors) {
			invokeWipe(session, "no surviving members remain");
			return;
		}
		int roomIndex;
		synchronized (this) {
			roomIndex = session.roomIndex;
			if (session.phase != PHASE_RUNNING) {
				return;
			}
		}
		enterRoom(session, roomIndex);
	}

	private void enterRoom(Session session, int index) {
		RaidRoomDefinition room = session.definition.rooms().get(index);
		synchronized (this) {
			session.roomTicks = 0;
			session.bossResult = RESULT_NONE;
		}
		boolean entered = runRoomCallback(room.onEnter(), session, index, "onEnter");
		if (!entered) {
			failSession(session, "the room enter callback threw");
			return;
		}
		if (room.boss() == null) {
			return;
		}
		BossArena slice = new BossArena(room.bounds().minX(),
				room.bounds().minY(), room.bounds().maxX(),
				room.bounds().maxY(), room.bounds().plane());
		List<ScriptedPlayer> participants = activeParticipants(session);
		BossController.start(room.boss(), session.handle, slice,
				new ScriptedPlayer(session.owner, session.generation),
				participants, terminalListener(session));
	}

	private BossController.TerminalListener terminalListener(
			final Session session) {
		return new BossController.TerminalListener() {
			@Override
			public void onTerminal(BossController controller,
					BossController.Status status,
					ScriptedPosition deathPosition, ScriptedPlayer killer) {
				synchronized (ScriptRaidRuntime.this) {
					if (sessions.get(session.definition.id()) != session) {
						return;
					}
					if (session.phase != PHASE_RUNNING) {
						return;
					}
					session.bossResult = status == BossController.Status.DEFEATED
							? RESULT_DEFEATED : RESULT_FAILED;
				}
			}
		};
	}

	private void rollRewardTable(Session session) {
		RaidDefinition definition = session.definition;
		if (!definition.hasRewardTable()) {
			return;
		}
		DropTableDefinition table = DropTableRegistry.get(
				definition.rewardTable());
		if (table == null) {
			logger.log(Level.WARNING, "Raid '" + definition.id()
					+ "' reward table '" + definition.rewardTable()
					+ "' is not active; completion drops were skipped");
			return;
		}
		for (Player member : session.eligibleRoster) {
			ScriptedPlayer recipient = new ScriptedPlayer(member,
					session.generation);
			List<ScriptDropResult> results = DropTransaction.execute(
					session.rng,
					new RaidGroundDelivery(session, member,
							session.handle.token(), definition.privateTicks()),
					table.entries(), recipient);
			if (results == null || results.isEmpty()) {
				logger.log(Level.WARNING, "Raid '" + definition.id()
						+ "' reward-table roll failed for "
						+ member.playerName + "; the committed rewards are "
						+ "forward-only and are not rolled back");
			}
		}
	}

	private void invokeWipe(Session session, String reason) {
		runRaidCallback(session.definition.onWipe(),
				newRaidContext(session, -1, 0), "onWipe", reason);
		closeSession(session);
	}

	private void closeSession(Session session) {
		ScriptEncounterHandle handle;
		synchronized (this) {
			if (sessions.get(session.definition.id()) != session) {
				return;
			}
			sessions.remove(session.definition.id());
			for (Player member : session.roster) {
				memberships.remove(member);
			}
			handle = session.handle;
		}
		if (handle != null && handle.isOpen()) {
			handle.close();
		}
	}

	private void closeLobbyLocked(Lobby lobby) {
		lobbies.remove(lobby.definition.id());
		for (Player member : lobby.optIns.keySet()) {
			memberships.remove(member);
		}
		memberships.remove(lobby.owner);
	}

	private void handleDeparture(Player player) {
		synchronized (this) {
			String raidId = memberships.get(player);
			if (raidId == null) {
				return;
			}
			Lobby lobby = lobbies.get(raidId);
			if (lobby != null) {
				if (lobby.owner == player) {
					// The lobby is pinned to its owner: only the owner's
					// departure closes it. A non-owner departure just
					// removes its invite/opt-in and membership.
					closeLobbyLocked(lobby);
				} else {
					lobby.invites.remove(
							player.playerName.toLowerCase(Locale.ROOT));
					if (lobby.optIns.remove(player) != null) {
						memberships.remove(player);
					}
				}
				return;
			}
			Session session = sessions.get(raidId);
			if (session == null) {
				memberships.remove(player);
				return;
			}
			if (player == session.owner) {
				wipe(session, "the raid owner departed");
			} else if (session.phase == PHASE_RUNNING) {
				session.departed.add(player);
				memberships.remove(player);
			} else if (session.phase == PHASE_BARRIER) {
				wipe(session,
						"a member departed during the reward barrier");
			}
		}
	}

	// ─── Guest callback execution ──────────────────────────────────────────

	private RaidRoomContext newRaidContext(Session session, int roomIndex,
			int elapsedTicks) {
		RaidDefinition definition = session.definition;
		String roomId = null;
		ScriptedPosition position = new ScriptedPosition(
				definition.entranceX(), definition.entranceY(),
				definition.entrancePlane());
		if (roomIndex >= 0) {
			RaidRoomDefinition room = definition.rooms().get(roomIndex);
			roomId = room.id();
			position = new ScriptedPosition(
					(room.bounds().minX() + room.bounds().maxX()) / 2,
					(room.bounds().minY() + room.bounds().maxY()) / 2,
					room.bounds().plane());
		}
		return new RaidRoomContext(session.handle,
				new ScriptedPlayer(session.owner, session.generation),
				definition.id(), roomId, roomIndex, elapsedTicks, position,
				activeParticipants(session));
	}

	private boolean runRoomCallback(Value handler, Session session,
			int roomIndex, String action) {
		if (handler == null) {
			return true;
		}
		int elapsed;
		synchronized (this) {
			elapsed = roomIndex == session.roomIndex ? session.roomTicks : 0;
		}
		return ScriptExecutor.executeChecked(handler, "raid",
				session.definition.id(), action,
				newRaidContext(session, roomIndex, elapsed));
	}

	private void runRaidCallback(Value handler, RaidRoomContext context,
			String action, Object... extraArguments) {
		if (handler == null) {
			return;
		}
		if (extraArguments.length == 0) {
			ScriptExecutor.execute(handler, "raid", context.id(), action,
					context);
		} else {
			ScriptExecutor.execute(handler, "raid", context.id(), action,
					context, extraArguments[0]);
		}
	}

	private RoomStatus runRoomTick(Value handler, Session session,
			int roomIndex) {
		if (handler == null || !handler.canExecute()) {
			return RoomStatus.IN_PROGRESS;
		}
		RaidRoomContext context = newRaidContext(session, roomIndex,
				session.roomTicks);
		try {
			Value result = handler.execute(context);
			if (result != null && result.hasMembers()) {
				Value status = result.getMember("status");
				if (status != null && status.isString()) {
					String value = status.asString();
					if ("completed".equals(value)) {
						return RoomStatus.COMPLETED;
					}
					if ("wiped".equals(value)) {
						Value reason = result.getMember("reason");
						return RoomStatus.wiped(reason != null
								&& reason.isString() ? reason.asString()
										: "the room failed");
					}
				}
			}
			return RoomStatus.IN_PROGRESS;
		} catch (RuntimeException failure) {
			logger.log(Level.WARNING, "Raid room tick for '" + session
					.definition.id() + "' room '" + roomIndex
					+ "' threw; the room wipes the raid", failure);
			return RoomStatus.wiped("the room tick callback threw");
		}
	}

	private List<ScriptedPlayer> activeParticipants(Session session) {
		List<Player> members;
		synchronized (this) {
			members = new ArrayList<Player>();
			for (Player member : session.roster) {
				if (!session.departed.contains(member)) {
					members.add(member);
				}
			}
		}
		List<ScriptedPlayer> scripted = new ArrayList<ScriptedPlayer>();
		for (Player member : members) {
			scripted.add(new ScriptedPlayer(member, session.generation));
		}
		return scripted;
	}

	// ─── Helpers ───────────────────────────────────────────────────────────

	private static boolean isLive(Player player) {
		return player != null && player.playerId >= 0
				&& player.playerId < PlayerHandler.players.length
				&& PlayerHandler.players[player.playerId] == player
				&& player.isActive && !player.disconnected
				&& player.initialized;
	}

	private static Player findLivePlayer(String name) {
		if (name == null) {
			return null;
		}
		for (Player player : PlayerHandler.players) {
			if (player != null && player.playerName != null
					&& player.playerName.equalsIgnoreCase(name)
					&& isLive(player)) {
				return player;
			}
		}
		return null;
	}

	private static void message(Player player, String text) {
		player.getPacketSender().sendMessage(text);
	}

	private long nextOwnerToken() {
		if (nextOwnerToken == Long.MAX_VALUE) {
			throw new IllegalStateException("raid owner token space exhausted");
		}
		return nextOwnerToken++;
	}

	private long nextSessionOrdinal() {
		if (nextSessionOrdinal == Long.MAX_VALUE) {
			throw new IllegalStateException(
					"raid session ordinal space exhausted");
		}
		return nextSessionOrdinal++;
	}

	/** Contained room-tick status of one guest {@code onTick} result. */
	private static final class RoomStatus {
		private static final RoomStatus IN_PROGRESS =
				new RoomStatus(false, null);
		private static final RoomStatus COMPLETED =
				new RoomStatus(false, null);
		private final boolean wiped;
		private final String reason;

		private RoomStatus(boolean wiped, String reason) {
			this.wiped = wiped;
			this.reason = reason;
		}

		static RoomStatus wiped(String reason) {
			return new RoomStatus(true, reason);
		}
	}

	/** Deferred guest/transaction action executed outside the monitor. */
	private interface RoomAction {
		void run();
	}

	/** Deferred wipe invocation for a session already marked PHASE_WIPED. */
	private RoomAction wipeAction(final Session session) {
		return new RoomAction() {
			@Override
			public void run() {
				invokeWipe(session, session.wipeReason == null
						? "the raid failed" : session.wipeReason);
			}
		};
	}

	/**
	 * Fails one session from a caller already outside the monitor (room
	 * callback failure): marks it wiped exactly once and invokes
	 * {@code onWipe} immediately.
	 */
	private void failSession(Session session, String reason) {
		boolean invoke;
		synchronized (this) {
			if (sessions.get(session.definition.id()) != session) {
				return;
			}
			wipe(session, reason);
			session.wipeInvoked = true;
			invoke = true;
		}
		if (invoke) {
			invokeWipe(session, reason);
		}
	}

	private static final class Lobby {
		private final RaidDefinition definition;
		private final long generation;
		private final Player owner;
		private final Map<String, Player> invites =
				new LinkedHashMap<String, Player>();
		private final Map<Player, String> optIns =
				new LinkedHashMap<Player, String>();

		Lobby(RaidDefinition definition, long generation, Player owner) {
			this.definition = definition;
			this.generation = generation;
			this.owner = owner;
		}
	}

	private final class Session
			implements RosterRewardTransaction.AwardCommit {
		private final RaidDefinition definition;
		private final long generation;
		private final Player owner;
		private final long ownerToken;
		private final long sessionOrdinal;
		private final ScriptEncounterHandle handle;
		private final List<Player> roster;
		private final RaidSessionRng rng;
		private final Set<Player> departed = Collections
				.newSetFromMap(new IdentityHashMap<Player, Boolean>());
		private List<Player> eligibleRoster = Collections.emptyList();
		private int phase = PHASE_RUNNING;
		private int roomIndex;
		private int roomTicks;
		private int sessionTicks;
		private int barrierTicksLeft;
		private int bossResult = RESULT_NONE;
		private boolean awarded;
		private long awardId;
		private long groundIdentityCount;
		private String wipeReason;
		private boolean wipeInvoked;

		Session(RaidDefinition definition, long generation, Player owner,
				long ownerToken, long sessionOrdinal,
				ScriptEncounterHandle handle, List<Player> roster) {
			this.definition = definition;
			this.generation = generation;
			this.owner = owner;
			this.ownerToken = ownerToken;
			this.sessionOrdinal = sessionOrdinal;
			this.handle = handle;
			this.roster = Collections.unmodifiableList(
					new ArrayList<Player>(roster));
			this.rng = new RaidSessionRng(ScriptRaidRuntime.this.processSeed,
					generation, ownerToken, sessionOrdinal);
		}

		/** Stable once-only award transaction id of owner token plus ordinal. */
		long awardId() {
			return ownerToken * 31L + sessionOrdinal;
		}

		@Override
		public boolean isAwarded() {
			synchronized (ScriptRaidRuntime.this) {
				return awarded;
			}
		}

		@Override
		public void markAwarded(long awardedId) {
			synchronized (ScriptRaidRuntime.this) {
				awarded = true;
				awardId = awardedId;
			}
		}
	}

	/**
	 * Raid-session RNG owner: exactly one per session with an immutable
	 * owner token, monotonic state version, state, and lock. The reward
	 * barrier and the reward-table roll transact through this owner;
	 * encounter-scoped room/boss randomness uses the accepted encounter
	 * handle surface.
	 */
	private final class RaidSessionRng
			implements DropRngTransactionOwner {

		private final Object mutex = new Object();
		private final long ownerToken;
		private long version;
		private long state;

		RaidSessionRng(long processSeed, long generation, long ownerToken,
				long sessionOrdinal) {
			this.ownerToken = ownerToken;
			this.state = ScriptEncounterRng.derive(processSeed, generation,
					ownerToken, sessionOrdinal).state();
		}

		@Override
		public void lock() {
			synchronized (mutex) {
				// Held for the complete transaction.
			}
		}

		@Override
		public void unlock() {
			synchronized (mutex) {
				// Released.
			}
		}

		@Override
		public long version() {
			synchronized (mutex) {
				return version;
			}
		}

		@Override
		public long state() {
			synchronized (mutex) {
				return state;
			}
		}

		@Override
		public void publishState(long nextState) {
			synchronized (mutex) {
				state = nextState;
				version++;
			}
		}
	}

	/**
	 * Private ground-delivery policy of the completion reward-table roll:
	 * exact live member recipient at their current position, private TTL,
	 * the session identity budget, invisible staging, and the final private
	 * detach through the exact-token cleanup path.
	 */
	private final class RaidGroundDelivery
			implements GroundDeliveryPolicy {

		private final Session session;
		private final Player member;
		private final long token;
		private final int x;
		private final int y;
		private final int plane;
		private final int privateTicks;

		RaidGroundDelivery(Session session, Player member, long token,
				int privateTicks) {
			this.session = session;
			this.member = member;
			this.token = token;
			this.x = member.absX;
			this.y = member.absY;
			this.plane = member.heightLevel;
			this.privateTicks = privateTicks;
		}

		@Override
		public boolean eligible() {
			return isLive(member) && privateTicks >= 1 && privateTicks <= 1000
					&& member.absX == x && member.absY == y
					&& member.heightLevel == plane;
		}

		@Override
		public int x() {
			return x;
		}

		@Override
		public int y() {
			return y;
		}

		@Override
		public int plane() {
			return plane;
		}

		@Override
		public boolean isPrivate() {
			return true;
		}

		@Override
		public int privateTicks() {
			return privateTicks;
		}

		@Override
		public long identityBudgetRemaining() {
			synchronized (ScriptRaidRuntime.this) {
				return MAX_GROUND_IDENTITIES_PER_RAID
						- session.groundIdentityCount;
			}
		}

		@Override
		public ScriptGroundItemHandle stage(ScriptedPlayer recipient,
				int itemId, int amount) {
			return GameEngine.itemHandler.createScriptGroundItems(member,
					token, itemId, amount, x, y, plane, 0);
		}

		@Override
		public void verifyStaged() {
		}

		@Override
		public boolean detach(List<ScriptGroundItemHandle> staged) {
			return GameEngine.itemHandler.detachExact(flatIdentities(staged),
					privateTicks);
		}

		@Override
		public void publish(List<ScriptGroundItemHandle> staged) {
			synchronized (ScriptRaidRuntime.this) {
				session.groundIdentityCount += flatIdentities(staged).size();
			}
		}

		@Override
		public void removeExact(List<ScriptGroundItemHandle> staged) {
			if (!staged.isEmpty()) {
				GameEngine.itemHandler.removeExact(flatIdentities(staged));
			}
		}

		private List<GroundItem> flatIdentities(
				List<ScriptGroundItemHandle> staged) {
			List<GroundItem> identities = new ArrayList<GroundItem>();
			for (ScriptGroundItemHandle handle : staged) {
				identities.addAll(handle.identities());
			}
			return identities;
		}
	}

}
