package com.rs2.script.area;

/**
 * Immutable inclusive bounding box of one area definition.
 */
public final class AreaBounds {

	private final int minX;
	private final int minY;
	private final int maxX;
	private final int maxY;
	private final int plane;

	public AreaBounds(int minX, int minY, int maxX, int maxY, int plane) {
		this.minX = minX;
		this.minY = minY;
		this.maxX = maxX;
		this.maxY = maxY;
		this.plane = plane;
	}

	public int minX() {
		return minX;
	}

	public int minY() {
		return minY;
	}

	public int maxX() {
		return maxX;
	}

	public int maxY() {
		return maxY;
	}

	public int plane() {
		return plane;
	}

	public boolean contains(int x, int y, int currentPlane) {
		return currentPlane == plane && x >= minX && x <= maxX && y >= minY
				&& y <= maxY;
	}

	@Override
	public String toString() {
		return "(" + minX + "," + minY + ")..(" + maxX + "," + maxY
				+ ") plane " + plane;
	}

}
