package com.rs2.script.shop;

import com.rs2.script.ScriptHost;
import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.definition.DefinitionRegistry;
import com.rs2.script.registries.RegistryStore;

/**
 * Typed facade over the common definition envelope for scripted shops
 * keyed by stable string id.
 */
public final class ShopDefinitionRegistry {

	/**
	 * Registers a typed shop and returns the previous record for the same
	 * id, or {@code null}.
	 */
	public static DefinitionRecord put(ShopDefinition definition) {
		return DefinitionRegistry.putTyped(DefinitionKind.SHOP,
				definition.id(), definition);
	}

	/** Returns the shop registered for {@code id} or {@code null}. */
	public static ShopDefinition get(RegistryStore.State state, String id) {
		DefinitionRecord record = DefinitionRegistry.get(state,
				DefinitionKind.SHOP, id);
		return record == null ? null : record.shopPayload();
	}

	public static ShopDefinition get(String id) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, id));
	}

	private ShopDefinitionRegistry() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
