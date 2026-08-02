package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Test;

import com.rs2.script.registries.CommandHandlerRegistry;

public class ScriptHostDispatchLeaseTest {

	private String previousContentDir;

	@After
	public void restoreProperty() {
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
	}

	@Test
	public void lookupAndInvocationRemainOnOneGenerationWhileReloadWaits()
			throws Exception {
		Path root = Files.createTempDirectory("script-dispatch-lease");
		Path loader = root.resolve("loader.js");
		setContentDir(root);
		Files.write(loader, script("old", "old").getBytes(StandardCharsets.UTF_8));
		ScriptHost host = ScriptHost.getInstance();
		host.reload();
		long oldGeneration = host.getActiveGeneration();

		CountDownLatch lookupReached = new CountDownLatch(1);
		CountDownLatch allowInvocation = new CountDownLatch(1);
		AtomicReference<String> result = new AtomicReference<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();

		Thread dispatch = new Thread(() -> {
			try {
				assertEquals(ScriptHost.DispatchResult.CONSUMED,
						host.dispatchActive(state -> {
							Value handler = CommandHandlerRegistry.get(state, "old");
							lookupReached.countDown();
							await(allowInvocation);
							return handler;
						}, (generation, handler) -> {
							assertEquals(oldGeneration, generation);
							result.set(handler.execute().asString());
						}));
			} catch (Throwable error) {
				failure.set(error);
			}
		}, "script-dispatch-test");
		dispatch.start();
		assertTrue(lookupReached.await(5, TimeUnit.SECONDS));

		Files.write(loader, script("replacement", "new")
				.getBytes(StandardCharsets.UTF_8));
		Thread reload = new Thread(host::reload, "script-reload-test");
		reload.start();
		allowInvocation.countDown();
		dispatch.join(5000);
		reload.join(5000);

		if (failure.get() != null) {
			throw new AssertionError(failure.get());
		}
		assertEquals("old", result.get());
		assertTrue(host.getActiveGeneration() > oldGeneration);
		AtomicReference<String> replacement = new AtomicReference<>();
		assertEquals(ScriptHost.DispatchResult.CONSUMED,
				host.dispatchActive(
						state -> CommandHandlerRegistry.get(state, "replacement"),
						(generation, handler) ->
								replacement.set(handler.execute().asString())));
		assertEquals("new", replacement.get());
	}

	@Test
	public void directRegistryReaderKeepsOneImmutableGenerationDuringReload()
			throws Exception {
		Path root = Files.createTempDirectory("script-direct-reader-lease");
		Path loader = root.resolve("loader.js");
		setContentDir(root);
		Files.write(loader, script("old", "old").getBytes(StandardCharsets.UTF_8));
		ScriptHost host = ScriptHost.getInstance();
		host.reload();

		CountDownLatch readReached = new CountDownLatch(1);
		CountDownLatch allowRead = new CountDownLatch(1);
		AtomicReference<Map<String, Value>> result = new AtomicReference<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread reader = new Thread(() -> {
			try {
				result.set(host.readActiveRegistry(state -> {
					readReached.countDown();
					await(allowRead);
					return CommandHandlerRegistry.all(state);
				}));
			} catch (Throwable error) {
				failure.set(error);
			}
		}, "script-direct-reader-test");
		reader.start();
		assertTrue(readReached.await(5, TimeUnit.SECONDS));

		Files.write(loader, script("replacement", "new")
				.getBytes(StandardCharsets.UTF_8));
		Thread reload = new Thread(host::reload, "script-direct-reader-reload");
		reload.start();
		allowRead.countDown();
		reader.join(5000);
		reload.join(5000);

		if (failure.get() != null) {
			throw new AssertionError(failure.get());
		}
		assertTrue(result.get().containsKey("old"));
		assertTrue(!result.get().containsKey("replacement"));
		assertTrue(!CommandHandlerRegistry.all().containsKey("old"));
		assertTrue(CommandHandlerRegistry.all().containsKey("replacement"));
	}

	@Test
	public void throwingInvocationReleasesTheLease() throws Exception {
		Path root = Files.createTempDirectory("script-dispatch-throw");
		Path loader = root.resolve("loader.js");
		setContentDir(root);
		Files.write(loader, script("throwing", "unused")
				.getBytes(StandardCharsets.UTF_8));
		ScriptHost host = ScriptHost.getInstance();
		host.reload();

		try {
			host.dispatchActive(
					state -> CommandHandlerRegistry.get(state, "throwing"),
					(generation, handler) -> {
						throw new IllegalStateException("expected");
					});
		} catch (IllegalStateException expected) {
			assertEquals("expected", expected.getMessage());
		}

		Files.write(loader, script("after", "ok").getBytes(StandardCharsets.UTF_8));
		host.reload();
		assertNotNull(CommandHandlerRegistry.get("after"));
	}

	private static String script(String command, String result) {
		return "onCommand('" + command + "', function () { return '"
				+ result + "'; });";
	}

	private void setContentDir(Path root) {
		previousContentDir = System.getProperty("singlescape.contentDir");
		System.setProperty("singlescape.contentDir", root.toAbsolutePath().toString());
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new AssertionError("Timed out waiting for dispatch release");
			}
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw new AssertionError(error);
		}
	}
}
