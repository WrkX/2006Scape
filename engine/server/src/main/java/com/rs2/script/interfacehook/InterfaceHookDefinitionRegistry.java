package com.rs2.script.interfacehook;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.rs2.script.ScriptHost;
import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.definition.DefinitionRegistry;
import com.rs2.script.registries.RegistryStore;

/**
 * Typed facade over declarative interface hooks keyed by stable string id,
 * with lookup by cache interface id for dispatch.
 */
public final class InterfaceHookDefinitionRegistry {

	public static DefinitionRecord put(InterfaceHookDefinition definition) {
		return DefinitionRegistry.putTyped(DefinitionKind.INTERFACE_HOOK,
				definition.id(), definition);
	}

	public static InterfaceHookDefinition get(RegistryStore.State state,
			String id) {
		DefinitionRecord record = DefinitionRegistry.get(state,
				DefinitionKind.INTERFACE_HOOK, id);
		return record == null ? null
				: (InterfaceHookDefinition) record.typedPayload();
	}

	public static InterfaceHookDefinition get(String id) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, id));
	}

	public static InterfaceHookDefinition getByInterfaceId(
			RegistryStore.State state, int interfaceId) {
		for (InterfaceHookDefinition definition : all(state).values()) {
			if (definition.interfaceId() == interfaceId) {
				return definition;
			}
		}
		return null;
	}

	public static InterfaceHookDefinition getByInterfaceId(int interfaceId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getByInterfaceId(state, interfaceId));
	}

	public static Map<String, InterfaceHookDefinition> all(
			RegistryStore.State state) {
		Map<String, InterfaceHookDefinition> definitions =
				new LinkedHashMap<String, InterfaceHookDefinition>();
		for (Map.Entry<String, DefinitionRecord> entry
				: DefinitionRegistry.all(state,
						DefinitionKind.INTERFACE_HOOK).entrySet()) {
			definitions.put(entry.getKey(),
					(InterfaceHookDefinition) entry.getValue()
							.typedPayload());
		}
		return Collections.unmodifiableMap(definitions);
	}

	private InterfaceHookDefinitionRegistry() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
