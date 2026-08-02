package com.rs2.script.registries;

import org.graalvm.polyglot.HostAccess;

/**
 * Immutable, Java-owned rectangle used by scripted area lifecycle handlers.
 */
public final class ScriptArea {

	private final String id;
	private final int minX;
	private final int minY;
	private final int maxX;
	private final int maxY;
	private final Integer plane;

	public ScriptArea(String id, int minX, int minY, int maxX, int maxY, Integer plane) {
		this.id = id;
		this.minX = minX;
		this.minY = minY;
		this.maxX = maxX;
		this.maxY = maxY;
		this.plane = plane;
	}

	@HostAccess.Export
	public String getId() {
		return id;
	}

	@HostAccess.Export
	public int getMinX() {
		return minX;
	}

	@HostAccess.Export
	public int getMinY() {
		return minY;
	}

	@HostAccess.Export
	public int getMaxX() {
		return maxX;
	}

	@HostAccess.Export
	public int getMaxY() {
		return maxY;
	}

	@HostAccess.Export
	public Integer getPlane() {
		return plane;
	}

	public boolean contains(int x, int y, int currentPlane) {
		return x >= minX && x <= maxX && y >= minY && y <= maxY
				&& (plane == null || plane.intValue() == currentPlane);
	}
}
