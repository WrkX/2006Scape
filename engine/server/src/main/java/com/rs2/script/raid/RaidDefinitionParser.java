package com.rs2.script.raid;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.graalvm.polyglot.Value;

import com.rs2.script.boss.BossDefinition;
import com.rs2.script.boss.BossDefinitionRegistry;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.drop.DropTableDefinition;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.reward.RewardDefinition;
import com.rs2.script.reward.RewardRegistry;

/**
 * Strict one-way parser for {@code defineRaid} schema-v1 definitions.
 *
 * <p>Allowed members: {@code id}, {@code name}, {@code command},
 * {@code bounds}, {@code muster}, {@code entrance}, {@code minPlayers},
 * {@code maxPlayers}, {@code timeLimitTicks}, {@code rooms}, {@code rewards},
 * {@code rewardTable}, {@code privateTicks}, {@code onStart},
 * {@code onComplete}, and {@code onWipe}. Rooms allow {@code id},
 * {@code name}, {@code bounds}, {@code onEnter}, {@code onTick},
 * {@code onComplete}, and {@code boss}; a boss room references a registered
 * {@code defineBoss} id by stable id.
 *
 * <p>The command route must not be a reserved admin alias, rooms must be
 * non-empty with unique ids and pairwise non-overlapping rectangles inside
 * the raid bounds, the muster and entrance lie on the raid plane inside the
 * bounds, player limits must be possible, the time limit must be bounded,
 * and every boss/reward/drop-table reference must already be registered in
 * the loading candidate. Duplicate stable raid ids reject the candidate.
 */
public final class RaidDefinitionParser {

	private static final int MAX_COORDINATE = 16383;
	private static final int MAX_AREA_SIDE = 64;
	private static final int MAX_PLAYERS = 8;
	private static final int MAX_ROOMS = 8;
	private static final int MAX_REWARDS = 8;
	private static final int MAX_TIME_LIMIT_TICKS = 100000;
	private static final int MAX_PRIVATE_TICKS = 1000;

	private final String source;
	private final int schemaVersion;

	public RaidDefinitionParser(String source, int schemaVersion) {
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public RaidDefinition parse(Value value) {
		if (value == null || !value.hasMembers()) {
			throw failure("definition must be an object");
		}
		only(value, "raid", "id", "name", "command", "bounds", "muster",
				"entrance", "minPlayers", "maxPlayers", "timeLimitTicks",
				"rooms", "rewards", "rewardTable", "privateTicks",
				"onStart", "onComplete", "onWipe");
		String id = requireId(value, "id");
		String name = optionalBoundedString(value.getMember("name"), "name",
				64);
		String command = requiredCommand(value, "command");
		rejectReserved(command);
		RaidBounds bounds = parseBounds(required(value, "bounds"), "bounds");
		RaidBounds muster = parseMuster(required(value, "muster"), bounds);
		int[] entrance = parseEntrance(required(value, "entrance"), bounds);
		int minPlayers = integral(required(value, "minPlayers"), 1,
				MAX_PLAYERS, "minPlayers");
		int maxPlayers = integral(required(value, "maxPlayers"), 1,
				MAX_PLAYERS, "maxPlayers");
		if (minPlayers > maxPlayers) {
			throw failure("minPlayers (" + minPlayers + ") must not exceed "
					+ "maxPlayers (" + maxPlayers + ")");
		}
		int timeLimitTicks = integral(required(value, "timeLimitTicks"), 1,
				MAX_TIME_LIMIT_TICKS, "timeLimitTicks");
		List<RaidRoomDefinition> rooms = parseRooms(
				required(value, "rooms"), bounds);
		List<RewardDefinition> rewards = parseRewards(
				value.getMember("rewards"));
		String rewardTable = optionalReference(value.getMember("rewardTable"),
				"rewardTable");
		Integer privateTicks = optionalIntegral(
				value.getMember("privateTicks"), 1, MAX_PRIVATE_TICKS,
				"privateTicks");
		if (rewardTable == null && privateTicks != null) {
			throw failure("'privateTicks' is allowed only together with a "
					+ "named 'rewardTable'");
		}
		if (rewardTable != null && privateTicks == null) {
			throw failure("a named 'rewardTable' requires 'privateTicks' "
					+ "1.." + MAX_PRIVATE_TICKS);
		}
		Value onStart = optionalExecutable(value.getMember("onStart"),
				"onStart");
		Value onComplete = optionalExecutable(value.getMember("onComplete"),
				"onComplete");
		Value onWipe = optionalExecutable(value.getMember("onWipe"),
				"onWipe");
		rejectDuplicateStableId(id);
		return new RaidDefinition(id, name, command, bounds, muster,
				entrance[0], entrance[1], entrance[2], minPlayers,
				maxPlayers, timeLimitTicks, rooms, rewards, rewardTable,
				privateTicks == null ? 0 : privateTicks.intValue(),
				rewardTable != null, onStart, onComplete, onWipe, source,
				schemaVersion);
	}

	private RaidBounds parseBounds(Value value, String label) {
		requireObject(value, label);
		only(value, label, "minX", "minY", "maxX", "maxY", "plane");
		int minX = integral(required(value, "minX"), 0, MAX_COORDINATE,
				label + ".minX");
		int minY = integral(required(value, "minY"), 0, MAX_COORDINATE,
				label + ".minY");
		int maxX = integral(required(value, "maxX"), 0, MAX_COORDINATE,
				label + ".maxX");
		int maxY = integral(required(value, "maxY"), 0, MAX_COORDINATE,
				label + ".maxY");
		int plane = integral(required(value, "plane"), 0, 3, label + ".plane");
		if (minX > maxX || minY > maxY) {
			throw failure(label + " bounds are inverted");
		}
		if (maxX - minX + 1 > MAX_AREA_SIDE
				|| maxY - minY + 1 > MAX_AREA_SIDE) {
			throw failure(label + " sides must be 1.." + MAX_AREA_SIDE
					+ " tiles");
		}
		return new RaidBounds(minX, minY, maxX, maxY, plane);
	}

	private RaidBounds parseMuster(Value value, RaidBounds bounds) {
		requireObject(value, "muster");
		only(value, "muster", "minX", "minY", "maxX", "maxY");
		int minX = integral(required(value, "minX"), 0, MAX_COORDINATE,
				"muster.minX");
		int minY = integral(required(value, "minY"), 0, MAX_COORDINATE,
				"muster.minY");
		int maxX = integral(required(value, "maxX"), 0, MAX_COORDINATE,
				"muster.maxX");
		int maxY = integral(required(value, "maxY"), 0, MAX_COORDINATE,
				"muster.maxY");
		if (minX > maxX || minY > maxY) {
			throw failure("muster bounds are inverted");
		}
		if (maxX - minX + 1 > MAX_AREA_SIDE
				|| maxY - minY + 1 > MAX_AREA_SIDE) {
			throw failure("muster sides must be 1.." + MAX_AREA_SIDE
					+ " tiles");
		}
		RaidBounds muster = new RaidBounds(minX, minY, maxX, maxY,
				bounds.plane());
		if (!bounds.contains(muster)) {
			throw failure("muster " + muster + " must lie inside the raid "
					+ "bounds " + bounds);
		}
		return muster;
	}

	private int[] parseEntrance(Value value, RaidBounds bounds) {
		requireObject(value, "entrance");
		only(value, "entrance", "x", "y", "plane");
		int x = integral(required(value, "x"), 0, MAX_COORDINATE,
				"entrance.x");
		int y = integral(required(value, "y"), 0, MAX_COORDINATE,
				"entrance.y");
		int plane = integral(required(value, "plane"), 0, 3, "entrance.plane");
		if (!bounds.contains(x, y, plane)) {
			throw failure("entrance (" + x + ", " + y + ", " + plane
					+ ") must lie inside the raid bounds " + bounds);
		}
		return new int[] { x, y, plane };
	}

	private List<RaidRoomDefinition> parseRooms(Value value,
			RaidBounds bounds) {
		requireBoundedArray(value, "rooms", MAX_ROOMS);
		List<RaidRoomDefinition> rooms = new ArrayList<RaidRoomDefinition>();
		Set<String> ids = new HashSet<String>();
		for (int index = 0; index < value.getArraySize(); index++) {			Value room = value.getArrayElement(index);
			requireObject(room, "rooms[" + index + "]");
			only(room, "rooms[" + index + "]", "id", "name", "bounds",
					"onEnter", "onTick", "onComplete", "boss");
			String id = requiredString(room, "id");
			if (id.isEmpty() || utf8Length(id) > 64) {
				throw failure("rooms[" + index
						+ "].id must be 1..64 UTF-8 bytes");
			}
			if (!ids.add(id)) {
				throw failure("rooms[" + index + "]: duplicate room id '"
						+ id + "'");
			}
			String name = requiredBoundedString(room, "name", 64,
					"rooms[" + index + "].name");
			RaidBounds roomBounds = parseBounds(
					required(room, "bounds"),
					"rooms[" + index + "].bounds");
			if (!bounds.contains(roomBounds)) {
				throw failure("room '" + id + "' bounds " + roomBounds
						+ " must lie inside the raid bounds " + bounds);
			}
			for (RaidRoomDefinition existing : rooms) {
				if (existing.bounds().overlaps(roomBounds)) {
					throw failure("room '" + id + "' bounds " + roomBounds
							+ " overlap room '" + existing.id() + "' bounds "
							+ existing.bounds());
				}
			}
			Value onEnter = requiredExecutable(room, "onEnter");
			Value onTick = requiredExecutable(room, "onTick");
			Value onComplete = requiredExecutable(room, "onComplete");
			BossDefinition boss = parseBossReference(
					room.getMember("boss"), id, roomBounds);
			rooms.add(new RaidRoomDefinition(id, name, roomBounds, onEnter,
					onTick, onComplete, boss));
		}
		if (rooms.isEmpty()) {
			throw failure("'rooms' must contain 1.." + MAX_ROOMS
					+ " rooms");
		}
		return rooms;
	}

	private BossDefinition parseBossReference(Value value, String roomId,
			RaidBounds roomBounds) {
		if (value == null || value.isNull()) {
			return null;
		}
		requireObject(value, "rooms[\"" + roomId + "\"].boss");
		only(value, "rooms[\"" + roomId + "\"].boss", "bossId");
		String bossId = requiredString(value, "bossId");
		if (bossId.isEmpty() || bossId.length() > 64
				|| !bossId.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
			throw failure("rooms[\"" + roomId + "\"].boss.bossId is invalid: "
					+ bossId);
		}
		BossDefinition boss = null;
		if (RegistryStore.isStagingActive()) {
			for (Map.Entry<Integer, BossDefinition> entry
					: BossDefinitionRegistry.all(RegistryStore.writable())
							.entrySet()) {
				if (entry.getValue().id().equals(bossId)) {
					boss = entry.getValue();
					break;
				}
			}
			if (boss == null) {
				throw failure("boss room '" + roomId + "' references boss '"
						+ bossId + "' which is not registered in the loading "
						+ "candidate; defineBoss must run before defineRaid");
			}
		}
		if (boss != null) {
			if (boss.arena().plane() != roomBounds.plane()) {
				throw failure("boss room '" + roomId + "' boss '" + bossId
						+ "' arena plane " + boss.arena().plane()
						+ " differs from the room plane "
						+ roomBounds.plane());
			}
			if (!roomBounds.contains(boss.spawnX(), boss.spawnY(),
					roomBounds.plane())) {
				throw failure("boss room '" + roomId + "' boss '" + bossId
						+ "' spawn (" + boss.spawnX() + ", " + boss.spawnY()
						+ ") is unreachable: it lies outside the room bounds "
						+ roomBounds);
			}
		}
		return boss;
	}

	private List<RewardDefinition> parseRewards(Value value) {
		List<RewardDefinition> rewards = new ArrayList<RewardDefinition>();
		if (value == null || value.isNull()) {
			throw failure("'rewards' must be present and contain 1.."
					+ MAX_REWARDS + " named reward ids");
		}
		requireBoundedArray(value, "rewards", MAX_REWARDS);
		Set<String> ids = new HashSet<String>();
		for (int index = 0; index < value.getArraySize(); index++) {
			Value reference = value.getArrayElement(index);
			if (reference == null || !reference.isString()
					|| reference.asString().trim().isEmpty()) {
				throw failure("rewards[" + index
						+ "] must be a non-empty string id");
			}
			String rewardId = reference.asString().trim();
			if (!ids.add(rewardId)) {
				throw failure("rewards[" + index
						+ "]: duplicate reward reference '" + rewardId + "'");
			}
			if (RegistryStore.isStagingActive()) {
				DefinitionRecord record = RegistryStore.writable()
						.definitions.get(
								com.rs2.script.definition.DefinitionKey.of(
										com.rs2.script.definition.DefinitionKind.REWARD,
										rewardId));
				RewardDefinition reward = record == null
						|| record.isGuestPayload() ? null
								: record.rewardPayload();
				if (reward == null) {
					throw failure("named reward '" + rewardId
							+ "' is not registered in the loading candidate; "
							+ "defineReward must run before defineRaid");
				}
				rewards.add(reward);
			}
		}
		if (rewards.isEmpty()) {
			throw failure("'rewards' must contain 1.." + MAX_REWARDS
					+ " named reward ids");
		}
		return rewards;
	}

	private String optionalReference(Value value, String member) {
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isString() || value.asString().trim().isEmpty()) {
			throw failure("'" + member + "' must be a non-empty string");
		}
		String reference = value.asString().trim();
		if (reference.length() > 64
				|| !reference.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
			throw failure("invalid " + member + " reference: " + reference);
		}
		if (RegistryStore.isStagingActive()) {
			DropTableDefinition table = com.rs2.script.drop.DropTableRegistry
					.get(RegistryStore.writable(), reference);
			if (table == null) {
				throw failure("named drop table '" + reference
						+ "' is not registered in the loading candidate; "
						+ "defineDropTable must run before defineRaid");
			}
		}
		return reference;
	}

	private void rejectReserved(String command) {
		if (com.rs2.script.route.RouteRegistry.RESERVED_COMMANDS
				.contains(command)) {
			throw failure("command alias is reserved for the engine admin "
					+ "transport: " + command);
		}
	}

	private void rejectDuplicateStableId(String id) {
		if (!RegistryStore.isStagingActive()) {
			return;
		}
		for (RaidDefinition existing
				: RaidDefinitionRegistry.all(RegistryStore.writable())
						.values()) {
			if (existing.id().equals(id)) {
				throw failure("duplicate raid id '" + id
						+ "' already registered by " + existing.source());
			}
		}
	}

	private String requireId(Value value, String member) {
		Value idValue = value.getMember(member);
		if (idValue == null || !idValue.isString()
				|| idValue.asString().trim().isEmpty()) {
			throw failure("'id' must be a non-empty string");
		}
		String id = idValue.asString().trim();
		if (id.length() > 64 || !id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
			throw failure("invalid raid id: " + id);
		}
		return id;
	}

	private String requiredCommand(Value value, String member) {
		Value commandValue = value.getMember(member);
		if (commandValue == null || !commandValue.isString()
				|| commandValue.asString().trim().isEmpty()) {
			throw failure("'" + member + "' must be a non-empty string");
		}
		String command = commandValue.asString().trim();
		if (command.length() > 64
				|| !command.matches("[a-z0-9][a-z0-9._-]*")) {
			throw failure("'" + member + "' must be a lower-case command "
					+ "name of at most 64 characters");
		}
		return command;
	}

	private String optionalBoundedString(Value value, String member,
			int maximumBytes) {
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isString() || value.asString().isEmpty()) {
			throw failure("'" + member + "' must be a non-empty string");
		}
		String string = value.asString();
		if (utf8Length(string) > maximumBytes) {
			throw failure("'" + member + "' must be 1.." + maximumBytes
					+ " UTF-8 bytes");
		}
		return string;
	}

	private String requiredBoundedString(Value parent, String member,
			int maximumBytes, String label) {
		String string = requiredString(parent, member);
		if (string.isEmpty() || utf8Length(string) > maximumBytes) {
			throw failure(label + " must be 1.." + maximumBytes
					+ " UTF-8 bytes");
		}
		return string;
	}

	private String requiredString(Value parent, String member) {
		Value value = parent.getMember(member);
		if (value == null || value.isNull() || !value.isString()) {
			throw failure("member '" + member + "' must be a string");
		}
		return value.asString();
	}

	private Value requiredExecutable(Value parent, String member) {
		Value value = required(parent, member);
		if (!isExecutable(value)) {
			throw failure("member '" + member + "' must be executable");
		}
		return value;
	}

	private Value optionalExecutable(Value value, String member) {
		if (value == null || value.isNull()) {
			return null;
		}
		if (!isExecutable(value)) {
			throw failure("member '" + member
					+ "' must be executable when present");
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
			throw failure("raid " + label + " must be an object");
		}
	}

	private void requireBoundedArray(Value array, String label, int maximum) {
		if (array == null || !array.hasArrayElements()) {
			throw failure("raid " + label + " must be an array");
		}
		long size = array.getArraySize();
		if (size < 0 || size > maximum) {
			throw failure("raid " + label + " must contain 0.." + maximum
					+ " entries");
		}
	}

	private void only(Value value, String label, String... allowed) {
		Set<String> allowedMembers = new TreeSet<String>();
		for (String member : allowed) {
			allowedMembers.add(member);
		}
		Set<String> keys = new TreeSet<String>();
		for (String key : value.getMemberKeys()) {
			keys.add(key);
		}
		if (!allowedMembers.containsAll(keys)) {
			throw failure("raid " + label + " has unknown members " + keys
					+ "; allowed: " + allowedMembers);
		}
	}

	private int integral(Value value, int minimum, int maximum, String label) {
		if (value == null || !value.isNumber()) {
			throw failure("raid " + label + " must be a number");
		}
		double raw = value.asDouble();
		if (!Double.isFinite(raw) || raw != Math.rint(raw)
				|| raw < minimum || raw > maximum) {
			throw failure("raid " + label + " must be integral " + minimum
					+ ".." + maximum);
		}
		return (int) raw;
	}

	private Integer optionalIntegral(Value value, int minimum, int maximum,
			String label) {
		if (value == null || value.isNull()) {
			return null;
		}
		return Integer.valueOf(integral(value, minimum, maximum, label));
	}

	private static boolean isExecutable(Value value) {
		return value != null && !value.isNull() && value.canExecute();
	}

	private static int utf8Length(String value) {
		return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
	}

	private IllegalArgumentException failure(String message) {
		return new IllegalArgumentException(describe() + ": " + message);
	}

	private String describe() {
		return "Script registration defineRaid (source: " + source
				+ ", schema v" + schemaVersion + ")";
	}

}
