package com.rs2.script.registries;

import org.graalvm.polyglot.Value;
import com.rs2.script.ScriptHost;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.ExecutableRouteRecord;
import com.rs2.script.route.RouteKind;
import com.rs2.script.route.RouteRegistry;

/**
 * Typed facade over the unified route registry for NPC interaction handlers
 * keyed by npc id and ordinal action ("first" .. "third").
 */
public final class NpcHandlerRegistry {

	/**
	 * Registers {@code handler} for the {@code (npcId, action)} pair.
	 */
	public static void put(int npcId, String action, Value handler) {
		RouteRegistry.put(ExecutableRouteKey.npc(npcId, action), handler);
	}

	/**
	 * Returns the guest handler registered for {@code (npcId, action)} or
	 * {@code null} when no guest route exists.
	 */
	public static Value get(int npcId, String action) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, npcId, action));
	}

	public static Value get(RegistryStore.State state, int npcId,
			String action) {
		ExecutableRouteRecord record = RouteRegistry.get(state,
				ExecutableRouteKey.npc(npcId, action));
		return record == null || !record.isGuest() ? null
				: record.guestInvoker();
	}

	/** Exact route record of the npc/action key, or {@code null}. */
	public static ExecutableRouteRecord getRecord(RegistryStore.State state,
			int npcId, String action) {
		return RouteRegistry.get(state,
				ExecutableRouteKey.npc(npcId, action));
	}

	/**
	 * Removes every registered handler. Intended for hot-reload.
	 */
	public static void clear() {
		RouteRegistry.clear(RegistryStore.writable(), RouteKind.NPC);
	}

	private NpcHandlerRegistry() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}

}
