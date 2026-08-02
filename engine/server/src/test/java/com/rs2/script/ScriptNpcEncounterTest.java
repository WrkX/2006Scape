package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.GameEngine;
import com.rs2.game.items.GroundItem;
import com.rs2.script.snapshot.ScriptNpcSnapshot;
import com.rs2.script.world.ScriptEncounterHandle;
import com.rs2.script.world.ScriptNpcHandle;
import com.rs2.script.world.ScriptNpcService;
import com.rs2.script.ScriptedPosition;

/** Focused production identity/animation checks for encounter NPC support. */
public class ScriptNpcEncounterTest {

    private ScriptEncounterTestSupport support;

    @Before
    public void setUp() throws Exception {
        support = new ScriptEncounterTestSupport();
    }

    @After
    public void tearDown() throws Exception {
        support.close();
    }

    @Test
    public void allocationsAndSnapshotsRemainStableAcrossSlotReuse() {
        Npc first = new Npc(7, 153);
        first.absX = 3200;
        first.absY = 3201;
        first.heightLevel = 0;
        first.MaxHP = 42;
        ScriptNpcSnapshot snapshot = ScriptNpcSnapshot.capture(first);

        Npc replacement = new Npc(7, 154);
        assertNotEquals(first.allocationToken(), replacement.allocationToken());
        first.absX = 3300;
        assertEquals(3200, snapshot.position().x);
        assertEquals(3201, snapshot.position().y);
        assertEquals(42, snapshot.maxHp());
    }

    @Test
    public void requestedAnimationSetsTheNpcUpdateMask() {
        Npc npc = new Npc(8, 153);
        assertTrue(npc.startAnimation(1234, npc.npcId) == 1234);
        assertEquals(1234, npc.animNumber);
        assertTrue(npc.animUpdateRequired);
        assertTrue(npc.updateRequired);
        npc.clearUpdateFlags();
        assertFalse(npc.animUpdateRequired);
    }

    @Test
    public void encounterSpawnRetainsExactHandleAndCleansUp() {
        ScriptEncounterTestSupport.TestClient owner =
                support.player(1, 3200, 3200, 0);
        ScriptEncounterHandle encounter = support.encounter(owner, "npc",
                3200, 3200, 3204, 3204, 0);
        ScriptNpcHandle npc = encounter.spawnNpc(153, 3200, 3200, 0,
                20, 5, 10, 10);
        assertTrue(npc != null);
        assertEquals(153, npc.id());
        assertEquals(20, npc.maxHp());
        assertTrue(npc.animate(123, 0));
        assertTrue(npc.walkTo(3201, 3200));
        assertEquals(3201, npc.position().x);
        Npc queued = null;
        for (int i = 1; i < NpcHandler.MAX_NPCS; i++) {
            if (NpcHandler.npcs[i] != null
                    && Long.toString(NpcHandler.npcs[i].allocationToken())
                            .equals(npc.token())) {
                queued = NpcHandler.npcs[i];
                break;
            }
        }
        assertTrue(queued != null);
        assertEquals(3200, queued.absX);
        assertTrue(queued.processScriptRouteStep());
        assertEquals(3201, queued.absX);
        assertTrue(encounter.close());
        assertEquals(0, npc.hp());
        assertFalse(npc.isAlive());
        assertFalse(npc.animate(124, 0));
    }

    @Test
    public void bridgeRejectsInvalidNumbersAndDoesNotReviveDeadNpc() {
        ScriptEncounterTestSupport.TestClient owner =
                support.player(1, 3200, 3200, 0);
        ScriptEncounterHandle encounter = support.encounter(owner, "bounds",
                3200, 3200, 3204, 3204, 0);
        assertNull(encounter.spawnNpc(Double.NaN, 3200, 3200, 0,
                20, 5, 10, 10));
        ScriptNpcHandle npc = encounter.spawnNpc(153, 3200, 3200, 0,
                20, 5, 10, 10);
        assertTrue(npc != null);
        assertEquals(0, npc.damage(Double.NaN, null));
        assertEquals(20, npc.damage(20, null));
        assertFalse(npc.isAlive());
        assertEquals(0, npc.heal(1));
        assertEquals(0, npc.damage(1, null));
        assertTrue(encounter.close());
        assertEquals(3200, npc.position().x);
        assertEquals(3200, npc.position().y);
    }

    @Test
    public void ownedDeathCallbackRejectsDuplicatesAndCanCloseEncounter()
            throws Exception {
        ScriptEncounterTestSupport.TestClient owner =
                support.player(1, 3200, 3200, 0);
        Context context = support.publishEmpty();
        ScriptEncounterHandle encounter = support.encounter(owner, "callback",
                3200, 3200, 3204, 3204, 0);
        ScriptNpcHandle handle = encounter.spawnNpc(153, 3200, 3200, 0,
                20, 5, 10, 10);
        assertTrue(handle != null);
        Value callback = context.eval("js",
                "(ctx) => { ctx.encounter.close(); }");
        assertTrue(encounter.onNpcDeath(handle, callback));
        assertFalse(encounter.onNpcDeath(handle, callback));
        int slot = -1;
        for (int i = 1; i < NpcHandler.MAX_NPCS; i++) {
            if (NpcHandler.npcs[i] != null
                    && Long.toString(NpcHandler.npcs[i].allocationToken())
                            .equals(handle.token())) {
                slot = i;
                break;
            }
        }
        assertTrue(slot > 0);
        Npc npc = NpcHandler.npcs[slot];
        assertEquals(20, handle.damage(20, null));
        assertTrue(ScriptNpcService.getInstance().beginDeath(npc));
        ScriptNpcService.getInstance().dispatchDeath(npc, null,
                new ScriptedPosition(3200, 3200, 0));
        ScriptNpcService.getInstance().finishDeath(npc);
        assertFalse(encounter.isOpen());
        assertEquals(3200, handle.position().x);
        assertFalse(handle.isAlive());
    }

    @Test
    public void productionDeathSuppressesLegacyDropsAndPreservesKillerSnapshot()
            throws Exception {
        ScriptEncounterTestSupport.TestClient owner =
                support.player(1, 3200, 3200, 0);
        List<GroundItem> previousItems = new ArrayList<GroundItem>(
                GameEngine.itemHandler.items);
        Npc[] previousNpcs = NpcHandler.npcs.clone();
        GameEngine.itemHandler.items.clear();
        Context context = support.publishEmpty();
        context.eval("js", "globalThis.calls=0;globalThis.killer='';"
                + "globalThis.hp=0;globalThis.x=0;");
        ScriptEncounterHandle encounter = support.encounter(owner, "process",
                3200, 3200, 3204, 3204, 0);
        ScriptNpcHandle handle = encounter.spawnNpc(153, 3200, 3200, 0,
                20, 5, 10, 10);
        assertTrue(handle != null);
        Value callback = context.eval("js", "(ctx)=>{calls++;"
                + "killer=ctx.killer ? ctx.killer.getUsername() : '';"
                + "hp=ctx.npc.maxHp();x=ctx.position.x;ctx.encounter.close();}");
        assertTrue(encounter.onNpcDeath(handle, callback));
        int slot = -1;
        for (int i = 1; i < NpcHandler.MAX_NPCS; i++) {
            if (NpcHandler.npcs[i] != null
                    && Long.toString(NpcHandler.npcs[i].allocationToken())
                            .equals(handle.token())) {
                slot = i;
                break;
            }
        }
        assertTrue(slot > 0);
        Npc npc = NpcHandler.npcs[slot];
        try {
            com.rs2.script.ScriptedPlayer killer =
                    new com.rs2.script.ScriptedPlayer(owner);
            assertEquals(20, handle.damage(20, killer));
            NpcHandler handler = new NpcHandler();
            Arrays.fill(NpcHandler.npcs, null);
            NpcHandler.npcs[slot] = npc;
            for (int tick = 0; tick < 6
                    && NpcHandler.npcs[slot] != null; tick++) {
                handler.process();
            }
            assertEquals(1, context.eval("js", "calls").asInt());
            assertEquals("wp3-player-1",
                    context.eval("js", "killer").asString());
            assertEquals(20, context.eval("js", "hp").asInt());
            assertEquals(3200, context.eval("js", "x").asInt());
            assertTrue(GameEngine.itemHandler.items.isEmpty());
            assertFalse(encounter.isOpen());
            assertTrue(NpcHandler.npcs[slot] == null);
            NpcHandler.npcs[slot] = new Npc(slot, 153);
            assertFalse(handle.animate(99, 0));
        } finally {
            System.arraycopy(previousNpcs, 0, NpcHandler.npcs, 0,
                    previousNpcs.length);
            GameEngine.itemHandler.items.clear();
            GameEngine.itemHandler.items.addAll(previousItems);
        }
    }

    @Test
    public void registeredDeathCallbackCanQueueNestedDespawnWithoutSlotReuseDeref()
            throws Exception {
        ScriptEncounterTestSupport.TestClient owner =
                support.player(1, 3200, 3200, 0);
        Npc[] previousNpcs = NpcHandler.npcs.clone();
        Context context = support.publishEmpty();
        context.eval("js", "globalThis.calls=0;globalThis.first=false;"
                + "globalThis.second=false;globalThis.closed=false;");
        ScriptEncounterHandle encounter = support.encounter(owner, "nested",
                3200, 3200, 3204, 3204, 0);
        ScriptNpcHandle handle = encounter.spawnNpc(153, 3200, 3200, 0,
                20, 5, 10, 10);
        assertTrue(handle != null);
        context.getBindings("js").putMember("capturedHandle", handle);
        Value callback = context.eval("js", "(ctx)=>{calls++;"
                + "first=capturedHandle.despawn();"
                + "second=capturedHandle.despawn();"
                + "closed=ctx.encounter.close();}");
        assertTrue(encounter.onNpcDeath(handle, callback));
        int slot = -1;
        for (int i = 1; i < NpcHandler.MAX_NPCS; i++) {
            if (NpcHandler.npcs[i] != null
                    && Long.toString(NpcHandler.npcs[i].allocationToken())
                            .equals(handle.token())) {
                slot = i;
                break;
            }
        }
        assertTrue(slot > 0);
        Npc npc = NpcHandler.npcs[slot];
        try {
            assertEquals(20, handle.damage(20, null));
            NpcHandler handler = new NpcHandler();
            Arrays.fill(NpcHandler.npcs, null);
            NpcHandler.npcs[slot] = npc;
            for (int tick = 0; tick < 6
                    && NpcHandler.npcs[slot] != null; tick++) {
                handler.process();
            }
            assertEquals(1, context.eval("js", "calls").asInt());
            assertTrue(context.eval("js", "first").asBoolean());
            assertTrue(context.eval("js", "second").asBoolean());
            assertTrue(context.eval("js", "closed").asBoolean());
            assertFalse(encounter.isOpen());
            assertNull(NpcHandler.npcs[slot]);

            Npc replacement = new Npc(slot, 153);
            NpcHandler.npcs[slot] = replacement;
            assertFalse(handle.despawn());
            assertTrue(NpcHandler.npcs[slot] == replacement);
        } finally {
            System.arraycopy(previousNpcs, 0, NpcHandler.npcs, 0,
                    previousNpcs.length);
        }
    }
}
