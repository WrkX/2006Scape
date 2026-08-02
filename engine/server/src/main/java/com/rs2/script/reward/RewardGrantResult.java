package com.rs2.script.reward;

import org.graalvm.polyglot.HostAccess;

/**
 * Narrow result-shaped facade of one named-reward grant.
 *
 * <p>Exposes only the named reward id and a stable result code; registry
 * maps and engine inventory arrays are never guest-visible.
 */
public final class RewardGrantResult {

	/** Closed wire union of the grant outcome. */
	public enum Code {
		/** The complete reward committed. */
		REWARDED,
		/** No named reward with this id is registered in the active generation. */
		NOT_FOUND,
		/** Item grants could not all fit. */
		INVENTORY_FULL,
		/** An XP grant would exceed the cap. */
		XP_CAP,
		/** The net quest-point total would leave 0..10000. */
		QUEST_POINTS_OVERFLOW,
		/** A mutation, postcondition, or state failure rolled everything back. */
		REWARD_FAILED
	}

	private final String rewardId;
	private final Code code;

	public RewardGrantResult(String rewardId, Code code) {
		this.rewardId = rewardId;
		this.code = code;
	}

	@HostAccess.Export
	public String rewardId() {
		return rewardId;
	}

	@HostAccess.Export
	public String code() {
		return code.name().toLowerCase(java.util.Locale.ROOT);
	}

	public Code codeValue() {
		return code;
	}

}
