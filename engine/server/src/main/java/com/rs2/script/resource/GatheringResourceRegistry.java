package com.rs2.script.resource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.rs2.script.ScriptHost;
import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.definition.DefinitionRegistry;
import com.rs2.script.registries.RegistryStore;

/**
 * Typed registry facade for gathering resource definitions.
 *
 * <p>Every definition is stored as one Java-owned typed record in the shared
 * candidate-wide definition envelope. Duplicate ids reject the candidate
 * through the common duplicate-detection path.
 */
public final class GatheringResourceRegistry {

	/**
	 * Registers a Java-owned resource descriptor and returns the previous
	 * record for the same id, or {@code null}.
	 */
	public static DefinitionRecord put(GatheringResourceDefinition definition) {
		return DefinitionRegistry.putTyped(DefinitionKind.RESOURCE,
				definition.id(), definition);
	}

	/** Returns the active resource definition, or {@code null}. */
	public static GatheringResourceDefinition get(String id) {
		DefinitionRecord record = DefinitionRegistry.get(
				DefinitionKind.RESOURCE, id);
		return record == null ? null
				: (GatheringResourceDefinition) record.typedPayload();
	}

	public static GatheringResourceDefinition get(RegistryStore.State state,
			String id) {
		DefinitionRecord record = DefinitionRegistry.get(state,
				DefinitionKind.RESOURCE, id);
		return record == null ? null
				: (GatheringResourceDefinition) record.typedPayload();
	}

	/** Returns every resource definition in deterministic id order. */
	public static Map<String, GatheringResourceDefinition> all(
			RegistryStore.State state) {
		Map<String, DefinitionRecord> records = DefinitionRegistry.all(state,
				DefinitionKind.RESOURCE);
		Map<String, GatheringResourceDefinition> definitions =
				new LinkedHashMap<String, GatheringResourceDefinition>();
		for (Map.Entry<String, DefinitionRecord> entry : records.entrySet()) {
			definitions.put(entry.getKey(),
					(GatheringResourceDefinition) entry.getValue()
							.typedPayload());
		}
		return Collections.unmodifiableMap(definitions);
	}

	/** Active-registry lookup of one resource definition. */
	public static GatheringResourceDefinition getActive(String id) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, id));
	}

	private GatheringResourceRegistry() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}

}
