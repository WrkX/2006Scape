package com.rs2.script.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.graalvm.polyglot.Value;

import com.rs2.script.ScriptEntityLimits;

/**
 * Validating parser for guest drop tables.
 *
 * <p>The guest value must be an array of {@code 1..64} entries with exactly
 * the five declared {@code ScriptDropEntry} members, definition-backed item
 * ids, integral bounded amounts and weights, and the always/weight coupling
 * rules. The weighted weight sum is validated in {@code long}; input order is
 * preserved. Any violation throws and rejects the whole candidate table.
 */
final class ScriptDropEntryParser {

	private static final int MAX_ENTRIES = 64;
	private static final int MAX_AMOUNT = 1_000_000;
	private static final int MAX_WEIGHT = 1_000_000;
	private static final int MAX_ITEM_ID = ScriptEntityLimits.MAX_ITEM_ID;
	private static final Set<String> EXPECTED_MEMBERS = expectedMembers();

	private ScriptDropEntryParser() {
	}

	static List<ScriptDropEntry> parse(Value value) {
		if (value == null || !value.hasArrayElements()) {
			throw new IllegalArgumentException(
					"drop table must be an array of entries");
		}
		long size = value.getArraySize();
		if (size < 1 || size > MAX_ENTRIES) {
			throw new IllegalArgumentException(
					"drop table must contain 1.." + MAX_ENTRIES + " entries");
		}
		List<ScriptDropEntry> entries = new ArrayList<ScriptDropEntry>();
		long weightSum = 0L;
		for (int index = 0; index < size; index++) {
			Value entry = value.getArrayElement(index);
			if (entry == null || !entry.hasMembers()) {
				throw new IllegalArgumentException(
						"drop table entry must be an object");
			}
			Set<String> keys = new TreeSet<String>();
			for (String key : entry.getMemberKeys()) {
				keys.add(key);
			}
			if (!keys.equals(EXPECTED_MEMBERS)) {
				throw new IllegalArgumentException(
						"drop table entry must have exactly the declared "
								+ "itemId, minAmount, maxAmount, weight, and "
								+ "always members");
			}
			int itemId = integral(entry.getMember("itemId"), 1, MAX_ITEM_ID,
					"itemId");
			int minAmount = integral(entry.getMember("minAmount"), 1,
					MAX_AMOUNT, "minAmount");
			int maxAmount = integral(entry.getMember("maxAmount"), 1,
					MAX_AMOUNT, "maxAmount");
			int weight = integral(entry.getMember("weight"), 0, MAX_WEIGHT,
					"weight");
			Value alwaysValue = entry.getMember("always");
			if (alwaysValue == null || !alwaysValue.isBoolean()) {
				throw new IllegalArgumentException(
						"drop table entry always must be a boolean");
			}
			boolean always = alwaysValue.asBoolean();
			if (minAmount > maxAmount) {
				throw new IllegalArgumentException(
						"drop table entry minAmount must not exceed maxAmount");
			}
			if (always != (weight == 0)) {
				throw new IllegalArgumentException(
						"drop table entry always requires weight 0 and "
								+ "non-always entries require a positive weight");
			}
			if (!org.apollo.cache.def.ItemDefinition.exists(itemId)) {
				throw new IllegalArgumentException(
						"drop table item " + itemId + " has no definition");
			}
			weightSum += weight;
			entries.add(new ScriptDropEntry(itemId, minAmount, maxAmount,
					weight, always));
		}
		if (weightSum > MAX_WEIGHT
				|| (weightSum == 0L && hasWeighted(entries))) {
			throw new IllegalArgumentException(
					"drop table weighted weight sum must be 1.."
							+ MAX_WEIGHT);
		}
		return entries;
	}

	private static boolean hasWeighted(List<ScriptDropEntry> entries) {
		for (ScriptDropEntry entry : entries) {
			if (entry.weight > 0) {
				return true;
			}
		}
		return false;
	}

	private static int integral(Value value, int min, int max, String member) {
		if (value == null || !value.isNumber()) {
			throw new IllegalArgumentException(
					"drop table entry " + member + " must be a number");
		}
		double raw = value.asDouble();
		if (!Double.isFinite(raw) || raw != Math.rint(raw)
				|| raw < min || raw > max) {
			throw new IllegalArgumentException(
					"drop table entry " + member + " must be integral "
							+ min + ".." + max);
		}
		return (int) raw;
	}

	private static Set<String> expectedMembers() {
		Set<String> members = new TreeSet<String>();
		members.add("itemId");
		members.add("minAmount");
		members.add("maxAmount");
		members.add("weight");
		members.add("always");
		return members;
	}
}
