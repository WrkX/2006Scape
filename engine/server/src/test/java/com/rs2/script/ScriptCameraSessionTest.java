package com.rs2.script;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.rs2.script.capability.ScriptCameraSession;

/** Camera operations share one lease and release/reset exactly once. */
public class ScriptCameraSessionTest {
    @Test public void cameraSessionComposesAndReleases() throws Exception {
        com.rs2.game.players.Player player = Wp5PlayerSupport.player(94);
        try {
            ScriptCameraSession camera = Wp5PlayerSupport.scripted(player).getPresentation().beginCamera(5);
            assertTrue(camera != null && camera.isActive());
            assertTrue(camera.position(1, 1, 100, 25, 0));
            assertFalse(camera.position(1, 104, 100, 25, 0));
            assertTrue(camera.lookAt(2, 2, 200, 25, 0));
            assertTrue(camera.shake(0, 1, 1, 1));
            assertTrue(camera.release());
            assertFalse(camera.isActive());
            assertFalse(camera.release());
        } finally {
            Wp5PlayerSupport.cleanup(player);
        }
    }
}
