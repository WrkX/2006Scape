package com.rs2.script;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.rs2.script.world.ScriptObjectHandle;
import com.rs2.script.world.ScriptEncounterHandle;
import com.rs2.world.WorldObjectResolver;
import com.rs2.world.WorldObjectService;
import com.rs2.world.ObjectHandler;
import com.rs2.GameEngine;
import com.rs2.game.objects.Objects;
import com.rs2.world.clip.Region;
import java.util.ArrayList;

/** Exact expected-shape replacement and encounter cleanup. */
public class ScriptObjectTransactionTest {
	@Test public void globalRotatedWallRestoresAndDeferredMaskBecomesSelected()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(90);
		ObjectHandler handler = new ObjectHandler();
		try {
			Objects lower = new Objects(2213, Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, 0, 0, 0);
			handler.addObject(lower);
			assertTrue(Region.getClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0) != 0);
			assertTrue(Region.getProjectileClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0) != 0);
			ScriptEncounterHandle encounter = Wp5PlayerSupport.scripted(player)
					.beginEncounter("global-wall-mask", Wp5PlayerSupport.X - 3,
							Wp5PlayerSupport.Y - 3, Wp5PlayerSupport.X + 3,
							Wp5PlayerSupport.Y + 3, 0);
			assertTrue(encounter.replaceObject(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, 2213, 0, 0, 2214, 0, 1) != null);
			assertEquals(0, Region.getProjectileClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0));
			Objects deferred = new Objects(2214, Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, 2, 0, 0);
			handler.addObject(deferred);
			assertTrue(encounter.close());
			assertEquals(2214, WorldObjectResolver.resolve(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0).getObjectId());
			assertTrue(Region.getClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0) != 0);
			assertEquals(0, Region.getProjectileClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0));
		} finally {
			WorldObjectService.getInstance().resetForTesting();
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void timedSolidTombstoneRestoresDeferredSelectedMasks()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(90);
		ArrayList<com.rs2.game.objects.Object> previous =
				new ArrayList<com.rs2.game.objects.Object>(GameEngine.objectManager.objects);
		try {
			GameEngine.objectManager.objects.clear();
			new com.rs2.game.objects.Object(2213, Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, 0, 10, -1, 20);
			assertTrue(Region.getClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0) != 0);
			assertTrue(Region.getProjectileClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0) != 0);
			ScriptEncounterHandle encounter = Wp5PlayerSupport.scripted(player)
					.beginEncounter("timed-tombstone-mask", Wp5PlayerSupport.X - 3,
							Wp5PlayerSupport.Y - 3, Wp5PlayerSupport.X + 3,
							Wp5PlayerSupport.Y + 3, 0);
			assertTrue(encounter.removeObject(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, 2213, 10, 0) != null);
			assertEquals(0, Region.getClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0));
			assertEquals(0, Region.getProjectileClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0));
			new com.rs2.game.objects.Object(2214, Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, 1, 10, -1, 20);
			assertTrue(encounter.close());
			assertEquals(2214, WorldObjectResolver.resolve(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0).getObjectId());
			assertTrue(Region.getClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0) != 0);
			assertEquals(0, Region.getProjectileClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0));
		} finally {
			WorldObjectService.getInstance().resetForTesting();
			GameEngine.objectManager.objects.clear();
			GameEngine.objectManager.objects.addAll(previous);
			Wp5PlayerSupport.cleanup(player);
		}
	}
	@Test public void verificationMismatchQuarantinesApplyReservationUntilReset()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(90);
		try {
			WorldObjectService.getInstance().resetForTesting();
			ScriptEncounterHandle encounter = Wp5PlayerSupport.scripted(player)
					.beginEncounter("apply-quarantine", Wp5PlayerSupport.X - 2,
							Wp5PlayerSupport.Y - 2, Wp5PlayerSupport.X + 2,
							Wp5PlayerSupport.Y + 2, 0);
			Region.failNextCollisionVerificationForTesting(false);
			assertTrue(encounter.replaceObject(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, -1, -1, -1, 2213, 10, 0) == null);
			assertEquals(1, Region.quarantinedCollisionCountForTesting());
			assertTrue(encounter.replaceObject(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, -1, -1, -1, 2213, 10, 0) == null);
		} finally {
			WorldObjectService.getInstance().resetForTesting();
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void verificationMismatchQuarantinesRestoreReservationUntilReset()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(90);
		try {
			WorldObjectService.getInstance().resetForTesting();
			Region.addObject(2213, Wp5PlayerSupport.X, Wp5PlayerSupport.Y,
					0, 10, 0, true);
			ScriptEncounterHandle encounter = Wp5PlayerSupport.scripted(player)
					.beginEncounter("restore-quarantine", Wp5PlayerSupport.X - 2,
							Wp5PlayerSupport.Y - 2, Wp5PlayerSupport.X + 2,
							Wp5PlayerSupport.Y + 2, 0);
			ScriptObjectHandle object = encounter.replaceObject(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, 2213, 10, 0, 2214, 10, 0);
			assertTrue(object != null);
			Region.failNextCollisionVerificationForTesting(true);
			boolean rejected = false;
			try { object.remove(); } catch (RuntimeException expected) { rejected = true; }
			assertTrue(rejected);
			assertEquals(1, Region.quarantinedCollisionCountForTesting());
		} finally {
			WorldObjectService.getInstance().resetForTesting();
			Wp5PlayerSupport.cleanup(player);
		}
	}
	@Test public void explicitHandleRemovalDrainsAcceptedGlobalWriter() throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(90);
		try {
			ScriptEncounterHandle encounter = Wp5PlayerSupport.scripted(player)
					.beginEncounter("global-drain", Wp5PlayerSupport.X - 2,
							Wp5PlayerSupport.Y - 2, Wp5PlayerSupport.X + 2,
							Wp5PlayerSupport.Y + 2, 0);
			ScriptObjectHandle overlay = encounter.replaceObject(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, -1, -1, -1, 2213, 10, 0);
			assertTrue(overlay != null);
			ObjectHandler handler = new ObjectHandler();
			Objects global = new Objects(2230, Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, 0, 10, 0);
			handler.addObject(global);
			assertTrue(handler.globalObjects.isEmpty());
			assertTrue(WorldObjectService.getInstance().isDeferredWriter(
					Wp5PlayerSupport.X, Wp5PlayerSupport.Y, 0));
			assertTrue(overlay.remove());
			assertTrue(handler.globalObjects.contains(global));
			assertEquals(2230, WorldObjectResolver.resolve(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0).getObjectId());
			assertTrue(encounter.close());
		} finally {
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void overlappingEqualCollisionContributorSurvivesReplacement()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(90);
		try {
			Region.addObject(2213, Wp5PlayerSupport.X, Wp5PlayerSupport.Y,
					0, 10, 0, true);
			Region.addObject(2213, Wp5PlayerSupport.X + 1, Wp5PlayerSupport.Y,
					0, 10, 0, true);
			int shared = Region.getClipping(Wp5PlayerSupport.X + 1,
					Wp5PlayerSupport.Y, 0);
			ScriptEncounterHandle encounter = Wp5PlayerSupport.scripted(player)
					.beginEncounter("overlap-ledger", Wp5PlayerSupport.X - 3,
							Wp5PlayerSupport.Y - 3, Wp5PlayerSupport.X + 4,
							Wp5PlayerSupport.Y + 3, 0);
			ScriptObjectHandle replacement = encounter.replaceObject(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, 2213, 10, 0, 2214, 10, 0);
			assertTrue(replacement != null);
			assertEquals(shared, Region.getClipping(Wp5PlayerSupport.X + 1,
					Wp5PlayerSupport.Y, 0));
			assertTrue(encounter.close());
			assertEquals(shared, Region.getClipping(Wp5PlayerSupport.X + 1,
					Wp5PlayerSupport.Y, 0));
		} finally {
			Wp5PlayerSupport.cleanup(player);
		}
	}

    @Test public void replaceRequiresExactExpectedShapeAndCleansOnClose() throws Exception {
        com.rs2.game.players.Player player = Wp5PlayerSupport.player(90);
        try {
            ScriptEncounterHandle encounter = Wp5PlayerSupport.scripted(player).beginEncounter("objects",
                    Wp5PlayerSupport.X - 2, Wp5PlayerSupport.Y - 2,
                    Wp5PlayerSupport.X + 2, Wp5PlayerSupport.Y + 2, 0);
            assertTrue(encounter != null);
            ScriptObjectHandle handle = encounter.replaceObject(
                    Wp5PlayerSupport.X, Wp5PlayerSupport.Y, 0,
                    -1, -1, -1, 2213, 10, 0);
            assertTrue(handle != null && handle.isActive());
            assertEquals(2213, WorldObjectResolver.resolve(
                    Wp5PlayerSupport.X, Wp5PlayerSupport.Y, 0).getObjectId());
            assertFalse(encounter.replaceObject(Wp5PlayerSupport.X,
                    Wp5PlayerSupport.Y, 0, 2213, 10, 1, 2214, 10, 0) != null);
            Region.addObject(2230, Wp5PlayerSupport.X, Wp5PlayerSupport.Y,
                    0, 10, 0, false);
            assertTrue(WorldObjectService.getInstance().isDeferredWriter(
                    Wp5PlayerSupport.X, Wp5PlayerSupport.Y, 0));
            assertTrue(encounter.close());
            assertFalse(handle.isActive());
            assertEquals(2230, WorldObjectResolver.resolve(
                    Wp5PlayerSupport.X, Wp5PlayerSupport.Y, 0).getObjectId());
        } finally {
            Wp5PlayerSupport.cleanup(player);
        }
    }

	@Test public void replacementSubtractsLowerMasksAndRestoresEveryUnionCell()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(90);
		try {
			Region.addObject(2213, Wp5PlayerSupport.X, Wp5PlayerSupport.Y,
					0, 10, 0, true);
			int beforeMovement0 = Region.getClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0);
			int beforeMovement1 = Region.getClipping(Wp5PlayerSupport.X + 1,
					Wp5PlayerSupport.Y, 0);
			int beforeProjectile0 = Region.getProjectileClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0);
			int beforeProjectile1 = Region.getProjectileClipping(Wp5PlayerSupport.X + 1,
					Wp5PlayerSupport.Y, 0);
			assertTrue(beforeMovement0 != 0 && beforeMovement1 != 0);
			assertTrue(beforeProjectile0 != 0 && beforeProjectile1 != 0);

			ScriptEncounterHandle encounter = Wp5PlayerSupport.scripted(player)
					.beginEncounter("collision-replace", Wp5PlayerSupport.X - 3,
							Wp5PlayerSupport.Y - 3, Wp5PlayerSupport.X + 3,
							Wp5PlayerSupport.Y + 3, 0);
			ScriptObjectHandle handle = encounter.replaceObject(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, 2213, 10, 0, 2214, 10, 0);
			assertTrue(handle != null && handle.isActive());
			assertEquals(0, Region.getProjectileClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0));
			assertEquals(0, Region.getClipping(Wp5PlayerSupport.X + 1,
					Wp5PlayerSupport.Y, 0));
			assertEquals(0, Region.getProjectileClipping(Wp5PlayerSupport.X + 1,
					Wp5PlayerSupport.Y, 0));
			Region.addObject(2230, Wp5PlayerSupport.X + 1, Wp5PlayerSupport.Y,
					0, 10, 0, false);
			assertTrue(WorldObjectService.getInstance().isDeferredWriter(
					Wp5PlayerSupport.X + 1, Wp5PlayerSupport.Y, 0));
			assertTrue(encounter.close());
			assertEquals(beforeMovement0, Region.getClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0));
			assertEquals(beforeProjectile0, Region.getProjectileClipping(
					Wp5PlayerSupport.X, Wp5PlayerSupport.Y, 0));
			// The deferred secondary-cell writer is applied only after exact rollback.
			assertTrue(Region.getClipping(Wp5PlayerSupport.X + 1,
					Wp5PlayerSupport.Y, 0) != 0);
		} finally {
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void tombstoneRemovesLowerWallAndCloseRestoresDirectionalMasks()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(90);
		try {
			Region.addObject(2213, Wp5PlayerSupport.X, Wp5PlayerSupport.Y,
					0, 0, 0, true);
			int originMovement = Region.getClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0);
			int westMovement = Region.getClipping(Wp5PlayerSupport.X - 1,
					Wp5PlayerSupport.Y, 0);
			int originProjectile = Region.getProjectileClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0);
			ScriptEncounterHandle encounter = Wp5PlayerSupport.scripted(player)
					.beginEncounter("collision-remove", Wp5PlayerSupport.X - 3,
							Wp5PlayerSupport.Y - 3, Wp5PlayerSupport.X + 3,
							Wp5PlayerSupport.Y + 3, 0);
			ScriptObjectHandle rotated = encounter.replaceObject(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, 2213, 0, 0, 2214, 0, 1);
			assertTrue(rotated != null && rotated.isActive());
			assertTrue(Region.getClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0) != 0);
			assertEquals(0, Region.getClipping(Wp5PlayerSupport.X - 1,
					Wp5PlayerSupport.Y, 0));
			assertTrue(Region.getClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y + 1, 0) != 0);
			assertEquals(0, Region.getProjectileClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0));
			ScriptObjectHandle tombstone = encounter.removeObject(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, 2214, 0, 1);
			assertTrue(tombstone != null && tombstone.isActive());
			assertEquals(-1, tombstone.id());
			assertEquals(0, Region.getClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0));
			assertEquals(0, Region.getClipping(Wp5PlayerSupport.X - 1,
					Wp5PlayerSupport.Y, 0));
			assertEquals(0, Region.getProjectileClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0));
			assertTrue(encounter.close());
			assertEquals(originMovement, Region.getClipping(Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0));
			assertEquals(westMovement, Region.getClipping(Wp5PlayerSupport.X - 1,
					Wp5PlayerSupport.Y, 0));
			assertEquals(originProjectile, Region.getProjectileClipping(
					Wp5PlayerSupport.X, Wp5PlayerSupport.Y, 0));
		} finally {
			Wp5PlayerSupport.cleanup(player);
		}
	}
}
