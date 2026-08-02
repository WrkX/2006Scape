package com.rs2.script.registries;

import org.graalvm.polyglot.Value;
import com.rs2.script.ScriptHost;

/**
 * Stores scripted boss definitions keyed by boss id.
 */
public final class BossRegistry {

	/**
	 * Registers {@code definition} for {@code bossId}.
	 */
	public static Value put(int bossId, Value definition) {
		return RegistryStore.writable().bosses.putIfAbsent(bossId, definition);
	}

	/**
	 * Returns the definition registered for {@code bossId} or {@code null}.
	 */
	public static Value get(int bossId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> state.bosses.get(bossId));
	}

	/**
	 * Removes every registered boss. Intended for hot-reload.
	 */
	public static void clear() {
		RegistryStore.writable().bosses.clear();
	}

	private BossRegistry() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}

}
