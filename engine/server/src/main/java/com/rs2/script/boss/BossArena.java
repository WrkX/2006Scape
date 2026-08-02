package com.rs2.script.boss;

/**
 * Immutable bounded arena slice consumed by a declarative boss.
 *
 * <p>Standalone bosses use the definition's full arena; a raid embeds the
 * controller with a room slice of the same plane. The slice is validated at
 * definition parse time (bounds order, 1..64 tiles per side, plane 0..3).
 */
public final class BossArena {

	private final int minX;
	private final int minY;
	private final int maxX;
	private final int maxY;
	private final int plane;

	public BossArena(int minX, int minY, int maxX, int maxY, int plane) {
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

}
