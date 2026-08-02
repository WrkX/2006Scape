package com.rs2.script.context;

import com.rs2.script.ScriptedPosition;
import com.rs2.script.snapshot.ScriptPlayerSnapshot;

import org.graalvm.polyglot.HostAccess;

public final class PlayerDeathScriptContext {
	@HostAccess.Export public final ScriptPlayerSnapshot player;
	@HostAccess.Export public final ScriptPlayerSnapshot killer;
	@HostAccess.Export public final ScriptedPosition position;
	@HostAccess.Export public final String action = "death";

	public PlayerDeathScriptContext(ScriptPlayerSnapshot player,
			ScriptPlayerSnapshot killer, ScriptedPosition position) {
		this.player = player;
		this.killer = killer;
		this.position = position;
	}
}
