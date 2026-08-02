package com.rs2.script.context;

import com.rs2.script.ScriptedPlayer;

import org.graalvm.polyglot.HostAccess;

public final class ButtonScriptContext {
	@HostAccess.Export public final ScriptedPlayer player;
	@HostAccess.Export public final Object target = null;
	@HostAccess.Export public final String action = "button";
	@HostAccess.Export public final int buttonId;

	public ButtonScriptContext(ScriptedPlayer player, int buttonId) {
		this.player = player;
		this.buttonId = buttonId;
	}
}
