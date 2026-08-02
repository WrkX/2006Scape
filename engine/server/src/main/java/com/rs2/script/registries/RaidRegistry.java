package com.rs2.script.registries;

import org.graalvm.polyglot.Value;
import com.rs2.script.ScriptHost;

/**
 * Stores scripted raid definitions keyed by raid name.
 */
public final class RaidRegistry {

	/**
	 * Registers {@code definition} for the raid named {@code name}.
	 */
	public static Value put(String name, Value definition) {
		return RegistryStore.writable().raids.putIfAbsent(name, definition);
	}

	/**
	 * Returns the definition registered for the raid named {@code name} or
	 * {@code null}.
	 */
	public static Value get(String name) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> state.raids.get(name));
	}

	/**
	 * Removes every registered raid. Intended for hot-reload.
	 */
	public static void clear() {
		RegistryStore.writable().raids.clear();
	}

	private RaidRegistry() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}

}
