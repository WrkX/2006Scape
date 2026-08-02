package com.rs2.script;

import org.graalvm.polyglot.Context;

import com.rs2.script.registries.RegistryStore;

/**
 * Publishes test handlers with the same context/state/generation bundle used
 * by production reloads.
 */
public final class ScriptRuntimeTestFixture {

	public static void publish(Context context, Runnable registrations) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			registrations.run();
			ScriptHost.getInstance().publishForTesting(context, candidate);
		} catch (RuntimeException error) {
			RegistryStore.rollback(candidate);
			throw error;
		}
	}

	public static void publishEmpty(Context context) {
		publish(context, new Runnable() {
			@Override
			public void run() {
				// Empty candidate.
			}
		});
	}

	public static void publishCandidate(Context context,
			RegistryStore.State candidate) {
		ScriptHost.getInstance().publishForTesting(context, candidate);
	}

	public static void reset() {
		ScriptHost.getInstance().resetForTesting();
	}

	private ScriptRuntimeTestFixture() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
