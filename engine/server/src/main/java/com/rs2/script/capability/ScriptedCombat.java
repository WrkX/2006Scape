package com.rs2.script.capability;

import org.graalvm.polyglot.HostAccess;

import com.rs2.Constants;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerAssistant;
import com.rs2.script.BridgeValidation;
import com.rs2.script.world.ScriptEncounterService;

/** Truthful combat/health facade. */
public final class ScriptedCombat {

    private final Player player;
    private final long generation;
    private final long facadeEpoch;

    public ScriptedCombat(Player player, long generation, long facadeEpoch) {
        this.player = player;
        this.generation = generation;
        this.facadeEpoch = facadeEpoch;
    }

    @HostAccess.Export
    public int hp() {
        return live() ? Math.max(0, player.playerLevel[Constants.HITPOINTS]) : 0;
    }

    @HostAccess.Export
    public int maxHp() {
        if (!live()) return 0;
        return Math.max(0, PlayerAssistant.getLevelForXP(
                player.playerXP[Constants.HITPOINTS]));
    }

    @HostAccess.Export
    public boolean inCombat() {
        return live() && (player.underAttackBy > 0 || player.npcIndex > 0
                || player.playerIndex > 0);
    }

    @HostAccess.Export
    public boolean underAttack() {
        return live() && (player.underAttackBy > 0 || player.underAttackBy2 > 0);
    }

    @HostAccess.Export
    public boolean poisoned() {
        return live() && (player.poisonDamage > 0 || player.poisonMask > 0);
    }

    /** True when the player currently has a wilderness skull. */
    @HostAccess.Export
    public boolean skulled() {
        return live() && player.isSkulled;
    }

    /**
     * Side-effect-free wilderness membership check. Does not open the legacy
     * wilderness warning interface.
     */
    @HostAccess.Export
    public boolean inWilderness() {
        return live() && coordinatesInWilderness();
    }

    /** Wilderness combat level, or {@code 0} when outside the wilderness. */
    @HostAccess.Export
    public int wildernessLevel() {
        if (!live() || !coordinatesInWilderness()) {
            return 0;
        }
        return Math.max(0, player.wildLevel);
    }

    /** True when the player is outside the wilderness PvP area. */
    @HostAccess.Export
    public boolean inSafeArea() {
        return live() && !coordinatesInWilderness();
    }

    @HostAccess.Export
    public int damage(double amountValue) {
        Integer amount = BridgeValidation.integral(amountValue, 1, 32767);
        if (amount == null || !canMutate() || player.isDead) return 0;
        int before = hp();
        int applied = Math.min(before, amount.intValue());
        if (applied <= 0) return 0;
        // Enter the normal hit pipeline so hit masks, redemption/teleport
        // rules, refreshes, and the engine's deferred death transition remain
        // authoritative. The facade never toggles isDead directly.
        player.handleHitMask(applied);
        player.dealDamage(applied);
        return Math.max(0, before - Math.max(0, player.playerLevel[Constants.HITPOINTS]));
    }

    @HostAccess.Export
    public int heal(double amountValue) {
        Integer amount = BridgeValidation.integral(amountValue, 1, 32767);
        if (amount == null || !canMutate() || player.isDead) return 0;
        int before = hp();
        int maximum = maxHp();
        int applied = Math.min(amount.intValue(), Math.max(0, maximum - before));
        if (applied <= 0) return 0;
        player.playerLevel[Constants.HITPOINTS] = before + applied;
        player.getPlayerAssistant().refreshSkill(Constants.HITPOINTS);
        return applied;
    }

    private boolean live() {
        return ScriptEncounterService.getInstance().canUseFacade(player,
                generation, facadeEpoch, false);
    }

    private boolean canMutate() {
        return ScriptEncounterService.getInstance().canMutate(player,
                generation, facadeEpoch);
    }

    private boolean coordinatesInWilderness() {
        if (player.inCw()) {
            return true;
        }
        return (player.absX > 2941 && player.absX < 3392
                && player.absY > 3518 && player.absY < 3966)
                || (player.absX > 2941 && player.absX < 3392
                        && player.absY > 9918 && player.absY < 10366);
    }
}
