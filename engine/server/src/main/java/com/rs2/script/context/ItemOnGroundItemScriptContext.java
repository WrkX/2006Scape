package com.rs2.script.context;

import com.rs2.script.ScriptedGroundItemView;
import com.rs2.script.ScriptedItem;
import com.rs2.script.ScriptedPlayer;

import org.graalvm.polyglot.HostAccess;

public final class ItemOnGroundItemScriptContext {
	@HostAccess.Export public final ScriptedPlayer player;
	@HostAccess.Export public final ScriptedGroundItemView target;
	@HostAccess.Export public final String action = "item-on-ground-item";
	@HostAccess.Export public final ScriptedItem item;
	@HostAccess.Export public final int slot;

	public ItemOnGroundItemScriptContext(ScriptedPlayer player,
			ScriptedGroundItemView target, ScriptedItem item, int slot) {
		this.player = player;
		this.target = target;
		this.item = item;
		this.slot = slot;
	}
}
