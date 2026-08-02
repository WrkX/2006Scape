package com.rs2.script.area;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.rs2.script.ScriptHost;
import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.definition.DefinitionRegistry;
import com.rs2.script.registries.RegistryStore;

/**
 * Typed facade over the common definition envelope for declarative areas
 * keyed by stable string id.
 */
public final class AreaDefinitionRegistry {

	/**
	 * Registers a typed area and returns the previous record for the same
	 * id, or {@code null}.
	 */
	public static DefinitionRecord put(AreaDefinition definition) {
		return DefinitionRegistry.putTyped(DefinitionKind.AREA,
				definition.id(), definition);
	}

	/** Returns the area registered for {@code id} or {@code null}. */
	public static AreaDefinition get(RegistryStore.State state, String id) {
		DefinitionRecord record = DefinitionRegistry.get(state,
				DefinitionKind.AREA, id);
		return record == null ? null : record.areaPayload();
	}

	public static AreaDefinition get(String id) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, id));
	}

	/** Every typed area record in deterministic key order. */
	public static Map<String, AreaDefinition> all(RegistryStore.State state) {
		Map<String, AreaDefinition> areas =
				new LinkedHashMap<String, AreaDefinition>();
		for (Map.Entry<String, DefinitionRecord> entry
				: DefinitionRegistry.all(state, DefinitionKind.AREA)
						.entrySet()) {
			DefinitionRecord record = entry.getValue();
			if (!record.isGuestPayload()) {
				areas.put(entry.getKey(), record.areaPayload());
			}
		}
		return Collections.unmodifiableMap(areas);
	}

	private AreaDefinitionRegistry() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
