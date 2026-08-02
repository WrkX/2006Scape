package com.rs2.game.items;

import com.rs2.game.players.Player;

/**
 * @author somedude, credits to Galkon for item weights
 */
public class Weight {

    /**
     * Calculates the weight when doing actions
     *
     * @param c
     * @param item
     * @param action
     *            - deleteItem, addItem.
     */
    public static void calcWeight(Player c, int item, String action) {
    	double weight = ItemDefinitions.getWeight(item);
        if (action.equalsIgnoreCase("deleteItem")) {
            if (weight > 99.20) {
                c.weight -= weight / 100;
                if (c.weight < 0) {
                    c.weight = 0.0;
                }
                c.getPacketSender().writeWeight((int) c.weight);
                return;
            }
            c.weight -= weight / 10;
            if (c.weight < 0) {
                c.weight = 0.0;
            }
            c.getPacketSender().writeWeight((int) c.weight);
        } else if (action.equalsIgnoreCase("addItem")) {
            if (weight > 99.20) {
                c.weight += weight / 100;
                c.getPacketSender().writeWeight((int) c.weight);
                return;
            }
            c.weight += weight / 10;
            c.getPacketSender().writeWeight((int) c.weight);
        }
    }

    /**
     * Updates the weight for inventory and equipment.
     *
     * @param player
     */
    public static void updateWeight(Player player) {
        if (player != null) {
            player.weight = calculateWeight(player.playerItems,
                    player.playerEquipment);
            player.getPacketSender().writeWeight((int) player.weight);
        }
    }

    /**
     * Pure canonical calculation. Inventory slots store {@code itemId + 1},
     * while equipment slots store the raw item id.
     */
    public static double calculateWeight(int[] inventory, int[] equipment) {
        double total = 0.0;
        for (int storedItem : inventory) {
            if (storedItem > 0) {
                total += normalizedWeight(storedItem - 1);
            }
        }
        for (int itemId : equipment) {
            if (itemId >= 0) {
                total += itemId == 88 ? -4.5 : normalizedWeight(itemId);
            }
        }
        return total;
    }

    private static double normalizedWeight(int itemId) {
        double weight = ItemDefinitions.getWeight(itemId);
        return weight > 99.20 ? weight / 100 : weight / 10;
    }
}
