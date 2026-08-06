package com.rs2.script.registries;

import org.graalvm.polyglot.Value;
import com.rs2.script.ScriptHost;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.ExecutableRouteRecord;
import com.rs2.script.route.RouteRegistry;

/**
 * Typed facade over the unified route registry for the Phase 4 interaction
 * routes.
 *
 * <p>Packet authority and fallback rules are deliberately implemented by the
 * packet adapters, not this registry.
 */
public final class InteractionHandlerRegistry {

	public static void putButton(int buttonId, Value handler) {
		RouteRegistry.put(ExecutableRouteKey.button(buttonId), handler);
	}

	public static Value getButton(RegistryStore.State state, int buttonId) {
		return guestValue(state, ExecutableRouteKey.button(buttonId));
	}

	public static ExecutableRouteRecord getButtonRecord(
			RegistryStore.State state, int buttonId) {
		return RouteRegistry.get(state, ExecutableRouteKey.button(buttonId));
	}

	public static Value getButton(int buttonId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getButton(state, buttonId));
	}

	public static void putItemOnGroundItem(int itemId, int groundItemId,
			Value handler) {
		RouteRegistry.put(ExecutableRouteKey.itemOnGroundItem(itemId,
				groundItemId), handler);
	}

	public static Value getItemOnGroundItem(RegistryStore.State state,
			int itemId, int groundItemId) {
		return guestValue(state, ExecutableRouteKey.itemOnGroundItem(
				itemId, groundItemId));
	}

	public static ExecutableRouteRecord getItemOnGroundItemRecord(
			RegistryStore.State state, int itemId, int groundItemId) {
		return RouteRegistry.get(state, ExecutableRouteKey.itemOnGroundItem(
				itemId, groundItemId));
	}

	public static Value getItemOnGroundItem(int itemId, int groundItemId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getItemOnGroundItem(state, itemId, groundItemId));
	}

	public static void putItemOnPlayer(int itemId, Value handler) {
		RouteRegistry.put(ExecutableRouteKey.itemOnPlayer(itemId), handler);
	}

	public static Value getItemOnPlayer(RegistryStore.State state, int itemId) {
		return guestValue(state, ExecutableRouteKey.itemOnPlayer(itemId));
	}

	public static ExecutableRouteRecord getItemOnPlayerRecord(
			RegistryStore.State state, int itemId) {
		return RouteRegistry.get(state, ExecutableRouteKey.itemOnPlayer(itemId));
	}

	public static Value getItemOnPlayer(int itemId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getItemOnPlayer(state, itemId));
	}

	public static void putMagicOnItem(int spellId, int itemId, Value handler) {
		RouteRegistry.put(ExecutableRouteKey.magicOnItem(spellId, itemId),
				handler);
	}

	public static Value getMagicOnItem(RegistryStore.State state,
			int spellId, int itemId) {
		return guestValue(state,
				ExecutableRouteKey.magicOnItem(spellId, itemId));
	}

	public static ExecutableRouteRecord getMagicOnItemRecord(
			RegistryStore.State state, int spellId, int itemId) {
		return RouteRegistry.get(state,
				ExecutableRouteKey.magicOnItem(spellId, itemId));
	}

	public static Value getMagicOnItem(int spellId, int itemId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getMagicOnItem(state, spellId, itemId));
	}

	public static void putMagicOnObject(int spellId, int objectId,
			Value handler) {
		RouteRegistry.put(ExecutableRouteKey.magicOnObject(spellId, objectId),
				handler);
	}

	public static Value getMagicOnObject(RegistryStore.State state,
			int spellId, int objectId) {
		return guestValue(state,
				ExecutableRouteKey.magicOnObject(spellId, objectId));
	}

	public static ExecutableRouteRecord getMagicOnObjectRecord(
			RegistryStore.State state, int spellId, int objectId) {
		return RouteRegistry.get(state,
				ExecutableRouteKey.magicOnObject(spellId, objectId));
	}

	public static Value getMagicOnObject(int spellId, int objectId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getMagicOnObject(state, spellId, objectId));
	}

	public static void putMagicOnNpc(int spellId, int npcId, Value handler) {
		RouteRegistry.put(ExecutableRouteKey.magicOnNpc(spellId, npcId),
				handler);
	}

	public static Value getMagicOnNpc(RegistryStore.State state, int spellId,
			int npcId) {
		return guestValue(state, ExecutableRouteKey.magicOnNpc(spellId, npcId));
	}

	public static ExecutableRouteRecord getMagicOnNpcRecord(
			RegistryStore.State state, int spellId, int npcId) {
		return RouteRegistry.get(state,
				ExecutableRouteKey.magicOnNpc(spellId, npcId));
	}

	public static Value getMagicOnNpc(int spellId, int npcId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getMagicOnNpc(state, spellId, npcId));
	}

	public static void putMagicOnPlayer(int spellId, Value handler) {
		RouteRegistry.put(ExecutableRouteKey.magicOnPlayer(spellId), handler);
	}

	public static Value getMagicOnPlayer(RegistryStore.State state, int spellId) {
		return guestValue(state, ExecutableRouteKey.magicOnPlayer(spellId));
	}

	public static ExecutableRouteRecord getMagicOnPlayerRecord(
			RegistryStore.State state, int spellId) {
		return RouteRegistry.get(state, ExecutableRouteKey.magicOnPlayer(spellId));
	}

	public static Value getMagicOnPlayer(int spellId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getMagicOnPlayer(state, spellId));
	}

	private static Value guestValue(RegistryStore.State state,
			ExecutableRouteKey key) {
		ExecutableRouteRecord record = RouteRegistry.get(state, key);
		return record == null || !record.isGuest() ? null
				: record.guestInvoker();
	}

	private InteractionHandlerRegistry() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
