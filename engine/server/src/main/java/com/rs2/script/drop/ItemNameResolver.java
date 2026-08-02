package com.rs2.script.drop;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apollo.cache.def.ItemDefinition;

/**
 * Exact deterministic item-name resolver used at candidate load.
 *
 * <p>Names are matched case-insensitively against the complete loaded
 * {@code ItemDefinition} table using one full-string comparison. Zero matches
 * fail as missing; more than one match fails as ambiguous. The legacy
 * {@code DeprecatedItems.getItemId} first-match lookup is deliberately not
 * used.
 */
public final class ItemNameResolver {

	private ItemNameResolver() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}

	/**
	 * Resolves {@code name} to exactly one loaded item id.
	 *
	 * @param registration diagnostic prefix for failure messages
	 * @param fieldPath diagnostic field path, for example
	 *            {@code drop table 'x'.entries[0].itemId}
	 * @return the resolved id
	 * @throws IllegalArgumentException when definitions are absent, the name
	 *             matches nothing, or it matches more than one item
	 */
	public static int resolve(String registration, String fieldPath,
			String name) {
		ItemDefinition[] definitions = ItemDefinition.getDefinitions();
		if (definitions == null || definitions.length == 0) {
			throw new IllegalArgumentException("Script registration "
					+ registration + ": " + fieldPath + ": no loaded item "
					+ "definitions to resolve item name '" + name + "'");
		}
		String target = name.trim().toLowerCase(Locale.ROOT);
		List<Integer> matches = new ArrayList<>();
		for (int id = 0; id < definitions.length; id++) {
			ItemDefinition definition = definitions[id];
			if (definition == null || definition.getId() != id) {
				continue;
			}
			String itemName = definition.getName();
			if (itemName != null && !itemName.isEmpty()
					&& itemName.toLowerCase(Locale.ROOT).equals(target)) {
				matches.add(Integer.valueOf(id));
			}
		}
		if (matches.isEmpty()) {
			throw new IllegalArgumentException("Script registration "
					+ registration + ": " + fieldPath + ": no loaded item "
					+ "matches name '" + name + "'");
		}
		if (matches.size() > 1) {
			throw new IllegalArgumentException("Script registration "
					+ registration + ": " + fieldPath + ": item name '" + name
					+ "' is ambiguous (" + matches.size() + " matches)");
		}
		return matches.get(0).intValue();
	}

}
