package com.rs2.game.players;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Test;

import com.rs2.script.state.PlayerStateNamespace;
import com.rs2.script.state.ScriptStateCodec;
import com.rs2.script.state.ScriptStateSnapshot;

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

	@Test
	public void validV0CharacterFileMigratesThroughPlayerSaveAndResavesV1()
			throws Exception {
		Path root = Files.createTempDirectory("player-save-v0-migration");
		Path characters = Files.createDirectories(root.resolve("data/characters"));
		previousUserDir = System.getProperty("user.dir");
		System.setProperty("user.dir", root.toString());
		previousPlayer = PlayerHandler.players[0];

		Path target = characters.resolve("migrated.txt");
		String v0 = v0Payload("dragon-awakens", "bones-recovered", true,
				"stage.dragon-awakens", 2.0);
		Files.write(target, ("[ACCOUNT]\n"
				+ "character-username = migrated\n"
				+ "character-password = password\n\n"
				+ "[CHARACTER]\n"
				+ "questPoints = 17\n"
				+ "character-script-state = " + v0 + "\n\n"
				+ "[EOF]\n").getBytes(StandardCharsets.UTF_8));

		Client player = savableClient("migrated");
		PlayerSave.loadPlayerInfo(player, "migrated", "password", false);
		assertEquals(17, player.questPoints);
		assertNull(player.getQuarantinedScriptStatePayload());
		PlayerStateNamespace state = new PlayerStateNamespace(
				player.getScriptState(), "dragon-awakens");
		assertTrue(state.getBoolean("bones-recovered"));
		assertEquals("in_progress", player.getScriptState().getInternal(
				"__quest", "state.dragon-awakens").asString());
		assertEquals(2.0, player.getScriptState().getInternal(
				"__quest", "stage.dragon-awakens").asNumber(), 0.0);
		PlayerHandler.players[0] = player;

		assertTrue(PlayerSave.saveGame(player));
		String saved = new String(Files.readAllBytes(target),
				StandardCharsets.UTF_8);
		String savedState = saved.lines()
				.filter(line -> line.startsWith("character-script-state = "))
				.findFirst().orElseThrow(() -> new AssertionError(
						"missing character-script-state line"));
		assertTrue("saved payload must be v1, was: " + savedState,
				savedState.startsWith("character-script-state = v1."));
		assertEquals("17", saved.lines()
				.filter(line -> line.startsWith("questPoints = "))
				.findFirst().get().substring("questPoints = ".length()));
		ScriptStateSnapshot decoded = new ScriptStateCodec().decode(
				savedState.substring("character-script-state = ".length()));
		assertEquals("in_progress", decoded.getNamespaces().get("__quest")
				.get("state.dragon-awakens").asString());
		assertEquals(2.0, decoded.getNamespaces().get("__quest")
				.get("stage.dragon-awakens").asNumber(), 0.0);
		assertTrue(decoded.getNamespaces().get("dragon-awakens")
				.get("bones-recovered").asBoolean());
	}

	@Test
	public void malformedV0CharacterFileIsQuarantinedAndRefusesSave()
			throws Exception {
		Path root = Files.createTempDirectory("player-save-v0-quarantine");
		Path characters = Files.createDirectories(root.resolve("data/characters"));
		Path target = characters.resolve("v0quarantined.txt");
		String original = "[ACCOUNT]\n"
				+ "character-username = v0quarantined\n"
				+ "character-password = password\n\n"
				+ "[CHARACTER]\n"
				+ "character-script-state = v0.###\n\n"
				+ "[EOF]\n";
		Files.write(target, original.getBytes(StandardCharsets.UTF_8));
		previousUserDir = System.getProperty("user.dir");
		System.setProperty("user.dir", root.toString());
		previousPlayer = PlayerHandler.players[0];

		Client player = savableClient("v0quarantined");
		PlayerSave.loadPlayerInfo(player, "v0quarantined", "password", false);
		assertEquals("v0.###", player.getQuarantinedScriptStatePayload());
		assertTrue(player.getScriptState().snapshot().getNamespaces().isEmpty());
		PlayerHandler.players[0] = player;

		assertFalse(PlayerSave.saveGame(player));
		assertEquals(original, new String(Files.readAllBytes(target),
				StandardCharsets.UTF_8));
		assertEquals("v0.###", player.getQuarantinedScriptStatePayload());
		try (Stream<Path> files = Files.list(characters)) {
			assertFalse(files.anyMatch(path ->
					path.getFileName().toString().endsWith(".tmp")));
		}
	}

	private static String v0Payload(String namespace, String key,
			boolean flag, String questKey, double questStage)
			throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeShort(3);
		writeEntry(out, "__quest", questKey, 2, questStage);
		writeEntry(out, "__quest", "state." + namespace, 3, "in_progress");
		writeEntry(out, namespace, key, 1, flag ? 1 : 0);
		out.flush();
		return "v0." + Base64.getUrlEncoder().withoutPadding()
				.encodeToString(bytes.toByteArray());
	}

	private static void writeEntry(DataOutputStream out, String namespace,
			String key, int type, Object value) throws Exception {
		byte[] namespaceBytes = namespace.getBytes(StandardCharsets.UTF_8);
		out.writeShort(namespaceBytes.length);
		out.write(namespaceBytes);
		byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
		out.writeShort(keyBytes.length);
		out.write(keyBytes);
		out.writeByte(type);
		if (type == 1) {
			out.writeByte(((Integer) value).intValue());
		} else if (type == 2) {
			out.writeDouble(((Double) value).doubleValue());
		} else if (type == 3) {
			byte[] valueBytes = ((String) value)
					.getBytes(StandardCharsets.UTF_8);
			out.writeInt(valueBytes.length);
			out.write(valueBytes);
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
