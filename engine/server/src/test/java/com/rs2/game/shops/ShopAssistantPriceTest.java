package com.rs2.game.shops;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ShopAssistantPriceTest {

	@Test
	public void rasoloBuysAtFivePercentAndSellsAtTwoHundredPercent() {
		assertEquals(2.0, ShopAssistant.getShopBuyMultiplier(226), 0.001);
		assertEquals(0.05, ShopAssistant.getShopSellMultiplier(226), 0.001);
	}

	@Test
	public void khazardGeneralStoreUsesOsrsModifiers() {
		assertEquals(1.4, ShopAssistant.getShopBuyMultiplier(220), 0.001);
		assertEquals(0.40, ShopAssistant.getShopSellMultiplier(220), 0.001);
	}

	@Test
	public void fishingGuildShopSellsAtTwoHundredPercent() {
		assertEquals(2.0, ShopAssistant.getShopBuyMultiplier(190), 0.001);
		assertEquals(-1, ShopAssistant.getShopSellMultiplier(190), 0.001);
	}

	@Test
	public void defaultShopsUseStandardModifiers() {
		assertEquals(1.0, ShopAssistant.getShopBuyMultiplier(3), 0.001);
		assertEquals(-1, ShopAssistant.getShopSellMultiplier(3), 0.001);
	}
}
