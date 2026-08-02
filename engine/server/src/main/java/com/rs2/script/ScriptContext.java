package com.rs2.script;

import org.graalvm.polyglot.HostAccess;

public class ScriptContext {

    @HostAccess.Export
    public final ScriptedPlayer player;
    @HostAccess.Export
    public final Object target;
    @HostAccess.Export
    public final String action;

    public ScriptContext(ScriptedPlayer player, Object target, String action) {
        this.player = player;
        this.target = target;
        this.action = action;
    }
}
