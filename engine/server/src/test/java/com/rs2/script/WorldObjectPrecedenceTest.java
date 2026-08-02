package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.world.ResolvedWorldObject;
import com.rs2.world.WorldObjectService;
import com.rs2.script.world.ScriptEncounterHandle;
import com.rs2.game.objects.Objects;
import com.rs2.game.globalworldobjects.GateHandler;
import com.rs2.game.objects.impl.OpenObject;
import com.rs2.GameEngine;
import com.rs2.world.clip.Region;
import java.util.ArrayList;

/** Focused precedence/identity checks for the encounter object layer. */
public class WorldObjectPrecedenceTest {
    @Before public void resetBefore() { WorldObjectService.getInstance().resetForTesting(); }
	@After public void resetAfter() { WorldObjectService.getInstance().resetForTesting(); }

	@Test public void createAnObjectQueuesExactlyOneGlobalMutationUnderReservation()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(89);
		com.rs2.world.ObjectHandler handler = new com.rs2.world.ObjectHandler();
		try {
			ScriptEncounterHandle encounter = Wp5PlayerSupport.scripted(player)
					.beginEncounter("single-writer", 3199, 3199, 3201, 3201, 0);
			assertTrue(encounter.replaceObject(3200, 3200, 0, -1, -1, -1,
					2213, 10, 0) != null);
			handler.createAnObject(2222, 3200, 3200, 0, 0, 10);
			assertEquals(1, WorldObjectService.getInstance()
					.deferredCountForTesting(3200, 3200, 0));
			assertTrue(handler.globalObjects.isEmpty());
			assertTrue(encounter.close());
			assertEquals(1, handler.globalObjects.size());
			assertEquals(ResolvedWorldObject.Layer.GLOBAL,
					WorldObjectService.getInstance().resolve(3200, 3200, 0).getLayer());
			handler.removeObject(handler.globalObjects.get(0));
			assertNull(WorldObjectService.getInstance().resolve(3200, 3200, 0));
		} finally {
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void timedPositiveExpiryProjectsRemovalWithoutCacheCopy()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(89);
		ArrayList<com.rs2.game.objects.Object> previous =
				new ArrayList<com.rs2.game.objects.Object>(GameEngine.objectManager.objects);
		try {
			GameEngine.objectManager.objects.clear();
			new com.rs2.game.objects.Object(2223, 3200, 3200, 0, 0, 10,
					2222, 0);
			Wp5PlayerSupport.recording(player).clearPackets();
			GameEngine.objectManager.process();
			assertNull(WorldObjectService.getInstance().resolve(3200, 3200, 0));
			assertNull(Region.getObjectAt(3200, 3200, 0, 10));
			assertEquals(1, Wp5PlayerSupport.recording(player).flushCount);
		} finally {
			WorldObjectService.getInstance().resetForTesting();
			GameEngine.objectManager.objects.clear();
			GameEngine.objectManager.objects.addAll(previous);
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void globalPositiveExpiryRestoresSelectedCacheWithoutCopy()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(89);
		com.rs2.world.ObjectHandler handler = new com.rs2.world.ObjectHandler();
		try {
			WorldObjectService service = WorldObjectService.getInstance();
			service.applyCacheMutation(new Objects(2213, 3200, 3200, 0, 0, 10, 0));
			Objects expiring = new Objects(2222, 3200, 3200, 0, 1, 10, 1);
			handler.addObject(expiring);
			Wp5PlayerSupport.recording(player).clearPackets();
			handler.process();
			assertTrue(handler.globalObjects.isEmpty());
			assertEquals(2213, service.resolve(3200, 3200, 0).getObjectId());
			assertEquals(2213, Region.getObjectAt(3200, 3200, 0, 10)
					.getObjectId());
			assertEquals(2, Wp5PlayerSupport.recording(player).flushCount);
		} finally {
			WorldObjectService.getInstance().applyCacheMutation(
					new Objects(-1, 3200, 3200, 0, 0, 10, 0));
			WorldObjectService.getInstance().resetForTesting();
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void hiddenGlobalMutationIsPacketSilent() throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(89);
		ArrayList<Objects> previousGlobal = new ArrayList<Objects>(
				GameEngine.objectHandler.globalObjects);
		ArrayList<com.rs2.game.objects.Object> previousTimed =
				new ArrayList<com.rs2.game.objects.Object>(GameEngine.objectManager.objects);
		try {
			GameEngine.objectHandler.globalObjects.clear();
			GameEngine.objectManager.objects.clear();
			new com.rs2.game.objects.Object(2223, 3200, 3200, 0, 0, 10,
					-1, 20);
			Wp5PlayerSupport.recording(player).clearPackets();
			Objects hidden = new Objects(2222, 3200, 3200, 0, 0, 10, 0);
			GameEngine.objectHandler.addObject(hidden);
			GameEngine.objectHandler.removeObject(hidden);
			assertEquals(0, Wp5PlayerSupport.recording(player).flushCount);
			assertEquals(ResolvedWorldObject.Layer.TIMED,
					WorldObjectService.getInstance().resolve(3200, 3200, 0).getLayer());
		} finally {
			WorldObjectService.getInstance().resetForTesting();
			GameEngine.objectHandler.globalObjects.clear();
			GameEngine.objectHandler.globalObjects.addAll(previousGlobal);
			GameEngine.objectManager.objects.clear();
			GameEngine.objectManager.objects.addAll(previousTimed);
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void exactCoordinateRemovalPreservesOtherSceneSlots()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(89);
		ArrayList<Objects> previousGlobal = new ArrayList<Objects>(
				GameEngine.objectHandler.globalObjects);
		ArrayList<com.rs2.game.objects.Object> previousTimed =
				new ArrayList<com.rs2.game.objects.Object>(GameEngine.objectManager.objects);
		try {
			GameEngine.objectHandler.globalObjects.clear();
			GameEngine.objectManager.objects.clear();
			Objects wall = new Objects(2213, 3200, 3200, 0, 0, 0, 0);
			Objects scenery = new Objects(2214, 3200, 3200, 0, 0, 10, 0);
			GameEngine.objectHandler.addObject(wall);
			GameEngine.objectHandler.addObject(scenery);
			GameEngine.objectHandler.removeObject(
					new Objects(-1, 3200, 3200, 0, 0, 0, 0));
			assertFalse(GameEngine.objectHandler.globalObjects.contains(wall));
			assertTrue(GameEngine.objectHandler.globalObjects.contains(scenery));

			com.rs2.game.objects.Object timedWall = new com.rs2.game.objects.Object(
					2213, 3201, 3200, 0, 0, 0, -1, 20);
			com.rs2.game.objects.Object timedScenery = new com.rs2.game.objects.Object(
					2214, 3201, 3200, 0, 0, 10, -1, 20);
			GameEngine.objectManager.removeObject(3201, 3200, 0, 0, 0);
			assertFalse(GameEngine.objectManager.objects.contains(timedWall));
			assertTrue(GameEngine.objectManager.objects.contains(timedScenery));
		} finally {
			WorldObjectService.getInstance().resetForTesting();
			GameEngine.objectHandler.globalObjects.clear();
			GameEngine.objectHandler.globalObjects.addAll(previousGlobal);
			GameEngine.objectManager.objects.clear();
			GameEngine.objectManager.objects.addAll(previousTimed);
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void staleDeferredPredecessorQuarantinesDependentSuffix()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(89);
		com.rs2.world.ObjectHandler handler = new com.rs2.world.ObjectHandler();
		try {
			ScriptEncounterHandle encounter = Wp5PlayerSupport.scripted(player)
					.beginEncounter("stale-chain", 3199, 3199, 3201, 3201, 0);
			assertTrue(encounter.replaceObject(3200, 3200, 0, -1, -1, -1,
					2213, 10, 0) != null);
			Objects first = new Objects(2222, 3200, 3200, 0, 0, 10, 0);
			Objects second = new Objects(2223, 3200, 3200, 0, 0, 10, 0);
			handler.addObject(first);
			handler.addObject(second);
			first.objectId = 2230;
			assertTrue(encounter.close());
			assertEquals(2, WorldObjectService.getInstance()
					.quarantinedDeferredCountForTesting(3200, 3200, 0));
			assertTrue(handler.globalObjects.isEmpty());
		} finally {
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void deferredGlobalChainsDrainOnlyTheirFinalState()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(89);
		com.rs2.game.players.Player observer = Wp5PlayerSupport.additionalPlayer(88);
		com.rs2.world.ObjectHandler handler = new com.rs2.world.ObjectHandler();
		try {
			ScriptEncounterHandle encounter = Wp5PlayerSupport.scripted(player)
					.beginEncounter("global-final-state", 3197, 3197, 3205, 3203, 0);
			com.rs2.script.world.ScriptObjectHandle explicit = encounter.replaceObject(
					3200, 3200, 0, -1, -1, -1, 2213, 10, 0);
			assertTrue(explicit != null);
			assertTrue(encounter.replaceObject(3203, 3200, 0, -1, -1, -1,
					2213, 10, 0) != null);

			Objects removed = new Objects(2222, 3200, 3200, 0, 0, 10, 0);
			handler.addObject(removed);
			handler.removeObject(new Objects(-1, 3200, 3200, 0, 0, 10, 0));

			Objects replaced = new Objects(2222, 3203, 3200, 0, 0, 10, 0);
			Objects replacement = new Objects(2223, 3203, 3200, 0, 1, 10, 0);
			Objects otherSlot = new Objects(2214, 3203, 3200, 0, 2, 0, 0);
			handler.addObject(replaced);
			handler.addObject(replacement);
			handler.removeObject(new Objects(-1, 3203, 3200, 0, 0, 10, 0));
			handler.addObject(otherSlot);

			Wp5PlayerSupport.recording(observer).clearPackets();
			assertTrue(explicit.remove());
			assertTrue(handler.globalObjects.isEmpty());
			assertEquals(0, Wp5PlayerSupport.recording(observer).flushCount);
			assertEquals(0, Region.getClipping(3200, 3200, 0));
			assertEquals(0, Region.getProjectileClipping(3200, 3200, 0));

			Wp5PlayerSupport.recording(observer).clearPackets();
			assertTrue(encounter.close());
			assertEquals(1, handler.globalObjects.size());
			assertTrue(handler.globalObjects.contains(otherSlot));
			assertNull(WorldObjectService.getInstance().resolve(observer,
					3203, 3200, 0, 2223, 10, 1));
			assertEquals(2214, WorldObjectService.getInstance().resolve(observer,
					3203, 3200, 0, 2214, 0, 2).getObjectId());
			assertEquals(1, Wp5PlayerSupport.recording(observer).flushCount);
			assertTrue(Region.getClipping(3203, 3200, 0) != 0);
			assertEquals(0, Region.getProjectileClipping(3203, 3200, 0));
		} finally {
			Wp5PlayerSupport.cleanup(observer);
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void deferredTimedChainsDrainOnlyTheirFinalState()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(89);
		com.rs2.game.players.Player observer = Wp5PlayerSupport.additionalPlayer(88);
		ArrayList<com.rs2.game.objects.Object> previous =
				new ArrayList<com.rs2.game.objects.Object>(GameEngine.objectManager.objects);
		try {
			GameEngine.objectManager.objects.clear();
			ScriptEncounterHandle encounter = Wp5PlayerSupport.scripted(player)
					.beginEncounter("timed-final-state", 3197, 3197, 3205, 3203, 0);
			com.rs2.script.world.ScriptObjectHandle explicit = encounter.replaceObject(
					3200, 3200, 0, -1, -1, -1, 2213, 10, 0);
			assertTrue(explicit != null);
			assertTrue(encounter.replaceObject(3203, 3200, 0, -1, -1, -1,
					2213, 10, 0) != null);

			new com.rs2.game.objects.Object(2222, 3200, 3200, 0, 0, 10, -1, 20);
			// Same coordinate-only overload used by NpcHandler door-support deaths.
			GameEngine.objectManager.removeObject(3200, 3200);

			new com.rs2.game.objects.Object(2222, 3203, 3200, 0, 0, 10, -1, 20);
			new com.rs2.game.objects.Object(2223, 3203, 3200, 0, 1, 10, -1, 20);
			GameEngine.objectManager.removeObject(3203, 3200, 0, 10, 1);
			com.rs2.game.objects.Object otherSlot = new com.rs2.game.objects.Object(
					2214, 3203, 3200, 0, 2, 0, -1, 20);

			Wp5PlayerSupport.recording(observer).clearPackets();
			assertTrue(explicit.remove());
			assertTrue(GameEngine.objectManager.objects.isEmpty());
			assertEquals(0, Wp5PlayerSupport.recording(observer).flushCount);
			assertEquals(0, Region.getClipping(3200, 3200, 0));
			assertEquals(0, Region.getProjectileClipping(3200, 3200, 0));

			Wp5PlayerSupport.recording(observer).clearPackets();
			assertTrue(encounter.close());
			assertEquals(1, GameEngine.objectManager.objects.size());
			assertTrue(GameEngine.objectManager.objects.contains(otherSlot));
			assertNull(WorldObjectService.getInstance().resolve(observer,
					3203, 3200, 0, 2223, 10, 1));
			assertEquals(2214, WorldObjectService.getInstance().resolve(observer,
					3203, 3200, 0, 2214, 0, 2).getObjectId());
			assertEquals(1, Wp5PlayerSupport.recording(observer).flushCount);
			assertTrue(Region.getClipping(3203, 3200, 0) != 0);
			assertEquals(0, Region.getProjectileClipping(3203, 3200, 0));
		} finally {
			WorldObjectService.getInstance().resetForTesting();
			GameEngine.objectManager.objects.clear();
			GameEngine.objectManager.objects.addAll(previous);
			Wp5PlayerSupport.cleanup(observer);
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void coordinateTimedRemovalFailsClosedAcrossSlotsAndPlanes()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(89);
		com.rs2.game.players.Player planeOwner = Wp5PlayerSupport.additionalPlayer(87);
		com.rs2.game.players.Player observer = Wp5PlayerSupport.additionalPlayer(88);
		ArrayList<com.rs2.game.objects.Object> previous =
				new ArrayList<com.rs2.game.objects.Object>(GameEngine.objectManager.objects);
		try {
			GameEngine.objectManager.objects.clear();
			ScriptEncounterHandle planeZero = Wp5PlayerSupport.scripted(player)
					.beginEncounter("coordinate-plane-zero", 3198, 3198, 3202, 3202, 0);
			ScriptEncounterHandle planeOne = Wp5PlayerSupport.scripted(planeOwner)
					.beginEncounter("coordinate-plane-one", 3198, 3198, 3202, 3202, 1);
			assertTrue(planeZero != null && planeOne != null);
			assertTrue(planeZero.replaceObject(3200, 3200, 0, -1, -1, -1,
					2213, 10, 0) != null);
			assertTrue(planeOne.replaceObject(3200, 3200, 1, -1, -1, -1,
					2213, 10, 0) != null);

			new com.rs2.game.objects.Object(2222, 3200, 3200, 0, 0, 10, -1, 20);
			new com.rs2.game.objects.Object(2214, 3200, 3200, 0, 2, 0, -1, 20);
			new com.rs2.game.objects.Object(2223, 3200, 3200, 1, 1, 10, -1, 20);
			GameEngine.objectManager.removeObject(3200, 3200);
			assertEquals(2, WorldObjectService.getInstance()
					.deferredCountForTesting(3200, 3200, 0));
			assertEquals(1, WorldObjectService.getInstance()
					.deferredCountForTesting(3200, 3200, 1));

			GameEngine.objectManager.removeObject(3200, 3200, 0, 0, 2);
			GameEngine.objectManager.removeObject(3200, 3200);
			assertEquals(3, WorldObjectService.getInstance()
					.deferredCountForTesting(3200, 3200, 0));
			assertEquals(1, WorldObjectService.getInstance()
					.deferredCountForTesting(3200, 3200, 1));

			GameEngine.objectManager.removeObject(3200, 3200, 1, 10, 1);
			GameEngine.objectManager.removeObject(3200, 3200);
			assertEquals(4, WorldObjectService.getInstance()
					.deferredCountForTesting(3200, 3200, 0));
			assertEquals(2, WorldObjectService.getInstance()
					.deferredCountForTesting(3200, 3200, 1));

			Wp5PlayerSupport.recording(observer).clearPackets();
			assertTrue(planeZero.close());
			assertTrue(planeOne.close());
			assertTrue(GameEngine.objectManager.objects.isEmpty());
			assertNull(WorldObjectService.getInstance().resolve(3200, 3200, 0));
			assertNull(WorldObjectService.getInstance().resolve(3200, 3200, 1));
			assertEquals(0, Region.getClipping(3200, 3200, 0));
			assertEquals(0, Region.getProjectileClipping(3200, 3200, 0));
			assertEquals(0, Region.getClipping(3200, 3200, 1));
			assertEquals(0, Region.getProjectileClipping(3200, 3200, 1));
			assertEquals(2, Wp5PlayerSupport.recording(observer).flushCount);
		} finally {
			WorldObjectService.getInstance().resetForTesting();
			GameEngine.objectManager.objects.clear();
			GameEngine.objectManager.objects.addAll(previous);
			Wp5PlayerSupport.cleanup(observer);
			Wp5PlayerSupport.cleanup(planeOwner);
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void gateOpenObjectAndCannonUseAuthoritativeCacheWriter()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(89);
		try {
			new GateHandler().spawnGate(player, 2213, 3200, 3200, 0, 0);
			assertEquals(2213, WorldObjectService.getInstance()
					.resolve(3200, 3200, 0).getObjectId());
			player.objectX = 3201;
			player.objectY = 3200;
			OpenObject.interactObject(player, 375);
			assertEquals(378, WorldObjectService.getInstance()
					.resolve(3201, 3200, 0).getObjectId());
			player.getCannon().placeObject(2214, 3202, 3200, true);
			assertEquals(2214, WorldObjectService.getInstance()
					.resolve(3202, 3200, 0).getObjectId());
		} finally {
			WorldObjectService.getInstance().applyCacheMutation(
					new Objects(-1, 3200, 3200, 0, 0, 0, 0));
			WorldObjectService.getInstance().applyCacheMutation(
					new Objects(-1, 3201, 3200, 0, 0, 10, 0));
			WorldObjectService.getInstance().applyCacheMutation(
					new Objects(-1, 3202, 3200, 0, 0, 10, 0));
			WorldObjectService.getInstance().resetForTesting();
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void cacheGlobalAndTimedReceiptsExpireThroughAcceptedOutputPath()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(89);
		ArrayList<Objects> previousGlobal = new ArrayList<Objects>(
				GameEngine.objectHandler.globalObjects);
		ArrayList<com.rs2.game.objects.Object> previousTimed =
				new ArrayList<com.rs2.game.objects.Object>(GameEngine.objectManager.objects);
		try {
			WorldObjectService service = WorldObjectService.getInstance();
			service.resetForTesting();
			GameEngine.objectHandler.globalObjects.clear();
			GameEngine.objectManager.objects.clear();
			Region.addObject(2213, 3200, 3200, 0, 10, 0, true);
			Objects global = new Objects(2222, 3200, 3200, 0, 0, 10, 0);
			GameEngine.objectHandler.addObject(global);
			com.rs2.game.objects.Object timed = new com.rs2.game.objects.Object(
					2223, 3200, 3200, 0, 0, 10, 2222, 0);
			assertEquals(ResolvedWorldObject.Layer.TIMED,
					service.resolve(3200, 3200, 0).getLayer());
			Wp5PlayerSupport.recording(player).clearPackets();
			GameEngine.objectManager.process();
			assertEquals(ResolvedWorldObject.Layer.GLOBAL,
					service.resolve(3200, 3200, 0).getLayer());
			assertEquals(2, Wp5PlayerSupport.recording(player).flushCount);

			Wp5PlayerSupport.recording(player).clearPackets();
			Objects unauthorized = new Objects(-1, 3200, 3200, 0, 0, 10, 0);
			assertEquals(WorldObjectService.MutationResult.INVALID,
					service.applyGlobalRemove(GameEngine.objectHandler, unauthorized));
			assertEquals(0, Wp5PlayerSupport.recording(player).flushCount);
			GameEngine.objectHandler.removeObject(global);
			assertEquals(2, Wp5PlayerSupport.recording(player).flushCount);
			assertEquals(ResolvedWorldObject.Layer.CACHE,
					service.resolve(3200, 3200, 0).getLayer());
		} finally {
			WorldObjectService.getInstance().resetForTesting();
			GameEngine.objectHandler.globalObjects.clear();
			GameEngine.objectHandler.globalObjects.addAll(previousGlobal);
			GameEngine.objectManager.objects.clear();
			GameEngine.objectManager.objects.addAll(previousTimed);
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void sameEncounterOverlayReplacementRetainsDeferredFifoOrder()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(89);
		ArrayList<Objects> previousGlobal = new ArrayList<Objects>(
				GameEngine.objectHandler.globalObjects);
		ArrayList<com.rs2.game.objects.Object> previousTimed =
				new ArrayList<com.rs2.game.objects.Object>(GameEngine.objectManager.objects);
		try {
			WorldObjectService service = WorldObjectService.getInstance();
			service.resetForTesting();
			GameEngine.objectHandler.globalObjects.clear();
			GameEngine.objectManager.objects.clear();
			ScriptEncounterHandle encounter = Wp5PlayerSupport.scripted(player)
					.beginEncounter("overlay-fifo", 3199, 3199, 3201, 3201, 0);
			assertTrue(encounter.replaceObject(3200, 3200, 0, -1, -1, -1,
					2213, 10, 0) != null);
			Objects global = new Objects(2222, 3200, 3200, 0, 0, 10, 0);
			GameEngine.objectHandler.addObject(global);
			assertTrue(encounter.replaceObject(3200, 3200, 0, 2213, 10, 0,
					2214, 10, 0) != null);
			com.rs2.game.objects.Object timed = new com.rs2.game.objects.Object(
					2223, 3200, 3200, 0, 0, 10, 2222, 10);
			assertTrue(GameEngine.objectHandler.globalObjects.isEmpty());
			assertTrue(GameEngine.objectManager.objects.isEmpty());
			assertTrue(encounter.close());
			assertTrue(GameEngine.objectHandler.globalObjects.contains(global));
			assertTrue(GameEngine.objectManager.objects.contains(timed));
			assertEquals(ResolvedWorldObject.Layer.TIMED,
					service.resolve(3200, 3200, 0).getLayer());
		} finally {
			WorldObjectService.getInstance().resetForTesting();
			GameEngine.objectHandler.globalObjects.clear();
			GameEngine.objectHandler.globalObjects.addAll(previousGlobal);
			GameEngine.objectManager.objects.clear();
			GameEngine.objectManager.objects.addAll(previousTimed);
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void bootstrapCacheDoesNotProjectOnRegionRebuild()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(89);
		try {
			WorldObjectService service = WorldObjectService.getInstance();
			service.loadCacheObject(new Objects(1276, 3200, 3200, 0, 0, 10, 0));
			Wp5PlayerSupport.recording(player).clearPackets();
			service.rebuildObjects(player);
			assertEquals(0, Wp5PlayerSupport.recording(player).flushCount);
		} finally {
			WorldObjectService.getInstance().resetForTesting();
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void cacheRebuildPreservesIndependentSceneSlotsOnOneTile()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(89);
		try {
			WorldObjectService service = WorldObjectService.getInstance();
			service.applyCacheMutation(new Objects(2213, 3200, 3200, 0, 0, 0, 0));
			service.applyCacheMutation(new Objects(2214, 3200, 3200, 0, 0, 10, 0));
			assertEquals(2213, Region.getObjectAt(3200, 3200, 0, 0).getObjectId());
			assertEquals(2214, Region.getObjectAt(3200, 3200, 0, 10).getObjectId());
			Wp5PlayerSupport.recording(player).clearPackets();
			service.rebuildObjects(player);
			assertEquals(2, Wp5PlayerSupport.recording(player).flushCount);
		} finally {
			WorldObjectService.getInstance().applyCacheMutation(
					new Objects(-1, 3200, 3200, 0, 0, 0, 0));
			WorldObjectService.getInstance().applyCacheMutation(
					new Objects(-1, 3200, 3200, 0, 0, 10, 0));
			WorldObjectService.getInstance().resetForTesting();
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void exactResolverNeverFindsMatchingShapeBehindSelectedLayer()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(89);
		ArrayList<Objects> previousGlobal = new ArrayList<Objects>(
				GameEngine.objectHandler.globalObjects);
		try {
			WorldObjectService service = WorldObjectService.getInstance();
			service.applyCacheMutation(new Objects(2213, 3200, 3200, 0, 0, 10, 0));
			Objects global = new Objects(2222, 3200, 3200, 0, 1, 10, 0);
			GameEngine.objectHandler.addObject(global);
			assertNull(service.resolve(player, 3200, 3200, 0, 2213, 10, 0));
			assertEquals(2222, service.resolve(player, 3200, 3200, 0,
					2222, 10, 1).getObjectId());
		} finally {
			WorldObjectService.getInstance().applyCacheMutation(
					new Objects(-1, 3200, 3200, 0, 0, 10, 0));
			WorldObjectService.getInstance().resetForTesting();
			GameEngine.objectHandler.globalObjects.clear();
			GameEngine.objectHandler.globalObjects.addAll(previousGlobal);
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void packetResolverFindsSecondarySlotAndRejectsAmbiguousId()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(89);
		try {
			WorldObjectService service = WorldObjectService.getInstance();
			service.applyCacheMutation(new Objects(2213, 3200, 3200, 0, 0, 0, 0));
			service.applyCacheMutation(new Objects(2214, 3200, 3200, 0, 1, 10, 0));
			assertEquals(2213, service.resolvePacketObject(player, 2213,
					3200, 3200, 0).getObjectId());
			assertEquals(2214, service.resolvePacketObject(player, 2214,
					3200, 3200, 0).getObjectId());
			service.applyCacheMutation(new Objects(2213, 3200, 3200, 0, 1, 10, 0));
			assertNull(service.resolvePacketObject(player, 2213, 3200, 3200, 0));
		} finally {
			WorldObjectService.getInstance().applyCacheMutation(
					new Objects(-1, 3200, 3200, 0, 0, 0, 0));
			WorldObjectService.getInstance().applyCacheMutation(
					new Objects(-1, 3200, 3200, 0, 0, 10, 0));
			WorldObjectService.getInstance().resetForTesting();
			Wp5PlayerSupport.cleanup(player);
		}
	}

	@Test public void encounterLayerWinsAndRestoresLowerLayer() throws Exception {
        WorldObjectService service = WorldObjectService.getInstance();
        assertNull(service.resolve(3200, 3200, 0));
        assertNull(service.replace(7L, 3200, 3200, 0, -1, -1, -1,
                2213, 10, 0));
        com.rs2.game.players.Player player = Wp5PlayerSupport.player(89);
		com.rs2.game.players.Player observer = Wp5PlayerSupport.additionalPlayer(88);
        GameEngine.objectHandler.addObject(new Objects(2222, 3200, 3200, 0, 0, 10, 0));
        ScriptEncounterHandle encounter = Wp5PlayerSupport.scripted(player).beginEncounter(
                "precedence", 3199, 3199, 3201, 3201, 0);
        assertEquals(2213, encounter.replaceObject(3200, 3200, 0, 2222, 10, 0,
                2213, 10, 0).id());
        ResolvedWorldObject resolved = service.resolve(3200, 3200, 0);
        assertEquals(ResolvedWorldObject.Layer.ENCOUNTER, resolved.getLayer());
        assertEquals(2213, resolved.getObjectId());
		assertEquals(2213, service.resolve(player, 3200, 3200, 0).getObjectId());
		assertEquals(2222, service.resolve(observer, 3200, 3200, 0).getObjectId());
		Objects lowerProjection = new Objects(2222, 3200, 3200, 0, 0, 10, 0);
		assertFalse(service.shouldProjectLower(player, lowerProjection));
		assertTrue(service.shouldProjectLower(observer, lowerProjection));
        GameEngine.objectHandler.addObject(new Objects(2223, 3200, 3200, 0, 0, 10, 0));
        encounter.close();
        assertEquals(2223, service.resolve(3200, 3200, 0).getObjectId());
        GameEngine.objectHandler.globalObjects.clear();
		Wp5PlayerSupport.cleanup(observer);
        Wp5PlayerSupport.cleanup(player);
    }
}
