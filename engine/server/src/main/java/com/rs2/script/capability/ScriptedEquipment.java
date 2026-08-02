package com.rs2.script.capability;

import org.graalvm.polyglot.HostAccess;

import com.rs2.game.items.ItemConstants;
import com.rs2.game.players.Player;
import com.rs2.script.world.ScriptEncounterService;

/** Read-only, identity-safe equipment view exposed to scripts. */
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

    private boolean live() {
        return ScriptEncounterService.getInstance().canUseFacade(player,
                generation, facadeEpoch, false);
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
