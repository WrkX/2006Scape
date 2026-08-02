package com.rs2.script.boss;

/**
 * Cleanup contract of a declarative boss.
 *
 * <p>{@link #CLOSE_ON_TERMINAL} is the only canonical value: the standalone
 * adapter closes its owned encounter on any terminal controller result so
 * normal death, explicit close, callback failure, owner death/logout, and
 * successful reload converge on the idempotent owner cleanup path. A raid
 * embeds the controller with its own wipe policy and never uses this member.
 */
public enum BossCleanupPolicy {

	CLOSE_ON_TERMINAL;

	private static final String CLOSE_ON_TERMINAL_NAME = "close-on-terminal";

	/**
	 * Parses the canonical script name, or returns {@code null} for an
	 * unknown value so the definition parser can fail with source context.
	 */
	public static BossCleanupPolicy fromScriptName(String name) {
		return CLOSE_ON_TERMINAL_NAME.equals(name) ? CLOSE_ON_TERMINAL : null;
	}

	public String scriptName() {
		return CLOSE_ON_TERMINAL_NAME;
	}

}
