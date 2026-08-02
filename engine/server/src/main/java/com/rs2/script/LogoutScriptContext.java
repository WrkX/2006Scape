package com.rs2.script;

/** Lifecycle context emitted once when an initialized player is removed. */
public final class LogoutScriptContext extends ScriptContext {

	public LogoutScriptContext(ScriptedPlayer player) {
		super(player, null, "logout");
	}
}
