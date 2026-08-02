package com.rs2.game.shops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.rs2.game.players.Client;

public class ShopHandlerPlayerShopTest {

	@Before
	public void setUp() {
		if (ShopHandler.shopName[0] == null) {
			new ShopHandler();
		}
	}

	@Test
	public void closePlayerShopClearsSlotAndDecrementsTotalShops() {
		Client shop = new Client(null);
		shop.properName = "Jonas";
		int totalBefore = ShopHandler.totalshops;

		ShopHandler.createPlayerShop(shop);
		int shopId = shop.shopId;
		assertTrue(shopId >= 0);
		assertEquals("Jonas's Store", ShopHandler.shopName[shopId]);
		assertEquals(totalBefore + 1, ShopHandler.totalshops);

		ShopHandler.closePlayerShop(shop);

		assertEquals("", ShopHandler.shopName[shopId]);
		assertEquals(0, shop.shopId);
		assertEquals(totalBefore, ShopHandler.totalshops);
	}

	@Test
	public void closePlayerShopFindsShopByNameWhenShopIdIsStale() {
		Client shop = new Client(null);
		shop.properName = "Tester";
		ShopHandler.createPlayerShop(shop);
		int shopId = shop.shopId;
		shop.shopId = 0;

		ShopHandler.closePlayerShop(shop);

		assertEquals("", ShopHandler.shopName[shopId]);
	}
}
