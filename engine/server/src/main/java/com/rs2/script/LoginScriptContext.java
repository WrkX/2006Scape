package com.rs2.script;

/** Lifecycle context emitted after a player finishes engine initialization. */
public final class LoginScriptContext extends ScriptContext {

	public LoginScriptContext(ScriptedPlayer player) {
		super(player, null, "login");
	}
}
