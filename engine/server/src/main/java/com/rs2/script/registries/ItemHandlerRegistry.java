package com.rs2.script.registries;

import java.util.Map;
import org.graalvm.polyglot.Value;
import com.rs2.script.ScriptHost;

/**
 * Exact-ID item interaction handlers owned by the active script context.
 */
public final class ItemHandlerRegistry {

	public static Value putItem(int itemId, String action, Value handler) {
		Map<Integer, Map<String, Value>> handlers = RegistryStore.writable().itemHandlers;
		Map<String, Value> byAction = handlers.get(itemId);
		if (byAction == null) {
			byAction = new java.util.HashMap<String, Value>();
			Map<String, Value> existing = handlers.putIfAbsent(itemId, byAction);
			if (existing != null) {
				byAction = existing;
			}
		}
		return byAction.putIfAbsent(action, handler);
	}

	public static Value getItem(int itemId, String action) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getItem(state, itemId, action));
	}

	public static Value getItem(RegistryStore.State state, int itemId, String action) {
		Map<String, Value> byAction = state.itemHandlers.get(itemId);
		return byAction == null ? null : byAction.get(action);
	}

	public static Value putItemOnItem(int firstItemId, int secondItemId, Value handler) {
		return RegistryStore.writable().itemOnItemHandlers.putIfAbsent(
				symmetricKey(firstItemId, secondItemId), handler);
	}

	public static Value getItemOnItem(int firstItemId, int secondItemId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getItemOnItem(state, firstItemId, secondItemId));
	}

	public static Value getItemOnItem(RegistryStore.State state,
			int firstItemId, int secondItemId) {
		return state.itemOnItemHandlers.get(
				symmetricKey(firstItemId, secondItemId));
	}

	public static Value putItemOnObject(int itemId, int objectId, Value handler) {
		return RegistryStore.writable().itemOnObjectHandlers.putIfAbsent(
				orderedKey(itemId, objectId), handler);
	}

	public static Value getItemOnObject(int itemId, int objectId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getItemOnObject(state, itemId, objectId));
	}

	public static Value getItemOnObject(RegistryStore.State state,
			int itemId, int objectId) {
		return state.itemOnObjectHandlers.get(orderedKey(itemId, objectId));
	}

	public static Value putItemOnNpc(int itemId, int npcId, Value handler) {
		return RegistryStore.writable().itemOnNpcHandlers.putIfAbsent(
				orderedKey(itemId, npcId), handler);
	}

	public static Value getItemOnNpc(int itemId, int npcId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getItemOnNpc(state, itemId, npcId));
	}

	public static Value getItemOnNpc(RegistryStore.State state,
			int itemId, int npcId) {
		return state.itemOnNpcHandlers.get(orderedKey(itemId, npcId));
	}

	public static void clear() {
		RegistryStore.State state = RegistryStore.writable();
		state.itemHandlers.clear();
		state.itemOnItemHandlers.clear();
		state.itemOnObjectHandlers.clear();
		state.itemOnNpcHandlers.clear();
	}

	private static long symmetricKey(int first, int second) {
		return first <= second ? orderedKey(first, second) : orderedKey(second, first);
	}

	private static long orderedKey(int first, int second) {
		return ((long) first << 32) | (second & 0xffffffffL);
	}

	private ItemHandlerRegistry() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}
}
