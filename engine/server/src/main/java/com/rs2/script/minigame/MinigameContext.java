package com.rs2.script.minigame;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.HostAccess;

import com.rs2.script.ScriptArray;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.ScriptedPosition;
import com.rs2.script.world.ScriptEncounterHandle;

/** Narrow runtime context passed to minigame callbacks. */
public final class MinigameContext {

	private final String minigameId;
	private final String waveId;
	private final int waveIndex;
	private final int elapsedTicks;
	private final ScriptedPosition position;
	private final List<ScriptedPlayer> participants;
	private final MinigameScoreDefinition score;

	@HostAccess.Export
	public final ScriptEncounterHandle encounter;

	public MinigameContext(ScriptEncounterHandle encounter, String minigameId,
			String waveId, int waveIndex, int elapsedTicks,
			ScriptedPosition position, List<ScriptedPlayer> participants,
			MinigameScoreDefinition score) {
		this.encounter = encounter;
		this.minigameId = minigameId;
		this.waveId = waveId;
		this.waveIndex = waveIndex;
		this.elapsedTicks = elapsedTicks;
		this.position = position;
		this.participants = new ArrayList<ScriptedPlayer>(participants);
		this.score = score;
	}

	@HostAccess.Export
	public String id() {
		return minigameId;
	}

	@HostAccess.Export
	public String waveId() {
		return waveId;
	}

	@HostAccess.Export
	public int waveIndex() {
		return waveIndex;
	}

	@HostAccess.Export
	public int elapsedTicks() {
		return elapsedTicks;
	}

	@HostAccess.Export
	public ScriptedPosition position() {
		return position;
	}

	@HostAccess.Export
	public ScriptArray participants() {
		return new ScriptArray(participants.toArray());
	}

	@HostAccess.Export
	public boolean announce(String text) {
		if (text == null) {
			return false;
		}
		for (ScriptedPlayer member : participants) {
			member.message(text);
		}
		return !participants.isEmpty();
	}

	@HostAccess.Export
	public double score(ScriptedPlayer player) {
		if (score == null || player == null) {
			return 0D;
		}
		return player.state(score.namespace())
				.getNumberOr(score.key(), 0D);
	}

	@HostAccess.Export
	public boolean addScore(ScriptedPlayer player, double delta) {
		if (score == null || player == null || !Double.isFinite(delta)) {
			return false;
		}
		double next = score(player) + delta;
		return player.state(score.namespace()).setNumber(score.key(), next);
	}
}
