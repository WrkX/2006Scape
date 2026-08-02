package com.rs2.script;

import com.rs2.game.objects.Objects;
import org.apollo.cache.def.ObjectDefinition;
import org.graalvm.polyglot.HostAccess;

public class ScriptedObject {

    private final int id;
    private final int x;
    private final int y;
    private final int plane;
    private final int rotation;
    private final int type;

    public ScriptedObject(Objects o) {
        id = o.getObjectId();
        x = o.getObjectX();
        y = o.getObjectY();
        plane = o.getObjectHeight();
        rotation = o.getObjectFace();
        type = o.getObjectType();
    }

    @HostAccess.Export
    public int getId() {
        return id;
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
    public ScriptedPosition getPosition() {
        return new ScriptedPosition(x, y, plane);
    }

    @HostAccess.Export
    public int getRotation() {
        return rotation;
    }

    @HostAccess.Export
    public int getType() {
        return type;
    }

    @HostAccess.Export
    public String getName() {
        return ObjectDefinition.lookup(id).getName();
    }
}
