package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;

import org.apollo.cache.def.ItemDefinition;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.script.registries.RegistryStore;
import com.rs2.script.shop.ShopDefinition;
import com.rs2.script.shop.ShopDefinitionRegistry;

/**
 * Proves the canonical {@code defineShop} schema-v1 contract: strict
 * members and bounds, exact item-name resolution at candidate load
 * (missing and ambiguous names reject with the field path), definition-
 * backed numeric ids, duplicate shop-id rejection, and the immutable
 * Java-owned descriptor.
 */
public class ShopDefinitionParserTest {

	private Context context;
	private ItemDefinition[] previousItems;

	@Before
	public void setUp() throws Exception {
		context = Context.create("js");
		previousItems = ItemDefinition.getDefinitions();
		ItemDefinition[] items = new ItemDefinition[1500];
		items[379] = named(379, "Lobster");
		items[380] = named(380, "Lobster");
		items[590] = named(590, "Tinderbox");
		Field field = ItemDefinition.class.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, items);
	}

	@After
	public void restore() throws Exception {
		ScriptRuntimeTestFixture.reset();
		if (context != null) {
			context.close();
		}
		Field field = ItemDefinition.class.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, previousItems);
	}

	@Test
	public void canonicalShopParsesIntoJavaOwnedDescriptor() {
		register("{id:'island_general',name:'Island Supplies',"
				+ "items:[{itemId:379,amount:10,price:150},"
				+ "{itemId:'tinderbox',amount:5,price:1}],"
				+ "buys:true,restockTicks:250}");

		ShopDefinition shop = ShopDefinitionRegistry
				.get("island_general");
		assertNotNull(shop);
		assertEquals("island_general", shop.id());
		assertEquals("Island Supplies", shop.name());
		assertEquals(2, shop.items().size());
		assertEquals(379, shop.items().get(0).itemId());
		assertEquals(10, shop.items().get(0).amount());
		assertEquals(150, shop.items().get(0).price());
		assertEquals(590, shop.items().get(1).itemId());
		assertTrue(shop.buys());
		assertEquals(250, shop.restockTicks());
		assertEquals(0, shop.schemaVersion());
		assertEquals(com.rs2.script.definition.ModuleScope.LEGACY_SOURCE,
				shop.source());
	}

	@Test
	public void defaultBuysAndRestockTicksApplyWhenAbsent() {
		register("{id:'plain',name:'Plain',items:[{itemId:379,amount:1,"
				+ "price:1}]}");
		ShopDefinition shop = ShopDefinitionRegistry.get("plain");
		assertFalse(shop.buys());
		assertEquals(1000, shop.restockTicks());
	}

	@Test
	public void ambiguousItemNameRejectsWithFieldPath() {
		expectFailure("{id:'ambiguous',name:'X',items:[{itemId:'lobster',"
				+ "amount:1,price:1}]}", "item name 'lobster' is ambiguous",
				"shop.items[0].itemId");
	}

	@Test
	public void missingItemNameRejectsWithFieldPath() {
		expectFailure("{id:'missing',name:'X',items:[{itemId:'shark',"
				+ "amount:1,price:1}]}", "no loaded item matches name 'shark'",
				"shop.items[0].itemId");
	}

	@Test
	public void undefinedNumericItemRejectsWhenDefinitionsAreLoaded() {
		expectFailure("{id:'undefined',name:'X',items:[{itemId:994,"
				+ "amount:1,price:1}]}", "item id 994 has no loaded definition",
				null);
	}

	@Test
	public void duplicateShopIdRejectsWithBothRecords() {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineShop().accept(
					shop("{id:'dup',name:'X',items:[{itemId:379,amount:1,"
							+ "price:1}]}"));
			try {
				ScriptFunctions.getInstance().getDefineShop().accept(
						shop("{id:'dup',name:'X',items:[{itemId:590,"
								+ "amount:1,price:1}]}"));
				fail("duplicate shop id should reject");
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains(
						"duplicate registration"));
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	@Test
	public void stockAndPriceBoundsReject() {
		expectFailure("{id:'too-many',name:'X',items:Array.from("
				+ "{length:41},()=>({itemId:379,amount:1,price:1}))}",
				"shop.items must contain 1..40 entries", null);
		expectFailure("{id:'zero-amount',name:'X',items:[{itemId:379,"
				+ "amount:0,price:1}]}", "shop.items[0].amount must be "
				+ "integral 1..100000", null);
		expectFailure("{id:'bad-restock',name:'X',items:[{itemId:379,"
				+ "amount:1,price:1}],restockTicks:100001}",
				"shop.restockTicks must be integral 1..100000", null);
	}

	private void register(String shopJs) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineShop().accept(
					shop(shopJs));
			ScriptHost.getInstance().publishForTesting(context, candidate);
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	private void expectFailure(String shopJs, String messagePart,
			String messagePart2) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			try {
				ScriptFunctions.getInstance().getDefineShop()
						.accept(shop(shopJs));
				fail("expected defineShop rejection for: " + shopJs);
			} catch (IllegalArgumentException expected) {
				if (messagePart != null) {
					assertTrue("missing '" + messagePart + "' in: "
							+ expected.getMessage(),
							expected.getMessage().contains(messagePart));
				}
				if (messagePart2 != null) {
					assertTrue("missing '" + messagePart2 + "' in: "
							+ expected.getMessage(),
							expected.getMessage().contains(messagePart2));
				}
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	private Value shop(String js) {
		return context.eval("js", "(" + js + ")");
	}

	private static ItemDefinition named(int id, String name) {
		ItemDefinition definition = new ItemDefinition(id);
		definition.setName(name);
		return definition;
	}

}
