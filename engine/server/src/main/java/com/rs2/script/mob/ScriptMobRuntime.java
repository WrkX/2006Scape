package com.rs2.script.mob;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.Value;

import com.rs2.game.content.combat.npcs.NpcEmotes;
import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcData;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.players.Client;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.ScriptExecutor;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.ScriptedPosition;
import com.rs2.script.world.ScriptNpcService;

/**
 * Java-owned world mob runtime for {@code defineMob}.
 *
 * <p>Combat ownership is read from the active {@link MobDefinitionRegistry}
 * (generation-scoped). Registered cache NPC ids suppress the legacy
 * {@code NpcCombat} switch and use declarative aggression, attack speed,
 * combat style, and max hit. Optional {@code onSpawn}/{@code onTick}/
 * {@code onDeath} callbacks are invalidated on reload and NPC despawn.
 */
public final class ScriptMobRuntime {

	private static volatile ScriptMobRuntime INSTANCE = new ScriptMobRuntime();

	/** Allocation tokens that have already received {@code onSpawn}. */
	private final Set<Long> spawnedTokens = new HashSet<Long>();
	/** Allocation tokens currently tracked for tick/death callbacks. */
	private final Map<Long, Integer> trackedSlots = new HashMap<Long, Integer>();

	private long activeGeneration;

	private ScriptMobRuntime() {
	}

	public static ScriptMobRuntime getInstance() {
		return INSTANCE;
	}

	public static ScriptMobRuntime installForTesting() {
		ScriptMobRuntime runtime = new ScriptMobRuntime();
		INSTANCE = runtime;
		return runtime;
	}

	/**
	 * Marks the definition as installed for the loading candidate. Combat
	 * ownership comes from {@link MobDefinitionRegistry}; this only prepares
	 * runtime bookkeeping for the upcoming generation publish.
	 */
	public void register(MobDefinition definition) {
		if (definition == null) {
			throw new IllegalArgumentException("mob definition must not be null");
		}
	}

	public boolean owns(int npcId) {
		return MobDefinitionRegistry.get(npcId) != null;
	}

	public MobDefinition definitionFor(int npcId) {
		return MobDefinitionRegistry.get(npcId);
	}

	/**
	 * Declarative max hit for a registered mob, or {@code -1} when the id is
	 * not owned by this runtime.
	 */
	public int maxHit(int npcId) {
		MobDefinition definition = MobDefinitionRegistry.get(npcId);
		return definition == null ? -1 : definition.maxHit();
	}

	public synchronized void onGenerationPublished(long generation) {
		// New callbacks replace the previous generation; re-fire onSpawn for
		// any live world NPCs that remain after reload.
		spawnedTokens.clear();
		trackedSlots.clear();
		activeGeneration = generation;
	}

	/**
	 * Drops tracked instances for a closed generation so guest callbacks
	 * cannot fire after their registering context is gone.
	 */
	public synchronized void closeGeneration(long generation) {
		if (generation == 0L) {
			return;
		}
		if (activeGeneration == generation) {
			spawnedTokens.clear();
			trackedSlots.clear();
			activeGeneration = 0L;
		}
	}

	public synchronized void resetForTesting() {
		spawnedTokens.clear();
		trackedSlots.clear();
		activeGeneration = 0L;
	}

	public synchronized int trackedCount() {
		return trackedSlots.size();
	}

	/**
	 * Finds a player inside the mob's aggression radius, or {@code 0}.
	 * Aggression {@code 0} never auto-aggros.
	 */
	public int findAggressionTarget(int npcIndex) {
		if (npcIndex < 0 || npcIndex >= NpcHandler.npcs.length
				|| NpcHandler.npcs[npcIndex] == null) {
			return 0;
		}
		Npc npc = NpcHandler.npcs[npcIndex];
		MobDefinition definition = MobDefinitionRegistry.get(npc.npcType);
		if (definition == null || definition.aggression() <= 0) {
			return 0;
		}
		int radius = definition.aggression();
		for (int j = 0; j < PlayerHandler.players.length; j++) {
			Player player = PlayerHandler.players[j];
			if (player == null) {
				continue;
			}
			if (!ScriptNpcService.getInstance().canAct(npc, player)) {
				continue;
			}
			if (player.heightLevel != npc.heightLevel) {
				continue;
			}
			if (player.underAttackBy > 0 || player.underAttackBy2 > 0) {
				continue;
			}
			int dx = Math.abs(player.absX - npc.absX);
			int dy = Math.abs(player.absY - npc.absY);
			if (Math.max(dx, dy) <= radius) {
				return j;
			}
		}
		return 0;
	}

	/**
	 * Replaces {@link com.rs2.game.content.combat.npcs.NpcCombat#attackPlayer}
	 * for registered mob ids: sets style, attack timer, hit delay, animation,
	 * and arms the legacy hit-application path with declarative max hit.
	 *
	 * @return {@code true} when this runtime consumed the attack (caller must
	 *         not run legacy {@code NpcCombat} / {@code loadSpell})
	 */
	public boolean tryAttack(Player player, int npcIndex) {
		if (player == null || npcIndex < 0 || npcIndex >= NpcHandler.npcs.length
				|| NpcHandler.npcs[npcIndex] == null) {
			return false;
		}
		Npc npc = NpcHandler.npcs[npcIndex];
		MobDefinition definition = MobDefinitionRegistry.get(npc.npcType);
		if (definition == null) {
			return false;
		}
		if (!ScriptNpcService.getInstance().canAct(npc, player)) {
			return true;
		}
		if (npc.isDead || player.respawnTimer > 0) {
			return true;
		}
		if (!NpcData.goodDistanceNpc(npc.npcId, player.getX(), player.getY(),
				NpcData.distanceRequired(npc.npcId))
				|| NpcData.inNpc(npc.npcId, player.getX(), player.getY())) {
			return true;
		}
		if (!NpcData.checkClip(npc)) {
			return true;
		}
		npc.facePlayer(player);
		npc.attackTimer = definition.attackSpeed();
		npc.hitDelayTimer = 2;
		npc.attackType = definition.combatStyle().attackType().getValue();
		npc.projectileId = -1;
		npc.endGfx = -1;
		int animation = definition.animation() >= 0
				? definition.animation()
				: NpcEmotes.getAttackEmote(npcIndex);
		NpcData.startAnimation(animation, npcIndex);
		player.underAttackBy2 = npcIndex;
		player.singleCombatDelay2 = System.currentTimeMillis();
		npc.oldIndex = player.playerId;
		npc.oldAllocationToken = npc.allocationToken();
		if (player instanceof Client) {
			((Client) player).getPacketSender().closeAllWindows();
		}
		return true;
	}

	/**
	 * Per-tick bookkeeping: discover new allocations ({@code onSpawn}), run
	 * {@code onTick}, and drop despawned tokens so callbacks stay generation-
	 * and allocation-valid.
	 */
	public void processGameTick(long generation) {
		if (generation == 0L) {
			return;
		}
		synchronized (this) {
			if (activeGeneration != generation) {
				return;
			}
		}
		Set<Long> liveTokens = new HashSet<Long>();
		for (int i = 0; i < NpcHandler.npcs.length; i++) {
			Npc npc = NpcHandler.npcs[i];
			if (npc == null) {
				continue;
			}
			MobDefinition definition = MobDefinitionRegistry.get(npc.npcType);
			if (definition == null) {
				continue;
			}
			long token = npc.allocationToken();
			if (token == 0L) {
				continue;
			}
			liveTokens.add(Long.valueOf(token));
			boolean firstSight;
			synchronized (this) {
				trackedSlots.put(Long.valueOf(token), Integer.valueOf(i));
				firstSight = spawnedTokens.add(Long.valueOf(token));
			}
			if (firstSight) {
				fireCallback(definition, definition.onSpawn(), "onSpawn", npc,
						generation, null, null);
			}
			if (!npc.isDead && !npc.applyDead && npc.HP > 0) {
				fireCallback(definition, definition.onTick(), "onTick", npc,
						generation, null, null);
			}
		}
		synchronized (this) {
			Iterator<Long> tokens = trackedSlots.keySet().iterator();
			while (tokens.hasNext()) {
				Long token = tokens.next();
				if (!liveTokens.contains(token)) {
					tokens.remove();
					spawnedTokens.remove(token);
				}
			}
		}
	}

	/**
	 * Invokes {@code onDeath} for a registered mob, then forgets the
	 * allocation so a later respawn can fire {@code onSpawn} again.
	 */
	public void onNpcDeath(Npc npc, Player killer, long generation,
			ScriptedPosition position) {
		if (npc == null) {
			return;
		}
		MobDefinition definition = MobDefinitionRegistry.get(npc.npcType);
		long token = npc.allocationToken();
		synchronized (this) {
			if (token != 0L) {
				spawnedTokens.remove(Long.valueOf(token));
				trackedSlots.remove(Long.valueOf(token));
			}
		}
		if (definition == null || definition.onDeath() == null) {
			return;
		}
		ScriptedPlayer scriptedKiller = killer == null ? null
				: new ScriptedPlayer(killer, generation);
		fireCallback(definition, definition.onDeath(), "onDeath", npc,
				generation, scriptedKiller, position);
	}

	private void fireCallback(MobDefinition definition, Value callback,
			String label, Npc npc, long generation, ScriptedPlayer killer,
			ScriptedPosition deathPosition) {
		if (callback == null) {
			return;
		}
		MobRuntimeContext context = new MobRuntimeContext(definition, npc,
				generation, killer, deathPosition);
		ScriptExecutor.executeChecked(callback, "mob", definition.id(), label,
				context);
	}

	/** Active generation observed by tests. */
	public synchronized long activeGenerationForTesting() {
		return activeGeneration;
	}

	public synchronized boolean hasSpawnedTokenForTesting(long token) {
		return spawnedTokens.contains(Long.valueOf(token));
	}
}
