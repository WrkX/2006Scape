package com.rs2.script.world;

/**
 * Immutable Java-owned drop-table entry copied from a guest value. Entries
 * never retain a Graal {@code Value}.
 */
final class ScriptDropEntry {

	final int itemId;
	final int minAmount;
	final int maxAmount;
	final int weight;
	final boolean always;

	ScriptDropEntry(int itemId, int minAmount, int maxAmount, int weight,
			boolean always) {
		this.itemId = itemId;
		this.minAmount = minAmount;
		this.maxAmount = maxAmount;
		this.weight = weight;
		this.always = always;
	}
}
