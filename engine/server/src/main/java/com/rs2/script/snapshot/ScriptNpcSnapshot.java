package com.rs2.script.snapshot;

import org.graalvm.polyglot.HostAccess;

import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.script.ScriptedPosition;

/** Immutable, capability-free view of an NPC captured at one point in time. */
public final class ScriptNpcSnapshot {

    private final int id;
    private final String name;
    private final ScriptedPosition position;
    private final int maxHp;

    public ScriptNpcSnapshot(int id, String name, ScriptedPosition position,
            int maxHp) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.position = position == null ? new ScriptedPosition(0, 0, 0) : position;
        this.maxHp = Math.max(0, maxHp);
    }

    public static ScriptNpcSnapshot capture(Npc npc) {
        if (npc == null) {
            return new ScriptNpcSnapshot(0, "", new ScriptedPosition(0, 0, 0), 0);
        }
        return new ScriptNpcSnapshot(npc.npcType,
                NpcHandler.getNpcListName(npc.npcType),
                new ScriptedPosition(npc.absX, npc.absY, npc.heightLevel),
                npc.MaxHP);
    }

    @HostAccess.Export
    public int id() {
        return id;
    }

    @HostAccess.Export
    public String name() {
        return name;
    }

    @HostAccess.Export
    public ScriptedPosition position() {
        return position;
    }

    @HostAccess.Export
    public int maxHp() {
        return maxHp;
    }
}
