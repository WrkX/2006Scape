package com.rs2.game.shops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apollo.cache.IndexedFileSystem;
import org.apollo.cache.decoder.ItemDefinitionDecoder;
import org.junit.BeforeClass;
import org.junit.Test;

import com.rs2.Constants;

import java.nio.file.Paths;

public class ShopHandlerLoadTest {

	@BeforeClass
	public static void loadItemDefinitions() throws Exception {
		if (org.apollo.cache.def.ItemDefinition.getDefinitions() != null) {
			return;
		}
		IndexedFileSystem cache = new IndexedFileSystem(
				Paths.get(Constants.FILE_SYSTEM_DIR), true);
		new ItemDefinitionDecoder(cache).run();
		new ShopHandler();
	}

	@Test
	public void portKhazardAndRasoloShopsLoadStock() {
		new ShopHandler().loadShops();

		assertTrue("Khazard General Store should load",
				ShopHandler.isStaticShop(220));
		assertEquals("Khazard General Store", ShopHandler.shopName[220]);
		assertTrue(ShopHandler.shopItems[220][0] > 0);
		assertTrue(ShopHandler.shopItemsN[220][0] > 0);

		assertTrue("Rasolo should load", ShopHandler.isStaticShop(226));
		assertEquals("Rasolo the Wandering Merchant", ShopHandler.shopName[226]);
		assertTrue(ShopHandler.shopItems[226][0] > 0);

		assertTrue("Fishing Guild shop should load", ShopHandler.isStaticShop(190));
		assertEquals("Fishing Guild Shop.", ShopHandler.shopName[190]);
		assertTrue(ShopHandler.shopItemsN[190][0] > 0);
	}
}
