package com.rs2.script.mob;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.rs2.script.ScriptHost;
import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.definition.DefinitionRegistry;
import com.rs2.script.registries.RegistryStore;

/**
 * Typed facade over declarative world mob definitions keyed by numeric
 * NPC id for combat ownership.
 */
public final class MobDefinitionRegistry {

	/**
	 * Registers a typed mob definition and returns the previous record for
	 * the same npc id, or {@code null}.
	 */
	public static DefinitionRecord put(MobDefinition definition) {
		return DefinitionRegistry.putTyped(DefinitionKind.MOB,
				String.valueOf(definition.npcId()), definition);
	}

	/** Returns the mob definition registered for {@code npcId} or {@code null}. */
	public static MobDefinition get(RegistryStore.State state, int npcId) {
		DefinitionRecord record = DefinitionRegistry.get(state,
				DefinitionKind.MOB, String.valueOf(npcId));
		return record == null ? null
				: (MobDefinition) record.typedPayload();
	}

	public static MobDefinition get(int npcId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, npcId));
	}

	public static MobDefinition getById(RegistryStore.State state, String id) {
		if (id == null) {
			return null;
		}
		for (MobDefinition definition : all(state).values()) {
			if (id.equals(definition.id())) {
				return definition;
			}
		}
		return null;
	}

	public static MobDefinition getById(String id) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getById(state, id));
	}

	/**
	 * Returns every typed mob definition of one candidate in deterministic
	 * npc-id order.
	 */
	public static Map<Integer, MobDefinition> all(RegistryStore.State state) {
		Map<Integer, MobDefinition> definitions =
				new LinkedHashMap<Integer, MobDefinition>();
		for (Map.Entry<String, DefinitionRecord> entry
				: DefinitionRegistry.all(state, DefinitionKind.MOB)
						.entrySet()) {
			definitions.put(Integer.valueOf(entry.getKey()),
					(MobDefinition) entry.getValue().typedPayload());
		}
		return Collections.unmodifiableMap(definitions);
	}

	private MobDefinitionRegistry() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
