package com.rs2.script.minigame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.graalvm.polyglot.Value;

import com.rs2.script.area.AreaBounds;

/**
 * Immutable Java-owned schema-v1 declarative minigame descriptor.
 *
 * <p>Composes {@code defineArea} references for lobby and arena bounds while
 * the runtime owns lobby queueing, one encounter per session, ordered waves,
 * and optional per-player score state.
 */
public final class MinigameDefinition {

	private final String id;
	private final String name;
	private final String command;
	private final String lobbyAreaId;
	private final String arenaAreaId;
	private final AreaBounds lobbyBounds;
	private final AreaBounds arenaBounds;
	private final int entranceX;
	private final int entranceY;
	private final int entrancePlane;
	private final int leaveX;
	private final int leaveY;
	private final int leavePlane;
	private final int minPlayers;
	private final int maxPlayers;
	private final int lobbyWaitTicks;
	private final int timeLimitTicks;
	private final List<MinigameWaveDefinition> waves;
	private final MinigameScoreDefinition score;
	private final Value onStart;
	private final Value onWaveStart;
	private final Value onWaveComplete;
	private final Value onTick;
	private final Value onComplete;
	private final Value onWipe;
	private final String source;
	private final int schemaVersion;

	public MinigameDefinition(String id, String name, String command,
			String lobbyAreaId, String arenaAreaId, AreaBounds lobbyBounds,
			AreaBounds arenaBounds, int entranceX, int entranceY,
			int entrancePlane, int leaveX, int leaveY, int leavePlane,
			int minPlayers, int maxPlayers, int lobbyWaitTicks,
			int timeLimitTicks, List<MinigameWaveDefinition> waves,
			MinigameScoreDefinition score, Value onStart, Value onWaveStart,
			Value onWaveComplete, Value onTick, Value onComplete,
			Value onWipe, String source, int schemaVersion) {
		this.id = id;
		this.name = name;
		this.command = command;
		this.lobbyAreaId = lobbyAreaId;
		this.arenaAreaId = arenaAreaId;
		this.lobbyBounds = lobbyBounds;
		this.arenaBounds = arenaBounds;
		this.entranceX = entranceX;
		this.entranceY = entranceY;
		this.entrancePlane = entrancePlane;
		this.leaveX = leaveX;
		this.leaveY = leaveY;
		this.leavePlane = leavePlane;
		this.minPlayers = minPlayers;
		this.maxPlayers = maxPlayers;
		this.lobbyWaitTicks = lobbyWaitTicks;
		this.timeLimitTicks = timeLimitTicks;
		this.waves = Collections.unmodifiableList(
				new ArrayList<MinigameWaveDefinition>(waves));
		this.score = score;
		this.onStart = onStart;
		this.onWaveStart = onWaveStart;
		this.onWaveComplete = onWaveComplete;
		this.onTick = onTick;
		this.onComplete = onComplete;
		this.onWipe = onWipe;
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String command() {
		return command;
	}

	public String lobbyAreaId() {
		return lobbyAreaId;
	}

	public String arenaAreaId() {
		return arenaAreaId;
	}

	public AreaBounds lobbyBounds() {
		return lobbyBounds;
	}

	public AreaBounds arenaBounds() {
		return arenaBounds;
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

	public int leaveX() {
		return leaveX;
	}

	public int leaveY() {
		return leaveY;
	}

	public int leavePlane() {
		return leavePlane;
	}

	public int minPlayers() {
		return minPlayers;
	}

	public int maxPlayers() {
		return maxPlayers;
	}

	public int lobbyWaitTicks() {
		return lobbyWaitTicks;
	}

	public int timeLimitTicks() {
		return timeLimitTicks;
	}

	public List<MinigameWaveDefinition> waves() {
		return waves;
	}

	public MinigameScoreDefinition score() {
		return score;
	}

	public Value onStart() {
		return onStart;
	}

	public Value onWaveStart() {
		return onWaveStart;
	}

	public Value onWaveComplete() {
		return onWaveComplete;
	}

	public Value onTick() {
		return onTick;
	}

	public Value onComplete() {
		return onComplete;
	}

	public Value onWipe() {
		return onWipe;
	}

	public String source() {
		return source;
	}

	public int schemaVersion() {
		return schemaVersion;
	}
}
