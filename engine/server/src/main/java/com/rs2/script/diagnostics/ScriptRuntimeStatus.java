package com.rs2.script.diagnostics;

/**
 * Immutable logical snapshot of the active script runtime for operator
 * diagnostics.
 *
 * <p>Every value is derived from the active immutable registry snapshot or
 * from Java-owned runtime singletons under their own monitors; the report
 * never exposes a raw Graal {@code Value}, engine object, host path,
 * stack trace, or credential. All string outputs are logical module and
 * definition identifiers only.
 *
 * <p>The runtime counts are captured near-instantaneously across the
 * singleton monitors (never while holding the script-host dispatch lease);
 * they are not a single atomic instant under live world mutation.
 */
public final class ScriptRuntimeStatus {

	private final long generation;
	private final int moduleCount;
	private final int definitionCount;
	private final int routeCount;
	private final long scheduledTasks;
	private final int activeEncounters;
	private final int activeBossSessions;
	private final int activeAreaSessions;
	private final int activeShops;
	private final int activeRaidLobbies;
	private final int activeRaidSessions;
	private final int activeMinigameLobbies;
	private final int activeMinigameSessions;
	private final int activeResourceSessions;
	private final int mappedQuestRows;

	public ScriptRuntimeStatus(long generation, int moduleCount,
			int definitionCount, int routeCount, long scheduledTasks,
			int activeEncounters, int activeBossSessions,
			int activeAreaSessions, int activeShops, int activeRaidLobbies,
			int activeRaidSessions, int activeMinigameLobbies,
			int activeMinigameSessions, int activeResourceSessions,
			int mappedQuestRows) {
		this.generation = generation;
		this.moduleCount = moduleCount;
		this.definitionCount = definitionCount;
		this.routeCount = routeCount;
		this.scheduledTasks = scheduledTasks;
		this.activeEncounters = activeEncounters;
		this.activeBossSessions = activeBossSessions;
		this.activeAreaSessions = activeAreaSessions;
		this.activeShops = activeShops;
		this.activeRaidLobbies = activeRaidLobbies;
		this.activeRaidSessions = activeRaidSessions;
		this.activeMinigameLobbies = activeMinigameLobbies;
		this.activeMinigameSessions = activeMinigameSessions;
		this.activeResourceSessions = activeResourceSessions;
		this.mappedQuestRows = mappedQuestRows;
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

	public long scheduledTasks() {
		return scheduledTasks;
	}

	public int activeEncounters() {
		return activeEncounters;
	}

	public int activeBossSessions() {
		return activeBossSessions;
	}

	public int activeAreaSessions() {
		return activeAreaSessions;
	}

	public int activeShops() {
		return activeShops;
	}

	public int activeRaidLobbies() {
		return activeRaidLobbies;
	}

	public int activeRaidSessions() {
		return activeRaidSessions;
	}

	public int activeMinigameLobbies() {
		return activeMinigameLobbies;
	}

	public int activeMinigameSessions() {
		return activeMinigameSessions;
	}

	public int activeResourceSessions() {
		return activeResourceSessions;
	}

	public int mappedQuestRows() {
		return mappedQuestRows;
	}

	@Override
	public String toString() {
		return "generation " + generation + ", " + moduleCount + " modules, "
				+ definitionCount + " definitions, " + routeCount + " routes";
	}

}
