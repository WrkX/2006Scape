package com.rs2.script;

import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import org.graalvm.polyglot.HostAccess;
import com.rs2.script.snapshot.ScriptNpcSnapshot;

public class ScriptedNpc {

    private final Npc npc;
    private final int id;
    private final String name;
    private final int x;
    private final int y;
    private final int plane;
    private final int hp;
    private final int maxHp;
    private final boolean dead;
    private final int combatLevel;

    public ScriptedNpc(Npc n) {
        this.npc = n;
        this.id = n.npcType;
        this.name = NpcHandler.getNpcListName(n.npcType);
        this.x = n.absX;
        this.y = n.absY;
        this.plane = n.heightLevel;
        this.hp = n.HP;
        this.maxHp = n.MaxHP;
        this.dead = n.isDead;
        this.combatLevel = n.combatLevel;
    }

    public static ScriptedNpc snapshot(Npc n) {
        return new ScriptedNpc(n, false);
    }

    public static ScriptedNpc snapshot(Npc n, ScriptNpcSnapshot snapshot) {
        if (snapshot == null) {
            return snapshot(n);
        }
        return snapshot(n, snapshot, n == null ? 0 : n.HP,
                n != null && n.isDead, n == null ? 0 : n.combatLevel);
    }

    public static ScriptedNpc snapshot(Npc n, ScriptNpcSnapshot snapshot,
            int capturedHp, boolean capturedDead, int capturedCombatLevel) {
        if (snapshot == null) {
            return snapshot(n);
        }
        return new ScriptedNpc(n, snapshot, capturedHp, capturedDead,
                capturedCombatLevel);
    }

    private ScriptedNpc(Npc n, boolean live) {
        this.npc = live ? n : null;
        this.id = n.npcType;
        this.name = NpcHandler.getNpcListName(n.npcType);
        this.x = n.absX;
        this.y = n.absY;
        this.plane = n.heightLevel;
        this.hp = n.HP;
        this.maxHp = n.MaxHP;
        this.dead = n.isDead;
        this.combatLevel = n.combatLevel;
    }

    private ScriptedNpc(Npc n, ScriptNpcSnapshot snapshot) {
        this(n, snapshot, n == null ? 0 : n.HP, n != null && n.isDead,
                n == null ? 0 : n.combatLevel);
    }

    private ScriptedNpc(Npc n, ScriptNpcSnapshot snapshot, int capturedHp,
            boolean capturedDead, int capturedCombatLevel) {
        this.npc = null;
        this.id = snapshot.id();
        this.name = snapshot.name();
        this.x = snapshot.position().x;
        this.y = snapshot.position().y;
        this.plane = snapshot.position().plane;
        this.hp = Math.max(0, capturedHp);
        this.maxHp = snapshot.maxHp();
        this.dead = capturedDead;
        this.combatLevel = capturedCombatLevel;
    }

    @HostAccess.Export
    public int getId() {
        return id;
    }

    @HostAccess.Export
    public String getName() {
        return name;
    }

    @HostAccess.Export
    public int getX() {
        return x;
    }

    @HostAccess.Export
    public int getY() {
        return y;
    }

    @HostAccess.Export
    public int getPlane() {
        return plane;
    }

    @HostAccess.Export
    public int getHp() {
        return hp;
    }

    @HostAccess.Export
    public int getMaxHp() {
        return maxHp;
    }

    @HostAccess.Export
    public boolean isDead() {
        return dead;
    }

    @HostAccess.Export
    public int getCombatLevel() {
        return combatLevel;
    }

    @HostAccess.Export
    public void forceChat(String text) {
        if (npc != null) {
            npc.forceChat(text);
        }
    }

    @HostAccess.Export
    public ScriptedPosition getPosition() {
        return new ScriptedPosition(x, y, plane);
    }
}
