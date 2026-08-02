package com.rs2.script;

import org.graalvm.polyglot.HostAccess;

/** Direction-preserving metadata for using one inventory item on another. */
public final class ItemOnItemScriptContext extends ScriptContext {

	@HostAccess.Export
	public final ScriptedItem usedItem;
	@HostAccess.Export
	public final int usedSlot;
	@HostAccess.Export
	public final ScriptedItem targetItem;
	@HostAccess.Export
	public final int targetSlot;

	public ItemOnItemScriptContext(ScriptedPlayer player, ScriptedItem usedItem, int usedSlot,
			ScriptedItem targetItem, int targetSlot) {
		super(player, targetItem, "item-on-item");
		this.usedItem = usedItem;
		this.usedSlot = usedSlot;
		this.targetItem = targetItem;
		this.targetSlot = targetSlot;
	}
}
