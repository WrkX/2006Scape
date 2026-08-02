package com.rs2.net.packets.impl;

import com.rs2.game.items.impl.RareProtection;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.net.Packet;
import com.rs2.net.packets.PacketType;
import com.rs2.script.ScriptExecutor;
import com.rs2.script.ScriptHost;
import com.rs2.script.ScriptedItem;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.context.ItemOnPlayerScriptContext;
import com.rs2.script.registries.InteractionHandlerRegistry;
import com.rs2.util.Misc;

/**
 * @author JaydenD12/Jaydennn
 */

public class ItemOnPlayer implements PacketType {

	@Override
	public void processPacket(Player player, Packet packet) {
		if (!ScriptInteractionGate.hasExactPayload(packet, 4)) {
			return;
		}
		int playerId = packet.readUnsignedWord();
		int slot = packet.readSignedWordBigEndian();
		if (!ScriptInteractionGate.fullyDecoded(packet)
				|| !ScriptInteractionGate.isCommonPlayerValid(player)
				|| !ScriptInteractionGate.validInventorySlot(slot)) {
			return;
		}
		int itemId = player.playerItems[slot] - 1;
		if (!ScriptInteractionGate.hasExactInventoryItem(player, slot, itemId)
				|| !ScriptInteractionGate.isDefinitionBackedItem(itemId)
				|| playerId < 1 || playerId >= PlayerHandler.players.length) {
			return;
		}
		Player target = PlayerHandler.players[playerId];
		if (!ScriptInteractionGate.isLiveTarget(target) || target == player
				|| target.heightLevel != player.heightLevel
				|| ScriptInteractionGate.chebyshevDistance(
						player, target.absX, target.absY) > 1
				|| ScriptInteractionGate.isActionLocked(player)) {
			return;
		}
		if (executeScriptItemOnPlayer(player, target, itemId, slot)) {
			return;
		}
		player.endCurrentTask();
		switch (itemId) {

		case 962:
			Player o = target;
			if (RareProtection.CRACKERS) {
				int delete = player.getItemAssistant().getItemAmount(962);
				player.getItemAssistant().deleteItem(962, delete);
				player.getPacketSender().sendMessage("You can't do that!");
				return;
			}
			player.turnPlayerTo(o.absX, o.absY);
			o.turnPlayerTo(player.absX, player.absY);
			o.gfx0(176);
			player.gfx0(176);
			player.startAnimation(451);
			o.startAnimation(451);
			player.getPacketSender().sendMessage(
						"You pull the Christmas Cracker...");
			o.getPacketSender().sendMessage(
					player.playerName.toUpperCase() + " need your help... You pull the Christmas Cracker...");
			player.getItemAssistant().deleteItem(962, 1);
			if (Misc.random(3) == 1) {
				o.forcedText = "Yay! I got the Cracker!";
				o.forcedChatUpdateRequired = true;
				o.getItemAssistant().addItem(1038 + Misc.random(5) * 2, 1);
			} else {
				player.forcedText = "Yay! I got the Cracker!";
				player.forcedChatUpdateRequired = true;
				player.getItemAssistant().addItem(1038 + Misc.random(5) * 2, 1);
			}
			break;
		default:
			player.getPacketSender().sendMessage("Nothing interesting happens.");
			break;
		}
	}

	static boolean executeScriptItemOnPlayer(Player player, Player target,
			int itemId, int slot) {
		return ScriptHost.getInstance().dispatchActive(
				state -> InteractionHandlerRegistry.getItemOnPlayer(state, itemId),
				(generation, handler) -> ScriptExecutor.execute(handler,
						"item-on-player", String.valueOf(itemId),
						"item-on-player", new ItemOnPlayerScriptContext(
								new ScriptedPlayer(player, generation),
								new ScriptedPlayer(target, generation),
								ScriptedItem.byId(itemId), slot)))
				== ScriptHost.DispatchResult.CONSUMED;
	}
}
