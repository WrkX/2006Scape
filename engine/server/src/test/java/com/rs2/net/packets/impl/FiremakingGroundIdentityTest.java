package com.rs2.net.packets.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.GameEngine;
import com.rs2.event.CycleEventHandler;
import com.rs2.game.items.GroundItem;
import com.rs2.game.content.music.sound.SoundList;
import com.rs2.world.GroundItemRef;
import com.rs2.net.packets.PacketHandler;
import com.rs2.script.ScriptRuntimeTestFixture;

public class FiremakingGroundIdentityTest {

	private InteractionPacketTestSupport support;
	private InteractionPacketTestSupport.TestPlayer player;

	@Before
	public void setUp() throws Exception {
		ScriptRuntimeTestFixture.reset();
		support = new InteractionPacketTestSupport();
		player = support.livePlayer(1);
		player.playerLevel[11] = 99;
	}

	@After
	public void tearDown() throws Exception {
		CycleEventHandler.getSingleton().stopEvents(player);
		CycleEventHandler.getSingleton().process();
		ScriptRuntimeTestFixture.reset();
		support.restore();
	}

	@Test
	public void delayedFiremakingNeverConsumesEqualReplacement() {
		GroundItem original = support.addGroundItem(player,
				InteractionPacketTestSupport.LOG,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 100);
		int xpBefore = player.playerXP[11];

		PacketHandler.processPacket(player,
				PacketFixtures.secondGroundItemClick(
						InteractionPacketTestSupport.LOG,
						InteractionPacketTestSupport.X,
						InteractionPacketTestSupport.Y));
		GameEngine.itemHandler.removeItem(original);
		GroundItem replacement = support.addGroundItem(player,
				InteractionPacketTestSupport.LOG,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 100);
		runCycles(12);

		assertTrue(GameEngine.itemHandler.items.contains(replacement));
		assertEquals(xpBefore, player.playerXP[11]);
		assertFalse(GameEngine.objectManager.objectExists(
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));
		assertFalse(outputContainsWord(SoundList.FIRE_SUCCESSFUL));
		runCycles(220);
		assertFalse(GameEngine.itemHandler.itemExists(592,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));
	}

	@Test
	public void delayedFiremakingConsumesItsExactCreationOnSuccess() {
		GroundItem original = support.addGroundItem(player,
				InteractionPacketTestSupport.LOG,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 100);

		PacketHandler.processPacket(player,
				PacketFixtures.secondGroundItemClick(
						InteractionPacketTestSupport.LOG,
						InteractionPacketTestSupport.X,
						InteractionPacketTestSupport.Y));
		runCycles(12);

		assertFalse(GameEngine.itemHandler.items.contains(original));
		assertTrue(GameEngine.objectManager.objectExists(
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));
		assertTrue(player.playerXP[11] > 0);
		assertTrue(outputContainsWord(SoundList.FIRE_SUCCESSFUL));
		runCycles(220);
		assertTrue(GameEngine.itemHandler.itemExists(592,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));
	}

	@Test
	public void delayedPickupNeverConsumesEqualReplacement() {
		GroundItem original = support.addGroundItem(player,
				InteractionPacketTestSupport.GROUND_ITEM,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 100);

		PacketHandler.processPacket(player, PacketFixtures.pickup(
				InteractionPacketTestSupport.GROUND_ITEM,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));
		GameEngine.itemHandler.removeItem(original);
		GroundItem replacement = support.addGroundItem(player,
				InteractionPacketTestSupport.GROUND_ITEM,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 100);
		runCycles(2);

		assertTrue(GameEngine.itemHandler.items.contains(replacement));
		assertEquals(0, player.getItemAssistant().getItemAmount(
				InteractionPacketTestSupport.GROUND_ITEM));
	}

	@Test
	public void itemOnGroundFiremakingCarriesExactTokenThroughDelay() {
		player.playerItems[0] = 591;
		player.playerItemsN[0] = 1;
		GroundItem original = support.addGroundItem(player,
				InteractionPacketTestSupport.LOG,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 100);

		PacketHandler.processPacket(player, PacketFixtures.itemOnGroundItem(
				590, InteractionPacketTestSupport.LOG, 0,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));
		GameEngine.itemHandler.removeItem(original);
		GroundItem replacement = support.addGroundItem(player,
				InteractionPacketTestSupport.LOG,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 100);
		runCycles(12);

		assertTrue(GameEngine.itemHandler.items.contains(replacement));
		assertEquals(0, player.playerXP[11]);
		assertFalse(outputContainsWord(SoundList.FIRE_SUCCESSFUL));
		assertFalse(GameEngine.objectManager.objectExists(
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));
	}

	@Test
	public void itemOnGroundFiremakingSucceedsOnlyForCapturedIdentity() {
		player.playerItems[0] = 591;
		player.playerItemsN[0] = 1;
		GroundItem original = support.addGroundItem(player,
				InteractionPacketTestSupport.LOG,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 100);

		PacketHandler.processPacket(player, PacketFixtures.itemOnGroundItem(
				590, InteractionPacketTestSupport.LOG, 0,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));
		runCycles(12);

		assertFalse(GameEngine.itemHandler.items.contains(original));
		assertTrue(player.playerXP[11] > 0);
		assertTrue(outputContainsWord(SoundList.FIRE_SUCCESSFUL));
		assertTrue(GameEngine.objectManager.objectExists(
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));
	}

	@Test
	public void consumedIdentityCannotCompleteScheduledFiremaking() {
		GroundItem original = support.addGroundItem(player,
				InteractionPacketTestSupport.LOG,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 100);
		GroundItemRef reference = GameEngine.itemHandler
				.resolveVisibleGroundItem(player,
						InteractionPacketTestSupport.LOG,
						InteractionPacketTestSupport.X,
						InteractionPacketTestSupport.Y, 0);

		PacketHandler.processPacket(player,
				PacketFixtures.secondGroundItemClick(
						InteractionPacketTestSupport.LOG,
						InteractionPacketTestSupport.X,
						InteractionPacketTestSupport.Y));
		assertEquals(1, GameEngine.itemHandler.consumeGroundItemExact(
				player, reference, false, 1));
		runCycles(12);

		assertFalse(GameEngine.itemHandler.items.contains(original));
		assertEquals(0, player.playerXP[11]);
		assertFalse(outputContainsWord(SoundList.FIRE_SUCCESSFUL));
		assertFalse(GameEngine.objectManager.objectExists(
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));
	}

	@Test
	public void expiredIdentityCannotCompleteScheduledFiremaking() {
		GroundItem original = support.addGroundItem(player,
				InteractionPacketTestSupport.LOG,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 0);
		original.removeTicks = 2;

		PacketHandler.processPacket(player,
				PacketFixtures.secondGroundItemClick(
						InteractionPacketTestSupport.LOG,
						InteractionPacketTestSupport.X,
						InteractionPacketTestSupport.Y));
		GameEngine.itemHandler.process();
		runCycles(12);

		assertFalse(GameEngine.itemHandler.items.contains(original));
		assertEquals(0, player.playerXP[11]);
		assertFalse(outputContainsWord(SoundList.FIRE_SUCCESSFUL));
		assertFalse(GameEngine.objectManager.objectExists(
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));
		runCycles(220);
		assertFalse(GameEngine.itemHandler.itemExists(592,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));
	}

	private static void runCycles(int count) {
		for (int cycle = 0; cycle < count; cycle++) {
			CycleEventHandler.getSingleton().process();
		}
	}

	private boolean outputContainsWord(int value) {
		if (player.hasFlushedWord(value)) {
			return true;
		}
		for (int index = 0; index + 1 < player.outStream.currentOffset;
				index++) {
			int word = (player.outStream.buffer[index] & 0xff) << 8
					| player.outStream.buffer[index + 1] & 0xff;
			if (word == value) {
				return true;
			}
		}
		return false;
	}
}
