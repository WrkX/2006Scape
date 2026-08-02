package com.rs2.script;

import org.graalvm.polyglot.HostAccess;

/** Immutable metadata for a completed ground-item transfer. */
public final class ItemPickupScriptContext extends ScriptContext {

	@HostAccess.Export
	public final ScriptedItem item;
	@HostAccess.Export
	public final int amount;
	@HostAccess.Export
	public final ScriptedPosition position;

	public ItemPickupScriptContext(ScriptedPlayer player, ScriptedItem item,
			int amount, ScriptedPosition position) {
		super(player, item, "pickup");
		this.item = item;
		this.amount = amount;
		this.position = position;
	}
}
