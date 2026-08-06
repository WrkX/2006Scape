package com.rs2.script.context;

import com.rs2.script.ScriptedPlayer;

import org.graalvm.polyglot.HostAccess;

public final class MagicOnPlayerScriptContext {
	@HostAccess.Export public final ScriptedPlayer player;
	@HostAccess.Export public final ScriptedPlayer target;
	@HostAccess.Export public final String action = "magic-on-player";
	@HostAccess.Export public final int spellId;

	public MagicOnPlayerScriptContext(ScriptedPlayer player,
			ScriptedPlayer target, int spellId) {
		this.player = player;
		this.target = target;
		this.spellId = spellId;
	}
}
