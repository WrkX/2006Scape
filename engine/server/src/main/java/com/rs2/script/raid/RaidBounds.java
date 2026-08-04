package com.rs2.script.raid;

/**
 * Immutable inclusive rectangle of one raid room or the raid bounds.
 *
 * <p>Sides are validated to 1..64 tiles and the plane to 0..3 at parse time.
 * The raid reservation and every room share one plane.
 */
public final class RaidBounds {

	private final int minX;
	private final int minY;
	private final int maxX;
	private final int maxY;
	private final int plane;

	public RaidBounds(int minX, int minY, int maxX, int maxY, int plane) {
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

	public boolean contains(int x, int y, int plane) {
		return plane == this.plane && x >= minX && x <= maxX
				&& y >= minY && y <= maxY;
	}

	public boolean contains(RaidBounds other) {
		return other.plane == plane && other.minX >= minX
				&& other.maxX <= maxX && other.minY >= minY
				&& other.maxY <= maxY;
	}

	public boolean overlaps(RaidBounds other) {
		return other.plane == plane && other.minX <= maxX
				&& other.maxX >= minX && other.minY <= maxY
				&& other.maxY >= minY;
	}

	@Override
	public String toString() {
		return "(" + minX + "," + minY + ")..(" + maxX + "," + maxY
				+ ") plane " + plane;
	}

}
