package com.rs2.script.definition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.polyglot.Value;

import com.rs2.script.ScriptHost;
import com.rs2.script.quest.QuestDefinition;
import com.rs2.script.registries.RegistryStore;

/**
 * Candidate-wide definition registry.
 *
 * <p>All definition families share one immutable envelope map so diagnostics,
 * duplicate detection, and the activation transaction see one candidate-owned
 * snapshot. Family-specific registries (boss, raid, area, quest) are typed
 * facades over this registry.
 */
public final class DefinitionRegistry {

	/**
	 * Registers a guest definition and returns the previous record for the
	 * same exact key, or {@code null}.
	 */
	public static DefinitionRecord put(DefinitionKind kind, String key,
			Value guestPayload) {
		RegistryStore.State candidate = RegistryStore.writable();
		DefinitionRecord record = DefinitionRecord.guest(kind, key,
				ModuleScope.currentSchemaVersion(), ModuleScope.currentSource(),
				guestPayload);
		return candidate.definitions.putIfAbsent(
				DefinitionKey.of(kind, key), record);
	}

	/**
	 * Registers a Java-owned typed descriptor and returns the previous record
	 * for the same kind/key, or {@code null}.
	 */
	public static DefinitionRecord putTyped(DefinitionKind kind, String key,
			Object typedPayload) {
		RegistryStore.State candidate = RegistryStore.writable();
		DefinitionRecord record = DefinitionRecord.typed(kind, key,
				ModuleScope.currentSchemaVersion(), ModuleScope.currentSource(),
				typedPayload);
		return candidate.definitions.putIfAbsent(
				DefinitionKey.of(kind, key), record);
	}

	/**
	 * Registers a Java-owned quest descriptor and returns the previous record
	 * for the same quest id, or {@code null}.
	 */
	public static DefinitionRecord putQuest(QuestDefinition definition) {
		return putTyped(DefinitionKind.QUEST, definition.getId(), definition);
	}

	public static DefinitionRecord get(RegistryStore.State state,
			DefinitionKind kind, String key) {
		return state.definitions.get(DefinitionKey.of(kind, key));
	}

	public static DefinitionRecord get(DefinitionKind kind, String key) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, kind, key));
	}

	/**
	 * Returns every record of one kind in deterministic key order. The result
	 * is an immutable copy of the active snapshot.
	 */
	public static Map<String, DefinitionRecord> all(
			RegistryStore.State state, DefinitionKind kind) {
		Map<String, DefinitionRecord> records =
				new LinkedHashMap<String, DefinitionRecord>();
		for (Map.Entry<DefinitionKey, DefinitionRecord> entry
				: state.definitions.entrySet()) {
			if (entry.getKey().kind() == kind) {
				records.put(entry.getKey().key(), entry.getValue());
			}
		}
		return Collections.unmodifiableMap(records);
	}

	private DefinitionRegistry() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
