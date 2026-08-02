package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Test;

import com.rs2.script.activation.ScriptRuntimeReport;
import com.rs2.script.registries.CommandHandlerRegistry;

/**
 * Proves the two-phase runtime activation protocol against the real reload
 * path with a synthetic projection adapter: old/new same-key handoff, exact
 * abort restoration before publication, atomic visibility at the no-throw
 * commit line, no unload invocation on any abort, mandatory commit after any
 * attempted unload (including a mutating/throwing hook), contained observer
 * failures, and explicit degraded handling after final cleanup failure.
 */
public class RuntimeActivationTransactionTest {

	private String previousContentDir;
	private Path contentRoot;

	@After
	public void restore() {
		ScriptRuntimeTestFixture.reset();
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
	}

	@Test
	public void prepareFailureAbortsWithoutReservingOrStaging() throws Exception {
		installLoader(commandScript("stable", "old"));
		SyntheticProjectionAdapter adapter = newAdapter(
				Collections.singletonList("boss-slot-1"),
				Collections.emptyList());
		adapter.failAt = SyntheticProjectionAdapter.Stage.PREPARE;
		ScriptHost host = ScriptHost.getInstance();
		host.setProjectionAdapterForTesting(adapter);

		host.reload();

		assertNull(host.getContext());
		assertEquals(0L, host.getActiveGeneration());
		assertNull(adapter.selectedGeneration);
		assertTrue(adapter.footprints.isEmpty());
		assertTrue(adapter.log.get(0).startsWith("prepare:"));
	}

	@Test
	public void reserveFailureStagesNothingAndRejectsThirdPartyOwners()
			throws Exception {
		installLoader(commandScript("stable", "old"));
		SyntheticProjectionAdapter adapter = newAdapter(
				Collections.singletonList("boss-slot-1"),
				Collections.emptyList());
		adapter.footprints.put("boss-slot-1", 99L);
		ScriptHost host = ScriptHost.getInstance();
		host.setProjectionAdapterForTesting(adapter);

		host.reload();

		assertNull(host.getContext());
		assertNull(adapter.selectedGeneration);
		assertEquals(Long.valueOf(99L), adapter.footprints.get("boss-slot-1"));
	}

	@Test
	public void shadowFailureReversesIntentsLifoAndKeepsPredecessorSelected()
			throws Exception {
		installLoader(commandScript("stable", "old"));
		SyntheticProjectionAdapter adapter = newAdapter(
				Arrays.asList("slot-a", "slot-b"), Collections.emptyList());
		ScriptHost host = ScriptHost.getInstance();
		host.setProjectionAdapterForTesting(adapter);
		host.reload();
		Context stable = host.getContext();
		long oldGeneration = host.getActiveGeneration();
		assertEquals(Long.valueOf(oldGeneration), adapter.selectedGeneration);

		adapter.resetRunState();
		adapter.failAt = SyntheticProjectionAdapter.Stage.APPLY_SHADOW;
		writeLoader(commandScript("candidate", "new"));
		host.reload();

		assertSame(stable, host.getContext());
		assertEquals(oldGeneration, host.getActiveGeneration());
		assertNull(CommandHandlerRegistry.get("candidate"));
		assertNotNull(CommandHandlerRegistry.get("stable"));
		assertEquals(Long.valueOf(oldGeneration), adapter.selectedGeneration);
		int shadowA = adapter.log.indexOf("shadow:slot-a");
		int unshadowB = adapter.log.indexOf("unshadow:slot-b");
		int unshadowA = adapter.log.indexOf("unshadow:slot-a");
		assertTrue(shadowA >= 0);
		assertTrue(unshadowA > shadowA);
		assertTrue(unshadowB > shadowA);
		assertTrue(unshadowA > unshadowB);
		assertFalse(containsAny(adapter.log, "undo", "commit:"));
		assertTrue(adapter.log.contains("release-reservations"));
	}

	@Test
	public void verifyShadowFailureRestoresExactlyLikeShadowFailure()
			throws Exception {
		installLoader(commandScript("stable", "old"));
		SyntheticProjectionAdapter adapter = newAdapter(
				Collections.singletonList("slot-a"),
				Collections.emptyList());
		ScriptHost host = ScriptHost.getInstance();
		host.setProjectionAdapterForTesting(adapter);
		host.reload();
		Context stable = host.getContext();

		adapter.resetRunState();
		adapter.failAt = SyntheticProjectionAdapter.Stage.VERIFY_SHADOW;
		writeLoader(commandScript("candidate", "new"));
		host.reload();

		assertSame(stable, host.getContext());
		assertNull(CommandHandlerRegistry.get("candidate"));
		assertNotNull(CommandHandlerRegistry.get("stable"));
		assertTrue(adapter.log.contains("unshadow:slot-a"));
		assertFalse(containsAny(adapter.log, "undo", "commit:"));
	}

	@Test
	public void retireFailureRestoresEveryRetiredIdentityBeforeCandidateCleanup()
			throws Exception {
		installLoader(commandScript("stable", "old"));
		SyntheticProjectionAdapter adapter = newAdapter(
				Collections.singletonList("boss-slot-1"),
				Collections.singletonList("boss-slot-1"));
		ScriptHost host = ScriptHost.getInstance();
		host.setProjectionAdapterForTesting(adapter);
		host.reload();
		Context stable = host.getContext();
		long oldGeneration = host.getActiveGeneration();

		adapter.resetRunState();
		adapter.failAt = SyntheticProjectionAdapter.Stage.RETIRE;
		writeLoader(commandScript("candidate", "new"));
		host.reload();

		assertSame(stable, host.getContext());
		assertEquals(oldGeneration, host.getActiveGeneration());
		assertEquals(Long.valueOf(oldGeneration),
				adapter.footprints.get("boss-slot-1"));
		int undoAt = adapter.log.indexOf("undo");
		int unshadowAt = adapter.log.indexOf("unshadow:boss-slot-1");
		int releaseAt = adapter.log.indexOf("release-reservations");
		assertTrue(undoAt >= 0);
		assertTrue(unshadowAt > undoAt);
		assertTrue(releaseAt > unshadowAt);
		assertFalse(adapter.log.contains("commit:"));
	}

	@Test
	public void firstUndoFailureRetriesIdempotentlyWhileHandoffIsHeld()
			throws Exception {
		installLoader(commandScript("stable", "old"));
		SyntheticProjectionAdapter adapter = newAdapter(
				Collections.singletonList("boss-slot-1"),
				Collections.singletonList("boss-slot-1"));
		ScriptHost host = ScriptHost.getInstance();
		host.setProjectionAdapterForTesting(adapter);
		host.reload();
		Context stable = host.getContext();
		long oldGeneration = host.getActiveGeneration();

		adapter.resetRunState();
		adapter.failAt = SyntheticProjectionAdapter.Stage.RETIRE;
		adapter.failFirstUndo = true;
		writeLoader(commandScript("candidate", "new"));
		host.reload();

		assertSame(stable, host.getContext());
		assertEquals(oldGeneration, host.getActiveGeneration());
		assertEquals(Long.valueOf(oldGeneration),
				adapter.footprints.get("boss-slot-1"));
		assertEquals(2, countEntries(adapter.log, "undo"));
	}

	@Test
	public void persistentUndoFailureIsFatalAndNeverClaimsCleanRollback()
			throws Exception {
		installLoader(commandScript("stable", "old"));
		SyntheticProjectionAdapter adapter = newAdapter(
				Collections.singletonList("boss-slot-1"),
				Collections.singletonList("boss-slot-1"));
		ScriptHost host = ScriptHost.getInstance();
		host.setProjectionAdapterForTesting(adapter);
		host.reload();
		Context stable = host.getContext();

		adapter.resetRunState();
		adapter.failAt = SyntheticProjectionAdapter.Stage.RETIRE;
		adapter.failAllUndo = true;
		writeLoader(commandScript("candidate", "new"));
		host.reload();

		assertSame(stable, host.getContext());
		assertEquals(3, countEntries(adapter.log, "undo"));
		assertTrue(adapter.log.contains("release-reservations"));
		assertFalse(adapter.log.contains("commit:"));
		assertFalse(host.getRuntimeDiagnostics().isEmpty());
	}

	@Test
	public void checkpointFailureNeverInvokesUnloadAndRestoresPredecessor()
			throws Exception {
		installLoader(moduleScript("alpha", 1,
				"function () { log('alpha-unload'); throw new Error('unload-boom'); }",
				null, commandScript("stable", "old")));
		ScriptHost host = ScriptHost.getInstance();
		host.setProjectionAdapterForTesting(newAdapter(
				Collections.singletonList("boss-slot-1"),
				Collections.singletonList("boss-slot-1")));
		host.reload();
		Context stable = host.getContext();
		long oldGeneration = host.getActiveGeneration();
		assertNull(host.getRuntimeReport().unloadResult());

		SyntheticProjectionAdapter failing = newAdapter(
				Collections.singletonList("boss-slot-1"),
				Collections.singletonList("boss-slot-1"));
		failing.failAt = SyntheticProjectionAdapter.Stage.CHECKPOINT;
		host.setProjectionAdapterForTesting(failing);
		writeLoader(moduleScript("beta", 1, null, null,
				commandScript("candidate", "new")));
		try (Capture capture = new Capture()) {
			host.reload();
			assertEquals(0, countEntries(capture.out(), "alpha-unload"));
		}

		assertSame(stable, host.getContext());
		assertEquals(oldGeneration, host.getActiveGeneration());
		assertNull(CommandHandlerRegistry.get("candidate"));
		assertNotNull(CommandHandlerRegistry.get("stable"));
		assertNull(host.getRuntimeReport().unloadResult());
		assertFalse(failing.log.contains("commit:"));
	}

	@Test
	public void sameFootprintHandoffPublishesAtomicallyWithOldVisibleThroughPrepare()
			throws Exception {
		installLoader(moduleScript("alpha", 1,
				"function () { log('alpha-unload'); }", null,
				commandScript("stable", "old")));
		SyntheticProjectionAdapter adapter = newAdapter(
				Collections.singletonList("boss-slot-1"),
				Collections.singletonList("boss-slot-1"));
		AtomicReference<Long> generationDuringReserve = new AtomicReference<>();
		adapter.onReserved = () -> generationDuringReserve
				.set(adapter.selectedGeneration);
		ScriptHost host = ScriptHost.getInstance();
		host.setProjectionAdapterForTesting(adapter);
		try (Capture capture = new Capture()) {
			host.reload();
			long firstGeneration = host.getActiveGeneration();
			assertEquals(Long.valueOf(firstGeneration),
					adapter.selectedGeneration);
			assertEquals(Long.valueOf(firstGeneration),
					adapter.footprints.get("boss-slot-1"));

			writeLoader(moduleScript("beta", 1, null,
					"function () { log('beta-load'); }",
					commandScript("replacement", "new")));
			host.reload();

			assertEquals(Long.valueOf(firstGeneration),
					generationDuringReserve.get());
			assertEquals(firstGeneration + 1L, host.getActiveGeneration());
			assertEquals(Long.valueOf(firstGeneration + 1L),
					adapter.selectedGeneration);
			assertEquals(Long.valueOf(firstGeneration + 1L),
					adapter.footprints.get("boss-slot-1"));
			assertNull(CommandHandlerRegistry.get("stable"));
			assertNotNull(CommandHandlerRegistry.get("replacement"));
			assertEquals(1, countEntries(capture.out(), "alpha-unload"));
			assertEquals(1, countEntries(capture.out(), "beta-load"));
		}
	}

	@Test
	public void thirdPartyWriterIsBlockedDuringHandoffAndRevalidatedAfter()
			throws Exception {
		installLoader(commandScript("stable", "old"));
		SyntheticProjectionAdapter adapter = newAdapter(
				Collections.singletonList("boss-slot-1"),
				Collections.singletonList("boss-slot-1"));
		AtomicReference<String> blocked = new AtomicReference<>();
		adapter.onReserved = () -> {
			try {
				adapter.thirdPartyWrite("boss-slot-1", 99L);
			} catch (IllegalStateException expected) {
				blocked.set(expected.getMessage());
			}
		};
		ScriptHost host = ScriptHost.getInstance();
		host.setProjectionAdapterForTesting(adapter);
		host.reload();
		assertTrue(blocked.get() != null
				&& blocked.get().contains("blocked by handoff reservation"));

		adapter.thirdPartyWrite("boss-slot-1", 99L);
		assertEquals(Long.valueOf(99L), adapter.footprints.get("boss-slot-1"));
	}

	@Test
	public void mutatingThrowingUnloadIsFollowedImmediatelyByMandatoryCommit()
			throws Exception {
		installLoader(moduleScript("alpha", 1,
				"function () { log('unload-mutation'); throw new Error('unload-boom'); }",
				null, commandScript("stable", "old")));
		SyntheticProjectionAdapter adapter = newAdapter(
				Collections.singletonList("boss-slot-1"),
				Collections.singletonList("boss-slot-1"));
		ScriptHost host = ScriptHost.getInstance();
		host.setProjectionAdapterForTesting(adapter);
		host.reload();
		long firstGeneration = host.getActiveGeneration();

		writeLoader(commandScript("replacement", "new"));
		try (Capture capture = new Capture()) {
			host.reload();
			long secondGeneration = host.getActiveGeneration();
			assertEquals(firstGeneration + 1L, secondGeneration);
			assertNotNull(CommandHandlerRegistry.get("replacement"));
			assertNull(CommandHandlerRegistry.get("stable"));
			assertEquals(1, countEntries(capture.out(), "unload-mutation"));
			ScriptRuntimeReport report = host.getRuntimeReport();
			assertNotNull(report.unloadResult());
			assertTrue(report.unloadResult().threw());
			assertEquals("alpha", report.unloadResult().identity());
			assertTrue(report.unloadResult().message().contains("unload-boom"));
			assertFalse(containsAny(adapter.log, "undo", "remove-shadow",
					"release-reservations"));
			assertTrue(adapter.log.contains("commit:" + secondGeneration));
			assertTrue(adapter.log.contains("dispose"));
		}
	}

	@Test
	public void throwingOnLoadLeavesCandidatePublishedAndReportsDiagnostics()
			throws Exception {
		installLoader(moduleScript("alpha", 1, null,
				"function () { log('load-mutation'); throw new Error('load-boom'); }",
				commandScript("stable", "old")));
		SyntheticProjectionAdapter adapter = newAdapter(
				Collections.emptyList(), Collections.emptyList());
		ScriptHost host = ScriptHost.getInstance();
		host.setProjectionAdapterForTesting(adapter);
		try (Capture capture = new Capture()) {
			host.reload();

			assertNotNull(host.getContext());
			assertTrue(host.getActiveGeneration() > 0L);
			assertNotNull(CommandHandlerRegistry.get("stable"));
			assertEquals(1, countEntries(capture.out(), "load-mutation"));
			assertTrue(containsAny(host.getRuntimeDiagnostics(),
					"onLoad observer failed", "load-boom"));
		}
	}

	@Test
	public void finalDisposeFailureIsDegradedButNeverRollsBackPublication()
			throws Exception {
		installLoader(commandScript("stable", "old"));
		SyntheticProjectionAdapter adapter = newAdapter(
				Collections.singletonList("boss-slot-1"),
				Collections.singletonList("boss-slot-1"));
		ScriptHost host = ScriptHost.getInstance();
		host.setProjectionAdapterForTesting(adapter);
		host.reload();
		long firstGeneration = host.getActiveGeneration();

		adapter.resetRunState();
		adapter.failDispose = true;
		writeLoader(commandScript("replacement", "new"));
		host.reload();

		assertEquals(firstGeneration + 1L, host.getActiveGeneration());
		assertNotNull(CommandHandlerRegistry.get("replacement"));
		assertEquals(Long.valueOf(firstGeneration + 1L),
				adapter.selectedGeneration);
		assertEquals(3, countEntries(adapter.log, "dispose"));
		assertTrue(containsAny(host.getRuntimeDiagnostics(),
				"finalize degraded", "disposal degraded"));
	}

	@Test
	public void moduleRegistrationAfterCommitIsRejectedThroughTheEnvelope()
			throws Exception {
		installLoader(moduleScript("alpha", 1, null, null,
				"onCommand('stable', function () {});"
						+ "globalThis.registerLateModule = function () {"
						+ "registerContentModule({ id: 'late', schemaVersion: 1 },"
						+ "function () { onCommand('late', function () {}); });"
						+ "};"));
		SyntheticProjectionAdapter adapter = newAdapter(
				Collections.emptyList(), Collections.emptyList());
		ScriptHost host = ScriptHost.getInstance();
		host.setProjectionAdapterForTesting(adapter);
		host.reload();
		Context stable = host.getContext();
		long generation = host.getActiveGeneration();

		try {
			stable.getBindings("js").getMember("registerLateModule").execute();
			fail("post-commit module registration should fail");
		} catch (org.graalvm.polyglot.PolyglotException expected) {
			assertTrue(expected.getMessage().contains("candidate"));
		}
		assertSame(stable, host.getContext());
		assertEquals(generation, host.getActiveGeneration());
		assertNotNull(CommandHandlerRegistry.get("stable"));
		assertNull(CommandHandlerRegistry.get("late"));
	}

	private SyntheticProjectionAdapter newAdapter(List<String> candidateFootprint,
			List<String> predecessorFootprint) {
		return new SyntheticProjectionAdapter(candidateFootprint,
				predecessorFootprint);
	}

	private void installLoader(String content) throws Exception {
		contentRoot = Files.createTempDirectory("activation-transaction");
		setContentDir(contentRoot);
		writeLoader(content);
	}

	private void writeLoader(String content) throws Exception {
		Files.write(contentRoot.resolve("loader.js"),
				content.getBytes(StandardCharsets.UTF_8));
	}

	private void setContentDir(Path root) {
		previousContentDir = System.getProperty("singlescape.contentDir");
		System.setProperty("singlescape.contentDir",
				root.toAbsolutePath().toString());
	}

	private static String commandScript(String command, String result) {
		return "onCommand('" + command + "', function () { return '"
				+ result + "'; });";
	}

	private static String moduleScript(String id, int version,
			String onUnload, String onLoad, String registrations) {
		StringBuilder source = new StringBuilder("registerContentModule({id:'");
		source.append(id).append("',schemaVersion:").append(version);
		if (onLoad != null) {
			source.append(",onLoad:").append(onLoad);
		}
		if (onUnload != null) {
			source.append(",onUnload:").append(onUnload);
		}
		source.append("},function(){").append(registrations)
				.append("});");
		return source.toString();
	}

	private static boolean containsAny(List<String> values, String... needles) {
		for (String value : values) {
			for (String needle : needles) {
				if (value.contains(needle)) {
					return true;
				}
			}
		}
		return false;
	}

	private static int countEntries(List<String> values, String needle) {
		int count = 0;
		for (String value : values) {
			if (value.contains(needle)) {
				count++;
			}
		}
		return count;
	}

	private static int countEntries(String haystack, String needle) {
		int count = 0;
		int from = 0;
		while ((from = haystack.indexOf(needle, from)) >= 0) {
			count++;
			from += needle.length();
		}
		return count;
	}

	/** Captures guest {@code log(...)} output written to stdout. */
	private static final class Capture implements AutoCloseable {

		private final PrintStream original = System.out;
		private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		private Capture() {
			System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
		}

		String out() {
			return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
		}

		@Override
		public void close() {
			System.setOut(original);
		}
	}

}
