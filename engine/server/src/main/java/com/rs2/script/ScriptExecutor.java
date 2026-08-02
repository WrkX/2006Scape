package com.rs2.script;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.graalvm.polyglot.Value;

import com.rs2.util.LoggerUtils;

/**
 * Exception boundary for guest callbacks invoked by the game engine.
 */
public final class ScriptExecutor {

	private static final Logger logger = LoggerUtils.getLogger(ScriptExecutor.class);

	/**
	 * Executes a registered handler. A non-null handler is authoritative even
	 * when it is malformed or throws, so callers must not run legacy fallback
	 * behavior after this method returns {@code true}.
	 */
	public static boolean execute(Value handler, String category, String identity,
			String action, Object... arguments) {
		if (handler == null) {
			return false;
		}
		try {
			if (!handler.canExecute()) {
				log(category, identity, action, "handler is not executable", null);
				return true;
			}
			handler.execute(arguments);
		} catch (RuntimeException e) {
			log(category, identity, action, "handler threw", e);
		}
		return true;
	}

	/**
	 * Executes a callback and reports whether it completed successfully.
	 *
	 * <p>This is used by repeating tasks, where a thrown guest exception must
	 * stop the task instead of producing failure spam every cycle.
	 */
	public static boolean executeChecked(Value handler, String category, String identity,
			String action, Object... arguments) {
		if (handler == null) {
			return false;
		}
		try {
			if (!handler.canExecute()) {
				log(category, identity, action, "handler is not executable", null);
				return false;
			}
			handler.execute(arguments);
			return true;
		} catch (RuntimeException e) {
			log(category, identity, action, "handler threw", e);
			return false;
		}
	}

	public static void run(String category, String identity, String action, Runnable callback) {
		try {
			callback.run();
		} catch (RuntimeException e) {
			log(category, identity, action, "callback threw", e);
		}
	}

	private static void log(String category, String identity, String action,
			String message, RuntimeException error) {
		String detail = "Script " + category + " [" + identity + "] action ["
				+ action + "]: " + message;
		if (error == null) {
			logger.log(Level.WARNING, detail);
		} else {
			logger.log(Level.WARNING, detail, error);
		}
	}

	private ScriptExecutor() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}
}
