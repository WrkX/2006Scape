package com.rs2.script.state;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thread-safe, bounded Java-owned player state.
 */
public final class ScriptStateStore {

	private final Map<String, Map<String, ScriptStateValue>> namespaces =
			new LinkedHashMap<>();

	public synchronized boolean has(String namespace, String key) {
		validatePublic(namespace, key);
		return getValue(namespace, key) != null;
	}

	public synchronized ScriptStateValue get(String namespace, String key) {
		validatePublic(namespace, key);
		return getValue(namespace, key);
	}

	public synchronized boolean set(String namespace, String key,
			ScriptStateValue value) {
		validatePublic(namespace, key);
		return setValidated(namespace, key, value);
	}

	public synchronized boolean remove(String namespace, String key) {
		validatePublic(namespace, key);
		return removeValidated(namespace, key);
	}

	public synchronized ScriptStateValue getInternal(String namespace, String key) {
		ScriptStateLimits.validateStoredNamespace(namespace);
		ScriptStateLimits.validateStoredKey(namespace, key);
		return getValue(namespace, key);
	}

	public synchronized boolean setInternal(String namespace, String key,
			ScriptStateValue value) {
		ScriptStateLimits.validateStoredNamespace(namespace);
		ScriptStateLimits.validateStoredKey(namespace, key);
		return setValidated(namespace, key, value);
	}

	public synchronized boolean removeInternal(String namespace, String key) {
		ScriptStateLimits.validateStoredNamespace(namespace);
		ScriptStateLimits.validateStoredKey(namespace, key);
		return removeValidated(namespace, key);
	}

	public synchronized ScriptStateSnapshot snapshot() {
		return new ScriptStateSnapshot(namespaces);
	}

	public synchronized void replace(ScriptStateSnapshot snapshot) {
		validateSnapshot(snapshot);
		namespaces.clear();
		for (Map.Entry<String, Map<String, ScriptStateValue>> namespace
				: snapshot.getNamespaces().entrySet()) {
			namespaces.put(namespace.getKey(),
					new LinkedHashMap<>(namespace.getValue()));
		}
	}

	private boolean setValidated(String namespace, String key,
			ScriptStateValue value) {
		if (value == null) {
			throw new ScriptStateException("State value must not be null");
		}
		Map<String, Map<String, ScriptStateValue>> candidate = mutableCopy();
		Map<String, ScriptStateValue> entries = candidate.get(namespace);
		if (entries == null) {
			entries = new LinkedHashMap<>();
			candidate.put(namespace, entries);
		}
		ScriptStateValue previous = entries.put(key, value);
		if (value.equals(previous)) {
			return false;
		}
		ScriptStateSnapshot snapshot = new ScriptStateSnapshot(candidate);
		validateSnapshot(snapshot);
		replaceUnchecked(snapshot);
		return true;
	}

	private boolean removeValidated(String namespace, String key) {
		Map<String, ScriptStateValue> entries = namespaces.get(namespace);
		if (entries == null || entries.remove(key) == null) {
			return false;
		}
		if (entries.isEmpty()) {
			namespaces.remove(namespace);
		}
		return true;
	}

	private ScriptStateValue getValue(String namespace, String key) {
		Map<String, ScriptStateValue> entries = namespaces.get(namespace);
		return entries == null ? null : entries.get(key);
	}

	private void validateSnapshot(ScriptStateSnapshot snapshot) {
		Map<String, Map<String, ScriptStateValue>> values = snapshot.getNamespaces();
		if (values.size() > ScriptStateLimits.MAX_NAMESPACES) {
			throw new ScriptStateException("Too many script-state namespaces");
		}
		if (snapshot.entryCount() > ScriptStateLimits.MAX_TOTAL_ENTRIES) {
			throw new ScriptStateException("Too many script-state entries");
		}
		for (Map.Entry<String, Map<String, ScriptStateValue>> namespace
				: values.entrySet()) {
			ScriptStateLimits.validateStoredNamespace(namespace.getKey());
			if (namespace.getValue().size()
					> ScriptStateLimits.MAX_ENTRIES_PER_NAMESPACE) {
				throw new ScriptStateException("Too many entries in namespace: "
						+ namespace.getKey());
			}
			for (Map.Entry<String, ScriptStateValue> entry
					: namespace.getValue().entrySet()) {
				ScriptStateLimits.validateStoredKey(namespace.getKey(), entry.getKey());
				if (entry.getValue() == null) {
					throw new ScriptStateException("Null script-state value");
				}
				if (entry.getValue().getType() == ScriptStateValue.Type.STRING) {
					ScriptStateLimits.validateString(entry.getValue().asString());
				}
			}
		}
		new ScriptStateCodec().encode(snapshot);
	}

	private Map<String, Map<String, ScriptStateValue>> mutableCopy() {
		Map<String, Map<String, ScriptStateValue>> copy = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, ScriptStateValue>> namespace
				: namespaces.entrySet()) {
			copy.put(namespace.getKey(), new LinkedHashMap<>(namespace.getValue()));
		}
		return copy;
	}

	private void replaceUnchecked(ScriptStateSnapshot snapshot) {
		namespaces.clear();
		for (Map.Entry<String, Map<String, ScriptStateValue>> namespace
				: snapshot.getNamespaces().entrySet()) {
			namespaces.put(namespace.getKey(),
					new LinkedHashMap<>(namespace.getValue()));
		}
	}

	private static void validatePublic(String namespace, String key) {
		ScriptStateLimits.validatePublicNamespace(namespace);
		ScriptStateLimits.validatePublicKey(key);
	}
}
