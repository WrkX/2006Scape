package com.rs2.script.mob;

import java.util.Set;
import java.util.TreeSet;

import org.graalvm.polyglot.Value;

import com.rs2.game.npcs.NpcHandler;
import com.rs2.script.ScriptEntityLimits;
import com.rs2.script.registries.RegistryStore;

/**
 * Strict one-way parser for {@code defineMob} schema-v1.
 *
 * <p>Allowed members: {@code id}, {@code npcId}, {@code name},
 * {@code aggression}, {@code combatStyle}, {@code attackSpeed},
 * {@code maxHit}, {@code animation}, {@code onSpawn}, {@code onTick},
 * {@code onDeath}.
 */
public final class MobDefinitionParser {

	private static final int MAX_AGGRESSION = 64;
	private static final int MAX_ATTACK_SPEED = 100;
	private static final int MAX_HIT = 32767;
	private static final int MAX_ANIMATION = 65535;

	private final String source;
	private final int schemaVersion;

	public MobDefinitionParser(String source, int schemaVersion) {
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public MobDefinition parse(Value value) {
		if (value == null || !value.hasMembers()) {
			throw failure("definition must be an object");
		}
		only(value, "mob", "id", "npcId", "name", "aggression", "combatStyle",
				"attackSpeed", "maxHit", "animation", "onSpawn", "onTick",
				"onDeath");
		String id = requireId(value);
		int npcId = integral(required(value, "npcId"), 0,
				ScriptEntityLimits.MAX_NPC_ID, "npcId");
		requireLoadedNpc(npcId);
		String name = optionalBoundedString(value.getMember("name"), "name",
				64);
		if (name == null) {
			name = id;
		}
		int aggression = integral(required(value, "aggression"), 0,
				MAX_AGGRESSION, "aggression");
		MobCombatStyle combatStyle = MobCombatStyle.fromScriptName(
				boundedString(required(value, "combatStyle"), "combatStyle",
						16));
		if (combatStyle == null) {
			throw failure("'combatStyle' must be 'melee', 'ranged', or 'magic'");
		}
		int attackSpeed = integral(required(value, "attackSpeed"), 1,
				MAX_ATTACK_SPEED, "attackSpeed");
		int maxHit = integral(required(value, "maxHit"), 0, MAX_HIT, "maxHit");
		int animation = -1;
		if (hasMember(value, "animation")) {
			animation = integral(required(value, "animation"), -1,
					MAX_ANIMATION, "animation");
		}
		Value onSpawn = optionalExecutable(value.getMember("onSpawn"),
				"onSpawn");
		Value onTick = optionalExecutable(value.getMember("onTick"), "onTick");
		Value onDeath = optionalExecutable(value.getMember("onDeath"),
				"onDeath");
		rejectDuplicateStableId(id, npcId);
		return new MobDefinition(id, npcId, name, aggression, combatStyle,
				attackSpeed, maxHit, animation, onSpawn, onTick, onDeath,
				source, schemaVersion);
	}

	private void requireLoadedNpc(int npcId) {
		if (NpcHandler.hasNpcDefinitions()
				&& !NpcHandler.hasNpcDefinition(npcId)) {
			throw failure("npc id " + npcId + " has no loaded definition");
		}
	}

	private void rejectDuplicateStableId(String id, int npcId) {
		if (!RegistryStore.isStagingActive()) {
			return;
		}
		for (MobDefinition existing : MobDefinitionRegistry.all(
				RegistryStore.writable()).values()) {
			if (existing.id().equals(id) && existing.npcId() != npcId) {
				throw failure("duplicate mob id '" + id
						+ "' already registered by " + existing.source());
			}
		}
	}

	private static boolean hasMember(Value value, String member) {
		Value entry = value.getMember(member);
		return entry != null && !entry.isNull();
	}

	private String requireId(Value value) {
		Value idValue = value.getMember("id");
		if (idValue == null || !idValue.isString()
				|| idValue.asString().trim().isEmpty()) {
			throw failure("'id' must be a non-empty string");
		}
		String id = idValue.asString().trim();
		if (id.length() > 64 || !id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
			throw failure("invalid mob id: " + id);
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
			throw failure("mob " + label + " must be a string");
		}
		String string = value.asString();
		if (string.isEmpty() || utf8Length(string) > maximumBytes) {
			throw failure("mob " + label + " must be 1.." + maximumBytes
					+ " UTF-8 bytes");
		}
		return string;
	}

	private Value required(Value parent, String member) {
		Value value = parent.getMember(member);
		if (value == null || value.isNull()) {
			throw failure("mob member '" + member + "' must be present");
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

	private static boolean isExecutable(Value value) {
		return value != null && !value.isNull() && value.canExecute();
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
		return "Script registration defineMob (source: " + source + ")";
	}
}
