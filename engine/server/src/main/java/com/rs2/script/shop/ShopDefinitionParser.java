package com.rs2.script.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apollo.cache.def.ItemDefinition;
import org.graalvm.polyglot.Value;

import com.rs2.script.drop.ItemNameResolver;

/**
 * Strict one-way parser for {@code defineShop} schema-v1 definitions.
 *
 * <p>Allowed members: {@code id}, {@code name}, {@code items}, {@code buys},
 * and {@code restockTicks}. Every item id is definition-backed: numeric ids
 * must exist in the loaded item table, string ids resolve through the exact
 * deterministic {@link ItemNameResolver} (missing or ambiguous names fail
 * with the shop id and field path). Stock is bounded at 1..40 items with
 * bounded amounts and prices, and the restock interval is bounded to
 * {@code 1..100000} game cycles.
 */
public final class ShopDefinitionParser {

	private static final int MAX_ITEMS = 40;
	private static final int MAX_AMOUNT = 100000;
	private static final int MAX_PRICE = 100000000;
	private static final int MAX_RESTOCK_TICKS = 100000;
	private static final int DEFAULT_RESTOCK_TICKS = 1000;

	private final String source;
	private final int schemaVersion;

	public ShopDefinitionParser(String source, int schemaVersion) {
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public ShopDefinition parse(Value value) {
		if (value == null || !value.hasMembers()) {
			throw failure("definition must be an object");
		}
		only(value, "shop", "id", "name", "items", "buys", "restockTicks");
		String id = requireId(value);
		String name = boundedString(required(value, "name"), "shop.name", 128);
		if (name.isEmpty()) {
			throw failure("shop.name must be a non-empty string");
		}
		List<ShopItemDefinition> items = parseItems(required(value, "items"),
				id);
		boolean buys = optionalBoolean(value.getMember("buys"), "shop.buys");
		Integer restockTicks = optionalIntegral(value.getMember("restockTicks"),
				1, MAX_RESTOCK_TICKS, "shop.restockTicks");
		return new ShopDefinition(id, name, items, buys,
				restockTicks == null ? DEFAULT_RESTOCK_TICKS
						: restockTicks.intValue(),
				source, schemaVersion);
	}

	private List<ShopItemDefinition> parseItems(Value value, String shopId) {
		if (value == null || !value.hasArrayElements()) {
			throw failure("shop.items must be an array");
		}
		long size = value.getArraySize();
		if (size < 1 || size > MAX_ITEMS) {
			throw failure("shop.items must contain 1.." + MAX_ITEMS
					+ " entries");
		}
		List<ShopItemDefinition> items = new ArrayList<ShopItemDefinition>();
		for (int index = 0; index < size; index++) {
			Value item = value.getArrayElement(index);
			if (item == null || !item.hasMembers()) {
				throw failure("shop.items[" + index
						+ "] must be an object");
			}
			only(item, "shop.items[" + index + "]", "itemId", "amount",
					"price");
			int itemId = parseItemId(item, shopId, index);
			int amount = integral(required(item, "amount"), 1, MAX_AMOUNT,
					"shop.items[" + index + "].amount");
			int price = integral(required(item, "price"), 1, MAX_PRICE,
					"shop.items[" + index + "].price");
			items.add(new ShopItemDefinition(itemId, amount, price));
		}
		return items;
	}

	private int parseItemId(Value item, String shopId, int index) {
		String label = "shop.items[" + index + "].itemId";
		Value value = item.getMember("itemId");
		if (value == null || value.isNull()) {
			throw failure(label + " must be present");
		}
		if (value.isString()) {
			String name = value.asString().trim();
			if (name.isEmpty()) {
				throw failure(label + " must not be an empty item name");
			}
			return ItemNameResolver.resolve(describe(), label, name);
		}
		if (value.isNumber()) {
			int itemId = integral(value, 1, 14999, label);
			try {
				if (!ItemDefinition.exists(itemId)) {
					throw failure(label + ": item id " + itemId
							+ " has no loaded definition");
				}
			} catch (RuntimeException unavailable) {
				throw failure(label + ": item id " + itemId
						+ " has no loaded definition");
			}
			return itemId;
		}
		throw failure(label + " must be a numeric item id or an item name");
	}

	private String requireId(Value value) {
		Value idValue = value.getMember("id");
		if (idValue == null || !idValue.isString()
				|| idValue.asString().trim().isEmpty()) {
			throw failure("'id' must be a non-empty string");
		}
		String id = idValue.asString().trim();
		if (id.length() > 64 || !id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
			throw failure("invalid shop id: " + id);
		}
		return id;
	}

	private boolean optionalBoolean(Value value, String label) {
		if (value == null || value.isNull()) {
			return false;
		}
		if (!value.isBoolean()) {
			throw failure(label + " must be a boolean");
		}
		return value.asBoolean();
	}

	private Value required(Value parent, String member) {
		Value value = parent.getMember(member);
		if (value == null || value.isNull()) {
			throw failure("member '" + member + "' must be present");
		}
		return value;
	}

	private String boundedString(Value value, String label, int maximumBytes) {
		if (!value.isString()) {
			throw failure(label + " must be a string");
		}
		String string = value.asString();
		if (utf8Length(string) > maximumBytes) {
			throw failure(label + " must be at most " + maximumBytes
					+ " UTF-8 bytes");
		}
		return string;
	}

	private int integral(Value value, int minimum, int maximum, String label) {
		if (value == null || !value.isNumber()) {
			throw failure(label + " must be a number");
		}
		double raw = value.asDouble();
		if (!Double.isFinite(raw) || raw != Math.rint(raw)
				|| raw < minimum || raw > maximum) {
			throw failure(label + " must be integral " + minimum + ".."
					+ maximum);
		}
		return (int) raw;
	}

	private Integer optionalIntegral(Value value, int minimum, int maximum,
			String label) {
		if (value == null || value.isNull()) {
			return null;
		}
		return Integer.valueOf(integral(value, minimum, maximum, label));
	}

	private void only(Value value, String label, String... allowed) {
		Set<String> allowedMembers = new TreeSet<String>();
		for (String member : allowed) {
			allowedMembers.add(member);
		}
		Set<String> keys = new TreeSet<String>();
		for (String key : value.getMemberKeys()) {
			keys.add(key);
		}
		if (!allowedMembers.containsAll(keys)) {
			throw failure(label + " has unknown members " + keys
					+ "; allowed: " + allowedMembers);
		}
	}

	private static int utf8Length(String value) {
		return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
	}

	private IllegalArgumentException failure(String message) {
		return new IllegalArgumentException(describe() + ": " + message);
	}

	private String describe() {
		return "Script registration defineShop (source: " + source
				+ ", schema v" + schemaVersion + ")";
	}

}
