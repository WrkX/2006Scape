package com.rs2.script.capability;

import org.graalvm.polyglot.HostAccess;

import com.rs2.game.items.ItemAssistant;
import com.rs2.game.items.ItemConstants;
import com.rs2.game.players.Player;
import com.rs2.script.BridgeValidation;
import com.rs2.script.ScriptEntityLimits;
import com.rs2.script.world.ScriptEncounterService;

/** Equipment view with equip/unequip mutation through ItemAssistant. */
public final class ScriptedEquipment {

    private final Player player;
    private final long generation;
    private final long facadeEpoch;

    public ScriptedEquipment(Player player, long generation, long facadeEpoch) {
        this.player = player;
        this.generation = generation;
        this.facadeEpoch = facadeEpoch;
    }

    @HostAccess.Export
    public Integer get(String slot) {
        int index = index(slot);
        if (index < 0 || !live()) {
            return null;
        }
        int item = player.playerEquipment[index];
        return item <= 0 ? null : Integer.valueOf(item);
    }

    @HostAccess.Export
    public int amount(String slot) {
        int index = index(slot);
        if (index < 0 || !live()) {
            return 0;
        }
        int item = player.playerEquipment[index];
        if (item <= 0) {
            return 0;
        }
        int amount = player.playerEquipmentN[index];
        return amount <= 0 ? 1 : amount;
    }

    /**
     * Equips one inventory item id through the host wear pipeline.
     *
     * @return {@code true} when the item ends up equipped in its target slot
     */
    @HostAccess.Export
    public boolean equip(double itemIdValue) {
        Integer itemId = BridgeValidation.integral(itemIdValue, 1,
                ScriptEntityLimits.MAX_ITEM_ID);
        if (itemId == null || !canMutate()) {
            return false;
        }
        ItemAssistant items = player.getItemAssistant();
        int inventorySlot = items.getItemSlot(itemId.intValue());
        if (inventorySlot < 0) {
            return false;
        }
        if (!items.wearItem(itemId.intValue(), inventorySlot)) {
            return false;
        }
        int targetSlot = com.rs2.game.items.ItemData.targetSlots[itemId.intValue()];
        return player.playerEquipment[targetSlot] == itemId.intValue();
    }

    /**
     * Unequips the item in one canonical slot name into the inventory.
     *
     * @return {@code true} when the slot is empty afterwards
     */
    @HostAccess.Export
    public int bonus(double indexValue) {
        Integer index = BridgeValidation.integral(indexValue, 0, 11);
        if (index == null || !live()) {
            return 0;
        }
        ItemAssistant items = player.getItemAssistant();
        items.resetBonus();
        items.getBonus();
        return player.playerBonus[index.intValue()];
    }

    @HostAccess.Export
    public String bonusName(double indexValue) {
        Integer index = BridgeValidation.integral(indexValue, 0, 11);
        if (index == null) {
            return null;
        }
        return player.getItemAssistant().BONUS_NAMES[index.intValue()];
    }

    @HostAccess.Export
    public boolean unequip(String slot) {
        int index = index(slot);
        if (index < 0 || !canMutate()) {
            return false;
        }
        if (player.playerEquipment[index] <= 0) {
            return true;
        }
        player.getItemAssistant().removeItem(index);
        return player.playerEquipment[index] <= 0;
    }

    private boolean live() {
        return ScriptEncounterService.getInstance().canUseFacade(player,
                generation, facadeEpoch, false);
    }

    private boolean canMutate() {
        return ScriptEncounterService.getInstance().canMutate(player,
                generation, facadeEpoch);
    }

    private static int index(String raw) {
        if (raw == null) return -1;
        String slot = raw.toLowerCase(java.util.Locale.ROOT);
        if ("hat".equals(slot)) return ItemConstants.HAT;
        if ("cape".equals(slot)) return ItemConstants.CAPE;
        if ("amulet".equals(slot)) return ItemConstants.AMULET;
        if ("weapon".equals(slot)) return ItemConstants.WEAPON;
        if ("chest".equals(slot)) return ItemConstants.CHEST;
        if ("shield".equals(slot)) return ItemConstants.SHIELD;
        if ("legs".equals(slot)) return ItemConstants.LEGS;
        if ("hands".equals(slot)) return ItemConstants.HANDS;
        if ("feet".equals(slot)) return ItemConstants.FEET;
        if ("ring".equals(slot)) return ItemConstants.RING;
        if ("arrows".equals(slot)) return ItemConstants.ARROWS;
        return -1;
    }
}
