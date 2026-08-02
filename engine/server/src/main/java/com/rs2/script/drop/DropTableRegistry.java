package com.rs2.script.drop;

import com.rs2.script.ScriptHost;
import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.definition.DefinitionRegistry;
import com.rs2.script.registries.RegistryStore;

/**
 * Typed facade over the common definition envelope for named drop tables
 * keyed by stable string id.
 */
public final class DropTableRegistry {

	/**
	 * Registers a typed drop table and returns the previous record for the
	 * same id, or {@code null}.
	 */
	public static DefinitionRecord put(DropTableDefinition definition) {
		return DefinitionRegistry.putTyped(DefinitionKind.DROP_TABLE,
				definition.id(), definition);
	}

	/** Returns the table registered for {@code id} or {@code null}. */
	public static DropTableDefinition get(RegistryStore.State state,
			String id) {
		DefinitionRecord record = DefinitionRegistry.get(state,
				DefinitionKind.DROP_TABLE, id);
		return record == null ? null : record.dropTablePayload();
	}

	public static DropTableDefinition get(String id) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, id));
	}

	private DropTableRegistry() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
