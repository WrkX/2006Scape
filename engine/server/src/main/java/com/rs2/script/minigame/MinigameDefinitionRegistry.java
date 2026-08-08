package com.rs2.script.minigame;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.rs2.script.ScriptHost;
import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.definition.DefinitionRegistry;
import com.rs2.script.registries.RegistryStore;

/** Typed facade over declarative minigame definitions keyed by stable id. */
public final class MinigameDefinitionRegistry {

	public static DefinitionRecord put(MinigameDefinition definition) {
		return DefinitionRegistry.putTyped(DefinitionKind.MINIGAME,
				definition.id(), definition);
	}

	public static MinigameDefinition get(RegistryStore.State state, String id) {
		DefinitionRecord record = DefinitionRegistry.get(state,
				DefinitionKind.MINIGAME, id);
		return record == null ? null
				: (MinigameDefinition) record.typedPayload();
	}

	public static MinigameDefinition get(String id) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, id));
	}

	public static Map<String, MinigameDefinition> all(RegistryStore.State state) {
		Map<String, MinigameDefinition> definitions =
				new LinkedHashMap<String, MinigameDefinition>();
		for (Map.Entry<String, DefinitionRecord> entry
				: DefinitionRegistry.all(state, DefinitionKind.MINIGAME)
						.entrySet()) {
			definitions.put(entry.getKey(),
					(MinigameDefinition) entry.getValue().typedPayload());
		}
		return Collections.unmodifiableMap(definitions);
	}

	private MinigameDefinitionRegistry() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
