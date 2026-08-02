package com.rs2.net.packets.impl;

import com.rs2.event.impl.MagicOnItemEvent;
import com.rs2.game.players.Player;
import com.rs2.net.Packet;
import com.rs2.net.packets.PacketType;
import com.rs2.script.ScriptExecutor;
import com.rs2.script.ScriptHost;
import com.rs2.script.ScriptedItem;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.context.MagicOnItemScriptContext;
import com.rs2.script.registries.InteractionHandlerRegistry;

/**
 * Magic on items
 **/

public class MagicOnItems implements PacketType {

	@Override
	public void processPacket(Player player, Packet packet) {
		if (!ScriptInteractionGate.hasExactPayload(packet, 8)) {
			return;
		}
		int slot = packet.readSignedWord();
		int itemId = packet.readSignedWordA();
		packet.readSignedWord();
		int spellId = packet.readSignedWordA();
		if (!ScriptInteractionGate.fullyDecoded(packet)
				|| !ScriptInteractionGate.isCommonPlayerValid(player)
				|| !ScriptInteractionGate.isDefinitionBackedItem(itemId)
				|| spellId < 0 || spellId > 65535
				|| !ScriptInteractionGate.hasExactInventoryItem(
						player, slot, itemId)
				|| ScriptInteractionGate.isActionLocked(player)) {
			return;
		}
		if (executeScriptMagicOnItem(player, spellId, itemId, slot)) {
			return;
		}
		player.endCurrentTask();
		player.usingMagic = true;
		player.getPlayerAssistant().magicOnItems(slot, itemId, spellId);
		player.post(new MagicOnItemEvent(itemId, slot, spellId));
		player.usingMagic = false;

	}

	static boolean executeScriptMagicOnItem(Player player, int spellId,
			int itemId, int slot) {
		return ScriptHost.getInstance().dispatchActive(
				state -> InteractionHandlerRegistry.getMagicOnItem(
						state, spellId, itemId),
				(generation, handler) -> ScriptExecutor.execute(handler,
						"magic-on-item", spellId + ":" + itemId,
						"magic-on-item", new MagicOnItemScriptContext(
								new ScriptedPlayer(player, generation),
								ScriptedItem.byId(itemId), spellId, slot)))
				== ScriptHost.DispatchResult.CONSUMED;
	}

}
