package com.rs2.script.registries;

import org.graalvm.polyglot.Value;
import com.rs2.script.ScriptHost;
import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.definition.DefinitionRegistry;

/**
 * Typed facade over the common definition envelope for boss definitions
 * keyed by numeric NPC id.
 */
public final class BossRegistry {

	/**
	 * Registers {@code definition} for {@code bossId}. Returns the previous
	 * record for the same id, or {@code null}.
	 */
	public static DefinitionRecord put(int bossId, Value definition) {
		return DefinitionRegistry.put(DefinitionKind.BOSS,
				String.valueOf(bossId), definition);
	}

	/**
	 * Returns the definition registered for {@code bossId} or {@code null}.
	 */
	public static Value get(int bossId) {
		DefinitionRecord record = DefinitionRegistry.get(DefinitionKind.BOSS,
				String.valueOf(bossId));
		return record == null || !record.isGuestPayload() ? null
				: record.guestPayload();
	}

	/**
	 * Removes every registered boss. Intended for hot-reload.
	 */
	public static void clear() {
		java.util.Iterator<com.rs2.script.definition.DefinitionKey> keys =
				RegistryStore.writable().definitions.keySet().iterator();
		while (keys.hasNext()) {
			if (keys.next().kind() == DefinitionKind.BOSS) {
				keys.remove();
			}
		}
	}

	private BossRegistry() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}

}
