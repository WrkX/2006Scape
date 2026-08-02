package com.rs2.script;

import org.graalvm.polyglot.HostAccess;

import com.rs2.script.snapshot.ScriptNpcSnapshot;
import com.rs2.script.world.ScriptEncounterHandle;

/** Immutable context for an NPC death owned by one encounter. */
public final class EncounterNpcDeathScriptContext {

    @HostAccess.Export
    public final ScriptEncounterHandle encounter;
    @HostAccess.Export
    public final ScriptNpcSnapshot npc;
    @HostAccess.Export
    public final ScriptedPlayer killer;
    @HostAccess.Export
    public final ScriptedPosition position;
    @HostAccess.Export
    public final String action = "encounter-npc-death";

    public EncounterNpcDeathScriptContext(ScriptEncounterHandle encounter,
            ScriptNpcSnapshot npc, ScriptedPlayer killer,
            ScriptedPosition position) {
        this.encounter = encounter;
        this.npc = npc;
        this.killer = killer;
        this.position = position;
    }
}
