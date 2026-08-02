package com.rs2.script.area;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.rs2.GameEngine;
import com.rs2.game.npcs.Npc;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.CommandScriptContext;
import com.rs2.script.ScriptContext;
import com.rs2.script.ScriptedNpc;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.ScriptedPosition;
import com.rs2.script.activation.ProjectionAdapter;
import com.rs2.script.activation.RuntimeSnapshot;
import com.rs2.script.drop.DropTableDefinition;
import com.rs2.script.drop.DropTableRegistry;
import com.rs2.script.drop.DropTransaction;
import com.rs2.script.registries.LifecycleRegistry;
import com.rs2.script.registries.ScriptArea;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.HostRoute;
import com.rs2.script.route.RouteRegistry;
import com.rs2.script.shop.ScriptShopRuntime;
import com.rs2.script.world.ScriptDropResult;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.script.world.ScriptGroundItemHandle;
import com.rs2.script.world.ScriptNpcHandle;
import com.rs2.script.world.ScriptNpcService;
import com.rs2.script.world.ScriptObjectHandle;
import com.rs2.util.LoggerUtils;
import com.rs2.world.ItemHandler;
import com.rs2.world.ResolvedWorldObject;
import com.rs2.world.WorldObjectService;

/**
 * Generation-owned declarative area runtime.
 *
 * <p>Every {@code defineArea} is parsed into an immutable Java-owned
 * descriptor and activated through the WP1 two-phase runtime activation
 * transaction: {@link ProjectionAdapter} stages shadow sessions with real
 * NPC allocations and object projections, retires the predecessor into an
 * idempotent undo ledger, and flips one no-throw selector at commit. The
 * runtime owns the exact spawn-allocation death authority (the NpcHandler
 * critical section claims one exact allocation and rolls the named WP2
 * table exactly once through the session RNG), the exact tile-position
 * object-drop routes, the scripted-shop opening routes of exact NPC
 * allocations, the deterministic area-session RNG, respawns, lifecycle
 * area observers, and reload/logout cleanup. Legacy content with equal ids
 * stays unmatched and retains its complete legacy path.
 *
 * <p>Same-area-id handoffs keep the old world visible until the atomic
 * selector swap: footprints owned by the retiring generation are deferred
 * to {@code commitSelection} instead of being staged live in
 * {@code applyShadow}. A failed deferred install after retirement is a
 * bounded degradation, not a throw: the exact key is left to the next
 * session in the current generation (an unspawned NPC re-enters the
 * respawn machinery; an uninstalled object stays under the previous
 * layer) and is recorded in the bounded diagnostic ledger. Escalating to
 * the host quarantine is intentionally not reachable from commit, because
 * the no-throw commit line must not fail.
 */
public final class ScriptAreaRuntime implements ProjectionAdapter {

	/** Bounded ground identities per area session (mirrors encounters). */
	public static final int MAX_GROUND_IDENTITIES_PER_AREA = 128;

	private static final int MAX_OBJECT_MUTATIONS_PER_AREA = 64;
	private static final int MAX_SESSIONS_PER_GENERATION = 64;
	private static final int MAX_DIAGNOSTICS = 16;
	private static final int RESPAWN_RETRY_TICKS = 5;

	private static final Logger logger = LoggerUtils.getLogger(
			ScriptAreaRuntime.class);

	private static final ScriptAreaRuntime INSTANCE = new ScriptAreaRuntime();

	private final Map<Long, Session> sessionsByToken =
			new HashMap<Long, Session>();
	private final Map<String, Long> selectedByAreaId =
			new LinkedHashMap<String, Long>();
	private final Map<String, Reservation> reservations =
			new LinkedHashMap<String, Reservation>();
	private final List<String> diagnostics = new ArrayList<String>();

	private long nextToken = 1L;
	private long nextOwnerToken = 1L;
	private long nextOrdinal = 1L;
	private long activeGeneration;
	private long currentTick;

	// One-execution adapter state; reload runs on the single game thread
	// under the ScriptHost monitor, so no two executions interleave.
	private List<Session> shadows = new ArrayList<Session>();
	private List<UndoEntry> undoLedger = new ArrayList<UndoEntry>();
	private long predecessorGeneration;

	// Test failure injection points; each fires once.
	private boolean failPrepare;
	private boolean failReserve;
	private boolean failApplyShadow;
	private boolean failVerifyShadow;
	private boolean failRetire;
	private boolean failVerifyRetirement;
	private boolean failCheckpoint;
	private boolean failDetach;
	private boolean failDropTableLookup;
	private Runnable midHandoffHookForTesting;

	public static ScriptAreaRuntime getInstance() {
		return INSTANCE;
	}

	private ScriptAreaRuntime() {
	}

	// ─── Candidate-load registration ───────────────────────────────────────

	/**
	 * Registers the definition's candidate-scoped routes and lifecycle
	 * observers: exact tile-position object-drop host routes and exact
	 * allocation-bound shop-opening NPC routes.
	 */
	public void registerArea(AreaDefinition area) {
		if (area.onEnter() != null) {
			LifecycleRegistry.putArea("enter", toScriptArea(area),
					area.onEnter());
		}
		if (area.onLeave() != null) {
			LifecycleRegistry.putArea("leave", toScriptArea(area),
					area.onLeave());
		}
		for (AreaObjectProjection object : area.objects()) {
			for (AreaObjectDrop drop : object.drops()) {
				RouteRegistry.putHost(ExecutableRouteKey.objectAt(
						object.objectId(), drop.action(), object.x(),
						object.y(), object.plane()),
						objectDropRoute(area.id(), object, drop));
			}
		}
		for (AreaNpcSpawn spawn : area.npcs()) {
			if (spawn.openShop() != null) {
				RouteRegistry.putHost(ExecutableRouteKey.npcAllocated(
						spawn.npcId(), "first", area.id(), spawn.key()),
						npcShopRoute(area.id(), spawn.key(),
								spawn.openShop()));
			}
		}
	}

	private static ScriptArea toScriptArea(AreaDefinition area) {
		return new ScriptArea(area.id(), area.bounds().minX(),
				area.bounds().minY(), area.bounds().maxX(),
				area.bounds().maxY(), Integer.valueOf(area.bounds().plane()));
	}

	// ─── ProjectionAdapter: two-phase activation ────────────────────────────

	@Override
	public synchronized void prepare(RuntimeSnapshot candidate) {
		if (failPrepare) {
			failPrepare = false;
			throw new IllegalStateException(
					"injected area prepare failure");
		}
		Map<String, AreaDefinition> areas = AreaDefinitionRegistry
				.all(candidate.registry());
		if (areas.size() > MAX_SESSIONS_PER_GENERATION) {
			throw new IllegalStateException("candidate registers "
					+ areas.size() + " areas; at most "
					+ MAX_SESSIONS_PER_GENERATION + " may activate");
		}
		Map<String, String> tiles = new HashMap<String, String>();
		for (AreaDefinition area : areas.values()) {
			for (AreaObjectProjection object : area.objects()) {
				String tile = tile(object);
				String previous = tiles.put(tile, area.id());
				if (previous != null && !previous.equals(area.id())) {
					throw new IllegalStateException(
							"conflicting area object footprints: tile "
									+ tile + " is claimed by area '"
									+ previous + "' and area '" + area.id()
									+ "'");
				}
				long owner = WorldObjectService.getInstance()
						.areaOwnerAt(object.x(), object.y(), object.plane());
				if (owner != 0L) {
					Session live = sessionsByToken.get(Long.valueOf(owner));
					if (live != null && live.selected && !live.closed
							&& !live.definition.id().equals(area.id())) {
						throw new IllegalStateException(
								"conflicting area object footprints: tile "
										+ tile + " is already active in area '"
										+ live.definition.id() + "'");
					}
				}
			}
		}
	}

	@Override
	public synchronized void reserve(RuntimeSnapshot predecessor,
			RuntimeSnapshot candidate) {
		if (failReserve) {
			failReserve = false;
			throw new IllegalStateException(
					"injected area reservation failure");
		}
		shadows = new ArrayList<Session>();
		undoLedger = new ArrayList<UndoEntry>();
		predecessorGeneration = predecessor == null ? 0L
				: predecessor.generation();
		Map<String, AreaDefinition> areas = AreaDefinitionRegistry
				.all(candidate.registry());
		for (AreaDefinition area : areas.values()) {
			Session session = newSession(area, candidate.generation());
			for (AreaObjectProjection object : area.objects()) {
				// The reservation records the exact owner at reservation
				// time: the retiring predecessor on a same-area-id handoff,
				// or none on a fresh activation. Only these two owners may
				// write the tile while the reservation is held.
				long oldOwner = WorldObjectService.getInstance().areaOwnerAt(
						object.x(), object.y(), object.plane());
				reservations.put(tile(object),
						new Reservation(tile(object), oldOwner,
								session.token));
			}
			shadows.add(session);
		}
	}

	@Override
	public synchronized void applyShadow(RuntimeSnapshot candidate) {
		if (failApplyShadow) {
			failApplyShadow = false;
			throw new IllegalStateException(
					"injected area shadow-apply failure");
		}
		for (Session session : shadows) {
			sessionsByToken.put(Long.valueOf(session.token), session);
			boolean sameAreaHandoff = predecessorSessionOf(
					session.definition.id(), session) != null;
			for (AreaNpcSpawn spawnDef : session.definition.npcs()) {
				if (sameAreaHandoff) {
					// Same-area-id handoff: the predecessor allocation is
					// still live; the new allocation must not be visible or
					// interactable before the selector swap. Spawn after
					// retirement at commit, mirroring pendingObjects.
					session.pendingSpawns.add(spawnDef);
					continue;
				}
				ScriptNpcHandle handle = spawnNpc(session, spawnDef);
				if (handle == null) {
					throw new IllegalStateException(
							"area '" + session.definition.id()
									+ "' shadow spawn failed for key '"
									+ spawnDef.key() + "'");
				}
				SpawnState spawn = new SpawnState(spawnDef, handle);
				session.spawns.put(spawnDef.key(), spawn);
				if (spawnDef.hasDropTable()) {
					ScriptNpcService.getInstance().registerAreaDeath(handle,
							deathListener(session, spawnDef.key()));
				}
			}
			for (AreaObjectProjection objectDef : session.definition
					.objects()) {
				long owner = WorldObjectService.getInstance().areaOwnerAt(
						objectDef.x(), objectDef.y(), objectDef.plane());
				if (owner != 0L) {
					Session predecessor = sessionsByToken
							.get(Long.valueOf(owner));
					if (predecessor != null
							&& predecessor.definition.id()
									.equals(session.definition.id())) {
						// Same-area-id handoff: the predecessor still owns
						// the tile; install after retirement at commit.
						session.pendingObjects.add(objectDef);
						continue;
					}
				}
				ScriptObjectHandle handle = installObject(session, objectDef);
				if (handle == null) {
					throw new IllegalStateException(
							"area '" + session.definition.id()
									+ "' shadow object projection failed "
									+ "for key '" + objectDef.key() + "'");
				}
				session.objects.put(objectDef.key(), handle);
			}
		}
		fireMidHandoffHook();
	}

	@Override
	public synchronized void verifyShadow(RuntimeSnapshot candidate) {
		if (failVerifyShadow) {
			failVerifyShadow = false;
			throw new IllegalStateException(
					"injected area shadow-verify failure");
		}
		for (Session session : shadows) {
			for (SpawnState spawn : session.spawns.values()) {
				if (spawn.allocation == null || !ScriptNpcService
						.getInstance()
						.hasLiveAllocation(spawn.allocation)) {
					throw new IllegalStateException(
							"area '" + session.definition.id()
									+ "' shadow NPC is not alive for key '"
									+ spawn.definition.key() + "'");
				}
			}
			for (ScriptObjectHandle handle : session.objects.values()) {
				if (handle == null || !handle.isActive()) {
					throw new IllegalStateException(
							"area '" + session.definition.id()
									+ "' shadow object is not active");
				}
			}
		}
	}

	@Override
	public synchronized void retirePredecessor(RuntimeSnapshot predecessor) {
		if (failRetire) {
			failRetire = false;
			throw new IllegalStateException(
					"injected area retirement failure");
		}
		if (predecessor == null) {
			return;
		}
		for (Session session : sessionsByToken.values()) {
			if (session.generation != predecessor.generation()
					|| session.closed) {
				continue;
			}
			for (SpawnState spawn : session.spawns.values()) {
				if (spawn.allocation == null) {
					continue;
				}
				ScriptNpcService.AreaNpcLease lease = ScriptNpcService
						.getInstance().retireAreaNpc(session.token,
								spawn.definition.key());
				if (lease == null) {
					throw new IllegalStateException(
							"area '" + session.definition.id()
									+ "' retirement failed for key '"
									+ spawn.definition.key() + "'");
				}
				undoLedger.add(UndoEntry.npc(lease, session, spawn));
				spawn.allocation = null;
			}
			List<WorldObjectService.AreaObjectRestore> restores =
					WorldObjectService.getInstance()
							.retireAreaObjects(session.token);
			undoLedger.add(UndoEntry.objects(restores, session));
			session.retired = true;
		}
		fireMidHandoffHook();
	}

	@Override
	public synchronized void verifyRetirement() {
		if (failVerifyRetirement) {
			failVerifyRetirement = false;
			throw new IllegalStateException(
					"injected area retirement-verify failure");
		}
		for (Session session : sessionsByToken.values()) {
			if (session.generation != predecessorGeneration
					|| !session.retired) {
				continue;
			}
			if (WorldObjectService.getInstance()
					.areaObjectCount(session.token) != 0) {
				throw new IllegalStateException(
						"area '" + session.definition.id()
								+ "' retirement left object projections");
			}
			for (SpawnState spawn : session.spawns.values()) {
				if (spawn.allocation != null) {
					throw new IllegalStateException(
							"area '" + session.definition.id()
									+ "' retirement left live NPC for key '"
									+ spawn.definition.key() + "'");
				}
			}
		}
	}

	@Override
	public synchronized void checkpoint() {
		if (failCheckpoint) {
			failCheckpoint = false;
			throw new IllegalStateException(
					"injected area pre-publication checkpoint failure");
		}
	}

	@Override
	public synchronized void commitSelection(RuntimeSnapshot candidate) {
		for (Session session : shadows) {
			for (AreaObjectProjection pending : session.pendingObjects) {
				ScriptObjectHandle handle = installObject(session, pending);
				if (handle == null) {
					appendDiagnostic("area '" + session.definition.id()
							+ "' same-footprint object install degraded for "
							+ "key '" + pending.key()
							+ "' after retirement");
					continue;
				}
				session.objects.put(pending.key(), handle);
			}
			for (AreaNpcSpawn pending : session.pendingSpawns) {
				// The predecessor allocation was retired; the new one must
				// appear together with the selector swap. A failed spawn is
				// a bounded degradation: the unclaimed spawn re-enters the
				// respawn machinery of the now-selected session.
				ScriptNpcHandle handle = spawnNpc(session, pending);
				if (handle == null) {
					appendDiagnostic("area '" + session.definition.id()
							+ "' same-footprint NPC install degraded for "
							+ "key '" + pending.key()
							+ "' after retirement");
					continue;
				}
				SpawnState spawn = new SpawnState(pending, handle);
				session.spawns.put(pending.key(), spawn);
				if (pending.hasDropTable()) {
					ScriptNpcService.getInstance().registerAreaDeath(handle,
							deathListener(session, pending.key()));
				}
			}
			session.selected = true;
			selectedByAreaId.put(session.definition.id(),
					Long.valueOf(session.token));
		}
		for (Session session : sessionsByToken.values()) {
			if (session.generation == predecessorGeneration
					&& session.retired) {
				session.closed = true;
			}
		}
	}

	@Override
	public synchronized boolean restorePredecessor() {
		if (undoLedger.isEmpty()) {
			return true;
		}
		boolean complete = true;
		for (UndoEntry entry : undoLedger) {
			if (entry.restored) {
				continue;
			}
			boolean ok;
			if (entry.lease != null) {
				ok = ScriptNpcService.getInstance()
						.restoreAreaNpc(entry.lease);
				if (ok && entry.session != null && entry.spawn != null) {
					entry.spawn.allocation = new ScriptNpcHandle(
							ScriptNpcService.getInstance(),
							entry.lease.allocationToken());
				}
			} else {
				ok = WorldObjectService.getInstance()
						.restoreAreaObjects(entry.restores);
			}
			if (ok) {
				entry.restored = true;
			} else {
				complete = false;
			}
		}
		if (complete) {
			for (UndoEntry entry : undoLedger) {
				if (entry.session != null) {
					entry.session.retired = false;
				}
			}
		}
		return complete;
	}

	@Override
	public synchronized void removeShadow() {
		for (Session session : shadows) {
			ScriptNpcService.getInstance().closeArea(session.token);
			WorldObjectService.getInstance().closeArea(session.token);
			sessionsByToken.remove(Long.valueOf(session.token));
		}
		shadows.clear();
	}

	@Override
	public synchronized void releaseReservations() {
		reservations.clear();
	}

	@Override
	public synchronized void dispose(RuntimeSnapshot predecessor) {
		if (predecessor != null) {
			for (Session session : new ArrayList<Session>(
					sessionsByToken.values())) {
				if (session.generation == predecessor.generation()
						&& session.retired) {
					sessionsByToken.remove(Long.valueOf(session.token));
				}
			}
		}
		reservations.clear();
		undoLedger = new ArrayList<UndoEntry>();
		shadows = new ArrayList<Session>();
	}

	// ─── Runtime lifecycle ─────────────────────────────────────────────────

	/** Marks the published generation active so claims may proceed. */
	public synchronized void onGenerationPublished(long generation) {
		activeGeneration = generation;
	}

	/**
	 * Closes every session of a retired generation: despawns the exact
	 * allocations, restores the object projections, removes only its
	 * unclaimed exact ground identities, closes the session RNG, and drops
	 * the sessions before the old context closes.
	 */
	public synchronized void closeGeneration(long generation) {
		if (generation == 0L) {
			return;
		}
		for (Session session : new ArrayList<Session>(
				sessionsByToken.values())) {
			if (session.generation != generation) {
				continue;
			}
			ScriptNpcService.getInstance().closeArea(session.token);
			WorldObjectService.getInstance().closeArea(session.token);
			GameEngine.itemHandler.closeAreaRewards(session.token);
			session.closed = true;
			session.selected = false;
			sessionsByToken.remove(Long.valueOf(session.token));
			Long selected = selectedByAreaId.get(session.definition.id());
			if (selected != null && selected.longValue() == session.token) {
				selectedByAreaId.remove(session.definition.id());
			}
		}
	}

	/** Game-cycle respawn processing of every selected session. */
	public synchronized void processGameTick() {
		currentTick++;
		for (Session session : new ArrayList<Session>(
				sessionsByToken.values())) {
			if (session.closed || !session.selected) {
				continue;
			}
			for (SpawnState spawn : session.spawns.values()) {
				if (spawn.allocation != null
						|| currentTick < spawn.nextRespawnTick) {
					continue;
				}
				ScriptNpcHandle handle = spawnNpc(session,
						spawn.definition);
				if (handle == null) {
					spawn.nextRespawnTick = currentTick
							+ RESPAWN_RETRY_TICKS;
					continue;
				}
				spawn.allocation = handle;
				if (spawn.definition.hasDropTable()) {
					ScriptNpcService.getInstance().registerAreaDeath(
							handle, deathListener(session,
									spawn.definition.key()));
				}
			}
		}
	}

	/** Test-only lifecycle reset. */
	public synchronized void resetForTesting() {
		for (Session session : new ArrayList<Session>(
				sessionsByToken.values())) {
			ScriptNpcService.getInstance().closeArea(session.token);
			WorldObjectService.getInstance().closeArea(session.token);
			GameEngine.itemHandler.closeAreaRewards(session.token);
		}
		sessionsByToken.clear();
		selectedByAreaId.clear();
		reservations.clear();
		shadows.clear();
		undoLedger = new ArrayList<UndoEntry>();
		diagnostics.clear();
		activeGeneration = 0L;
		currentTick = 0L;
		failPrepare = failReserve = failApplyShadow = failVerifyShadow = false;
		failRetire = failVerifyRetirement = failCheckpoint = false;
		failDetach = false;
		failDropTableLookup = false;
		midHandoffHookForTesting = null;
	}

	/** Engine-visible count for tests; never exported to guest code. */
	public synchronized int sessionCount() {
		return sessionsByToken.size();
	}

	/** Exact session RNG state for deterministic death/drop tests. */
	public synchronized long areaRngStateForTesting(long areaToken) {
		Session session = sessionsByToken.get(Long.valueOf(areaToken));
		return session == null ? 0L : session.rng.state();
	}

	/** Engine-visible selected-area count for tests. */
	public synchronized int selectedAreaCount() {
		return selectedByAreaId.size();
	}

	/** Bounded sanitized diagnostics of degraded handoff steps. */
	public synchronized List<String> getDiagnostics() {
		return new ArrayList<String>(diagnostics);
	}

	/** Immutable bounded diagnostics of one session for tests. */
	public synchronized long sessionToken(String areaId) {
		Long token = selectedByAreaId.get(areaId);
		return token == null ? 0L : token.longValue();
	}

	// ─── Test failure injection ────────────────────────────────────────────

	/** Test-only: makes the next prepare stage throw. */
	public void failNextPrepare() {
		failPrepare = true;
	}

	/** Test-only: makes the next reservation stage throw. */
	public void failNextReserve() {
		failReserve = true;
	}

	/** Test-only: makes the next shadow-apply stage throw. */
	public void failNextApplyShadow() {
		failApplyShadow = true;
	}

	/** Test-only: makes the next shadow-verify stage throw. */
	public void failNextVerifyShadow() {
		failVerifyShadow = true;
	}

	/** Test-only: makes the next retirement stage throw. */
	public void failNextRetire() {
		failRetire = true;
	}

	/** Test-only: makes the next retirement-verify stage throw. */
	public void failNextVerifyRetirement() {
		failVerifyRetirement = true;
	}

	/** Test-only: makes the next pre-publication checkpoint throw. */
	public void failNextCheckpoint() {
		failCheckpoint = true;
	}

	/** Test-only: makes the next claim's final ground detach fail. */
	public void failNextDetachForTesting() {
		failDetach = true;
	}

	/** Test-only: consumes the next claim as if its drop table were gone. */
	public void failNextDropTableLookupForTesting() {
		failDropTableLookup = true;
	}

	/** Test-only: observes the world between activation stages. */
	public void setMidHandoffHookForTesting(Runnable hook) {
		midHandoffHookForTesting = hook;
	}

	/** Test-only: consumes the one-shot detach failure injection. */
	boolean consumeFailDetachForTesting() {
		if (failDetach) {
			failDetach = false;
			return true;
		}
		return false;
	}

	// ─── Object authority consumed by WorldObjectService ───────────────────

	/** Write authorization of one area session: open and inside its bounds. */
	public synchronized boolean canAreaObjectWrite(long areaToken, int x,
			int y, int plane) {
		Session session = sessionsByToken.get(Long.valueOf(areaToken));
		return session != null && !session.closed && !session.retired
				&& session.definition.bounds().contains(x, y, plane);
	}

	public synchronized boolean canAreaObjectFootprint(long areaToken, int x,
			int y, int plane, int objectId, int rotation, int objectType) {
		return canAreaObjectWrite(areaToken, x, y, plane);
	}

	public synchronized boolean reserveAreaObjectMutation(long areaToken) {
		Session session = sessionsByToken.get(Long.valueOf(areaToken));
		if (session == null || session.closed || session.retired
				|| session.objectMutationCount
						>= MAX_OBJECT_MUTATIONS_PER_AREA) {
			return false;
		}
		session.objectMutationCount++;
		return true;
	}

	public synchronized void releaseAreaObjectMutation(long areaToken) {
		Session session = sessionsByToken.get(Long.valueOf(areaToken));
		if (session != null && session.objectMutationCount > 0) {
			session.objectMutationCount--;
		}
	}

	/**
	 * Same-area-id shadow replacement: the old owner is the predecessor of
	 * the new session in the current handoff and has been retired.
	 */
	public synchronized boolean canShadowReplace(long oldToken, long newToken,
			int x, int y, int plane) {
		Session oldSession = sessionsByToken.get(Long.valueOf(oldToken));
		Session newSession = sessionsByToken.get(Long.valueOf(newToken));
		return oldSession != null && newSession != null
				&& oldSession.retired && !oldSession.closed
				&& oldSession.definition.id()
						.equals(newSession.definition.id())
				&& !newSession.closed;
	}

	// ─── Ground identity authority ─────────────────────────────────────────

	public synchronized boolean isSessionActive(long areaToken) {
		Session session = sessionsByToken.get(Long.valueOf(areaToken));
		return session != null && session.selected && !session.closed
				&& session.generation == activeGeneration;
	}

	synchronized int groundIdentityCount(long areaToken) {
		Session session = sessionsByToken.get(Long.valueOf(areaToken));
		return session == null ? 0 : session.groundIdentityCount;
	}

	synchronized void publishGroundIdentities(long areaToken,
			List<ScriptGroundItemHandle> staged) {
		Session session = sessionsByToken.get(Long.valueOf(areaToken));
		if (session == null) {
			return;
		}
		for (ScriptGroundItemHandle handle : staged) {
			session.groundIdentityCount += handle.identityCount();
		}
	}

	// ─── Death claim authority ─────────────────────────────────────────────

	private ScriptNpcService.AreaDeathListener deathListener(
			final Session session, final String spawnKey) {
		return new ScriptNpcService.AreaDeathListener() {
			@Override
			public void onDeath(ScriptNpcHandle npc, ScriptedPlayer killer,
					ScriptedPosition position) {
				onAllocationDeath(session, spawnKey, npc, killer, position);
			}
		};
	}

	private void onAllocationDeath(Session session, String spawnKey,
			ScriptNpcHandle npc, ScriptedPlayer killer,
			ScriptedPosition position) {
		synchronized (this) {
			SpawnState spawn = session.spawns.get(spawnKey);
			if (spawn == null || spawn.allocation == null
					|| spawn.allocation.tokenValue() != npc.tokenValue()) {
				return;
			}
			if (session.closed || session.retired || !session.selected
					|| session.generation != activeGeneration) {
				// The exact allocation died outside the claim window (a
				// staged shadow or a not-yet-published generation). Release
				// the binding so finishDeath's despawn cannot desynchronize
				// the runtime: the respawn machinery restores the spawn as
				// soon as the session is selected and active again.
				spawn.allocation = null;
				spawn.nextRespawnTick = currentTick
						+ spawn.definition.respawnTicks();
				return;
			}
			// Compare-and-remove: the allocation is claimed exactly once.
			spawn.allocation = null;
			spawn.nextRespawnTick = currentTick
					+ spawn.definition.respawnTicks();
			if (!spawn.definition.hasDropTable()) {
				return;
			}
			DropTableDefinition table = dropTable(
					spawn.definition.dropTable());
			if (table == null) {
				appendDiagnostic("area '" + session.definition.id()
						+ "' named drop table '"
						+ spawn.definition.dropTable()
						+ "' is not active; claim consumed without reward");
				return;
			}
			if (killer == null) {
				// NO_RECIPIENT: handled, no RNG/ground mutation, no legacy.
				return;
			}
			AreaDeliveryPolicy delivery = new AreaDeliveryPolicy(this,
					session.token, killer, killer.backingPlayer(),
					position.x, position.y, position.plane,
					spawn.definition.dropPolicy(),
					spawn.definition.privateTicks());
			// The WP2 transaction runs under the area runtime monitor (RNG
			// owner) and publishes exact identities and RNG state together;
			// any failure removes every staged identity and leaves RNG
			// state unchanged.
			DropTransaction.execute(session.rng, delivery,
					table.entries(), killer);
		}
	}

	// ─── Object drop routes ────────────────────────────────────────────────

	private HostRoute objectDropRoute(final String areaId,
			final AreaObjectProjection object, final AreaObjectDrop drop) {
		return new HostRoute() {
			@Override
			public void invoke(Object... arguments) {
				Player player = playerOf(arguments);
				if (player == null) {
					return;
				}
				claimObjectDrop(areaId, object, drop, player);
			}
		};
	}

	private synchronized void claimObjectDrop(String areaId,
			AreaObjectProjection object, AreaObjectDrop drop, Player player) {
		Long tokenValue = selectedByAreaId.get(areaId);
		if (tokenValue == null) {
			return;
		}
		Session session = sessionsByToken.get(tokenValue);
		if (session == null || session.closed || session.retired
				|| session.generation != activeGeneration) {
			return;
		}
		ScriptObjectHandle handle = session.objects.get(object.key());
		if (handle == null || !handle.isActive()) {
			return;
		}
		// Exact resolver identity: the tile must still carry this area's
		// projection with the declared shape.
		com.rs2.game.objects.Objects projected = WorldObjectService
				.getInstance().resolveAreaProjection(
						tokenValue.longValue(), object.x(), object.y(),
						object.plane());
		if (projected == null || projected.getObjectId() != object
				.objectId()) {
			return;
		}
		// One-shot claim: the binding is consumed by the first roll.
		session.objects.remove(object.key());
		DropTableDefinition table = dropTable(drop.dropTable());
		if (table == null) {
			appendDiagnostic("area '" + areaId + "' named drop table '"
					+ drop.dropTable()
					+ "' is not active; claim consumed without reward");
			return;
		}
		ScriptedPlayer recipient = new ScriptedPlayer(player,
				session.generation);
		AreaDeliveryPolicy delivery = new AreaDeliveryPolicy(this,
				tokenValue.longValue(), recipient, player, object.x(),
				object.y(), object.plane(), drop.dropPolicy(),
				drop.privateTicks());
		DropTransaction.execute(session.rng, delivery, table.entries(),
				recipient);
	}

	// ─── Shop-opening NPC routes ───────────────────────────────────────────

	private HostRoute npcShopRoute(final String areaId, final String spawnKey,
			final String shopId) {
		return new HostRoute() {
			@Override
			public void invoke(Object... arguments) {
				Player player = playerOf(arguments);
				if (player == null) {
					return;
				}
				ScriptedNpc target = npcOf(arguments);
				if (target == null || target.backingNpc() == null) {
					return;
				}
				String[] binding = ScriptNpcService.getInstance()
						.areaSpawnOf(target.backingNpc());
				if (binding == null || !areaId.equals(binding[0])
						|| !spawnKey.equals(binding[1])) {
					return;
				}
				// The exact allocation must belong to the selected and active
				// session: a staged shadow or a not-yet-published generation
				// must never open the shop before the selector swap.
				if (!isAllocationShopActive(areaId, spawnKey,
						target.backingNpc().allocationToken())) {
					return;
				}
				ScriptShopRuntime.getInstance().open(player, shopId);
			}
		};
	}

	/** The exact allocation is shop-eligible only in the active session. */
	public synchronized boolean isAllocationShopActive(String areaId,
			String spawnKey, long npcAllocationToken) {
		Long tokenValue = selectedByAreaId.get(areaId);
		if (tokenValue == null) {
			return false;
		}
		Session session = sessionsByToken.get(tokenValue);
		if (session == null || session.closed || !session.selected
				|| session.generation != activeGeneration) {
			return false;
		}
		SpawnState spawn = session.spawns.get(spawnKey);
		return spawn != null && spawn.allocation != null
				&& spawn.allocation.tokenValue() == npcAllocationToken;
	}

	// ─── Spawning helpers ──────────────────────────────────────────────────

	private ScriptNpcHandle spawnNpc(Session session, AreaNpcSpawn def) {
		int walkingType = 0;
		boolean randomWalk = false;
		if (def.walkRadius() > 0) {
			walkingType = 1;
			randomWalk = true;
		} else if (def.direction() != null) {
			walkingType = directionWalkingType(def.direction());
		}
		return ScriptNpcService.getInstance().spawnArea(session.token,
				session.generation, def.npcId(), def.x(), def.y(), def.plane(),
				def.hp(), def.maxHit(), def.attack(), def.defence(),
				session.definition.id(), def.key(), walkingType, randomWalk);
	}

	private static int directionWalkingType(String direction) {
		if ("north".equals(direction)) {
			return 2;
		}
		if ("south".equals(direction)) {
			return 3;
		}
		if ("east".equals(direction)) {
			return 4;
		}
		return 5;
	}

	private ScriptObjectHandle installObject(Session session,
			AreaObjectProjection objectDef) {
		int expectedId = -1;
		int expectedType = -1;
		int expectedRotation = -1;
		ResolvedWorldObject current = WorldObjectService.getInstance()
				.resolve(objectDef.x(), objectDef.y(), objectDef.plane());
		if (current != null) {
			expectedId = current.getObjectId();
			expectedType = current.getObjectType();
			expectedRotation = current.getObjectRotation();
		}
		ScriptObjectHandle handle = WorldObjectService.getInstance()
				.replaceArea(session.token, objectDef.x(), objectDef.y(),
						objectDef.plane(), expectedId, expectedType,
						expectedRotation, objectDef.objectId(),
						objectDef.type(), objectDef.rotation());
		if (handle == null) {
			appendDiagnostic("area '" + session.definition.id()
					+ "' object install failed for key '" + objectDef.key()
					+ "' at " + objectDef.x() + "," + objectDef.y() + ","
					+ objectDef.plane() + " (token " + session.token
					+ ", bounds " + session.definition.bounds() + ")");
		}
		return handle;
	}

	private Session newSession(AreaDefinition definition, long generation) {
		long token = nextToken++;
		long ownerToken = nextOwnerToken++;
		long ordinal = nextOrdinal++;
		long processSeed = ScriptEncounterService.getInstance().processSeed();
		AreaSessionRng rng = new AreaSessionRng(processSeed, generation,
				ownerToken, ordinal);
		return new Session(definition, generation, token, ownerToken, ordinal,
				rng);
	}

	private static String tile(AreaObjectProjection object) {
		return "object:" + object.x() + "," + object.y() + "," + object.plane();
	}

	private static Player playerOf(Object[] arguments) {
		if (arguments == null || arguments.length < 1) {
			return null;
		}
		if (arguments[0] instanceof CommandScriptContext) {
			return ((CommandScriptContext) arguments[0]).player
					.backingPlayer();
		}
		if (arguments[0] instanceof ScriptContext) {
			return ((ScriptContext) arguments[0]).player.backingPlayer();
		}
		if (arguments[0] instanceof ScriptedPlayer) {
			return ((ScriptedPlayer) arguments[0]).backingPlayer();
		}
		return null;
	}

	private static ScriptedNpc npcOf(Object[] arguments) {
		if (arguments != null && arguments.length >= 1
				&& arguments[0] instanceof ScriptContext
				&& ((ScriptContext) arguments[0]).target instanceof ScriptedNpc) {
			return (ScriptedNpc) ((ScriptContext) arguments[0]).target;
		}
		return null;
	}

	private void appendDiagnostic(String message) {
		synchronized (diagnostics) {
			diagnostics.add(bound(message));
			while (diagnostics.size() > MAX_DIAGNOSTICS) {
				diagnostics.remove(0);
			}
		}
		logger.log(Level.WARNING, message);
	}

	/** Active-registry drop table lookup of one claim, with the fail seam. */
	private DropTableDefinition dropTable(String id) {
		if (failDropTableLookup) {
			failDropTableLookup = false;
			return null;
		}
		return DropTableRegistry.get(id);
	}

	/**
	 * The one live session of {@code areaId} other than {@code self}: the
	 * retiring predecessor during a same-area-id handoff, or {@code null}
	 * on a fresh activation.
	 */
	private Session predecessorSessionOf(String areaId, Session self) {
		for (Session candidate : sessionsByToken.values()) {
			if (candidate != self && !candidate.closed
					&& candidate.definition.id().equals(areaId)) {
				return candidate;
			}
		}
		return null;
	}

	/** Test-only mid-handoff observation hook; fires once per staged stage. */
	private void fireMidHandoffHook() {
		if (midHandoffHookForTesting != null) {
			midHandoffHookForTesting.run();
		}
	}

	/**
	 * Handoff reservation authority consulted by the layered object write
	 * path before it takes the object-service monitor: while a candidate
	 * holds the reservation, only the exact old or new owner session of the
	 * tile may write it; any other owner (encounter or unrelated area) is
	 * rejected.
	 */
	public synchronized boolean tileReservationAllows(int x, int y, int plane,
			long areaToken, long encounterToken) {
		Reservation reservation = reservations.get(tileKey(x, y, plane));
		if (reservation == null) {
			return true;
		}
		if (areaToken == 0L) {
			return false;
		}
		return areaToken == reservation.newToken
				|| areaToken == reservation.oldToken;
	}

	private static String tileKey(int x, int y, int plane) {
		return "object:" + x + "," + y + "," + plane;
	}

	private static String bound(String value) {
		String trimmed = value == null ? "unknown failure" : value.trim();
		return trimmed.length() <= 512 ? trimmed
				: trimmed.substring(0, 512) + "...";
	}

	private static final class Session {
		private final AreaDefinition definition;
		private final long generation;
		private final long token;
		private final long ownerToken;
		private final long ordinal;
		private final AreaSessionRng rng;
		private final Map<String, SpawnState> spawns =
				new LinkedHashMap<String, SpawnState>();
		private final Map<String, ScriptObjectHandle> objects =
				new LinkedHashMap<String, ScriptObjectHandle>();
		private final List<AreaObjectProjection> pendingObjects =
				new ArrayList<AreaObjectProjection>();
		private final List<AreaNpcSpawn> pendingSpawns =
				new ArrayList<AreaNpcSpawn>();
		private int groundIdentityCount;
		private int objectMutationCount;
		private boolean selected;
		private boolean retired;
		private boolean closed;

		Session(AreaDefinition definition, long generation, long token,
				long ownerToken, long ordinal, AreaSessionRng rng) {
			this.definition = definition;
			this.generation = generation;
			this.token = token;
			this.ownerToken = ownerToken;
			this.ordinal = ordinal;
			this.rng = rng;
		}
	}

	private static final class SpawnState {
		private final AreaNpcSpawn definition;
		private ScriptNpcHandle allocation;
		private long nextRespawnTick;

		SpawnState(AreaNpcSpawn definition, ScriptNpcHandle allocation) {
			this.definition = definition;
			this.allocation = allocation;
		}
	}

	private static final class Reservation {
		private final String key;
		private final long oldToken;
		private final long newToken;

		Reservation(String key, long oldToken, long newToken) {
			this.key = key;
			this.oldToken = oldToken;
			this.newToken = newToken;
		}
	}

	private static final class UndoEntry {
		private final ScriptNpcService.AreaNpcLease lease;
		private final List<WorldObjectService.AreaObjectRestore> restores;
		private final Session session;
		private final SpawnState spawn;
		private boolean restored;

		static UndoEntry npc(ScriptNpcService.AreaNpcLease lease,
				Session session, SpawnState spawn) {
			return new UndoEntry(lease, null, session, spawn);
		}

		static UndoEntry objects(
				List<WorldObjectService.AreaObjectRestore> restores,
				Session session) {
			return new UndoEntry(null, restores, session, null);
		}

		private UndoEntry(ScriptNpcService.AreaNpcLease lease,
				List<WorldObjectService.AreaObjectRestore> restores,
				Session session, SpawnState spawn) {
			this.lease = lease;
			this.restores = restores;
			this.session = session;
			this.spawn = spawn;
		}
	}

}
