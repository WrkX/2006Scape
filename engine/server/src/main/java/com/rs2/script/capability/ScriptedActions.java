package com.rs2.script.capability;

import org.graalvm.polyglot.HostAccess;

import com.rs2.game.players.Player;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.script.world.ScriptLockHandle;

/** WP3 action-lock capability. Other action capabilities are added later. */
public final class ScriptedActions {

	private final Player player;
	private final long generation;
	private final long facadeEpoch;

	public ScriptedActions(Player player, long generation, long facadeEpoch) {
		this.player = player;
		this.generation = generation;
		this.facadeEpoch = facadeEpoch;
	}

	@HostAccess.Export
	public ScriptLockHandle lock(double ticks) {
		return ScriptEncounterService.getInstance()
				.acquireActionLock(player, generation, facadeEpoch, ticks);
	}
}
