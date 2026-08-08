package com.rs2.script.interfacehook;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.graalvm.polyglot.Value;

import com.rs2.script.registries.RegistryStore;

/**
 * Strict one-way parser for {@code defineInterfaceHook} schema-v1.
 *
 * <p>Allowed members: {@code id}, {@code interfaceId}, {@code buttons},
 * {@code onOpen}, {@code onClose}. Button keys are numeric component ids;
 * handlers run only while the hook's interface is the player's main frame.
 */
public final class InterfaceHookDefinitionParser {

	private static final int MAX_BUTTON_ID = 255255;

	private final String source;
	private final int schemaVersion;

	public InterfaceHookDefinitionParser(String source, int schemaVersion) {
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public InterfaceHookDefinition parse(Value value) {
		if (value == null || !value.hasMembers()) {
			throw failure("definition must be an object");
		}
		only(value, "interfaceHook", "id", "interfaceId", "buttons",
				"onOpen", "onClose");
		String id = requireId(value);
		int interfaceId = integral(required(value, "interfaceId"), 0, 65535,
				"interfaceId");
		Value buttonsValue = value.getMember("buttons");
		Map<Integer, Value> buttons = parseButtons(buttonsValue);
		Value onOpen = optionalExecutable(value.getMember("onOpen"), "onOpen");
		Value onClose = optionalExecutable(value.getMember("onClose"),
				"onClose");
		if (buttons.isEmpty() && onOpen == null && onClose == null) {
			throw failure("interface hook must define at least one button "
					+ "handler or lifecycle callback");
		}
		rejectDuplicateStableId(id, interfaceId);
		return new InterfaceHookDefinition(id, interfaceId, buttons, onOpen,
				onClose, source, schemaVersion);
	}

	private Map<Integer, Value> parseButtons(Value buttonsValue) {
		Map<Integer, Value> buttons = new LinkedHashMap<Integer, Value>();
		if (buttonsValue == null || buttonsValue.isNull()) {
			return buttons;
		}
		if (!buttonsValue.hasMembers()) {
			throw failure("'buttons' must be an object when present");
		}
		for (String key : buttonsValue.getMemberKeys()) {
			int buttonId = parseButtonId(key);
			Value handler = buttonsValue.getMember(key);
			if (handler == null || handler.isNull() || !handler.canExecute()) {
				throw failure("buttons[" + key + "] must be executable");
			}
			if (buttons.containsKey(Integer.valueOf(buttonId))) {
				throw failure("duplicate button id " + buttonId
						+ " in buttons map");
			}
			buttons.put(Integer.valueOf(buttonId), handler);
		}
		return buttons;
	}

	private int parseButtonId(String key) {
		if (key == null || key.trim().isEmpty()) {
			throw failure("buttons keys must be non-empty numeric strings");
		}
		try {
			long parsed = Long.parseLong(key.trim());
			if (parsed < 0 || parsed > MAX_BUTTON_ID) {
				throw failure("button id " + key + " is out of range 0.."
						+ MAX_BUTTON_ID);
			}
			int buttonId = (int) parsed;
			if (buttonId / 1000 > 255 || buttonId % 1000 > 255) {
				throw failure("button id " + buttonId
						+ " is not decodable from two unsigned bytes");
			}
			return buttonId;
		} catch (NumberFormatException invalid) {
			throw failure("buttons key '" + key + "' must be a numeric id");
		}
	}

	private void rejectDuplicateStableId(String id, int interfaceId) {
		if (!RegistryStore.isStagingActive()) {
			return;
		}
		for (InterfaceHookDefinition existing
				: InterfaceHookDefinitionRegistry.all(
						RegistryStore.writable()).values()) {
			if (existing.id().equals(id)
					&& existing.interfaceId() != interfaceId) {
				throw failure("duplicate interface hook id '" + id
						+ "' already registered by " + existing.source());
			}
			if (existing.interfaceId() == interfaceId
					&& !existing.id().equals(id)) {
				throw failure("interface id " + interfaceId
						+ " already claimed by hook '" + existing.id()
						+ "' (" + existing.source() + ")");
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
			throw failure("invalid interface hook id: " + id);
		}
		return id;
	}

	private Value required(Value parent, String member) {
		Value entry = parent.getMember(member);
		if (entry == null || entry.isNull()) {
			throw failure("interfaceHook member '" + member
					+ "' must be present");
		}
		return entry;
	}

	private Value optionalExecutable(Value value, String member) {
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.canExecute()) {
			throw failure("member '" + member + "' must be executable when "
					+ "present");
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

	private IllegalArgumentException failure(String message) {
		return new IllegalArgumentException("Script registration "
				+ "defineInterfaceHook (source: " + source + "): " + message);
	}
}
