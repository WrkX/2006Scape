package com.rs2.script.world;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Value;

import com.rs2.game.items.GroundItem;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.DialogueChain;
import com.rs2.script.ScriptArray;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.ScriptedPosition;
import com.rs2.script.capability.ScriptCameraSession;
import com.rs2.script.scheduler.ScriptScheduler;
import com.rs2.script.scheduler.ScriptTaskHandle;
import com.rs2.script.snapshot.ScriptPlayerSnapshot;
import com.rs2.world.clip.Region;
import com.rs2.world.WorldObjectService;
import com.rs2.world.ItemHandler;
import com.rs2.GameEngine;

/**
 * Authoritative WP3 encounter ownership, reservation, task, and lock service.
 *
 * <p>All indexes are guarded by this object's monitor. Every removal compares
 * the encounter token and exact player identity before changing an index.
 */
public final class ScriptEncounterService {

	/** Engine-only relocation authority; never exported through a script handle. */
	public enum RelocationCause {
		STANDARD,
		DEATH_CLEANUP,
		ADMIN_RECOVERY
	}

	public static final int MAX_ENCOUNTERS_PER_GENERATION = 64;
	public static final int MAX_PARTICIPANTS = 8;
	public static final int MAX_TASKS = 32;
	public static final int MAX_LOCKS_PER_TYPE_PER_PARTICIPANT = 16;
	public static final int MAX_LOCK_TICKS = 100000;
	public static final int MAX_NPCS_PER_ENCOUNTER = 16;
	public static final int MAX_NPC_DEATH_CALLBACKS = 32;
	public static final int MAX_GROUND_IDENTITIES_PER_ENCOUNTER = 128;
	public static final int MAX_OBJECT_MUTATIONS_PER_ENCOUNTER = 32;

	private static final Pattern ID_PATTERN =
			Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
	/** Direction-independent blocked-tile clipping mask (solid, clipped, impassable). */
	private static final int WALKABLE_BLOCK_MASK = 0x1280100;
	private static volatile ScriptEncounterService INSTANCE =
			new ScriptEncounterService();

	private final long processSeed;
	private long nextOwnerToken = 1L;
	private long nextEncounterOrdinal = 1L;
	private final Map<Player, Long> ownerTokens =
			new IdentityHashMap<Player, Long>();
	private int failStagingAtIndexForTesting = -1;
	private boolean failDetachForTesting;

	private final Map<Long, ScriptEncounter> encounters =
			new LinkedHashMap<Long, ScriptEncounter>();
	private final Map<EncounterIdKey, Long> encounterIds =
			new HashMap<EncounterIdKey, Long>();
	private final Map<Player, ParticipantLease> participants =
			new IdentityHashMap<Player, ParticipantLease>();
	private final Map<Integer, List<ReservationLease>> reservations =
			new HashMap<Integer, List<ReservationLease>>();
	private final Map<Long, LockLease> locks = new HashMap<Long, LockLease>();
	private final Map<Player, ScriptCameraSession> cameras =
			new IdentityHashMap<Player, ScriptCameraSession>();
	private final Map<ScriptCameraSession, Long> cameraExpiries =
			new IdentityHashMap<ScriptCameraSession, Long>();
	private final Map<Player, Long> facadeEpochs =
			new IdentityHashMap<Player, Long>();
	private final Map<Player, DialogueChainLease> dialogueChains =
			new IdentityHashMap<Player, DialogueChainLease>();
	private final Map<Player, DialogueOptionLease> dialogueOptions =
			new IdentityHashMap<Player, DialogueOptionLease>();
	private final Map<Player, ScriptPlayerDeathTicket> deathsByPlayer =
			new IdentityHashMap<Player, ScriptPlayerDeathTicket>();
	private final Map<ScriptPlayerDeathTicket, Player> playersByDeath =
			new IdentityHashMap<ScriptPlayerDeathTicket, Player>();

	private long nextToken = 1L;
	private long currentTick;
	private long activeGeneration;
	private long nextFacadeEpoch = 1L;

	public static ScriptEncounterService getInstance() {
		return INSTANCE;
	}

	private ScriptEncounterService() {
		this(new SecureRandom().nextLong());
	}

	/** Deterministic-seed constructor used by the focused RNG/drop tests. */
	ScriptEncounterService(long processSeed) {
		this.processSeed = processSeed;
	}

	/**
	 * Test-only installation of a service with a deterministic process seed.
	 * Never exported to guest code; counter state is monotonic and is not
	 * reset by {@link #resetForTesting()}.
	 */
	public static ScriptEncounterService installForTesting(long processSeed) {
		ScriptEncounterService service = new ScriptEncounterService(processSeed);
		INSTANCE = service;
		return service;
	}

	public synchronized ScriptEncounterHandle begin(ScriptedPlayer scriptedOwner,
			String rawId, double minXValue, double minYValue, double maxXValue,
			double maxYValue, double planeValue) {
		if (scriptedOwner == null) {
			return null;
		}
		Player owner = scriptedOwner.backingPlayer();
		long generation = scriptedOwner.generation();
		String id = normalizeId(rawId);
		Integer minX = coordinate(minXValue);
		Integer minY = coordinate(minYValue);
		Integer maxX = coordinate(maxXValue);
		Integer maxY = coordinate(maxYValue);
		Integer plane = integral(planeValue, 0, 3);
		if (id == null || minX == null || minY == null || maxX == null
				|| maxY == null || plane == null
				|| !canUseFacade(owner, generation,
						scriptedOwner.facadeEpoch(), false)
				|| generation <= 0L
				|| generation != activeGeneration
				|| minX > maxX || minY > maxY
				|| maxX - minX + 1 > 64 || maxY - minY + 1 > 64
				|| !loaded(minX, minY, maxX, maxY)) {
			return null;
		}
		if (participants.containsKey(owner)
				|| countGeneration(generation) >= MAX_ENCOUNTERS_PER_GENERATION) {
			return null;
		}
		EncounterIdKey idKey = new EncounterIdKey(generation, owner, id);
		if (encounterIds.containsKey(idKey)) {
			return null;
		}
		ScriptEncounterReservation reservation =
				new ScriptEncounterReservation(minX, minY, maxX, maxY, plane);
		List<ReservationLease> planeReservations = reservations.get(plane);
		if (planeReservations != null) {
			for (ReservationLease active : planeReservations) {
				if (active.reservation.overlaps(reservation)) {
					return null;
				}
			}
		}

		long ownerToken = nextOwnerToken(owner);
		if (ownerToken == 0L) {
			return null;
		}
		long ordinal = nextEncounterOrdinal;
		if (ordinal == Long.MAX_VALUE) {
			return null;
		}
		nextEncounterOrdinal++;
		ScriptEncounterRng rng = ScriptEncounterRng.derive(processSeed,
				generation, ownerToken, ordinal);
		// Replay log: never exposed to guest code.
		System.out.println("[encounter] generation=" + generation
				+ " ownerToken=" + ownerToken + " ordinal=" + ordinal
				+ " id=" + id + " initialState="
				+ Long.toUnsignedString(rng.state(), 16));

		long token = nextToken++;
		ScriptEncounter encounter = new ScriptEncounter(token, generation, id,
				owner, scriptedOwner, reservation, rng, ownerToken, ordinal);
		ReservationLease reservationLease =
				new ReservationLease(token, generation, reservation);
		if (planeReservations == null) {
			planeReservations = new ArrayList<ReservationLease>();
			reservations.put(plane, planeReservations);
		}
		planeReservations.add(reservationLease);
		encounters.put(token, encounter);
		encounterIds.put(idKey, token);
		participants.put(owner, new ParticipantLease(token, generation, owner));
		return new ScriptEncounterHandle(this, token, id, scriptedOwner);
	}

	public synchronized boolean isOpen(long token) {
		ScriptEncounter encounter = encounters.get(token);
		return encounter != null && encounter.open;
	}

	/** Engine-side validation for allocating an encounter-owned NPC. */
	public synchronized boolean canSpawnNpc(long token, long generation,
			Player player, long facadeEpoch, int x, int y, int plane) {
		ScriptEncounter encounter = openEncounter(token);
		return encounter != null && encounter.generation == generation
				&& generation == activeGeneration && encounter.owner != null
				&& participants.get(player) != null
				&& participants.get(player).encounterToken == token
				&& participants.get(player).player == player
				&& canUseFacade(player, generation, facadeEpoch, false)
				&& encounter.reservation.contains(x, y, plane);
	}

	/** Reserves one of the bounded encounter-owned NPC allocations. */
	public synchronized boolean reserveNpc(long token, long generation) {
		ScriptEncounter encounter = openEncounter(token);
		if (encounter == null || encounter.generation != generation
				|| encounter.npcCount >= MAX_NPCS_PER_ENCOUNTER) {
			return false;
		}
		encounter.npcCount++;
		return true;
	}

	public synchronized void releaseNpc(long token, long generation) {
		ScriptEncounter encounter = encounters.get(token);
		if (encounter != null && encounter.generation == generation
				&& encounter.npcCount > 0) {
			encounter.npcCount--;
		}
	}

	/** Reserves a callback registration; duplicate registrations are rejected. */
	public synchronized boolean reserveNpcDeathCallback(long token,
			long generation) {
		ScriptEncounter encounter = openEncounter(token);
		if (encounter == null || encounter.generation != generation
				|| encounter.deathCallbackCount >= MAX_NPC_DEATH_CALLBACKS) {
			return false;
		}
		encounter.deathCallbackCount++;
		return true;
	}

	public synchronized boolean isOpenForScript(long token, long generation) {
		ScriptEncounter encounter = openEncounter(token);
		return encounter != null && encounter.generation == generation
				&& generation == activeGeneration;
	}

	public synchronized boolean isParticipant(long token, long generation,
			Player player) {
		ParticipantLease lease = participants.get(player);
		return lease != null && lease.player == player
				&& lease.encounterToken == token && lease.generation == generation
				&& isOpenForScript(token, generation);
	}

	public synchronized ScriptEncounterHandle handleForToken(long token) {
		ScriptEncounter encounter = encounters.get(token);
		return encounter == null ? null : new ScriptEncounterHandle(this, token,
				encounter.id, encounter.scriptedOwner);
	}

	public synchronized ScriptNpcHandle spawnNpc(long token, long generation,
			ScriptedPlayer owner, int npcId, int x, int y, int plane, int hp,
			int maxHit, int attack, int defence) {
		return ScriptNpcService.getInstance().spawn(token, generation, owner,
				npcId, x, y, plane, hp, maxHit, attack, defence);
	}

	public synchronized ScriptGroundItemHandle dropFor(long token, long generation,
			ScriptedPlayer target, int itemId, int amount, int x, int y, int plane) {
		ScriptEncounter encounter = openEncounter(token);
		if (encounter == null || target == null || target.generation() != generation
				|| !isParticipant(token, generation, target.backingPlayer())
				|| !encounter.reservation.contains(x, y, plane)
				|| !canUseFacade(target.backingPlayer(), generation,
						target.facadeEpoch(), false)) return null;
		int identities;
		try {
			if (!org.apollo.cache.def.ItemDefinition.exists(itemId)) return null;
			org.apollo.cache.def.ItemDefinition definition =
					org.apollo.cache.def.ItemDefinition.lookup(itemId);
			identities = definition.isStackable() ? 1 : amount;
		} catch (RuntimeException invalidDefinition) {
			return null;
		}
		if (identities <= 0 || encounter.groundIdentityCount >
				MAX_GROUND_IDENTITIES_PER_ENCOUNTER - identities) return null;
		ScriptGroundItemHandle handle =
				GameEngine.itemHandler.createScriptGroundItems(target.backingPlayer(),
						token, itemId, amount, x, y, plane, 0);
		if (handle != null) encounter.groundIdentityCount += handle.identityCount();
		return handle;
	}

	/** Authorizes an object mutation only for an open encounter and its area. */
	public synchronized boolean isObjectWriteAuthorized(long token, int x, int y,
			int plane) {
		ScriptEncounter encounter = openEncounter(token);
		return encounter != null && encounter.reservation.contains(x, y, plane);
	}

	/** Atomically reserves one authoritative object mutation for an encounter. */
	public synchronized boolean reserveObjectMutation(long token) {
		ScriptEncounter encounter = openEncounter(token);
		if (encounter == null || encounter.objectMutationCount >= MAX_OBJECT_MUTATIONS_PER_ENCOUNTER) {
			return false;
		}
		encounter.objectMutationCount++;
		return true;
	}

	/** Releases a reservation when an object transaction fails before commit. */
	public synchronized void releaseObjectMutation(long token) {
		ScriptEncounter encounter = encounters.get(token);
		if (encounter != null && encounter.open && encounter.objectMutationCount > 0) {
			encounter.objectMutationCount--;
		}
	}

	/** Validates every tile occupied by an object footprint against the lease. */
	public synchronized boolean isObjectFootprintAuthorized(long token, int x, int y,
			int plane, int objectId, int rotation) {
		return isObjectFootprintAuthorized(token, x, y, plane, objectId, rotation, 10);
	}

	/** Validates the complete occupied/collision footprint of an object. */
	public synchronized boolean isObjectFootprintAuthorized(long token, int x, int y,
			int plane, int objectId, int rotation, int objectType) {
		ScriptEncounter encounter = openEncounter(token);
		if (encounter == null || objectId < 0 || rotation < 0 || rotation > 3
				|| objectType < 0 || objectType > 22
				|| !encounter.reservation.contains(x, y, plane)) {
			return false;
		}
		int width;
		int length;
		try {
			org.apollo.cache.def.ObjectDefinition[] definitions =
					org.apollo.cache.def.ObjectDefinition.getDefinitions();
			if (definitions == null || objectId >= definitions.length || definitions[objectId] == null) return false;
			org.apollo.cache.def.ObjectDefinition definition = definitions[objectId];
			if (rotation == 1 || rotation == 3) {
				width = definition.getLength(); length = definition.getWidth();
			} else {
				width = definition.getWidth(); length = definition.getLength();
			}
		} catch (RuntimeException invalidDefinition) {
			return false;
		}
		for (int dx = 0; dx < Math.max(1, width); dx++) {
			for (int dy = 0; dy < Math.max(1, length); dy++) {
				if (!encounter.reservation.contains(x + dx, y + dy, plane)) return false;
			}
		}
		// Walls reserve every cell whose clipping bit is changed, including the
		// adjacent tile(s), so an update cannot partially escape its lease.
		if (objectType <= 3) {
			for (int[] offset : wallOffsets(objectType, rotation)) {
				if (!encounter.reservation.contains(x + offset[0], y + offset[1], plane)) return false;
			}
		}
		return true;
	}

	private static int[][] wallOffsets(int type, int face) {
		int f = face & 3;
		if (type == 0) {
			return new int[][] {{0, 0}, f == 0 ? new int[] {-1, 0} : f == 1 ? new int[] {0, 1}
					: f == 2 ? new int[] {1, 0} : new int[] {0, -1}};
		}
		if (type == 1 || type == 3) {
			return new int[][] {{0, 0}, f == 0 ? new int[] {-1, 1} : f == 1 ? new int[] {1, 1}
					: f == 2 ? new int[] {1, -1} : new int[] {-1, -1}};
		}
		return f == 0 ? new int[][] {{0, 0}, {-1, 0}, {0, 1}}
				: f == 1 ? new int[][] {{0, 0}, {0, 1}, {1, 0}}
				: f == 2 ? new int[][] {{0, 0}, {1, 0}, {0, -1}}
				: new int[][] {{0, 0}, {0, -1}, {-1, 0}};
	}

	public synchronized boolean onNpcDeath(long token, ScriptNpcHandle npc,
			Value callback) {
		if (npc == null || callback == null || callback.isNull()) {
			return false;
		}
		ScriptNpcService.OwnedNpc owned = ScriptNpcService.getInstance()
				.resolve(npc);
		if (owned == null || owned.encounterToken != token) {
			return false;
		}
		return ScriptNpcService.getInstance().registerDeath(npc, callback);
	}

	public synchronized boolean addParticipant(long token,
			ScriptedPlayer scriptedPlayer) {
		ScriptEncounter encounter = openEncounter(token);
		if (encounter == null || scriptedPlayer == null
				|| scriptedPlayer.generation() != encounter.generation) {
			return false;
		}
		Player player = scriptedPlayer.backingPlayer();
		if (!canUseFacade(player, encounter.generation,
				scriptedPlayer.facadeEpoch(), false)) {
			return false;
		}
		if (encounter.participants.containsKey(player)) {
			return true;
		}
		if (encounter.participants.size() >= MAX_PARTICIPANTS
				|| participants.containsKey(player)) {
			return false;
		}
		encounter.participants.put(player, scriptedPlayer);
		participants.put(player,
				new ParticipantLease(token, encounter.generation, player));
		return true;
	}

	public synchronized boolean removeParticipant(long token,
			ScriptedPlayer scriptedPlayer) {
		ScriptEncounter encounter = openEncounter(token);
		if (encounter == null || scriptedPlayer == null) {
			return false;
		}
		Player player = scriptedPlayer.backingPlayer();
		if (player == encounter.owner) {
			return closeInternal(encounter);
		}
		return removeParticipantInternal(encounter, player);
	}

	public synchronized ScriptArray participants(long token) {
		ScriptEncounter encounter = openEncounter(token);
		if (encounter == null) {
			return new ScriptArray(new Object[0]);
		}
		return new ScriptArray(encounter.participants.values());
	}

	public synchronized boolean contains(long token, double xValue,
			double yValue, double planeValue) {
		ScriptEncounter encounter = openEncounter(token);
		Integer x = coordinate(xValue);
		Integer y = coordinate(yValue);
		Integer plane = integral(planeValue, 0, 3);
		return encounter != null && x != null && y != null && plane != null
				&& encounter.reservation.contains(x, y, plane);
	}

	/** Encounter-scoped bounded roll; invalid bounds return -1 without advance. */
	public synchronized int nextInt(long token, int bound) {
		ScriptEncounter encounter = openEncounter(token);
		return encounter == null ? -1 : encounter.rng.nextInt(bound);
	}

	/** Encounter-scoped rational chance; invalid input returns false. */
	public synchronized boolean chance(long token, int numerator,
			int denominator) {
		ScriptEncounter encounter = openEncounter(token);
		return encounter != null
				&& encounter.rng.chance(numerator, denominator);
	}

	/**
	 * Rolls and stages one logical reward per selected entry as one
	 * transaction: parse, preflight, selection and amount rolls on a cloned
	 * RNG, exact identity staging, and a final private detach. Any failure
	 * removes every staged identity and leaves the encounter RNG unchanged.
	 */
	public synchronized ScriptArray rollDrops(long token, long generation,
			ScriptedPlayer target, int x, int y, int plane, int privateTicks,
			Value entries) {
		ScriptEncounter encounter = openEncounter(token);
		if (encounter == null || target == null
				|| target.generation() != generation
				|| !isParticipant(token, generation, target.backingPlayer())
				|| !encounter.reservation.contains(x, y, plane)
				|| privateTicks < 1 || privateTicks > 1000
				|| !canUseFacade(target.backingPlayer(), generation,
						target.facadeEpoch(), false)) {
			return new ScriptArray(new Object[0]);
		}
		List<ScriptDropEntry> parsed;
		try {
			parsed = ScriptDropEntryParser.parse(entries);
		} catch (RuntimeException invalidTable) {
			return new ScriptArray(new Object[0]);
		}
		long worstCaseIdentities = 0L;
		long worstCaseAmount = 0L;
		for (ScriptDropEntry entry : parsed) {
			try {
				org.apollo.cache.def.ItemDefinition definition =
						org.apollo.cache.def.ItemDefinition.lookup(entry.itemId);
				worstCaseIdentities += definition.isStackable()
						? 1L : entry.maxAmount;
			} catch (RuntimeException invalidDefinition) {
				return new ScriptArray(new Object[0]);
			}
			worstCaseAmount += entry.maxAmount;
		}
		if (worstCaseIdentities > MAX_GROUND_IDENTITIES_PER_ENCOUNTER
				- encounter.groundIdentityCount
				|| worstCaseAmount > Integer.MAX_VALUE) {
			return new ScriptArray(new Object[0]);
		}

		ScriptEncounterRng transaction = encounter.rng.copy();
		int failAt = failStagingAtIndexForTesting;
		boolean failDetach = failDetachForTesting;
		failStagingAtIndexForTesting = -1;
		failDetachForTesting = false;
		ScriptDropEntry weightedWinner = selectWeighted(parsed, transaction);
		List<ScriptDropEntry> selected = new ArrayList<ScriptDropEntry>();
		List<Integer> amounts = new ArrayList<Integer>();
		List<ScriptGroundItemHandle> staged =
				new ArrayList<ScriptGroundItemHandle>();
		List<GroundItem> stagedIdentities = new ArrayList<GroundItem>();
		try {
			int stageIndex = 0;
			for (ScriptDropEntry entry : parsed) {
				if (!entry.always && entry != weightedWinner) {
					continue;
				}
				if (stageIndex == failAt) {
					throw new IllegalStateException("injected staging failure");
				}
				int amount = entry.minAmount + transaction.nextInt(
						entry.maxAmount - entry.minAmount + 1);
				ScriptGroundItemHandle handle = GameEngine.itemHandler
						.createScriptGroundItems(target.backingPlayer(), token,
								entry.itemId, amount, x, y, plane, 0);
				if (handle == null) {
					throw new IllegalStateException("reward staging failed");
				}
				selected.add(entry);
				amounts.add(Integer.valueOf(amount));
				staged.add(handle);
				stagedIdentities.addAll(handle.identities());
				stageIndex++;
			}
			if (failDetach) {
				throw new IllegalStateException("injected detach failure");
			}
			if (!GameEngine.itemHandler.detachExact(stagedIdentities,
					privateTicks)) {
				throw new IllegalStateException("reward detach failed");
			}
		} catch (RuntimeException failure) {
			GameEngine.itemHandler.removeExact(stagedIdentities);
			return new ScriptArray(new Object[0]);
		}
		encounter.rng.restore(transaction.state());
		encounter.groundIdentityCount += stagedIdentities.size();
		Object[] results = new Object[selected.size()];
		for (int index = 0; index < selected.size(); index++) {
			results[index] = new ScriptDropResult(selected.get(index).itemId,
					amounts.get(index).intValue(), staged.get(index));
		}
		return new ScriptArray(results);
	}

	/** Chebyshev distance between two valid same-plane positions, else -1. */
	public synchronized int distance(long token, ScriptedPosition first,
			ScriptedPosition second) {
		ScriptEncounter encounter = openEncounter(token);
		if (encounter == null || first == null || second == null
				|| first.plane != second.plane
				|| first.plane < 0 || first.plane > 3
				|| first.x < 0 || first.x > 16383
				|| first.y < 0 || first.y > 16383
				|| second.x < 0 || second.x > 16383
				|| second.y < 0 || second.y > 16383) {
			return -1;
		}
		return Math.max(Math.abs(first.x - second.x),
				Math.abs(first.y - second.y));
	}

	/** Authoritative clipping result for a loaded in-area cell. */
	public synchronized boolean isWalkable(long token, double xValue,
			double yValue, double planeValue) {
		ScriptEncounter encounter = openEncounter(token);
		Integer x = coordinate(xValue);
		Integer y = coordinate(yValue);
		Integer plane = integral(planeValue, 0, 3);
		if (encounter == null || x == null || y == null || plane == null
				|| !encounter.reservation.contains(x, y, plane)
				|| Region.getRegion(x, y) == null) {
			return false;
		}
		return (Region.getClipping(x, y, plane) & WALKABLE_BLOCK_MASK) == 0;
	}

	/** Authoritative straight-line projectile clipping between two in-area cells. */
	public synchronized boolean hasProjectilePath(long token,
			double fromXValue, double fromYValue, double toXValue,
			double toYValue, double planeValue) {
		ScriptEncounter encounter = openEncounter(token);
		Integer fromX = coordinate(fromXValue);
		Integer fromY = coordinate(fromYValue);
		Integer toX = coordinate(toXValue);
		Integer toY = coordinate(toYValue);
		Integer plane = integral(planeValue, 0, 3);
		if (encounter == null || fromX == null || fromY == null || toX == null
				|| toY == null || plane == null
				|| !encounter.reservation.contains(fromX, fromY, plane)
				|| !encounter.reservation.contains(toX, toY, plane)
				|| Region.getRegion(fromX, fromY) == null
				|| Region.getRegion(toX, toY) == null) {
			return false;
		}
		int x = fromX.intValue();
		int y = fromY.intValue();
		while (x != toX.intValue() || y != toY.intValue()) {
			int stepX = toX.intValue() > x ? 1 : toX.intValue() < x ? -1 : 0;
			int stepY = toY.intValue() > y ? 1 : toY.intValue() < y ? -1 : 0;
			if (!Region.canShoot(x, y, plane.intValue(),
					directionOf(stepX, stepY))) {
				return false;
			}
			x += stepX;
			y += stepY;
		}
		return true;
	}

	/** Encounter RNG state used by the focused drop-transaction tests. */
	public synchronized long rngStateForTesting(long token) {
		ScriptEncounter encounter = encounters.get(token);
		return encounter == null ? 0L : encounter.rng.state();
	}

	/** Encounter ordinal used by the focused drop-transaction tests. */
	public synchronized long encounterOrdinalForTesting(long token) {
		ScriptEncounter encounter = encounters.get(token);
		return encounter == null ? 0L : encounter.ordinal;
	}

	/** Owner token used by the focused drop-transaction tests. */
	public synchronized long ownerTokenForTesting(Player player) {
		Long ownerToken = player == null ? null : ownerTokens.get(player);
		return ownerToken == null ? 0L : ownerToken.longValue();
	}

	/** One-shot staging failure injection; reset after the next roll. */
	public synchronized void failStagingForTesting(int index) {
		failStagingAtIndexForTesting = index;
	}

	/** One-shot detach failure injection; reset after the next roll. */
	public synchronized void failDetachForTesting() {
		failDetachForTesting = true;
	}

	/** Assigns the monotonic positive owner token, rejecting overflow. */
	private long nextOwnerToken(Player owner) {
		Long existing = ownerTokens.get(owner);
		if (existing != null) {
			return existing.longValue();
		}
		if (nextOwnerToken == Long.MAX_VALUE) {
			return 0L;
		}
		long ownerToken = nextOwnerToken++;
		ownerTokens.put(owner, Long.valueOf(ownerToken));
		return ownerToken;
	}

	/** Selects exactly one weighted entry by cumulative input order. */
	private static ScriptDropEntry selectWeighted(List<ScriptDropEntry> entries,
			ScriptEncounterRng rng) {
		long sum = 0L;
		for (ScriptDropEntry entry : entries) {
			sum += entry.weight;
		}
		if (sum == 0L) {
			return null;
		}
		int pick = rng.nextInt((int) sum);
		long cumulative = 0L;
		for (ScriptDropEntry entry : entries) {
			if (entry.weight == 0) {
				continue;
			}
			cumulative += entry.weight;
			if (pick < cumulative) {
				return entry;
			}
		}
		return null;
	}

	private static int directionOf(int stepX, int stepY) {
		if (stepX == -1 && stepY == 1) {
			return 0;
		}
		if (stepX == 0 && stepY == 1) {
			return 1;
		}
		if (stepX == 1 && stepY == 1) {
			return 2;
		}
		if (stepX == -1 && stepY == 0) {
			return 3;
		}
		if (stepX == 1 && stepY == 0) {
			return 4;
		}
		if (stepX == -1 && stepY == -1) {
			return 5;
		}
		if (stepX == 0 && stepY == -1) {
			return 6;
		}
		return 7;
	}

	public synchronized ScriptTaskHandle schedule(long token, double ticksValue,
			boolean repeating, Value callback) {
		ScriptEncounter encounter = openEncounter(token);
		Integer ticks = integral(ticksValue, 1, ScriptScheduler.MAX_TICKS);
		if (encounter == null || ticks == null
				|| !isExecutable(callback)) {
			return null;
		}
		pruneDoneTasks(encounter);
		if (taskCount(encounter) >= MAX_TASKS) {
			return null;
		}
		ScriptTaskHandle task = ScriptScheduler.getInstance().schedule(
				encounter.owner, encounter.generation, ticks, repeating, callback,
				new Runnable() {
					@Override
					public void run() {
						close(token);
					}
				});
		encounter.tasks.add(task);
		return task;
	}

	public synchronized ScriptTaskHandle scheduleParticipantTask(Player player,
			long generation, long facadeEpoch, double ticksValue, boolean repeating,
			Value callback) {
		ParticipantLease lease = participants.get(player);
		if (lease == null) {
			Integer ticks = integral(ticksValue, 1, ScriptScheduler.MAX_TICKS);
			return !canUseFacade(player, generation, facadeEpoch, false)
					|| ticks == null
					|| !isExecutable(callback)
					? ScriptTaskHandle.rejected()
					: ScriptScheduler.getInstance().schedule(
					player, generation, ticks, repeating, callback);
		}
		if (lease.generation != generation || generation != activeGeneration
				|| lease.player != player
				|| !canUseFacade(player, generation, facadeEpoch, false)) {
			return ScriptTaskHandle.rejected();
		}
		ScriptEncounter encounter = openEncounter(lease.encounterToken);
		Integer ticks = integral(ticksValue, 1, ScriptScheduler.MAX_TICKS);
		if (encounter == null) {
			if (participants.get(player) == lease) {
				participants.remove(player);
			}
			return ScriptTaskHandle.rejected();
		}
		if (ticks == null || !isExecutable(callback)) {
			return ScriptTaskHandle.rejected();
		}
		pruneDoneTasks(encounter);
		if (taskCount(encounter) >= MAX_TASKS) {
			return ScriptTaskHandle.rejected();
		}
		ScriptTaskHandle task = ScriptScheduler.getInstance().schedule(
				player, generation, ticks, repeating, callback,
				new Runnable() {
					@Override
					public void run() {
						close(lease.encounterToken);
					}
				});
		List<ScriptTaskHandle> owned = encounter.participantTasks.get(player);
		if (owned == null) {
			owned = new ArrayList<ScriptTaskHandle>();
			encounter.participantTasks.put(player, owned);
		}
		owned.add(task);
		return task;
	}

	public synchronized ScriptLockHandle acquireActionLock(Player player,
			long generation, long facadeEpoch, double ticksValue) {
		return acquireLock(player, generation, facadeEpoch,
				ticksValue, LockType.ACTION);
	}

	public synchronized ScriptLockHandle acquireMovementLock(Player player,
			long generation, long facadeEpoch, double ticksValue) {
		return acquireLock(player, generation, facadeEpoch,
				ticksValue, LockType.MOVEMENT);
	}

	/** Allocates the sole composable camera session for a player. */
	public synchronized ScriptCameraSession beginCamera(Player player,
			long generation, long facadeEpoch, int ticks) {
		if (player == null || ticks < 1 || ticks > MAX_LOCK_TICKS
				|| !canUseFacade(player, generation, facadeEpoch, false)
				|| player.getOutStream() == null
				|| cameras.containsKey(player)) {
			return null;
		}
		long token = nextToken++;
		ScriptCameraSession session = new ScriptCameraSession(this, player,
				generation, facadeEpoch, token);
		cameras.put(player, session);
		cameraExpiries.put(session, Long.valueOf(currentTick + ticks));
		return session;
	}

	public synchronized boolean isCameraActive(ScriptCameraSession session) {
		if (session == null || cameras.get(session.player()) != session
				|| !canUseFacade(session.player(), session.generation(),
						session.facadeEpoch(), false)) {
			return false;
		}
		Long expiry = cameraExpiries.get(session);
		return expiry != null && expiry.longValue() > currentTick;
	}

	public synchronized boolean releaseCamera(ScriptCameraSession session) {
		if (session == null || cameras.get(session.player()) != session) return false;
		cameras.remove(session.player(), session);
		cameraExpiries.remove(session);
		if (session.player().getOutStream() != null) {
			session.player().getPlayerAssistant().sendCameraReset();
		}
		return true;
	}

	public synchronized boolean resetCamera(Player player, long generation,
			long facadeEpoch) {
		if (!canUseFacade(player, generation, facadeEpoch, false)) return false;
		ScriptCameraSession session = cameras.get(player);
		return session == null ? false : releaseCamera(session);
	}

	private ScriptLockHandle acquireLock(Player player, long generation,
			long facadeEpoch, double ticksValue, LockType type) {
		Integer ticks = integral(ticksValue, 1, MAX_LOCK_TICKS);
		ParticipantLease participant = participants.get(player);
		if (ticks == null || participant == null
				|| participant.generation != generation
				|| generation != activeGeneration
				|| participant.player != player
				|| !canUseFacade(player, generation, facadeEpoch, false)) {
			return null;
		}
		ScriptEncounter encounter = openEncounter(participant.encounterToken);
		if (encounter == null) {
			return null;
		}
		Map<Player, List<Long>> index = type == LockType.ACTION
				? encounter.actionLocks : encounter.movementLocks;
		List<Long> playerLocks = index.get(player);
		if (playerLocks == null) {
			playerLocks = new ArrayList<Long>();
			index.put(player, playerLocks);
		}
		if (playerLocks.size() >= MAX_LOCKS_PER_TYPE_PER_PARTICIPANT) {
			return null;
		}
		long token = nextToken++;
		playerLocks.add(token);
		locks.put(token, new LockLease(token, encounter.token,
				encounter.generation, player, type, currentTick + ticks));
		return new ScriptLockHandle(this, token);
	}

	public synchronized boolean isLockActive(long token) {
		return locks.containsKey(token);
	}

	public synchronized boolean releaseLock(long token) {
		LockLease lease = locks.remove(token);
		if (lease == null) {
			return false;
		}
		ScriptEncounter encounter = encounters.get(lease.encounterToken);
		if (encounter != null && encounter.generation == lease.generation) {
			Map<Player, List<Long>> index = lease.type == LockType.ACTION
					? encounter.actionLocks : encounter.movementLocks;
			List<Long> playerLocks = index.get(lease.player);
			if (playerLocks != null) {
				playerLocks.remove(Long.valueOf(token));
				if (playerLocks.isEmpty()) {
					index.remove(lease.player);
				}
			}
		}
		return true;
	}

	public synchronized boolean isActionLocked(Player player) {
		return hasLocks(player, LockType.ACTION);
	}

	public synchronized boolean isMovementLocked(Player player) {
		return hasLocks(player, LockType.MOVEMENT);
	}

	public synchronized boolean canEnter(Player player, int x, int y,
			int plane) {
		ReservationLease destination = reservationAt(x, y, plane);
		if (destination == null) {
			return true;
		}
		ParticipantLease participant = participants.get(player);
		return participant != null
				&& participant.encounterToken == destination.encounterToken
				&& participant.player == player;
	}

	public synchronized boolean canDestination(Player player, int x, int y,
			int plane) {
		return canDestination(player, x, y, plane, RelocationCause.STANDARD);
	}

	public synchronized boolean canDestination(Player player, int x, int y,
			int plane, RelocationCause cause) {
		if (cause == RelocationCause.DEATH_CLEANUP
				|| cause == RelocationCause.ADMIN_RECOVERY) {
			return true;
		}
		if (isMovementLocked(player)) {
			return false;
		}
		ParticipantLease participant = participants.get(player);
		if (participant == null) {
			return canEnter(player, x, y, plane);
		}
		ScriptEncounter encounter = openEncounter(participant.encounterToken);
		return encounter != null
				&& encounter.reservation.contains(x, y, plane);
	}

	public synchronized boolean canMutate(Player player, long generation,
			long facadeEpoch) {
		return canUseFacade(player, generation, facadeEpoch, true)
				&& !hasLocks(player, LockType.ACTION);
	}

	/** Validation for movement facades; action locks deliberately do not apply. */
	public synchronized boolean canMoveFacade(Player player, long generation,
			long facadeEpoch) {
		return canUseFacade(player, generation, facadeEpoch, false);
	}

	/** Validation for stateful scheduling/lock facade access. */
	public synchronized boolean canUseFacade(Player player, long generation,
			long facadeEpoch, boolean requireOutput) {
		Long currentEpoch = facadeEpochs.get(player);
		if (generation != activeGeneration || currentEpoch == null
				|| currentEpoch.longValue() != facadeEpoch
				|| !isAuthoritativeLive(player, requireOutput)) {
			return false;
		}
		ParticipantLease participant = participants.get(player);
		if (participant == null) {
			return true;
		}
		ScriptEncounter encounter = openEncounter(participant.encounterToken);
		return participant.player == player
				&& participant.generation == generation
				&& encounter != null
				&& encounter.generation == generation
				&& encounter.participants.containsKey(player);
	}

	/**
	 * Arms one exact deferred dialogue chain for the originating facade.
	 * Replacing a chain also invalidates any older scripted option.
	 */
	public synchronized long armDialogueChain(Player player, long generation,
			long facadeEpoch) {
		if (!canMutate(player, generation, facadeEpoch)) {
			invalidateDialogueInternal(player);
			return 0L;
		}
		invalidateDialogueInternal(player);
		long token = nextToken++;
		dialogueChains.put(player, new DialogueChainLease(
				token, generation, facadeEpoch, player));
		player.scriptDialogueGeneration = generation;
		player.scriptDialogueFacadeEpoch = facadeEpoch;
		player.scriptDialogueToken = token;
		return token;
	}

	/** Revalidates the exact chain immediately before a deferred frame runs. */
	public synchronized boolean canAdvanceDialogueChain(Player player) {
		DialogueChainLease lease = dialogueChains.get(player);
		if (lease == null || lease.player != player
				|| lease.token != player.scriptDialogueToken
				|| lease.generation != player.scriptDialogueGeneration
				|| lease.facadeEpoch != player.scriptDialogueFacadeEpoch
				|| !canMutate(player, lease.generation, lease.facadeEpoch)) {
			invalidateDialogueInternal(player);
			return false;
		}
		return true;
	}

	/** Completes only the exact currently armed chain, preserving any option. */
	public synchronized void completeDialogueChain(Player player, long token) {
		DialogueChainLease lease = dialogueChains.get(player);
		if (lease == null || lease.player != player || lease.token != token) {
			return;
		}
		dialogueChains.remove(player);
		clearDialogueChainFields(player);
	}

	/**
	 * Arms an exact one-shot scripted option and records the offered choice
	 * count in the authoritative lease.
	 */
	public synchronized long armDialogueOption(Player player, long generation,
			long facadeEpoch, int offeredCount, Consumer<Integer> callback) {
		if (callback == null || offeredCount < 2 || offeredCount > 5
				|| !canMutate(player, generation, facadeEpoch)) {
			clearDialogueOptionInternal(player);
			return 0L;
		}
		clearDialogueOptionInternal(player);
		long token = nextToken++;
		dialogueOptions.put(player, new DialogueOptionLease(token, generation,
				facadeEpoch, player, offeredCount));
		player.pendingScriptOption = callback;
		player.pendingOptionCount = offeredCount;
		player.pendingScriptOptionGeneration = generation;
		player.pendingScriptOptionFacadeEpoch = facadeEpoch;
		player.pendingScriptOptionToken = token;
		return token;
	}

	/**
	 * Returns the service-owned offered count. Action locks deliberately do not
	 * apply: this is only the read/authentication half of the exact exception.
	 */
	public synchronized int pendingDialogueOptionCount(Player player) {
		DialogueOptionLease lease = exactDialogueOption(player);
		if (lease == null || !canUseFacade(player, lease.generation,
				lease.facadeEpoch, true)) {
			invalidateDialogueInternal(player);
			return -1;
		}
		return lease.offeredCount;
	}

	/**
	 * Consumes an exact, live, actually-offered option once. This is the sole
	 * authenticated action-lock exception, so it uses the facade validator
	 * without the action-lock predicate.
	 */
	public synchronized boolean consumeDialogueOption(Player player,
			int choice) {
		DialogueOptionLease lease = exactDialogueOption(player);
		if (lease == null) {
			invalidateDialogueInternal(player);
			return false;
		}
		if (choice < 0 || choice >= lease.offeredCount) {
			return false;
		}
		if (!canUseFacade(player, lease.generation,
				lease.facadeEpoch, true)) {
			invalidateDialogueInternal(player);
			return false;
		}
		dialogueOptions.remove(player);
		clearDialogueOptionFields(player);
		return true;
	}

	/** Callback-free invalidation used by lifecycle and dialogue cleanup. */
	public synchronized void invalidateDialogue(Player player) {
		invalidateDialogueInternal(player);
	}

	public synchronized boolean canObserve(Player viewer, Player target) {
		if (!isAuthoritativeLive(viewer, true)
				|| !isAuthoritativeLive(target, true)) {
			return false;
		}
		ParticipantLease viewerLease = participants.get(viewer);
		ParticipantLease targetLease = participants.get(target);
		if (viewerLease == null && targetLease == null) {
			return true;
		}
		return viewerLease != null && targetLease != null
				&& viewerLease.encounterToken == targetLease.encounterToken;
	}

	public synchronized boolean canTarget(Player actor, Player target) {
		return canObserve(actor, target);
	}

	/** WP4 preparation seam for an entity bound to an owned reservation. */
	public synchronized boolean canObserveReservation(Player viewer,
			long encounterToken, int x, int y, int plane) {
		if (!isAuthoritativeLive(viewer, true)) {
			return false;
		}
		ScriptEncounter encounter = openEncounter(encounterToken);
		ParticipantLease participant = participants.get(viewer);
		return encounter != null && participant != null
				&& participant.player == viewer
				&& participant.encounterToken == encounterToken
				&& encounter.reservation.contains(x, y, plane);
	}

	/** Participant gate for encounter-owned world-object visibility/interactions. */
	public synchronized boolean canObserveOwnedObject(Player viewer,
			long encounterToken, int x, int y, int plane) {
		if (!isAuthoritativeLive(viewer, true)) return false;
		ParticipantLease lease = participants.get(viewer);
		ScriptEncounter encounter = openEncounter(encounterToken);
		return lease != null && lease.player == viewer
				&& lease.encounterToken == encounterToken
				&& encounter != null && encounter.reservation.contains(x, y, plane)
				&& encounter.participants.containsKey(viewer);
	}

	/** Validates a presentation endpoint against the caller's encounter area. */
	public synchronized boolean canPresentAt(Player player, long generation,
			long facadeEpoch, int x, int y, int plane) {
		if (!canUseFacade(player, generation, facadeEpoch, true)) return false;
		ParticipantLease lease = participants.get(player);
		if (lease == null) return true;
		ScriptEncounter encounter = openEncounter(lease.encounterToken);
		return encounter != null && encounter.reservation.contains(x, y, plane);
	}

	/** WP4 preparation seam for targeting an entity in an owned reservation. */
	public synchronized boolean canTargetReservation(Player actor,
			long encounterToken, int x, int y, int plane) {
		return canObserveReservation(actor, encounterToken, x, y, plane);
	}

	public synchronized void processGameTick() {
		currentTick++;
		List<ScriptCameraSession> expiredCameras = new ArrayList<ScriptCameraSession>();
		for (Map.Entry<ScriptCameraSession, Long> entry : cameraExpiries.entrySet()) {
			if (entry.getValue().longValue() <= currentTick) expiredCameras.add(entry.getKey());
		}
		for (ScriptCameraSession session : expiredCameras) releaseCamera(session);
		List<Long> expired = new ArrayList<Long>();
		for (LockLease lease : locks.values()) {
			if (lease.expiresAt <= currentTick) {
				expired.add(lease.token);
			}
		}
		Collections.sort(expired);
		for (Long token : expired) {
			releaseLock(token);
		}
	}

	public synchronized void onPlayerLogout(Player player) {
		advanceFacadeEpoch(player);
		removeForLifecycle(player);
	}

	public synchronized void onPlayerRemoved(Player player) {
		advanceFacadeEpoch(player);
		removeForLifecycle(player);
	}

	public synchronized void onPlayerDeath(Player player) {
		advanceFacadeEpoch(player);
		removeForLifecycle(player);
	}

	public synchronized void onPlayerLogin(Player player) {
		advanceFacadeEpoch(player);
	}

	public synchronized void closeGeneration(long generation) {
		List<ScriptEncounter> closing = new ArrayList<ScriptEncounter>();
		for (ScriptEncounter encounter : encounters.values()) {
			if (encounter.generation == generation) {
				closing.add(encounter);
			}
		}
		Collections.sort(closing, new Comparator<ScriptEncounter>() {
			@Override
			public int compare(ScriptEncounter first, ScriptEncounter second) {
				return Long.compare(first.token, second.token);
			}
		});
		for (ScriptEncounter encounter : closing) {
			closeInternal(encounter);
		}
	}

	/** Records the generation atomically published by ScriptHost. */
	public synchronized void onGenerationPublished(long generation) {
		activeGeneration = generation;
		invalidateAllDialogueContinuations();
	}

	public synchronized boolean close(long token) {
		ScriptEncounter encounter = encounters.get(token);
		return encounter != null && closeInternal(encounter);
	}

	public synchronized void closeAll() {
		List<Long> tokens = new ArrayList<Long>(encounters.keySet());
		for (Long token : tokens) {
			close(token);
		}
	}

	public synchronized ScriptPlayerDeathTicket beginPlayerDeath(Player victim) {
		if (!isAuthoritativeDeathTransition(victim)
				|| deathsByPlayer.containsKey(victim)) {
			return null;
		}
		long transition = nextToken++;
		ScriptPlayerSnapshot victimSnapshot = new ScriptPlayerSnapshot(victim);
		Player killer = resolveKiller(victim);
		ScriptPlayerSnapshot killerSnapshot =
				killer == null ? null : new ScriptPlayerSnapshot(killer);
		ScriptedPosition position =
				new ScriptedPosition(victim.absX, victim.absY, victim.heightLevel);
		ScriptPlayerDeathTicket ticket =
				new ScriptPlayerDeathTicket(transition, victimSnapshot,
				killerSnapshot, position);
		deathsByPlayer.put(victim, ticket);
		playersByDeath.put(ticket, victim);
		advanceFacadeEpoch(victim);
		removeForLifecycle(victim);
		return ticket;
	}

	public synchronized boolean completePlayerDeath(
			ScriptPlayerDeathTicket ticket) {
		if (ticket == null) {
			return false;
		}
		Player victim = playersByDeath.remove(ticket);
		if (victim == null || deathsByPlayer.get(victim) != ticket) {
			return false;
		}
		deathsByPlayer.remove(victim);
		return true;
	}

	public synchronized void resetForTesting() {
		closeAll();
		ScriptNpcService.getInstance().resetForTesting();
		WorldObjectService.getInstance().resetForTesting();
		GameEngine.itemHandler.items.clear();
		invalidateAllDialogueContinuations();
		locks.clear();
		cameras.clear();
		cameraExpiries.clear();
		reservations.clear();
		participants.clear();
		encounterIds.clear();
		facadeEpochs.clear();
		deathsByPlayer.clear();
		playersByDeath.clear();
		failStagingAtIndexForTesting = -1;
		failDetachForTesting = false;
		currentTick = 0L;
		activeGeneration = 0L;
		nextFacadeEpoch = 1L;
	}

	/** Captures the exact lifecycle epoch owned by a ScriptedPlayer wrapper. */
	public synchronized long captureFacadeEpoch(Player player) {
		Long epoch = facadeEpochs.get(player);
		if (epoch == null) {
			epoch = Long.valueOf(nextFacadeEpoch++);
			facadeEpochs.put(player, epoch);
		}
		return epoch.longValue();
	}

	private void advanceFacadeEpoch(Player player) {
		if (player != null) {
			ScriptCameraSession session = cameras.get(player);
			if (session != null) releaseCamera(session);
			invalidateDialogueInternal(player);
			facadeEpochs.put(player, Long.valueOf(nextFacadeEpoch++));
		}
	}

	private DialogueOptionLease exactDialogueOption(Player player) {
		DialogueOptionLease lease = dialogueOptions.get(player);
		return lease != null && lease.player == player
				&& lease.token == player.pendingScriptOptionToken
				&& lease.generation == player.pendingScriptOptionGeneration
				&& lease.facadeEpoch == player.pendingScriptOptionFacadeEpoch
				&& lease.offeredCount == player.pendingOptionCount
				&& player.pendingScriptOption != null ? lease : null;
	}

	private void invalidateAllDialogueContinuations() {
		List<Player> players = new ArrayList<Player>();
		players.addAll(dialogueChains.keySet());
		for (Player player : dialogueOptions.keySet()) {
			if (!players.contains(player)) {
				players.add(player);
			}
		}
		for (Player player : players) {
			invalidateDialogueInternal(player);
		}
		dialogueChains.clear();
		dialogueOptions.clear();
	}

	private void invalidateDialogueInternal(Player player) {
		if (player == null) {
			return;
		}
		dialogueChains.remove(player);
		dialogueOptions.remove(player);
		clearDialogueChainFields(player);
		clearDialogueOptionFields(player);
		player.dialogueAction = 0;
	}

	private void clearDialogueOptionInternal(Player player) {
		if (player == null) {
			return;
		}
		dialogueOptions.remove(player);
		clearDialogueOptionFields(player);
	}

	private static void clearDialogueChainFields(Player player) {
		player.scriptDialogueFrames = null;
		player.scriptDialogueFrameIndex = 0;
		player.scriptDialogueGeneration = 0L;
		player.scriptDialogueFacadeEpoch = 0L;
		player.scriptDialogueToken = 0L;
		if (player.nextChat == DialogueChain.CHAIN_SENTINEL) {
			player.nextChat = 0;
		}
	}

	private static void clearDialogueOptionFields(Player player) {
		player.pendingScriptOption = null;
		player.pendingOptionCount = 0;
		player.pendingScriptOptionGeneration = 0L;
		player.pendingScriptOptionFacadeEpoch = 0L;
		player.pendingScriptOptionToken = 0L;
	}

	private boolean closeInternal(ScriptEncounter encounter) {
		if (!encounter.open) {
			return false;
		}
		ScriptNpcService.getInstance().closeEncounter(encounter.token);
		WorldObjectService.getInstance().closeEncounter(encounter.token);
		GameEngine.itemHandler.closeEncounterRewards(encounter.token);
		// WP3 close order: close/callback unregister, tasks, camera no-op,
		// action/movement locks, targeting, participants/indexes, reservation.
		encounter.open = false;
		for (Player player : new ArrayList<Player>(encounter.participants.keySet())) {
			ScriptCameraSession camera = cameras.get(player);
			if (camera != null) releaseCamera(camera);
		}
		for (ScriptTaskHandle task : new ArrayList<ScriptTaskHandle>(
				encounter.tasks)) {
			task.cancel();
		}
		encounter.tasks.clear();
		for (List<ScriptTaskHandle> owned :
				new ArrayList<List<ScriptTaskHandle>>(
						encounter.participantTasks.values())) {
			for (ScriptTaskHandle task :
					new ArrayList<ScriptTaskHandle>(owned)) {
				task.cancel();
			}
		}
		encounter.participantTasks.clear();
		releaseEncounterLocks(encounter, LockType.ACTION);
		releaseEncounterLocks(encounter, LockType.MOVEMENT);
		for (Player player : new ArrayList<Player>(
				encounter.participants.keySet())) {
			clearTargeting(player);
			removeParticipantIndex(encounter, player);
		}
		encounter.participants.clear();
		encounters.remove(encounter.token, encounter);
		encounterIds.remove(new EncounterIdKey(encounter.generation,
				encounter.owner, encounter.id), encounter.token);
		List<ReservationLease> plane =
				reservations.get(encounter.reservation.plane);
		if (plane != null) {
			Iterator<ReservationLease> iterator = plane.iterator();
			while (iterator.hasNext()) {
				ReservationLease lease = iterator.next();
				if (lease.encounterToken == encounter.token
						&& lease.generation == encounter.generation
						&& lease.reservation == encounter.reservation) {
					iterator.remove();
					break;
				}
			}
			if (plane.isEmpty()) {
				reservations.remove(encounter.reservation.plane);
			}
		}
		return true;
	}

	private boolean removeParticipantInternal(ScriptEncounter encounter,
			Player player) {
		if (!encounter.participants.containsKey(player)) {
			return false;
		}
		releasePlayerLocks(encounter, player, LockType.ACTION);
		releasePlayerLocks(encounter, player, LockType.MOVEMENT);
		List<ScriptTaskHandle> ownedTasks =
				encounter.participantTasks.remove(player);
		if (ownedTasks != null) {
			for (ScriptTaskHandle task : ownedTasks) {
				task.cancel();
			}
		}
		clearTargeting(player);
		ScriptCameraSession camera = cameras.get(player);
		if (camera != null) releaseCamera(camera);
		encounter.participants.remove(player);
		removeParticipantIndex(encounter, player);
		return true;
	}

	private void removeForLifecycle(Player player) {
		ParticipantLease lease = participants.get(player);
		if (lease == null || lease.player != player) {
			return;
		}
		ScriptEncounter encounter = encounters.get(lease.encounterToken);
		if (encounter == null || encounter.generation != lease.generation) {
			participants.remove(player, lease);
			return;
		}
		if (encounter.owner == player) {
			closeInternal(encounter);
		} else {
			removeParticipantInternal(encounter, player);
		}
	}

	private void removeParticipantIndex(ScriptEncounter encounter, Player player) {
		ParticipantLease current = participants.get(player);
		if (current != null && current.encounterToken == encounter.token
				&& current.generation == encounter.generation
				&& current.player == player) {
			participants.remove(player);
		}
	}

	private void releaseEncounterLocks(ScriptEncounter encounter,
			LockType type) {
		Map<Player, List<Long>> index = type == LockType.ACTION
				? encounter.actionLocks : encounter.movementLocks;
		List<Long> tokens = new ArrayList<Long>();
		for (List<Long> playerTokens : index.values()) {
			tokens.addAll(playerTokens);
		}
		for (Long token : tokens) {
			releaseLock(token);
		}
		index.clear();
	}

	private void releasePlayerLocks(ScriptEncounter encounter, Player player,
			LockType type) {
		Map<Player, List<Long>> index = type == LockType.ACTION
				? encounter.actionLocks : encounter.movementLocks;
		List<Long> tokens = index.get(player);
		if (tokens == null) {
			return;
		}
		for (Long token : new ArrayList<Long>(tokens)) {
			releaseLock(token);
		}
	}

	private boolean hasLocks(Player player, LockType type) {
		ParticipantLease participant = participants.get(player);
		if (participant == null) {
			return false;
		}
		ScriptEncounter encounter = encounters.get(participant.encounterToken);
		if (encounter == null) {
			return false;
		}
		List<Long> values = (type == LockType.ACTION
				? encounter.actionLocks : encounter.movementLocks).get(player);
		return values != null && !values.isEmpty();
	}

	private ReservationLease reservationAt(int x, int y, int plane) {
		List<ReservationLease> values = reservations.get(plane);
		if (values == null) {
			return null;
		}
		for (ReservationLease value : values) {
			if (value.reservation.contains(x, y, plane)) {
				return value;
			}
		}
		return null;
	}

	private ScriptEncounter openEncounter(long token) {
		ScriptEncounter encounter = encounters.get(token);
		return encounter != null && encounter.open ? encounter : null;
	}

	private int countGeneration(long generation) {
		int count = 0;
		for (ScriptEncounter encounter : encounters.values()) {
			if (encounter.generation == generation && encounter.open) {
				count++;
			}
		}
		return count;
	}

	private static int taskCount(ScriptEncounter encounter) {
		if (encounter == null) {
			return 0;
		}
		int count = encounter.tasks.size();
		for (List<ScriptTaskHandle> owned :
				encounter.participantTasks.values()) {
			count += owned.size();
		}
		return count;
	}

	private static void pruneDoneTasks(ScriptEncounter encounter) {
		if (encounter == null) {
			return;
		}
		Iterator<ScriptTaskHandle> encounterIterator =
				encounter.tasks.iterator();
		while (encounterIterator.hasNext()) {
			if (encounterIterator.next().isDoneInternal()) {
				encounterIterator.remove();
			}
		}
		Iterator<Map.Entry<Player, List<ScriptTaskHandle>>> ownerIterator =
				encounter.participantTasks.entrySet().iterator();
		while (ownerIterator.hasNext()) {
			List<ScriptTaskHandle> owned = ownerIterator.next().getValue();
			Iterator<ScriptTaskHandle> taskIterator = owned.iterator();
			while (taskIterator.hasNext()) {
				if (taskIterator.next().isDoneInternal()) {
					taskIterator.remove();
				}
			}
			if (owned.isEmpty()) {
				ownerIterator.remove();
			}
		}
	}

	private static Player resolveKiller(Player victim) {
		if (victim == null || victim.damageTaken == null) {
			return null;
		}
		Player selected = null;
		int selectedDamage = 0;
		int selectedIndex = Integer.MAX_VALUE;
		int limit = Math.min(victim.damageTaken.length,
				PlayerHandler.players.length);
		for (int index = 0; index < limit; index++) {
			int damage = victim.damageTaken[index];
			Player candidate = PlayerHandler.players[index];
			if (damage <= 0 || candidate == null || candidate == victim
					|| candidate.playerId != index || !candidate.initialized
					|| !candidate.isActive || candidate.disconnected
					|| candidate.isDead) {
				continue;
			}
			if (damage > selectedDamage
					|| damage == selectedDamage && index < selectedIndex) {
				selected = candidate;
				selectedDamage = damage;
				selectedIndex = index;
			}
		}
		return selected;
	}

	private static boolean loaded(int minX, int minY, int maxX, int maxY) {
		try {
			return Region.getRegion(minX, minY) != null
					&& Region.getRegion(minX, maxY) != null
					&& Region.getRegion(maxX, minY) != null
					&& Region.getRegion(maxX, maxY) != null;
		} catch (RuntimeException unavailable) {
			return false;
		}
	}

	static boolean isAuthoritativeLive(Player player,
			boolean requireOutput) {
		if (player == null || player.playerId < 0
				|| player.playerId >= PlayerHandler.players.length) {
			return false;
		}
			return PlayerHandler.players[player.playerId] == player
					&& player.initialized && player.isActive
					&& !player.disconnected && !player.isDead
					&& !player.isTeleporting
					&& player.respawnTimer <= 0 && !player.properLogout
				&& (!requireOutput || player.getOutStream() != null);
	}

	private static boolean isAuthoritativeDeathTransition(Player player) {
		if (player == null || player.playerId < 0
				|| player.playerId >= PlayerHandler.players.length) {
			return false;
		}
		return PlayerHandler.players[player.playerId] == player
				&& player.initialized && player.isActive
				&& !player.disconnected && player.isDead
				&& player.respawnTimer == -6;
	}

	private static String normalizeId(String raw) {
		if (raw == null) {
			return null;
		}
		String normalized = raw.trim().toLowerCase(Locale.ROOT);
		return ID_PATTERN.matcher(normalized).matches() ? normalized : null;
	}

	private static Integer coordinate(double value) {
		return integral(value, 0, 16383);
	}

	private static Integer integral(double value, int min, int max) {
		if (!Double.isFinite(value) || value != Math.rint(value)
				|| value < min || value > max) {
			return null;
		}
		return Integer.valueOf((int) value);
	}

	private static boolean isExecutable(Value callback) {
		if (callback == null) {
			return false;
		}
		try {
			return !callback.isNull() && callback.canExecute();
		} catch (RuntimeException invalidGuestValue) {
			return false;
		}
	}

	private static void clearTargeting(Player player) {
		player.playerIndex = 0;
		player.npcIndex = 0;
		player.followPlayerId = 0;
		player.followNpcId = 0;
	}

	private enum LockType {
		ACTION,
		MOVEMENT
	}

	private static final class DialogueChainLease {
		private final long token;
		private final long generation;
		private final long facadeEpoch;
		private final Player player;

		private DialogueChainLease(long token, long generation,
				long facadeEpoch, Player player) {
			this.token = token;
			this.generation = generation;
			this.facadeEpoch = facadeEpoch;
			this.player = player;
		}
	}

	private static final class DialogueOptionLease {
		private final long token;
		private final long generation;
		private final long facadeEpoch;
		private final Player player;
		private final int offeredCount;

		private DialogueOptionLease(long token, long generation,
				long facadeEpoch, Player player, int offeredCount) {
			this.token = token;
			this.generation = generation;
			this.facadeEpoch = facadeEpoch;
			this.player = player;
			this.offeredCount = offeredCount;
		}
	}

	private static final class ParticipantLease {
		private final long encounterToken;
		private final long generation;
		private final Player player;

		private ParticipantLease(long encounterToken, long generation,
				Player player) {
			this.encounterToken = encounterToken;
			this.generation = generation;
			this.player = player;
		}
	}

	private static final class ReservationLease {
		private final long encounterToken;
		private final long generation;
		private final ScriptEncounterReservation reservation;

		private ReservationLease(long encounterToken, long generation,
				ScriptEncounterReservation reservation) {
			this.encounterToken = encounterToken;
			this.generation = generation;
			this.reservation = reservation;
		}
	}

	private static final class LockLease {
		private final long token;
		private final long encounterToken;
		private final long generation;
		private final Player player;
		private final LockType type;
		private final long expiresAt;

		private LockLease(long token, long encounterToken, long generation,
				Player player, LockType type, long expiresAt) {
			this.token = token;
			this.encounterToken = encounterToken;
			this.generation = generation;
			this.player = player;
			this.type = type;
			this.expiresAt = expiresAt;
		}
	}

	private static final class EncounterIdKey {
		private final long generation;
		private final Player owner;
		private final String id;

		private EncounterIdKey(long generation, Player owner, String id) {
			this.generation = generation;
			this.owner = owner;
			this.id = id;
		}

		@Override
		public boolean equals(Object value) {
			if (!(value instanceof EncounterIdKey)) {
				return false;
			}
			EncounterIdKey other = (EncounterIdKey) value;
			return generation == other.generation && owner == other.owner
					&& id.equals(other.id);
		}

		@Override
		public int hashCode() {
			int result = Long.valueOf(generation).hashCode();
			result = 31 * result + System.identityHashCode(owner);
			return 31 * result + id.hashCode();
		}
	}
}
