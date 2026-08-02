package com.rs2.script.registries;

import java.util.Map;

import org.graalvm.polyglot.Value;
import com.rs2.script.ScriptHost;

/**
 * Stores scripted command handlers as part of the active context state.
 */
public final class CommandHandlerRegistry {

	public static Value put(String command, Value handler) {
		return RegistryStore.writable().commandHandlers.putIfAbsent(command, handler);
	}

	public static Value get(String command) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, command));
	}

	public static Value get(RegistryStore.State state, String command) {
		return state.commandHandlers.get(command);
	}

	public static Map<String, Value> all() {
		return ScriptHost.getInstance().readActiveRegistry(
				CommandHandlerRegistry::all);
	}

	public static Map<String, Value> all(RegistryStore.State state) {
		return state.commandHandlers;
	}

	public static void clear() {
		RegistryStore.writable().commandHandlers.clear();
	}

	private CommandHandlerRegistry() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}
}
