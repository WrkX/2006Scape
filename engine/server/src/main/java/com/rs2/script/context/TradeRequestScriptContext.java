package com.rs2.script.context;

import org.graalvm.polyglot.HostAccess;

import com.rs2.script.ScriptedPlayer;

/** Gate context for trade initiation; scripts may deny but cannot mutate offers. */
public final class TradeRequestScriptContext {

	@HostAccess.Export
	public final ScriptedPlayer requester;
	@HostAccess.Export
	public final ScriptedPlayer target;
	@HostAccess.Export
	public final String action = "trade-request";

	private boolean allowed = true;
	private String denialMessage;

	public TradeRequestScriptContext(ScriptedPlayer requester,
			ScriptedPlayer target) {
		this.requester = requester;
		this.target = target;
	}

	/**
	 * Blocks trade initiation and optionally sends a message to the requester.
	 * Returns {@code false} so handlers can use {@code return ctx.deny(...)}.
	 */
	@HostAccess.Export
	public boolean deny(String message) {
		allowed = false;
		if (message != null) {
			String trimmed = message.trim();
			if (!trimmed.isEmpty()) {
				denialMessage = trimmed;
			}
		}
		return false;
	}

	public boolean isAllowed() {
		return allowed;
	}

	public String denialMessage() {
		return denialMessage;
	}
}
