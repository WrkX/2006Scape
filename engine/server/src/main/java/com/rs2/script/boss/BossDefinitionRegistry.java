package com.rs2.script.boss;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.rs2.script.ScriptHost;
import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.definition.DefinitionRegistry;
import com.rs2.script.registries.RegistryStore;

/**
 * Typed facade over the common definition envelope for declarative boss
 * definitions keyed by the numeric npc id that owns combat identity.
 */
public final class BossDefinitionRegistry {

	/**
	 * Registers a typed boss definition and returns the previous record for
	 * the same npc id, or {@code null}.
	 */
	public static DefinitionRecord put(BossDefinition definition) {
		return DefinitionRegistry.putTyped(DefinitionKind.BOSS,
				String.valueOf(definition.npcId()), definition);
	}

	/** Returns the boss definition registered for {@code npcId} or {@code null}. */
	public static BossDefinition get(RegistryStore.State state, int npcId) {
		DefinitionRecord record = DefinitionRegistry.get(state,
				DefinitionKind.BOSS, String.valueOf(npcId));
		return record == null ? null : record.bossPayload();
	}

	public static BossDefinition get(int npcId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, npcId));
	}

	/**
	 * Returns every typed boss definition of one candidate in deterministic
	 * npc-id order. Guest legacy records are excluded: they carry no
	 * descriptor and are rejected once the compiled loader migrates.
	 */
	public static Map<Integer, BossDefinition> all(RegistryStore.State state) {
		Map<Integer, BossDefinition> definitions =
				new LinkedHashMap<Integer, BossDefinition>();
		for (Map.Entry<String, DefinitionRecord> entry
				: DefinitionRegistry.all(state, DefinitionKind.BOSS)
						.entrySet()) {
			DefinitionRecord record = entry.getValue();
			if (!record.isGuestPayload()) {
				definitions.put(Integer.valueOf(entry.getKey()),
						record.bossPayload());
			}
		}
		return Collections.unmodifiableMap(definitions);
	}

	private BossDefinitionRegistry() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
