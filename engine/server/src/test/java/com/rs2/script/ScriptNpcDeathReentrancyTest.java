package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.script.world.ScriptNpcService;

/** Guard/FIFO tests exercise the same service used by NpcHandler callbacks. */
public class ScriptNpcDeathReentrancyTest {

    private final int slot = 18;

    @After
    public void cleanup() {
        NpcHandler.npcs[slot] = null;
        ScriptNpcService.getInstance().resetForTesting();
    }

    @Test
    public void recursiveDeathIsGuardedAndDeferredActionsDrainFifo() {
        Npc npc = new Npc(slot, 153);
        NpcHandler.npcs[slot] = npc;
        ScriptNpcService service = ScriptNpcService.getInstance();
        assertTrue(service.beginDeath(npc));
        assertFalse(service.beginDeath(npc));
        final List<Integer> order = new ArrayList<Integer>();
        service.deferOrRun(npc, new Runnable() {
            @Override
            public void run() {
                order.add(1);
            }
        });
        service.deferOrRun(npc, new Runnable() {
            @Override
            public void run() {
                order.add(2);
            }
        });
        assertTrue(service.isDeathGuarded(npc));
        service.finishDeath(npc);
        assertFalse(service.isDeathGuarded(npc));
        assertEquals(2, order.size());
        assertEquals(Integer.valueOf(1), order.get(0));
        assertEquals(Integer.valueOf(2), order.get(1));
    }
}
