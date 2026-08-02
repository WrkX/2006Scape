package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Test;

import com.rs2.game.npcs.Npc;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.scheduler.ScriptTaskHandle;

public class SchedulingLifecycleTest {

	private String previousContentDir;
	private Player installedPlayer;
	private Player previousPlayer;
	private int installedSlot = -1;

	@After
	public void cleanUp() {
		if (installedPlayer != null) {
			ScriptLifecycleService.getInstance().onPlayerRemoved(installedPlayer);
			if (installedSlot >= 0 && PlayerHandler.players[installedSlot] == installedPlayer) {
				PlayerHandler.players[installedSlot] = previousPlayer;
			}
		}
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
	}

	@Test
	public void schedulerIsDeterministicCancellableAndGenerationOwned()
			throws Exception {
		Path root = Files.createTempDirectory("script-scheduler");
		Path loader = root.resolve("loader.js");
		write(loader, "globalThis.calls = []; globalThis.failures = 0;");
		load(root.toFile());
		Player player = installPlayer(17, 3200, 3200);
		Context context = ScriptHost.getInstance().getContext();
		ScriptedPlayer scripted = new ScriptedPlayer(player);

		scripted.after(2, callback(context, "() => calls.push('late')"));
		scripted.after(1, callback(context, "() => calls.push('first')"));
		scripted.after(1, callback(context, "() => calls.push('second')"));
		ScriptTaskHandle cancelled = scripted.after(
				1, callback(context, "() => calls.push('cancelled')"));
		assertTrue(cancelled.cancel());
		assertFalse(cancelled.cancel());

		ScriptLifecycleService.getInstance().processGameTick();
		assertEquals("first,second", context.eval("js", "calls.join(',')").asString());
		ScriptLifecycleService.getInstance().processGameTick();
		assertEquals("first,second,late",
				context.eval("js", "calls.join(',')").asString());

		context.eval("js", "globalThis.crossCancelled = 0;");
		scripted.after(1, callback(context, "() => cancelTarget.cancel()"));
		ScriptTaskHandle crossCancelled = scripted.after(
				1, callback(context, "() => crossCancelled++"));
		context.getBindings("js").putMember("cancelTarget", crossCancelled);
		ScriptLifecycleService.getInstance().processGameTick();
		assertEquals(0, context.eval("js", "crossCancelled").asInt());
		assertTrue(crossCancelled.isCancelled());
		ScriptLifecycleService.getInstance().processGameTick();
		assertEquals(0, context.eval("js", "crossCancelled").asInt());

		context.eval("js", "globalThis.selfCancelled = 0;");
		ScriptTaskHandle selfCancelled = scripted.every(1,
				callback(context, "(handle) => { selfCancelled++; handle.cancel(); }"));
		ScriptLifecycleService.getInstance().processGameTick();
		ScriptLifecycleService.getInstance().processGameTick();
		assertEquals(1, context.eval("js", "selfCancelled").asInt());
		assertTrue(selfCancelled.isCancelled());

		context.eval("js", "globalThis.repeatCalls = 0;");
		ScriptTaskHandle repeating = scripted.every(
				2, callback(context, "() => repeatCalls++"));
		for (int tick = 1; tick <= 6; tick++) {
			ScriptLifecycleService.getInstance().processGameTick();
			assertEquals(tick / 2, context.eval("js", "repeatCalls").asInt());
		}
		assertTrue(repeating.cancel());

		ScriptTaskHandle failing = scripted.every(1,
				callback(context, "() => { failures++; throw new Error('expected'); }"));
		ScriptLifecycleService.getInstance().processGameTick();
		ScriptLifecycleService.getInstance().processGameTick();
		assertEquals(1, context.eval("js", "failures").asInt());
		assertTrue(failing.isCancelled());

		ScriptTaskHandle survivesFailedReload = scripted.after(
				1, callback(context, "() => calls.push('survived')"));
		write(loader, "not valid javascript !!!");
		ScriptHost.getInstance().reload();
		assertFalse(survivesFailedReload.isCancelled());
		ScriptLifecycleService.getInstance().processGameTick();
		assertEquals("first,second,late,survived",
				context.eval("js", "calls.join(',')").asString());

		ScriptTaskHandle stale = scripted.after(
				100, callback(context, "() => calls.push('stale')"));
		write(loader, "globalThis.replacement = true;");
		ScriptHost.getInstance().reload();
		assertTrue(stale.isCancelled());

		Context replacement = ScriptHost.getInstance().getContext();
		replacement.eval("js", "globalThis.logoutCalls = 0;");
		ScriptedPlayer current = new ScriptedPlayer(player);
		ScriptTaskHandle logoutCancelled = current.after(
				1, callback(replacement, "() => logoutCalls++"));
		ScriptLifecycleService.getInstance().onExplicitLogout(player);
		ScriptLifecycleService.getInstance().processGameTick();
		assertTrue(logoutCancelled.isCancelled());
		assertEquals(0, replacement.eval("js", "logoutCalls").asInt());
	}

	@Test
	public void lifecycleDispatchIsExactIdempotentAndAreaFailuresAreIsolated()
			throws Exception {
		Path root = Files.createTempDirectory("script-lifecycle");
		Path loader = root.resolve("loader.js");
		String lifecycleSource =
				"globalThis.counts={login:0,logout:0,death:0,pickup:0,enter:0,leave:0,afterFailure:0,plane:0};"
				+ "globalThis.areaOrder=[];"
				+ "onLogin(c=>counts.login++);"
				+ "onLogout(c=>counts.logout++);"
				+ "onNpcDeath(42,c=>counts.death++);"
				+ "onItemPickup(100,c=>{counts.pickup+=c.amount;});"
				+ "onEnterArea({id:'a-failure',minX:10,minY:10,maxX:20,maxY:20},"
				+ "c=>{throw new Error('expected area failure');});"
				+ "onEnterArea({id:'b-success',minX:10,minY:10,maxX:20,maxY:20},"
				+ "c=>counts.afterFailure++);"
				+ "onEnterArea({id:'tracked',minX:10,minY:10,maxX:20,maxY:20},"
				+ "c=>counts.enter++);"
				+ "onLeaveArea({id:'tracked',minX:10,minY:10,maxX:20,maxY:20},"
				+ "c=>counts.leave++);"
				+ "onEnterArea({id:'z-order',minX:10,minY:10,maxX:20,maxY:20},"
				+ "c=>areaOrder.push('z'));"
				+ "onEnterArea({id:'m-order',minX:10,minY:10,maxX:20,maxY:20},"
				+ "c=>areaOrder.push('m'));"
				+ "onEnterArea({id:'plane-one',minX:10,minY:10,maxX:20,maxY:20,plane:1},"
				+ "c=>counts.plane++);";
		write(loader, lifecycleSource);
		load(root.toFile());
		Player player = installPlayer(18, 15, 15);
		Context context = ScriptHost.getInstance().getContext();
		ScriptLifecycleService service = ScriptLifecycleService.getInstance();

		service.onLogin(player);
		service.onLogin(player);
		assertCount(context, "login", 1);

		service.processGameTick();
		assertCount(context, "enter", 1);
		assertCount(context, "afterFailure", 1);
		assertEquals("m,z", context.eval("js", "areaOrder.join(',')").asString());
		service.processGameTick();
		assertEquals("m,z", context.eval("js", "areaOrder.join(',')").asString());
		assertCount(context, "plane", 0);
		player.heightLevel = 1;
		service.processGameTick();
		assertCount(context, "plane", 1);
		player.heightLevel = 0;
		player.absX = 30;
		service.processGameTick();
		assertCount(context, "leave", 1);

		Npc matchingNpc = new Npc(1, 42);
		matchingNpc.absX = 15;
		matchingNpc.absY = 16;
		service.onNpcDeath(matchingNpc, player);
		service.onNpcDeath(new Npc(2, 43), player);
		assertCount(context, "death", 1);

		service.onItemPickup(player, 100, 4, 15, 16, 0);
		service.onItemPickup(player, 101, 9, 15, 16, 0);
		assertCount(context, "pickup", 4);

		player.absX = 15;
		write(loader, lifecycleSource);
		ScriptHost.getInstance().reload();
		context = ScriptHost.getInstance().getContext();
		service.processGameTick();
		assertCount(context, "login", 0);
		assertCount(context, "logout", 0);
		assertCount(context, "enter", 0);
		assertCount(context, "leave", 0);

		service.onPlayerRemoved(player);
		service.onPlayerRemoved(player);
		assertCount(context, "logout", 1);
		installedPlayer = null;
		PlayerHandler.players[installedSlot] = previousPlayer;
	}

	@Test
	public void lifecycleEventHoldingLeaseCompletesExactlyOnceBeforeReload()
			throws Exception {
		Path root = Files.createTempDirectory("script-lifecycle-race");
		Path loader = root.resolve("loader.js");
		write(loader, "onItemPickup(100,c=>barrier.blockAndRecord());");
		load(root.toFile());
		final Context oldContext = ScriptHost.getInstance().getContext();
		final BlockingRecorder barrier = new BlockingRecorder();
		oldContext.getBindings("js").putMember("barrier", barrier);
		final Player player = installPlayer(19, 15, 15);
		final AtomicReference<Throwable> failure = new AtomicReference<>();

		Thread event = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					ScriptLifecycleService.getInstance().onItemPickup(
							player, 100, 1, 15, 15, 0);
				} catch (Throwable error) {
					failure.set(error);
				}
			}
		}, "lifecycle-event");
		event.start();
		assertTrue(barrier.entered.await(5, TimeUnit.SECONDS));

		write(loader, "onItemPickup(100,c=>{});");
		final CountDownLatch reloadStarted = new CountDownLatch(1);
		final CountDownLatch reloadDone = new CountDownLatch(1);
		Thread reload = new Thread(new Runnable() {
			@Override
			public void run() {
				reloadStarted.countDown();
				try {
					ScriptHost.getInstance().reload();
				} catch (Throwable error) {
					failure.set(error);
				} finally {
					reloadDone.countDown();
				}
			}
		}, "script-reload");
		reload.start();
		assertTrue(reloadStarted.await(5, TimeUnit.SECONDS));
		barrier.release.countDown();
		event.join(5000);
		assertTrue(reloadDone.await(5, TimeUnit.SECONDS));
		assertFalse(event.isAlive());
		assertEquals(null, failure.get());
		assertEquals(1, barrier.calls);
	}

	private void load(File root) {
		previousContentDir = System.getProperty("singlescape.contentDir");
		System.setProperty("singlescape.contentDir", root.getAbsolutePath());
		ScriptHost.getInstance().reload();
	}

	private Player installPlayer(int slot, int x, int y) {
		previousPlayer = PlayerHandler.players[slot];
		Player player = new Player(slot) { };
		player.playerName = "phase-two-test";
		player.absX = x;
		player.absY = y;
		player.heightLevel = 0;
		player.isActive = true;
		player.disconnected = false;
		player.initialized = true;
		PlayerHandler.players[slot] = player;
		installedPlayer = player;
		installedSlot = slot;
		return player;
	}

	private static Value callback(Context context, String source) {
		return context.eval("js", source);
	}

	private static void assertCount(Context context, String name, int expected) {
		assertEquals(expected,
				context.eval("js", "counts." + name).asInt());
	}

	private static void write(Path file, String source) throws Exception {
		Files.write(file, source.getBytes(StandardCharsets.UTF_8));
	}

	public static final class BlockingRecorder {
		private final CountDownLatch entered = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private volatile int calls;

		@HostAccess.Export
		public void blockAndRecord() {
			calls++;
			entered.countDown();
			try {
				if (!release.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("timed out waiting to release lifecycle event");
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("lifecycle event interrupted", e);
			}
		}
	}
}
