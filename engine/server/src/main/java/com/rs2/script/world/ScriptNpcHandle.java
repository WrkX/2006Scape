package com.rs2.script.world;

import org.graalvm.polyglot.HostAccess;

import com.rs2.game.npcs.Npc;
import com.rs2.game.players.Player;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.ScriptedPosition;
import com.rs2.world.clip.PathFinder;

/** Capability handle for one exact encounter-owned NPC allocation. */
public final class ScriptNpcHandle {

    private final ScriptNpcService service;
    private final long token;
    private final int id;
    private final int maxHp;
    private ScriptedPosition lastPosition;

    ScriptNpcHandle(ScriptNpcService service, long token) {
        this.service = service;
        this.token = token;
        Npc npc = service.current(this);
        this.id = npc == null ? 0 : npc.npcType;
        this.maxHp = npc == null ? 0 : npc.MaxHP;
        this.lastPosition = npc == null
                ? new ScriptedPosition(0, 0, 0)
                : new ScriptedPosition(npc.absX, npc.absY, npc.heightLevel);
    }

    long tokenValue() {
        return token;
    }

    @HostAccess.Export
    public String token() {
        return Long.toString(token);
    }

    @HostAccess.Export
    public int id() {
        return id;
    }

    @HostAccess.Export
    public ScriptedPosition position() {
        return lastPosition;
    }

    @HostAccess.Export
    public int hp() {
        Npc npc = current();
        return npc == null ? 0 : Math.max(0, npc.HP);
    }

    @HostAccess.Export
    public int maxHp() {
        return maxHp;
    }

    @HostAccess.Export
    public boolean isAlive() {
        Npc npc = current();
        return npc != null && !npc.isDead && !npc.applyDead && npc.HP > 0;
    }

    @HostAccess.Export
    public boolean setTarget(ScriptedPlayer player) {
        ScriptNpcService.OwnedNpc owned = owned();
        if (owned == null || player == null || !service.canMutate(owned,
                player.backingPlayer())) {
            return false;
        }
        Npc npc = owned.npc;
        npc.killerId = player.backingPlayer().playerId;
        npc.underAttack = true;
        npc.underAttackBy = player.backingPlayer().playerId;
        return true;
    }

    @HostAccess.Export
    public boolean clearTarget() {
        ScriptNpcService.OwnedNpc owned = owned();
        if (!service.canMutate(owned, null)) {
            return false;
        }
        owned.npc.killerId = 0;
        owned.npc.underAttackBy = 0;
        owned.npc.underAttack = false;
        return true;
    }

    @HostAccess.Export
    public boolean face(double xValue, double yValue) {
        Integer x = integral(xValue, 0, 16383);
        Integer y = integral(yValue, 0, 16383);
        if (x == null || y == null) {
            return false;
        }
        ScriptNpcService.OwnedNpc owned = owned();
        if (!service.canMutate(owned, null)) {
            return false;
        }
        owned.npc.turnNpc(x, y);
        return true;
    }

    public boolean face(int x, int y) {
        return face((double) x, (double) y);
    }

    @HostAccess.Export
    public boolean walkTo(double xValue, double yValue) {
        Integer x = integral(xValue, 0, 16383);
        Integer y = integral(yValue, 0, 16383);
        ScriptNpcService.OwnedNpc owned = owned();
        if (x == null || y == null || owned == null) {
            return false;
        }
        PathFinder.RouteStep next = service.findNextMove(owned, x, y,
                owned.npc.heightLevel);
        if (next == null || !owned.npc.queueScriptRouteStep(next.x(),
                next.y(), next.plane())) {
            return false;
        }
        owned.lastPosition = new ScriptedPosition(next.x(), next.y(),
                next.plane());
        lastPosition = owned.lastPosition;
        return true;
    }

    public boolean walkTo(int x, int y) {
        return walkTo((double) x, (double) y);
    }

    @HostAccess.Export
    public int damage(double amountValue, ScriptedPlayer source) {
        Integer amountValueChecked = integral(amountValue, 1, 32767);
        if (amountValueChecked == null) {
            return 0;
        }
        int amount = amountValueChecked.intValue();
        ScriptNpcService.OwnedNpc owned = owned();
        Player player = source == null ? null : source.backingPlayer();
        if (!service.canMutate(owned, player)) {
            return 0;
        }
        Npc npc = owned.npc;
        int applied = Math.min(amount, Math.max(0, npc.HP));
        npc.HP -= applied;
        npc.hitDiff = applied;
        npc.hitUpdateRequired = true;
        npc.updateRequired = true;
        if (player != null) {
            npc.killerId = player.playerId;
            npc.killedBy = player.playerId;
            if (applied > 0) {
                player.lastNpcAttacked = npc.npcId;
                player.killingNpcIndex = npc.npcId;
                player.totalDamageDealt += applied;
            }
        }
        if (npc.HP <= 0) {
            npc.HP = 0;
            npc.isDead = true;
            npc.applyDead = false;
            npc.needRespawn = false;
            npc.actionTimer = 0;
        }
        return applied;
    }

    public int damage(int amount, ScriptedPlayer source) {
        return damage((double) amount, source);
    }

    @HostAccess.Export
    public int heal(double amountValue) {
        Integer amountValueChecked = integral(amountValue, 1, 32767);
        if (amountValueChecked == null) {
            return 0;
        }
        int amount = amountValueChecked.intValue();
        ScriptNpcService.OwnedNpc owned = owned();
        if (!service.canMutate(owned, null)) {
            return 0;
        }
        int before = Math.max(0, owned.npc.HP);
        int after = Math.min(maxHp, before + amount);
        owned.npc.HP = after;
        return after - before;
    }

    public int heal(int amount) {
        return heal((double) amount);
    }

    @HostAccess.Export
    public boolean animate(double animationValue, double delayValue) {
        Integer animationId = integral(animationValue, -1, 65535);
        Integer delay = integral(delayValue, 0, 255);
        ScriptNpcService.OwnedNpc owned = owned();
        if (animationId == null || delay == null
                || !service.canMutate(owned, null)) {
            return false;
        }
        owned.npc.animNumber = animationId.intValue();
        owned.npc.animDelay = delay.intValue();
        owned.npc.animUpdateRequired = true;
        owned.npc.updateRequired = true;
        return true;
    }

    public boolean animate(int animationId, int delay) {
        return animate((double) animationId, (double) delay);
    }

    @HostAccess.Export
    public boolean graphic(double graphicValue, String height) {
        Integer graphicId = integral(graphicValue, 0, 65535);
        ScriptNpcService.OwnedNpc owned = owned();
        if (graphicId == null || (!"low".equals(height)
                && !"high".equals(height)) || !service.canMutate(owned, null)) {
            return false;
        }
        if ("high".equals(height)) {
            owned.npc.gfx100(graphicId.intValue());
        } else {
            owned.npc.gfx0(graphicId.intValue());
        }
        return true;
    }

    public boolean graphic(int graphicId, String height) {
        return graphic((double) graphicId, height);
    }

    @HostAccess.Export
    public boolean forcedChat(String text) {
        ScriptNpcService.OwnedNpc owned = owned();
        if (text == null || text.length() < 1 || text.length() > 80
                || !service.canMutate(owned, null)) {
            return false;
        }
        owned.npc.forceChat(text);
        return true;
    }

    @HostAccess.Export
    public boolean despawn() {
        return service.despawn(this);
    }

    private ScriptNpcService.OwnedNpc owned() {
        synchronized (service) {
            return service.resolve(this);
        }
    }

    private Npc current() {
        return service.current(this);
    }

    private static Integer integral(double value, int min, int max) {
        if (!Double.isFinite(value) || value != Math.rint(value)
                || value < min || value > max) {
            return null;
        }
        return Integer.valueOf((int) value);
    }
}
