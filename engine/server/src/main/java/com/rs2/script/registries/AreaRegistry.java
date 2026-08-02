package com.rs2.script.registries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.graalvm.polyglot.Value;
import com.rs2.script.ScriptHost;
import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.definition.DefinitionRegistry;

/**
 * Typed facade over the common definition envelope for area definitions
 * keyed by stable string id.
 */
public final class AreaRegistry {

	/**
	 * Registers {@code definition} for the area named {@code name}. Returns
	 * the previous record for the same id, or {@code null}.
	 */
	public static DefinitionRecord put(String name, Value definition) {
		return DefinitionRegistry.put(DefinitionKind.AREA, name, definition);
	}

	/**
	 * Returns the definition registered for the area named {@code name} or
	 * {@code null}.
	 */
	public static Value get(String name) {
		DefinitionRecord record = DefinitionRegistry.get(DefinitionKind.AREA,
				name);
		return record == null || !record.isGuestPayload() ? null
				: record.guestPayload();
	}

	/**
	 * Returns every registered area definition in key order. The returned
	 * list is immutable.
	 */
	public static List<Value> all() {
		return ScriptHost.getInstance().readActiveRegistry(state -> {
			List<Value> definitions = new ArrayList<>();
			for (DefinitionRecord record
					: DefinitionRegistry.all(state, DefinitionKind.AREA)
							.values()) {
				definitions.add(record.guestPayload());
			}
			return Collections.unmodifiableList(definitions);
		});
	}

	/**
	 * Removes every registered area. Intended for hot-reload.
	 */
	public static void clear() {
		java.util.Iterator<com.rs2.script.definition.DefinitionKey> keys =
				RegistryStore.writable().definitions.keySet().iterator();
		while (keys.hasNext()) {
			if (keys.next().kind() == DefinitionKind.AREA) {
				keys.remove();
			}
		}
	}

	private AreaRegistry() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}

}
