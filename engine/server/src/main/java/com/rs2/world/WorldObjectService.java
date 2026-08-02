package com.rs2.world;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.rs2.GameEngine;
import com.rs2.game.objects.Objects;
import com.rs2.script.world.ScriptObjectHandle;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.world.clip.Region;
import com.rs2.world.clip.Region.ContributorReceipt;
import org.apollo.cache.def.ObjectDefinition;

/**
 * Layered world-object authority used by scripted encounters. Legacy timed,
 * global and cache stores remain projections for unowned content, but all
 * encounter writes are keyed by exact tile identity and version here.
 */
public final class WorldObjectService {

    public enum MutationResult { APPLIED, DEFERRED_BY_RESERVATION, INVALID }

    private static final WorldObjectService INSTANCE = new WorldObjectService();
    private final Map<Tile, OwnedObject> encounterObjects = new HashMap<Tile, OwnedObject>();
    private final Map<ObjectSlot, LayerObject> timedObjects = new HashMap<ObjectSlot, LayerObject>();
    private final Map<ObjectSlot, LayerObject> globalObjects = new HashMap<ObjectSlot, LayerObject>();
	private final Map<ObjectSlot, LayerObject> cacheObjects = new HashMap<ObjectSlot, LayerObject>();
    private final Map<Tile, ArrayDeque<DeferredObjectMutation>> deferredRegionObjects =
            new HashMap<Tile, ArrayDeque<DeferredObjectMutation>>();
	private final Map<Tile, ArrayDeque<DeferredObjectMutation>> quarantinedDeferredObjects =
			new HashMap<Tile, ArrayDeque<DeferredObjectMutation>>();
    private final Map<Tile, OwnedObject> footprintOwners = new HashMap<Tile, OwnedObject>();
    private final Map<Tile, CollisionCell> collisionCells = new HashMap<Tile, CollisionCell>();
    private final Map<Long, List<CollisionContribution>> collisionSnapshots = new HashMap<Long, List<CollisionContribution>>();
    private long nextToken = 1L;
    private long nextVersion = 1L;

    public static WorldObjectService getInstance() { return INSTANCE; }

    public synchronized ResolvedWorldObject resolve(int x, int y, int plane) {
        Tile tile = new Tile(x, y, plane);
        OwnedObject owned = encounterObjects.get(tile);
        if (owned != null) {
            if (owned.tombstone) return null;
            return new ResolvedWorldObject(copy(owned.object), ResolvedWorldObject.Layer.ENCOUNTER,
                    owned.version, owned.token);
        }
        return resolveLower(x, y, plane);
    }

    /** Participant-aware resolver used by packet and rebuild boundaries. */
    public synchronized ResolvedWorldObject resolve(Player player, int x, int y, int plane) {
        Tile tile = new Tile(x, y, plane);
        OwnedObject owned = encounterObjects.get(tile);
        if (owned != null && ScriptEncounterService.getInstance()
                .canObserveOwnedObject(player, owned.encounterToken, x, y, plane)) {
            return owned.tombstone ? null : new ResolvedWorldObject(copy(owned.object),
                    ResolvedWorldObject.Layer.ENCOUNTER, owned.version, owned.token);
        }
        return resolveLower(x, y, plane);
    }

    private ResolvedWorldObject resolveLower(int x, int y, int plane) {
		return resolveLower(x, y, plane, -2, -2, -2);
	}

	private ResolvedWorldObject resolveLower(int x, int y, int plane, int id,
			int type, int rotation) {
		if (id != -2) {
			ResolvedWorldObject selected = resolveLowerSlot(x, y, plane, type);
			return selected != null && selected.matches(id, x, y, plane, type,
					rotation) ? selected : null;
		}
		for (Map<ObjectSlot, LayerObject> layer : java.util.Arrays.asList(
				timedObjects, globalObjects, cacheObjects)) {
			for (int sceneSlot : new int[] { 2, 0, 1, 3 }) {
				ObjectSlot key = new ObjectSlot(x, y, plane, sceneSlot);
				LayerObject value = layer.get(key);
				if (value != null) return resolved(value, key);
			}
		}
		for (int sceneSlot : new int[] { 2, 0, 1, 3 }) {
			ResolvedWorldObject cached = resolveLowerSlot(new ObjectSlot(x, y, plane,
					sceneSlot), representativeType(sceneSlot));
			if (cached != null) return cached;
		}
		return null;
    }

	private ResolvedWorldObject resolveLowerSlot(int x, int y, int plane,
			int type) {
		return resolveLowerSlot(new ObjectSlot(x, y, plane, objectSlot(type)), type);
	}

	private ResolvedWorldObject resolveLowerSlot(ObjectSlot key, int requestedType) {
		LayerObject value = selected(key);
		if (value != null) return resolved(value, key);
        Objects cached;
		try { cached = Region.getObjectAt(key.x, key.y, key.plane, requestedType); }
        catch (RuntimeException unavailable) { cached = null; }
		if (cached == null) return null;
		ContributorReceipt receipt = Region.contributorReceipt(cached);
		if (receipt == null) receipt = Region.registerObjectContributor(cached, true);
		LayerObject cache = new LayerObject(nextToken++, nextVersion++, copy(cached),
				cached, receipt);
		cacheObjects.put(slot(cached), cache);
		return new ResolvedWorldObject(copy(cached), ResolvedWorldObject.Layer.CACHE,
				cache.version, cache.token);
    }

	private ResolvedWorldObject resolved(LayerObject value, ObjectSlot key) {
		ResolvedWorldObject.Layer layer = timedObjects.get(key) == value
				? ResolvedWorldObject.Layer.TIMED : globalObjects.get(key) == value
						? ResolvedWorldObject.Layer.GLOBAL
						: ResolvedWorldObject.Layer.CACHE;
		return new ResolvedWorldObject(copy(value.object), layer, value.version,
				value.token);
	}

	/** Resolves only the selected visible object in the requested scene slot. */
	public synchronized ResolvedWorldObject resolve(Player player, int x, int y,
			int plane, int id, int type, int rotation) {
		ResolvedWorldObject selected = resolveVisibleSlot(player, x, y, plane, type);
		return selected != null && selected.matches(id, x, y, plane, type, rotation)
				? selected : null;
	}

	/** Packet identity has no shape fields. Search selected slots only and fail
	 * closed if the same id is visible in more than one slot. */
	public synchronized ResolvedWorldObject resolvePacketObject(Player player,
			int id, int x, int y, int plane) {
		ResolvedWorldObject match = null;
		for (int sceneSlot : new int[] { 0, 1, 2, 3 }) {
			ResolvedWorldObject candidate = resolveVisibleSlot(player,
					new ObjectSlot(x, y, plane, sceneSlot));
			if (candidate == null || candidate.getObjectId() != id) continue;
			if (match != null) return null;
			match = candidate;
		}
		return match;
	}

	private ResolvedWorldObject resolveVisibleSlot(Player player, int x, int y,
			int plane, int type) {
		return resolveVisibleSlot(player, new ObjectSlot(x, y, plane,
				objectSlot(type)));
	}

	private ResolvedWorldObject resolveVisibleSlot(Player player, ObjectSlot key) {
		OwnedObject owned = encounterObjects.get(key.tile());
		boolean participant = owned != null && ScriptEncounterService.getInstance()
				.canObserveOwnedObject(player, owned.encounterToken, key.x, key.y,
						key.plane);
		if (participant) {
			if (owned.object != null && slot(owned.object).equals(key)) {
				return new ResolvedWorldObject(copy(owned.object),
						ResolvedWorldObject.Layer.ENCOUNTER, owned.version, owned.token);
			}
			if (owned.lowerObject != null && slot(owned.lowerObject).equals(key)) {
				return null;
			}
		}
		return resolveLowerSlot(key, representativeType(key.slot));
	}

    public ScriptObjectHandle replace(long encounterToken, int x, int y,
            int plane, int expectedId, int expectedType, int expectedRotation,
            int replacementId, int replacementType, int replacementRotation) {
        if (!valid(x, y, plane) || !validShape(expectedId, expectedType, expectedRotation)
                || !validShape(replacementId, replacementType, replacementRotation)) return null;
        // Validate ownership before taking this service's monitor. Encounter
        // close takes the inverse (encounter -> object) lock order.
        if (!ScriptEncounterService.getInstance().isObjectWriteAuthorized(
                encounterToken, x, y, plane)) return null;
        if (replacementId >= 0 && !ScriptEncounterService.getInstance()
                .isObjectFootprintAuthorized(encounterToken, x, y, plane,
                        replacementId, replacementRotation, replacementType)) return null;
        synchronized (this) {
        Tile tile = new Tile(x, y, plane);
        OwnedObject previous = encounterObjects.get(tile);
        boolean emptyExpected = expectedId == -1 && expectedType == -1 && expectedRotation == -1;
		int requestedType = emptyExpected ? replacementType : expectedType;
		ResolvedWorldObject current;
		if (previous != null && previous.object != null
				&& objectSlot(previous.object.getObjectType()) == objectSlot(requestedType)) {
			current = new ResolvedWorldObject(copy(previous.object),
					ResolvedWorldObject.Layer.ENCOUNTER, previous.version, previous.token);
		} else if (previous != null && previous.lowerObject != null
				&& objectSlot(previous.lowerObject.getObjectType()) == objectSlot(requestedType)) {
			current = null;
		} else {
			current = resolveLowerSlot(x, y, plane, requestedType);
		}
        if (emptyExpected ? current != null : current == null
                || !current.matches(expectedId, x, y, plane, expectedType, expectedRotation)) return null;
        if (previous != null && previous.encounterToken != encounterToken) return null;
        if (previous != null && previous.object != null
                && !ScriptEncounterService.getInstance().isObjectFootprintAuthorized(
                        encounterToken, x, y, plane, previous.object.getObjectId(),
                        previous.object.getObjectFace(), previous.object.getObjectType())) return null;
        Objects replacement = replacementId == -1 ? null : new Objects(replacementId,
                x, y, plane, replacementRotation, replacementType, 0);
        if (previous != null && previous.tombstone && replacement == null) {
            return new ScriptObjectHandle(this, tile, previous.token, previous.version,
                    encounterToken, null);
        }
        if (current == null && previous == null && replacement == null) return null;
        if (!ScriptEncounterService.getInstance().reserveObjectMutation(encounterToken)) return null;
        long token = nextToken++;
        long version = nextVersion++;
        Objects lower = previous == null
                ? (current == null ? null : current.getObject()) : previous.lowerObject;
		ContributorReceipt lowerReceipt = previous == null
				? receiptFor(current, tile) : previous.lowerReceipt;
        OwnedObject owned = new OwnedObject(encounterToken, token, version, lower,
				lowerReceipt, replacement, replacement == null);
        if (!canReserve(owned, previous)) {
            ScriptEncounterService.getInstance().releaseObjectMutation(encounterToken);
            return null;
        }
        boolean previousRemoved = false;
        boolean installed = false;
        try {
            if (previous != null) {
                removeCollision(previous);
                releaseFootprint(previous);
                previousRemoved = true;
            }
            reserveFootprint(owned);
            addCollision(owned);
            encounterObjects.put(tile, owned);
            installed = true;
        } catch (RuntimeException failure) {
            ScriptEncounterService.getInstance().releaseObjectMutation(encounterToken);
            if (installed || collisionSnapshots.containsKey(Long.valueOf(owned.token))) {
                try { removeCollision(owned); } catch (RuntimeException ignored) { }
            }
            releaseFootprint(owned);
            if (previousRemoved) {
                reserveFootprint(previous);
                addCollision(previous);
            }
            if (previous == null) encounterObjects.remove(tile); else encounterObjects.put(tile, previous);
            return null;
        }
        broadcast(current == null ? null : current.getObject(), replacement, encounterToken);
        if (previous != null) {
            ScriptEncounterService.getInstance().releaseObjectMutation(encounterToken);
        }
        return new ScriptObjectHandle(this, tile, token, version, encounterToken, replacement);
        }
    }

    public ScriptObjectHandle remove(long encounterToken, int x, int y, int plane,
            int expectedId, int expectedType, int expectedRotation) {
        return replace(encounterToken, x, y, plane, expectedId, expectedType,
                expectedRotation, -1, -1, -1);
    }

    public synchronized boolean isActive(ScriptObjectHandle handle) {
        OwnedObject owned = handle == null ? null : encounterObjects.get(handle.tile());
        return owned != null && owned.token == handle.tokenValue()
                && owned.version == handle.versionValue()
                && owned.encounterToken == handle.encounterTokenValue()
                && sameShape(owned.object, handle);
    }

    public boolean removeHandle(ScriptObjectHandle handle) {
        if (handle == null || !ScriptEncounterService.getInstance().isObjectWriteAuthorized(
                handle.encounterTokenValue(), handle.tile().x, handle.tile().y,
                handle.tile().plane)) return false;
        OwnedObject released;
        synchronized (this) {
            if (!isActive(handle)) return false;
            released = encounterObjects.get(handle.tile());
            removeCollision(released);
            releaseFootprint(released);
            drainDeferred(released);
			encounterObjects.remove(handle.tile());
            ResolvedWorldObject lower = resolveLower(handle.tile().x, handle.tile().y,
                    handle.tile().plane);
            broadcast(released.object, lower == null ? null : lower.getObject(),
                    released.encounterToken);
        }
        ScriptEncounterService.getInstance().releaseObjectMutation(released.encounterToken);
        return true;
    }

    public void closeEncounter(long encounterToken) {
        int releasedCount = 0;
        synchronized (this) {
            Iterator<Map.Entry<Tile, OwnedObject>> iterator = encounterObjects.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Tile, OwnedObject> entry = iterator.next();
                if (entry.getValue().encounterToken == encounterToken) {
                    OwnedObject released = entry.getValue();
                    Objects old = released.object;
                    removeCollision(released);
                    releaseFootprint(released);
                    drainDeferred(released);
					iterator.remove();
                    ResolvedWorldObject restored = resolve(entry.getKey().x,
                            entry.getKey().y, entry.getKey().plane);
                    Objects lower = restored == null ? null : restored.getObject();
                    broadcast(old, lower, encounterToken);
                    releasedCount++;
                }
            }
        }
        while (releasedCount-- > 0) {
            ScriptEncounterService.getInstance().releaseObjectMutation(encounterToken);
        }
    }

    public synchronized MutationResult applyTimedAdd(ObjectManager manager,
            com.rs2.game.objects.Object object) {
        if (manager == null || object == null) return MutationResult.INVALID;
        Objects projection = new Objects(object.objectId, object.objectX, object.objectY,
                object.height, object.face, object.type, 0);
        OwnedObject owner = reservationOwner(projection);
        if (owner != null) {
            enqueue(owner, DeferredObjectMutation.timedAdd(nextToken++, nextVersion++,
                    owner, manager, object, projection));
            return MutationResult.DEFERRED_BY_RESERVATION;
        }
        applyTimedAddNow(manager, object, projection, nextToken++, nextVersion++);
        return MutationResult.APPLIED;
    }

	/** Authoritative cache writer used by Region.addObject compatibility API. */
	public synchronized MutationResult applyCacheAdd(Objects object) {
		if (object == null) return MutationResult.INVALID;
		OwnedObject owner = reservationOwner(object);
		if (owner != null) {
			enqueue(owner, DeferredObjectMutation.cache(nextToken++, nextVersion++, owner, object));
			return MutationResult.DEFERRED_BY_RESERVATION;
		}
		return applyCacheNow(object, nextToken++, nextVersion++)
				? MutationResult.APPLIED : MutationResult.INVALID;
	}

	/** Receipt-bearing compatibility writer owning cache state, collision and output. */
	public synchronized MutationResult applyCacheMutation(Objects object) {
		if (object == null) return MutationResult.INVALID;
		ObjectSlot key = slot(object);
		LayerObject prior = cacheObjects.get(key);
		Objects old = prior == null ? null : copy(prior.object);
		boolean visibleBefore = prior != null && selected(key) == prior;
		MutationResult result = applyCacheAdd(object);
		LayerObject after = cacheObjects.get(key);
		boolean visibleAfter = after != null && selected(key) == after;
		if (result == MutationResult.APPLIED && (visibleBefore || visibleAfter)) {
			projectCacheMutation(old, object.getObjectId() < 0 ? null : object);
		}
		return result;
	}

	private void projectCacheMutation(Objects oldObject, Objects newObject) {
		Objects positioned = newObject == null ? oldObject : newObject;
		if (positioned == null) return;
		for (Player player : PlayerHandler.players) if (player != null
				&& shouldProjectLower(player, positioned)) {
			if (oldObject != null) player.getPacketSender().createObjectSpawn(-1,
					oldObject.getObjectX(), oldObject.getObjectY(), oldObject.getObjectHeight(),
					oldObject.getObjectFace(), oldObject.getObjectType());
			if (newObject != null) player.getPacketSender().createObjectSpawn(
					newObject.getObjectId(), newObject.getObjectX(), newObject.getObjectY(),
					newObject.getObjectHeight(), newObject.getObjectFace(),
					newObject.getObjectType());
		}
	}

	/** Lossless cache bootstrap path. Map archives may contain several object
	 * slots on one tile, so loading must not collapse their backing identities. */
	public synchronized void loadCacheObject(Objects object) {
		if (object == null) return;
		ObjectSlot tile = slot(object);
		if (cacheObjects.containsKey(tile)) return;
		LayerObject before = selected(tile);
		ContributorReceipt prior = Region.contributorReceipt(object);
		boolean selected = before == null && (prior == null
				|| Region.isContributorSelected(prior));
		ContributorReceipt receipt = prior == null
				? Region.registerObjectContributor(object, true, selected)
				: Region.replaceObjectContributor(prior, object, true,
						selected || before != null && before.receipt == prior);
		Region.applyCacheLoadBacking(object);
		cacheObjects.put(tile, new LayerObject(
				nextToken++, nextVersion++, copy(object), copy(object), receipt));
	}

    public synchronized MutationResult applyTimedRemove(ObjectManager manager,
            com.rs2.game.objects.Object object) {
        return applyTimedRemove(manager, object, false);
    }

	/** Coordinate-only compatibility path used by legacy callers such as
	 * {@code NpcHandler}. It remains fail-closed across planes and scene slots. */
	synchronized MutationResult applyUniquePendingTimedRemove(ObjectManager manager,
			int x, int y) {
		if (manager == null) return MutationResult.INVALID;
		Map<ObjectSlot, DeferredObjectMutation> latest =
				new HashMap<ObjectSlot, DeferredObjectMutation>();
		Map<ObjectSlot, Tile> origins = new HashMap<ObjectSlot, Tile>();
		for (Map.Entry<Tile, ArrayDeque<DeferredObjectMutation>> entry
				: deferredRegionObjects.entrySet()) {
			for (DeferredObjectMutation mutation : entry.getValue()) {
				if (mutation.layer != DeferredLayer.TIMED
						|| mutation.slot.x != x || mutation.slot.y != y) continue;
				if (!mutation.requestUnchanged()) return MutationResult.INVALID;
				Tile priorOrigin = origins.get(mutation.slot);
				if (priorOrigin != null && !priorOrigin.equals(entry.getKey()))
					return MutationResult.INVALID;
				origins.put(mutation.slot, entry.getKey());
				latest.put(mutation.slot, mutation);
			}
		}
		com.rs2.game.objects.Object candidate = null;
		for (DeferredObjectMutation mutation : latest.values()) {
			if (mutation.objectManager != manager || !mutation.successor.present) continue;
			if (!(mutation.successor.backing instanceof com.rs2.game.objects.Object))
				return MutationResult.INVALID;
			com.rs2.game.objects.Object backing =
					(com.rs2.game.objects.Object) mutation.successor.backing;
			if (candidate != null && candidate != backing) return MutationResult.INVALID;
			candidate = backing;
		}
		return candidate == null ? MutationResult.INVALID
				: applyTimedRemove(manager, candidate);
	}

	public synchronized MutationResult applyTimedRemove(ObjectManager manager,
			int x, int y, int plane, int type, int rotation) {
		if (manager == null) return MutationResult.INVALID;
		Objects request = new Objects(-1, x, y, plane, rotation, type, 0);
		OwnedObject owner = reservationOwner(request);
		com.rs2.game.objects.Object backing = resolveTimedRemoval(owner, manager,
				request, null, true);
		if (backing == null) return MutationResult.INVALID;
		if (owner != null) {
			enqueue(owner, DeferredObjectMutation.timedRemove(nextToken++,
					nextVersion++, owner, manager, backing, request, false));
			return MutationResult.DEFERRED_BY_RESERVATION;
		}
		return applyTimedRemoveNow(manager, backing)
				? MutationResult.APPLIED : MutationResult.INVALID;
	}

    /** Timed expiry uses the same receipt path as an explicit removal, but its
     * accepted output restores the object's configured {@code newId}. */
    public synchronized MutationResult applyTimedExpire(ObjectManager manager,
            com.rs2.game.objects.Object object) {
        return applyTimedRemove(manager, object, true);
    }

    private MutationResult applyTimedRemove(ObjectManager manager,
            com.rs2.game.objects.Object object, boolean expiry) {
        if (manager == null || object == null) return MutationResult.INVALID;
        Objects projection = new Objects(-1, object.objectX, object.objectY,
                object.height, object.face, object.type, 0);
        OwnedObject owner = reservationOwner(projection);
		com.rs2.game.objects.Object backing = resolveTimedRemoval(owner, manager,
				projection, object, false);
		if (backing == null) return MutationResult.INVALID;
        if (owner != null) {
            DeferredKind kind = expiry ? DeferredKind.TIMED_EXPIRE : DeferredKind.TIMED_REMOVE;
			if (!hasDeferredTimed(owner.origin, backing, kind)) enqueue(owner,
                    DeferredObjectMutation.timedRemove(nextToken++, nextVersion++, owner,
							manager, backing, projection, expiry));
            return MutationResult.DEFERRED_BY_RESERVATION;
        }
		return applyTimedRemoveNow(manager, backing)
                ? MutationResult.APPLIED : MutationResult.INVALID;
    }

    public synchronized MutationResult applyGlobalAdd(ObjectHandler handler, Objects object) {
        if (handler == null || object == null || object.getObjectId() < 0) {
            return MutationResult.INVALID;
        }
        OwnedObject owner = reservationOwner(object);
        if (owner != null) {
            enqueue(owner, DeferredObjectMutation.globalAdd(nextToken++, nextVersion++,
                    owner, handler, object));
            return MutationResult.DEFERRED_BY_RESERVATION;
        }
        applyGlobalAddNow(handler, object, nextToken++, nextVersion++);
        return MutationResult.APPLIED;
    }

    public synchronized MutationResult applyGlobalRemove(ObjectHandler handler, Objects object) {
        if (handler == null || object == null) return MutationResult.INVALID;
        OwnedObject owner = reservationOwner(object);
		Objects backing = resolveGlobalRemoval(owner, handler, object);
		if (backing == null) return MutationResult.INVALID;
        if (owner != null) {
            enqueue(owner, DeferredObjectMutation.globalRemove(nextToken++, nextVersion++,
					owner, handler, backing));
            return MutationResult.DEFERRED_BY_RESERVATION;
        }
		return applyGlobalRemoveNow(handler, backing)
                ? MutationResult.APPLIED : MutationResult.INVALID;
    }

    public synchronized boolean isGlobalActive(Objects object) {
        if (object == null) return false;
        LayerObject active = globalObjects.get(slot(object));
        return active != null && sameObject(active.object, object);
    }

    public synchronized boolean isTimedActive(com.rs2.game.objects.Object object) {
        if (object == null) return false;
        LayerObject active = timedObjects.get(slot(object));
        return active != null && active.backing == object;
    }

    public synchronized void resetForTesting() {
        Region.clearScriptObjectCollisions();
        Region.clearObjectCollisionContributors();
        encounterObjects.clear();
        timedObjects.clear();
        globalObjects.clear();
		cacheObjects.clear();
        deferredRegionObjects.clear();
		quarantinedDeferredObjects.clear();
        footprintOwners.clear();
        collisionCells.clear();
        collisionSnapshots.clear();
        nextToken = 1L;
        nextVersion = 1L;
    }

    /** Conservative live collision adapter for encounter-owned solids/walls. */
    public synchronized boolean blocksStep(int x, int y, int plane, int nextX, int nextY) {
        if (blockedCell(x, y, plane) || blockedCell(nextX, nextY, plane)) return true;
        int dx = Integer.compare(nextX, x);
        int dy = Integer.compare(nextY, y);
        int direction = direction(dx, dy);
        if (direction >= 0) {
            try {
                // Region is the engine's authoritative directional mask. The
                // service map below remains a conservative fallback for tests
                // running without initialized region data.
                if (!Region.canMove(x, y, plane, direction)) return true;
            } catch (RuntimeException unavailable) {
                // Fall through to the contributor map below.
            }
        }
        for (OwnedObject owned : encounterObjects.values()) {
            if (owned.tombstone || owned.object == null || owned.object.getObjectHeight() != plane) continue;
            ObjectDefinition definition;
            try { definition = ObjectDefinition.lookup(owned.object.getObjectId()); }
            catch (RuntimeException failure) { continue; }
            if (definition == null || !definition.isSolid()) continue;
            int type = owned.object.getObjectType();
            if (type >= 9) {
                int[] size = owned.object.getObjectSize();
                if (inside(owned.object, x, y, size) || inside(owned.object, nextX, nextY, size)) return true;
            } else if (type >= 0 && type <= 3
                    && wallBlocks(owned.object, x, y, nextX, nextY)) {
                return true;
            }
        }
        return false;
    }

    private static int direction(int dx, int dy) {
        if (dx == -1 && dy == 1) return 0;
        if (dx == 0 && dy == 1) return 1;
        if (dx == 1 && dy == 1) return 2;
        if (dx == -1 && dy == 0) return 3;
        if (dx == 1 && dy == 0) return 4;
        if (dx == -1 && dy == -1) return 5;
        if (dx == 0 && dy == -1) return 6;
        if (dx == 1 && dy == -1) return 7;
        return -1;
    }

    /** Region writers call this before mutating cache state. */
    public synchronized boolean deferRegionObject(Objects object) {
        if (object == null) return false;
        OwnedObject owner = reservationOwner(object);
        if (owner == null) return false;
        enqueue(owner, DeferredObjectMutation.cache(nextToken++, nextVersion++, owner, object));
        return true;
    }

    public synchronized boolean hasEncounterOverlay(int x, int y, int plane) {
        return footprintOwners.containsKey(new Tile(x, y, plane));
    }

    public synchronized boolean isDeferredWriter(int x, int y, int plane) {
        OwnedObject owner = ownedAt(x, y, plane);
        Tile key = owner == null ? new Tile(x, y, plane) : owner.origin;
        ArrayDeque<DeferredObjectMutation> queue = deferredRegionObjects.get(key);
        return queue != null && !queue.isEmpty();
    }

	public synchronized int deferredCountForTesting(int x, int y, int plane) {
		OwnedObject owner = ownedAt(x, y, plane);
		Tile key = owner == null ? new Tile(x, y, plane) : owner.origin;
		ArrayDeque<DeferredObjectMutation> queue = deferredRegionObjects.get(key);
		return queue == null ? 0 : queue.size();
	}

    /** Nonparticipants may use the lower object, but never the encounter overlay. */
    public synchronized boolean canInteract(Player player, int x, int y, int plane) {
        OwnedObject owner = ownedAt(x, y, plane);
        if (owner == null || ScriptEncounterService.getInstance()
                .canObserveOwnedObject(player, owner.encounterToken, owner.origin.x,
                        owner.origin.y, owner.origin.plane)) return true;
        return resolveLower(x, y, plane) != null;
    }

    /** Whether a lower-layer projection should be emitted to this player. */
    public synchronized boolean shouldProjectLower(Player player, Objects object) {
        if (player == null || object == null) return false;
        OwnedObject owner = ownedAt(object.getObjectX(), object.getObjectY(),
                object.getObjectHeight());
        return owner == null || !ScriptEncounterService.getInstance()
                .canObserveOwnedObject(player, owner.encounterToken, owner.origin.x,
                        owner.origin.y, owner.origin.plane);
    }

    /** Adds the participant-only overlay after normal lower-layer rebuilds. */
    public synchronized void rebuildEncounterObjects(Player player) {
        if (player == null || player.getOutStream() == null) return;
        for (OwnedObject owned : encounterObjects.values()) {
            if (player.heightLevel != owned.origin.plane
                    || Math.max(Math.abs(player.absX - owned.origin.x),
                            Math.abs(player.absY - owned.origin.y)) > 60
                    || !ScriptEncounterService.getInstance().canObserveOwnedObject(
                            player, owned.encounterToken, owned.origin.x,
                            owned.origin.y, owned.origin.plane)) continue;
            Objects lower = owned.lowerObject;
            if (lower != null) player.getPacketSender().createObjectSpawn(-1,
                    owned.origin.x, owned.origin.y, owned.origin.plane,
                    lower.getObjectFace(), lower.getObjectType());
            if (owned.object != null) player.getPacketSender().createObjectSpawn(
                    owned.object.getObjectId(), owned.origin.x, owned.origin.y,
                    owned.origin.plane, owned.object.getObjectFace(),
                    owned.object.getObjectType());
        }
    }

	/** One participant-aware snapshot for all dynamic object rebuild output. */
	public synchronized void rebuildObjects(Player player) {
		if (player == null || player.getOutStream() == null) return;
		Map<ObjectSlot, ObjectSlot> slots = new HashMap<ObjectSlot, ObjectSlot>();
		for (ObjectSlot slot : cacheObjects.keySet()) slots.put(slot, slot);
		for (ObjectSlot slot : globalObjects.keySet()) slots.put(slot, slot);
		for (ObjectSlot slot : timedObjects.keySet()) slots.put(slot, slot);
		for (OwnedObject owned : encounterObjects.values()) {
			if (owned.lowerObject != null) slots.put(slot(owned.lowerObject),
					slot(owned.lowerObject));
			if (owned.object != null) slots.put(slot(owned.object), slot(owned.object));
		}
		for (ObjectSlot objectSlot : slots.keySet()) {
			Tile tile = objectSlot.tile();
			if (tile.plane != player.heightLevel
					|| Math.max(Math.abs(player.absX - tile.x),
							Math.abs(player.absY - tile.y)) > 60) continue;
			OwnedObject owned = encounterObjects.get(tile);
			boolean participant = owned != null && ScriptEncounterService.getInstance()
					.canObserveOwnedObject(player, owned.encounterToken,
							tile.x, tile.y, tile.plane);
			boolean lowerSlot = participant && owned.lowerObject != null
					&& objectSlot.equals(slot(owned.lowerObject));
			boolean overlaySlot = participant && owned.object != null
					&& objectSlot.equals(slot(owned.object));
			if (lowerSlot) {
				Objects lower = owned.lowerObject;
				player.getPacketSender().createObjectSpawn(-1, tile.x, tile.y, tile.plane,
						lower.getObjectFace(), lower.getObjectType());
			}
			if (overlaySlot) {
				Objects object = owned.object;
				player.getPacketSender().createObjectSpawn(object.getObjectId(), tile.x,
						tile.y, tile.plane, object.getObjectFace(), object.getObjectType());
			} else if (!lowerSlot) {
				LayerObject resolved = selected(objectSlot);
				if (resolved != null) {
					Objects object = resolved.object;
					player.getPacketSender().createObjectSpawn(object.getObjectId(), tile.x,
							tile.y, tile.plane, object.getObjectFace(), object.getObjectType());
				}
			}
		}
	}

    private OwnedObject ownedAt(int x, int y, int plane) {
        return footprintOwners.get(new Tile(x, y, plane));
    }

    private boolean blockedCell(int x, int y, int plane) {
        CollisionCell cell = collisionCells.get(new Tile(x, y, plane));
        return cell != null && (cell.movementRefs > 0 || cell.projectileRefs > 0);
    }

    private static boolean inside(Objects object, int x, int y, int[] size) {
        return x >= object.getObjectX() && x < object.getObjectX() + size[0]
                && y >= object.getObjectY() && y < object.getObjectY() + size[1];
    }

    private static boolean wallBlocks(Objects object, int x, int y, int nx, int ny) {
        int ox = object.getObjectX();
        int oy = object.getObjectY();
        int face = object.getObjectFace() & 3;
        if (object.getObjectType() == 1 || object.getObjectType() == 3) {
            return (x == ox && y == oy) || (nx == ox && ny == oy);
        }
        if (face == 0 || face == 2) {
            int boundary = face == 0 ? ox : ox + 1;
            return y == oy && ny == oy
                    && ((x == boundary - 1 && nx == boundary)
                    || (x == boundary && nx == boundary - 1));
        }
        int boundary = face == 1 ? oy + 1 : oy;
        return x == ox && nx == ox
                && ((y == boundary - 1 && ny == boundary)
                || (y == boundary && ny == boundary - 1));
    }

    /** Captures the exact movement/projectile contribution for an object. */
    private void addCollision(OwnedObject owned) {
        if (owned == null) return;
        Region.addScriptObjectCollision(owned.token, owned.lowerReceipt, owned.object);
        List<CollisionContribution> snapshot = owned.object == null || !isSolid(owned.object)
                ? new ArrayList<CollisionContribution>() : collisionSnapshot(owned.object);
        collisionSnapshots.put(owned.token, snapshot);
        for (CollisionContribution contribution : snapshot) {
            CollisionCell cell = collisionCells.get(contribution.tile);
            if (cell == null) {
                cell = new CollisionCell();
                collisionCells.put(contribution.tile, cell);
            }
            if (contribution.movementMask != 0) {
                cell.movementRefs++;
                increment(cell.movementContributors, contribution.movementMask);
                cell.movementMask |= contribution.movementMask;
            }
            if (contribution.projectileMask != 0) {
                cell.projectileRefs++;
                increment(cell.projectileContributors, contribution.projectileMask);
                cell.projectileMask |= contribution.projectileMask;
            }
        }
    }

    private void removeCollision(OwnedObject owned) {
        if (owned == null) return;
        Region.removeScriptObjectCollision(owned.token);
        List<CollisionContribution> snapshot = collisionSnapshots.remove(owned.token);
        if (snapshot == null) return;
        for (CollisionContribution contribution : snapshot) {
            CollisionCell cell = collisionCells.get(contribution.tile);
            if (cell == null) continue;
            if (contribution.movementMask != 0) {
                cell.movementRefs = Math.max(0, cell.movementRefs - 1);
                decrement(cell.movementContributors, contribution.movementMask);
                cell.movementMask = union(cell.movementContributors);
            }
            if (contribution.projectileMask != 0) {
                cell.projectileRefs = Math.max(0, cell.projectileRefs - 1);
                decrement(cell.projectileContributors, contribution.projectileMask);
                cell.projectileMask = union(cell.projectileContributors);
            }
            if (cell.movementRefs == 0 && cell.projectileRefs == 0) collisionCells.remove(contribution.tile);
        }
    }

    private boolean canReserve(OwnedObject candidate, OwnedObject previous) {
        for (Tile tile : candidate.footprint) {
            OwnedObject owner = footprintOwners.get(tile);
            if (owner != null && owner != previous) return false;
        }
        return true;
    }

    private void reserveFootprint(OwnedObject owned) {
        for (Tile tile : owned.footprint) {
            OwnedObject prior = footprintOwners.put(tile, owned);
            if (prior != null && prior != owned) {
                for (Tile reserved : owned.footprint) {
                    if (footprintOwners.get(reserved) == owned) footprintOwners.remove(reserved);
                }
                throw new IllegalStateException("object footprint already reserved");
            }
        }
    }

    private void releaseFootprint(OwnedObject owned) {
        if (owned == null) return;
        for (Tile tile : owned.footprint) {
            if (footprintOwners.get(tile) == owned) footprintOwners.remove(tile);
        }
    }

    private static boolean isSolid(Objects object) {
        try {
            ObjectDefinition definition = ObjectDefinition.lookup(object.getObjectId());
            return definition != null && definition.isSolid();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private static List<CollisionContribution> collisionSnapshot(Objects object) {
        List<CollisionContribution> result = new ArrayList<CollisionContribution>();
        int[] size;
        try {
            size = object.getObjectSize();
        } catch (RuntimeException unavailable) {
            // Legacy cache/object ids are not always present in the compact
            // test definitions.  A writer still needs a deterministic origin
            // footprint for reservation/deferral; Region validation remains
            // fail-closed for scripted replacements.
            size = new int[] { 1, 1 };
        }
        int width = Math.max(1, size[0]);
        int length = Math.max(1, size[1]);
        int movementMask = object.getObjectType() >= 9 ? 0xffff
                : 1 << (object.getObjectFace() & 3);
        int projectileMask = object.getObjectType() >= 9 ? 0xffff
                : 1 << ((object.getObjectFace() & 3) + 4);
        if (object.getObjectType() >= 9) {
            for (int dx = 0; dx < width; dx++) {
                for (int dy = 0; dy < length; dy++) {
                    result.add(new CollisionContribution(new Tile(object.getObjectX() + dx,
                            object.getObjectY() + dy, object.getObjectHeight()),
                            movementMask, projectileMask));
                }
            }
        } else {
            int ox = object.getObjectX(), oy = object.getObjectY(), face = object.getObjectFace() & 3;
            result.add(new CollisionContribution(new Tile(ox, oy, object.getObjectHeight()), movementMask, projectileMask));
            if (object.getObjectType() == 0) {
                int dx = face == 0 ? -1 : face == 2 ? 1 : 0;
                int dy = face == 1 ? 1 : face == 3 ? -1 : 0;
                result.add(new CollisionContribution(new Tile(ox + dx, oy + dy, object.getObjectHeight()), movementMask, projectileMask));
            } else if (object.getObjectType() == 1 || object.getObjectType() == 3) {
                int dx = face == 0 || face == 3 ? -1 : 1;
                int dy = face == 0 || face == 1 ? 1 : -1;
                result.add(new CollisionContribution(new Tile(ox + dx, oy + dy, object.getObjectHeight()), movementMask, projectileMask));
            } else if (object.getObjectType() == 2) {
                int dx = face == 0 || face == 3 ? -1 : 1;
                int dy = face == 0 || face == 1 ? 1 : -1;
                result.add(new CollisionContribution(new Tile(ox + dx, oy, object.getObjectHeight()), movementMask, projectileMask));
                result.add(new CollisionContribution(new Tile(ox, oy + dy, object.getObjectHeight()), movementMask, projectileMask));
            }
        }
        return result;
    }

    private OwnedObject reservationOwner(Objects object) {
        OwnedObject owner = footprintOwners.get(new Tile(object.getObjectX(),
                object.getObjectY(), object.getObjectHeight()));
        if (owner == null && object.getObjectId() >= 0) {
            for (CollisionContribution contribution : collisionSnapshot(object)) {
                owner = footprintOwners.get(contribution.tile);
                if (owner != null) break;
            }
        }
        return owner;
    }

	private ContributorReceipt receiptFor(ResolvedWorldObject resolved, Tile tile) {
		if (resolved == null) return null;
		ObjectSlot key = slot(resolved.getObject());
		LayerObject layer = resolved.getLayer() == ResolvedWorldObject.Layer.TIMED
				? timedObjects.get(key)
				: resolved.getLayer() == ResolvedWorldObject.Layer.GLOBAL
						? globalObjects.get(key) : cacheObjects.get(key);
		return layer == null ? null : layer.receipt;
	}

    private void enqueue(OwnedObject owner, DeferredObjectMutation mutation) {
        ArrayDeque<DeferredObjectMutation> queue = deferredRegionObjects.get(owner.origin);
        if (queue == null) {
            queue = new ArrayDeque<DeferredObjectMutation>();
            deferredRegionObjects.put(owner.origin, queue);
        }
		DeferredObjectMutation predecessor = null;
		for (DeferredObjectMutation queued : queue) if (queued.layer == mutation.layer
				&& queued.slot.equals(mutation.slot)) predecessor = queued;
		mutation.predecessor = predecessor == null
				? expectation(mutation.layer, mutation.slot) : predecessor.successor;
		mutation.successor = mutation.successorExpectation();
        queue.addLast(mutation);
    }

	private LayerExpectation expectation(DeferredLayer layer, ObjectSlot key) {
		LayerObject value = layer == DeferredLayer.CACHE ? cacheObjects.get(key)
				: layer == DeferredLayer.GLOBAL ? globalObjects.get(key)
						: timedObjects.get(key);
		return LayerExpectation.live(layer, key, value);
	}

	/** Resolves the current logical global backing without exposing a queued,
	 * mutable backing to compatibility callers. */
	private Objects resolveGlobalRemoval(OwnedObject owner, ObjectHandler handler,
			Objects request) {
		ObjectSlot key = slot(request);
		if (owner != null) {
			ArrayDeque<DeferredObjectMutation> queue =
					deferredRegionObjects.get(owner.origin);
			DeferredObjectMutation latest = null;
			if (queue != null) for (DeferredObjectMutation mutation : queue) {
				if (mutation.layer != DeferredLayer.GLOBAL
						|| !mutation.slot.equals(key)) continue;
				if (mutation.encounterToken != owner.encounterToken
						|| mutation.objectHandler != handler
						|| !mutation.requestUnchanged()) return null;
				latest = mutation;
			}
			if (latest != null) {
				LayerExpectation logical = latest.successor;
				if (!logical.present || !(logical.backing instanceof Objects)
						|| !matchesGlobalRemoval(logical.object, request)) return null;
				return (Objects) logical.backing;
			}
		}
		LayerObject active = globalObjects.get(key);
		if (active == null || !(active.backing instanceof Objects)
				|| request.getObjectId() < 0
				|| !handler.globalObjects.contains(active.backing)
				|| !matchesGlobalRemoval(active.object, request)) return null;
		return (Objects) active.backing;
	}

	private static boolean matchesGlobalRemoval(Objects logical, Objects request) {
		if (logical == null || request == null
				|| logical.getObjectX() != request.getObjectX()
				|| logical.getObjectY() != request.getObjectY()
				|| logical.getObjectHeight() != request.getObjectHeight()
				|| objectSlot(logical.getObjectType())
						!= objectSlot(request.getObjectType())) return false;
		return request.getObjectId() < 0 || sameObject(logical, request);
	}

	/** Exact timed selector for live or virtual FIFO successors. */
	private com.rs2.game.objects.Object resolveTimedRemoval(OwnedObject owner,
			ObjectManager manager, Objects request,
			com.rs2.game.objects.Object requestedBacking, boolean exactShape) {
		ObjectSlot key = slot(request);
		if (owner != null) {
			ArrayDeque<DeferredObjectMutation> queue =
					deferredRegionObjects.get(owner.origin);
			DeferredObjectMutation latest = null;
			if (queue != null) for (DeferredObjectMutation mutation : queue) {
				if (mutation.layer != DeferredLayer.TIMED
						|| !mutation.slot.equals(key)) continue;
				if (mutation.encounterToken != owner.encounterToken
						|| mutation.objectManager != manager
						|| !mutation.requestUnchanged()) return null;
				latest = mutation;
			}
			if (latest != null) {
				LayerExpectation logical = latest.successor;
				if (!logical.present
						|| !(logical.backing instanceof com.rs2.game.objects.Object)
						|| !matchesTimedRemoval(logical.object, request,
								(com.rs2.game.objects.Object) logical.backing,
								requestedBacking, exactShape)) return null;
				return (com.rs2.game.objects.Object) logical.backing;
			}
		}
		LayerObject active = timedObjects.get(key);
		if (active == null
				|| !(active.backing instanceof com.rs2.game.objects.Object)
				|| !manager.objects.contains(active.backing)
				|| !matchesTimedRemoval(active.object, request,
						(com.rs2.game.objects.Object) active.backing,
						requestedBacking, exactShape)) return null;
		return (com.rs2.game.objects.Object) active.backing;
	}

	private static boolean matchesTimedRemoval(Objects logical, Objects request,
			com.rs2.game.objects.Object logicalBacking,
			com.rs2.game.objects.Object requestedBacking, boolean exactShape) {
		if (logical == null || logicalBacking == null || request == null
				|| logical.getObjectX() != request.getObjectX()
				|| logical.getObjectY() != request.getObjectY()
				|| logical.getObjectHeight() != request.getObjectHeight()
				|| objectSlot(logical.getObjectType())
						!= objectSlot(request.getObjectType())) return false;
		if (requestedBacking != null) return logicalBacking == requestedBacking
				&& logical.getObjectId() == requestedBacking.objectId
				&& logical.getObjectType() == requestedBacking.type
				&& logical.getObjectFace() == requestedBacking.face;
		return !exactShape || logical.getObjectType() == request.getObjectType()
				&& logical.getObjectFace() == request.getObjectFace();
	}

	private boolean matchesExpectation(LayerExpectation expected) {
		LayerExpectation live = expectation(expected.layer, expected.slot);
		if (expected.present != live.present) return false;
		if (!expected.present) return true;
		if (expected.token != live.token || expected.version != live.version
				|| !sameObject(expected.object, live.object)
				|| live.receipt == null || !Region.isContributorActive(live.receipt)) {
			return false;
		}
		if (expected.receipt != null && expected.receipt != live.receipt) return false;
		return expected.backing == null || expected.backing == live.backing;
	}

    private boolean hasDeferredTimed(Tile tile, com.rs2.game.objects.Object backing,
            DeferredKind kind) {
        ArrayDeque<DeferredObjectMutation> queue = deferredRegionObjects.get(tile);
        if (queue != null) for (DeferredObjectMutation mutation : queue) {
            if (mutation.kind == kind && mutation.timedBacking == backing) return true;
        }
        queue = quarantinedDeferredObjects.get(tile);
        if (queue != null) for (DeferredObjectMutation mutation : queue) {
            if (mutation.kind == kind && mutation.timedBacking == backing) return true;
        }
        return false;
    }

    private void drainDeferred(OwnedObject released) {
        ArrayDeque<DeferredObjectMutation> queue = deferredRegionObjects.remove(released.origin);
        if (queue == null) return;
        while (!queue.isEmpty()) {
            DeferredObjectMutation mutation = queue.removeFirst();
			if (mutation.kind == DeferredKind.CACHE) {
				if (mutation.encounterToken != released.encounterToken
						|| !mutation.requestUnchanged()
						|| !matchesExpectation(mutation.predecessor)) {
					quarantineDeferred(released.origin, mutation, queue);
					return;
				}
				try {
				ObjectSlot cacheKey = slot(mutation.snapshot);
				LayerObject cachePrior = cacheObjects.get(cacheKey);
				Objects cacheOld = cachePrior == null ? null : copy(cachePrior.object);
				boolean cacheVisibleBefore = cachePrior != null
						&& selected(cacheKey) == cachePrior;
				if (!applyCacheNow(mutation.snapshot, mutation.token, mutation.version))
						throw new IllegalStateException(
						"deferred cache receipt rejected");
				LayerObject cacheAfter = cacheObjects.get(cacheKey);
				boolean cacheVisibleAfter = cacheAfter != null
						&& selected(cacheKey) == cacheAfter;
				if (cacheVisibleBefore || cacheVisibleAfter) projectCacheMutation(
						cacheOld, mutation.snapshot.getObjectId() < 0
								? null : mutation.snapshot);
				} catch (RuntimeException failure) {
					quarantineDeferred(released.origin, mutation, queue);
					return;
				}
				continue;
			}

			List<DeferredObjectMutation> run = new ArrayList<DeferredObjectMutation>();
			run.add(mutation);
			while (!queue.isEmpty() && sameLogicalChain(mutation, queue.peekFirst())) {
				run.add(queue.removeFirst());
			}
			if (!validDeferredRun(released, run)) {
				ArrayDeque<DeferredObjectMutation> suffix =
						new ArrayDeque<DeferredObjectMutation>();
				for (int index = 1; index < run.size(); index++)
					suffix.addLast(run.get(index));
				while (!queue.isEmpty()) suffix.addLast(queue.removeFirst());
				quarantineDeferred(released.origin, run.get(0), suffix);
				return;
			}
			LayerObject before = selected(mutation.slot);
			for (int index = 0; index < run.size(); index++) {
				DeferredObjectMutation current = run.get(index);
				try {
					applyDeferredWithoutOutput(current);
				} catch (RuntimeException failure) {
					projectSelectedTransition(before, selected(mutation.slot));
					ArrayDeque<DeferredObjectMutation> suffix =
							new ArrayDeque<DeferredObjectMutation>();
					for (int rest = index + 1; rest < run.size(); rest++)
						suffix.addLast(run.get(rest));
					while (!queue.isEmpty()) suffix.addLast(queue.removeFirst());
					quarantineDeferred(released.origin, current, suffix);
					return;
				}
			}
			projectSelectedTransition(before, selected(mutation.slot));
        }
    }

	private boolean sameLogicalChain(DeferredObjectMutation first,
			DeferredObjectMutation second) {
		if (first == null || second == null || first.layer == DeferredLayer.CACHE
				|| first.layer != second.layer || !first.slot.equals(second.slot)) {
			return false;
		}
		return first.layer == DeferredLayer.GLOBAL
				? first.objectHandler == second.objectHandler
				: first.objectManager == second.objectManager;
	}

	private boolean validDeferredRun(OwnedObject released,
			List<DeferredObjectMutation> run) {
		for (int index = 0; index < run.size(); index++) {
			DeferredObjectMutation mutation = run.get(index);
			if (mutation.encounterToken != released.encounterToken
					|| !mutation.requestUnchanged()) return false;
			if (index == 0) {
				if (!matchesExpectation(mutation.predecessor)) return false;
			} else if (!sameExpectation(mutation.predecessor,
					run.get(index - 1).successor)) return false;
		}
		return true;
	}

	private static boolean sameExpectation(LayerExpectation first,
			LayerExpectation second) {
		return first != null && second != null && first.layer == second.layer
				&& first.slot.equals(second.slot) && first.present == second.present
				&& first.token == second.token && first.version == second.version
				&& sameObject(first.object, second.object)
				&& first.backing == second.backing && first.receipt == second.receipt;
	}

	private void applyDeferredWithoutOutput(DeferredObjectMutation mutation) {
		switch (mutation.kind) {
		case GLOBAL_ADD:
			applyGlobalAddNow(mutation.objectHandler, mutation.globalBacking,
					mutation.token, mutation.version, false);
			return;
		case GLOBAL_REMOVE:
			if (!applyGlobalRemoveNow(mutation.objectHandler,
					mutation.globalBacking, false)) throw new IllegalStateException(
					"deferred global receipt rejected");
			return;
		case TIMED_ADD:
			applyTimedAddNow(mutation.objectManager, mutation.timedBacking,
					mutation.snapshot, mutation.token, mutation.version, false);
			return;
		case TIMED_REMOVE:
		case TIMED_EXPIRE:
			if (!applyTimedRemoveNow(mutation.objectManager,
					mutation.timedBacking, false)) throw new IllegalStateException(
					"deferred timed removal receipt rejected");
			return;
		default:
			throw new IllegalStateException("unknown deferred object mutation");
		}
	}

	private void quarantineDeferred(Tile tile, DeferredObjectMutation failed,
			ArrayDeque<DeferredObjectMutation> suffix) {
		ArrayDeque<DeferredObjectMutation> quarantined = quarantinedDeferredObjects.get(tile);
		if (quarantined == null) {
			quarantined = new ArrayDeque<DeferredObjectMutation>();
			quarantinedDeferredObjects.put(tile, quarantined);
		}
		quarantined.addLast(failed);
		while (!suffix.isEmpty()) quarantined.addLast(suffix.removeFirst());
	}

	public synchronized int quarantinedDeferredCountForTesting(int x, int y, int plane) {
		ArrayDeque<DeferredObjectMutation> queue = quarantinedDeferredObjects.get(
				new Tile(x, y, plane));
		return queue == null ? 0 : queue.size();
	}

	private boolean applyCacheNow(Objects mutation, long installToken,
			long installVersion) {
		ObjectSlot tile = slot(mutation);
		LayerObject prior = cacheObjects.get(tile);
		if (prior == null) {
			Objects backing = Region.getObjectAt(tile.x, tile.y, tile.plane,
					mutation.getObjectType());
			if (backing != null) {
				ContributorReceipt receipt = Region.contributorReceipt(backing);
				if (receipt == null) receipt = Region.registerObjectContributor(backing, true,
						selected(slot(backing)) == null);
				prior = new LayerObject(nextToken++, nextVersion++, copy(backing),
						backing, receipt);
				cacheObjects.put(tile, prior);
			}
		}
		Objects replacement = mutation.getObjectId() < 0 ? null : mutation;
		if (prior == null && replacement == null) return false;
		LayerObject before = selected(tile);
		boolean wasSelected = before == prior;
		ContributorReceipt receipt = Region.replaceObjectContributor(
				prior == null ? null : prior.receipt, replacement, true, wasSelected);
		Region.applyCacheBackingMutation(prior == null ? null : prior.object, replacement);
		if (replacement == null) cacheObjects.remove(tile);
		else cacheObjects.put(tile, new LayerObject(installToken, installVersion,
				copy(replacement), copy(replacement), receipt));
		if (wasSelected && replacement == null) select(selected(tile));
		return true;
	}

    private void applyGlobalAddNow(ObjectHandler handler, Objects backing,
            long token, long version) {
		applyGlobalAddNow(handler, backing, token, version, true);
	}

	private void applyGlobalAddNow(ObjectHandler handler, Objects backing,
			long token, long version, boolean output) {
		ObjectSlot tile = slot(backing);
        LayerObject prior = globalObjects.get(tile);
		LayerObject before = selected(tile);
		boolean wasSelected = before == prior;
		ContributorReceipt receipt = Region.replaceObjectContributor(
				prior == null ? null : prior.receipt, backing, false, wasSelected);
        if (prior != null) handler.applyAuthoritativeRemove((Objects) prior.backing);
        handler.applyAuthoritativeAdd(backing);
		globalObjects.put(tile, new LayerObject(token, version, copy(backing), backing, receipt));
		if (!wasSelected && selected(tile).receipt == receipt) {
			deselect(before); select(selected(tile));
		}
		if (output) projectSelectedTransition(before, selected(tile));
    }

    private boolean applyGlobalRemoveNow(ObjectHandler handler, Objects backing) {
		return applyGlobalRemoveNow(handler, backing, true);
	}

	private boolean applyGlobalRemoveNow(ObjectHandler handler, Objects backing,
			boolean output) {
		ObjectSlot tile = slot(backing);
        LayerObject active = globalObjects.get(tile);
        if (active == null || active.backing != backing) return false;
		LayerObject before = selected(tile);
		Region.replaceObjectContributor(active.receipt, null, false);
        handler.applyAuthoritativeRemove(backing);
		globalObjects.remove(tile);
		select(selected(tile));
		if (output) projectSelectedTransition(before, selected(tile));
        return true;
    }

    private void applyTimedAddNow(ObjectManager manager,
            com.rs2.game.objects.Object backing, Objects projection,
            long token, long version) {
		applyTimedAddNow(manager, backing, projection, token, version, true);
	}

	private void applyTimedAddNow(ObjectManager manager,
			com.rs2.game.objects.Object backing, Objects projection,
			long token, long version, boolean output) {
		ObjectSlot tile = slot(projection);
        LayerObject prior = timedObjects.get(tile);
		LayerObject before = selected(tile);
		boolean wasSelected = before == prior;
		ContributorReceipt receipt = Region.replaceObjectContributor(
				prior == null ? null : prior.receipt, projection, false, wasSelected);
		if (prior != null) manager.applyAuthoritativeRemove(
                (com.rs2.game.objects.Object) prior.backing);
        manager.applyAuthoritativeAdd(backing);
		timedObjects.put(tile, new LayerObject(token, version, copy(projection), backing, receipt));
		if (!wasSelected) { deselect(before); select(selected(tile)); }
		if (output) projectSelectedTransition(before, selected(tile));
    }

    private boolean applyTimedRemoveNow(ObjectManager manager,
            com.rs2.game.objects.Object backing) {
		return applyTimedRemoveNow(manager, backing, true);
	}

	private boolean applyTimedRemoveNow(ObjectManager manager,
			com.rs2.game.objects.Object backing, boolean output) {
		ObjectSlot tile = slot(backing);
        LayerObject active = timedObjects.get(tile);
        if (active == null || active.backing != backing) return false;
		LayerObject before = selected(tile);
		Region.replaceObjectContributor(active.receipt, null, false);
        manager.applyAuthoritativeRemove(backing);
		timedObjects.remove(tile);
		select(selected(tile));
		if (output) projectSelectedTransition(before, selected(tile));
        return true;
    }

	/** The authoritative mutation owns its selected output. Hidden layer writes
	 * leave the same selected receipt in place and therefore emit nothing. */
	private void projectSelectedTransition(LayerObject before, LayerObject after) {
		if (before == after) return;
		Objects oldObject = before == null ? null : before.object;
		Objects newObject = after == null ? null : after.object;
		Objects positioned = newObject == null ? oldObject : newObject;
		if (positioned == null) return;
		for (Player player : PlayerHandler.players) {
			if (player == null || player.getOutStream() == null
					|| player.heightLevel != positioned.getObjectHeight()
					|| player.distanceToPoint(positioned.getObjectX(),
							positioned.getObjectY()) > 60
					|| !shouldProjectLower(player, positioned)) continue;
			if (oldObject != null) player.getPacketSender().createObjectSpawn(-1,
					oldObject.getObjectX(), oldObject.getObjectY(),
					oldObject.getObjectHeight(), oldObject.getObjectFace(),
					oldObject.getObjectType());
			if (newObject != null) player.getPacketSender().createObjectSpawn(
					newObject.getObjectId(), newObject.getObjectX(),
					newObject.getObjectY(), newObject.getObjectHeight(),
					newObject.getObjectFace(), newObject.getObjectType());
		}
	}

	private LayerObject selected(ObjectSlot slot) {
		LayerObject value = timedObjects.get(slot);
		if (value == null) value = globalObjects.get(slot);
		if (value == null) value = cacheObjects.get(slot);
		return value;
	}

	private static void select(LayerObject value) {
		if (value != null && !Region.isContributorSelected(value.receipt))
			Region.setContributorSelected(value.receipt, true);
	}

	private static void deselect(LayerObject value) {
		if (value != null && Region.isContributorSelected(value.receipt))
			Region.setContributorSelected(value.receipt, false);
	}

    private static boolean valid(int x, int y, int plane) {
        return x >= 0 && x <= 16383 && y >= 0 && y <= 16383 && plane >= 0 && plane <= 3;
    }

    private static boolean validShape(int id, int type, int rotation) {
        if (id == -1) return type == -1 && rotation == -1;
        return id >= 0 && id <= 65535 && type >= 0 && type <= 22
                && rotation >= 0 && rotation <= 3 && hasDefinition(id);
    }

    private static boolean hasDefinition(int id) {
        try {
            ObjectDefinition[] definitions = ObjectDefinition.getDefinitions();
            return definitions != null && id >= 0 && id < definitions.length
                    && definitions[id] != null;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private static void increment(Map<Integer, Integer> counts, int mask) {
        Integer count = counts.get(mask);
        counts.put(mask, count == null ? 1 : count + 1);
    }

    private static void decrement(Map<Integer, Integer> counts, int mask) {
        Integer count = counts.get(mask);
        if (count == null || count <= 1) counts.remove(mask); else counts.put(mask, count - 1);
    }

    private static int union(Map<Integer, Integer> counts) {
        int mask = 0;
        for (Integer contribution : counts.keySet()) mask |= contribution.intValue();
        return mask;
    }

    private static Objects copy(Objects object) {
        return new Objects(object.getObjectId(), object.getObjectX(), object.getObjectY(),
                object.getObjectHeight(), object.getObjectFace(), object.getObjectType(), 0);
    }

    private static boolean sameObject(Objects first, Objects second) {
        if (first == null || second == null) return first == second;
        return first.getObjectId() == second.getObjectId()
                && first.getObjectX() == second.getObjectX()
                && first.getObjectY() == second.getObjectY()
                && first.getObjectHeight() == second.getObjectHeight()
                && first.getObjectFace() == second.getObjectFace()
                && first.getObjectType() == second.getObjectType();
    }

    private static boolean sameShape(Objects object, ScriptObjectHandle handle) {
        if (object == null) return handle.id() == -1 && handle.type() == -1
                && handle.rotation() == -1;
        return object.getObjectId() == handle.id()
                && object.getObjectType() == handle.type()
                && object.getObjectFace() == handle.rotation();
    }

    /** Projects an authoritative mutation to nearby clients without mutating
     * the lower-layer stores. */
    private static void broadcast(Objects oldObject, Objects newObject, long encounterToken) {
        int x = newObject != null ? newObject.getObjectX() : oldObject == null ? -1 : oldObject.getObjectX();
        int y = newObject != null ? newObject.getObjectY() : oldObject == null ? -1 : oldObject.getObjectY();
        int plane = newObject != null ? newObject.getObjectHeight() : oldObject == null ? -1 : oldObject.getObjectHeight();
        if (x < 0 || y < 0 || plane < 0) return;
        for (Player player : PlayerHandler.players) {
            if (player == null || player.heightLevel != plane || player.getOutStream() == null
                    || Math.max(Math.abs(player.absX - x), Math.abs(player.absY - y)) > 60) continue;
            if (!ScriptEncounterService.getInstance().canObserveOwnedObject(
                    player, encounterToken, x, y, plane)) continue;
            if (oldObject != null) player.getPacketSender().createObjectSpawn(-1, x, y,
                    plane, oldObject.getObjectFace(), oldObject.getObjectType());
            if (newObject != null) player.getPacketSender().createObjectSpawn(
                    newObject.getObjectId(), x, y, plane, newObject.getObjectFace(),
                    newObject.getObjectType());
        }
    }

    public static final class Tile {
        public final int x, y, plane;
        public Tile(int x, int y, int plane) { this.x = x; this.y = y; this.plane = plane; }
        @Override public int hashCode() { return ((plane * 16384) + x) * 16384 + y; }
        @Override public boolean equals(Object value) {
            if (!(value instanceof Tile)) return false;
            Tile other = (Tile) value;
            return x == other.x && y == other.y && plane == other.plane;
        }
    }

	/** The four object slots used by the scene protocol. */
	private static final class ObjectSlot {
		final int x, y, plane, slot;
		ObjectSlot(int x, int y, int plane, int slot) {
			this.x = x; this.y = y; this.plane = plane; this.slot = slot;
		}
		Tile tile() { return new Tile(x, y, plane); }
		@Override public int hashCode() { return 31 * tile().hashCode() + slot; }
		@Override public boolean equals(java.lang.Object value) {
			if (!(value instanceof ObjectSlot)) return false;
			ObjectSlot other = (ObjectSlot) value;
			return x == other.x && y == other.y && plane == other.plane
					&& slot == other.slot;
		}
	}

	private static int objectSlot(int type) {
		if (type >= 0 && type <= 3) return 0;
		if (type >= 4 && type <= 8) return 1;
		return type == 22 ? 3 : 2;
	}

	private static int representativeType(int sceneSlot) {
		return sceneSlot == 0 ? 0 : sceneSlot == 1 ? 4 : sceneSlot == 3 ? 22 : 10;
	}

	private static ObjectSlot slot(Objects object) {
		return new ObjectSlot(object.getObjectX(), object.getObjectY(),
				object.getObjectHeight(), objectSlot(object.getObjectType()));
	}

	private static ObjectSlot slot(com.rs2.game.objects.Object object) {
		return new ObjectSlot(object.objectX, object.objectY, object.height,
				objectSlot(object.type));
	}

    private static final class OwnedObject {
        final long encounterToken, token, version;
        final Tile origin;
        final Objects lowerObject;
		final ContributorReceipt lowerReceipt;
        final Objects object;
        final boolean tombstone;
        final List<Tile> footprint;
        OwnedObject(long encounterToken, long token, long version, Objects lowerObject,
				ContributorReceipt lowerReceipt, Objects object, boolean tombstone) {
            this.encounterToken = encounterToken; this.token = token; this.version = version;
            this.lowerObject = lowerObject == null ? null : copy(lowerObject);
			this.lowerReceipt = lowerReceipt;
            this.object = object == null ? null : copy(object); this.tombstone = tombstone;
            Objects positioned = object == null ? lowerObject : object;
            this.origin = new Tile(positioned.getObjectX(), positioned.getObjectY(),
                    positioned.getObjectHeight());
            Map<Tile, Tile> union = new HashMap<Tile, Tile>();
            union.put(origin, origin);
            addFootprint(union, lowerObject);
            addFootprint(union, object);
            this.footprint = new ArrayList<Tile>(union.keySet());
        }
        private static void addFootprint(Map<Tile, Tile> union, Objects value) {
            if (value == null) return;
            for (CollisionContribution contribution : collisionSnapshot(value)) {
                union.put(contribution.tile, contribution.tile);
            }
        }
    }

    private static final class CollisionCell {
        int movementRefs;
        int projectileRefs;
        int movementMask;
        int projectileMask;
        final Map<Integer, Integer> movementContributors = new HashMap<Integer, Integer>();
        final Map<Integer, Integer> projectileContributors = new HashMap<Integer, Integer>();
    }

    private static final class LayerObject {
        final long token, version;
        final Objects object;
        final java.lang.Object backing;
		final ContributorReceipt receipt;
        LayerObject(long token, long version, Objects object, java.lang.Object backing,
				ContributorReceipt receipt) {
            this.token = token; this.version = version;
			this.object = object; this.backing = backing; this.receipt = receipt;
        }
    }

    private enum DeferredKind {
        CACHE, GLOBAL_ADD, GLOBAL_REMOVE, TIMED_ADD, TIMED_REMOVE, TIMED_EXPIRE
    }

	private enum DeferredLayer { CACHE, GLOBAL, TIMED }

	private static final class LayerExpectation {
		final DeferredLayer layer;
		final ObjectSlot slot;
		final boolean present;
		final long token, version;
		final Objects object;
		final java.lang.Object backing;
		final ContributorReceipt receipt;

		private LayerExpectation(DeferredLayer layer, ObjectSlot slot,
				boolean present, long token, long version, Objects object,
				java.lang.Object backing, ContributorReceipt receipt) {
			this.layer = layer; this.slot = slot; this.present = present;
			this.token = token; this.version = version;
			this.object = object == null ? null : copy(object);
			this.backing = backing; this.receipt = receipt;
		}

		static LayerExpectation live(DeferredLayer layer, ObjectSlot slot,
				LayerObject value) {
			return value == null ? absent(layer, slot)
					: new LayerExpectation(layer, slot, true, value.token,
							value.version, value.object, value.backing, value.receipt);
		}

		static LayerExpectation queued(DeferredLayer layer, ObjectSlot slot,
				long token, long version, Objects object,
				java.lang.Object backing) {
			return new LayerExpectation(layer, slot, true, token, version,
					object, backing, null);
		}

		static LayerExpectation absent(DeferredLayer layer, ObjectSlot slot) {
			return new LayerExpectation(layer, slot, false, 0L, 0L, null,
					null, null);
		}
	}

    private static final class DeferredObjectMutation {
		final long token, version, encounterToken;
        final DeferredKind kind;
		final DeferredLayer layer;
		final ObjectSlot slot;
        final Objects snapshot;
        final ObjectHandler objectHandler;
        final ObjectManager objectManager;
        final Objects globalBacking;
        final com.rs2.game.objects.Object timedBacking;
		LayerExpectation predecessor;
		LayerExpectation successor;

        private DeferredObjectMutation(long token, long version, OwnedObject owner,
                DeferredKind kind, Objects snapshot, ObjectHandler objectHandler,
                ObjectManager objectManager, Objects globalBacking,
                com.rs2.game.objects.Object timedBacking) {
            this.token = token; this.version = version;
			this.encounterToken = owner.encounterToken;
            this.kind = kind; this.snapshot = copy(snapshot);
			this.layer = kind == DeferredKind.CACHE ? DeferredLayer.CACHE
					: kind == DeferredKind.GLOBAL_ADD
							|| kind == DeferredKind.GLOBAL_REMOVE
									? DeferredLayer.GLOBAL : DeferredLayer.TIMED;
			this.slot = slot(snapshot);
            this.objectHandler = objectHandler; this.objectManager = objectManager;
            this.globalBacking = globalBacking; this.timedBacking = timedBacking;
        }

        static DeferredObjectMutation cache(long token, long version,
                OwnedObject owner, Objects object) {
            return new DeferredObjectMutation(token, version, owner, DeferredKind.CACHE,
                    object, null, null, null, null);
        }
        static DeferredObjectMutation globalAdd(long token, long version,
                OwnedObject owner, ObjectHandler handler, Objects object) {
            return new DeferredObjectMutation(token, version, owner, DeferredKind.GLOBAL_ADD,
                    object, handler, null, object, null);
        }
        static DeferredObjectMutation globalRemove(long token, long version,
                OwnedObject owner, ObjectHandler handler, Objects object) {
            return new DeferredObjectMutation(token, version, owner, DeferredKind.GLOBAL_REMOVE,
                    object, handler, null, object, null);
        }
        static DeferredObjectMutation timedAdd(long token, long version,
                OwnedObject owner, ObjectManager manager,
                com.rs2.game.objects.Object object, Objects projection) {
            return new DeferredObjectMutation(token, version, owner, DeferredKind.TIMED_ADD,
                    projection, null, manager, null, object);
        }
        static DeferredObjectMutation timedRemove(long token, long version,
                OwnedObject owner, ObjectManager manager,
                com.rs2.game.objects.Object object, Objects projection, boolean expiry) {
            return new DeferredObjectMutation(token, version, owner,
                    expiry ? DeferredKind.TIMED_EXPIRE : DeferredKind.TIMED_REMOVE,
                    projection, null, manager, null, object);
        }
		LayerExpectation successorExpectation() {
			boolean remove = kind == DeferredKind.GLOBAL_REMOVE
					|| kind == DeferredKind.TIMED_REMOVE
					|| kind == DeferredKind.TIMED_EXPIRE
					|| kind == DeferredKind.CACHE && snapshot.getObjectId() < 0;
			if (remove) return LayerExpectation.absent(layer, slot);
			java.lang.Object backing = layer == DeferredLayer.GLOBAL ? globalBacking
					: layer == DeferredLayer.TIMED ? timedBacking : null;
			return LayerExpectation.queued(layer, slot, token, version, snapshot,
					backing);
		}

		boolean requestUnchanged() {
			if (kind == DeferredKind.CACHE) return true;
            if (timedBacking != null) {
                int id = kind == DeferredKind.TIMED_REMOVE || kind == DeferredKind.TIMED_EXPIRE
                        ? -1 : timedBacking.objectId;
				return sameObject(snapshot, new Objects(id, timedBacking.objectX, timedBacking.objectY,
						timedBacking.height, timedBacking.face, timedBacking.type, 0));
            }
			return globalBacking != null && sameObject(snapshot, globalBacking);
        }
    }

    private static final class CollisionContribution {
        final Tile tile;
        final int movementMask;
        final int projectileMask;
        CollisionContribution(Tile tile, int movementMask, int projectileMask) {
            this.tile = tile;
            this.movementMask = movementMask;
            this.projectileMask = projectileMask;
        }
    }

    private WorldObjectService() { }
}
