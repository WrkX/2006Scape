package com.rs2.script.shop;

/**
 * Immutable item of one scripted shop definition: exact numeric item id
 * (resolved at candidate load), declared start stock, and declared buy
 * price in coins.
 */
public final class ShopItemDefinition {

	private final int itemId;
	private final int amount;
	private final int price;

	public ShopItemDefinition(int itemId, int amount, int price) {
		this.itemId = itemId;
		this.amount = amount;
		this.price = price;
	}

	public int itemId() {
		return itemId;
	}

	public int amount() {
		return amount;
	}

	public int price() {
		return price;
	}

	@Override
	public String toString() {
		return "shop item " + itemId + " x" + amount + " @ " + price;
	}

}
