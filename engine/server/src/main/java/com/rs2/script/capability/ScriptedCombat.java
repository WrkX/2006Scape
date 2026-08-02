package com.rs2.script.capability;

import org.graalvm.polyglot.HostAccess;

import com.rs2.Constants;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerAssistant;
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
    public int damage(double amountValue) {
        Integer amount = integral(amountValue, 1, 32767);
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
        Integer amount = integral(amountValue, 1, 32767);
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

    private static Integer integral(double value, int min, int max) {
        return !Double.isFinite(value) || value != Math.rint(value)
                || value < min || value > max ? null : Integer.valueOf((int) value);
    }
}
