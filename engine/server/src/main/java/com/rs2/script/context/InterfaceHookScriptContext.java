package com.rs2.script.context;

import com.rs2.script.ScriptedPlayer;

import org.graalvm.polyglot.HostAccess;

/** Context passed to {@code defineInterfaceHook} open/close callbacks. */
public final class InterfaceHookScriptContext {
	@HostAccess.Export public final ScriptedPlayer player;
	@HostAccess.Export public final Object target = null;
	@HostAccess.Export public final String action;
	@HostAccess.Export public final int interfaceId;
	@HostAccess.Export public final String hookId;

	public InterfaceHookScriptContext(ScriptedPlayer player, String action,
			int interfaceId, String hookId) {
		this.player = player;
		this.action = action;
		this.interfaceId = interfaceId;
		this.hookId = hookId;
	}
}
