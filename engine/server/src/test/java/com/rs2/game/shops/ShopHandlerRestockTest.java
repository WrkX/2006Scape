package com.rs2.game.shops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class ShopHandlerRestockTest {

	private static final int SHOP_ID = 190;
	private static final int RAW_COD_SLOT = 2;
	private static final int RAW_COD_ITEM = 341;

	@Before
	public void setUp() {
		ShopHandler.shopItemsStandard[SHOP_ID] = 0;
		ShopHandler.shopItems[SHOP_ID][RAW_COD_SLOT] = 0;
		ShopHandler.shopItemsN[SHOP_ID][RAW_COD_SLOT] = 0;
		ShopHandler.shopItemsSN[SHOP_ID][RAW_COD_SLOT] = 0;
		ShopHandler.shopItemsRestock[SHOP_ID][RAW_COD_SLOT] = 0;
		setStaticShopConfigured(SHOP_ID, true);
	}

	private static void setStaticShopConfigured(int shopId, boolean configured) {
		try {
			java.lang.reflect.Field field = ShopHandler.class.getDeclaredField("staticShopConfigured");
			field.setAccessible(true);
			boolean[] flags = (boolean[]) field.get(null);
			flags[shopId] = configured;
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void zeroStockCatalogItemsRestockToOne() {
		ShopHandler.shopItemsStandard[SHOP_ID] = 5;
		ShopHandler.shopItems[SHOP_ID][RAW_COD_SLOT] = RAW_COD_ITEM + 1;
		ShopHandler.shopItemsSN[SHOP_ID][RAW_COD_SLOT] = 0;

		assertEquals(1, ShopHandler.getRestockCap(SHOP_ID, RAW_COD_SLOT));
	}

	@Test
	public void fishDoesNotRestockBeforeTimerExpires() {
		ShopHandler.shopItemsStandard[SHOP_ID] = 5;
		ShopHandler.shopItems[SHOP_ID][RAW_COD_SLOT] = RAW_COD_ITEM + 1;
		ShopHandler.shopItemsSN[SHOP_ID][RAW_COD_SLOT] = 0;
		ShopHandler.shopItemsN[SHOP_ID][RAW_COD_SLOT] = 0;
		long now = 1_000_000L;
		ShopHandler.shopItemsRestock[SHOP_ID][RAW_COD_SLOT] = now;

		assertFalse(ShopHandler.tryRestockSlot(SHOP_ID, RAW_COD_SLOT, now + 60_000L));
		assertEquals(0, ShopHandler.shopItemsN[SHOP_ID][RAW_COD_SLOT]);
	}

	@Test
	public void fishRestocksAfterOsrsInterval() {
		ShopHandler.shopItemsStandard[SHOP_ID] = 5;
		ShopHandler.shopItems[SHOP_ID][RAW_COD_SLOT] = RAW_COD_ITEM + 1;
		ShopHandler.shopItemsSN[SHOP_ID][RAW_COD_SLOT] = 0;
		ShopHandler.shopItemsN[SHOP_ID][RAW_COD_SLOT] = 0;
		long now = 1_000_000L;
		ShopHandler.shopItemsRestock[SHOP_ID][RAW_COD_SLOT] = now;

		assertTrue(ShopHandler.tryRestockSlot(SHOP_ID, RAW_COD_SLOT,
				now + ShopRestockTimes.getRestockMs(SHOP_ID, RAW_COD_ITEM)));
		assertEquals(1, ShopHandler.shopItemsN[SHOP_ID][RAW_COD_SLOT]);
	}

	@Test
	public void fishDoesNotRestockAboveOne() {
		ShopHandler.shopItemsStandard[SHOP_ID] = 5;
		ShopHandler.shopItems[SHOP_ID][RAW_COD_SLOT] = RAW_COD_ITEM + 1;
		ShopHandler.shopItemsSN[SHOP_ID][RAW_COD_SLOT] = 0;
		ShopHandler.shopItemsN[SHOP_ID][RAW_COD_SLOT] = 1;
		long now = 5_000_000L;

		assertFalse(ShopHandler.tryRestockSlot(SHOP_ID, RAW_COD_SLOT, now));
	}

	@Test
	public void baitUsesFastRestockInterval() {
		assertEquals(600L, ShopRestockTimes.getRestockMs(SHOP_ID, 313));
	}
}
