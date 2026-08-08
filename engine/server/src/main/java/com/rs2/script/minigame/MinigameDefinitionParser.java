package com.rs2.script.minigame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.graalvm.polyglot.Value;

import com.rs2.game.npcs.NpcHandler;
import com.rs2.script.ScriptEntityLimits;
import com.rs2.script.area.AreaBounds;
import com.rs2.script.area.AreaDefinition;
import com.rs2.script.area.AreaDefinitionRegistry;
import com.rs2.script.registries.RegistryStore;

/**
 * Strict one-way parser for {@code defineMinigame} schema-v1 definitions.
 */
public final class MinigameDefinitionParser {

	private static final int MAX_COORDINATE = 16383;
	private static final int MAX_PLAYERS = 25;
	private static final int MAX_WAVES = 16;
	private static final int MAX_SPAWNS_PER_WAVE = 16;
	private static final int MAX_TIME_LIMIT_TICKS = 100000;
	private static final int MAX_LOBBY_WAIT_TICKS = 10000;

	private final String source;
	private final int schemaVersion;

	public MinigameDefinitionParser(String source, int schemaVersion) {
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public MinigameDefinition parse(Value value) {
		if (value == null || !value.hasMembers()) {
			throw failure("definition must be an object");
		}
		only(value, "id", "name", "command", "lobbyAreaId", "arenaAreaId",
				"entrance", "leave", "minPlayers", "maxPlayers",
				"lobbyWaitTicks", "timeLimitTicks", "waves", "score",
				"onStart", "onWaveStart", "onWaveComplete", "onTick",
				"onComplete", "onWipe");
		String id = requireId(value);
		String name = optionalBoundedString(value.getMember("name"), "name", 64);
		String command = requiredCommand(value, "command");
		rejectReserved(command);
		String lobbyAreaId = requiredReference(value, "lobbyAreaId");
		String arenaAreaId = requiredReference(value, "arenaAreaId");
		AreaDefinition lobbyArea = requireArea(lobbyAreaId);
		AreaDefinition arenaArea = requireArea(arenaAreaId);
		int[] entrance = parsePoint(required(value, "entrance"), "entrance",
				arenaArea.bounds());
		int[] leave = parsePoint(required(value, "leave"), "leave", null);
		int minPlayers = integral(required(value, "minPlayers"), 1,
				MAX_PLAYERS, "minPlayers");
		int maxPlayers = integral(required(value, "maxPlayers"), 1,
				MAX_PLAYERS, "maxPlayers");
		if (minPlayers > maxPlayers) {
			throw failure("minPlayers must not exceed maxPlayers");
		}
		int lobbyWaitTicks = integral(required(value, "lobbyWaitTicks"), 0,
				MAX_LOBBY_WAIT_TICKS, "lobbyWaitTicks");
		int timeLimitTicks = integral(required(value, "timeLimitTicks"), 1,
				MAX_TIME_LIMIT_TICKS, "timeLimitTicks");
		List<MinigameWaveDefinition> waves = parseWaves(
				required(value, "waves"), arenaArea.bounds().plane());
		MinigameScoreDefinition score = parseScore(value.getMember("score"));
		Value onStart = optionalExecutable(value.getMember("onStart"),
				"onStart");
		Value onWaveStart = optionalExecutable(value.getMember("onWaveStart"),
				"onWaveStart");
		Value onWaveComplete = optionalExecutable(
				value.getMember("onWaveComplete"), "onWaveComplete");
		Value onTick = optionalExecutable(value.getMember("onTick"), "onTick");
		Value onComplete = optionalExecutable(value.getMember("onComplete"),
				"onComplete");
		Value onWipe = optionalExecutable(value.getMember("onWipe"), "onWipe");
		rejectDuplicateStableId(id);
		return new MinigameDefinition(id, name, command, lobbyAreaId,
				arenaAreaId, lobbyArea.bounds(), arenaArea.bounds(),
				entrance[0], entrance[1], entrance[2], leave[0], leave[1],
				leave[2], minPlayers, maxPlayers, lobbyWaitTicks,
				timeLimitTicks, waves, score, onStart, onWaveStart,
				onWaveComplete, onTick, onComplete, onWipe, source,
				schemaVersion);
	}

	private List<MinigameWaveDefinition> parseWaves(Value array,
			int defaultPlane) {
		requireBoundedArray(array, "waves", MAX_WAVES);
		List<MinigameWaveDefinition> waves =
				new ArrayList<MinigameWaveDefinition>();
		Set<String> ids = new TreeSet<String>();
		long size = array.getArraySize();
		if (size < 1) {
			throw failure("waves must contain at least one entry");
		}
		for (long index = 0; index < size; index++) {
			Value entry = array.getArrayElement(index);
			if (entry == null || !entry.hasMembers()) {
				throw failure("waves[" + index + "] must be an object");
			}
			only(entry, "id", "npcs");
			String waveId = requiredBoundedString(entry, "id", 64,
					"waves[" + index + "].id");
			if (!ids.add(waveId)) {
				throw failure("duplicate wave id '" + waveId + "'");
			}
			waves.add(new MinigameWaveDefinition(waveId,
					parseSpawns(required(entry, "npcs"), defaultPlane,
							"waves[" + index + "].npcs")));
		}
		return waves;
	}

	private List<MinigameWaveSpawn> parseSpawns(Value array, int defaultPlane,
			String label) {
		requireBoundedArray(array, label, MAX_SPAWNS_PER_WAVE);
		List<MinigameWaveSpawn> spawns = new ArrayList<MinigameWaveSpawn>();
		long size = array.getArraySize();
		if (size < 1) {
			throw failure(label + " must contain at least one spawn");
		}
		for (long index = 0; index < size; index++) {
			Value entry = array.getArrayElement(index);
			if (entry == null || !entry.hasMembers()) {
				throw failure(label + "[" + index + "] must be an object");
			}
			only(entry, "npcId", "x", "y", "plane");
			int npcId = integral(required(entry, "npcId"), 0,
					ScriptEntityLimits.MAX_NPC_ID, label + "[" + index
							+ "].npcId");
			requireLoadedNpc(npcId);
			int x = integral(required(entry, "x"), 0, MAX_COORDINATE,
					label + "[" + index + "].x");
			int y = integral(required(entry, "y"), 0, MAX_COORDINATE,
					label + "[" + index + "].y");
			int plane = defaultPlane;
			if (hasMember(entry, "plane")) {
				plane = integral(required(entry, "plane"), 0, 3,
						label + "[" + index + "].plane");
			}
			spawns.add(new MinigameWaveSpawn(npcId, x, y, plane));
		}
		return spawns;
	}

	private MinigameScoreDefinition parseScore(Value value) {
		if (value == null || value.isNull()) {
			return null;
		}
		requireObject(value, "score");
		only(value, "score", "namespace", "key");
		String namespace = requiredBoundedString(value, "namespace", 32,
				"score.namespace");
		String key = requiredBoundedString(value, "key", 32, "score.key");
		return new MinigameScoreDefinition(namespace, key);
	}

	private int[] parsePoint(Value value, String label, AreaBounds bounds) {
		requireObject(value, label);
		only(value, label, "x", "y", "plane");
		int x = integral(required(value, "x"), 0, MAX_COORDINATE,
				label + ".x");
		int y = integral(required(value, "y"), 0, MAX_COORDINATE,
				label + ".y");
		int plane = integral(required(value, "plane"), 0, 3, label + ".plane");
		if (bounds != null && !bounds.contains(x, y, plane)) {
			throw failure(label + " must lie inside the arena area bounds");
		}
		return new int[] { x, y, plane };
	}

	private AreaDefinition requireArea(String areaId) {
		if (!RegistryStore.isStagingActive()) {
			return null;
		}
		AreaDefinition area = AreaDefinitionRegistry.get(
				RegistryStore.writable(), areaId);
		if (area == null) {
			throw failure("unknown area id '" + areaId + "'");
		}
		return area;
	}

	private void requireLoadedNpc(int npcId) {
		if (NpcHandler.hasNpcDefinitions()
				&& !NpcHandler.hasNpcDefinition(npcId)) {
			throw failure("npc id " + npcId + " has no loaded definition");
		}
	}

	private void rejectDuplicateStableId(String id) {
		if (!RegistryStore.isStagingActive()) {
			return;
		}
		if (MinigameDefinitionRegistry.get(RegistryStore.writable(), id)
				!= null) {
			throw failure("duplicate minigame id '" + id + "'");
		}
	}

	private static void rejectReserved(String command) {
		if ("scripts".equals(command) || "reload".equals(command)
				|| "scriptdir".equals(command)) {
			throw new IllegalArgumentException("defineMinigame: command alias '"
					+ command
					+ "' is reserved for the engine admin transport");
		}
	}

	private static boolean hasMember(Value value, String member) {
		Value entry = value.getMember(member);
		return entry != null && !entry.isNull();
	}

	private String requireId(Value value) {
		return requiredBoundedString(value, "id", 64, "id");
	}

	private String requiredCommand(Value parent, String member) {
		String command = requiredBoundedString(parent, member, 32, member);
		if (!command.matches("[a-z0-9][a-z0-9-]*")) {
			throw failure("'" + member + "' must be lowercase letters, "
					+ "digits, or hyphens");
		}
		return command;
	}

	private String requiredReference(Value parent, String member) {
		return requiredBoundedString(parent, member, 64, member);
	}

	private String optionalBoundedString(Value value, String member,
			int maximumBytes) {
		if (value == null || value.isNull()) {
			return null;
		}
		return boundedString(value, member, maximumBytes);
	}

	private String requiredBoundedString(Value parent, String member,
			int maximumBytes, String label) {
		String string = boundedString(required(parent, member), label,
				maximumBytes);
		if (string.isEmpty()) {
			throw failure("'" + label + "' must be non-empty");
		}
		return string;
	}

	private String boundedString(Value value, String label, int maximumBytes) {
		if (!value.isString()) {
			throw failure("'" + label + "' must be a string");
		}
		String string = value.asString().trim();
		if (utf8Length(string) > maximumBytes) {
			throw failure("'" + label + "' must be 1.." + maximumBytes
					+ " UTF-8 bytes");
		}
		return string;
	}

	private Value optionalExecutable(Value value, String member) {
		if (value == null || value.isNull()) {
			return null;
		}
		if (!isExecutable(value)) {
			throw failure("'" + member + "' must be executable when present");
		}
		return value;
	}

	private Value required(Value parent, String member) {
		Value value = parent.getMember(member);
		if (value == null || value.isNull()) {
			throw failure("member '" + member + "' must be present");
		}
		return value;
	}

	private void requireObject(Value value, String label) {
		if (value == null || !value.hasMembers()) {
			throw failure(label + " must be an object");
		}
	}

	private void requireBoundedArray(Value array, String label, int maximum) {
		if (array == null || !array.hasArrayElements()) {
			throw failure(label + " must be an array");
		}
		long size = array.getArraySize();
		if (size < 0 || size > maximum) {
			throw failure(label + " must contain 0.." + maximum + " entries");
		}
	}

	private void only(Value value, String... allowed) {
		Set<String> allowedMembers = new TreeSet<String>();
		for (String member : allowed) {
			allowedMembers.add(member);
		}
		Set<String> keys = new TreeSet<String>();
		for (String key : value.getMemberKeys()) {
			keys.add(key);
		}
		if (!allowedMembers.containsAll(keys)) {
			throw failure("unknown members " + keys + "; allowed: "
					+ allowedMembers);
		}
	}

	private int integral(Value value, int minimum, int maximum, String label) {
		if (value == null || !value.isNumber()) {
			throw failure(label + " must be a number");
		}
		double raw = value.asDouble();
		if (!Double.isFinite(raw) || raw != Math.rint(raw)
				|| raw < minimum || raw > maximum) {
			throw failure(label + " must be integral " + minimum + ".."
					+ maximum);
		}
		return (int) raw;
	}

	private static boolean isExecutable(Value value) {
		return value != null && value.canExecute();
	}

	private static int utf8Length(String value) {
		return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
	}

	private IllegalArgumentException failure(String message) {
		return new IllegalArgumentException("defineMinigame: " + message);
	}
}
