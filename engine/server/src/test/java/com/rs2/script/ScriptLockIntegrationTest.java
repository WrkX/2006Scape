package com.rs2.script;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.rs2.script.world.ScriptLockHandle;
import com.rs2.script.world.ScriptEncounterHandle;

/** Early lock checks prevent movement/action side effects and expire cleanly. */
public class ScriptLockIntegrationTest {
    @Test public void movementLockBlocksScriptMovementButNotHandleRelease() throws Exception {
        com.rs2.game.players.Player player = Wp5PlayerSupport.player(93);
        try {
            ScriptEncounterHandle encounter = Wp5PlayerSupport.scripted(player).beginEncounter("locks",
                    Wp5PlayerSupport.X - 2, Wp5PlayerSupport.Y - 2,
                    Wp5PlayerSupport.X + 2, Wp5PlayerSupport.Y + 2, 0);
            assertTrue(encounter != null);
            ScriptLockHandle lock = Wp5PlayerSupport.scripted(player).getMovement().lock(5);
            assertTrue(lock != null && lock.isActive());
            assertFalse(Wp5PlayerSupport.scripted(player).getMovement().teleport(
                    Wp5PlayerSupport.X + 1, Wp5PlayerSupport.Y, 0));
            assertTrue(lock.release());
            assertFalse(lock.isActive());
            assertTrue(encounter.close());
        } finally {
            Wp5PlayerSupport.cleanup(player);
        }
    }
}
