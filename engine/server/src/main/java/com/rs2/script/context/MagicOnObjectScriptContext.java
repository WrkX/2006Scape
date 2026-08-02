package com.rs2.script.context;

import com.rs2.script.ScriptedObject;
import com.rs2.script.ScriptedPlayer;

import org.graalvm.polyglot.HostAccess;

public final class MagicOnObjectScriptContext {
	@HostAccess.Export public final ScriptedPlayer player;
	@HostAccess.Export public final ScriptedObject target;
	@HostAccess.Export public final String action = "magic-on-object";
	@HostAccess.Export public final int spellId;

	public MagicOnObjectScriptContext(ScriptedPlayer player, ScriptedObject target,
			int spellId) {
		this.player = player;
		this.target = target;
		this.spellId = spellId;
	}
}
