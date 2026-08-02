package com.rs2.script.boss;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apollo.cache.def.ObjectDefinition;
import org.graalvm.polyglot.Value;

import com.rs2.game.npcs.NpcHandler;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.registries.RegistryStore;

/**
 * Strict one-way parser for {@code defineBoss} schema-v1 definitions.
 *
 * <p>Allowed members: {@code id}, {@code npcId}, {@code name},
 * {@code combatLevel}, {@code maxHitpoints}, {@code maxHit}, {@code attack},
 * {@code defence}, {@code arena}, {@code spawn}, {@code command},
 * {@code closeCommand}, {@code objectEntry}, {@code entryTeleport},
 * {@code onSpawn}, {@code onTick}, {@code onDeath}, {@code phases},
 * {@code specials}, {@code dropTable}, {@code privateTicks}, and
 * {@code cleanupPolicy}.
 *
 * <p>Exactly one entry source (command or object entry) is required so no
 * canonical boss can be inert, the npc id must be definition-backed when
 * the npc.json list is loaded, the drop table must already be registered in
 * the loading candidate, and duplicate stable ids reject the candidate.
 * Phases allow {@code 0..8} entries while specials require {@code 1..16}:
 * a boss without phases is valid, but a named-specials map must be
 * non-empty. Callbacks are captured as generation-owned guest values; every
 * other member is copied into the immutable Java-owned descriptor.
 */
public final class BossDefinitionParser {

	private static final int MAX_STATS = 32767;
	private static final int MAX_COORDINATE = 16383;
	private static final int MAX_ARENA_SIDE = 64;
	private static final int MAX_PHASES = 8;
	private static final int MAX_SPECIALS = 16;
	private static final int MAX_COOLDOWN_TICKS = 100000;
	private static final int MAX_PRIVATE_TICKS = 1000;
	private static final int MAX_OBJECT_ID = 65535;

	private static final String[] ENTRY_ACTIONS = {
			"first", "second", "third", "fourth"
	};

	private final String source;
	private final int schemaVersion;

	public BossDefinitionParser(String source, int schemaVersion) {
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public BossDefinition parse(Value value) {
		if (value == null || !value.hasMembers()) {
			throw failure("definition must be an object");
		}
		only(value, "boss", "id", "npcId", "name", "combatLevel",
				"maxHitpoints", "maxHit", "attack", "defence", "arena",
				"spawn", "command", "closeCommand", "objectEntry",
				"entryTeleport", "onSpawn", "onTick", "onDeath", "phases",
				"specials", "dropTable", "privateTicks", "cleanupPolicy");
		String id = requireId(value, "id");
		int npcId = integral(required(value, "npcId"), 0, 14999, "npcId");
		requireLoadedNpc(npcId);
		String name = optionalBoundedString(value.getMember("name"), "name",
				64);
		int combatLevel = integral(required(value, "combatLevel"), 1,
				MAX_STATS, "combatLevel");
		int maxHitpoints = integral(required(value, "maxHitpoints"), 1,
				MAX_STATS, "maxHitpoints");
		int maxHit = integral(required(value, "maxHit"), 0, MAX_STATS,
				"maxHit");
		int attack = integral(required(value, "attack"), 0, MAX_STATS,
				"attack");
		int defence = integral(required(value, "defence"), 0, MAX_STATS,
				"defence");
		BossArena arena = parseArena(required(value, "arena"));
		int[] spawn = parseSpawn(required(value, "spawn"), arena);
		String command = optionalCommand(value.getMember("command"), "command");
		String closeCommand = optionalCommand(value.getMember("closeCommand"),
				"closeCommand");
		ObjectEntry objectEntry = parseObjectEntry(
				value.getMember("objectEntry"));
		Teleport entryTeleport = parseTeleport(
				value.getMember("entryTeleport"), arena);
		if (command == null && objectEntry == null) {
			throw failure("exactly one of 'command' or 'objectEntry' must "
					+ "be present so the boss has a production entry route");
		}
		if (command != null && objectEntry != null) {
			throw failure("'command' and 'objectEntry' are mutually "
					+ "exclusive; register exactly one entry source");
		}
		Value onSpawn = requiredExecutable(value, "onSpawn");
		Value onTick = optionalExecutable(value.getMember("onTick"), "onTick");
		Value onDeath = optionalExecutable(value.getMember("onDeath"),
				"onDeath");
		List<BossPhaseDefinition> phases = parsePhases(
				value.getMember("phases"));
		Map<String, BossSpecialDefinition> specials = parseSpecials(
				value.getMember("specials"));
		String dropTable = optionalReference(value.getMember("dropTable"),
				"dropTable");
		Integer privateTicks = optionalIntegral(
				value.getMember("privateTicks"), 1, MAX_PRIVATE_TICKS,
				"privateTicks");
		if (dropTable == null && privateTicks != null) {
			throw failure("'privateTicks' is allowed only together with a "
					+ "named 'dropTable'");
		}
		if (dropTable != null && privateTicks == null) {
			throw failure("a named 'dropTable' requires 'privateTicks' "
					+ "1.." + MAX_PRIVATE_TICKS);
		}
		BossCleanupPolicy cleanupPolicy = parseCleanupPolicy(
				value.getMember("cleanupPolicy"));
		rejectDuplicateStableId(id, npcId);
		return new BossDefinition(id, npcId, name, combatLevel, maxHitpoints,
				maxHit, attack, defence, arena, spawn[0], spawn[1], command,
				closeCommand,
				objectEntry == null ? 0 : objectEntry.objectId,
				objectEntry == null ? null : objectEntry.action,
				objectEntry != null,
				entryTeleport == null ? 0 : entryTeleport.x,
				entryTeleport == null ? 0 : entryTeleport.y,
				entryTeleport != null, onSpawn, onTick, onDeath, phases,
				specials, dropTable,
				privateTicks == null ? 0 : privateTicks.intValue(),
				dropTable != null, cleanupPolicy, source, schemaVersion);
	}

	private BossArena parseArena(Value value) {
		requireObject(value, "arena");
		only(value, "arena", "minX", "minY", "maxX", "maxY", "plane");
		int minX = integral(required(value, "minX"), 0, MAX_COORDINATE,
				"arena.minX");
		int minY = integral(required(value, "minY"), 0, MAX_COORDINATE,
				"arena.minY");
		int maxX = integral(required(value, "maxX"), 0, MAX_COORDINATE,
				"arena.maxX");
		int maxY = integral(required(value, "maxY"), 0, MAX_COORDINATE,
				"arena.maxY");
		int plane = integral(required(value, "plane"), 0, 3, "arena.plane");
		if (minX > maxX || minY > maxY) {
			throw failure("arena bounds are inverted");
		}
		if (maxX - minX + 1 > MAX_ARENA_SIDE
				|| maxY - minY + 1 > MAX_ARENA_SIDE) {
			throw failure("arena sides must be 1.." + MAX_ARENA_SIDE
					+ " tiles");
		}
		return new BossArena(minX, minY, maxX, maxY, plane);
	}

	private int[] parseSpawn(Value value, BossArena arena) {
		requireObject(value, "spawn");
		only(value, "spawn", "x", "y");
		int x = integral(required(value, "x"), 0, MAX_COORDINATE, "spawn.x");
		int y = integral(required(value, "y"), 0, MAX_COORDINATE, "spawn.y");
		if (!arena.contains(x, y, arena.plane())) {
			throw failure("spawn (" + x + ", " + y + ") must lie inside the "
					+ "declared arena on plane " + arena.plane());
		}
		return new int[] { x, y };
	}

	private ObjectEntry parseObjectEntry(Value value) {
		if (value == null || value.isNull()) {
			return null;
		}
		requireObject(value, "objectEntry");
		only(value, "objectEntry", "objectId", "action");
		int objectId = integral(required(value, "objectId"), 0, MAX_OBJECT_ID,
				"objectEntry.objectId");
		requireLoadedObject(objectId);
		String action = requiredString(value, "action");
		boolean validAction = false;
		for (String entry : ENTRY_ACTIONS) {
			if (entry.equals(action)) {
				validAction = true;
				break;
			}
		}
		if (!validAction) {
			throw failure("objectEntry.action must be one of first, second, "
					+ "third, or fourth");
		}
		return new ObjectEntry(objectId, action);
	}

	private Teleport parseTeleport(Value value, BossArena arena) {
		if (value == null || value.isNull()) {
			return null;
		}
		requireObject(value, "entryTeleport");
		only(value, "entryTeleport", "x", "y");
		int x = integral(required(value, "x"), 0, MAX_COORDINATE,
				"entryTeleport.x");
		int y = integral(required(value, "y"), 0, MAX_COORDINATE,
				"entryTeleport.y");
		if (!arena.contains(x, y, arena.plane())) {
			throw failure("entryTeleport (" + x + ", " + y
					+ ") must lie inside the declared arena on plane "
					+ arena.plane() + ": the owner is a participant and "
					+ "cannot be relocated outside the reservation");
		}
		return new Teleport(x, y);
	}

	private List<BossPhaseDefinition> parsePhases(Value value) {
		List<BossPhaseDefinition> phases = new ArrayList<BossPhaseDefinition>();
		if (value == null || value.isNull()) {
			return phases;
		}
		requireBoundedArray(value, "phases", MAX_PHASES);
		Set<String> names = new HashSet<String>();
		int previousThreshold = 101;
		for (int index = 0; index < value.getArraySize(); index++) {
			Value phase = value.getArrayElement(index);
			requireObject(phase, "phases[" + index + "]");
			only(phase, "phases[" + index + "]", "name",
					"hpPercentThreshold", "onEnter");
			String name = requiredString(phase, "name");
			if (name.isEmpty() || utf8Length(name) > 64) {
				throw failure("phases[" + index
						+ "].name must be 1..64 UTF-8 bytes");
			}
			if (!names.add(name)) {
				throw failure("phases[" + index + "]: duplicate phase name '"
						+ name + "'");
			}
			int threshold = integral(required(phase, "hpPercentThreshold"),
					0, 100, "phases[" + index + "].hpPercentThreshold");
			if (threshold >= previousThreshold) {
				throw failure("phases must be in strictly descending "
						+ "hpPercentThreshold order; phase '" + name
						+ "' at " + threshold + " follows " + previousThreshold);
			}
			previousThreshold = threshold;
			Value onEnter = requiredExecutable(phase, "onEnter");
			phases.add(new BossPhaseDefinition(name, threshold, onEnter));
		}
		return phases;
	}

	private Map<String, BossSpecialDefinition> parseSpecials(Value value) {
		Map<String, BossSpecialDefinition> specials =
				new java.util.LinkedHashMap<String, BossSpecialDefinition>();
		if (value == null || value.isNull()) {
			return specials;
		}
		if (!value.hasMembers()) {
			throw failure("specials must be an object of named specials");
		}
		Set<String> keys = new TreeSet<String>();
		for (String key : value.getMemberKeys()) {
			keys.add(key);
		}
		if (keys.size() < 1 || keys.size() > MAX_SPECIALS) {
			throw failure("specials must contain 1.." + MAX_SPECIALS
					+ " named specials");
		}
		for (String name : keys) {
			if (!name.matches("[a-z][a-z0-9_-]*")) {
				throw failure("specials: invalid special name '" + name
						+ "' (lower-case identifier expected)");
			}
			Value special = value.getMember(name);
			requireObject(special, "specials." + name);
			only(special, "specials." + name, "cooldownTicks", "handler");
			int cooldown = integral(required(special, "cooldownTicks"), 1,
					MAX_COOLDOWN_TICKS, "specials." + name + ".cooldownTicks");
			Value handler = requiredExecutable(special, "handler");
			specials.put(name, new BossSpecialDefinition(name, cooldown,
					handler));
		}
		return specials;
	}

	private BossCleanupPolicy parseCleanupPolicy(Value value) {
		if (value == null || value.isNull()) {
			return BossCleanupPolicy.CLOSE_ON_TERMINAL;
		}
		if (!value.isString()) {
			throw failure("cleanupPolicy must be a string");
		}
		BossCleanupPolicy policy = BossCleanupPolicy.fromScriptName(
				value.asString());
		if (policy == null) {
			throw failure("cleanupPolicy must be '"
					+ BossCleanupPolicy.CLOSE_ON_TERMINAL.scriptName() + "'");
		}
		return policy;
	}

	private void requireLoadedNpc(int npcId) {
		// The same definition source the spawn path validates (npc.json):
		// enforced only while that list is loaded, so synthetic test
		// environments without definitions are not over-rejected.
		if (NpcHandler.hasNpcDefinitions()
				&& !NpcHandler.hasNpcDefinition(npcId)) {
			throw failure("npc id " + npcId + " has no loaded definition");
		}
	}

	private void requireLoadedObject(int objectId) {
		ObjectDefinition[] definitions = ObjectDefinition.getDefinitions();
		if (definitions != null && (objectId >= definitions.length
				|| definitions[objectId] == null
				|| definitions[objectId].getId() != objectId)) {
			throw failure("objectEntry.objectId " + objectId
					+ " has no loaded definition");
		}
	}

	private void rejectDuplicateStableId(String id, int npcId) {
		if (!RegistryStore.isStagingActive()) {
			return;
		}
		for (BossDefinition existing : BossDefinitionRegistry.all(
				RegistryStore.writable()).values()) {
			if (existing.id().equals(id) && existing.npcId() != npcId) {
				throw failure("duplicate boss id '" + id
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
			throw failure("invalid boss id: " + id);
		}
		return id;
	}

	private String optionalCommand(Value value, String member) {
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isString() || value.asString().trim().isEmpty()) {
			throw failure("'" + member + "' must be a non-empty string");
		}
		String command = value.asString().trim();
		if (command.length() > 64
				|| !command.matches("[a-z0-9][a-z0-9._-]*")) {
			throw failure("'" + member + "' must be a lower-case command "
					+ "name of at most 64 characters");
		}
		return command;
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
		if (member.equals("dropTable") && RegistryStore.isStagingActive()
				&& com.rs2.script.drop.DropTableRegistry.get(
						RegistryStore.writable(), reference) == null) {
			throw failure("named drop table '" + reference
					+ "' is not registered in the loading candidate; "
					+ "defineDropTable must run before defineBoss");
		}
		return reference;
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
			throw failure("member '" + member + "' must be executable when "
					+ "present");
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
			throw failure("boss " + label + " must be an object");
		}
	}

	private void requireBoundedArray(Value array, String label, int maximum) {
		if (array == null || !array.hasArrayElements()) {
			throw failure("boss " + label + " must be an array");
		}
		long size = array.getArraySize();
		if (size < 0 || size > maximum) {
			throw failure("boss " + label + " must contain 0.." + maximum
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
			throw failure("boss " + label
					+ " has unknown members " + keys + "; allowed: "
					+ allowedMembers);
		}
	}

	private int integral(Value value, int minimum, int maximum, String label) {
		if (value == null || !value.isNumber()) {
			throw failure("boss " + label + " must be a number");
		}
		double raw = value.asDouble();
		if (!Double.isFinite(raw) || raw != Math.rint(raw)
				|| raw < minimum || raw > maximum) {
			throw failure("boss " + label + " must be integral " + minimum
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
		return "Script registration defineBoss (source: " + source
				+ ", schema v" + schemaVersion + ")";
	}

	private static final class ObjectEntry {
		private final int objectId;
		private final String action;

		ObjectEntry(int objectId, String action) {
			this.objectId = objectId;
			this.action = action;
		}
	}

	private static final class Teleport {
		private final int x;
		private final int y;

		Teleport(int x, int y) {
			this.x = x;
			this.y = y;
		}
	}

}
