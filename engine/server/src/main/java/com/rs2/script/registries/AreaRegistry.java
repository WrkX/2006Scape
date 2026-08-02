package com.rs2.script.registries;

import java.util.Collection;
import java.util.Collections;
import org.graalvm.polyglot.Value;
import com.rs2.script.ScriptHost;

/**
 * Stores scripted area definitions keyed by area name.
 */
public final class AreaRegistry {

	/**
	 * Registers {@code definition} for the area named {@code name}.
	 */
	public static Value put(String name, Value definition) {
		return RegistryStore.writable().areas.putIfAbsent(name, definition);
	}

	/**
	 * Returns the definition registered for the area named {@code name} or
	 * {@code null}.
	 */
	public static Value get(String name) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> state.areas.get(name));
	}

	/**
	 * Returns every registered area definition. The returned collection is
	 * an unmodifiable live view of the registry.
	 */
	public static Collection<Value> all() {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> Collections.unmodifiableCollection(state.areas.values()));
	}

	/**
	 * Removes every registered area. Intended for hot-reload.
	 */
	public static void clear() {
		RegistryStore.writable().areas.clear();
	}

	private AreaRegistry() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}

}
