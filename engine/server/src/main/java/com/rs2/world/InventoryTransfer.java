package com.rs2.world;

import com.rs2.game.players.Player;

/**
 * Atomic full-amount inventory transfer used by ground-item pickup paths.
 */
final class InventoryTransfer {

	static boolean addCompletely(Player player, int itemId, int amount) {
		if (amount <= 0) {
			return false;
		}
		int before = player.getItemAssistant().getItemAmount(itemId);
		int[] itemSnapshot = player.playerItems.clone();
		int[] amountSnapshot = player.playerItemsN.clone();
		double weightSnapshot = player.weight;
		try {
			if (player.getItemAssistant().addItem(itemId, amount)
					&& player.getItemAssistant().getItemAmount(itemId) - before == amount) {
				return true;
			}
		} catch (RuntimeException e) {
			// Restore below; a failed pickup must not partially mutate inventory.
		}
		System.arraycopy(itemSnapshot, 0, player.playerItems, 0, itemSnapshot.length);
		System.arraycopy(amountSnapshot, 0, player.playerItemsN, 0, amountSnapshot.length);
		player.weight = weightSnapshot;
		player.getItemAssistant().resetItems(3214);
		player.getPacketSender().writeWeight((int) weightSnapshot);
		return false;
	}

	private InventoryTransfer() {
	}
}
