package com.rs2.script.area;

/**
 * Drop-delivery policy of one area NPC spawn or object projection.
 */
public enum AreaDropPolicy {

	/** Exact identities private to the captured killer/acting player. */
	PRIVATE_TO_KILLER,

	/** Exact public identities at the captured tile with a bounded lifetime. */
	PUBLIC;

	/** Canonical script name, or {@code null} for unknown values. */
	public static AreaDropPolicy fromScriptName(String name) {
		if (name == null) {
			return null;
		}
		if ("private-to-killer".equals(name)) {
			return PRIVATE_TO_KILLER;
		}
		if ("public".equals(name)) {
			return PUBLIC;
		}
		return null;
	}

	/** Canonical script name of this policy. */
	public String scriptName() {
		return this == PRIVATE_TO_KILLER ? "private-to-killer" : "public";
	}

}
