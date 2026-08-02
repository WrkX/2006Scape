package com.rs2.script.registries;

import java.util.Map;
import org.graalvm.polyglot.Value;
import com.rs2.script.ScriptHost;

/**
 * Stores script handlers for NPC interactions keyed by npc id and action
 * string (e.g. "first", "second", "third", "talk", "trade", "attack").
 */
public final class NpcHandlerRegistry {

	/**
	 * Registers {@code handler} for the {@code (npcId, action)} pair.
	 */
	public static Value put(int npcId, String action, Value handler) {
		Map<Integer, Map<String, Value>> handlers = RegistryStore.writable().npcHandlers;
		Map<String, Value> byAction = handlers.get(npcId);
		if (byAction == null) {
			byAction = new java.util.HashMap<String, Value>();
			Map<String, Value> existing = handlers.putIfAbsent(npcId, byAction);
			if (existing != null) {
				byAction = existing;
			}
		}
		return byAction.putIfAbsent(action, handler);
	}

	/**
	 * Returns the handler registered for {@code (npcId, action)} or
	 * {@code null} if none is registered.
	 */
	public static Value get(int npcId, String action) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, npcId, action));
	}

	public static Value get(RegistryStore.State state, int npcId, String action) {
		Map<String, Value> byAction = state.npcHandlers.get(npcId);
		if (byAction == null) {
			return null;
		}
		return byAction.get(action);
	}

	/**
	 * Removes every registered handler. Intended for hot-reload.
	 */
	public static void clear() {
		RegistryStore.writable().npcHandlers.clear();
	}

	private NpcHandlerRegistry() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}

}
