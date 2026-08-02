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
 * Typed facade over the common definition envelope for raid definitions
 * keyed by stable string id.
 */
public final class RaidRegistry {

	/**
	 * Registers {@code definition} for the raid named {@code name}. Returns
	 * the previous record for the same id, or {@code null}.
	 */
	public static DefinitionRecord put(String name, Value definition) {
		return DefinitionRegistry.put(DefinitionKind.RAID, name, definition);
	}

	/**
	 * Returns the definition registered for the raid named {@code name} or
	 * {@code null}.
	 */
	public static Value get(String name) {
		DefinitionRecord record = DefinitionRegistry.get(DefinitionKind.RAID,
				name);
		return record == null || !record.isGuestPayload() ? null
				: record.guestPayload();
	}

	/** Returns every registered raid definition in key order. */
	public static List<Value> all() {
		return ScriptHost.getInstance().readActiveRegistry(state -> {
			List<Value> definitions = new ArrayList<>();
			for (DefinitionRecord record
					: DefinitionRegistry.all(state, DefinitionKind.RAID)
							.values()) {
				definitions.add(record.guestPayload());
			}
			return Collections.unmodifiableList(definitions);
		});
	}

	/**
	 * Removes every registered raid. Intended for hot-reload.
	 */
	public static void clear() {
		java.util.Iterator<com.rs2.script.definition.DefinitionKey> keys =
				RegistryStore.writable().definitions.keySet().iterator();
		while (keys.hasNext()) {
			if (keys.next().kind() == DefinitionKind.RAID) {
				keys.remove();
			}
		}
	}

	private RaidRegistry() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}

}
