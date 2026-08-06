package com.rs2.script.diagnostics;

/**
 * Immutable, bounded outcome of one operator-initiated reload attempt.
 *
 * <p>A successful reload reports the newly committed generation and the
 * candidate module count. A failed reload reports the bounded candidate
 * error message and proves that the previous generation remains live. The
 * result is captured by the admin transport from {@link
 * com.rs2.script.ScriptHost#reloadWithResult()}; it never exposes a raw
 * exception, stack trace, host path, or engine object.
 */
public final class ScriptReloadResult {

	private final boolean succeeded;
	private final long generation;
	private final int moduleCount;
	private final String failure;

	private ScriptReloadResult(boolean succeeded, long generation,
			int moduleCount, String failure) {
		this.succeeded = succeeded;
		this.generation = generation;
		this.moduleCount = moduleCount;
		this.failure = failure;
	}

	public static ScriptReloadResult success(long generation, int moduleCount) {
		return new ScriptReloadResult(true, generation, moduleCount, null);
	}

	public static ScriptReloadResult failure(long retainedGeneration,
			String message) {
		return new ScriptReloadResult(false, retainedGeneration, 0, bound(message));
	}

	/** {@code true} when the candidate was published and is now selected. */
	public boolean succeeded() {
		return succeeded;
	}

	/** Active generation after the attempt (the new one on success). */
	public long generation() {
		return generation;
	}

	/** Registered content-module count of the published candidate. */
	public int moduleCount() {
		return moduleCount;
	}

	/** Bounded candidate failure message; {@code null} on success. */
	public String failure() {
		return failure;
	}

	private static String bound(String value) {
		if (value == null) {
			return "unknown failure";
		}
		int limit = 512;
		String trimmed = value.trim();
		return trimmed.length() <= limit ? trimmed
				: trimmed.substring(0, limit) + "...";
	}

}
