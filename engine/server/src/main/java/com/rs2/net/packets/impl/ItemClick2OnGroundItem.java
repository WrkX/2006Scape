package com.rs2.net.packets.impl;

import com.rs2.GameEngine;
import com.rs2.game.content.skills.firemaking.Firemaking;
import com.rs2.game.content.skills.firemaking.LogData;
import com.rs2.game.players.Player;
import com.rs2.net.Packet;
import com.rs2.net.packets.PacketType;
import com.rs2.world.GroundItemRef;

public class ItemClick2OnGroundItem implements PacketType {

	@Override
	public void processPacket(Player player, Packet packet) {
		if (!ScriptInteractionGate.hasExactPayload(packet, 6)) {
			return;
		}
		final int itemX = packet.readSignedWordBigEndian();
		final int itemY = packet.readSignedWordBigEndianA();
		final int itemId = packet.readUnsignedWordA();
		if (!ScriptInteractionGate.fullyDecoded(packet)
				|| !ScriptInteractionGate.isCommonPlayerValid(player)
				|| !ScriptInteractionGate.validCoordinate(itemX)
				|| !ScriptInteractionGate.validCoordinate(itemY)
				|| !ScriptInteractionGate.isDefinitionBackedItem(itemId)
				|| player.absX != itemX || player.absY != itemY) {
			return;
		}
		GroundItemRef target = GameEngine.itemHandler.resolveVisibleGroundItem(
				player, itemId, itemX, itemY, player.heightLevel);
		if (target == null || ScriptInteractionGate.isActionLocked(player)) {
			return;
		}
		System.out.println("ItemClick2OnGroundItem - " + player.playerName + " - " + itemId + " - " + itemX + " - " + itemY);
		// Reset position for the telekinetic guardian statue
		if (itemId == 6888) {
			player.getMageTrainingArena().telekinetic.resetStatue(itemX, itemY);
			return;
		}
		player.endCurrentTask();
		for (LogData l : LogData.values()) {
			if (itemId == l.getLogId()) {
				Firemaking.attemptFire(player, 590, itemId, itemX, itemY, true,
						target);
				return;
			}
		}
	}
}
