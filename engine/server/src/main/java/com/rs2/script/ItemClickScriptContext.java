package com.rs2.script;

import org.graalvm.polyglot.HostAccess;

/** Invocation metadata for an inventory item option. */
public final class ItemClickScriptContext extends ScriptContext {

	@HostAccess.Export
	public final ScriptedItem item;
	@HostAccess.Export
	public final int slot;

	public ItemClickScriptContext(ScriptedPlayer player, ScriptedItem item, int slot, String action) {
		super(player, item, action);
		this.item = item;
		this.slot = slot;
	}
}
