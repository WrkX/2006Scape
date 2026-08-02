package com.rs2.script.world;

import org.graalvm.polyglot.HostAccess;

import com.rs2.game.objects.Objects;
import com.rs2.script.ScriptedPosition;
import com.rs2.world.WorldObjectService;

/** Exact encounter-owned object identity. */
public final class ScriptObjectHandle {
    private final WorldObjectService service;
    private final WorldObjectService.Tile tile;
    private final long token;
    private final long version;
    private final long encounterToken;
    private final int id, type, rotation;

    public ScriptObjectHandle(WorldObjectService service, WorldObjectService.Tile tile,
            long token, Objects object) {
        this(service, tile, token, 0L, 0L, object);
    }

    public ScriptObjectHandle(WorldObjectService service, WorldObjectService.Tile tile,
            long token, long version, long encounterToken, Objects object) {
        this.service = service; this.tile = tile; this.token = token;
        this.version = version; this.encounterToken = encounterToken;
        this.id = object == null ? -1 : object.getObjectId();
        this.type = object == null ? -1 : object.getObjectType();
        this.rotation = object == null ? -1 : object.getObjectFace();
    }

    @HostAccess.Export public String token() { return Long.toUnsignedString(token); }
    @HostAccess.Export public int id() { return id; }
    @HostAccess.Export public ScriptedPosition position() { return new ScriptedPosition(tile.x, tile.y, tile.plane); }
    @HostAccess.Export public int type() { return type; }
    @HostAccess.Export public int rotation() { return rotation; }
    @HostAccess.Export public boolean isActive() { return service.isActive(this); }
    @HostAccess.Export public boolean remove() { return service.removeHandle(this); }
    public WorldObjectService.Tile tile() { return tile; }
    public long tokenValue() { return token; }
    public long versionValue() { return version; }
    public long encounterTokenValue() { return encounterToken; }
}
