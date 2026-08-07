package com.rs2.script.mob;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.Value;

import com.rs2.game.content.combat.AttackType;
import com.rs2.game.content.combat.npcs.NpcEmotes;
import com.rs2.game.items.impl.Greegree.MonkeyData;
import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcData;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.players.Client;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.ScriptExecutor;
import com.rs2.script.ScriptHost;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.ScriptedPosition;
import com.rs2.script.world.ScriptNpcService;
import com.rs2.world.Boundary;

import static com.rs2.game.content.StaticNpcList.BARRICADE;
import static com.rs2.game.content.StaticNpcList.BARRICADE_1534;
import static com.rs2.game.content.StaticNpcList.CHICKEN_1401;
import static com.rs2.game.content.StaticNpcList.GUARD;
import static com.rs2.game.content.StaticNpcList.LESSER_DEMON_752;
import static com.rs2.game.content.StaticNpcList.OGRE_374;

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
	private final Set<Long> trackedSlots = new HashSet<Long>();

	/**
	 * Immutable npcId-to-definition lookup published once per generation. The
	 * authoritative registry is read under the global {@link ScriptHost}
	 * monitor only at reload; combat and tick hot paths read this volatile
	 * snapshot lock-free so they never contend on that monitor.
	 */
	private volatile Map<Integer, MobDefinition> definitionSnapshot =
			Collections.emptyMap();

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
		return definitionSnapshot.containsKey(Integer.valueOf(npcId));
	}

	public MobDefinition definitionFor(int npcId) {
		return definitionSnapshot.get(Integer.valueOf(npcId));
	}

	/**
	 * Declarative max hit for a registered mob, or {@code -1} when the id is
	 * not owned by this runtime.
	 */
	public int maxHit(int npcId) {
		MobDefinition definition = definitionFor(npcId);
		return definition == null ? -1 : definition.maxHit();
	}

	/** Lock-free snapshot lookup for an npc type. */
	private MobDefinition definitionForType(int npcType) {
		return definitionSnapshot.get(Integer.valueOf(npcType));
	}

	public synchronized void onGenerationPublished(long generation) {
		// New callbacks replace the previous generation; re-fire onSpawn for
		// any live world NPCs that remain after reload. Reload already holds the
		// ScriptHost monitor, so rebuilding the lock-free snapshot here is one
		// authoritative read per publish, not a per-lookup hot-path lock.
		definitionSnapshot = ScriptHost.getInstance().readActiveRegistry(
				state -> MobDefinitionRegistry.all(state));
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
		MobDefinition definition = definitionForType(npc.npcType);
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
			// A dead player or one in the respawn window is not a valid target;
			// the legacy gate also refuses players on the respawn timer.
			if (player.isDead || player.respawnTimer > 0) {
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
	 * <p>The attack type is set before the distance gate so ranged/magic mobs
	 * hold their projectile range instead of closing to melee distance. For
	 * non-melee styles a default projectile and impact graphic are armed so the
	 * player sees the attack; the legacy projectile broadcast is created here
	 * because {@code NpcCombat} skips its own once this method consumes the
	 * attack.
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
		MobDefinition definition = definitionForType(npc.npcType);
		if (definition == null) {
			return false;
		}
		if (!ScriptNpcService.getInstance().canAct(npc, player)) {
			return true;
		}
		if (npc.isDead || player.respawnTimer > 0) {
			return true;
		}
		// Legacy give-up guards from NpcCombat.attackPlayer: a scripted mob must
		// not attack through a case the legacy switch would have refused, or it
		// would pierce single-combat, plane, and static-npc protections.
		if (!player.npcCanAttack) {
			return true;
		}
		if (npc.heightLevel != player.heightLevel) {
			npc.killerId = 0;
			return true;
		}
		if (!npc.inMulti() && npc.underAttackBy > 0
				&& npc.underAttackBy != player.playerId) {
			npc.killerId = 0;
			return true;
		}
		if (!npc.inMulti() && (player.underAttackBy > 0
				|| player.underAttackBy2 > 0
						&& player.underAttackBy2 != npcIndex)) {
			npc.killerId = 0;
			return true;
		}
		if (isStaticNpcRefusal(npc, player)) {
			return true;
		}
		// Resolve the attack type before the distance gate: ranged/magic mobs
		// must keep their projectile range rather than read the previous melee
		// default that the gate evaluates against.
		AttackType attackType = definition.combatStyle().attackType();
		npc.attackType = attackType.getValue();
		if (!NpcData.goodDistanceNpc(npcIndex, player.getX(), player.getY(),
				NpcData.distanceRequired(npcIndex))
				|| NpcData.inNpc(npcIndex, player.getX(), player.getY())) {
			return true;
		}
		if (!NpcData.checkClip(npc)) {
			return true;
		}
		npc.facePlayer(player);
		npc.attackTimer = definition.attackSpeed();
		npc.hitDelayTimer = attackType == AttackType.RANGE
				|| attackType == AttackType.MAGIC ? 3 : 2;
		// Default projectile/impact for non-melee styles; melee keeps -1 so no
		// stray graphic is sent. The declarative maxHit path in registerNpcHit
		// applies the actual damage.
		boolean projectile = attackType == AttackType.RANGE
				|| attackType == AttackType.MAGIC;
		npc.projectileId = projectile ? 90 : -1;
		npc.endGfx = projectile ? 91 : -1;
		int animation = definition.animation() >= 0
				? definition.animation()
				: NpcEmotes.getAttackEmote(npcIndex);
		NpcData.startAnimation(animation, npcIndex);
		player.underAttackBy2 = npcIndex;
		player.singleCombatDelay2 = System.currentTimeMillis();
		npc.oldIndex = player.playerId;
		npc.oldAllocationToken = npc.allocationToken();
		if (projectile) {
			int nX = npc.getX() + NpcHandler.offset(npcIndex);
			int nY = npc.getY() + NpcHandler.offset(npcIndex);
			int pX = player.getX();
			int pY = player.getY();
			int offX = (nY - pY) * -1;
			int offY = (nX - pX) * -1;
			player.getPlayerAssistant().createPlayersProjectile(nX, nY,
					offX, offY, 50, NpcHandler.getProjectileSpeed(npcIndex),
					npc.projectileId, 43, 31, -player.getId() - 1, 65);
		}
		if (player instanceof Client) {
			((Client) player).getPacketSender().closeAllWindows();
		}
		return true;
	}

	/**
	 * Mirrors the static-npc and region refusals in
	 * {@code NpcCombat.attackPlayer}: barricades, position-locked guards, and
	 * protected zones never attack even when a script registers a mob over
	 * their type.
	 */
	private boolean isStaticNpcRefusal(Npc npc, Player player) {
		if (npc.absY == 3228 && player.absY == 3227
				|| npc.absY == 3224 && player.absY == 3225
				|| npc.absY == 3226 && player.absY == 3227
				|| Boundary.isIn(player, Boundary.DRAYNOR_BUILDING)
						&& (npc.npcType == 172 || npc.npcType == 174)
				|| npc.inLesserNpc()) {
			return true;
		}
		if (npc.npcType == BARRICADE || npc.npcType == BARRICADE_1534
				|| npc.npcType == 6145 || npc.npcType == 6144
				|| npc.npcType == 6143 || npc.npcType == 6142
				|| npc.npcType == LESSER_DEMON_752) {
			return true;
		}
		if (Boundary.isIn(player, Boundary.APE_ATOLL)
				&& MonkeyData.isWearingGreegree(player)) {
			return true;
		}
		if (npc.npcType == CHICKEN_1401
				&& (Boundary.isIn(player, Boundary.TUTORIAL)
						|| player.tutorialProgress < 36)) {
			return true;
		}
		if (npc.npcType == GUARD && player.absX == 3180
				&& player.absY > 3433 && player.absY < 3447) {
			return true;
		}
		if (npc.npcType == OGRE_374 && player.absY == 3372
				&& player.absX > 2522 && player.absX < 2532) {
			return true;
		}
		return false;
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
			MobDefinition definition = definitionForType(npc.npcType);
			if (definition == null) {
				continue;
			}
			long token = npc.allocationToken();
			if (token == 0L) {
				continue;
			}
			liveTokens.add(Long.valueOf(token));
			// A corpse keeps its allocation through the respawn window: the death
			// path removed the token from spawnedTokens so the next tick would see
			// it as first sight and re-fire onSpawn. Only treat a live NPC as
			// first sight so onSpawn fires exactly once per life (the real respawn
			// allocates a fresh token and fires it again).
			boolean alive = !npc.isDead && !npc.applyDead && npc.HP > 0;
			boolean firstSight;
			synchronized (this) {
				trackedSlots.add(Long.valueOf(token));
				firstSight = alive && spawnedTokens.add(Long.valueOf(token));
			}
			if (firstSight) {
				fireCallback(definition, definition.onSpawn(), "onSpawn", npc,
						generation, null, null);
			}
			if (alive) {
				fireCallback(definition, definition.onTick(), "onTick", npc,
						generation, null, null);
			}
		}
		synchronized (this) {
			Iterator<Long> tokens = trackedSlots.iterator();
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
		MobDefinition definition = definitionForType(npc.npcType);
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
