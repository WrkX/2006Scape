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
 * Typed facade over declarative NPC overlays keyed by numeric NPC id.
 */
public final class NpcOverlayDefinitionRegistry {

	public static DefinitionRecord put(NpcOverlayDefinition definition) {
		return DefinitionRegistry.putTyped(DefinitionKind.NPC_OVERLAY,
				String.valueOf(definition.npcId()), definition);
	}

	public static NpcOverlayDefinition get(RegistryStore.State state,
			int npcId) {
		DefinitionRecord record = DefinitionRegistry.get(state,
				DefinitionKind.NPC_OVERLAY, String.valueOf(npcId));
		return record == null ? null
				: (NpcOverlayDefinition) record.typedPayload();
	}

	public static NpcOverlayDefinition get(int npcId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, npcId));
	}

	public static Map<Integer, NpcOverlayDefinition> all(
			RegistryStore.State state) {
		Map<Integer, NpcOverlayDefinition> definitions =
				new LinkedHashMap<Integer, NpcOverlayDefinition>();
		for (Map.Entry<String, DefinitionRecord> entry
				: DefinitionRegistry.all(state, DefinitionKind.NPC_OVERLAY)
						.entrySet()) {
			definitions.put(Integer.valueOf(entry.getKey()),
					(NpcOverlayDefinition) entry.getValue().typedPayload());
		}
		return Collections.unmodifiableMap(definitions);
	}

	private NpcOverlayDefinitionRegistry() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
