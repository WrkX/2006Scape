package com.rs2.script.area;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apollo.cache.def.ObjectDefinition;
import org.graalvm.polyglot.Value;

import com.rs2.game.npcs.NpcHandler;
import com.rs2.script.ScriptEntityLimits;
import com.rs2.script.boss.BossDefinition;
import com.rs2.script.boss.BossDefinitionRegistry;
import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRegistry;
import com.rs2.script.drop.DropTableRegistry;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.shop.ShopDefinitionRegistry;

/**
 * Strict one-way parser for {@code defineArea} schema-v1 definitions.
 *
 * <p>Allowed members: {@code id}, {@code name}, {@code bounds}, {@code npcs},
 * {@code objects}, {@code shops}, {@code quests}, {@code bosses},
 * {@code raids}, {@code onEnter}, and {@code onLeave}. Every npc/object id
 * must be definition-backed when the corresponding definitions are loaded,
 * spawn/object keys are unique per area, object tiles are unique and lie
 * inside the bounds, named drop tables must be registered in the loading
 * candidate, private delivery requires a private TTL, and every referenced
 * shop/quest/boss/raid must resolve to a definition record in the candidate.
 * Nested definitions are not canonical: content must register shops and
 * other families separately and reference them by id.
 */
public final class AreaDefinitionParser {

	private static final int MAX_COORDINATE = 16383;
	private static final int MAX_BOUND_SIDE = 512;
	private static final int MAX_NPCS = 64;
	private static final int MAX_OBJECTS = 64;
	private static final int MAX_REFERENCES = 16;
	private static final int MAX_SPAWN_KEY_LENGTH = 64;
	private static final int MAX_WALK_RADIUS = 64;
	private static final int MAX_RESPAWN_TICKS = 100000;
	private static final int DEFAULT_RESPAWN_TICKS = 100;
	private static final int MAX_PRIVATE_TICKS = 1000;
	private static final int MAX_STATS = 32767;
	private static final int MAX_OBJECT_ID = ScriptEntityLimits.MAX_OBJECT_ID;

	private static final String[] ACTIONS = {
			"first", "second", "third", "fourth"
	};

	private final String source;
	private final int schemaVersion;

	public AreaDefinitionParser(String source, int schemaVersion) {
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public AreaDefinition parse(Value value) {
		if (value == null || !value.hasMembers()) {
			throw failure("definition must be an object");
		}
		only(value, "area", "id", "name", "bounds", "npcs", "objects",
				"shops", "quests", "bosses", "raids", "onEnter", "onLeave");
		String id = requireId(value);
		String name = optionalBoundedString(value.getMember("name"), "name",
				128);
		if (name == null) {
			throw failure("'name' must be a non-empty string");
		}
		AreaBounds bounds = parseBounds(required(value, "bounds"));
		List<AreaNpcSpawn> npcs = parseNpcs(required(value, "npcs"), bounds);
		List<AreaObjectProjection> objects = parseObjects(
				required(value, "objects"), bounds);
		List<String> shops = parseReferences(value.getMember("shops"),
				"shops");
		List<String> quests = parseReferences(value.getMember("quests"),
				"quests");
		List<String> bosses = parseReferences(value.getMember("bosses"),
				"bosses");
		List<String> raids = parseReferences(value.getMember("raids"),
				"raids");
		Value onEnter = optionalExecutable(value.getMember("onEnter"),
				"onEnter");
		Value onLeave = optionalExecutable(value.getMember("onLeave"),
				"onLeave");
		validateReferences(id, shops, quests, bosses, raids);
		return new AreaDefinition(id, name, bounds, npcs, objects, shops,
				quests, bosses, raids, onEnter, onLeave, source,
				schemaVersion);
	}

	private AreaBounds parseBounds(Value value) {
		requireObject(value, "bounds");
		only(value, "bounds", "minX", "minY", "maxX", "maxY", "plane");
		int minX = integral(required(value, "minX"), 0, MAX_COORDINATE,
				"bounds.minX");
		int minY = integral(required(value, "minY"), 0, MAX_COORDINATE,
				"bounds.minY");
		int maxX = integral(required(value, "maxX"), 0, MAX_COORDINATE,
				"bounds.maxX");
		int maxY = integral(required(value, "maxY"), 0, MAX_COORDINATE,
				"bounds.maxY");
		int plane = integral(required(value, "plane"), 0, 3, "bounds.plane");
		if (minX > maxX || minY > maxY) {
			throw failure("bounds are inverted");
		}
		if (maxX - minX + 1 > MAX_BOUND_SIDE
				|| maxY - minY + 1 > MAX_BOUND_SIDE) {
			throw failure("bounds sides must be 1.." + MAX_BOUND_SIDE
					+ " tiles");
		}
		return new AreaBounds(minX, minY, maxX, maxY, plane);
	}

	private List<AreaNpcSpawn> parseNpcs(Value value, AreaBounds bounds) {
		requireBoundedArray(value, "npcs", MAX_NPCS);
		List<AreaNpcSpawn> npcs = new ArrayList<AreaNpcSpawn>();
		Set<String> keys = new HashSet<String>();
		for (int index = 0; index < value.getArraySize(); index++) {
			Value spawn = value.getArrayElement(index);
			requireObject(spawn, "npcs[" + index + "]");
			only(spawn, "npcs[" + index + "]", "key", "npcId", "x", "y",
					"plane", "walkRadius", "direction", "respawnTicks",
					"hp", "maxHit", "attack", "defence", "dropTable",
					"dropPolicy", "privateTicks", "openShop");
			String key = requiredKey(spawn, "npcs[" + index + "]");
			if (!keys.add(key)) {
				throw failure("npcs[" + index + "]: duplicate spawn key '"
						+ key + "'");
			}
			int npcId = integral(required(spawn, "npcId"), 0,
					ScriptEntityLimits.MAX_NPC_ID,
					"npcs[" + index + "].npcId");
			requireLoadedNpc(npcId);
			int x = integral(required(spawn, "x"), 0, MAX_COORDINATE,
					"npcs[" + index + "].x");
			int y = integral(required(spawn, "y"), 0, MAX_COORDINATE,
					"npcs[" + index + "].y");
			Integer planeValue = optionalIntegral(spawn.getMember("plane"), 0,
					3, "npcs[" + index + "].plane");
			int plane = planeValue == null ? 0 : planeValue.intValue();
			if (!bounds.contains(x, y, plane)) {
				throw failure("npcs[" + index + "] spawn (" + x + ", " + y
						+ ", " + plane + ") must lie inside the declared "
						+ "bounds");
			}
			Integer walkRadiusValue = optionalIntegral(
					spawn.getMember("walkRadius"), 0, MAX_WALK_RADIUS,
					"npcs[" + index + "].walkRadius");
			int walkRadius = walkRadiusValue == null ? 0
					: walkRadiusValue.intValue();
			String direction = optionalDirection(spawn.getMember("direction"),
					"npcs[" + index + "].direction");
			Integer respawnTicks = optionalIntegral(
					spawn.getMember("respawnTicks"), 1, MAX_RESPAWN_TICKS,
					"npcs[" + index + "].respawnTicks");
			Integer hp = optionalIntegral(spawn.getMember("hp"), 0, MAX_STATS,
					"npcs[" + index + "].hp");
			Integer maxHit = optionalIntegral(spawn.getMember("maxHit"), 0,
					MAX_STATS, "npcs[" + index + "].maxHit");
			Integer attack = optionalIntegral(spawn.getMember("attack"), 0,
					MAX_STATS, "npcs[" + index + "].attack");
			Integer defence = optionalIntegral(spawn.getMember("defence"), 0,
					MAX_STATS, "npcs[" + index + "].defence");
			String dropTable = optionalReference(spawn.getMember("dropTable"),
					"npcs[" + index + "].dropTable");
			AreaDropPolicy policy = parsePolicy(spawn.getMember("dropPolicy"),
					"npcs[" + index + "].dropPolicy");
			Integer privateTicks = optionalIntegral(
					spawn.getMember("privateTicks"), 1, MAX_PRIVATE_TICKS,
					"npcs[" + index + "].privateTicks");
			String openShop = optionalReference(spawn.getMember("openShop"),
					"npcs[" + index + "].openShop");
			validateDropCoupling(dropTable, policy, privateTicks,
					"npcs[" + index + "]");
			if (openShop != null && walkRadius > 0) {
				throw failure("npcs[" + index
						+ "]: a shop-opening spawn must not walk away from "
						+ "its exact allocation tile");
			}
			int combat = NpcHandler.getNpcListCombat(npcId);
			npcs.add(new AreaNpcSpawn(key, npcId, x, y, plane, walkRadius,
					direction,
					respawnTicks == null ? DEFAULT_RESPAWN_TICKS
							: respawnTicks.intValue(),
					hp == null ? NpcHandler.getNpcListHP(npcId)
							: hp.intValue(),
					maxHit == null ? Math.max(0, combat) : maxHit.intValue(),
					attack == null ? Math.max(0, combat) : attack.intValue(),
					defence == null ? Math.max(0, combat)
							: defence.intValue(),
					dropTable, policy,
					privateTicks == null ? 0 : privateTicks.intValue(),
					dropTable != null, openShop));
		}
		return npcs;
	}

	private List<AreaObjectProjection> parseObjects(Value value,
			AreaBounds bounds) {
		requireBoundedArray(value, "objects", MAX_OBJECTS);
		List<AreaObjectProjection> objects =
				new ArrayList<AreaObjectProjection>();
		Set<String> keys = new HashSet<String>();
		Set<String> tiles = new HashSet<String>();
		for (int index = 0; index < value.getArraySize(); index++) {
			Value object = value.getArrayElement(index);
			requireObject(object, "objects[" + index + "]");
			only(object, "objects[" + index + "]", "key", "objectId", "x",
					"y", "plane", "type", "rotation", "drops");
			String key = requiredKey(object, "objects[" + index + "]");
			if (!keys.add(key)) {
				throw failure("objects[" + index
						+ "]: duplicate object key '" + key + "'");
			}
			int objectId = integral(required(object, "objectId"), 0,
					MAX_OBJECT_ID, "objects[" + index + "].objectId");
			requireLoadedObject(objectId);
			int x = integral(required(object, "x"), 0, MAX_COORDINATE,
					"objects[" + index + "].x");
			int y = integral(required(object, "y"), 0, MAX_COORDINATE,
					"objects[" + index + "].y");
			Integer planeValue = optionalIntegral(object.getMember("plane"),
					0, 3, "objects[" + index + "].plane");
			int plane = planeValue == null ? 0 : planeValue.intValue();
			Integer typeValue = optionalIntegral(object.getMember("type"), 0,
					22, "objects[" + index + "].type");
			int type = typeValue == null ? 10 : typeValue.intValue();
			Integer rotationValue = optionalIntegral(
					object.getMember("rotation"), 0, 3,
					"objects[" + index + "].rotation");
			int rotation = rotationValue == null ? 0
					: rotationValue.intValue();
			if (!bounds.contains(x, y, plane)) {
				throw failure("objects[" + index + "] projection (" + x
						+ ", " + y + ", " + plane
						+ ") must lie inside the declared bounds");
			}
			String tile = x + "," + y + "," + plane;
			if (!tiles.add(tile)) {
				throw failure("objects[" + index
						+ "]: duplicate object tile " + tile);
			}
			List<AreaObjectDrop> drops = parseObjectDrops(
					object.getMember("drops"), "objects[" + index + "]");
			objects.add(new AreaObjectProjection(key, objectId, x, y, plane,
					type, rotation, drops));
		}
		return objects;
	}

	private List<AreaObjectDrop> parseObjectDrops(Value value, String label) {
		List<AreaObjectDrop> drops = new ArrayList<AreaObjectDrop>();
		if (value == null || value.isNull()) {
			return drops;
		}
		requireBoundedArray(value, label + ".drops", 4);
		Set<String> actions = new HashSet<String>();
		for (int index = 0; index < value.getArraySize(); index++) {
			Value drop = value.getArrayElement(index);
			requireObject(drop, label + ".drops[" + index + "]");
			only(drop, label + ".drops[" + index + "]", "action",
					"dropTable", "dropPolicy", "privateTicks");
			String action = requiredString(drop, "action");
			boolean validAction = false;
			for (String entry : ACTIONS) {
				if (entry.equals(action)) {
					validAction = true;
					break;
				}
			}
			if (!validAction) {
				throw failure(label + ".drops[" + index
						+ "].action must be one of first, second, third, "
						+ "or fourth");
			}
			if (!actions.add(action)) {
				throw failure(label + ".drops[" + index
						+ "]: duplicate action '" + action + "'");
			}
			String dropTable = optionalReference(drop.getMember("dropTable"),
					label + ".drops[" + index + "].dropTable");
			AreaDropPolicy policy = parsePolicy(drop.getMember("dropPolicy"),
					label + ".drops[" + index + "].dropPolicy");
			Integer privateTicks = optionalIntegral(
					drop.getMember("privateTicks"), 1, MAX_PRIVATE_TICKS,
					label + ".drops[" + index + "].privateTicks");
			validateDropCoupling(dropTable, policy, privateTicks,
					label + ".drops[" + index + "]");
			drops.add(new AreaObjectDrop(action, dropTable, policy,
					privateTicks == null ? 0 : privateTicks.intValue()));
		}
		return drops;
	}

	private List<String> parseReferences(Value value, String member) {
		List<String> references = new ArrayList<String>();
		if (value == null || value.isNull()) {
			return references;
		}
		requireBoundedArray(value, member, MAX_REFERENCES);
		Set<String> seen = new HashSet<String>();
		for (int index = 0; index < value.getArraySize(); index++) {
			Value entry = value.getArrayElement(index);
			if (entry == null || !entry.isString()
					|| entry.asString().trim().isEmpty()) {
				throw failure(member + "[" + index
						+ "] must be a non-empty string");
			}
			String reference = entry.asString().trim();
			if (reference.length() > 64
					|| !reference.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
				throw failure("invalid " + member + " reference: "
						+ reference);
			}
			if (!seen.add(reference)) {
				throw failure(member + ": duplicate reference '" + reference
						+ "'");
			}
			references.add(reference);
		}
		return references;
	}

	private void validateReferences(String areaId, List<String> shops,
			List<String> quests, List<String> bosses, List<String> raids) {
		if (!RegistryStore.isStagingActive()) {
			return;
		}
		for (String shop : shops) {
			if (ShopDefinitionRegistry.get(RegistryStore.writable(), shop) == null) {
				throw failure("area '" + areaId + "': referenced shop '"
						+ shop
						+ "' is not registered in the loading candidate; "
						+ "defineShop must run before defineArea");
			}
		}
		for (String quest : quests) {
			if (DefinitionRegistry.get(RegistryStore.writable(),
					DefinitionKind.QUEST, quest) == null) {
				throw failure("area '" + areaId + "': referenced quest '"
						+ quest
						+ "' is not registered in the loading candidate");
			}
		}
		for (String boss : bosses) {
			boolean found = false;
			for (BossDefinition definition : BossDefinitionRegistry
					.all(RegistryStore.writable()).values()) {
				if (definition.id().equals(boss)) {
					found = true;
					break;
				}
			}
			if (!found) {
				throw failure("area '" + areaId + "': referenced boss '"
						+ boss
						+ "' is not registered in the loading candidate; "
						+ "defineBoss must run before defineArea");
			}
		}
		for (String raid : raids) {
			if (DefinitionRegistry.get(RegistryStore.writable(),
					DefinitionKind.RAID, raid) == null) {
				throw failure("area '" + areaId + "': referenced raid '"
						+ raid
						+ "' is not registered in the loading candidate");
			}
		}
	}

	private void validateDropCoupling(String dropTable, AreaDropPolicy policy,
			Integer privateTicks, String label) {
		if (dropTable == null && policy != null) {
			throw failure(label + ": 'dropPolicy' is allowed only together "
					+ "with a named 'dropTable'");
		}
		if (dropTable != null && policy == null) {
			throw failure(label
					+ ": a named 'dropTable' requires a 'dropPolicy' of "
					+ "'private-to-killer' or 'public'");
		}
		if (policy == AreaDropPolicy.PRIVATE_TO_KILLER
				&& privateTicks == null) {
			throw failure(label
					+ ": 'private-to-killer' delivery requires 'privateTicks' "
					+ "1.." + MAX_PRIVATE_TICKS);
		}
		if (policy == AreaDropPolicy.PUBLIC && privateTicks != null) {
			throw failure(label
					+ ": 'privateTicks' is not allowed for 'public' delivery");
		}
	}

	private AreaDropPolicy parsePolicy(Value value, String label) {
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isString()) {
			throw failure(label + " must be a string");
		}
		AreaDropPolicy policy = AreaDropPolicy.fromScriptName(
				value.asString());
		if (policy == null) {
			throw failure(label + " must be 'private-to-killer' or 'public'");
		}
		return policy;
	}

	private String optionalDirection(Value value, String label) {
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isString()) {
			throw failure(label + " must be a string");
		}
		String direction = value.asString();
		if (!"north".equals(direction) && !"south".equals(direction)
				&& !"east".equals(direction) && !"west".equals(direction)) {
			throw failure(label + " must be one of north, south, east, west");
		}
		return direction;
	}

	private void requireLoadedNpc(int npcId) {
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
			throw failure("object id " + objectId
					+ " has no loaded definition");
		}
	}

	private String requiredKey(Value parent, String label) {
		String key = requiredString(parent, "key");
		if (key.isEmpty() || key.length() > MAX_SPAWN_KEY_LENGTH
				|| !key.matches("[a-z][a-z0-9_-]*")) {
			throw failure(label
					+ ".key must be a lower-case identifier of 1.."
					+ MAX_SPAWN_KEY_LENGTH + " characters");
		}
		return key;
	}

	private String optionalReference(Value value, String label) {
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isString() || value.asString().trim().isEmpty()) {
			throw failure("'" + label + "' must be a non-empty string");
		}
		String reference = value.asString().trim();
		if (reference.length() > 64
				|| !reference.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
			throw failure("invalid " + label + " reference: " + reference);
		}
		if (label.endsWith("dropTable") && RegistryStore.isStagingActive()
				&& DropTableRegistry.get(RegistryStore.writable(),
						reference) == null) {
			throw failure("named drop table '" + reference
					+ "' is not registered in the loading candidate; "
					+ "defineDropTable must run before defineArea");
		}
		if (label.endsWith("openShop") && RegistryStore.isStagingActive()
				&& ShopDefinitionRegistry.get(RegistryStore.writable(),
						reference) == null) {
			throw failure("scripted shop '" + reference
					+ "' is not registered in the loading candidate; "
					+ "defineShop must run before defineArea");
		}
		return reference;
	}

	private String requireId(Value value) {
		Value idValue = value.getMember("id");
		if (idValue == null || !idValue.isString()
				|| idValue.asString().trim().isEmpty()) {
			throw failure("'id' must be a non-empty string");
		}
		String id = idValue.asString().trim();
		if (id.length() > 64 || !id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
			throw failure("invalid area id: " + id);
		}
		return id;
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
			throw failure("area " + label + " must be an object");
		}
	}

	private void requireBoundedArray(Value array, String label, int maximum) {
		if (array == null || !array.hasArrayElements()) {
			throw failure("area " + label + " must be an array");
		}
		long size = array.getArraySize();
		if (size < 0 || size > maximum) {
			throw failure("area " + label + " must contain 0.." + maximum
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
			throw failure("area " + label + " has unknown members " + keys
					+ "; allowed: " + allowedMembers);
		}
	}

	private int integral(Value value, int minimum, int maximum, String label) {
		if (value == null || !value.isNumber()) {
			throw failure("area " + label + " must be a number");
		}
		double raw = value.asDouble();
		if (!Double.isFinite(raw) || raw != Math.rint(raw)
				|| raw < minimum || raw > maximum) {
			throw failure("area " + label + " must be integral " + minimum
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
		return "Script registration defineArea (source: " + source
				+ ", schema v" + schemaVersion + ")";
	}

}
