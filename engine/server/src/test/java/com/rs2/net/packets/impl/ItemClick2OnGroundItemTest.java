package com.rs2.net.packets.impl;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.net.packets.PacketHandler;
import com.rs2.script.ScriptRuntimeTestFixture;
import com.rs2.GameEngine;
import com.rs2.game.items.GroundItem;
import com.rs2.world.GroundItemRef;

public class ItemClick2OnGroundItemTest {

	private InteractionPacketTestSupport support;

	@Before
	public void setUp() throws Exception {
		ScriptRuntimeTestFixture.reset();
		support = new InteractionPacketTestSupport();
	}

	@After
	public void tearDown() throws Exception {
		ScriptRuntimeTestFixture.reset();
		support.restore();
	}

	@Test
	public void invalidAndLockedPacketsPrecedeAllLegacyEffects() {
		InteractionPacketTestSupport.TestPlayer player = support.livePlayer(1);
		support.addGroundItem(player, InteractionPacketTestSupport.GROUND_ITEM,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 100);

		PacketHandler.processPacket(player, PacketFixtures.rawPacket(
				253, 1, 2, 3, 4, 5));
		PacketHandler.processPacket(player,
				PacketFixtures.secondGroundItemClick(
						InteractionPacketTestSupport.GROUND_ITEM,
						InteractionPacketTestSupport.X + 1,
						InteractionPacketTestSupport.Y));
		ScriptInteractionGate.setActionLockedForTest(player, true);
		PacketHandler.processPacket(player,
				PacketFixtures.secondGroundItemClick(
						InteractionPacketTestSupport.GROUND_ITEM,
						InteractionPacketTestSupport.X,
						InteractionPacketTestSupport.Y));

		assertEquals(0, player.endedTasks);
	}

	@Test
	public void validUnlockedNonLogPreservesSecondClickContinuation() {
		InteractionPacketTestSupport.TestPlayer player = support.livePlayer(1);
		support.addGroundItem(player, InteractionPacketTestSupport.GROUND_ITEM,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 100);

		PacketHandler.processPacket(player,
				PacketFixtures.secondGroundItemClick(
						InteractionPacketTestSupport.GROUND_ITEM,
						InteractionPacketTestSupport.X,
						InteractionPacketTestSupport.Y));

		assertEquals(1, player.endedTasks);
	}

	@Test
	public void sharedResolverPrefersPrivateThenLowestCreationToken() {
		InteractionPacketTestSupport.TestPlayer player = support.livePlayer(1);
		GroundItem publicItem = support.addGroundItem(player,
				InteractionPacketTestSupport.GROUND_ITEM,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 0);
		GroundItem privateFirst = support.addGroundItem(player,
				InteractionPacketTestSupport.GROUND_ITEM,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 100);
		support.addGroundItem(player,
				InteractionPacketTestSupport.GROUND_ITEM,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 100);

		GroundItemRef resolved = GameEngine.itemHandler.resolveVisibleGroundItem(
				player, InteractionPacketTestSupport.GROUND_ITEM,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 0);

		assertEquals(privateFirst.getCreationToken(), resolved.getToken());
		org.junit.Assert.assertTrue(
				resolved.getToken() != publicItem.getCreationToken());
	}

	@Test
	public void reusableSlotAndUsernameNeverGrantPrivateVisibility() {
		InteractionPacketTestSupport.TestPlayer owner = support.livePlayer(1);
		GroundItem exact = support.addGroundItem(owner,
				InteractionPacketTestSupport.GROUND_ITEM,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 100);
		InteractionPacketTestSupport.TestPlayer replacement =
				support.livePlayer(1);
		replacement.playerName = owner.playerName;

		GroundItemRef resolved = GameEngine.itemHandler.resolveVisibleGroundItem(
				replacement, InteractionPacketTestSupport.GROUND_ITEM,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 0);

		org.junit.Assert.assertNull(resolved);
		org.junit.Assert.assertTrue(GameEngine.itemHandler.items.contains(exact));
	}

	@Test
	public void ownerlessHiddenItemIsInvisibleUntilPublicTransition() {
		InteractionPacketTestSupport.TestPlayer player = support.livePlayer(1);
		GroundItem ownerless = new GroundItem(
				InteractionPacketTestSupport.GROUND_ITEM,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 0, 1, player.playerId, 2,
				player.playerName);
		GameEngine.itemHandler.addItem(ownerless);

		org.junit.Assert.assertNull(GameEngine.itemHandler
				.resolveVisibleGroundItem(player,
						InteractionPacketTestSupport.GROUND_ITEM,
						InteractionPacketTestSupport.X,
						InteractionPacketTestSupport.Y, 0));
		GameEngine.itemHandler.process();
		GroundItemRef publicRef = GameEngine.itemHandler
				.resolveVisibleGroundItem(player,
						InteractionPacketTestSupport.GROUND_ITEM,
						InteractionPacketTestSupport.X,
						InteractionPacketTestSupport.Y, 0);
		org.junit.Assert.assertNotNull(publicRef);
		org.junit.Assert.assertFalse(publicRef.isPrivateToPlayer());
	}

	@Test
	public void universalValidationRejectsLiveDefinitionAndIdentityFailures() {
		InteractionPacketTestSupport.TestPlayer player = support.livePlayer(1);

		PacketHandler.processPacket(player,
				PacketFixtures.secondGroundItemClick(
						InteractionPacketTestSupport.GROUND_ITEM,
						InteractionPacketTestSupport.X,
						InteractionPacketTestSupport.Y));
		support.addGroundItem(player,
				InteractionPacketTestSupport.GROUND_ITEM,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 100);
		player.initialized = false;
		PacketHandler.processPacket(player,
				PacketFixtures.secondGroundItemClick(
						InteractionPacketTestSupport.GROUND_ITEM,
						InteractionPacketTestSupport.X,
						InteractionPacketTestSupport.Y));
		player.initialized = true;
		PacketHandler.processPacket(player,
				PacketFixtures.secondGroundItemClick(14999,
						InteractionPacketTestSupport.X,
						InteractionPacketTestSupport.Y));
		PacketHandler.processPacket(player,
				PacketFixtures.secondGroundItemClick(
						InteractionPacketTestSupport.GROUND_ITEM,
						-1, InteractionPacketTestSupport.Y));

		assertEquals(0, player.endedTasks);
	}
}
