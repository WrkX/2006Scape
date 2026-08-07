package com.rs2.script.overlay;

import java.util.Set;
import java.util.TreeSet;

import org.graalvm.polyglot.Value;

/**
 * Shared strict parsing helpers for schema-v1 overlay kits.
 */
final class OverlayParserSupport {

	private OverlayParserSupport() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}

	static void only(Value value, String label, String... allowed) {
		Set<String> allowedMembers = new TreeSet<String>();
		for (String member : allowed) {
			allowedMembers.add(member);
		}
		for (String member : value.getMemberKeys()) {
			if (!allowedMembers.contains(member)) {
				throw failure(label, label + " has unknown member '" + member
						+ "'");
			}
		}
	}

	static String requireId(Value value, String label) {
		Value idValue = value.getMember("id");
		if (idValue == null || !idValue.isString()
				|| idValue.asString().trim().isEmpty()) {
			throw failure(label, "'id' must be a non-empty string");
		}
		String id = idValue.asString().trim();
		if (id.length() > 64 || !id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
			throw failure(label, "invalid overlay id: " + id);
		}
		return id;
	}

	static String optionalBoundedString(Value value, String label,
			String member, int maximumBytes) {
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isString() || value.asString().isEmpty()) {
			throw failure(label, "'" + member + "' must be a non-empty string");
		}
		String string = value.asString();
		if (utf8Length(string) > maximumBytes) {
			throw failure(label, "'" + member + "' must be 1.." + maximumBytes
					+ " UTF-8 bytes");
		}
		return string;
	}

	static Value required(Value parent, String label, String member) {
		Value value = parent.getMember(member);
		if (value == null || value.isNull()) {
			throw failure(label, "member '" + member + "' must be present");
		}
		return value;
	}

	static boolean hasMember(Value value, String member) {
		Value entry = value.getMember(member);
		return entry != null && !entry.isNull();
	}

	static int integral(Value value, String label, String member, int min,
			int max) {
		if (value == null || !value.isNumber()) {
			throw failure(label, "'" + member + "' must be a number");
		}
		double raw = value.asDouble();
		if (!Double.isFinite(raw) || raw != Math.rint(raw)
				|| raw < min || raw > max) {
			throw failure(label, "'" + member + "' must be integral " + min
					+ ".." + max);
		}
		return (int) raw;
	}

	static Boolean optionalBoolean(Value value, String label, String member) {
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isBoolean()) {
			throw failure(label, "'" + member + "' must be a boolean");
		}
		return Boolean.valueOf(value.asBoolean());
	}

	static int[] optionalRequirementLevels(Value value, String label) {
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.hasMembers()) {
			throw failure(label, "'requirements' must be an object");
		}
		only(value, label + ".requirements", "attack", "strength", "defence",
				"hitpoints", "ranged", "prayer", "magic");
		int[] levels = new int[7];
		levels[0] = readOptionalLevel(value, label, "attack", 1, 99);
		levels[1] = readOptionalLevel(value, label, "strength", 1, 99);
		levels[2] = readOptionalLevel(value, label, "defence", 1, 99);
		levels[3] = readOptionalLevel(value, label, "hitpoints", 1, 99);
		levels[4] = readOptionalLevel(value, label, "ranged", 1, 99);
		levels[5] = readOptionalLevel(value, label, "prayer", 1, 99);
		levels[6] = readOptionalLevel(value, label, "magic", 1, 99);
		return levels;
	}

	private static int readOptionalLevel(Value parent, String label,
			String member, int min, int max) {
		if (!hasMember(parent, member)) {
			return 1;
		}
		return integral(parent.getMember(member), label, member, min, max);
	}

	static int[] optionalBonuses(Value value, String label) {
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.hasMembers()) {
			throw failure(label, "'bonuses' must be an object");
		}
		only(value, label + ".bonuses", "attackStab", "attackSlash",
				"attackCrush", "attackMagic", "attackRange", "defenceStab",
				"defenceSlash", "defenceCrush", "defenceMagic", "defenceRange",
				"strength", "prayer");
		int[] bonuses = new int[12];
		bonuses[0] = readOptionalBonus(value, label, "attackStab");
		bonuses[1] = readOptionalBonus(value, label, "attackSlash");
		bonuses[2] = readOptionalBonus(value, label, "attackCrush");
		bonuses[3] = readOptionalBonus(value, label, "attackMagic");
		bonuses[4] = readOptionalBonus(value, label, "attackRange");
		bonuses[5] = readOptionalBonus(value, label, "defenceStab");
		bonuses[6] = readOptionalBonus(value, label, "defenceSlash");
		bonuses[7] = readOptionalBonus(value, label, "defenceCrush");
		bonuses[8] = readOptionalBonus(value, label, "defenceMagic");
		bonuses[9] = readOptionalBonus(value, label, "defenceRange");
		bonuses[10] = readOptionalBonus(value, label, "strength");
		bonuses[11] = readOptionalBonus(value, label, "prayer");
		return bonuses;
	}

	private static int readOptionalBonus(Value parent, String label,
			String member) {
		if (!hasMember(parent, member)) {
			return 0;
		}
		return integral(parent.getMember(member), label, member, -32768, 32767);
	}

	static String[] optionalActions(Value value, String label) {
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.hasArrayElements()) {
			throw failure(label, "'actions' must be an array");
		}
		long length = value.getArraySize();
		if (length > 5) {
			throw failure(label, "'actions' must have at most 5 entries");
		}
		String[] actions = new String[5];
		for (int index = 0; index < length; index++) {
			Value entry = value.getArrayElement(index);
			if (entry == null || entry.isNull()) {
				continue;
			}
			if (!entry.isString() || entry.asString().isEmpty()) {
				throw failure(label, "'actions[" + index
						+ "]' must be a non-empty string");
			}
			String action = entry.asString();
			if (utf8Length(action) > 32) {
				throw failure(label, "'actions[" + index
						+ "]' must be 1..32 UTF-8 bytes");
			}
			actions[index] = action;
		}
		return actions;
	}

	static String requireEquipSlot(String slot, String label) {
		if (slot == null) {
			return null;
		}
		String normalized = slot.toLowerCase(java.util.Locale.ROOT);
		if ("hat".equals(normalized) || "cape".equals(normalized)
				|| "amulet".equals(normalized) || "weapon".equals(normalized)
				|| "chest".equals(normalized) || "shield".equals(normalized)
				|| "legs".equals(normalized) || "hands".equals(normalized)
				|| "feet".equals(normalized) || "ring".equals(normalized)
				|| "arrows".equals(normalized)) {
			return normalized;
		}
		throw failure(label,
				"'equipSlot' must be one of hat, cape, amulet, weapon, "
						+ "chest, shield, legs, hands, feet, ring, arrows");
	}

	static int utf8Length(String value) {
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

	static IllegalArgumentException failure(String label, String message) {
		return new IllegalArgumentException("Script registration " + label
				+ ": " + message);
	}
}
