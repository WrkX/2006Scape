package com.rs2.script.processing;

import java.util.Set;
import java.util.TreeSet;

import org.apollo.cache.def.ItemDefinition;
import org.apollo.cache.def.ObjectDefinition;
import org.graalvm.polyglot.Value;

import com.rs2.script.ScriptEntityLimits;
import com.rs2.script.drop.ItemNameResolver;
import com.rs2.script.quest.QuestSkill;

/**
 * Strict one-way parser for {@code defineProcessingSkill} schema-v1.
 *
 * <p>Allowed members: {@code id}, {@code name}, {@code skill}, {@code level},
 * {@code inputItemId}, {@code objectId}, {@code productItemId},
 * {@code failProductItemId}, {@code experience}, {@code animation},
 * {@code sound}, {@code intervalTicks}, {@code stopBurnLevel},
 * {@code stopBurnLevelWithGloves}, {@code glovesItemId}, {@code burnBonus}.
 */
public final class ProcessingSkillDefinitionParser {

	private static final int MAX_ITEM_ID = ScriptEntityLimits.MAX_ITEM_ID;
	private static final int MAX_OBJECT_ID = ScriptEntityLimits.MAX_OBJECT_ID;
	private static final int MAX_ANIMATION = 65535;
	private static final int MAX_SOUND = 65535;
	private static final int MAX_INTERVAL_TICKS = 100000;
	private static final int MAX_EXPERIENCE = 200000000;
	private static final int DEFAULT_BURN_BONUS = 3;

	private final String source;
	private final int schemaVersion;

	public ProcessingSkillDefinitionParser(String source, int schemaVersion) {
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public ProcessingSkillDefinition parse(Value value) {
		if (value == null || !value.hasMembers()) {
			throw failure("definition must be an object");
		}
		only(value, "processing", "id", "name", "skill", "level",
				"inputItemId", "objectId", "productItemId", "failProductItemId",
				"experience", "animation", "sound", "intervalTicks",
				"stopBurnLevel", "stopBurnLevelWithGloves", "glovesItemId",
				"burnBonus");
		String id = requireId(value);
		String name = optionalBoundedString(value.getMember("name"), "name", 128);
		if (name == null) {
			throw failure("'name' must be a non-empty string");
		}
		QuestSkill skill = QuestSkill.fromScriptName(boundedString(
				required(value, "skill"), "skill", 32));
		int level = integral(required(value, "level"), 1, 255, "level");
		int inputItemId = resolveItemId(value, "inputItemId");
		int objectId = integral(required(value, "objectId"), 0, MAX_OBJECT_ID,
				"objectId");
		requireLoadedObject(objectId);
		int productItemId = resolveItemId(value, "productItemId");
		if (productItemId == inputItemId) {
			throw failure("'productItemId' must differ from 'inputItemId'");
		}
		int failProductItemId = -1;
		if (hasMember(value, "failProductItemId")) {
			failProductItemId = resolveItemId(value, "failProductItemId");
			if (failProductItemId == inputItemId
					|| failProductItemId == productItemId) {
				throw failure("'failProductItemId' must differ from input and "
						+ "product item ids");
			}
		}
		int experience = integral(required(value, "experience"), 1,
				MAX_EXPERIENCE, "experience");
		int animation = integral(required(value, "animation"), -1,
				MAX_ANIMATION, "animation");
		int sound = -1;
		if (hasMember(value, "sound")) {
			sound = integral(required(value, "sound"), 0, MAX_SOUND, "sound");
		}
		int intervalTicks = integral(required(value, "intervalTicks"), 1,
				MAX_INTERVAL_TICKS, "intervalTicks");
		int stopBurnLevel = integral(required(value, "stopBurnLevel"), 1, 255,
				"stopBurnLevel");
		if (stopBurnLevel < level) {
			throw failure("'stopBurnLevel' must be >= 'level'");
		}
		int glovesItemId = -1;
		int stopBurnLevelWithGloves = -1;
		if (hasMember(value, "glovesItemId")) {
			glovesItemId = resolveItemId(value, "glovesItemId");
			stopBurnLevelWithGloves = integral(
					required(value, "stopBurnLevelWithGloves"), 1, 255,
					"stopBurnLevelWithGloves");
			if (stopBurnLevelWithGloves < level) {
				throw failure("'stopBurnLevelWithGloves' must be >= 'level'");
			}
		} else if (hasMember(value, "stopBurnLevelWithGloves")) {
			throw failure("'stopBurnLevelWithGloves' requires 'glovesItemId'");
		}
		int burnBonus = DEFAULT_BURN_BONUS;
		if (hasMember(value, "burnBonus")) {
			burnBonus = integral(required(value, "burnBonus"), 0, 55,
					"burnBonus");
		}
		return new ProcessingSkillDefinition(id, name, skill.getIndex(), level,
				inputItemId, objectId, productItemId, failProductItemId,
				experience, animation, sound, intervalTicks, stopBurnLevel,
				stopBurnLevelWithGloves, glovesItemId, burnBonus, source,
				schemaVersion);
	}

	private static boolean hasMember(Value value, String member) {
		Value entry = value.getMember(member);
		return entry != null && !entry.isNull();
	}

	private int resolveItemId(Value parent, String fieldPath) {
		Value itemValue = parent.getMember(fieldPath);
		if (itemValue == null || itemValue.isNull()) {
			throw failure("'" + fieldPath + "' must be present");
		}
		if (itemValue.isString()) {
			String itemName = itemValue.asString();
			if (itemName == null || itemName.trim().isEmpty()) {
				throw failure("'" + fieldPath + "' must not be empty");
			}
			int resolved = ItemNameResolver.resolve(describe(), fieldPath,
					itemName);
			if (ItemDefinition.exists(resolved)) {
				return resolved;
			}
			throw failure(fieldPath + ": resolved item '" + itemName
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
			throw failure("invalid processing id: " + id);
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
			throw failure("processing " + label + " must be a string");
		}
		String string = value.asString();
		if (string.isEmpty() || utf8Length(string) > maximumBytes) {
			throw failure("processing " + label + " must be 1.." + maximumBytes
					+ " UTF-8 bytes");
		}
		return string;
	}

	private Value required(Value parent, String member) {
		Value value = parent.getMember(member);
		if (value == null || value.isNull()) {
			throw failure("processing member '" + member + "' must be present");
		}
		return value;
	}

	private void only(Value value, String label, String... allowed) {
		Set<String> allowedMembers = new TreeSet<String>();
		for (String member : allowed) {
			allowedMembers.add(member);
		}
		for (String member : value.getMemberKeys()) {
			if (!allowedMembers.contains(member)) {
				throw failure(label + " has unknown member '" + member + "'");
			}
		}
	}

	private int integral(Value value, int min, int max, String label) {
		if (value == null || !value.isNumber()) {
			throw failure("'" + label + "' must be a number");
		}
		double raw = value.asDouble();
		if (!Double.isFinite(raw) || raw != Math.rint(raw)
				|| raw < min || raw > max) {
			throw failure("'" + label + "' must be integral " + min + ".."
					+ max);
		}
		return (int) raw;
	}

	private static int utf8Length(String value) {
		int length = 0;
		for (int index = 0; index < value.length(); index++) {
			char code = value.charAt(index);
			if (code < 0x80) {
				length += 1;
			} else if (code < 0x800) {
				length += 2;
			} else if (Character.isHighSurrogate(code)
					&& index + 1 < value.length()
					&& Character.isLowSurrogate(value.charAt(index + 1))) {
				length += 4;
				index++;
			} else {
				length += 3;
			}
		}
		return length;
	}

	private IllegalArgumentException failure(String message) {
		return new IllegalArgumentException(describe() + ": " + message);
	}

	private String describe() {
		return "Script registration defineProcessingSkill (source: "
				+ source + ")";
	}
}
