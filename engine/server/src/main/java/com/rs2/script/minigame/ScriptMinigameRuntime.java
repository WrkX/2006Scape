package com.rs2.script.minigame;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.graalvm.polyglot.Value;

import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.players.Player;
import com.rs2.script.CommandScriptContext;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.ScriptedPosition;
import com.rs2.script.ScriptExecutor;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.HostRoute;
import com.rs2.script.route.RouteRegistry;
import com.rs2.script.world.ScriptEncounterHandle;
import com.rs2.script.world.ScriptNpcHandle;
import com.rs2.util.LoggerUtils;

/**
 * Declarative minigame runtime for lobby queueing, one encounter per session,
 * ordered waves, and optional per-player score state.
 */
public final class ScriptMinigameRuntime {

	private static final int PHASE_RUNNING = 0;
	private static final int PHASE_COMPLETED = 1;
	private static final int PHASE_WIPED = 2;

	private static volatile ScriptMinigameRuntime INSTANCE =
			new ScriptMinigameRuntime();

	private static final Logger logger = LoggerUtils
			.getLogger(ScriptMinigameRuntime.class);

	private final Map<String, Lobby> lobbies =
			new LinkedHashMap<String, Lobby>();
	private final Map<String, Session> sessions =
			new LinkedHashMap<String, Session>();
	private final Map<Player, String> memberships =
			new IdentityHashMap<Player, String>();

	private ScriptMinigameRuntime() {
	}

	public static ScriptMinigameRuntime getInstance() {
		return INSTANCE;
	}

	public static ScriptMinigameRuntime installForTesting() {
		ScriptMinigameRuntime runtime = new ScriptMinigameRuntime();
		INSTANCE = runtime;
		return runtime;
	}

	public void registerRoutes(MinigameDefinition definition) {
		RouteRegistry.putHost(ExecutableRouteKey.command(
				definition.command()), commandRoute(definition));
	}

	public void onPlayerRemoved(Player player) {
		handleDeparture(player);
	}

	public void onPlayerLogout(Player player) {
		handleDeparture(player);
	}

	public void onPlayerDeath(Player player) {
		handleDeparture(player);
	}

	public void processGameTick(long generation) {
		List<Lobby> currentLobbies;
		synchronized (this) {
			currentLobbies = new ArrayList<Lobby>(lobbies.values());
		}
		for (Lobby lobby : currentLobbies) {
			if (lobby.generation != generation) {
				continue;
			}
			tickLobby(lobby);
		}
		List<Session> currentSessions;
		synchronized (this) {
			currentSessions = new ArrayList<Session>(sessions.values());
		}
		for (Session session : currentSessions) {
			if (session.generation != generation) {
				continue;
			}
			tickSession(session, generation);
		}
	}

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
			closeSession(session, "the minigame generation closed");
		}
	}

	public void resetForTesting() {
		synchronized (this) {
			lobbies.clear();
			sessions.clear();
			memberships.clear();
		}
	}

	public int lobbyCount() {
		synchronized (this) {
			return lobbies.size();
		}
	}

	public int sessionCount() {
		synchronized (this) {
			return sessions.size();
		}
	}

	public int membershipCount() {
		synchronized (this) {
			return memberships.size();
		}
	}

	private HostRoute commandRoute(final MinigameDefinition definition) {
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
				if ("join".equals(subcommand)) {
					join(definition, player);
				} else if ("leave".equals(subcommand)) {
					leave(definition, player);
				} else if ("start".equals(subcommand)) {
					start(definition, player);
				} else {
					message(player, "Minigame usage: ::"
							+ definition.command() + " join|leave|start");
				}
			}
		};
	}

	private void join(MinigameDefinition definition, Player player) {
		long generation = com.rs2.script.ScriptHost.getInstance()
				.getActiveGeneration();
		synchronized (this) {
			if (sessions.containsKey(definition.id())) {
				message(player, "A minigame session is already active.");
				return;
			}
			if (memberships.containsKey(player)) {
				message(player,
						"You are already in another minigame lobby or session.");
				return;
			}
			if (!definition.lobbyBounds().contains(player.absX, player.absY,
					player.heightLevel)) {
				message(player, "You must be in the lobby area to join.");
				return;
			}
			Lobby lobby = lobbies.get(definition.id());
			if (lobby == null) {
				lobby = new Lobby(definition, generation);
				lobbies.put(definition.id(), lobby);
			}
			if (lobby.members.size() >= definition.maxPlayers()) {
				message(player, "The minigame lobby is full.");
				return;
			}
			lobby.members.put(player, player);
			memberships.put(player, definition.id());
		}
		message(player, "You joined the " + definition.name() + " lobby.");
	}

	private void leave(MinigameDefinition definition, Player player) {
		Session session;
		synchronized (this) {
			session = sessions.get(definition.id());
			if (session != null && session.members.containsKey(player)) {
				session.members.remove(player);
				memberships.remove(player);
				if (session.members.isEmpty()) {
					wipeSessionLocked(session, "all members left");
				} else if (session.phase == PHASE_RUNNING) {
					wipeSessionLocked(session, "a member left during the game");
				}
				message(player, "You left the minigame.");
				return;
			}
			Lobby lobby = lobbies.get(definition.id());
			if (lobby != null && lobby.members.remove(player) != null) {
				memberships.remove(player);
				if (lobby.members.isEmpty()) {
					lobbies.remove(definition.id());
				}
				message(player, "You left the minigame lobby.");
				return;
			}
		}
		message(player, "You are not in this minigame.");
	}

	private void start(MinigameDefinition definition, Player player) {
		List<Player> roster;
		long generation;
		synchronized (this) {
			if (sessions.containsKey(definition.id())) {
				message(player, "A minigame session is already active.");
				return;
			}
			Lobby lobby = lobbies.get(definition.id());
			if (lobby == null) {
				message(player, "No minigame lobby is open.");
				return;
			}
			roster = new ArrayList<Player>(lobby.members.keySet());
			generation = lobby.generation;
		}
		if (roster.size() < definition.minPlayers()
				|| roster.size() > definition.maxPlayers()) {
			message(player, "The minigame requires "
					+ definition.minPlayers() + ".." + definition.maxPlayers()
					+ " players to start.");
			return;
		}
		for (Player member : roster) {
			if (!isLive(member)) {
				message(player, "A lobby member is no longer available.");
				return;
			}
			if (!definition.lobbyBounds().contains(member.absX, member.absY,
					member.heightLevel)) {
				message(player, member.playerName
						+ " is outside the lobby area.");
				return;
			}
		}
		ScriptedPlayer leader = new ScriptedPlayer(roster.get(0), generation);
		ScriptEncounterHandle handle = leader.beginEncounter(definition.id(),
				definition.arenaBounds().minX(),
				definition.arenaBounds().minY(),
				definition.arenaBounds().maxX(),
				definition.arenaBounds().maxY(),
				definition.arenaBounds().plane());
		if (handle == null) {
			message(player, "The arena is busy; try again shortly.");
			return;
		}
		for (int index = 1; index < roster.size(); index++) {
			Player member = roster.get(index);
			if (!handle.addParticipant(
					new ScriptedPlayer(member, generation))) {
				handle.close();
				message(player,
						"The minigame could not admit every member.");
				return;
			}
		}
		for (Player member : roster) {
			ScriptedPlayer scripted = new ScriptedPlayer(member, generation);
			scripted.teleport(definition.entranceX(), definition.entranceY(),
					definition.entrancePlane());
			resetScore(definition, scripted);
		}
		Session session;
		synchronized (this) {
			Lobby lobby = lobbies.remove(definition.id());
			if (lobby == null) {
				handle.close();
				return;
			}
			session = new Session(definition, generation, handle, roster);
			sessions.put(definition.id(), session);
		}
		runCallback(definition.onStart(), session, -1, "onStart");
		beginWave(session, 0);
	}

	private void tickLobby(Lobby lobby) {
		if (lobby.definition.lobbyWaitTicks() <= 0) {
			return;
		}
		lobby.waitTicks++;
		if (lobby.waitTicks < lobby.definition.lobbyWaitTicks()) {
			return;
		}
		if (lobby.members.size() < lobby.definition.minPlayers()) {
			lobby.waitTicks = 0;
			return;
		}
		Player starter = lobby.members.keySet().iterator().next();
		start(lobby.definition, starter);
	}

	private void tickSession(Session session, long generation) {
		TickAction action = null;
		synchronized (this) {
			if (session.phase != PHASE_RUNNING) {
				return;
			}
			session.elapsedTicks++;
			if (session.elapsedTicks > session.definition.timeLimitTicks()) {
				wipeSessionLocked(session, "time expired");
				action = TickAction.WIPED;
			} else if (waveCleared(session)) {
				action = TickAction.WAVE_CLEARED;
			}
		}
		if (action == TickAction.WAVE_CLEARED) {
			runCallback(session.definition.onWaveComplete(), session,
					session.waveIndex, "onWaveComplete");
			synchronized (this) {
				if (session.phase != PHASE_RUNNING) {
					return;
				}
				int nextWave = session.waveIndex + 1;
				if (nextWave >= session.definition.waves().size()) {
					completeSessionLocked(session);
					action = TickAction.COMPLETED;
				} else {
					beginWaveLocked(session, nextWave);
				}
			}
		}
		if (action == TickAction.COMPLETED) {
			runCallback(session.definition.onComplete(), session, -1,
					"onComplete");
			teleportMembersToLeave(session);
			closeSession(session, null);
			return;
		}
		if (action == TickAction.WIPED) {
			runWipeCallback(session, session.wipeReason);
			teleportMembersToLeave(session);
			closeSession(session, null);
			return;
		}
		TickStatus status = runTickCallback(session);
		if (status.wiped) {
			synchronized (this) {
				if (session.phase == PHASE_RUNNING) {
					wipeSessionLocked(session, status.reason);
				}
			}
			runWipeCallback(session, session.wipeReason);
			teleportMembersToLeave(session);
			closeSession(session, null);
		}
	}

	private void beginWave(Session session, int waveIndex) {
		synchronized (this) {
			beginWaveLocked(session, waveIndex);
		}
	}

	private void beginWaveLocked(Session session, int waveIndex) {
		session.waveIndex = waveIndex;
		session.waveTicks = 0;
		session.waveNpcs.clear();
		MinigameWaveDefinition wave = session.definition.waves().get(waveIndex);
		for (MinigameWaveSpawn spawn : wave.spawns()) {
			int hp = NpcHandler.getNpcListHP(spawn.npcId());
			if (hp <= 0) {
				hp = 10;
			}
			ScriptNpcHandle npc = session.handle.spawnNpc(spawn.npcId(),
					spawn.x(), spawn.y(), spawn.plane(), hp, 1, 1, 1);
			if (npc != null) {
				session.waveNpcs.add(npc);
			}
		}
		runCallback(session.definition.onWaveStart(), session, waveIndex,
				"onWaveStart");
	}

	private boolean waveCleared(Session session) {
		if (session.waveNpcs.isEmpty()) {
			return false;
		}
		for (ScriptNpcHandle npc : session.waveNpcs) {
			if (npc.isAlive()) {
				return false;
			}
		}
		return true;
	}

	private void completeSessionLocked(Session session) {
		session.phase = PHASE_COMPLETED;
	}

	private void wipeSessionLocked(Session session, String reason) {
		session.phase = PHASE_WIPED;
		session.wipeReason = reason;
	}

	private void closeSession(Session session, String reason) {
		if (reason != null) {
			runWipeCallback(session, reason);
			teleportMembersToLeave(session);
		}
		if (session.handle != null && session.handle.isOpen()) {
			session.handle.close();
		}
		synchronized (this) {
			for (Player member : session.members.keySet()) {
				memberships.remove(member);
			}
			sessions.remove(session.definition.id());
		}
	}

	private void closeLobbyLocked(Lobby lobby) {
		synchronized (this) {
			for (Player member : lobby.members.keySet()) {
				memberships.remove(member);
			}
			lobbies.remove(lobby.definition.id());
		}
	}

	private void handleDeparture(Player player) {
		if (player == null) {
			return;
		}
		String minigameId;
		synchronized (this) {
			minigameId = memberships.remove(player);
		}
		if (minigameId == null) {
			return;
		}
		MinigameDefinition definition = MinigameDefinitionRegistry.get(minigameId);
		if (definition == null) {
			return;
		}
		leave(definition, player);
	}

	private void teleportMembersToLeave(Session session) {
		for (Player member : session.members.keySet()) {
			if (!isLive(member)) {
				continue;
			}
			new ScriptedPlayer(member, session.generation).teleport(
					session.definition.leaveX(),
					session.definition.leaveY(),
					session.definition.leavePlane());
		}
	}

	private void resetScore(MinigameDefinition definition,
			ScriptedPlayer player) {
		MinigameScoreDefinition score = definition.score();
		if (score == null) {
			return;
		}
		player.state(score.namespace()).setNumber(score.key(), 0D);
	}

	private MinigameContext newContext(Session session, int waveIndex) {
		String waveId = waveIndex >= 0
				&& waveIndex < session.definition.waves().size()
				? session.definition.waves().get(waveIndex).id() : null;
		List<ScriptedPlayer> participants = new ArrayList<ScriptedPlayer>();
		for (Player member : session.members.keySet()) {
			participants.add(new ScriptedPlayer(member, session.generation));
		}
		return new MinigameContext(session.handle, session.definition.id(),
				waveId, waveIndex, session.elapsedTicks,
				new ScriptedPosition(session.definition.entranceX(),
						session.definition.entranceY(),
						session.definition.entrancePlane()),
				participants, session.definition.score());
	}

	private void runCallback(Value handler, Session session, int waveIndex,
			String action) {
		if (handler == null) {
			return;
		}
		ScriptExecutor.execute(handler, "minigame", session.definition.id(),
				action, newContext(session, waveIndex));
	}

	private void runWipeCallback(Session session, String reason) {
		Value handler = session.definition.onWipe();
		if (handler == null) {
			return;
		}
		ScriptExecutor.execute(handler, "minigame", session.definition.id(),
				"onWipe", newContext(session, session.waveIndex), reason);
	}

	private TickStatus runTickCallback(Session session) {
		Value handler = session.definition.onTick();
		if (handler == null || !handler.canExecute()) {
			return TickStatus.IN_PROGRESS;
		}
		try {
			Value result = handler.execute(newContext(session,
					session.waveIndex));
			if (result != null && result.hasMembers()) {
				Value status = result.getMember("status");
				if (status != null && status.isString()
						&& "wiped".equals(status.asString())) {
					Value reason = result.getMember("reason");
					return TickStatus.wiped(reason != null && reason.isString()
							? reason.asString() : "the minigame failed");
				}
			}
			return TickStatus.IN_PROGRESS;
		} catch (RuntimeException failure) {
			logger.log(Level.WARNING, "Minigame tick for '"
					+ session.definition.id() + "' failed", failure);
			return TickStatus.wiped("the minigame tick callback threw");
		}
	}

	private static boolean isLive(Player player) {
		return player != null && player.initialized && player.isActive
				&& !player.disconnected && !player.isDead;
	}

	private static void message(Player player, String text) {
		if (player != null && text != null) {
			player.getPacketSender().sendMessage(text);
		}
	}

	private static final class Lobby {
		private final MinigameDefinition definition;
		private final long generation;
		private final LinkedHashMap<Player, Player> members =
				new LinkedHashMap<Player, Player>();
		private int waitTicks;

		private Lobby(MinigameDefinition definition, long generation) {
			this.definition = definition;
			this.generation = generation;
		}
	}

	private static final class Session {
		private final MinigameDefinition definition;
		private final long generation;
		private final ScriptEncounterHandle handle;
		private final LinkedHashMap<Player, Player> members =
				new LinkedHashMap<Player, Player>();
		private final List<ScriptNpcHandle> waveNpcs =
				new ArrayList<ScriptNpcHandle>();
		private int phase = PHASE_RUNNING;
		private int waveIndex = -1;
		private int waveTicks;
		private int elapsedTicks;
		private String wipeReason;

		private Session(MinigameDefinition definition, long generation,
				ScriptEncounterHandle handle, List<Player> roster) {
			this.definition = definition;
			this.generation = generation;
			this.handle = handle;
			for (Player member : roster) {
				this.members.put(member, member);
			}
		}
	}

	private enum TickAction {
		WAVE_CLEARED,
		COMPLETED,
		WIPED
	}

	private static final class TickStatus {
		private static final TickStatus IN_PROGRESS = new TickStatus(false,
				null);
		private final boolean wiped;
		private final String reason;

		private TickStatus(boolean wiped, String reason) {
			this.wiped = wiped;
			this.reason = reason;
		}

		static TickStatus wiped(String reason) {
			return new TickStatus(true, reason);
		}
	}
}
