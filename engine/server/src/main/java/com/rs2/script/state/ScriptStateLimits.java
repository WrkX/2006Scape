package com.rs2.script.state;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Stable limits for persisted script-owned player state.
 */
public final class ScriptStateLimits {

	public static final int MAX_NAMESPACES = 32;
	public static final int MAX_ENTRIES_PER_NAMESPACE = 256;
	public static final int MAX_TOTAL_ENTRIES = 1024;
	public static final int MAX_NAMESPACE_BYTES = 48;
	public static final int MAX_KEY_BYTES = 96;
	public static final int MAX_STRING_BYTES = 4096;
	public static final int MAX_ENCODED_PAYLOAD_BYTES = 65536;
	public static final String QUEST_NAMESPACE = "__quest";

	private static final Pattern PUBLIC_NAME = Pattern.compile(
			"^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*$");

	public static void validatePublicNamespace(String namespace) {
		validatePublicIdentifier(namespace, MAX_NAMESPACE_BYTES, "namespace");
		if (isReserved(namespace)) {
			throw new ScriptStateException("State namespace is reserved: " + namespace);
		}
	}

	public static void validatePublicKey(String key) {
		validatePublicIdentifier(key, MAX_KEY_BYTES, "key");
		if (isReserved(key)) {
			throw new ScriptStateException("State key is reserved: " + key);
		}
	}

	static void validateStoredNamespace(String namespace) {
		if (QUEST_NAMESPACE.equals(namespace)) {
			return;
		}
		validatePublicNamespace(namespace);
	}

	static void validateStoredKey(String namespace, String key) {
		if (QUEST_NAMESPACE.equals(namespace)) {
			validateBytes(key, MAX_KEY_BYTES, "internal quest key");
			if (key.isEmpty() || key.indexOf('\n') >= 0 || key.indexOf('\r') >= 0) {
				throw new ScriptStateException("Invalid internal quest key");
			}
			return;
		}
		validatePublicKey(key);
	}

	static void validateString(String value) {
		validateBytes(value, MAX_STRING_BYTES, "string value");
	}

	private static void validatePublicIdentifier(String value, int maxBytes,
			String label) {
		validateBytes(value, maxBytes, label);
		if (!PUBLIC_NAME.matcher(value).matches()) {
			throw new ScriptStateException("Invalid state " + label + ": " + value);
		}
	}

	private static void validateBytes(String value, int maxBytes, String label) {
		if (value == null) {
			throw new ScriptStateException("State " + label + " must not be null");
		}
		int length = value.getBytes(StandardCharsets.UTF_8).length;
		if (length < 1 || length > maxBytes) {
			throw new ScriptStateException(
					"State " + label + " must be 1-" + maxBytes + " UTF-8 bytes");
		}
	}

	private static boolean isReserved(String value) {
		return QUEST_NAMESPACE.equals(value) || "_".equals(value)
				|| "$".equals(value) || value.startsWith("sys.");
	}

	private ScriptStateLimits() {
	}
}
