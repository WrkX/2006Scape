package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;

import org.apollo.cache.def.ItemDefinition;
import org.apollo.cache.def.ObjectDefinition;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.npcs.NpcList;
import com.rs2.script.area.AreaDefinition;
import com.rs2.script.area.AreaDefinitionRegistry;
import com.rs2.script.area.AreaDropPolicy;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.ExecutableRouteRecord;
import com.rs2.script.route.RouteRegistry;

/**
 * Proves the canonical {@code defineArea} schema-v1 contract: strict
 * members and bounds, exact spawn/object keys and unique tiles,
 * definition-backed npc/object ids when the definitions are loaded,
 * drop-policy/private-TTL coupling, candidate-scoped shop/drop references,
 * duplicate area-id rejection, and the exact tile-position object and
 * allocation-bound NPC host routes registered by the area runtime.
 */
public class AreaDefinitionParserTest {

	private Context context;
	private ItemDefinition[] previousItems;
	private ObjectDefinition[] previousObjects;
	private NpcList[] previousNpcList;

	@Before
	public void setUp() throws Exception {
		context = Context.create("js");
		previousItems = ItemDefinition.getDefinitions();
		previousObjects = ObjectDefinition.getDefinitions();
		previousNpcList = NpcHandler.NpcList.clone();
		ItemDefinition[] items = new ItemDefinition[1500];
		items[536] = named(536, "Dragon bones");
		items[995] = named(995, "Coins");
		items[379] = named(379, "Lobster");
		Field field = ItemDefinition.class.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, items);
		ObjectDefinition[] objects = new ObjectDefinition[2500];
		objects[409] = new ObjectDefinition(409);
		objects[2213] = new ObjectDefinition(2213);
		Field objectField = ObjectDefinition.class.getDeclaredField("definitions");
		objectField.setAccessible(true);
		objectField.set(null, objects);
		NpcHandler.NpcList = new NpcList[NpcHandler.maxListedNPCs];
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
		Field objectField = ObjectDefinition.class.getDeclaredField("definitions");
		objectField.setAccessible(true);
		objectField.set(null, previousObjects);
		System.arraycopy(previousNpcList, 0, NpcHandler.NpcList, 0,
				previousNpcList.length);
	}

	@Test
	public void canonicalAreaParsesIntoJavaOwnedDescriptorWithRoutes() {
		register(canonical(0));

		AreaDefinition area = AreaDefinitionRegistry.get("test-area");
		assertNotNull(area);
		assertEquals("test-area", area.id());
		assertEquals("Test Area", area.name());
		assertEquals(2830, area.bounds().minX());
		assertEquals(2835, area.bounds().maxX());
		assertEquals(0, area.bounds().plane());
		assertEquals(1, area.npcs().size());
		assertEquals("guardian-1", area.npcs().get(0).key());
		assertEquals(153, area.npcs().get(0).npcId());
		assertEquals(200, area.npcs().get(0).privateTicks());
		assertEquals(AreaDropPolicy.PRIVATE_TO_KILLER,
				area.npcs().get(0).dropPolicy());
		assertEquals("guardian_loot", area.npcs().get(0).dropTable());
		assertEquals(1, area.objects().size());
		assertEquals("ancient-chest", area.objects().get(0).key());
		assertEquals(2213, area.objects().get(0).objectId());
		assertEquals("first", area.objects().get(0).drops().get(0).action());
		assertEquals(AreaDropPolicy.PUBLIC,
				area.objects().get(0).drops().get(0).dropPolicy());
		assertEquals(1, area.shops().size());
		assertEquals("island_general", area.shops().get(0));
		assertEquals(0, area.schemaVersion());
		assertEquals(com.rs2.script.definition.ModuleScope.LEGACY_SOURCE,
				area.source());
		assertNotNull(area.onEnter());

		// The exact tile-position chest route and the allocation-bound shop
		// route are registered as Java host consumers.
		ExecutableRouteRecord chest = ScriptHost.getInstance()
				.readActiveRegistry(state -> RouteRegistry.get(state,
						ExecutableRouteKey.objectAt(2213, "first", 2835,
								2835, 0)));
		assertNotNull(chest);
		assertFalse(chest.isGuest());
		ExecutableRouteRecord shop = ScriptHost.getInstance()
				.readActiveRegistry(state -> RouteRegistry.get(state,
						ExecutableRouteKey.npcAllocated(153, "first",
								"test-area", "guardian-1")));
		assertNotNull(shop);
		assertFalse(shop.isGuest());
	}

	@Test
	public void duplicateAreaIdRejectsWithBothRecords() {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineDropTable().accept(table(
					"{id:'guardian_loot',entries:[{itemId:536,minAmount:1,"
							+ "maxAmount:1,weight:0,always:true}]}"));
			ScriptFunctions.getInstance().getDefineDropTable().accept(table(
					"{id:'chest_loot',entries:[{itemId:995,minAmount:1,"
							+ "maxAmount:1,weight:0,always:true}]}"));
			ScriptFunctions.getInstance().getDefineShop().accept(shop(
					"{id:'island_general',name:'Island',items:[{itemId:379,"
							+ "amount:5,price:100}]}"));
			ScriptFunctions.getInstance().getDefineArea()
					.accept(area(canonical(0)));
			try {
				ScriptFunctions.getInstance().getDefineArea()
						.accept(area(canonical(0)));
				fail("duplicate area id should reject");
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains(
						"duplicate registration"));
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	@Test
	public void duplicateSpawnKeyAndObjectTileRejectWithPath() {
		expectFailure(area("{id:'dup-spawn',name:'Dup',"
				+ "bounds:{minX:2830,minY:2830,maxX:2835,maxY:2835,plane:0},"
				+ "npcs:[{key:'a',npcId:153,x:2831,y:2831},"
				+ "{key:'a',npcId:153,x:2832,y:2832}],"
				+ "objects:[],shops:[],quests:[],bosses:[],raids:[]}"),
				"duplicate spawn key 'a'", null);
		expectFailure(area("{id:'dup-tile',name:'Dup',"
				+ "bounds:{minX:2830,minY:2830,maxX:2835,maxY:2835,plane:0},"
				+ "npcs:[],"
				+ "objects:[{key:'o1',objectId:2213,x:2831,y:2831},"
				+ "{key:'o2',objectId:2213,x:2831,y:2831}],"
				+ "shops:[],quests:[],bosses:[],raids:[]}"),
				"duplicate object tile", null);
	}

	@Test
	public void spawnOutsideBoundsAndUnknownMembersReject() {
		expectFailure(area("{id:'outside',name:'X',"
				+ "bounds:{minX:2830,minY:2830,maxX:2835,maxY:2835,plane:0},"
				+ "npcs:[{key:'a',npcId:153,x:2900,y:2900}],"
				+ "objects:[],shops:[],quests:[],bosses:[],raids:[]}"),
				"must lie inside the declared bounds", null);
		expectFailure(area("{id:'extra',name:'X',"
				+ "bounds:{minX:2830,minY:2830,maxX:2835,maxY:2835,plane:0},"
				+ "npcs:[],objects:[],shops:[],quests:[],bosses:[],raids:[],"
				+ "onLoad:function(){}}"), "unknown members", null);
	}

	@Test
	public void dropPolicyCouplingRejectsInconsistentSpawnsAndObjectDrops() {
		String base = "{id:'coupling',name:'X',"
				+ "bounds:{minX:2830,minY:2830,maxX:2835,maxY:2835,plane:0},"
				+ "npcs:[{key:'a',npcId:153,x:2831,y:2831,dropTable:'loot',"
				+ "dropPolicy:'private-to-killer'}],"
				+ "objects:[],shops:[],quests:[],bosses:[],raids:[]}";
		expectFailureWithTables(base,
				"'private-to-killer' delivery requires 'privateTicks'", null);
		expectFailureWithTables("{id:'coupling2',name:'X',"
				+ "bounds:{minX:2830,minY:2830,maxX:2835,maxY:2835,plane:0},"
				+ "npcs:[{key:'a',npcId:153,x:2831,y:2831,dropTable:'loot',"
				+ "dropPolicy:'public',privateTicks:200}],"
				+ "objects:[],shops:[],quests:[],bosses:[],raids:[]}",
				"'privateTicks' is not allowed for 'public' delivery", null);
	}

	@Test
	public void unloadedNpcAndObjectIdsRejectWhenDefinitionsAreLoaded() {
		NpcList[] list = new NpcList[NpcHandler.maxListedNPCs];
		list[0] = new NpcList(153);
		list[0].npcName = "test_npc";
		NpcHandler.NpcList = list;
		try {
			expectFailure(area("{id:'unloaded-npc',name:'X',"
					+ "bounds:{minX:2830,minY:2830,maxX:2835,maxY:2835,"
					+ "plane:0},"
					+ "npcs:[{key:'a',npcId:154,x:2831,y:2831}],"
					+ "objects:[],shops:[],quests:[],bosses:[],raids:[]}"),
					"npc id 154 has no loaded definition", null);
		} finally {
			ScriptRuntimeTestFixture.reset();
		}
		expectFailure(area("{id:'unloaded-object',name:'X',"
				+ "bounds:{minX:2830,minY:2830,maxX:2835,maxY:2835,plane:0},"
				+ "npcs:[],"
				+ "objects:[{key:'o',objectId:9999,x:2831,y:2831}],"
				+ "shops:[],quests:[],bosses:[],raids:[]}"),
				"object id 9999 has no loaded definition", null);
	}

	@Test
	public void missingShopAndDropReferencesRejectWithOrderDiagnostics() {
		expectFailure(area("{id:'orphan-shop',name:'X',"
				+ "bounds:{minX:2830,minY:2830,maxX:2835,maxY:2835,plane:0},"
				+ "npcs:[],objects:[],shops:['missing_shop'],quests:[],"
				+ "bosses:[],raids:[]}"),
				"referenced shop 'missing_shop' is not registered",
				"defineShop must run before defineArea");
		expectFailure(area("{id:'orphan-drop',name:'X',"
				+ "bounds:{minX:2830,minY:2830,maxX:2835,maxY:2835,plane:0},"
				+ "npcs:[{key:'a',npcId:153,x:2831,y:2831,"
				+ "dropTable:'missing_loot',dropPolicy:'public'}],"
				+ "objects:[],shops:[],quests:[],bosses:[],raids:[]}"),
				"named drop table 'missing_loot' is not registered",
				"defineDropTable must run before defineArea");
	}

	@Test
	public void shopOpeningSpawnMustBeStationary() {
		expectFailureWithTables("{id:'walking-shop',name:'X',"
				+ "bounds:{minX:2830,minY:2830,maxX:2835,maxY:2835,plane:0},"
				+ "npcs:[{key:'a',npcId:153,x:2831,y:2831,walkRadius:3,"
				+ "openShop:'island_general'}],"
				+ "objects:[],shops:[],quests:[],bosses:[],raids:[]}",
				"a shop-opening spawn must not walk away", null);
	}

	/**
	 * Rejection helper that first registers the canonical prerequisites
	 * (drop tables and the scripted shop) so the failure under test is the
	 * area rule itself rather than a missing-reference diagnostic.
	 */
	private void expectFailureWithTables(String areaJs, String messagePart,
			String messagePart2) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineDropTable().accept(table(
					"{id:'loot',entries:[{itemId:536,minAmount:1,"
							+ "maxAmount:1,weight:0,always:true}]}"));
			ScriptFunctions.getInstance().getDefineShop().accept(shop(
					"{id:'island_general',name:'Island',items:[{itemId:379,"
							+ "amount:5,price:100}]}"));
			try {
				ScriptFunctions.getInstance().getDefineArea()
						.accept(area(areaJs));
				fail("expected defineArea rejection for: " + areaJs);
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

	private static String canonical(int suffix) {
		return "{id:'test-area',name:'Test Area',"
				+ "bounds:{minX:2830,minY:2830,maxX:2835,maxY:2835,plane:0},"
				+ "npcs:[{key:'guardian-1',npcId:153,x:2830,y:2830,"
				+ "dropTable:'guardian_loot',dropPolicy:'private-to-killer',"
				+ "privateTicks:200,openShop:'island_general'}],"
				+ "objects:[{key:'ancient-chest',objectId:2213,x:2835,"
				+ "y:2835,drops:[{action:'first',dropTable:'chest_loot',"
				+ "dropPolicy:'public'}]}],"
				+ "shops:['island_general'],quests:[],bosses:[],raids:[],"
				+ "onEnter:function(){}}";
	}

	private void register(String areaJs) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineDropTable().accept(table(
					"{id:'guardian_loot',entries:[{itemId:536,minAmount:1,"
							+ "maxAmount:1,weight:0,always:true}]}"));
			ScriptFunctions.getInstance().getDefineDropTable().accept(table(
					"{id:'chest_loot',entries:[{itemId:995,minAmount:1,"
							+ "maxAmount:1,weight:0,always:true}]}"));
			ScriptFunctions.getInstance().getDefineShop().accept(shop(
					"{id:'island_general',name:'Island',items:[{itemId:379,"
							+ "amount:5,price:100}]}"));
			ScriptFunctions.getInstance().getDefineArea().accept(
					area(areaJs));
			ScriptHost.getInstance().publishForTesting(context, candidate);
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	private void expectFailure(Value areaValue, String messagePart,
			String messagePart2) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			try {
				ScriptFunctions.getInstance().getDefineArea()
						.accept(areaValue);
				fail("expected defineArea rejection for: " + areaValue);
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

	private Value area(String js) {
		return evalValue(js);
	}

	private Value table(String js) {
		return evalValue(js);
	}

	private Value shop(String js) {
		return evalValue(js);
	}

	private Value evalValue(String source) {
		return context.eval("js", "(" + source + ")");
	}

	private static ItemDefinition named(int id, String name) {
		ItemDefinition definition = new ItemDefinition(id);
		definition.setName(name);
		return definition;
	}

}
