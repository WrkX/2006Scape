package com.rs2.script.context;

import com.rs2.script.ScriptedItem;
import com.rs2.script.ScriptedPlayer;

import org.graalvm.polyglot.HostAccess;

public final class MagicOnItemScriptContext {
	@HostAccess.Export public final ScriptedPlayer player;
	@HostAccess.Export public final ScriptedItem target;
	@HostAccess.Export public final String action = "magic-on-item";
	@HostAccess.Export public final int spellId;
	@HostAccess.Export public final int slot;

	public MagicOnItemScriptContext(ScriptedPlayer player, ScriptedItem target,
			int spellId, int slot) {
		this.player = player;
		this.target = target;
		this.spellId = spellId;
		this.slot = slot;
	}
}
