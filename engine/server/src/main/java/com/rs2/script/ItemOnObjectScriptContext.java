package com.rs2.script;

import org.graalvm.polyglot.HostAccess;

/** Invocation metadata for using an inventory item on a world object. */
public final class ItemOnObjectScriptContext extends ScriptContext {

	@HostAccess.Export
	public final ScriptedItem item;
	@HostAccess.Export
	public final int slot;

	public ItemOnObjectScriptContext(ScriptedPlayer player, ScriptedItem item, int slot,
			ScriptedObject target) {
		super(player, target, "item-on-object");
		this.item = item;
		this.slot = slot;
	}
}
