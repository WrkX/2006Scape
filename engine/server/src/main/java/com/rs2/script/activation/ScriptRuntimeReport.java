package com.rs2.script.activation;

/**
 * Immutable snapshot of one committed script runtime generation.
 *
 * <p>The report is built before the no-throw publication line and is part of
 * the atomic commit, so it carries only facts known before publication: the
 * generation, bounded registry counts, and the captured old-generation
 * unload-observer result. Post-commit observer and finalization failures are
 * reported through the host diagnostics seam, never through a re-rolled-back
 * report.
 */
public final class ScriptRuntimeReport {

	/** Publication outcome of the generation. */
	public enum Status {
		/** The generation is fully committed and selected. */
		LOADED,
		/** The generation failed before publication; no report is selected. */
		FAILED
	}

	private final Status status;
	private final long generation;
	private final int moduleCount;
	private final int definitionCount;
	private final int routeCount;
	private final HookResult unloadResult;
	private final String quarantineWarning;

	public ScriptRuntimeReport(Status status, long generation,
			int moduleCount, int definitionCount, int routeCount,
			HookResult unloadResult) {
		this(status, generation, moduleCount, definitionCount, routeCount,
				unloadResult, null);
	}

	public ScriptRuntimeReport(Status status, long generation,
			int moduleCount, int definitionCount, int routeCount,
			HookResult unloadResult, String quarantineWarning) {
		this.status = status;
		this.generation = generation;
		this.moduleCount = moduleCount;
		this.definitionCount = definitionCount;
		this.routeCount = routeCount;
		this.unloadResult = unloadResult;
		this.quarantineWarning = quarantineWarning;
	}

	public Status status() {
		return status;
	}

	public long generation() {
		return generation;
	}

	public int moduleCount() {
		return moduleCount;
	}

	public int definitionCount() {
		return definitionCount;
	}

	public int routeCount() {
		return routeCount;
	}

	/** First old-generation unload-observer failure, or {@code null}. */
	public HookResult unloadResult() {
		return unloadResult;
	}

	/**
	 * Bounded non-fatal activation failure captured during commit, or
	 * {@code null} when none occurred.
	 */
	public String quarantineWarning() {
		return quarantineWarning;
	}

}
