package com.rs2.script.context;

import com.rs2.script.ScriptedNpc;
import com.rs2.script.ScriptedPlayer;

import org.graalvm.polyglot.HostAccess;

public final class MagicOnNpcScriptContext {
	@HostAccess.Export public final ScriptedPlayer player;
	@HostAccess.Export public final ScriptedNpc target;
	@HostAccess.Export public final String action = "magic-on-npc";
	@HostAccess.Export public final int spellId;

	public MagicOnNpcScriptContext(ScriptedPlayer player, ScriptedNpc target,
			int spellId) {
		this.player = player;
		this.target = target;
		this.spellId = spellId;
	}
}
