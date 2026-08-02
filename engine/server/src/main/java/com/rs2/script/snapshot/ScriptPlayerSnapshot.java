package com.rs2.script.snapshot;

import com.rs2.game.players.Player;
import com.rs2.script.ScriptedPosition;

import org.graalvm.polyglot.HostAccess;

/** Immutable player value-copy safe to expose during terminal transitions. */
public final class ScriptPlayerSnapshot {

	private final String username;
	private final ScriptedPosition position;
	private final int combatLevel;
	private final int rights;

	public ScriptPlayerSnapshot(Player player) {
		this(player.playerName,
				new ScriptedPosition(player.absX, player.absY, player.heightLevel),
				player.calculateCombatLevel(), player.playerRights);
	}

	public ScriptPlayerSnapshot(String username, ScriptedPosition position,
			int combatLevel, int rights) {
		this.username = username;
		this.position = position;
		this.combatLevel = combatLevel;
		this.rights = rights;
	}

	@HostAccess.Export
	public String username() {
		return username;
	}

	@HostAccess.Export
	public ScriptedPosition position() {
		return position;
	}

	@HostAccess.Export
	public int combatLevel() {
		return combatLevel;
	}

	@HostAccess.Export
	public int rights() {
		return rights;
	}
}
