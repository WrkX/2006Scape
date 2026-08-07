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
 * Typed facade over declarative item overlays keyed by numeric item id.
 */
public final class ItemOverlayDefinitionRegistry {

	public static DefinitionRecord put(ItemOverlayDefinition definition) {
		return DefinitionRegistry.putTyped(DefinitionKind.ITEM_OVERLAY,
				String.valueOf(definition.itemId()), definition);
	}

	public static ItemOverlayDefinition get(RegistryStore.State state,
			int itemId) {
		DefinitionRecord record = DefinitionRegistry.get(state,
				DefinitionKind.ITEM_OVERLAY, String.valueOf(itemId));
		return record == null ? null
				: (ItemOverlayDefinition) record.typedPayload();
	}

	public static ItemOverlayDefinition get(int itemId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, itemId));
	}

	public static Map<Integer, ItemOverlayDefinition> all(
			RegistryStore.State state) {
		Map<Integer, ItemOverlayDefinition> definitions =
				new LinkedHashMap<Integer, ItemOverlayDefinition>();
		for (Map.Entry<String, DefinitionRecord> entry
				: DefinitionRegistry.all(state, DefinitionKind.ITEM_OVERLAY)
						.entrySet()) {
			definitions.put(Integer.valueOf(entry.getKey()),
					(ItemOverlayDefinition) entry.getValue().typedPayload());
		}
		return Collections.unmodifiableMap(definitions);
	}

	private ItemOverlayDefinitionRegistry() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
