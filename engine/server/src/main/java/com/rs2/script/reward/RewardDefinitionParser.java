package com.rs2.script.reward;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.graalvm.polyglot.Value;

import com.rs2.script.ScriptEntityLimits;
import com.rs2.script.drop.ItemNameResolver;
import com.rs2.script.quest.QuestSkill;

/**
 * Strict one-way parser for {@code defineReward} schema-v1 definitions.
 *
 * <p>Allowed members: {@code id}, optional {@code items} ({@code 0..28}
 * unique item grants), optional {@code experience} ({@code 0..21} unique
 * skill grants), optional {@code questPoints}, and optional {@code state}
 * mutations. Numeric item ids must be definition-backed when definitions are
 * loaded; string item ids resolve at candidate load through
 * {@link ItemNameResolver} and missing or ambiguous names fail with the
 * definition source and field path. State namespace/key bounds mirror the
 * persistent state store.
 */
public final class RewardDefinitionParser {

	private static final int MAX_ITEMS = 28;
	private static final int MAX_EXPERIENCE = 21;
	private static final int MAX_STATE_MUTATIONS = 32;
	private static final int MAX_ITEM_ID = ScriptEntityLimits.MAX_ITEM_ID;
	private static final int MAX_XP = 200000000;
	private static final int MAX_QUEST_POINTS = 10000;

	private final String source;
	private final int schemaVersion;

	public RewardDefinitionParser(String source, int schemaVersion) {
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public RewardDefinition parse(Value value) {
		if (value == null || !value.hasMembers()) {
			throw failure("definition must be an object");
		}
		only(value, "reward", "id", "items", "experience", "questPoints",
				"state");
		String id = requireId(value);
		List<RewardDefinition.ItemReward> items =
				parseItems(value.getMember("items"));
		List<RewardDefinition.ExperienceReward> experience =
				parseExperience(value.getMember("experience"));
		int questPoints = optionalIntegral(value.getMember("questPoints"), 0,
				-MAX_QUEST_POINTS, MAX_QUEST_POINTS, "questPoints");
		List<RewardDefinition.StateMutation> state =
				parseState(value.getMember("state"));
		return new RewardDefinition(id, source, schemaVersion, items,
				experience, questPoints, state);
	}

	private List<RewardDefinition.ItemReward> parseItems(Value array) {
		List<RewardDefinition.ItemReward> items =
				new ArrayList<RewardDefinition.ItemReward>();
		if (array == null || array.isNull()) {
			return items;
		}
		requireBoundedArray(array, "items", MAX_ITEMS);
		Set<Integer> seen = new HashSet<Integer>();
		for (int index = 0; index < array.getArraySize(); index++) {
			Value item = array.getArrayElement(index);
			requireObject(item, "items[" + index + "]");
			only(item, "items[" + index + "]", "id", "amount");
			int itemId = resolveItemId(item, "items[" + index + "].id");
			if (!seen.add(Integer.valueOf(itemId))) {
				throw failure("items[" + index + "]: duplicate item id "
						+ itemId);
			}
			items.add(new RewardDefinition.ItemReward(itemId,
					integral(required(item, "amount"), 1, Integer.MAX_VALUE,
							"items[" + index + "].amount")));
		}
		return items;
	}

	private List<RewardDefinition.ExperienceReward> parseExperience(
			Value array) {
		List<RewardDefinition.ExperienceReward> experience =
				new ArrayList<RewardDefinition.ExperienceReward>();
		if (array == null || array.isNull()) {
			return experience;
		}
		requireBoundedArray(array, "experience", MAX_EXPERIENCE);
		Set<QuestSkill> seen = new HashSet<QuestSkill>();
		for (int index = 0; index < array.getArraySize(); index++) {
			Value item = array.getArrayElement(index);
			requireObject(item, "experience[" + index + "]");
			only(item, "experience[" + index + "]", "skill", "amount");
			QuestSkill skill = QuestSkill.fromScriptName(boundedString(
					required(item, "skill"), "experience[" + index
							+ "].skill", 32));
			if (!seen.add(skill)) {
				throw failure("experience[" + index
						+ "]: duplicate experience reward: "
						+ skill.getScriptName());
			}
			experience.add(new RewardDefinition.ExperienceReward(
					skill.getIndex(), integral(required(item, "amount"), 1,
							MAX_XP, "experience[" + index + "].amount")));
		}
		return experience;
	}

	private List<RewardDefinition.StateMutation> parseState(Value array) {
		List<RewardDefinition.StateMutation> mutations =
				new ArrayList<RewardDefinition.StateMutation>();
		if (array == null || array.isNull()) {
			return mutations;
		}
		requireBoundedArray(array, "state", MAX_STATE_MUTATIONS);
		Set<String> seen = new HashSet<String>();
		for (int index = 0; index < array.getArraySize(); index++) {
			Value item = array.getArrayElement(index);
			requireObject(item, "state[" + index + "]");
			only(item, "state[" + index + "]", "namespace", "key", "value");
			String namespace = boundedString(required(item, "namespace"),
					"state[" + index + "].namespace",
					com.rs2.script.state.ScriptStateLimits.MAX_NAMESPACE_BYTES);
			String key = boundedString(required(item, "key"),
					"state[" + index + "].key",
					com.rs2.script.state.ScriptStateLimits.MAX_KEY_BYTES);
			if (!namespace.matches("[a-z][a-z0-9.-]*")
					|| !key.matches("[a-z][a-z0-9.-]*")) {
				throw failure("state[" + index
						+ "]: namespace and key must be lower-case "
						+ "identifiers separated by '.' or '-'");
			}
			if (namespace.startsWith("sys.")) {
				throw failure("state[" + index
						+ "]: namespace '" + namespace
						+ "' is reserved for engine state");
			}
			String mutationKey = namespace + ":" + key;
			if (!seen.add(mutationKey)) {
				throw failure("state[" + index + "]: duplicate mutation for "
						+ mutationKey);
			}
			Value value = required(item, "value");
			if (value.isBoolean()) {
				mutations.add(new RewardDefinition.StateMutation(namespace,
						key, value.asBoolean()));
			} else if (value.isNumber()) {
				double number = value.asDouble();
				if (!Double.isFinite(number)) {
					throw failure("state[" + index
							+ "].value must be a finite number");
				}
				mutations.add(new RewardDefinition.StateMutation(namespace,
						key, number));
			} else if (value.isString()) {
				mutations.add(new RewardDefinition.StateMutation(namespace,
						key, boundedString(value, "state[" + index
								+ "].value",
								com.rs2.script.state.ScriptStateLimits
										.MAX_STRING_BYTES)));
			} else {
				throw failure("state[" + index
						+ "].value must be a boolean, finite number, or string");
			}
		}
		return mutations;
	}

	private int resolveItemId(Value entry, String fieldPath) {
		Value itemValue = entry.getMember("id");
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
			if (org.apollo.cache.def.ItemDefinition.exists(resolved)) {
				return resolved;
			}
			throw failure(fieldPath + ": resolved item '" + name
					+ "' has no loaded definition");
		}
		int itemId = integral(itemValue, 1, MAX_ITEM_ID, fieldPath);
		if (org.apollo.cache.def.ItemDefinition.getDefinitions() != null
				&& !org.apollo.cache.def.ItemDefinition.exists(itemId)) {
			throw failure(fieldPath + ": item " + itemId
					+ " has no loaded definition");
		}
		return itemId;
	}

	private String requireId(Value value) {
		Value idValue = value.getMember("id");
		if (idValue == null || !idValue.isString()
				|| idValue.asString().trim().isEmpty()) {
			throw failure("id must be a non-empty string");
		}
		String id = idValue.asString().trim();
		if (id.length() > 64 || !id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
			throw failure("invalid reward id: " + id);
		}
		return id;
	}

	private void requireObject(Value value, String label) {
		if (value == null || !value.hasMembers()) {
			throw failure("reward " + label + " must be an object");
		}
	}

	private void requireBoundedArray(Value array, String label,
			int maximum) {
		if (array == null || !array.hasArrayElements()) {
			throw failure("reward " + label + " must be an array");
		}
		long size = array.getArraySize();
		if (size < 0 || size > maximum) {
			throw failure("reward " + label + " must contain 0.." + maximum
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
		if (!keys.equals(allowedMembers)) {
			throw failure("reward " + label + " must have exactly the members "
					+ allowedMembers);
		}
	}

	private Value required(Value parent, String member) {
		Value value = parent.getMember(member);
		if (value == null || value.isNull()) {
			throw failure("reward member '" + member + "' must be present");
		}
		return value;
	}

	private String boundedString(Value value, String label,
			int maximumBytes) {
		if (value == null || !value.isString()) {
			throw failure("reward " + label + " must be a string");
		}
		String string = value.asString();
		if (string.isEmpty() || utf8Length(string) > maximumBytes) {
			throw failure("reward " + label + " must be 1.." + maximumBytes
					+ " UTF-8 bytes");
		}
		return string;
	}

	private int integral(Value value, int minimum, int maximum,
			String label) {
		if (value == null || !value.isNumber()) {
			throw failure("reward " + label + " must be a number");
		}
		double raw = value.asDouble();
		if (!Double.isFinite(raw) || raw != Math.rint(raw)
				|| raw < minimum || raw > maximum) {
			throw failure("reward " + label + " must be integral " + minimum
					+ ".." + maximum);
		}
		return (int) raw;
	}

	private int optionalIntegral(Value value, int fallback,
			int minimum, int maximum, String label) {
		if (value == null || value.isNull()) {
			return fallback;
		}
		return integral(value, minimum, maximum, label);
	}

	private static int utf8Length(String value) {
		return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
	}

	private IllegalArgumentException failure(String message) {
		return new IllegalArgumentException(describe() + ": " + message);
	}

	private String describe() {
		return "Script registration defineReward (source: " + source
				+ ", schema v" + schemaVersion + ")";
	}

}
