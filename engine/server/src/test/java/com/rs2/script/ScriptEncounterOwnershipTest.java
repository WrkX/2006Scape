package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.game.dialogues.DialogueOptions;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.world.ScriptEncounterHandle;
import com.rs2.script.scheduler.ScriptTaskHandle;
import com.rs2.script.world.ScriptLockHandle;

public class ScriptEncounterOwnershipTest {

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
	public void creationNormalizesIdAndReservesBeforeMutation() {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3199, 3200, 0);
		ScriptEncounterHandle encounter =
				support.encounter(owner, " Boss.One ", 3200, 3200, 3204, 3204, 0);

		assertNotNull(encounter);
		assertEquals("boss.one", encounter.id());
		assertEquals(1, encounter.participants().length());
		assertEquals(3199, owner.absX);
		assertTrue(encounter.contains(3200, 3200, 0));
		assertTrue(encounter.contains(3204, 3204, 0));
		assertFalse(encounter.contains(3204, 3204, 1));
		assertNull(support.encounter(
				support.player(2, 3198, 3200, 0), "overlap",
				3204, 3204, 3208, 3208, 0));
		assertNotNull(support.encounter(
				support.player(3, 3198, 3200, 1), "other-plane",
				3200, 3200, 3204, 3204, 1));
	}

	@Test
	public void validatesBoundsParticipantsAndOwnerRemoval() {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3200, 3200, 0);
		ScriptEncounterTestSupport.TestClient participant =
				support.player(2, 3201, 3200, 0);
		ScriptEncounterHandle encounter =
				support.encounter(owner, "limits", 3200, 3200, 3204, 3204, 0);

		assertNull(support.encounter(support.player(3, 3190, 3190, 0),
				"too-wide", 3200, 3200, 3264, 3200, 0));
		assertTrue(encounter.addParticipant(new ScriptedPlayer(participant)));
		assertTrue(encounter.addParticipant(new ScriptedPlayer(participant)));
		assertEquals(2, encounter.participants().length());
		assertFalse(support.encounter(participant, "already-owned",
				3210, 3200, 3212, 3202, 0) != null);
		assertTrue(encounter.removeParticipant(new ScriptedPlayer(participant)));
		assertTrue(encounter.isOpen());
		assertTrue(encounter.removeParticipant(new ScriptedPlayer(owner)));
		assertFalse(encounter.isOpen());
		assertFalse(encounter.close());
	}

	@Test
	public void successfulPublicationClosesOldGenerationAndStaleWrapper() throws Exception {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3200, 3200, 0);
		ScriptEncounterTestSupport.TestClient chained =
				support.player(2, 3201, 3200, 0);
		ScriptedPlayer stale = new ScriptedPlayer(owner);
		ScriptedPlayer chainedFacade = new ScriptedPlayer(chained);
		ScriptEncounterHandle encounter = stale.beginEncounter(
				"reload", 3200, 3200, 3204, 3204, 0);
		assertNotNull(encounter);
		Value callback = ScriptHost.getInstance().getContext()
				.eval("js", "() => 1");
		AtomicInteger optionCalls = new AtomicInteger();
		stale.getDialogue().options(new String[] {"One", "Two"},
				ScriptHost.getInstance().getContext().asValue(
						(ProxyExecutable) arguments -> {
							optionCalls.incrementAndGet();
							return null;
						}));
		chainedFacade.getDialogue().statement("First").statement("Second").end();
		assertNotNull(owner.pendingScriptOption);
		assertNotNull(chained.scriptDialogueFrames);
		ScriptTaskHandle oldTask = stale.after(100, callback);
		ScriptLockHandle oldLock = stale.getActions().lock(100);
		assertNotNull(oldTask);
		assertNotNull(oldLock);

		Context replacement = support.publishEmpty();
		assertNotNull(replacement);
		assertFalse(encounter.isOpen());
		assertTrue(oldTask.isCancelled());
		assertFalse(oldLock.isActive());
		assertNull(owner.pendingScriptOption);
		assertNull(chained.scriptDialogueFrames);
		assertEquals(0, optionCalls.get());
		assertNull(stale.beginEncounter(
				"stale", 3200, 3200, 3204, 3204, 0));
		assertNotNull(new ScriptedPlayer(owner).beginEncounter(
				"new", 3200, 3200, 3204, 3204, 0));
	}

	@Test
	public void validatesIdsCoordinatesParticipantAndGenerationCaps() {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3199, 3199, 0);
		ScriptedPlayer scripted = new ScriptedPlayer(owner);
		assertNull(scripted.beginEncounter(
				null, 3200, 3200, 3200, 3200, 0));
		assertNull(scripted.beginEncounter(
				"", 3200, 3200, 3200, 3200, 0));
		assertNull(scripted.beginEncounter(
				"abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklm",
				3200, 3200, 3200, 3200, 0));
		assertNull(scripted.beginEncounter(
				"fraction", 3200.5, 3200, 3200, 3200, 0));
		assertNull(scripted.beginEncounter(
				"nan", Double.NaN, 3200, 3200, 3200, 0));
		assertNull(scripted.beginEncounter(
				"plane", 3200, 3200, 3200, 3200, 4));
		assertNull(scripted.beginEncounter(
				"reversed", 3201, 3200, 3200, 3200, 0));
		assertNull(scripted.beginEncounter(
				"wide", 3200, 3200, 3264, 3200, 0));
		assertNull(scripted.beginEncounter(
				"unloaded", 4000, 4000, 4000, 4000, 0));

		ScriptEncounterHandle participants = scripted.beginEncounter(
				"participants", 3200, 3200, 3200, 3200, 0);
		assertNotNull(participants);
		for (int slot = 2; slot <= 8; slot++) {
			assertTrue(participants.addParticipant(new ScriptedPlayer(
					support.player(slot, 3200, 3200, 0))));
		}
		assertFalse(participants.addParticipant(new ScriptedPlayer(
				support.player(9, 3200, 3200, 0))));
		assertTrue(participants.close());

		for (int index = 0; index < 64; index++) {
			int x = 3200 + index % 8;
			int y = 3200 + index / 8;
			ScriptEncounterTestSupport.TestClient capOwner =
					support.player(index + 1, 3199, 3199, 0);
			assertNotNull(new ScriptedPlayer(capOwner).beginEncounter(
					"cap-" + index, x, y, x, y, 0));
		}
		ScriptEncounterTestSupport.TestClient overflow =
				support.player(100, 3199, 3199, 0);
		assertNull(new ScriptedPlayer(overflow).beginEncounter(
				"cap-overflow", 3210, 3210, 3210, 3210, 0));
	}

	@Test
	public void logoutAndRemovalUseOwnerAwareCleanup() {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3200, 3200, 0);
		ScriptEncounterTestSupport.TestClient participant =
				support.player(2, 3201, 3200, 0);
		ScriptEncounterHandle encounter =
				support.encounter(owner, "lifecycle", 3200, 3200, 3204, 3204, 0);
		assertTrue(encounter.addParticipant(new ScriptedPlayer(participant)));

		ScriptLifecycleService.getInstance().onPlayerRemoved(participant);
		assertTrue(encounter.isOpen());
		assertEquals(1, encounter.participants().length());
		ScriptLifecycleService.getInstance().onExplicitLogout(owner);
		assertFalse(encounter.isOpen());
	}

	@Test
	public void retainedFacadeRequiresExactLiveIdentityOutputLocksAndEpoch() {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3200, 3200, 0);
		ScriptedPlayer retained = new ScriptedPlayer(owner);
		assertTrue(retained.state("authority").setBoolean("live", true));
		ScriptEncounterHandle encounter = retained.beginEncounter(
				"facade-authority", 3200, 3200, 3204, 3204, 0);
		ScriptLockHandle action = retained.getActions().lock(100);
		assertNotNull(encounter);
		assertNotNull(action);
		assertFalse(retained.state("authority").setBoolean("locked", true));
		assertTrue(action.release());

		com.rs2.util.Stream output = owner.outStream;
		owner.outStream = null;
		assertFalse(retained.state("authority").setBoolean("output", true));
		owner.outStream = output;
		owner.disconnected = true;
		assertFalse(retained.state("authority").setBoolean("disconnected", true));
		owner.disconnected = false;

		support.player(1, 3201, 3201, 0);
		assertFalse(retained.state("authority").setBoolean("identity", true));
		PlayerHandler.players[1] = owner;
		ScriptLifecycleService.getInstance().onPlayerRemoved(owner);
		assertFalse(retained.state("authority").setBoolean("epoch", true));
		assertTrue(retained.after(1,
				ScriptHost.getInstance().getContext().eval("js", "() => 1"))
				.isCancelled());
		assertFalse(encounter.isOpen());
	}

	@Test
	public void dialogueContinuationRejectsReplacementLogoutAndRemoval() {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3200, 3200, 0);
		ScriptEncounterTestSupport.TestClient participant =
				support.player(2, 3201, 3200, 0);
		ScriptedPlayer ownerFacade = new ScriptedPlayer(owner);
		ScriptedPlayer participantFacade = new ScriptedPlayer(participant);
		AtomicInteger calls = new AtomicInteger();
		Value callback = ScriptHost.getInstance().getContext().asValue(
				(ProxyExecutable) arguments -> {
					calls.incrementAndGet();
					return null;
				});

		ownerFacade.getDialogue().options(
				new String[] {"One", "Two"}, callback);
		assertNotNull(owner.pendingScriptOption);
		support.player(1, 3202, 3202, 0);
		assertFalse(DialogueOptions.handleScriptDialogueOption(owner, 9157));
		assertEquals(0, calls.get());
		assertNull(owner.pendingScriptOption);

		PlayerHandler.players[1] = owner;
		ownerFacade.getDialogue().options(
				new String[] {"One", "Two"}, callback);
		assertNotNull(owner.pendingScriptOption);
		ScriptLifecycleService.getInstance().onExplicitLogout(owner);
		assertNull(owner.pendingScriptOption);
		assertEquals(0L, owner.pendingScriptOptionToken);

		participantFacade.getDialogue().options(
				new String[] {"One", "Two"}, callback);
		assertNotNull(participant.pendingScriptOption);
		ScriptLifecycleService.getInstance().onPlayerRemoved(participant);
		assertNull(participant.pendingScriptOption);
		assertEquals(0L, participant.pendingScriptOptionToken);
		assertEquals(0, calls.get());
	}

	@Test
	public void rejectedReloadPreservesEncounterTasksAndLocks()
			throws Exception {
		ScriptEncounterTestSupport.TestClient owner =
				support.player(1, 3200, 3200, 0);
		ScriptEncounterTestSupport.TestClient chained =
				support.player(2, 3201, 3200, 0);
		ScriptedPlayer scripted = new ScriptedPlayer(owner);
		ScriptedPlayer chainedFacade = new ScriptedPlayer(chained);
		ScriptEncounterHandle encounter = scripted.beginEncounter(
				"failed-reload", 3200, 3200, 3204, 3204, 0);
		Context activeContext = ScriptHost.getInstance().getContext();
		Value callback = activeContext.eval("js", "() => 1");
		scripted.getDialogue().options(
				new String[] {"One", "Two"}, callback);
		chainedFacade.getDialogue().statement("First").statement("Second").end();
		long optionToken = owner.pendingScriptOptionToken;
		long chainToken = chained.scriptDialogueToken;
		assertTrue(optionToken != 0L);
		assertTrue(chainToken != 0L);
		ScriptTaskHandle task = scripted.after(100, callback);
		ScriptLockHandle lock = scripted.getActions().lock(100);
		long generation = ScriptHost.getInstance().getActiveGeneration();
		Path content = Files.createTempDirectory("wp3-rejected-reload");
		Files.write(content.resolve("loader.js"),
				"not valid javascript !!!".getBytes(StandardCharsets.UTF_8));
		String previousContentDir =
				System.getProperty("singlescape.contentDir");
		try {
			System.setProperty("singlescape.contentDir",
					content.toAbsolutePath().toString());
			ScriptHost.getInstance().reload();
		} finally {
			if (previousContentDir == null) {
				System.clearProperty("singlescape.contentDir");
			} else {
				System.setProperty(
						"singlescape.contentDir", previousContentDir);
			}
		}

		assertEquals(generation,
				ScriptHost.getInstance().getActiveGeneration());
		assertTrue(encounter.isOpen());
		assertFalse(task.isCancelled());
		assertTrue(lock.isActive());
		assertNotNull(owner.pendingScriptOption);
		assertEquals(optionToken, owner.pendingScriptOptionToken);
		assertNotNull(chained.scriptDialogueFrames);
		assertEquals(chainToken, chained.scriptDialogueToken);
	}
}
