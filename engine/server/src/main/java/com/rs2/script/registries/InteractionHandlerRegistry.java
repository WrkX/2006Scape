package com.rs2.script.registries;

import org.graalvm.polyglot.Value;
import com.rs2.script.ScriptHost;

/**
 * Candidate-owned exact registrations for the Phase 4 interaction routes.
 *
 * <p>Packet authority and fallback rules are deliberately implemented by the
 * packet adapters, not this registry.
 */
public final class InteractionHandlerRegistry {

	public static Value putButton(int buttonId, Value handler) {
		return RegistryStore.writable().buttonHandlers.putIfAbsent(buttonId, handler);
	}

	public static Value getButton(RegistryStore.State state, int buttonId) {
		return state.buttonHandlers.get(buttonId);
	}

	public static Value getButton(int buttonId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getButton(state, buttonId));
	}

	public static Value putItemOnGroundItem(int itemId, int groundItemId,
			Value handler) {
		return RegistryStore.writable().itemOnGroundItemHandlers.putIfAbsent(
				orderedKey(itemId, groundItemId), handler);
	}

	public static Value getItemOnGroundItem(RegistryStore.State state,
			int itemId, int groundItemId) {
		return state.itemOnGroundItemHandlers.get(
				orderedKey(itemId, groundItemId));
	}

	public static Value getItemOnGroundItem(int itemId, int groundItemId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getItemOnGroundItem(state, itemId, groundItemId));
	}

	public static Value putItemOnPlayer(int itemId, Value handler) {
		return RegistryStore.writable().itemOnPlayerHandlers.putIfAbsent(
				itemId, handler);
	}

	public static Value getItemOnPlayer(RegistryStore.State state, int itemId) {
		return state.itemOnPlayerHandlers.get(itemId);
	}

	public static Value getItemOnPlayer(int itemId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getItemOnPlayer(state, itemId));
	}

	public static Value putMagicOnItem(int spellId, int itemId, Value handler) {
		return RegistryStore.writable().magicOnItemHandlers.putIfAbsent(
				orderedKey(spellId, itemId), handler);
	}

	public static Value getMagicOnItem(RegistryStore.State state,
			int spellId, int itemId) {
		return state.magicOnItemHandlers.get(orderedKey(spellId, itemId));
	}

	public static Value getMagicOnItem(int spellId, int itemId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getMagicOnItem(state, spellId, itemId));
	}

	public static Value putMagicOnObject(int spellId, int objectId, Value handler) {
		return RegistryStore.writable().magicOnObjectHandlers.putIfAbsent(
				orderedKey(spellId, objectId), handler);
	}

	public static Value getMagicOnObject(RegistryStore.State state,
			int spellId, int objectId) {
		return state.magicOnObjectHandlers.get(orderedKey(spellId, objectId));
	}

	public static Value getMagicOnObject(int spellId, int objectId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> getMagicOnObject(state, spellId, objectId));
	}

	private static long orderedKey(int first, int second) {
		return ((long) first << 32) | (second & 0xffffffffL);
	}

	private InteractionHandlerRegistry() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
