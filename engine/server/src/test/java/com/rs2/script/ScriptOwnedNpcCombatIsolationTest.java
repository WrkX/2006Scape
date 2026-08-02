package com.rs2.script;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.script.world.ScriptNpcService;
import com.rs2.script.world.ScriptEncounterHandle;
import com.rs2.game.content.combat.npcs.NpcCombat;

/** Production authorization seam checks for owned/unowned NPC combat. */
public class ScriptOwnedNpcCombatIsolationTest {

    private final int slot = 17;
    private ScriptEncounterTestSupport support;

    @Before
    public void setUp() throws Exception {
        support = new ScriptEncounterTestSupport();
    }

    @After
    public void cleanup() {
        if (support != null) {
            try {
                support.close();
            } catch (Exception ignored) {
                // Preserve the primary assertion when fixture cleanup fails.
            }
        }
        NpcHandler.npcs[slot] = null;
        ScriptNpcService.getInstance().resetForTesting();
    }

    @Test
    public void unownedNpcKeepsLegacyAuthorization() {
        Npc npc = new Npc(slot, 153);
        NpcHandler.npcs[slot] = npc;
        assertTrue(ScriptNpcService.getInstance().canAct(npc, null));
        NpcHandler.npcs[slot] = new Npc(slot, 154);
        assertTrue(ScriptNpcService.getInstance().canAct(npc, null));
    }

    @Test
    public void staleOwnedIdentityCannotBeResolvedAfterRemoval() {
        Npc npc = new Npc(slot, 153);
        NpcHandler.npcs[slot] = npc;
        ScriptNpcService.getInstance().resetForTesting();
        assertFalse(ScriptNpcService.getInstance().isOwned(npc));
    }

    @Test
    public void npcRetaliationRejectsAnOutsiderAtTheProductionBoundary() {
        ScriptEncounterTestSupport.TestClient owner =
                support.player(1, 3200, 3200, 0);
        ScriptEncounterTestSupport.TestClient outsider =
                support.player(2, 3200, 3200, 0);
        ScriptEncounterHandle encounter = support.encounter(owner, "combat",
                3200, 3200, 3204, 3204, 0);
        com.rs2.script.world.ScriptNpcHandle handle = encounter.spawnNpc(
                153, 3200, 3200, 0, 20, 5, 10, 10);
        assertTrue(handle != null);
        NpcCombat.attackPlayer(outsider, Integer.parseInt(handle.token()) == 0
                ? 0 : findSlot(handle));
        assertTrue(outsider.underAttackBy2 == 0);
    }

    private int findSlot(com.rs2.script.world.ScriptNpcHandle handle) {
        for (int i = 1; i < NpcHandler.MAX_NPCS; i++) {
            if (NpcHandler.npcs[i] != null
                    && Long.toString(NpcHandler.npcs[i].allocationToken())
                            .equals(handle.token())) {
                return i;
            }
        }
        return 0;
    }
}
