package com.rs2.script;

import org.graalvm.polyglot.HostAccess;

public final class ScriptedPosition {

    @HostAccess.Export
    public final int x;
    @HostAccess.Export
    public final int y;
    @HostAccess.Export
    public final int plane;

    public ScriptedPosition(int x, int y, int plane) {
        this.x = x;
        this.y = y;
        this.plane = plane;
    }
}
