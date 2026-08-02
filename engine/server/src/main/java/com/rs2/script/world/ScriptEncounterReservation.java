package com.rs2.script.world;

/** Immutable, inclusive encounter area reservation. */
final class ScriptEncounterReservation {

	final int minX;
	final int minY;
	final int maxX;
	final int maxY;
	final int plane;

	ScriptEncounterReservation(int minX, int minY, int maxX, int maxY,
			int plane) {
		this.minX = minX;
		this.minY = minY;
		this.maxX = maxX;
		this.maxY = maxY;
		this.plane = plane;
	}

	boolean contains(int x, int y, int candidatePlane) {
		return plane == candidatePlane && x >= minX && x <= maxX
				&& y >= minY && y <= maxY;
	}

	boolean overlaps(ScriptEncounterReservation other) {
		return plane == other.plane && minX <= other.maxX
				&& maxX >= other.minX && minY <= other.maxY
				&& maxY >= other.minY;
	}
}
