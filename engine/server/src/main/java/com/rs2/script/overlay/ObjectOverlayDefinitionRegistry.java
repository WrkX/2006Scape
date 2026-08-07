package com.rs2.script.overlay;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.rs2.script.ScriptHost;
import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.definition.DefinitionRegistry;
import com.rs2.script.registries.RegistryStore;

/**
 * Typed facade over declarative object overlays keyed by numeric object id.
 */
public final class ObjectOverlayDefinitionRegistry {

	public static DefinitionRecord put(ObjectOverlayDefinition definition) {
		return DefinitionRegistry.putTyped(DefinitionKind.OBJECT_OVERLAY,
				String.valueOf(definition.objectId()), definition);
	}

	public static ObjectOverlayDefinition get(RegistryStore.State state,
			int objectId) {
		DefinitionRecord record = DefinitionRegistry.get(state,
				DefinitionKind.OBJECT_OVERLAY, String.valueOf(objectId));
		return record == null ? null
				: (ObjectOverlayDefinition) record.typedPayload();
	}

	public static ObjectOverlayDefinition get(int objectId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, objectId));
	}

	public static Map<Integer, ObjectOverlayDefinition> all(
			RegistryStore.State state) {
		Map<Integer, ObjectOverlayDefinition> definitions =
				new LinkedHashMap<Integer, ObjectOverlayDefinition>();
		for (Map.Entry<String, DefinitionRecord> entry
				: DefinitionRegistry.all(state, DefinitionKind.OBJECT_OVERLAY)
						.entrySet()) {
			definitions.put(Integer.valueOf(entry.getKey()),
					(ObjectOverlayDefinition) entry.getValue().typedPayload());
		}
		return Collections.unmodifiableMap(definitions);
	}

	private ObjectOverlayDefinitionRegistry() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
