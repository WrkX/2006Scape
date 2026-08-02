package com.rs2.script.route;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.graalvm.polyglot.Value;

import com.rs2.script.ScriptHost;
import com.rs2.script.definition.ModuleScope;
import com.rs2.script.registries.RegistryStore;

/**
 * Unified candidate-wide executable route registry.
 *
 * <p>Guest callbacks and Java host consumers share one immutable registry
 * with no precedence escape hatch: a duplicate exact key between any two
 * sources or owners rejects the whole candidate and identifies both records.
 * Command aliases reserved for the Java admin transport reject both guest and
 * host registrations.
 */
public final class RouteRegistry {

	/**
	 * Commands owned by the Java admin transport. Content may never register
	 * a guest or host route for these aliases.
	 */
	public static final List<String> RESERVED_COMMANDS = Collections
			.unmodifiableList(Arrays.asList("scripts", "reload", "scriptdir"));

	/**
	 * Registers a guest route. Reserved aliases and duplicate exact keys
	 * reject the candidate with both records identified.
	 */
	public static void put(ExecutableRouteKey key, Value guestInvoker) {
		putRecord(key, ExecutableRouteRecord.guest(key,
				ModuleScope.currentSource(), guestInvoker));
	}

	/**
	 * Registers a Java host route. Reserved aliases and duplicate exact keys
	 * reject the candidate with both records identified.
	 */
	public static void putHost(ExecutableRouteKey key, HostRoute hostInvoker) {
		putRecord(key, ExecutableRouteRecord.host(key,
				ModuleScope.currentSource(), hostInvoker));
	}

	public static ExecutableRouteRecord get(RegistryStore.State state,
			ExecutableRouteKey key) {
		return state.routes.get(key);
	}

	public static ExecutableRouteRecord get(ExecutableRouteKey key) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, key));
	}

	/**
	 * Removes every route of one kind from the active candidate. Used by
	 * legacy compatibility clears.
	 */
	public static void clear(RegistryStore.State state, RouteKind kind) {
		Iterator<ExecutableRouteKey> keys = state.routes.keySet().iterator();
		while (keys.hasNext()) {
			if (keys.next().kind() == kind) {
				keys.remove();
			}
		}
	}

	private static void putRecord(ExecutableRouteKey key,
			ExecutableRouteRecord record) {
		RegistryStore.State candidate = RegistryStore.writable();
		if (key.kind() == RouteKind.COMMAND
				&& RESERVED_COMMANDS.contains(key.key())) {
			throw new IllegalArgumentException("Script registration " + key
					+ " (source: " + record.source()
					+ "): command alias is reserved for the engine admin transport");
		}
		ExecutableRouteRecord previous = candidate.routes.putIfAbsent(
				key, record);
		if (previous != null) {
			throw new IllegalArgumentException("Script registration " + key
					+ " (source: " + record.source()
					+ "): duplicate registration; existing record: "
					+ previous);
		}
	}

	private RouteRegistry() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
