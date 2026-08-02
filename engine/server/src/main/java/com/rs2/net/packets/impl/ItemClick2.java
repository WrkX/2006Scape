package com.rs2.net.packets.impl;

import com.rs2.event.impl.ItemSecondClickEvent;
import com.rs2.game.items.impl.HandleEmpty;
import com.rs2.game.players.Player;
import com.rs2.net.Packet;
import com.rs2.net.packets.PacketType;

/**
 * Item Click 2 Or Alternative Item Option 1
 * @author Ryan / Lmctruck30 Proper Streams
 */

public class ItemClick2 implements PacketType {

	@Override
	public void processPacket(Player player, Packet packet) {
		if (ScriptInteractionGate.isActionLocked(player)) {
			return;
		}
		int itemId = packet.readSignedWordA();
		int itemSlot = packet.readSignedWordBigEndianA();
		packet.readSignedWordBigEndianA();

		if (itemSlot < 0 || itemSlot >= player.playerItems.length
				|| !player.getItemAssistant().playerHasItem(itemId, 1, itemSlot)) {
			return;
		}

		player.endCurrentTask();
		if (ClickItem.executeScriptItemClick(player, itemId, itemSlot, "second")) {
			return;
		}
		player.post(new ItemSecondClickEvent(itemId));

		if (HandleEmpty.canEmpty(player, itemId)) {
			HandleEmpty.handleEmptyItem(player, itemId, HandleEmpty.filledToEmpty(player, itemId));
		}

	}

}
