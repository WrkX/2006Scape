package com.rs2.script.context;

import org.graalvm.polyglot.HostAccess;

import com.rs2.script.ScriptedPlayer;

/** Observe-only context for delivered private messages. */
public final class PrivateMessageScriptContext {

	@HostAccess.Export
	public final ScriptedPlayer sender;
	@HostAccess.Export
	public final ScriptedPlayer recipient;
	@HostAccess.Export
	public final String message;
	@HostAccess.Export
	public final String action = "private-message";

	public PrivateMessageScriptContext(ScriptedPlayer sender,
			ScriptedPlayer recipient, String message) {
		this.sender = sender;
		this.recipient = recipient;
		this.message = message == null ? "" : message;
	}
}
