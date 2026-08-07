package com.rs2.script.processing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.rs2.script.ScriptHost;
import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.definition.DefinitionRegistry;
import com.rs2.script.registries.RegistryStore;

/**
 * Typed registry facade for processing skill definitions.
 */
public final class ProcessingSkillRegistry {

	public static DefinitionRecord put(ProcessingSkillDefinition definition) {
		return DefinitionRegistry.putTyped(DefinitionKind.PROCESSING,
				definition.id(), definition);
	}

	public static ProcessingSkillDefinition get(String id) {
		DefinitionRecord record = DefinitionRegistry.get(
				DefinitionKind.PROCESSING, id);
		return record == null ? null
				: (ProcessingSkillDefinition) record.typedPayload();
	}

	public static ProcessingSkillDefinition get(RegistryStore.State state,
			String id) {
		DefinitionRecord record = DefinitionRegistry.get(state,
				DefinitionKind.PROCESSING, id);
		return record == null ? null
				: (ProcessingSkillDefinition) record.typedPayload();
	}

	public static Map<String, ProcessingSkillDefinition> all(
			RegistryStore.State state) {
		Map<String, DefinitionRecord> records = DefinitionRegistry.all(state,
				DefinitionKind.PROCESSING);
		Map<String, ProcessingSkillDefinition> definitions =
				new LinkedHashMap<String, ProcessingSkillDefinition>();
		for (Map.Entry<String, DefinitionRecord> entry : records.entrySet()) {
			definitions.put(entry.getKey(),
					(ProcessingSkillDefinition) entry.getValue()
							.typedPayload());
		}
		return Collections.unmodifiableMap(definitions);
	}

	public static ProcessingSkillDefinition getActive(String id) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, id));
	}

	private ProcessingSkillRegistry() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
