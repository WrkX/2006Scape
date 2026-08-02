package com.rs2.script.area;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable object projection of one area definition.
 *
 * <p>The projection replaces the tile's visible object through the exact
 * layered object transaction; {@code type}/{@code rotation} default to the
 * canonical static-object shape. Optional per-action drop bindings register
 * exact tile-position host routes and roll the named table when the acting
 * player clicks the exact projection.
 */
public final class AreaObjectProjection {

	private final String key;
	private final int objectId;
	private final int x;
	private final int y;
	private final int plane;
	private final int type;
	private final int rotation;
	private final List<AreaObjectDrop> drops;

	public AreaObjectProjection(String key, int objectId, int x, int y,
			int plane, int type, int rotation, List<AreaObjectDrop> drops) {
		this.key = key;
		this.objectId = objectId;
		this.x = x;
		this.y = y;
		this.plane = plane;
		this.type = type;
		this.rotation = rotation;
		this.drops = Collections.unmodifiableList(
				new ArrayList<AreaObjectDrop>(drops));
	}

	public String key() {
		return key;
	}

	public int objectId() {
		return objectId;
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int plane() {
		return plane;
	}

	public int type() {
		return type;
	}

	public int rotation() {
		return rotation;
	}

	/** Immutable per-action drop bindings in registration order. */
	public List<AreaObjectDrop> drops() {
		return drops;
	}

	@Override
	public String toString() {
		return "object '" + key + "' (id " + objectId + " at " + x + "," + y
				+ "," + plane + ")";
	}

}
