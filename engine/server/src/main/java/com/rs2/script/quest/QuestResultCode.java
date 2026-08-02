package com.rs2.script.quest;

/**
 * Closed set of stable wire codes exposed by {@link QuestResult#code()}.
 */
enum QuestResultCode {
	CAN_START("can_start"),
	STARTED("started"),
	ALREADY_COMPLETED("already_completed"),
	ALREADY_STARTED("already_started"),
	REQUIREMENTS_NOT_MET("requirements_not_met"),
	STATE_FAILED("state_failed"),
	NOT_IN_PROGRESS("not_in_progress"),
	STAGE_MISMATCH("stage_mismatch"),
	INVALID_STAGE("invalid_stage"),
	ADVANCED("advanced"),
	NOT_FINAL_STAGE("not_final_stage"),
	QUEST_POINTS_OVERFLOW("quest_points_overflow"),
	INVENTORY_FULL("inventory_full"),
	XP_CAP("xp_cap"),
	REWARD_FAILED("reward_failed"),
	COMPLETED("completed");

	private final String wireCode;

	QuestResultCode(String wireCode) {
		this.wireCode = wireCode;
	}

	String wireCode() {
		return wireCode;
	}
}
