package com.rs2.script.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Value;

import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.players.Player;
import com.rs2.script.EncounterNpcDeathScriptContext;
import com.rs2.script.ScriptExecutor;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.ScriptedPosition;
import com.rs2.script.snapshot.ScriptNpcSnapshot;
import com.rs2.world.clip.PathFinder;

/**
 * Authoritative identity and capability service for encounter-owned NPCs.
 * Legacy NPC code can continue using the slot array; every script operation
 * resolves the slot and then compares the allocation token and backing object.
 */
public final class ScriptNpcService {

    private static final ScriptNpcService INSTANCE = new ScriptNpcService();

    private final Map<Long, OwnedNpc> ownedByToken = new HashMap<Long, OwnedNpc>();
    private final Map<Npc, OwnedNpc> ownedByIdentity =
            new IdentityHashMap<Npc, OwnedNpc>();
    private final Map<Npc, DeathState> deaths =
            new IdentityHashMap<Npc, DeathState>();
    /** Tombstones keep stale slot identities fail-closed after despawn. */
    private final Map<Npc, Boolean> retiredOwned =
            new IdentityHashMap<Npc, Boolean>();

    public static ScriptNpcService getInstance() {
        return INSTANCE;
    }

    private ScriptNpcService() {
    }

    public synchronized ScriptNpcHandle spawn(long encounterToken,
            long generation, ScriptedPlayer owner, int npcId, int x, int y,
            int plane, int hp, int maxHit, int attack, int defence) {
        if (owner == null || !NpcHandler.hasNpcDefinition(npcId) || x < 0 || y < 0
                || x > 16383 || y > 16383 || plane < 0 || plane > 3
                || hp <= 0 || hp > 32767 || maxHit < 0 || maxHit > 32767
                || attack < 0 || attack > 32767 || defence < 0
                || defence > 32767) {
            return null;
        }
        ScriptEncounterService encounters = ScriptEncounterService.getInstance();
        if (!encounters.canSpawnNpc(encounterToken, generation,
                owner.backingPlayer(), owner.facadeEpoch(), x, y, plane)) {
            return null;
        }
        if (!encounters.reserveNpc(encounterToken, generation)) {
            return null;
        }
        int slot = -1;
        for (int i = 1; i < NpcHandler.MAX_NPCS; i++) {
            if (NpcHandler.npcs[i] == null) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            encounters.releaseNpc(encounterToken, generation);
            return null;
        }
        Npc npc = new Npc(slot, npcId);
        npc.absX = x;
        npc.absY = y;
        npc.makeX = x;
        npc.makeY = y;
        npc.heightLevel = plane;
        npc.HP = hp;
        npc.MaxHP = hp;
        npc.maxHit = maxHit;
        npc.attack = attack;
        npc.defence = defence;
        NpcHandler.npcs[slot] = npc;
        OwnedNpc owned = new OwnedNpc(npc, encounterToken, generation,
                owner.backingPlayer(), owner, new ScriptedPosition(x, y, plane));
        ownedByToken.put(Long.valueOf(npc.allocationToken()), owned);
        ownedByIdentity.put(npc, owned);
        return new ScriptNpcHandle(this, npc.allocationToken());
    }

    public synchronized boolean registerDeath(ScriptNpcHandle handle,
            Value callback) {
        OwnedNpc owned = resolve(handle);
        if (owned == null || callback == null || callback.isNull()
                || !callback.canExecute()
                || !ScriptEncounterService.getInstance().isOpenForScript(
                        owned.encounterToken, owned.generation)) {
            return false;
        }
        if (owned.deathCallback != null || owned.deathListener != null
                || !ScriptEncounterService.getInstance()
                        .reserveNpcDeathCallback(owned.encounterToken,
                                owned.generation)) {
            return false;
        }
        owned.deathCallback = callback;
        return true;
    }

    /**
     * Registers a Java-owned death listener for one exact NPC allocation.
     * Only one death handler (guest callback or Java listener) may be
     * registered per allocation.
     */
    public synchronized boolean registerDeath(ScriptNpcHandle handle,
            EncounterDeathListener listener) {
        OwnedNpc owned = resolve(handle);
        if (owned == null || listener == null
                || !ScriptEncounterService.getInstance().isOpenForScript(
                        owned.encounterToken, owned.generation)) {
            return false;
        }
        if (owned.deathCallback != null || owned.deathListener != null
                || !ScriptEncounterService.getInstance()
                        .reserveNpcDeathCallback(owned.encounterToken,
                                owned.generation)) {
            return false;
        }
        owned.deathListener = listener;
        return true;
    }

    public synchronized boolean isOwned(Npc npc) {
        return exact(npc) != null;
    }

    public synchronized boolean isOwnedBy(Npc npc, Player player) {
        OwnedNpc owned = exact(npc);
        if (owned == null || player == null) {
            return false;
        }
        ScriptEncounterService encounters = ScriptEncounterService.getInstance();
        return ScriptEncounterService.isAuthoritativeLive(player, true)
                && player.heightLevel == npc.heightLevel
                && encounters.isParticipant(owned.encounterToken,
                        owned.generation, player)
                && encounters.contains(owned.encounterToken, player.absX,
                        player.absY, player.heightLevel)
                && encounters.contains(owned.encounterToken, npc.absX,
                        npc.absY, npc.heightLevel);
    }

    public synchronized boolean canAct(Npc npc, Player player) {
        OwnedNpc owned = exact(npc);
        return owned == null ? !knownOwned(npc) : isOwnedBy(npc, player);
    }

    public synchronized boolean isExactOwned(Npc npc) {
        return exact(npc) != null;
    }

    public synchronized boolean beginDeath(Npc npc) {
        if (npc == null || deaths.containsKey(npc)) {
            return false;
        }
        deaths.put(npc, new DeathState(npc));
        return true;
    }

    public synchronized boolean isDeathGuarded(Npc npc) {
        return deaths.containsKey(npc);
    }

    public void finishDeath(Npc npc) {
        while (true) {
            Runnable action;
            synchronized (this) {
                DeathState state = deaths.get(npc);
                if (state == null || state.deferred.isEmpty()) {
                    deaths.remove(npc);
                    break;
                }
                // Keep the guard installed while draining so a callback that
                // queues another destructive operation appends at the FIFO
                // tail instead of executing inline.
                action = state.deferred.removeFirst();
            }
            try {
                action.run();
            } catch (RuntimeException ignored) {
                // One failed cleanup must not strand later FIFO actions.
            }
        }
        // Script-owned NPCs do not enter the legacy respawn path.  Despawn
        // only the exact allocation; an index reused by another NPC is left
        // untouched.
        synchronized (this) {
            if (exact(npc) != null) {
                despawnExact(npc);
            }
        }
    }

    public void deferOrRun(Npc npc, Runnable action) {
        boolean deferred = false;
        synchronized (this) {
            DeathState state = deaths.get(npc);
            if (state != null) {
                state.deferred.addLast(action);
                deferred = true;
            }
        }
        if (!deferred) {
            try {
                action.run();
            } catch (RuntimeException ignored) {
                // Lifecycle cleanup is best effort and remains idempotent.
            }
        }
    }

    public void dispatchDeath(Npc npc, Player killer, ScriptedPosition position) {
        dispatchDeath(npc, killer, position, ScriptNpcSnapshot.capture(npc));
    }

    public void dispatchDeath(Npc npc, Player killer, ScriptedPosition position,
            ScriptNpcSnapshot snapshot) {
        OwnedNpc owned;
        Value callback;
        EncounterDeathListener deathListener;
        ScriptEncounterHandle encounterHandle;
        synchronized (this) {
            owned = exact(npc);
            callback = owned == null ? null : owned.deathCallback;
            deathListener = owned == null ? null : owned.deathListener;
            encounterHandle = owned == null ? null
                    : ScriptEncounterService.getInstance().isOpenForScript(
                            owned.encounterToken, owned.generation)
                                    ? ScriptEncounterService.getInstance()
                                            .handleForToken(owned.encounterToken)
                                    : null;
        }
        if (owned == null || encounterHandle == null) {
            return;
        }
        ScriptedPlayer scriptedKiller = killer == null ? null
                : new ScriptedPlayer(killer, owned.generation);
        if (deathListener != null) {
            ScriptNpcHandle handle = new ScriptNpcHandle(this,
                    owned.npc.allocationToken());
            boolean completed;
            try {
                deathListener.onDeath(handle, scriptedKiller, position,
                        encounterHandle);
                completed = true;
            } catch (RuntimeException listenerFailure) {
                System.err.println("[script-npc] encounter death listener "
                        + "threw for token " + owned.encounterToken + ": "
                        + listenerFailure.getMessage());
                completed = false;
            }
            if (!completed) {
                deferOrRun(npc, new Runnable() {
                    @Override
                    public void run() {
                        ScriptEncounterService.getInstance()
                                .close(owned.encounterToken);
                    }
                });
            }
            return;
        }
        if (callback == null) {
            return;
        }
        EncounterNpcDeathScriptContext context =
                new EncounterNpcDeathScriptContext(
                        encounterHandle,
                        snapshot, scriptedKiller, position);
        boolean completed = ScriptExecutor.executeChecked(callback, "encounter",
                Long.toString(owned.encounterToken),
                "encounter-npc-death", context);
        if (!completed) {
            deferOrRun(npc, new Runnable() {
                @Override
                public void run() {
                    ScriptEncounterService.getInstance()
                            .close(owned.encounterToken);
                }
            });
        }
    }

    public void closeEncounter(final long encounterToken) {
        List<Npc> targets = new ArrayList<Npc>();
        synchronized (this) {
            for (OwnedNpc owned : ownedByToken.values()) {
                if (owned.encounterToken == encounterToken) {
                    targets.add(owned.npc);
                }
            }
        }
        for (final Npc npc : targets) {
            deferOrRun(npc, new Runnable() {
                @Override
                public void run() {
                    despawnExact(npc);
                }
            });
        }
    }

    public synchronized void resetForTesting() {
        ownedByToken.clear();
        ownedByIdentity.clear();
        deaths.clear();
        retiredOwned.clear();
    }

    public boolean despawn(ScriptNpcHandle handle) {
        OwnedNpc owned;
        synchronized (this) {
            owned = resolve(handle);
        }
        if (owned == null) {
            return false;
        }
        final Npc npc = owned.npc;
        deferOrRun(npc, new Runnable() {
            @Override
            public void run() {
                despawnExact(npc);
            }
        });
        return true;
    }

    private synchronized void despawnExact(Npc npc) {
        OwnedNpc owned = exact(npc);
        if (owned == null) {
            return;
        }
        if (npc.npcId >= 0 && npc.npcId < NpcHandler.npcs.length
                && NpcHandler.npcs[npc.npcId] == npc) {
            NpcHandler.npcs[npc.npcId] = null;
        }
        ownedByIdentity.remove(npc);
        ownedByToken.remove(Long.valueOf(npc.allocationToken()));
        retiredOwned.put(npc, Boolean.TRUE);
        ScriptEncounterService.getInstance().releaseNpc(owned.encounterToken,
                owned.generation);
    }

    synchronized Npc current(ScriptNpcHandle handle) {
        OwnedNpc owned = resolve(handle);
        return owned == null ? null : owned.npc;
    }

    synchronized OwnedNpc exact(Npc npc) {
        if (npc == null) {
            return null;
        }
        OwnedNpc owned = ownedByIdentity.get(npc);
        if (owned == null || owned.npc != npc
                || owned.npc.allocationToken() != npc.allocationToken()
                || npc.npcId < 0 || npc.npcId >= NpcHandler.npcs.length
                || NpcHandler.npcs[npc.npcId] != npc) {
            return null;
        }
        return owned;
    }

    synchronized OwnedNpc resolve(ScriptNpcHandle handle) {
        if (handle == null) {
            return null;
        }
        OwnedNpc owned = ownedByToken.get(Long.valueOf(handle.tokenValue()));
        return owned == null || owned.npc.allocationToken() != handle.tokenValue()
                ? null : exact(owned.npc);
    }

    synchronized boolean canMutate(OwnedNpc owned, Player actor) {
        return owned != null && exact(owned.npc) == owned
                && !deaths.containsKey(owned.npc)
                && !owned.npc.isDead && !owned.npc.applyDead
                && owned.npc.HP > 0
                && ScriptEncounterService.getInstance().isOpenForScript(
                        owned.encounterToken, owned.generation)
                && isOwnedBy(owned.npc,
                        actor == null ? owned.owner : actor);
    }

    synchronized boolean canMove(OwnedNpc owned, int x, int y, int plane) {
        return findNextMove(owned, x, y, plane) != null;
    }

    synchronized PathFinder.RouteStep findNextMove(OwnedNpc owned, int x,
            int y, int plane) {
        if (!canMutate(owned, null) || plane != owned.npc.heightLevel
                || x < 0 || y < 0 || x > 16383 || y > 16383
                || !ScriptEncounterService.getInstance().contains(
                        owned.encounterToken, x, y, plane)) {
            return null;
        }
        PathFinder.RouteStep next = PathFinder.findNextStep(owned.npc.absX,
                owned.npc.absY, plane, x, y);
        return next != null && ScriptEncounterService.getInstance().contains(
                owned.encounterToken, next.x(), next.y(), next.plane())
                        ? next : null;
    }

    private boolean knownOwned(Npc npc) {
        return npc != null && (ownedByIdentity.containsKey(npc)
                || retiredOwned.containsKey(npc));
    }

    static final class OwnedNpc {
        final Npc npc;
        final long encounterToken;
        final long generation;
        final Player owner;
        final ScriptedPlayer scriptedOwner;
        ScriptedPosition lastPosition;
        Value deathCallback;
        EncounterDeathListener deathListener;

        OwnedNpc(Npc npc, long encounterToken, long generation, Player owner,
                ScriptedPlayer scriptedOwner, ScriptedPosition lastPosition) {
            this.npc = npc;
            this.encounterToken = encounterToken;
            this.generation = generation;
            this.owner = owner;
            this.scriptedOwner = scriptedOwner;
            this.lastPosition = lastPosition;
        }
    }

    /**
     * Java-owned death observer for one exact owned NPC allocation. Runs
     * with the same containment and deferred-cleanup guarantees as the guest
     * callback path.
     */
    public interface EncounterDeathListener {
        void onDeath(ScriptNpcHandle npc, ScriptedPlayer killer,
                ScriptedPosition position, ScriptEncounterHandle encounter);
    }

    private static final class DeathState {
        final Npc npc;
        final ArrayDeque<Runnable> deferred = new ArrayDeque<Runnable>();

        DeathState(Npc npc) {
            this.npc = npc;
        }
    }
}
