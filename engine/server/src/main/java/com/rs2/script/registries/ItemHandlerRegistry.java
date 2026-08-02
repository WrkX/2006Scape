package com.rs2.script.registries;

import org.graalvm.polyglot.Value;
import com.rs2.script.ScriptHost;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.ExecutableRouteRecord;
import com.rs2.script.route.RouteKind;
import com.rs2.script.route.RouteRegistry;

/**
 * Typed facade over the unified route registry for exact-ID item
 * interaction handlers.
 */
public final class ItemHandlerRegistry {

	public static void putItem(int itemId, String action, Value handler) {
		RouteRegistry.put(ExecutableRouteKey.item(itemId, action), handler);
	}

	public static Value getItem(int itemId, String action) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getItem(state, itemId, action));
	}

	public static Value getItem(RegistryStore.State state, int itemId,
			String action) {
		ExecutableRouteRecord record = RouteRegistry.get(state,
				ExecutableRouteKey.item(itemId, action));
		return record == null || !record.isGuest() ? null
				: record.guestInvoker();
	}

	public static ExecutableRouteRecord getItemRecord(RegistryStore.State state,
			int itemId, String action) {
		return RouteRegistry.get(state,
				ExecutableRouteKey.item(itemId, action));
	}

	public static void putItemOnItem(int firstItemId, int secondItemId,
			Value handler) {
		RouteRegistry.put(ExecutableRouteKey.itemOnItem(firstItemId,
				secondItemId), handler);
	}

	public static Value getItemOnItem(int firstItemId, int secondItemId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getItemOnItem(state, firstItemId, secondItemId));
	}

	public static Value getItemOnItem(RegistryStore.State state,
			int firstItemId, int secondItemId) {
		ExecutableRouteRecord record = RouteRegistry.get(state,
				ExecutableRouteKey.itemOnItem(firstItemId, secondItemId));
		return record == null || !record.isGuest() ? null
				: record.guestInvoker();
	}

	public static ExecutableRouteRecord getItemOnItemRecord(
			RegistryStore.State state, int firstItemId, int secondItemId) {
		return RouteRegistry.get(state, ExecutableRouteKey.itemOnItem(
				firstItemId, secondItemId));
	}

	public static void putItemOnObject(int itemId, int objectId, Value handler) {
		RouteRegistry.put(ExecutableRouteKey.itemOnObject(itemId, objectId),
				handler);
	}

	public static Value getItemOnObject(int itemId, int objectId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getItemOnObject(state, itemId, objectId));
	}

	public static Value getItemOnObject(RegistryStore.State state,
			int itemId, int objectId) {
		ExecutableRouteRecord record = RouteRegistry.get(state,
				ExecutableRouteKey.itemOnObject(itemId, objectId));
		return record == null || !record.isGuest() ? null
				: record.guestInvoker();
	}

	public static ExecutableRouteRecord getItemOnObjectRecord(
			RegistryStore.State state, int itemId, int objectId) {
		return RouteRegistry.get(state, ExecutableRouteKey.itemOnObject(
				itemId, objectId));
	}

	public static void putItemOnNpc(int itemId, int npcId, Value handler) {
		RouteRegistry.put(ExecutableRouteKey.itemOnNpc(itemId, npcId), handler);
	}

	public static Value getItemOnNpc(int itemId, int npcId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getItemOnNpc(state, itemId, npcId));
	}

	public static Value getItemOnNpc(RegistryStore.State state,
			int itemId, int npcId) {
		ExecutableRouteRecord record = RouteRegistry.get(state,
				ExecutableRouteKey.itemOnNpc(itemId, npcId));
		return record == null || !record.isGuest() ? null
				: record.guestInvoker();
	}

	public static ExecutableRouteRecord getItemOnNpcRecord(
			RegistryStore.State state, int itemId, int npcId) {
		return RouteRegistry.get(state,
				ExecutableRouteKey.itemOnNpc(itemId, npcId));
	}

	public static void clear() {
		RegistryStore.State state = RegistryStore.writable();
		RouteRegistry.clear(state, RouteKind.ITEM);
		RouteRegistry.clear(state, RouteKind.ITEM_ON_ITEM);
		RouteRegistry.clear(state, RouteKind.ITEM_ON_OBJECT);
		RouteRegistry.clear(state, RouteKind.ITEM_ON_NPC);
	}

	private ItemHandlerRegistry() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}
}
