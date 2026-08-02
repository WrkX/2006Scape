package com.rs2.script.world;

/**
 * Immutable Java-owned drop-table entry copied from a guest value. Entries
 * never retain a Graal {@code Value}.
 *
 * <p>Canonical entries match the Phase 4 WP6 contract exactly: integral
 * amounts and weights, {@code always} with weight {@code 0}, preserved
 * input order, and no fractional or infinite values at the Java boundary.
 */
public final class ScriptDropEntry {

	final int itemId;
	final int minAmount;
	final int maxAmount;
	final int weight;
	final boolean always;

	public ScriptDropEntry(int itemId, int minAmount, int maxAmount, int weight,
			boolean always) {
		this.itemId = itemId;
		this.minAmount = minAmount;
		this.maxAmount = maxAmount;
		this.weight = weight;
		this.always = always;
	}

	public int itemId() {
		return itemId;
	}

	public int minAmount() {
		return minAmount;
	}

	public int maxAmount() {
		return maxAmount;
	}

	public int weight() {
		return weight;
	}

	public boolean always() {
		return always;
	}
}
