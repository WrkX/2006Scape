package com.rs2;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Test;

import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.ScriptHost;
import com.rs2.script.ScriptedPlayer;

public class GameEngineScriptCycleTest {

	private static final int SLOT = 25;
	private String previousContentDir;
	private Player previousPlayer;

	@After
	public void cleanUp() {
		Player current = PlayerHandler.players[SLOT];
		if (current != null) {
			com.rs2.script.ScriptLifecycleService.getInstance().onPlayerRemoved(current);
		}
		PlayerHandler.players[SLOT] = previousPlayer;
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
	}

	@Test
	public void productionTickSeamAdvancesScriptScheduler() throws Exception {
		Path root = Files.createTempDirectory("game-engine-script-cycle");
		Files.write(root.resolve("loader.js"),
				"globalThis.calls=0;".getBytes(StandardCharsets.UTF_8));
		previousContentDir = System.getProperty("singlescape.contentDir");
		System.setProperty("singlescape.contentDir", root.toString());
		ScriptHost.getInstance().reload();
		Context context = ScriptHost.getInstance().getContext();

		previousPlayer = PlayerHandler.players[SLOT];
		Player player = new Player(SLOT) { };
		player.playerName = "cycle-player";
		player.isActive = true;
		player.initialized = true;
		player.disconnected = false;
		PlayerHandler.players[SLOT] = player;
		new ScriptedPlayer(player).after(
				1, context.eval("js", "()=>calls++"));

		GameEngine.processScriptLifecycleCycle();
		assertEquals(1, context.eval("js", "calls").asInt());
	}
}
