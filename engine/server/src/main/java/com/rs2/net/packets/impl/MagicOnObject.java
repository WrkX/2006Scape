package com.rs2.net.packets.impl;

import com.rs2.game.content.skills.crafting.OrbCharging;
import com.rs2.game.players.Player;
import com.rs2.net.Packet;
import com.rs2.net.packets.PacketType;
import com.rs2.script.ScriptExecutor;
import com.rs2.script.ScriptHost;
import com.rs2.script.ScriptedObject;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.context.MagicOnObjectScriptContext;
import com.rs2.script.registries.InteractionHandlerRegistry;
import com.rs2.world.ResolvedWorldObject;
import com.rs2.world.WorldObjectService;

public class MagicOnObject implements PacketType {

	@Override
	public void processPacket(Player player, Packet packet) {
		if (!ScriptInteractionGate.hasExactPayload(packet, 8)) {
			return;
		}
		int x = packet.readSignedWordBigEndian();
		int magicId = packet.readUnsignedWord();
		int y = packet.readUnsignedWordA();
		int objectId = packet.readSignedWordBigEndian();
		if (!ScriptInteractionGate.fullyDecoded(packet)
				|| !ScriptInteractionGate.isCommonPlayerValid(player)
				|| magicId < 0 || magicId > 65535
				|| !ScriptInteractionGate.isDefinitionBackedObject(objectId)
				|| !ScriptInteractionGate.validCoordinate(x)
				|| !ScriptInteractionGate.validCoordinate(y)
				|| ScriptInteractionGate.chebyshevDistance(player, x, y) > 5) {
			return;
		}
		ResolvedWorldObject target = WorldObjectService.getInstance()
				.resolvePacketObject(player, objectId, x, y, player.heightLevel);
		if (target == null
				|| ScriptInteractionGate.isActionLocked(player)
				|| executeScriptMagicOnObject(player, magicId, target)) {
			return;
		}
		player.turnPlayerTo(x, y);
		switch (objectId) {
		case 2153:
		case 2152:
		case 2151:
		case 2150:
			OrbCharging.chargeOrbs(player, magicId, objectId);
			break;
		}
	}

	static boolean executeScriptMagicOnObject(Player player, int spellId,
			ResolvedWorldObject target) {
		int objectId = target.getObject().getObjectId();
		return ScriptHost.getInstance().dispatchActive(
				state -> InteractionHandlerRegistry.getMagicOnObjectRecord(
						state, spellId, objectId),
				(generation, route) -> ScriptExecutor.executeRoute(
						route,
						"magic-on-object", spellId + ":" + objectId,
						"magic-on-object", new MagicOnObjectScriptContext(
								new ScriptedPlayer(player, generation),
								new ScriptedObject(target.getObject()), spellId)))
				== ScriptHost.DispatchResult.CONSUMED;
	}

}
