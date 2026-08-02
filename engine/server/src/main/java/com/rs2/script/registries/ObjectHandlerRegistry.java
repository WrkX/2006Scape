package com.rs2.script.registries;

import org.graalvm.polyglot.Value;
import com.rs2.script.ScriptHost;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.ExecutableRouteRecord;
import com.rs2.script.route.RouteKind;
import com.rs2.script.route.RouteRegistry;

/**
 * Typed facade over the unified route registry for object interaction
 * handlers keyed by object id and ordinal action ("first" .. "fourth").
 */
public final class ObjectHandlerRegistry {

	/**
	 * Registers {@code handler} for the {@code (objectId, action)} pair.
	 */
	public static void put(int objectId, String action, Value handler) {
		RouteRegistry.put(ExecutableRouteKey.object(objectId, action), handler);
	}

	/**
	 * Returns the guest handler registered for {@code (objectId, action)} or
	 * {@code null} when no guest route exists.
	 */
	public static Value get(int objectId, String action) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, objectId, action));
	}

	public static Value get(RegistryStore.State state, int objectId,
			String action) {
		ExecutableRouteRecord record = RouteRegistry.get(state,
				ExecutableRouteKey.object(objectId, action));
		return record == null || !record.isGuest() ? null
				: record.guestInvoker();
	}

	/** Exact route record of the object/action key, or {@code null}. */
	public static ExecutableRouteRecord getRecord(RegistryStore.State state,
			int objectId, String action) {
		return RouteRegistry.get(state,
				ExecutableRouteKey.object(objectId, action));
	}

	/**
	 * Removes every registered handler. Intended for hot-reload.
	 */
	public static void clear() {
		RouteRegistry.clear(RegistryStore.writable(), RouteKind.OBJECT);
	}

	private ObjectHandlerRegistry() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}

}
