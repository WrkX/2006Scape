package com.rs2.script.area;

/**
 * Immutable object drop binding of one area object projection: the ordinal
 * action, the named WP2 drop table, and the delivery policy.
 */
public final class AreaObjectDrop {

	private final String action;
	private final String dropTable;
	private final AreaDropPolicy dropPolicy;
	private final int privateTicks;

	public AreaObjectDrop(String action, String dropTable,
			AreaDropPolicy dropPolicy, int privateTicks) {
		this.action = action;
		this.dropTable = dropTable;
		this.dropPolicy = dropPolicy;
		this.privateTicks = privateTicks;
	}

	public String action() {
		return action;
	}

	public String dropTable() {
		return dropTable;
	}

	public AreaDropPolicy dropPolicy() {
		return dropPolicy;
	}

	/** Private TTL in game cycles; valid only for private delivery. */
	public int privateTicks() {
		return privateTicks;
	}

	@Override
	public String toString() {
		return "object drop '" + action + "' via table '" + dropTable + "'";
	}

}
