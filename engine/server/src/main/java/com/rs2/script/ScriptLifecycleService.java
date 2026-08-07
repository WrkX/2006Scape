package com.rs2.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.Value;

import com.rs2.game.npcs.Npc;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.registries.LifecycleRegistry;
import com.rs2.script.registries.ScriptArea;
import com.rs2.script.scheduler.ScriptScheduler;
import com.rs2.script.context.PlayerDeathScriptContext;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.script.world.ScriptPlayerDeathTicket;
import com.rs2.script.snapshot.ScriptNpcSnapshot;

/**
 * Observes authoritative engine transitions and dispatches bounded lifecycle
 * callbacks without replacing legacy gameplay behavior.
 */
public final class ScriptLifecycleService {

	private static final ScriptLifecycleService INSTANCE = new ScriptLifecycleService();

	private final Set<Player> initializedPlayers =
			Collections.newSetFromMap(new IdentityHashMap<Player, Boolean>());
	private final Map<Player, AreaState> areaStates = new IdentityHashMap<>();

	public static ScriptLifecycleService getInstance() {
		return INSTANCE;
	}

	public void onLogin(Player player) {
		ScriptEncounterService.getInstance().onPlayerLogin(player);
		com.rs2.script.shop.ScriptShopRuntime.getInstance()
				.onPlayerRemoved(player);
		if (!ScriptHost.getInstance().executeInActiveGeneration(
				new ScriptHost.ActiveGenerationOperation() {
			@Override
			public void run(long generation) {
				synchronized (ScriptLifecycleService.this) {
					if (!initializedPlayers.add(player)) {
						return;
					}
					areaStates.put(player, new AreaState(generation,
							new ScriptedPosition(player.absX, player.absY,
									player.heightLevel),
							new HashSet<String>()));
				}
				ScriptExecutor.execute(LifecycleRegistry.getSingleton("login"),
						"player", player.playerName, "login",
						new LoginScriptContext(new ScriptedPlayer(player, generation)));
			}
		})) {
			synchronized (this) {
				initializedPlayers.add(player);
			}
		}
	}

	public void onExplicitLogout(Player player) {
		ScriptEncounterService.getInstance().onPlayerLogout(player);
		com.rs2.script.raid.ScriptRaidRuntime.getInstance()
				.onPlayerLogout(player);
		ScriptScheduler.getInstance().cancelPlayer(player);
	}

	public void onPlayerRemoved(Player player) {
		ScriptEncounterService.getInstance().onPlayerRemoved(player);
		com.rs2.script.shop.ScriptShopRuntime.getInstance()
				.onPlayerRemoved(player);
		com.rs2.script.raid.ScriptRaidRuntime.getInstance()
				.onPlayerRemoved(player);
		com.rs2.script.resource.ScriptResourceRuntime.getInstance()
				.onPlayerRemoved(player);
		com.rs2.script.processing.ScriptProcessingRuntime.getInstance()
				.onPlayerRemoved(player);
		if (!ScriptHost.getInstance().executeInActiveGeneration(
				new ScriptHost.ActiveGenerationOperation() {
			@Override
			public void run(long generation) {
				boolean removed;
				synchronized (ScriptLifecycleService.this) {
					removed = initializedPlayers.remove(player);
					areaStates.remove(player);
				}
				if (removed) {
					ScriptExecutor.execute(LifecycleRegistry.getSingleton("logout"),
							"player", player.playerName, "logout",
							new LogoutScriptContext(new ScriptedPlayer(player, generation)));
				}
			}
		})) {
			synchronized (this) {
				initializedPlayers.remove(player);
				areaStates.remove(player);
			}
		}
		ScriptScheduler.getInstance().cancelPlayer(player);
	}

	public void onNpcDeath(Npc npc, Player killer) {
		if (npc == null) {
			return;
		}
		onNpcDeath(npc, killer, ScriptNpcSnapshot.capture(npc),
				new ScriptedPosition(npc.absX, npc.absY, npc.heightLevel));
	}

	public void onNpcDeath(Npc npc, Player killer, ScriptNpcSnapshot snapshot,
			ScriptedPosition position) {
		onNpcDeath(npc, killer, snapshot, position,
				npc == null ? 0 : npc.HP, npc != null && npc.isDead,
				npc == null ? 0 : npc.combatLevel);
	}

	public void onNpcDeath(Npc npc, Player killer, ScriptNpcSnapshot snapshot,
			ScriptedPosition position, int capturedHp, boolean capturedDead,
			int capturedCombatLevel) {
		if (npc == null) {
			return;
		}
		ScriptHost.getInstance().executeInActiveGeneration(
				new ScriptHost.ActiveGenerationOperation() {
			@Override
			public void run(long generation) {
				int npcType = npc.npcType;
				ScriptedPlayer scriptedKiller = killer == null ? null
						: new ScriptedPlayer(killer, generation);
				ScriptExecutor.execute(LifecycleRegistry.getNpcDeath(npcType),
						"npc", Integer.toString(npcType), "death",
						new NpcDeathScriptContext(ScriptedNpc.snapshot(npc, snapshot,
								capturedHp, capturedDead, capturedCombatLevel),
								scriptedKiller, position));
			}
		});
	}

	public void onItemPickup(Player player, int itemId, int amount,
			int x, int y, int plane) {
		if (amount <= 0) {
			return;
		}
		ScriptHost.getInstance().executeInActiveGeneration(
				new ScriptHost.ActiveGenerationOperation() {
			@Override
			public void run(long generation) {
				ScriptExecutor.execute(LifecycleRegistry.getItemPickup(itemId),
						"item", Integer.toString(itemId), "pickup",
						new ItemPickupScriptContext(
								new ScriptedPlayer(player, generation),
								ScriptedItem.byId(itemId), amount,
								new ScriptedPosition(x, y, plane)));
			}
		});
	}

	public void processGameTick() {
		ScriptEncounterService.getInstance().processGameTick();
		ScriptScheduler.getInstance().processGameTick();

		ScriptHost.getInstance().executeInActiveGeneration(
				new ScriptHost.ActiveGenerationOperation() {
			@Override
			public void run(long generation) {
				processAreasUnderLease(generation);
				com.rs2.script.raid.ScriptRaidRuntime.getInstance()
						.processGameTick(generation);
			}
		});
	}

	/**
	 * Captures terminal player state and performs callback-free encounter
	 * ownership cleanup immediately before core death bookkeeping. Raid
	 * membership marks the dead member departed (or wipes on owner/barrier
	 * death) without running guest code.
	 */
	public ScriptPlayerDeathTicket beginPlayerDeath(Player player) {
		com.rs2.script.raid.ScriptRaidRuntime.getInstance()
				.onPlayerDeath(player);
		com.rs2.script.resource.ScriptResourceRuntime.getInstance()
				.onPlayerDeath(player);
		com.rs2.script.processing.ScriptProcessingRuntime.getInstance()
				.onPlayerDeath(player);
		return ScriptEncounterService.getInstance().beginPlayerDeath(player);
	}

	/**
	 * Dispatches the singleton player-death observer only after applyDead has
	 * completed. Registry lookup and invocation share one active-state lease.
	 */
	public void completePlayerDeath(ScriptPlayerDeathTicket ticket) {
		if (!ScriptEncounterService.getInstance().completePlayerDeath(ticket)) {
			return;
		}
		final PlayerDeathScriptContext context = new PlayerDeathScriptContext(
				ticket.player(), ticket.killer(), ticket.position());
		ScriptHost.getInstance().dispatchObserverActive(
				new ScriptHost.ObserverLookup() {
					@Override
					public Value find(com.rs2.script.registries.RegistryStore.State state) {
						return LifecycleRegistry.getPlayerDeath(state);
					}
				},
				new ScriptHost.ObserverInvocation() {
					@Override
					public void invoke(long generation, Value handler) {
						ScriptExecutor.execute(handler, "player",
								ticket.player().username(), "death", context);
					}
				});
	}

	/**
	 * Re-baselines online players after a successful reload. This deliberately
	 * emits no synthetic enter or leave transitions.
	 */
	public synchronized void onGenerationCommitted(long generation) {
		areaStates.clear();
		initializedPlayers.clear();
		for (Player player : PlayerHandler.players) {
			if (isLiveInitialized(player)) {
				initializedPlayers.add(player);
				Set<String> memberships = memberships(player, LifecycleRegistry.areas());
				areaStates.put(player, new AreaState(generation,
						new ScriptedPosition(player.absX, player.absY, player.heightLevel),
						memberships));
				com.rs2.game.content.quests.QuestAssistant.sendStages(player);
			}
		}
	}

	private void processAreasUnderLease(long generation) {
		List<Player> players = liveInitializedPlayers();
		List<ScriptArea> areas = LifecycleRegistry.areas();
		List<AreaTransition> transitions = new ArrayList<>();
		for (Player player : players) {
			ScriptedPosition to = new ScriptedPosition(
					player.absX, player.absY, player.heightLevel);
			AreaState previous;
			Set<String> current = memberships(player, areas);
			synchronized (this) {
				previous = areaStates.get(player);
				if (previous == null || previous.generation != generation) {
					areaStates.put(player, new AreaState(generation, to, current));
					continue;
				}
				areaStates.put(player, new AreaState(generation, to, current));
			}
			for (ScriptArea area : areas) {
				boolean wasInside = previous.memberships.contains(area.getId());
				boolean isInside = current.contains(area.getId());
				if (wasInside == isInside) {
					continue;
				}
				String event = isInside ? "enter" : "leave";
				transitions.add(new AreaTransition(
						LifecycleRegistry.getAreaHandler(event, area.getId()),
						area.getId(), event,
						new AreaTransitionScriptContext(
								new ScriptedPlayer(player, generation), area,
								previous.position, to, event)));
			}
		}
		for (AreaTransition transition : transitions) {
			ScriptExecutor.execute(transition.handler, "area", transition.areaId,
					transition.action, transition.context);
		}
	}

	private static Set<String> memberships(Player player, List<ScriptArea> areas) {
		Set<String> memberships = new HashSet<>();
		for (ScriptArea area : areas) {
			if (area.contains(player.absX, player.absY, player.heightLevel)) {
				memberships.add(area.getId());
			}
		}
		return memberships;
	}

	private static List<Player> liveInitializedPlayers() {
		List<Player> players = new ArrayList<>();
		for (Player player : PlayerHandler.players) {
			if (isLiveInitialized(player)) {
				players.add(player);
			}
		}
		Collections.sort(players, new Comparator<Player>() {
			@Override
			public int compare(Player first, Player second) {
				return Integer.compare(first.playerId, second.playerId);
			}
		});
		return players;
	}

	private static boolean isLiveInitialized(Player player) {
		return player != null && player.isActive && !player.disconnected
				&& player.initialized;
	}

	private static final class AreaState {
		private final long generation;
		private final ScriptedPosition position;
		private final Set<String> memberships;

		private AreaState(long generation, ScriptedPosition position,
				Set<String> memberships) {
			this.generation = generation;
			this.position = position;
			this.memberships = memberships;
		}
	}

	private static final class AreaTransition {
		private final Value handler;
		private final String areaId;
		private final String action;
		private final AreaTransitionScriptContext context;

		private AreaTransition(Value handler, String areaId, String action,
				AreaTransitionScriptContext context) {
			this.handler = handler;
			this.areaId = areaId;
			this.action = action;
			this.context = context;
		}
	}

	private ScriptLifecycleService() {
	}
}
