package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.ArrayList;

import org.junit.Test;

import com.rs2.game.items.GroundItem;
import com.rs2.script.world.ScriptGroundItemHandle;
import com.rs2.world.ItemHandler;
import com.rs2.GameEngine;

/** Exact identity and detach semantics independent of packet timing. */
public class ScriptGroundRewardIntegrationTest {
	@Test public void productionDropForUsesOneProjectionThroughDetachRemoveCloseAndExpiry()
			throws Exception {
		com.rs2.game.players.Player owner = Wp5PlayerSupport.player(91);
		com.rs2.game.players.Player observer = null;
		ArrayList<GroundItem> previous = new ArrayList<GroundItem>(
				GameEngine.itemHandler.items);
		try {
			observer = Wp5PlayerSupport.additionalPlayer(90);
			GameEngine.itemHandler.items.clear();
			GameEngine.itemHandler.resetProjectionsForTesting();
			GroundItem equalPublic = new GroundItem(995, Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, 7, -1, 0, "public", null);
			GameEngine.itemHandler.createGlobalItem(equalPublic);
			Wp5PlayerSupport.recording(owner).clearPackets();
			Wp5PlayerSupport.recording(observer).clearPackets();

			com.rs2.script.world.ScriptEncounterHandle encounter =
					Wp5PlayerSupport.scripted(owner).beginEncounter("drop-production",
							Wp5PlayerSupport.X - 1, Wp5PlayerSupport.Y - 1,
							Wp5PlayerSupport.X + 1, Wp5PlayerSupport.Y + 1, 0);
			ScriptGroundItemHandle expiring = encounter.dropFor(
					Wp5PlayerSupport.scripted(owner), 995, 1,
					Wp5PlayerSupport.X, Wp5PlayerSupport.Y, 0);
			assertNotNull(expiring);
			assertEquals(2, Wp5PlayerSupport.recording(owner).flushCount);
			assertEquals(0, Wp5PlayerSupport.recording(observer).flushCount);
			assertEquals(Long.parseUnsignedLong(expiring.token()),
					GameEngine.itemHandler.projectedTokenForTesting(owner, 995,
							Wp5PlayerSupport.X, Wp5PlayerSupport.Y, 0));

			Wp5PlayerSupport.recording(owner).clearPackets();
			assertTrue(expiring.detach(1));
			GameEngine.itemHandler.process();
			assertTrue(expiring.isClaimed());
			assertEquals(2, Wp5PlayerSupport.recording(owner).flushCount);
			assertEquals(0, Wp5PlayerSupport.recording(observer).flushCount);

			ScriptGroundItemHandle removed = encounter.dropFor(
					Wp5PlayerSupport.scripted(owner), 995, 1,
					Wp5PlayerSupport.X, Wp5PlayerSupport.Y, 0);
			assertTrue(removed.remove());
			ScriptGroundItemHandle closed = encounter.dropFor(
					Wp5PlayerSupport.scripted(owner), 995, 1,
					Wp5PlayerSupport.X, Wp5PlayerSupport.Y, 0);
			assertTrue(encounter.close());
			assertTrue(closed.isClaimed());
			assertTrue(GameEngine.itemHandler.containsExact(equalPublic));
		} finally {
			GameEngine.itemHandler.items.clear();
			GameEngine.itemHandler.items.addAll(previous);
			GameEngine.itemHandler.resetProjectionsForTesting();
			Wp5PlayerSupport.cleanup(observer);
			Wp5PlayerSupport.cleanup(owner);
		}
	}
	@Test public void productionCreateAndRemoveProjectOnlyOwnerAndRedrawEqualPublic()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(91);
		com.rs2.game.players.Player observer = null;
		try {
			observer = Wp5PlayerSupport.additionalPlayer(90);
			ItemHandler handler = new ItemHandler();
			ScriptGroundItemHandle handle = handler.createScriptGroundItems(player,
					31L, 995, 1, Wp5PlayerSupport.X, Wp5PlayerSupport.Y, 0, 0);
			assertNotNull(handle);
			GroundItem privateItem = handle.identities().get(0);
			assertTrue(privateItem.isOwnerProjected());
			GroundItem publicItem = new GroundItem(995, Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, 1, -1, 0, "public", null);
			handler.addItem(publicItem);
			assertTrue(handle.remove());
			assertFalse(privateItem.isOwnerProjected());
			assertTrue(handler.containsExact(publicItem));
		} finally {
			Wp5PlayerSupport.cleanup(observer);
			Wp5PlayerSupport.cleanup(player);
		}
	}

    @Test public void handleKeepsInitialSnapshotAcrossPartialLifecycle() throws Exception {
        com.rs2.game.players.Player player = Wp5PlayerSupport.player(91);
        try {
            ItemHandler handler = new ItemHandler();
            GroundItem first = new GroundItem(995, Wp5PlayerSupport.X,
                    Wp5PlayerSupport.Y, 0, 1, player.playerId, 10,
                    player.playerName, player);
			GroundItem second = new GroundItem(995, Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, 1, player.playerId, 10,
					player.playerName, player);
			first.configureScript(1L, true, false, 0);
			second.configureScript(1L, true, false, 0);
            handler.addItem(first);
			handler.addItem(second);
            ScriptGroundItemHandle handle = new ScriptGroundItemHandle(handler,
					Arrays.asList(first, second));
            assertEquals(2, handle.identityCount());
			assertEquals(2, handle.amount());
            assertTrue(handle.isAttached());
			assertTrue(handler.removeExact(first));
			assertEquals(2, handle.identityCount());
			assertEquals(2, handle.amount());
			assertTrue(handle.isAttached());
            assertTrue(handle.detach(10));
			assertFalse(handle.isAttached());
            assertFalse(handle.isClaimed());
            assertTrue(handle.remove());
            assertFalse(handle.isAttached());
            assertTrue(handle.isClaimed());
        } finally {
            Wp5PlayerSupport.cleanup(player);
        }
    }

	@Test public void detachedPrivateTtlIsExactAndPreservesEqualPublicIdentity()
			throws Exception {
		com.rs2.game.players.Player player = Wp5PlayerSupport.player(91);
		com.rs2.game.players.Player observer = null;
		try {
			observer = Wp5PlayerSupport.additionalPlayer(90);
			ItemHandler handler = new ItemHandler();
			GroundItem privateItem = new GroundItem(995, Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, 1, player.playerId, 0,
					player.playerName, player);
			privateItem.configureScript(2L, true, false, 0);
			GroundItem publicItem = new GroundItem(995, Wp5PlayerSupport.X,
					Wp5PlayerSupport.Y, 0, 1, -1, 0, "public", null);
			handler.addItem(privateItem);
			handler.addItem(publicItem);
			ScriptGroundItemHandle handle = new ScriptGroundItemHandle(handler,
					java.util.Collections.singletonList(privateItem));
			assertTrue(handle.detach(2));
			handler.process();
			assertTrue(handler.containsExact(privateItem));
			int observerPackets = observer.outStream.currentOffset;
			handler.process();
			assertFalse(handler.containsExact(privateItem));
			assertTrue(handler.containsExact(publicItem));
			assertEquals(observerPackets, observer.outStream.currentOffset);
		} finally {
			Wp5PlayerSupport.cleanup(observer);
			Wp5PlayerSupport.cleanup(player);
		}
	}
}
