package com.rs2.script.activation;

import org.graalvm.polyglot.Value;

/**
 * Contained runner for module lifecycle observers.
 *
 * <p>Return and throw are captured into a bounded {@link HookResult}.
 * Registration is already closed when hooks run, so an attempted
 * registration throws and is contained here; other guest-visible hook
 * effects are not reversible and are never rolled back.
 */
public final class HookRunner {

	private HookRunner() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}

	/**
	 * Runs one hook. Returns {@code null} when the hook is absent, otherwise
	 * a result capturing success or the bounded failure.
	 */
	public static HookResult run(Value hook, String category, String identity) {
		if (hook == null) {
			return null;
		}
		try {
			if (!hook.canExecute()) {
				return HookResult.failure(identity,
						"module " + category + " hook is not executable");
			}
			hook.execute();
			return HookResult.success(identity);
		} catch (Throwable failure) {
			String message = failure.getMessage();
			if (message == null || message.trim().isEmpty()) {
				message = failure.getClass().getSimpleName();
			}
			return HookResult.failure(identity, message);
		}
	}

}
