package com.rs2.script.registries;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.polyglot.Value;
import com.rs2.script.ScriptHost;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.ExecutableRouteRecord;
import com.rs2.script.route.RouteKind;
import com.rs2.script.route.RouteRegistry;

/**
 * Typed facade over the unified route registry for scripted command
 * handlers. A command route owns the consumed-versus-legacy decision for its
 * exact lower-case name.
 */
public final class CommandHandlerRegistry {

	public static void put(String command, Value handler) {
		RouteRegistry.put(ExecutableRouteKey.command(command), handler);
	}

	public static Value get(String command) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, command));
	}

	/** Guest value of the exact command route, or {@code null}. */
	public static Value get(RegistryStore.State state, String command) {
		ExecutableRouteRecord record = RouteRegistry.get(state,
				ExecutableRouteKey.command(command));
		return record == null || !record.isGuest() ? null
				: record.guestInvoker();
	}

	/** Exact route record of the command, or {@code null}. */
	public static ExecutableRouteRecord getRecord(RegistryStore.State state,
			String command) {
		return RouteRegistry.get(state, ExecutableRouteKey.command(command));
	}

	public static Map<String, Value> all() {
		return ScriptHost.getInstance().readActiveRegistry(
				CommandHandlerRegistry::all);
	}

	public static Map<String, Value> all(RegistryStore.State state) {
		Map<String, Value> handlers = new LinkedHashMap<>();
		for (Map.Entry<ExecutableRouteKey, ExecutableRouteRecord> entry
				: state.routes.entrySet()) {
			if (entry.getKey().kind() == RouteKind.COMMAND
					&& entry.getValue().isGuest()) {
				handlers.put(entry.getKey().key(), entry.getValue()
						.guestInvoker());
			}
		}
		return Collections.unmodifiableMap(handlers);
	}

	public static void clear() {
		RouteRegistry.clear(RegistryStore.writable(), RouteKind.COMMAND);
	}

	private CommandHandlerRegistry() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}
}
