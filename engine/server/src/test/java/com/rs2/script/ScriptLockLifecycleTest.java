package com.rs2.script;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.script.scheduler.ScriptTaskHandle;
import com.rs2.script.world.ScriptEncounterHandle;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.script.world.ScriptLockHandle;
import com.rs2.net.Packet;
import com.rs2.net.packets.PacketHandler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ScriptLockLifecycleTest {

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
	public void locksStackExpireAndReleaseByExactToken() {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3200, 3200, 0);
		ScriptedPlayer scripted = new ScriptedPlayer(owner);
		ScriptEncounterHandle encounter = scripted.beginEncounter(
				"locks", 3200, 3200, 3204, 3204, 0);
		ScriptLockHandle first = scripted.getActions().lock(1);
		ScriptLockHandle second = scripted.getActions().lock(2);
		ScriptLockHandle movement = scripted.getMovement().lock(2);

		assertNotNull(encounter);
		assertTrue(first.isActive());
		assertTrue(second.isActive());
		assertTrue(movement.isActive());
		assertTrue(ScriptEncounterService.getInstance().isActionLocked(owner));
		assertTrue(ScriptEncounterService.getInstance().isMovementLocked(owner));
		ScriptLifecycleService.getInstance().processGameTick();
		assertFalse(first.isActive());
		assertTrue(second.isActive());
		assertTrue(ScriptEncounterService.getInstance().isActionLocked(owner));
		assertFalse(first.release());
		ScriptLifecycleService.getInstance().processGameTick();
		assertFalse(second.isActive());
		assertFalse(movement.isActive());
		assertFalse(ScriptEncounterService.getInstance().isActionLocked(owner));
		assertFalse(ScriptEncounterService.getInstance().isMovementLocked(owner));
	}

	@Test
	public void participantRemovalCancelsOnlyParticipantTasks() {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3200, 3200, 0);
		ScriptEncounterTestSupport.TestClient participant =
				support.player(2, 3201, 3200, 0);
		ScriptedPlayer scriptedOwner = new ScriptedPlayer(owner);
		ScriptedPlayer scriptedParticipant = new ScriptedPlayer(participant);
		ScriptEncounterHandle encounter = scriptedOwner.beginEncounter(
				"tasks", 3200, 3200, 3204, 3204, 0);
		assertTrue(encounter.addParticipant(scriptedParticipant));
		Context context = ScriptHost.getInstance().getContext();
		Value callback = context.eval("js", "() => 1");
		ScriptTaskHandle ownerTask = scriptedOwner.after(10, callback);
		ScriptTaskHandle participantTask =
				scriptedParticipant.after(10, callback);

		assertTrue(encounter.removeParticipant(scriptedParticipant));
		assertTrue(participantTask.isCancelled());
		assertFalse(ownerTask.isCancelled());
		assertTrue(encounter.close());
		assertTrue(ownerTask.isCancelled());
	}

	@Test
	public void closeAndOwnerLogoutReleaseAllLockTypes() {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3200, 3200, 0);
		ScriptedPlayer scripted = new ScriptedPlayer(owner);
		ScriptEncounterHandle encounter = scripted.beginEncounter(
				"cleanup", 3200, 3200, 3204, 3204, 0);
		ScriptLockHandle action = scripted.getActions().lock(100);
		ScriptLockHandle movement = scripted.getMovement().lock(100);

		ScriptLifecycleService.getInstance().onExplicitLogout(owner);
		assertFalse(encounter.isOpen());
		assertFalse(action.isActive());
		assertFalse(movement.isActive());
	}

	@Test
	public void invalidTaskCallbacksReturnNullInsideAndOutsideEncounter() {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3200, 3200, 0);
		ScriptedPlayer scripted = new ScriptedPlayer(owner);
		Context context = ScriptHost.getInstance().getContext();
		Value nonExecutable = context.asValue("not-a-callback");

		assertRejected(scripted.after(1, null));
		assertRejected(scripted.every(1, nonExecutable));
		ScriptEncounterHandle encounter = scripted.beginEncounter(
				"invalid-task", 3200, 3200, 3204, 3204, 0);
		assertNotNull(encounter);
		assertRejected(scripted.after(1, null));
		assertRejected(scripted.every(1, nonExecutable));
		assertNull(encounter.after(1, null));
		assertNull(encounter.every(1, nonExecutable));
		assertTrue(encounter.isOpen());
	}

	@Test
	public void playerTaskCapReturnsSafeRejectedHandleAndIsGraalCallable() {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3200, 3200, 0);
		ScriptedPlayer scripted = new ScriptedPlayer(owner);
		ScriptEncounterHandle encounter = scripted.beginEncounter(
				"task-cap", 3200, 3200, 3204, 3204, 0);
		Context context = ScriptHost.getInstance().getContext();
		Value callback = context.eval("js", "() => 1");
		for (int index = 0; index < 32; index++) {
			ScriptTaskHandle handle = scripted.after(100, callback);
			assertNotNull(handle);
			assertFalse(handle.isCancelled());
		}
		assertRejected(scripted.after(100, callback));
		context.getBindings("js").putMember("wp3player", scripted);
		assertTrue(context.eval("js",
				"wp3player.after(1, 'invalid').isCancelled()").asBoolean());
		assertTrue(encounter.isOpen());
	}

	@Test
	public void lockCapAndCallbackFailureCloseAreDeterministic() {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3200, 3200, 0);
		ScriptedPlayer scripted = new ScriptedPlayer(owner);
		ScriptEncounterHandle encounter = scripted.beginEncounter(
				"lock-cap", 3200, 3200, 3204, 3204, 0);
		List<ScriptLockHandle> locks = new ArrayList<ScriptLockHandle>();
		for (int index = 0; index < 16; index++) {
			ScriptLockHandle lock = scripted.getActions().lock(100);
			assertNotNull(lock);
			locks.add(lock);
		}
		assertNull(scripted.getActions().lock(100));
		for (ScriptLockHandle lock : locks) {
			assertTrue(lock.release());
		}
		Value failure = ScriptHost.getInstance().getContext().eval(
				"js", "() => { throw new Error('expected wp3 failure'); }");
		assertNotNull(encounter.every(1, failure));
		ScriptLifecycleService.getInstance().processGameTick();
		assertFalse(encounter.isOpen());
	}

	@Test
	public void actionLockDropsEveryLegacyInteractionFamilyAtPacketHandlerEntry() {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3200, 3200, 0);
		ScriptedPlayer scripted = new ScriptedPlayer(owner);
		ScriptEncounterHandle encounter = scripted.beginEncounter(
				"packet-lock", 3200, 3200, 3204, 3204, 0);
		ScriptLockHandle lock = scripted.getActions().lock(100);
		assertNotNull(encounter);
		assertNotNull(lock);
		owner.npcIndex = 77;
		owner.npcClickIndex = 78;
		owner.playerIndex = 79;
		owner.clickNpcType = 80;
		owner.objectX = 3210;
		owner.objectY = 3211;
		owner.objectId = 999;
		owner.clickObjectType = 4;

		int[][] packets = {
				{155, 2}, {132, 6}, {122, 6}, {16, 6}, {75, 6},
				{53, 4}, {192, 12}, {57, 6}
		};
		for (int[] descriptor : packets) {
			PacketHandler.processPacket(owner,
					zeroPacket(descriptor[0], descriptor[1]));
		}

		assertEquals(0, owner.endedTasks);
		assertEquals(0, owner.postedEvents);
		assertEquals(77, owner.npcIndex);
		assertEquals(79, owner.playerIndex);
		assertEquals(3210, owner.objectX);
		assertEquals(999, owner.objectId);

		assertTrue(lock.release());
		PacketHandler.processPacket(owner, zeroPacket(122, 6));
		assertEquals(1, owner.endedTasks);
	}

	@Test
	public void exactOfferedDialogueOptionIsOneShotActionLockException() {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3200, 3200, 0);
		ScriptedPlayer scripted = new ScriptedPlayer(owner);
		ScriptEncounterHandle encounter = scripted.beginEncounter(
				"dialogue-lock", 3200, 3200, 3204, 3204, 0);
		AtomicInteger selected = new AtomicInteger(-1);
		Value callback = ScriptHost.getInstance().getContext().asValue(
				(ProxyExecutable) arguments -> {
					selected.set(arguments[0].asInt());
					return null;
				});
		scripted.getDialogue().options(
				new String[] {"First", "Second"}, callback);
		long exactToken = owner.pendingScriptOptionToken;
		ScriptLockHandle lock = scripted.getActions().lock(100);
		assertNotNull(encounter);
		assertNotNull(lock);
		assertTrue(exactToken != 0L);

		PacketHandler.processPacket(owner, actionButtonPacket(9167));
		assertEquals(-1, selected.get());
		assertEquals(exactToken, owner.pendingScriptOptionToken);
		assertNotNull(owner.pendingScriptOption);

		PacketHandler.processPacket(owner, actionButtonPacket(9157));
		assertEquals(0, selected.get());
		assertNull(owner.pendingScriptOption);
		assertEquals(0L, owner.pendingScriptOptionToken);
		PacketHandler.processPacket(owner, actionButtonPacket(9157));
		assertEquals(0, selected.get());

		assertTrue(lock.release());
		scripted.getDialogue().statement("First").statement("Second").end();
		assertEquals(DialogueChain.CHAIN_SENTINEL, owner.nextChat);
		ScriptLockHandle frameLock = scripted.getActions().lock(100);
		assertNotNull(frameLock);
		PacketHandler.processPacket(owner, zeroPacket(40, 0));
		assertNull(owner.scriptDialogueFrames);
		assertEquals(0, owner.nextChat);
	}

	private static void assertRejected(ScriptTaskHandle handle) {
		assertNotNull(handle);
		assertTrue(handle.isCancelled());
		assertFalse(handle.cancel());
	}

	private static Packet zeroPacket(int opcode, int length) {
		return new Packet(opcode, Packet.Type.FIXED,
				Unpooled.buffer(length).writeZero(length));
	}

	private static Packet actionButtonPacket(int actionButtonId) {
		ByteBuf payload = Unpooled.buffer(2);
		payload.writeByte(actionButtonId / 1000);
		payload.writeByte(actionButtonId % 1000);
		return new Packet(185, Packet.Type.FIXED, payload);
	}
}
