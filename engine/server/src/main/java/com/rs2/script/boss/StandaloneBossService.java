package com.rs2.script.boss;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.rs2.game.players.Player;
import com.rs2.script.CommandScriptContext;
import com.rs2.script.ScriptContext;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.ScriptedPosition;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.HostRoute;
import com.rs2.script.route.RouteRegistry;
import com.rs2.script.world.ScriptEncounterHandle;

/**
 * Standalone owning adapter for declarative bosses.
 *
 * <p>For every registered {@code defineBoss} the service registers the
 * definition's exact WP1 host command/object entry route (and optional
 * close route). On entry it begins exactly one encounter, teleports the
 * owner, starts an encounter-agnostic {@link BossController} with the
 * owner-only participant view, and closes the handle on any terminal result
 * (normal death, explicit close, callback failure, owner death/logout, or
 * successful reload). The controller never begins or closes the encounter
 * itself; this adapter owns that exactly-once decision.
 */
public final class StandaloneBossService {

	private static final StandaloneBossService INSTANCE =
			new StandaloneBossService();

	private final Map<Player, Session> sessions =
			new IdentityHashMap<Player, Session>();

	public static StandaloneBossService getInstance() {
		return INSTANCE;
	}

	private StandaloneBossService() {
	}

	/**
	 * Registers the definition's exact WP1 host routes in the loading
	 * candidate. Duplicate keys and reserved admin aliases reject the whole
	 * candidate through the unified route registry.
	 */
	public void registerRoutes(BossDefinition definition) {
		if (definition.command() != null) {
			RouteRegistry.putHost(ExecutableRouteKey.command(
					definition.command()), commandRoute(definition, false));
		}
		if (definition.closeCommand() != null) {
			RouteRegistry.putHost(ExecutableRouteKey.command(
					definition.closeCommand()), commandRoute(definition, true));
		}
		if (definition.hasObjectEntry()) {
			RouteRegistry.putHost(ExecutableRouteKey.object(
					definition.objectEntryId(),
					definition.objectEntryAction()),
					objectRoute(definition));
		}
	}

	/**
	 * Closes and removes every session of one generation. Called by the
	 * script host after a successful reload before the old context closes.
	 */
	public synchronized void closeGeneration(long generation) {
		List<Session> closing = new ArrayList<Session>();
		for (Session session : sessions.values()) {
			if (session.generation == generation) {
				closing.add(session);
			}
		}
		for (Session session : closing) {
			terminate(session.owner, session.handle);
		}
	}

	/** Closes and removes every session. Test-only lifecycle reset. */
	public synchronized void resetForTesting() {
		for (Session session : new ArrayList<Session>(sessions.values())) {
			terminate(session.owner, session.handle);
		}
	}

	/** Engine-visible count for tests; never exported to guest code. */
	public synchronized int sessionCount() {
		return sessions.size();
	}

	private HostRoute commandRoute(final BossDefinition definition,
			final boolean close) {
		return new HostRoute() {
			@Override
			public void invoke(Object... arguments) {
				Player player = playerOf(arguments);
				if (player == null) {
					return;
				}
				if (close) {
					closeExplicit(definition, player);
				} else {
					enter(definition, player);
				}
			}
		};
	}

	private HostRoute objectRoute(final BossDefinition definition) {
		return new HostRoute() {
			@Override
			public void invoke(Object... arguments) {
				Player player = playerOf(arguments);
				if (player == null) {
					return;
				}
				enter(definition, player);
			}
		};
	}

	private static Player playerOf(Object[] arguments) {
		if (arguments == null || arguments.length < 1) {
			return null;
		}
		if (arguments[0] instanceof CommandScriptContext) {
			return ((CommandScriptContext) arguments[0]).player.backingPlayer();
		}
		if (arguments[0] instanceof ScriptContext) {
			return ((ScriptContext) arguments[0]).player.backingPlayer();
		}
		if (arguments[0] instanceof ScriptedPlayer) {
			return ((ScriptedPlayer) arguments[0]).backingPlayer();
		}
		return null;
	}

	/**
	 * Enters a boss for one live player. The session-map bookkeeping is
	 * synchronized, but the encounter begin, teleport, and the controller
	 * start (which runs the guest {@code onSpawn} callback) deliberately
	 * happen outside the monitor so guest code never executes while this
	 * service's lock is held. The encounter service itself serializes
	 * overlapping begins for the same player and arena.
	 */
	private void enter(BossDefinition definition, Player player) {
		if (player == null) {
			return;
		}
		synchronized (this) {
			Session existing = sessions.get(player);
			if (existing != null && existing.handle.isOpen()) {
				player.getPacketSender().sendMessage(
						"The boss arena is busy.");
				return;
			}
			if (existing != null) {
				sessions.remove(player);
			}
		}
		ScriptedPlayer scripted = new ScriptedPlayer(player);
		ScriptEncounterHandle handle = scripted.beginEncounter(definition.id(),
				definition.arena().minX(), definition.arena().minY(),
				definition.arena().maxX(), definition.arena().maxY(),
				definition.arena().plane());
		if (handle == null) {
			player.getPacketSender().sendMessage(
					"The boss arena is busy.");
			return;
		}
		if (definition.hasEntryTeleport()) {
			scripted.teleport(definition.entryTeleportX(),
					definition.entryTeleportY(), definition.arena().plane());
		}
		List<ScriptedPlayer> participants = new ArrayList<ScriptedPlayer>();
		participants.add(scripted);
		BossController controller = BossController.start(definition, handle,
				definition.arena(), scripted, participants,
				terminalListener(player, handle));
		synchronized (this) {
			if (controller.status() != BossController.Status.RUNNING) {
				// The terminal listener already closed the handle and any
				// session; nothing is inserted for a failed start.
				player.getPacketSender().sendMessage(
						"The boss could not be summoned.");
				return;
			}
			sessions.put(player,
					new Session(player, handle, scripted.generation()));
		}
	}

	private synchronized void closeExplicit(BossDefinition definition,
			Player player) {
		Session session = sessions.get(player);
		if (session == null || !session.handle.isOpen()) {
			player.getPacketSender().sendMessage(
					"No boss encounter is active.");
			return;
		}
		terminate(player, session.handle);
		player.getPacketSender().sendMessage(
				"The boss arena has been sealed.");
	}

	private BossController.TerminalListener terminalListener(
			final Player player, final ScriptEncounterHandle handle) {
		return new BossController.TerminalListener() {
			@Override
			public void onTerminal(BossController controller,
					BossController.Status status,
					ScriptedPosition deathPosition, ScriptedPlayer killer) {
				terminate(player, handle);
			}
		};
	}

	private synchronized void terminate(Player player,
			ScriptEncounterHandle handle) {
		Session session = sessions.get(player);
		if (session != null && session.handle == handle) {
			sessions.remove(player);
		}
		if (handle != null && handle.isOpen()) {
			handle.close();
		}
	}

	private static final class Session {
		private final Player owner;
		private final ScriptEncounterHandle handle;
		private final long generation;

		Session(Player owner, ScriptEncounterHandle handle, long generation) {
			this.owner = owner;
			this.handle = handle;
			this.generation = generation;
		}
	}

}
