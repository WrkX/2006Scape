package com.rs2.net.packets.impl;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.apollo.cache.def.ItemDefinition;
import org.apollo.cache.def.ObjectDefinition;

import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.net.Packet;
import com.rs2.script.ScriptEntityLimits;
import com.rs2.script.world.ScriptEncounterService;

/**
 * Universal pre-dispatch validation shared by script-authoritative packet
 * routes. The action-lock hook is intentionally narrow; WP3 replaces its
 * default-unlocked production source with service-owned lock state.
 */
final class ScriptInteractionGate {

	private static final Map<Player, Boolean> TEST_LOCKS =
			Collections.synchronizedMap(new WeakHashMap<Player, Boolean>());

	static boolean hasExactPayload(Packet packet, int length) {
		return packet != null && packet.getLength() == length
				&& packet.getPayload().readableBytes() == length;
	}

	static boolean fullyDecoded(Packet packet) {
		return packet != null && packet.getPayload().readableBytes() == 0;
	}

	static boolean isCommonPlayerValid(Player player) {
		if (player == null || player.playerId < 0
				|| player.playerId >= PlayerHandler.players.length
				|| PlayerHandler.players[player.playerId] != player) {
			return false;
		}
		return player.initialized && player.isActive && !player.disconnected
				&& !player.isDead && player.respawnTimer <= 0
				&& !player.isTeleporting && player.teleTimer <= 0
				&& player.getOutStream() != null;
	}

	static boolean isLiveTarget(Player player) {
		return player != null && player.initialized && player.isActive
				&& !player.disconnected && !player.isDead;
	}

	static boolean isActionLocked(Player player) {
		return ScriptEncounterService.getInstance().isActionLocked(player)
				|| Boolean.TRUE.equals(TEST_LOCKS.get(player));
	}

	static void setActionLockedForTest(Player player, boolean locked) {
		if (locked) {
			TEST_LOCKS.put(player, Boolean.TRUE);
		} else {
			TEST_LOCKS.remove(player);
		}
	}

	static boolean validCoordinate(int coordinate) {
		return coordinate >= 0 && coordinate <= 16383;
	}

	static boolean validInventorySlot(int slot) {
		return slot >= 0 && slot < 28;
	}

	static boolean hasExactInventoryItem(Player player, int slot, int itemId) {
		return validInventorySlot(slot) && player.playerItems[slot] - 1 == itemId
				&& player.playerItemsN[slot] > 0;
	}

	static boolean isDefinitionBackedItem(int itemId) {
		if (itemId < 0 || itemId > ScriptEntityLimits.MAX_ITEM_ID) {
			return false;
		}
		try {
			return ItemDefinition.lookup(itemId) != null;
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	static boolean isDefinitionBackedObject(int objectId) {
		if (objectId < 0 || objectId > ScriptEntityLimits.MAX_OBJECT_ID) {
			return false;
		}
		try {
			return ObjectDefinition.lookup(objectId) != null;
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	static int chebyshevDistance(Player player, int x, int y) {
		return Math.max(Math.abs(player.absX - x), Math.abs(player.absY - y));
	}

	private ScriptInteractionGate() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
