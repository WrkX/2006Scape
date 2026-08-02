package com.rs2.game.content.traveling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.Constants;
import com.rs2.event.CycleEventHandler;
import com.rs2.game.players.Client;
import com.rs2.game.players.Player;
import com.rs2.util.Stream;
import com.rs2.world.Boundary;

public class DesertHeatTest {

	private static final int DESERT_X = 3250;
	private static final int DESERT_Y = 3080;
	private static final int SHANTAY_X = 3303;
	private static final int SHANTAY_Y = 3129;

	private Player player;

	@Before
	public void setUp() {
		io.netty.channel.embedded.EmbeddedChannel channel =
				new io.netty.channel.embedded.EmbeddedChannel();
		org.apollo.game.session.GameSession session =
				new org.apollo.game.session.GameSession(channel, null, false);
		player = new Client(session, 1);
		player.playerId = 1;
		player.playerName = "desert-test";
		player.outStream = new Stream(new byte[Constants.BUFFER_SIZE]);
		player.outStream.packetEncryption =
				new org.apollo.util.security.IsaacRandom(new int[4]);
		player.absX = DESERT_X;
		player.absY = DESERT_Y;
		player.heightLevel = 0;
		player.playerLevel[Constants.HITPOINTS] = 10;
		player.playerXP[Constants.HITPOINTS] = 1154;
	}

	@After
	public void tearDown() {
		CycleEventHandler.getSingleton().stopEvents(player);
		CycleEventHandler.getSingleton().process();
	}

	@Test
	public void shantayPassTilesAreInDesertButProtectedFromHeat() {
		assertTrue(Boundary.isIn(SHANTAY_X, SHANTAY_Y, Boundary.DESERT));
		assertTrue(Boundary.isIn(SHANTAY_X, 3130, Boundary.DESERT));
		assertTrue(Boundary.isIn(SHANTAY_X, SHANTAY_Y, Boundary.SHANTAY_PASS));
		assertTrue(Boundary.isIn(SHANTAY_X, 3130, Boundary.SHANTAY_PASS));
		player.absX = SHANTAY_X;
		player.absY = SHANTAY_Y;
		assertTrue(Boundary.isIn(player, Boundary.NO_HEAT));
	}

	@Test
	public void callHeatInitializesTimerWithoutImmediateDamage() {
		int hpBefore = player.playerLevel[Constants.HITPOINTS];

		DesertHeat.callHeat(player);

		assertNotEquals(0, player.lastDesert);
		assertTrue(player.desertHeatActive);
		assertEquals(hpBefore, player.playerLevel[Constants.HITPOINTS]);
	}

	@Test
	public void callHeatDoesNotStartDuplicateEvents() {
		DesertHeat.callHeat(player);
		int eventsAfterFirst = CycleEventHandler.getSingleton().getEventsCount();

		DesertHeat.callHeat(player);

		assertEquals(eventsAfterFirst,
				CycleEventHandler.getSingleton().getEventsCount());
	}

	@Test
	public void onLeaveDesertResetsTimerState() {
		DesertHeat.callHeat(player);

		DesertHeat.onLeaveDesert(player);

		assertEquals(0, player.lastDesert);
		assertFalse(player.desertHeatActive);
	}

	@Test
	public void checkWaterskinConsumesHighestAvailableDose() {
		player.getItemAssistant().addItem(1823, 1);

		assertTrue(DesertHeat.checkWaterskin(player));
		assertFalse(player.getItemAssistant().playerHasItem(1823, 1));
		assertTrue(player.getItemAssistant().playerHasItem(1825, 1));
	}

	@Test
	public void checkWaterskinReturnsFalseWithoutWater() {
		assertFalse(DesertHeat.checkWaterskin(player));
	}

	@Test
	public void recentTimerDoesNotApplyDamageWhenEventRuns() {
		player.lastDesert = System.currentTimeMillis();
		int hpBefore = player.playerLevel[Constants.HITPOINTS];

		DesertHeat.callHeat(player);
		CycleEventHandler.getSingleton().process();

		assertEquals(hpBefore, player.playerLevel[Constants.HITPOINTS]);
	}

}
