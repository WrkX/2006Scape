package com.rs2.script.reward;

import com.rs2.script.ScriptHost;
import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.definition.DefinitionRegistry;
import com.rs2.script.registries.RegistryStore;

/**
 * Typed facade over the common definition envelope for named rewards keyed
 * by stable string id.
 */
public final class RewardRegistry {

	/**
	 * Registers a typed reward and returns the previous record for the same
	 * id, or {@code null}.
	 */
	public static DefinitionRecord put(RewardDefinition definition) {
		return DefinitionRegistry.putTyped(DefinitionKind.REWARD,
				definition.id(), definition);
	}

	/** Returns the reward registered for {@code id} or {@code null}. */
	public static RewardDefinition get(RegistryStore.State state, String id) {
		DefinitionRecord record = DefinitionRegistry.get(state,
				DefinitionKind.REWARD, id);
		return record == null ? null : record.rewardPayload();
	}

	public static RewardDefinition get(String id) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, id));
	}

	private RewardRegistry() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
