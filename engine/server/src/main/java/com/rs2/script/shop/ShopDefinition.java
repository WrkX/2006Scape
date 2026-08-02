package com.rs2.script.shop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable Java-owned schema-v1 scripted shop descriptor.
 *
 * <p>The descriptor carries copied canonical values only: a stable string
 * id, display name, bounded declared stock with exact prices, whether the
 * shop buys items back (capped at the declared stock amounts), and the
 * restock interval in game cycles. Scripted shops are separate from legacy
 * numeric static shops and player shops; they never touch the fixed
 * {@code ShopHandler} arrays.
 */
public final class ShopDefinition {

	private final String id;
	private final String name;
	private final List<ShopItemDefinition> items;
	private final boolean buys;
	private final int restockTicks;
	private final String source;
	private final int schemaVersion;

	public ShopDefinition(String id, String name,
			List<ShopItemDefinition> items, boolean buys, int restockTicks,
			String source, int schemaVersion) {
		this.id = id;
		this.name = name;
		this.items = Collections.unmodifiableList(
				new ArrayList<ShopItemDefinition>(items));
		this.buys = buys;
		this.restockTicks = restockTicks;
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	/** Immutable declared stock in registration order. */
	public List<ShopItemDefinition> items() {
		return items;
	}

	/** Whether the shop buys back declared items at 85% of their price. */
	public boolean buys() {
		return buys;
	}

	/** Restock interval in game cycles; one unit per item per interval. */
	public int restockTicks() {
		return restockTicks;
	}

	/** Bounded logical source module, or the legacy-unscoped marker. */
	public String source() {
		return source;
	}

	public int schemaVersion() {
		return schemaVersion;
	}

	@Override
	public String toString() {
		return "shop '" + id + "' (" + items.size() + " items, source: "
				+ source + ", schema v" + schemaVersion + ")";
	}

}
