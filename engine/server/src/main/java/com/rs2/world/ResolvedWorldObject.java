package com.rs2.world;

import com.rs2.game.objects.Objects;

/** Immutable read-only result from the layered world-object resolver. */
public final class ResolvedWorldObject {

	public enum Layer {
		ENCOUNTER,
		TIMED,
		GLOBAL,
		CACHE
	}

	private final Objects object;
	private final Layer layer;
	private final long version;
	private final long backingToken;

	ResolvedWorldObject(Objects object, Layer layer) {
		this(object, layer, 0L, 0L);
	}

	ResolvedWorldObject(Objects object, Layer layer, long version,
			long backingToken) {
		this.object = object;
		this.layer = layer;
		this.version = version;
		this.backingToken = backingToken;
	}

	public Objects getObject() {
		return object;
	}

	public Layer getLayer() {
		return layer;
	}

	public long getVersion() { return version; }

	public long getBackingToken() { return backingToken; }

	public int getObjectType() { return object.getObjectType(); }

	public int getObjectRotation() { return object.getObjectFace(); }

	public int getObjectId() { return object.getObjectId(); }

	public boolean matches(int objectId, int x, int y, int plane) {
		return object.getObjectId() == objectId && object.getObjectX() == x
				&& object.getObjectY() == y
				&& object.getObjectHeight() == plane;
	}

	public boolean matches(int objectId, int x, int y, int plane,
			int type, int rotation) {
		return matches(objectId, x, y, plane)
				&& object.getObjectType() == type
				&& object.getObjectFace() == rotation;
	}
}
