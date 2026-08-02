package com.rs2.game.players;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Test;

import com.rs2.net.PacketSender;
import com.rs2.script.ScriptHost;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.scheduler.ScriptTaskHandle;

public class PlayerLifecycleIntegrationTest {

	private String previousContentDir;
	private Player previousPlayer;
	private RecordingPlayer player;
	private static final int SLOT = 23;

	@After
	public void cleanUp() {
		if (PlayerHandler.players[SLOT] == player) {
			PlayerHandler.players[SLOT] = previousPlayer;
		}
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
	}

	@Test
	public void productionLoginAndRemovalSeamsAreExactAndPreserveContinuation()
			throws Exception {
		Context context = load(
				"globalThis.logins=0;globalThis.logouts=0;"
				+ "onLogin(c=>{logins++;throw new Error('expected login');});"
				+ "onLogout(c=>{logouts++;throw new Error('expected logout');});");
		player = installPlayer();
		PlayerHandler handler = new PlayerHandler();

		handler.initializePlayer(player);
		handler.initializePlayer(player);
		assertTrue(player.loginCompleted);
		assertTrue(player.initialized);
		assertEquals(1, context.eval("js", "logins").asInt());

		ScriptTaskHandle task = new ScriptedPlayer(player).after(
				20, context.eval("js", "()=>{}"));
		handler.removePlayer(player);
		assertEquals(1, context.eval("js", "logouts").asInt());
		assertEquals(1, player.destructCalls);
		assertTrue(task.isCancelled());
	}

	private Context load(String source) throws Exception {
		Path root = Files.createTempDirectory("player-lifecycle");
		Files.write(root.resolve("loader.js"), source.getBytes(StandardCharsets.UTF_8));
		previousContentDir = System.getProperty("singlescape.contentDir");
		System.setProperty("singlescape.contentDir", root.toFile().getAbsolutePath());
		ScriptHost.getInstance().reload();
		return ScriptHost.getInstance().getContext();
	}

	private RecordingPlayer installPlayer() {
		previousPlayer = PlayerHandler.players[SLOT];
		RecordingPlayer installed = new RecordingPlayer();
		installed.playerId = SLOT;
		installed.playerName = "player-lifecycle";
		installed.privateChat = 2;
		installed.isActive = true;
		installed.disconnected = false;
		PlayerHandler.players[SLOT] = installed;
		return installed;
	}

	private static final class RecordingPlayer extends Player {
		private final PacketSender sender = new RecordingPacketSender(this);
		private boolean loginCompleted;
		private int destructCalls;

		private RecordingPlayer() {
			super(SLOT);
		}

		@Override
		public PacketSender getPacketSender() {
			return sender;
		}

		@Override
		public void destruct() {
			destructCalls++;
		}
	}

	private static final class RecordingPacketSender extends PacketSender {
		private final RecordingPlayer player;

		private RecordingPacketSender(RecordingPlayer player) {
			super(player);
			this.player = player;
		}

		@Override
		public PacketSender loginPlayer() {
			player.loginCompleted = true;
			return this;
		}
	}
}
