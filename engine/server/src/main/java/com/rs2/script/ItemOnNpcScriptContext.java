package com.rs2.script;

import org.graalvm.polyglot.HostAccess;

/** Invocation metadata for using an inventory item on an NPC. */
public final class ItemOnNpcScriptContext extends ScriptContext {

	@HostAccess.Export
	public final ScriptedItem item;
	@HostAccess.Export
	public final int slot;

	public ItemOnNpcScriptContext(ScriptedPlayer player, ScriptedItem item, int slot,
			ScriptedNpc target) {
		super(player, target, "item-on-npc");
		this.item = item;
		this.slot = slot;
	}
}
