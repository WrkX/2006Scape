package com.rs2.script.world;

import com.rs2.script.ScriptedPosition;
import com.rs2.script.snapshot.ScriptPlayerSnapshot;

/** Immutable hand-off between core player-death begin and completion. */
public final class ScriptPlayerDeathTicket {

	private final long transitionToken;
	private final ScriptPlayerSnapshot player;
	private final ScriptPlayerSnapshot killer;
	private final ScriptedPosition position;

	ScriptPlayerDeathTicket(long transitionToken, ScriptPlayerSnapshot player,
			ScriptPlayerSnapshot killer, ScriptedPosition position) {
		this.transitionToken = transitionToken;
		this.player = player;
		this.killer = killer;
		this.position = position;
	}

	public long transitionToken() {
		return transitionToken;
	}

	public ScriptPlayerSnapshot player() {
		return player;
	}

	public ScriptPlayerSnapshot killer() {
		return killer;
	}

	public ScriptedPosition position() {
		return position;
	}
}
