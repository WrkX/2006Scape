package com.rs2.script.drop;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.graalvm.polyglot.Value;

import com.rs2.script.world.ScriptDropEntry;

/**
 * Strict one-way parser for {@code defineDropTable} schema-v1 definitions.
 *
 * <p>Canonical entries match the Phase 4 WP6 contract exactly: {@code 1..64}
 * entries with exactly the five declared members, integral amounts and
 * weights, {@code always} requires weight {@code 0} and non-always entries
 * require a positive weight, the weighted weight sum is bounded in
 * {@code long}, and input order is preserved. Numeric item ids must be
 * definition-backed when definitions are loaded; string item ids resolve at
 * candidate load through {@link ItemNameResolver} and missing or ambiguous
 * names fail with the definition source and field path.
 */
public final class DropTableDefinitionParser {

	private static final int MAX_ENTRIES = 64;
	private static final int MAX_AMOUNT = 1_000_000;
	private static final int MAX_WEIGHT = 1_000_000;
	private static final int MAX_ITEM_ID = 14999;
	private static final Set<String> EXPECTED_MEMBERS = expectedMembers();

	private final String source;
	private final int schemaVersion;

	public DropTableDefinitionParser(String source, int schemaVersion) {
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public DropTableDefinition parse(Value value) {
		if (value == null || !value.hasMembers()) {
			throw failure("definition must be an object");
		}
		only(value, "definition", "id", "entries");
		String id = requireId(value);
		Value entriesValue = value.getMember("entries");
		if (entriesValue == null || !entriesValue.hasArrayElements()) {
			throw failure("entries must be an array");
		}
		long size = entriesValue.getArraySize();
		if (size < 1 || size > MAX_ENTRIES) {
			throw failure("entries must contain 1.." + MAX_ENTRIES
					+ " entries");
		}
		List<ScriptDropEntry> entries = new ArrayList<ScriptDropEntry>();
		long weightSum = 0L;
		for (int index = 0; index < size; index++) {
			Value entry = entriesValue.getArrayElement(index);
			if (entry == null || !entry.hasMembers()) {
				throw failure("entries[" + index + "] must be an object");
			}
			Set<String> keys = new TreeSet<String>();
			for (String key : entry.getMemberKeys()) {
				keys.add(key);
			}
			if (!keys.equals(EXPECTED_MEMBERS)) {
				throw failure("entries[" + index + "] must have exactly the "
						+ "declared itemId, minAmount, maxAmount, weight, and "
						+ "always members");
			}
			int itemId = resolveItemId(entry, "entries[" + index + "].itemId");
			int minAmount = integral(entry.getMember("minAmount"), 1,
					MAX_AMOUNT, "entries[" + index + "].minAmount");
			int maxAmount = integral(entry.getMember("maxAmount"), 1,
					MAX_AMOUNT, "entries[" + index + "].maxAmount");
			int weight = integral(entry.getMember("weight"), 0, MAX_WEIGHT,
					"entries[" + index + "].weight");
			Value alwaysValue = entry.getMember("always");
			if (alwaysValue == null || !alwaysValue.isBoolean()) {
				throw failure("entries[" + index
						+ "].always must be a boolean");
			}
			boolean always = alwaysValue.asBoolean();
			if (minAmount > maxAmount) {
				throw failure("entries[" + index
						+ "].minAmount must not exceed maxAmount");
			}
			if (always != (weight == 0)) {
				throw failure("entries[" + index
						+ "].always requires weight 0 and non-always entries "
						+ "require a positive weight");
			}
			weightSum += weight;
			entries.add(new ScriptDropEntry(itemId, minAmount, maxAmount,
					weight, always));
		}
		if (weightSum > MAX_WEIGHT || (weightSum == 0L && hasWeighted(entries))) {
			throw failure("weighted weight sum must be 1.." + MAX_WEIGHT);
		}
		return new DropTableDefinition(id, source, schemaVersion, entries);
	}

	private int resolveItemId(Value entry, String fieldPath) {
		Value itemValue = entry.getMember("itemId");
		if (itemValue == null) {
			throw failure(fieldPath + " must be present");
		}
		if (itemValue.isString()) {
			String name = itemValue.asString();
			if (name == null || name.trim().isEmpty()) {
				throw failure(fieldPath + " must not be empty");
			}
			int resolved = ItemNameResolver.resolve(describe(), fieldPath,
					name);
			if (org.apollo.cache.def.ItemDefinition.exists(resolved)) {
				return resolved;
			}
			throw failure(fieldPath + ": resolved item '" + name
					+ "' has no loaded definition");
		}
		int itemId = integral(itemValue, 1, MAX_ITEM_ID, fieldPath);
		if (org.apollo.cache.def.ItemDefinition.getDefinitions() != null
				&& !org.apollo.cache.def.ItemDefinition.exists(itemId)) {
			throw failure(fieldPath + ": item " + itemId
					+ " has no loaded definition");
		}
		return itemId;
	}

	private String requireId(Value value) {
		Value idValue = value.getMember("id");
		if (idValue == null || !idValue.isString()
				|| idValue.asString().trim().isEmpty()) {
			throw failure("id must be a non-empty string");
		}
		String id = idValue.asString().trim();
		if (id.length() > 64
				|| !id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
			throw failure("invalid drop table id: " + id);
		}
		return id;
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

	private static boolean hasWeighted(List<ScriptDropEntry> entries) {
		for (ScriptDropEntry entry : entries) {
			if (entry.weight() > 0) {
				return true;
			}
		}
		return false;
	}

	private static void only(Value value, String label, String... allowed) {
		Set<String> allowedMembers = new TreeSet<String>();
		for (String member : allowed) {
			allowedMembers.add(member);
		}
		Set<String> keys = new TreeSet<String>();
		for (String key : value.getMemberKeys()) {
			keys.add(key);
		}
		if (!keys.equals(allowedMembers)) {
			throw new IllegalArgumentException("drop table " + label
					+ " must have exactly the members " + allowedMembers);
		}
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

	private IllegalArgumentException failure(String message) {
		return new IllegalArgumentException(describe() + ": " + message);
	}

	private String describe() {
		return "Script registration defineDropTable (source: " + source
				+ ", schema v" + schemaVersion + ")";
	}

}
