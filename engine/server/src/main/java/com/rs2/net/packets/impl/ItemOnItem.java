package com.rs2.net.packets.impl;

import com.rs2.game.items.UseItem;
import com.rs2.game.players.Player;
import com.rs2.net.Packet;
import com.rs2.net.packets.PacketType;
import com.rs2.script.ItemOnItemScriptContext;
import com.rs2.script.ScriptExecutor;
import com.rs2.script.ScriptHost;
import com.rs2.script.ScriptedItem;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.registries.ItemHandlerRegistry;
import org.graalvm.polyglot.Value;

public class ItemOnItem implements PacketType {

	@Override
	public void processPacket(Player player, Packet packet) {
		if (ScriptInteractionGate.isActionLocked(player)) {
			return;
		}
		int usedWithSlot = packet.readUnsignedWord();
		int itemUsedSlot = packet.readUnsignedWordA();
		if (usedWithSlot < 0 || usedWithSlot >= player.playerItems.length
				|| itemUsedSlot < 0 || itemUsedSlot >= player.playerItems.length) {
			return;
		}
		int useWith = player.playerItems[usedWithSlot] - 1;
		int itemUsed = player.playerItems[itemUsedSlot] - 1;
		if (!player.getItemAssistant().playerHasItem(useWith, 1, usedWithSlot)|| !player.getItemAssistant().playerHasItem(itemUsed, 1, itemUsedSlot)) {
			return;
		}
		player.endCurrentTask();
		if (executeScriptItemOnItem(player, itemUsed, itemUsedSlot, useWith, usedWithSlot)) {
			return;
		}
		UseItem.itemOnItem(player, itemUsed, useWith);
	}

	static boolean executeScriptItemOnItem(Player player, int usedItemId, int usedSlot,
			int targetItemId, int targetSlot) {
		return ScriptHost.getInstance().dispatchActive(
				state -> ItemHandlerRegistry.getItemOnItem(
						state, usedItemId, targetItemId),
				(generation, handler) -> ScriptExecutor.execute(
						handler, "item-on-item",
						usedItemId + ":" + targetItemId, "item-on-item",
						new ItemOnItemScriptContext(
								new ScriptedPlayer(player, generation),
								ScriptedItem.byId(usedItemId), usedSlot,
								ScriptedItem.byId(targetItemId), targetSlot)))
				== ScriptHost.DispatchResult.CONSUMED;
	}

}
