package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Test;

import com.rs2.game.players.Client;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.game.players.Trading;
import com.rs2.script.registries.LifecycleRegistry;
import com.rs2.script.social.ScriptSocialRuntime;
import com.rs2.util.Stream;
import org.apollo.util.security.IsaacRandom;

/** Trade gate and private-message observer behavior. */
public class ScriptSocialRuntimeTest {

	private Player requester;
	private Player target;
	private Player previousRequester;
	private Player previousTarget;

	@After
	public void tearDown() {
		ScriptRuntimeTestFixture.reset();
		if (requester != null && requester.playerId >= 0
				&& requester.playerId < PlayerHandler.players.length
				&& PlayerHandler.players[requester.playerId] == requester) {
			PlayerHandler.players[requester.playerId] = previousRequester;
		}
		if (target != null && target.playerId >= 0
				&& target.playerId < PlayerHandler.players.length
				&& PlayerHandler.players[target.playerId] == target) {
			PlayerHandler.players[target.playerId] = previousTarget;
		}
	}

	@Test
	public void tradeRequestProceedsWhenNoHandlerIsRegistered() throws Exception {
		installPlayers(40, 41);
		ScriptRuntimeTestFixture.publishEmpty(context());

		new Trading(requester).requestTrade(target.playerId);

		assertTrue(requester.tradeRequested);
	}

	@Test
	public void tradeRequestCanBeDeniedWithMessage() throws Exception {
		installPlayers(42, 43);
		Context context = context();
		ScriptRuntimeTestFixture.publish(context, new Runnable() {
			@Override
			public void run() {
				LifecycleRegistry.putSingleton("trade-request",
						context.eval("js",
								"(ctx) => ctx.deny('Trading is blocked.')"));
			}
		});

		new Trading(requester).requestTrade(target.playerId);

		assertFalse(requester.tradeRequested);
		assertTrue(messages(requester).contains("Trading is blocked."));
	}

	@Test
	public void tradeRequestAllowsWhenHandlerDoesNotDeny() throws Exception {
		installPlayers(44, 45);
		Context context = context();
		ScriptRuntimeTestFixture.publish(context, new Runnable() {
			@Override
			public void run() {
				LifecycleRegistry.putSingleton("trade-request",
						context.eval("js", "(ctx) => { /* allow */ }"));
			}
		});

		new Trading(requester).requestTrade(target.playerId);

		assertTrue(requester.tradeRequested);
	}

	@Test
	public void privateMessageObserverIsObserveOnly() throws Exception {
		installPlayers(46, 47);
		Context context = context();
		context.eval("js",
				"globalThis.observed = 0;"
						+ "globalThis.observedMessage = null;"
						+ "globalThis.handler = (ctx) => {"
						+ "  globalThis.observed++;"
						+ "  globalThis.observedMessage = ctx.message;"
						+ "};");
		ScriptRuntimeTestFixture.publish(context, new Runnable() {
			@Override
			public void run() {
				Value handler = context.getBindings("js").getMember("handler");
				LifecycleRegistry.putSingleton("private-message", handler);
			}
		});

		ScriptSocialRuntime.getInstance().observePrivateMessage(
				requester, target, "hello there");

		Value bindings = context.getBindings("js");
		assertEquals(1, bindings.getMember("observed").asInt());
		assertEquals("hello there",
				bindings.getMember("observedMessage").asString());
	}

	@Test
	public void wildernessCombatQueriesAreSideEffectFree() throws Exception {
		Player player = Wp5PlayerSupport.player(48);
		try {
			player.isSkulled = true;
			player.wildLevel = 12;
			player.absX = 3000;
			player.absY = 3600;
			player.wildernessWarning = false;

			ScriptedPlayer scripted = Wp5PlayerSupport.scripted(player);
			assertTrue(scripted.getCombat().skulled());
			assertTrue(scripted.getCombat().inWilderness());
			assertEquals(12, scripted.getCombat().wildernessLevel());
			assertFalse(scripted.getCombat().inSafeArea());
			assertFalse(player.wildernessWarning);

			player.absX = Wp5PlayerSupport.X;
			player.absY = Wp5PlayerSupport.Y;
			assertFalse(scripted.getCombat().inWilderness());
			assertEquals(0, scripted.getCombat().wildernessLevel());
			assertTrue(scripted.getCombat().inSafeArea());
		} finally {
			Wp5PlayerSupport.cleanup(player);
		}
	}

	private void installPlayers(int requesterSlot, int targetSlot)
			throws Exception {
		previousRequester = PlayerHandler.players[requesterSlot];
		previousTarget = PlayerHandler.players[targetSlot];
		requester = liveClient(requesterSlot, "requester");
		target = liveClient(targetSlot, "target");
		PlayerHandler.players[requesterSlot] = requester;
		PlayerHandler.players[targetSlot] = target;
	}

	private static Player liveClient(int slot, String name) {
		RecordingPlayer player = new RecordingPlayer(slot);
		player.playerName = name;
		player.initialized = true;
		player.isActive = true;
		player.disconnected = false;
		player.isDead = false;
		player.absX = Wp5PlayerSupport.X;
		player.absY = Wp5PlayerSupport.Y;
		player.heightLevel = 0;
		return player;
	}

	private static Context context() {
		return Context.newBuilder("js")
				.allowHostAccess(HostAccess.ALL)
				.build();
	}

	private static List<String> messages(Player player) {
		return ((RecordingPlayer) player).messages;
	}

	private static final class RecordingPlayer extends Client {
		private final List<String> messages = new ArrayList<>();

		RecordingPlayer(int slot) {
			super(null, slot);
			outStream = new Stream(new byte[65536]);
			outStream.packetEncryption = new IsaacRandom(new int[4]);
		}

		@Override
		public void flushOutStream() {
			if (outStream != null) {
				outStream.currentOffset = 0;
			}
		}

		@Override
		public com.rs2.net.PacketSender getPacketSender() {
			return new com.rs2.net.PacketSender(this) {
				@Override
				public com.rs2.net.PacketSender sendMessage(String s) {
					messages.add(s);
					return this;
				}
			};
		}
	}
}
