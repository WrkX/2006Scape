package com.rs2.script.state;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable defensive snapshot of a complete state store.
 */
public final class ScriptStateSnapshot {

	private final Map<String, Map<String, ScriptStateValue>> namespaces;

	ScriptStateSnapshot(Map<String, Map<String, ScriptStateValue>> source) {
		Map<String, Map<String, ScriptStateValue>> copy = new TreeMap<>();
		for (Map.Entry<String, Map<String, ScriptStateValue>> namespace
				: source.entrySet()) {
			copy.put(namespace.getKey(), Collections.unmodifiableMap(
					new TreeMap<>(namespace.getValue())));
		}
		namespaces = Collections.unmodifiableMap(new LinkedHashMap<>(copy));
	}

	public Map<String, Map<String, ScriptStateValue>> getNamespaces() {
		return namespaces;
	}

	public int entryCount() {
		int count = 0;
		for (Map<String, ScriptStateValue> entries : namespaces.values()) {
			count += entries.size();
		}
		return count;
	}
}
