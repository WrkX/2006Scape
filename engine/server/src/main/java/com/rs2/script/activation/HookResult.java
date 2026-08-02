package com.rs2.script.activation;

/**
 * Bounded result of one contained lifecycle observer invocation.
 *
 * <p>Observer return and throw are captured into this preallocated value.
 * The mutation or throw is never rolled back and never vetoes publication;
 * it is reported so operators can see it.
 */
public final class HookResult {

	private final String identity;
	private final boolean threw;
	private final String message;

	private HookResult(String identity, boolean threw, String message) {
		this.identity = identity;
		this.threw = threw;
		this.message = message;
	}

	public static HookResult success(String identity) {
		return new HookResult(identity, false, null);
	}

	public static HookResult failure(String identity, String message) {
		return new HookResult(identity, true, bound(message));
	}

	/** Module or hook identity that produced this result. */
	public String identity() {
		return identity;
	}

	public boolean threw() {
		return threw;
	}

	/** Bounded failure message, or {@code null} on success. */
	public String message() {
		return message;
	}

	private static String bound(String value) {
		if (value == null) {
			return null;
		}
		int limit = 512;
		String trimmed = value.trim();
		return trimmed.length() <= limit ? trimmed
				: trimmed.substring(0, limit) + "...";
	}

}
