package com.rs2.script.registries;

import com.rs2.script.ScriptHost;
import com.rs2.script.boss.BossDefinition;

/**
 * Typed facade over the common definition envelope for declarative boss
 * definitions keyed by numeric NPC id. Every canonical boss is a Java-owned
 * typed record; guest legacy payloads are rejected once the compiled loader
 * migrates.
 */
public final class BossRegistry {

	/** Returns the boss definition registered for {@code bossId} or {@code null}. */
	public static BossDefinition get(int bossId) {
		return com.rs2.script.boss.BossDefinitionRegistry.get(bossId);
	}

	/** Returns the boss definition registered for {@code bossId} or {@code null}. */
	public static BossDefinition get(RegistryStore.State state, int bossId) {
		return com.rs2.script.boss.BossDefinitionRegistry.get(state, bossId);
	}

	private BossRegistry() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}

}
