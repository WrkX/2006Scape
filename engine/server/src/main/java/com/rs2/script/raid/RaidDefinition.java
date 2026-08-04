package com.rs2.script.raid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.graalvm.polyglot.Value;

import com.rs2.script.drop.DropTableDefinition;
import com.rs2.script.reward.RewardDefinition;

/**
 * Immutable Java-owned schema-v1 declarative raid descriptor.
 *
 * <p>The descriptor carries copied canonical values only: a stable string
 * id, the exact WP1 host command route, the raid bounds and bounded muster
 * area on one plane, the entrance point, bounded player limits, the time
 * limit, the ordered non-overlapping rooms, the resolved named rewards and
 * optional drop table, the optional private TTL for the drop-table roll, and
 * the generation-owned {@code onStart}/{@code onComplete}/{@code onWipe}
 * callbacks. Cross-references (boss ids, reward ids, drop-table id) are
 * resolved at candidate validation; callbacks are valid only while the
 * registering context is active.
 */
public final class RaidDefinition {

	private final String id;
	private final String name;
	private final String command;
	private final RaidBounds bounds;
	private final RaidBounds muster;
	private final int entranceX;
	private final int entranceY;
	private final int entrancePlane;
	private final int minPlayers;
	private final int maxPlayers;
	private final int timeLimitTicks;
	private final List<RaidRoomDefinition> rooms;
	private final List<RewardDefinition> rewards;
	private final String rewardTable;
	private final int privateTicks;
	private final boolean hasRewardTable;
	private final Value onStart;
	private final Value onComplete;
	private final Value onWipe;
	private final String source;
	private final int schemaVersion;

	public RaidDefinition(String id, String name, String command,
			RaidBounds bounds, RaidBounds muster, int entranceX,
			int entranceY, int entrancePlane, int minPlayers, int maxPlayers,
			int timeLimitTicks, List<RaidRoomDefinition> rooms,
			List<RewardDefinition> rewards, String rewardTable,
			int privateTicks, boolean hasRewardTable, Value onStart,
			Value onComplete, Value onWipe, String source, int schemaVersion) {
		this.id = id;
		this.name = name;
		this.command = command;
		this.bounds = bounds;
		this.muster = muster;
		this.entranceX = entranceX;
		this.entranceY = entranceY;
		this.entrancePlane = entrancePlane;
		this.minPlayers = minPlayers;
		this.maxPlayers = maxPlayers;
		this.timeLimitTicks = timeLimitTicks;
		this.rooms = Collections.unmodifiableList(
				new ArrayList<RaidRoomDefinition>(rooms));
		this.rewards = Collections.unmodifiableList(
				new ArrayList<RewardDefinition>(rewards));
		this.rewardTable = rewardTable;
		this.privateTicks = privateTicks;
		this.hasRewardTable = hasRewardTable;
		this.onStart = onStart;
		this.onComplete = onComplete;
		this.onWipe = onWipe;
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	/** Stable string id referenced by areas and diagnostics. */
	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	/** Exact WP1 host command route; never a reserved admin alias. */
	public String command() {
		return command;
	}

	/** The single-plane rectangle the encounter reserves for the raid. */
	public RaidBounds bounds() {
		return bounds;
	}

	/** Bounded muster rectangle on the raid plane for the start check. */
	public RaidBounds muster() {
		return muster;
	}

	public int entranceX() {
		return entranceX;
	}

	public int entranceY() {
		return entranceY;
	}

	public int entrancePlane() {
		return entrancePlane;
	}

	public int minPlayers() {
		return minPlayers;
	}

	public int maxPlayers() {
		return maxPlayers;
	}

	public int timeLimitTicks() {
		return timeLimitTicks;
	}

	/** Immutable ordered rooms; the final room's completion starts the barrier. */
	public List<RaidRoomDefinition> rooms() {
		return rooms;
	}

	/** Named reward definitions applied roster-wide at completion. */
	public List<RewardDefinition> rewards() {
		return rewards;
	}

	/** Named drop table rolled once after the roster commit; may be absent. */
	public String rewardTable() {
		return rewardTable;
	}

	/** Private TTL of the reward-table roll; valid only with a reward table. */
	public int privateTicks() {
		return privateTicks;
	}

	public boolean hasRewardTable() {
		return hasRewardTable;
	}

	public Value onStart() {
		return onStart;
	}

	public Value onComplete() {
		return onComplete;
	}

	public Value onWipe() {
		return onWipe;
	}

	/** Bounded logical source module, or the legacy-unscoped marker. */
	public String source() {
		return source;
	}

	public int schemaVersion() {
		return schemaVersion;
	}

	@Override
	public String toString() {
		return "raid '" + id + "' (command: " + command + ", source: "
				+ source + ", schema v" + schemaVersion + ")";
	}

}
