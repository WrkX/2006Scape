package com.rs2.script.registries;

import java.util.Map;
import org.graalvm.polyglot.Value;
import com.rs2.script.ScriptHost;

/**
 * Stores script handlers for object interactions keyed by object id and
 * action string (e.g. "first", "second", "third").
 */
public final class ObjectHandlerRegistry {

	/**
	 * Registers {@code handler} for the {@code (objectId, action)} pair.
	 */
	public static Value put(int objectId, String action, Value handler) {
		Map<Integer, Map<String, Value>> handlers = RegistryStore.writable().objectHandlers;
		Map<String, Value> byAction = handlers.get(objectId);
		if (byAction == null) {
			byAction = new java.util.HashMap<String, Value>();
			Map<String, Value> existing = handlers.putIfAbsent(objectId, byAction);
			if (existing != null) {
				byAction = existing;
			}
		}
		return byAction.putIfAbsent(action, handler);
	}

	/**
	 * Returns the handler registered for {@code (objectId, action)} or
	 * {@code null} if none is registered.
	 */
	public static Value get(int objectId, String action) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, objectId, action));
	}

	public static Value get(RegistryStore.State state, int objectId, String action) {
		Map<String, Value> byAction = state.objectHandlers.get(objectId);
		if (byAction == null) {
			return null;
		}
		return byAction.get(action);
	}

	/**
	 * Removes every registered handler. Intended for hot-reload.
	 */
	public static void clear() {
		RegistryStore.writable().objectHandlers.clear();
	}

	private ObjectHandlerRegistry() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}

}
