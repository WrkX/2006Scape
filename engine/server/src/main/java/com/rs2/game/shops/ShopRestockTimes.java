package com.rs2.game.shops;

/**
 * OSRS-aligned shop restock intervals (milliseconds).
 * Game ticks are ~600ms; OSRS wiki restock times are converted from ticks.
 */
public final class ShopRestockTimes {

	private static final int TICK_MS = 600;

	private ShopRestockTimes() {
	}

	public static long getRestockMs(int shopId, int itemId) {
		if (shopId == 190) {
			return fishingGuildRestockMs(itemId);
		}
		return 1000;
	}

	private static long fishingGuildRestockMs(int itemId) {
		switch (itemId) {
			case 313: // Fishing bait
			case 314: // Feather
				return TICK_MS;
			case 341: // Raw cod
			case 339: // Cod
				return 300L * TICK_MS;
			case 353: // Raw mackerel
			case 355: // Mackerel
				return 600L * TICK_MS;
			case 363: // Raw bass
			case 365: // Bass
				return 900L * TICK_MS;
			case 359: // Raw tuna
			case 361: // Tuna
				return 2300L * TICK_MS;
			case 377: // Raw lobster
			case 379: // Lobster
				return 2600L * TICK_MS;
			case 371: // Raw swordfish
			case 373: // Swordfish
				return 2900L * TICK_MS;
			default:
				return 1000;
		}
	}
}
