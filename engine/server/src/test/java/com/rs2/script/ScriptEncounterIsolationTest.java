package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.game.players.Player;
import com.rs2.net.Packet;
import com.rs2.net.packets.impl.Walking;
import com.rs2.script.world.ScriptEncounterHandle;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.world.clip.PathFinder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ScriptEncounterIsolationTest {

	private ScriptEncounterTestSupport support;

	@Before
	public void setUp() throws Exception {
		support = new ScriptEncounterTestSupport();
	}

	@After
	public void tearDown() throws Exception {
		support.close();
	}

	@Test
	public void realWalkingPacketRejectsBeforeLegacySideEffects() {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3200, 3200, 0);
		ScriptEncounterTestSupport.TestClient outsider =
				support.player(2, 3199, 3200, 0);
		ScriptEncounterHandle encounter =
				support.encounter(owner, "walking", 3200, 3200, 3204, 3204, 0);
		assertNotNull(encounter);

		outsider.isTeleporting = true;
		new Walking().processPacket(outsider, walkingPacket(3200, 3200));
		assertTrue(outsider.isTeleporting);

		owner.isTeleporting = true;
		new Walking().processPacket(owner, walkingPacket(3205, 3200));
		assertTrue(owner.isTeleporting);

		owner.isTeleporting = true;
		new Walking().processPacket(owner, walkingPacket(3201, 3200));
		assertFalse(owner.isTeleporting);
	}

	@Test
	public void centralMovesRespectParticipantAndPlaneBoundaries() {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3200, 3200, 0);
		ScriptEncounterTestSupport.TestClient outsider =
				support.player(2, 3199, 3200, 0);
		ScriptEncounterHandle encounter =
				support.encounter(owner, "central", 3200, 3200, 3204, 3204, 0);

		owner.teleportToX = -1;
		owner.getPlayerAssistant().movePlayer(3205, 3200, 0);
		assertTrue(owner.teleportToX == -1);
		owner.getPlayerAssistant().movePlayer(3204, 3204, 0);
		assertTrue(owner.teleportToX == 3204);

		outsider.teleportToX = -1;
		outsider.getPlayerAssistant().movePlayer(3200, 3200, 0);
		assertTrue(outsider.teleportToX == -1);
		outsider.getPlayerAssistant().movePlayer(3200, 3200, 1);
		assertTrue(outsider.teleportToX == 3200);
		assertTrue(ScriptEncounterService.getInstance()
				.canObserve(owner, owner));
		assertFalse(ScriptEncounterService.getInstance()
				.canObserve(owner, outsider));
		assertNotNull(encounter);
	}

	@Test
	public void productionInteractionAndRetainedMutationUseServiceLock()
			throws Exception {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3200, 3200, 0);
		ScriptedPlayer retained = new ScriptedPlayer(owner);
		ScriptEncounterHandle encounter = retained.beginEncounter(
				"gate", 3200, 3200, 3204, 3204, 0);
		assertNotNull(encounter);
		assertNotNull(retained.getActions().lock(2));

		Class<?> gate = Class.forName(
				"com.rs2.net.packets.impl.ScriptInteractionGate");
		Method isActionLocked = gate.getDeclaredMethod(
				"isActionLocked", Player.class);
		isActionLocked.setAccessible(true);
		assertTrue((Boolean) isActionLocked.invoke(null, owner));
		assertFalse(retained.animate(1234));

		owner.isTeleporting = true;
		new Walking().processPacket(owner, walkingPacket(3201, 3200));
		assertFalse(owner.isTeleporting);
		assertNotNull(retained.getMovement().lock(2));
		owner.isTeleporting = true;
		new Walking().processPacket(owner, walkingPacket(3201, 3200));
		assertTrue(owner.isTeleporting);
	}

	@Test
	public void queuedOutsiderRouteIsPurgedAtReservationBoundary() {
		ScriptEncounterTestSupport.TestClient outsider =
				support.player(2, 3198, 3202, 0);
		queueAbsolute(outsider, 3206, 3202);
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3200, 3200, 0);
		ScriptEncounterHandle encounter =
				support.encounter(owner, "queued-outsider",
						3200, 3200, 3204, 3204, 0);
		assertNotNull(encounter);

		drainWalkingQueue(outsider, encounter);

		assertEquals(3199, outsider.absX);
		assertEquals(3202, outsider.absY);
		assertEquals(-1, outsider.getNextWalkingDirection());
	}

	@Test
	public void queuedParticipantRouteCannotLeaveReservation() {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3202, 3202, 0);
		queueAbsolute(owner, 3198, 3202);
		ScriptEncounterHandle encounter =
				support.encounter(owner, "queued-owner",
						3200, 3200, 3204, 3204, 0);
		assertNotNull(encounter);

		drainWalkingQueue(owner, encounter);

		assertEquals(3200, owner.absX);
		assertEquals(3202, owner.absY);
		assertEquals(-1, owner.getNextWalkingDirection());
	}

	@Test
	public void pathFinderExpansionRoutesOutsiderAroundReservation() {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3200, 3200, 0);
		ScriptEncounterTestSupport.TestClient outsider =
				support.player(2, 3198, 3202, 0);
		ScriptEncounterHandle encounter =
				support.encounter(owner, "path-expansion",
						3200, 3200, 3204, 3204, 0);
		assertNotNull(encounter);

		PathFinder.getPathFinder().findRoute(
				outsider, 3206, 3202, false, 1, 1);
		drainWalkingQueue(outsider, encounter);

		assertEquals(3206, outsider.absX);
		assertEquals(3202, outsider.absY);
	}

	private static void queueAbsolute(Player player, int x, int y) {
		player.addToWalkingQueue(
				x - player.mapRegionX * 8, y - player.mapRegionY * 8);
	}

	private static void drainWalkingQueue(Player player,
			ScriptEncounterHandle encounter) {
		for (int step = 0; step < 100; step++) {
			int direction = player.getNextWalkingDirection();
			if (direction == -1) {
				return;
			}
			if (player != encounter.owner().backingPlayer()) {
				assertFalse(encounter.contains(
						player.absX, player.absY, player.heightLevel));
			} else {
				assertTrue(encounter.contains(
						player.absX, player.absY, player.heightLevel));
			}
		}
		throw new AssertionError("walking queue did not drain");
	}

	private static Packet walkingPacket(int x, int y) {
		ByteBuf payload = Unpooled.buffer(5);
		payload.writeByte(x + 128);
		payload.writeByte(x >> 8);
		payload.writeByte(y);
		payload.writeByte(y >> 8);
		payload.writeByte(0);
		return new Packet(164, Packet.Type.FIXED, payload);
	}
}
