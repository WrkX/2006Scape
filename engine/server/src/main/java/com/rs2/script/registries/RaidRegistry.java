package com.rs2.script.registries;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.rs2.script.ScriptHost;
import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.definition.DefinitionRegistry;
import com.rs2.script.raid.RaidDefinition;

/**
 * Typed facade over the common definition envelope for declarative raid
 * definitions keyed by stable string id.
 */
public final class RaidRegistry {

	/**
	 * Registers a typed raid definition and returns the previous record for
	 * the same id, or {@code null}.
	 */
	public static DefinitionRecord put(RaidDefinition definition) {
		return DefinitionRegistry.putTyped(DefinitionKind.RAID,
				definition.id(), definition);
	}

	/** Returns the raid definition registered for {@code id} or {@code null}. */
	public static RaidDefinition get(String id) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, id));
	}

	public static RaidDefinition get(RegistryStore.State state, String id) {
		DefinitionRecord record = DefinitionRegistry.get(state,
				DefinitionKind.RAID, id);
		return record == null || record.isGuestPayload() ? null
				: record.raidPayload();
	}

	/** Returns every typed raid definition in deterministic id order. */
	public static Map<String, RaidDefinition> all(
			RegistryStore.State state) {
		Map<String, RaidDefinition> definitions =
				new LinkedHashMap<String, RaidDefinition>();
		for (Map.Entry<String, DefinitionRecord> entry
				: DefinitionRegistry.all(state, DefinitionKind.RAID)
						.entrySet()) {
			DefinitionRecord record = entry.getValue();
			if (!record.isGuestPayload()) {
				definitions.put(entry.getKey(), record.raidPayload());
			}
		}
		return Collections.unmodifiableMap(definitions);
	}

	private RaidRegistry() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}

}
