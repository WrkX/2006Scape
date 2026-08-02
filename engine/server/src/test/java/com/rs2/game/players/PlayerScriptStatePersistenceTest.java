package com.rs2.game.players;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Test;

import com.rs2.script.state.PlayerStateNamespace;
import com.rs2.script.state.ScriptStateCodec;

public class PlayerScriptStatePersistenceTest {

	private String previousUserDir;
	private Player previousPlayer;

	@After
	public void restore() {
		if (previousUserDir != null) {
			System.setProperty("user.dir", previousUserDir);
		}
		if (previousPlayer != null || PlayerHandler.players[0] != null) {
			PlayerHandler.players[0] = previousPlayer;
		}
	}

	@Test
	public void validMissingAndMalformedPayloadsInstallInIsolation() {
		Player player = new Player(-1) { };
		PlayerStateNamespace state = new PlayerStateNamespace(
				player.getScriptState(), "feature");
		state.setString("value", "persisted");
		String payload = new ScriptStateCodec().encode(
				player.getScriptState().snapshot());

		Player loaded = new Player(-1) { };
		PlayerSave.installScriptState(loaded, payload, false);
		assertEquals("persisted", new PlayerStateNamespace(
				loaded.getScriptState(), "feature").getString("value"));
		assertNull(loaded.getQuarantinedScriptStatePayload());

		PlayerSave.installScriptState(loaded, "v9.bad", false);
		assertEquals("v9.bad", loaded.getQuarantinedScriptStatePayload());
		assertTrue(loaded.getScriptState().snapshot().getNamespaces().isEmpty());

		PlayerSave.installScriptState(loaded, null, false);
		assertNull(loaded.getQuarantinedScriptStatePayload());
		assertTrue(loaded.getScriptState().snapshot().getNamespaces().isEmpty());
	}

	@Test
	public void duplicatePayloadIsQuarantinedAndNeverInstalled() {
		Player player = new Player(-1) { };
		PlayerSave.installScriptState(player, "v1.AAA", true);
		assertEquals("v1.AAA", player.getQuarantinedScriptStatePayload());
		assertTrue(player.getScriptState().snapshot().getNamespaces().isEmpty());
	}

	@Test
	public void playerSaveRoundTripPreservesStateAndLegacyQuestPoints()
			throws Exception {
		Path root = Files.createTempDirectory("player-state-roundtrip");
		Files.createDirectories(root.resolve("data/characters"));
		previousUserDir = System.getProperty("user.dir");
		System.setProperty("user.dir", root.toString());
		previousPlayer = PlayerHandler.players[0];

		Client player = savableClient("roundtrip");
		player.questPoints = 17;
		new PlayerStateNamespace(player.getScriptState(), "quest.demo")
				.setString("progress", "stage-two");
		PlayerHandler.players[0] = player;
		assertTrue(PlayerSave.saveGame(player));

		Client loaded = new Client(null, 0);
		loaded.playerName = "roundtrip";
		PlayerSave.loadPlayerInfo(loaded, "roundtrip", "password", false);
		assertEquals("stage-two", new PlayerStateNamespace(
				loaded.getScriptState(), "quest.demo").getString("progress"));
		assertEquals(17, loaded.questPoints);
		assertNull(loaded.getQuarantinedScriptStatePayload());
	}

	@Test
	public void failedCandidateWritePreservesPreviousCharacterFile()
			throws Exception {
		Path root = Files.createTempDirectory("player-save-atomic");
		Path characters = Files.createDirectories(root.resolve("data/characters"));
		Path target = characters.resolve("preserve.txt");
		Files.write(target, "last-known-good".getBytes(StandardCharsets.UTF_8));
		previousUserDir = System.getProperty("user.dir");
		System.setProperty("user.dir", root.toString());
		previousPlayer = PlayerHandler.players[0];

		Client player = savableClient("preserve");
		player.playerPass = null;
		PlayerHandler.players[0] = player;
		assertFalse(PlayerSave.saveGame(player));
		assertEquals("last-known-good", new String(Files.readAllBytes(target),
				StandardCharsets.UTF_8));
		try (Stream<Path> files = Files.list(characters)) {
			assertFalse(files.anyMatch(path ->
					path.getFileName().toString().endsWith(".tmp")));
		}
	}

	@Test
	public void quarantinedPayloadRefusesSaveAndPreservesOriginalFile()
			throws Exception {
		Path root = Files.createTempDirectory("player-save-quarantine");
		Path characters = Files.createDirectories(root.resolve("data/characters"));
		Path target = characters.resolve("quarantined.txt");
		String original = "[ACCOUNT]\n"
				+ "character-username = quarantined\n"
				+ "character-password = password\n\n"
				+ "[CHARACTER]\n"
				+ "character-script-state = v9.bad\n\n"
				+ "[EOF]\n";
		Files.write(target, original.getBytes(StandardCharsets.UTF_8));
		previousUserDir = System.getProperty("user.dir");
		System.setProperty("user.dir", root.toString());
		previousPlayer = PlayerHandler.players[0];

		Client player = savableClient("quarantined");
		PlayerSave.loadPlayerInfo(player, "quarantined", "password", false);
		assertEquals("v9.bad", player.getQuarantinedScriptStatePayload());
		PlayerHandler.players[0] = player;

		assertFalse(PlayerSave.saveGame(player));
		assertEquals(original, new String(Files.readAllBytes(target),
				StandardCharsets.UTF_8));
		assertEquals("v9.bad", player.getQuarantinedScriptStatePayload());
		try (Stream<Path> files = Files.list(characters)) {
			assertFalse(files.anyMatch(path ->
					path.getFileName().toString().endsWith(".tmp")));
		}
	}

	private static Client savableClient(String name) {
		Client player = new Client(null, 0);
		player.playerName = name;
		player.playerName2 = name;
		player.playerPass = "password";
		player.saveFile = true;
		player.saveCharacter = true;
		player.newPlayer = false;
		return player;
	}
}
