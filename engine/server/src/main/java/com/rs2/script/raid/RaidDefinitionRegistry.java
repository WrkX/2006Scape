package com.rs2.script.raid;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.rs2.script.ScriptHost;
import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.definition.DefinitionRegistry;
import com.rs2.script.registries.RegistryStore;

/**
 * Typed facade over the common definition envelope for declarative raid
 * definitions keyed by stable string id.
 */
public final class RaidDefinitionRegistry {

	/**
	 * Registers a typed raid definition and returns the previous record for
	 * the same id, or {@code null}.
	 */
	public static DefinitionRecord put(RaidDefinition definition) {
		return DefinitionRegistry.putTyped(DefinitionKind.RAID,
				definition.id(), definition);
	}

	/** Returns the raid definition registered for {@code id} or {@code null}. */
	public static RaidDefinition get(RegistryStore.State state, String id) {
		DefinitionRecord record = DefinitionRegistry.get(state,
				DefinitionKind.RAID, id);
		return record == null || record.isGuestPayload() ? null
				: record.raidPayload();
	}

	public static RaidDefinition get(String id) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, id));
	}

	/**
	 * Returns every typed raid definition of one candidate in deterministic
	 * id order. Guest legacy records are excluded: they carry no descriptor
	 * and are rejected once the compiled loader migrates.
	 */
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

	private RaidDefinitionRegistry() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
