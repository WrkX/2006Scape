package com.rs2.script.resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apollo.cache.def.ItemDefinition;
import org.apollo.cache.def.ObjectDefinition;
import org.graalvm.polyglot.Value;

import com.rs2.script.ScriptEntityLimits;
import com.rs2.script.drop.ItemNameResolver;
import com.rs2.script.quest.QuestSkill;

/**
 * Strict one-way parser for {@code defineGatheringResource} schema-v1
 * definitions.
 *
 * <p>Allowed members: {@code id}, {@code name}, {@code objectId},
 * {@code npcId}, {@code action}, {@code skill}, {@code level}, {@code tools},
 * {@code animation}, {@code intervalTicks}, {@code successChance},
 * {@code rewards}, {@code experience}, {@code depletes},
 * {@code depletedObjectId}, and {@code respawnTicks}. Exactly one of
 * {@code objectId} or {@code npcId} must be present. Object targets require
 * {@code depletedObjectId} and {@code respawnTicks} when {@code depletes} is
 * true (the default for object targets). NPC targets default to
 * {@code depletes=false} and keep harvesting until the player moves away or
 * inventory is full.
 */
public final class GatheringResourceDefinitionParser {

	private static final int MAX_OBJECT_ID = ScriptEntityLimits.MAX_OBJECT_ID;
	private static final int MAX_TOOLS = 16;
	private static final int MAX_REWARDS = 16;
	private static final int MAX_ITEM_ID = ScriptEntityLimits.MAX_ITEM_ID;
	private static final int MAX_ITEM_AMOUNT = 2147483647;
	private static final int MAX_ANIMATION = 65535;
	private static final int MAX_INTERVAL_TICKS = 100000;
	private static final int MAX_CHANCE_DENOMINATOR = 1000000;
	private static final int MAX_EXPERIENCE = 200000000;
	private static final int MAX_RESPAWN_TICKS = 100000;

	private static final String[] ACTIONS = {
			"first", "second", "third", "fourth"
	};

	private final String source;
	private final int schemaVersion;

	public GatheringResourceDefinitionParser(String source, int schemaVersion) {
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public GatheringResourceDefinition parse(Value value) {
		if (value == null || !value.hasMembers()) {
			throw failure("definition must be an object");
		}
		only(value, "resource", "id", "name", "objectId", "npcId", "action",
				"skill", "level", "tools", "animation", "intervalTicks",
				"successChance", "rewards", "experience", "depletes",
				"depletedObjectId", "respawnTicks");
		String id = requireId(value);
		String name = optionalBoundedString(value.getMember("name"), "name", 128);
		if (name == null) {
			throw failure("'name' must be a non-empty string");
		}
		Value objectMember = value.getMember("objectId");
		Value npcMember = value.getMember("npcId");
		boolean hasObject = objectMember != null && !objectMember.isNull();
		boolean hasNpc = npcMember != null && !npcMember.isNull();
		if (hasObject == hasNpc) {
			throw failure("exactly one of 'objectId' or 'npcId' must be present");
		}
		int objectId = 0;
		int npcId = 0;
		if (hasObject) {
			objectId = integral(objectMember, 0, MAX_OBJECT_ID, "objectId");
			requireLoadedObject(objectId);
		} else {
			npcId = integral(npcMember, 1, ScriptEntityLimits.MAX_NPC_ID,
					"npcId");
		}
		boolean depletes = hasObject;
		Value depletesMember = value.getMember("depletes");
		if (depletesMember != null && !depletesMember.isNull()) {
			if (!depletesMember.isBoolean()) {
				throw failure("'depletes' must be a boolean when present");
			}
			depletes = depletesMember.asBoolean();
		}
		String action = requiredAction(value);
		QuestSkill skill = QuestSkill.fromScriptName(boundedString(
				required(value, "skill"), "skill", 32));
		int level = integral(required(value, "level"), 1, 255, "level");
		List<GatheringResourceDefinition.Tool> tools = parseTools(
				required(value, "tools"));
		int animation = integral(required(value, "animation"), -1,
				MAX_ANIMATION, "animation");
		int intervalTicks = integral(required(value, "intervalTicks"), 1,
				MAX_INTERVAL_TICKS, "intervalTicks");
		Value chance = required(value, "successChance");
		requireObject(chance, "successChance");
		only(chance, "successChance", "numerator", "denominator");
		int numerator = integral(required(chance, "numerator"), 0,
				MAX_CHANCE_DENOMINATOR, "successChance.numerator");
		int denominator = integral(required(chance, "denominator"), 1,
				MAX_CHANCE_DENOMINATOR, "successChance.denominator");
		if (numerator > denominator) {
			throw failure("successChance.numerator must not exceed "
					+ "successChance.denominator");
		}
		List<GatheringResourceDefinition.ItemReward> rewards = parseRewards(
				required(value, "rewards"));
		int experience = integral(required(value, "experience"), 1,
				MAX_EXPERIENCE, "experience");
		int depletedObjectId = -1;
		int respawnTicks = 0;
		if (depletes) {
			depletedObjectId = integral(required(value, "depletedObjectId"), 0,
					MAX_OBJECT_ID, "depletedObjectId");
			requireLoadedObject(depletedObjectId);
			if (depletedObjectId == objectId) {
				throw failure("'depletedObjectId' must differ from the resource "
						+ "objectId");
			}
			respawnTicks = integral(required(value, "respawnTicks"), 1,
					MAX_RESPAWN_TICKS, "respawnTicks");
		} else if (hasMember(value, "depletedObjectId")
				|| hasMember(value, "respawnTicks")) {
			throw failure("'depletedObjectId' and 'respawnTicks' are only "
					+ "allowed when depletes is true");
		}
		return new GatheringResourceDefinition(id, name, objectId, npcId,
				action, skill.getIndex(), level, tools, animation, intervalTicks,
				numerator, denominator, rewards, experience, depletes,
				depletedObjectId, respawnTicks, source, schemaVersion);
	}

	private static boolean hasMember(Value value, String member) {
		Value entry = value.getMember(member);
		return entry != null && !entry.isNull();
	}

	private List<GatheringResourceDefinition.Tool> parseTools(Value array) {
		requireBoundedArray(array, "tools", MAX_TOOLS);
		List<GatheringResourceDefinition.Tool> tools =
				new ArrayList<GatheringResourceDefinition.Tool>();
		Set<Integer> seen = new HashSet<Integer>();
		for (int index = 0; index < array.getArraySize(); index++) {
			Value tool = array.getArrayElement(index);
			requireObject(tool, "tools[" + index + "]");
			only(tool, "tools[" + index + "]", "itemId", "consume");
			int itemId = resolveItemId(tool, "tools[" + index + "].itemId");
			if (!seen.add(Integer.valueOf(itemId))) {
				throw failure("tools[" + index + "]: duplicate tool item id "
						+ itemId);
			}
			boolean consume = false;
			Value consumeValue = tool.getMember("consume");
			if (consumeValue != null && !consumeValue.isNull()) {
				if (!consumeValue.isBoolean()) {
					throw failure("tools[" + index
							+ "].consume must be a boolean when present");
				}
				consume = consumeValue.asBoolean();
			}
			tools.add(new GatheringResourceDefinition.Tool(itemId, consume));
		}
		if (tools.isEmpty()) {
			throw failure("a resource must declare at least one tool");
		}
		return tools;
	}

	private List<GatheringResourceDefinition.ItemReward> parseRewards(
			Value array) {
		requireBoundedArray(array, "rewards", MAX_REWARDS);
		List<GatheringResourceDefinition.ItemReward> rewards =
				new ArrayList<GatheringResourceDefinition.ItemReward>();
		for (int index = 0; index < array.getArraySize(); index++) {
			Value reward = array.getArrayElement(index);
			requireObject(reward, "rewards[" + index + "]");
			only(reward, "rewards[" + index + "]", "itemId", "amount");
			rewards.add(new GatheringResourceDefinition.ItemReward(
					resolveItemId(reward, "rewards[" + index + "].itemId"),
					integral(required(reward, "amount"), 1, MAX_ITEM_AMOUNT,
							"rewards[" + index + "].amount")));
		}
		if (rewards.isEmpty()) {
			throw failure("a resource must declare at least one reward");
		}
		return rewards;
	}

	private int resolveItemId(Value entry, String fieldPath) {
		Value itemValue = entry.getMember("itemId");
		if (itemValue == null) {
			throw failure(fieldPath + " must be present");
		}
		if (itemValue.isString()) {
			String name = itemValue.asString();
			if (name == null || name.trim().isEmpty()) {
				throw failure(fieldPath + " must not be empty");
			}
			int resolved = ItemNameResolver.resolve(describe(), fieldPath,
					name);
			if (ItemDefinition.exists(resolved)) {
				return resolved;
			}
			throw failure(fieldPath + ": resolved item '" + name
					+ "' has no loaded definition");
		}
		int itemId = integral(itemValue, 1, MAX_ITEM_ID, fieldPath);
		if (ItemDefinition.getDefinitions() != null
				&& !ItemDefinition.exists(itemId)) {
			throw failure(fieldPath + ": item " + itemId
					+ " has no loaded definition");
		}
		return itemId;
	}

	private String requiredAction(Value value) {
		Value actionValue = required(value, "action");
		if (!actionValue.isString()) {
			throw failure("'action' must be a string");
		}
		String action = actionValue.asString();
		for (String entry : ACTIONS) {
			if (entry.equals(action)) {
				return action;
			}
		}
		throw failure("'action' must be one of first, second, third, or fourth");
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

	private String requireId(Value value) {
		Value idValue = value.getMember("id");
		if (idValue == null || !idValue.isString()
				|| idValue.asString().trim().isEmpty()) {
			throw failure("'id' must be a non-empty string");
		}
		String id = idValue.asString().trim();
		if (id.length() > 64 || !id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
			throw failure("invalid resource id: " + id);
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

	private String boundedString(Value value, String label,
			int maximumBytes) {
		if (value == null || !value.isString()) {
			throw failure("resource " + label + " must be a string");
		}
		String string = value.asString();
		if (string.isEmpty() || utf8Length(string) > maximumBytes) {
			throw failure("resource " + label + " must be 1.." + maximumBytes
					+ " UTF-8 bytes");
		}
		return string;
	}

	private Value required(Value parent, String member) {
		Value value = parent.getMember(member);
		if (value == null || value.isNull()) {
			throw failure("resource member '" + member + "' must be present");
		}
		return value;
	}

	private void requireObject(Value value, String label) {
		if (value == null || !value.hasMembers()) {
			throw failure("resource " + label + " must be an object");
		}
	}

	private void requireBoundedArray(Value array, String label, int maximum) {
		if (array == null || !array.hasArrayElements()) {
			throw failure("resource " + label + " must be an array");
		}
		long size = array.getArraySize();
		if (size < 0 || size > maximum) {
			throw failure("resource " + label + " must contain 0.." + maximum
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
			throw failure("resource " + label + " has unknown members " + keys
					+ "; allowed: " + allowedMembers);
		}
	}

	private int integral(Value value, int minimum, int maximum, String label) {
		if (value == null || !value.isNumber()) {
			throw failure("resource " + label + " must be a number");
		}
		double raw = value.asDouble();
		if (!Double.isFinite(raw) || raw != Math.rint(raw)
				|| raw < minimum || raw > maximum) {
			throw failure("resource " + label + " must be integral " + minimum
					+ ".." + maximum);
		}
		return (int) raw;
	}

	private static int utf8Length(String value) {
		return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
	}

	private IllegalArgumentException failure(String message) {
		return new IllegalArgumentException(describe() + ": " + message);
	}

	private String describe() {
		return "Script registration defineGatheringResource (source: "
				+ source + ", schema v" + schemaVersion + ")";
	}

}
