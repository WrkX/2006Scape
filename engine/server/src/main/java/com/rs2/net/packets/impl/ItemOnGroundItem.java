package com.rs2.net.packets.impl;

import com.rs2.GameEngine;
import com.rs2.game.content.skills.firemaking.Firemaking;
import com.rs2.game.players.Player;
import com.rs2.net.Packet;
import com.rs2.net.packets.PacketType;
import com.rs2.script.ScriptExecutor;
import com.rs2.script.ScriptHost;
import com.rs2.script.ScriptedGroundItemView;
import com.rs2.script.ScriptedItem;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.ScriptedPosition;
import com.rs2.script.context.ItemOnGroundItemScriptContext;
import com.rs2.script.registries.InteractionHandlerRegistry;
import com.rs2.world.GroundItemRef;

public class ItemOnGroundItem implements PacketType {

	@Override
	public void processPacket(Player player, Packet packet) {
		if (!ScriptInteractionGate.hasExactPayload(packet, 12)) {
			return;
		}
		packet.readSignedWordBigEndian();
		int itemUsed = packet.readSignedWordA();
		int groundItem = packet.readUnsignedWord();
		int gItemY = packet.readSignedWordA();
		int itemUsedSlot = packet.readSignedWordBigEndianA();
		int gItemX = packet.readUnsignedWord();
		if (!ScriptInteractionGate.fullyDecoded(packet)
				|| !ScriptInteractionGate.isCommonPlayerValid(player)
				|| !ScriptInteractionGate.isDefinitionBackedItem(itemUsed)
				|| !ScriptInteractionGate.isDefinitionBackedItem(groundItem)
				|| !ScriptInteractionGate.validCoordinate(gItemX)
				|| !ScriptInteractionGate.validCoordinate(gItemY)
				|| !ScriptInteractionGate.hasExactInventoryItem(
						player, itemUsedSlot, itemUsed)) {
			return;
		}
		GroundItemRef target = GameEngine.itemHandler.resolveVisibleGroundItem(
				player, groundItem, gItemX, gItemY, player.heightLevel);
		if (target == null
				|| ScriptInteractionGate.chebyshevDistance(
						player, gItemX, gItemY) > 1
				|| ScriptInteractionGate.isActionLocked(player)) {
			return;
		}
		if (executeScriptItemOnGroundItem(player, itemUsed, itemUsedSlot,
				target)) {
			return;
		}

		player.endCurrentTask();

		switch (itemUsed) {
		case 590:
		case 7331:
		case 7330:
		case 7329:
			Firemaking.attemptFire(player, itemUsed, groundItem, gItemX, gItemY,
					true, target);
			break;

		default:
			if (player.playerRights == 3) {
				System.out.println("ItemUsed " + itemUsed + " on Ground Item "
						+ groundItem);
			}
			break;
		}
	}

	static boolean executeScriptItemOnGroundItem(Player player, int itemId,
			int slot, GroundItemRef target) {
		return ScriptHost.getInstance().dispatchActive(
				state -> InteractionHandlerRegistry.getItemOnGroundItemRecord(
						state, itemId, target.getItemId()),
				(generation, route) -> ScriptExecutor.executeRoute(
						route,
						"item-on-ground-item",
						itemId + ":" + target.getItemId(),
						"item-on-ground-item",
						new ItemOnGroundItemScriptContext(
								new ScriptedPlayer(player, generation),
								new ScriptedGroundItemView(
										String.valueOf(target.getToken()),
										target.getItemId(), target.getAmount(),
										new ScriptedPosition(target.getX(),
												target.getY(), target.getPlane()),
										target.isPrivateToPlayer()),
								ScriptedItem.byId(itemId), slot)))
				== ScriptHost.DispatchResult.CONSUMED;
	}

}
