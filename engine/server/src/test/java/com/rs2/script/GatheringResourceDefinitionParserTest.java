package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

import com.rs2.script.registries.RegistryStore;
import com.rs2.script.resource.GatheringResourceDefinition;
import com.rs2.script.resource.GatheringResourceRegistry;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.ExecutableRouteRecord;
import com.rs2.script.route.RouteRegistry;

/**
 * Proves the canonical {@code defineGatheringResource} schema-v1 contract:
 * strict members and bounds, canonical skill/level, ordered unique tools,
 * bounded rewards, a definition-backed object id and depleted id, the
 * deterministic success-chance contract, the canonical host object route,
 * and duplicate-resource rejection.
 */
public class GatheringResourceDefinitionParserTest {

	private Context context;
	private ItemDefinition[] previousItems;
	private ObjectDefinition[] previousObjects;

	@Before
	public void setUp() throws Exception {
		context = Context.create("js");
		previousItems = ItemDefinition.getDefinitions();
		previousObjects = ObjectDefinition.getDefinitions();
		ItemDefinition[] items = new ItemDefinition[2500];
		items[1351] = named(1351, "Bronze axe");
		items[1511] = named(1511, "Logs");
		items[1521] = named(1521, "Oak logs");
		Field field = ItemDefinition.class.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, items);
		ObjectDefinition[] objects = new ObjectDefinition[2500];
		objects[1276] = new ObjectDefinition(1276);
		objects[1281] = new ObjectDefinition(1281);
		objects[1341] = new ObjectDefinition(1341);
		Field objectField = ObjectDefinition.class.getDeclaredField("definitions");
		objectField.setAccessible(true);
		objectField.set(null, objects);
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
	}

	@Test
	public void canonicalResourceParsesIntoJavaOwnedDescriptorWithRoute() {
		register(canonical());

		GatheringResourceDefinition resource = GatheringResourceRegistry
				.get("test-tree");
		assertNotNull(resource);
		assertEquals("test-tree", resource.id());
		assertEquals("Tree", resource.name());
		assertEquals(1276, resource.objectId());
		assertEquals("first", resource.action());
		assertEquals(com.rs2.Constants.WOODCUTTING, resource.skill());
		assertEquals(1, resource.level());
		assertEquals(1, resource.tools().size());
		assertEquals(1351, resource.tools().get(0).itemId());
		assertFalse(resource.tools().get(0).consume());
		assertEquals(879, resource.animation());
		assertEquals(4, resource.intervalTicks());
		assertEquals(3, resource.successNumerator());
		assertEquals(4, resource.successDenominator());
		assertEquals(1, resource.rewards().size());
		assertEquals(1511, resource.rewards().get(0).itemId());
		assertEquals(1, resource.rewards().get(0).amount());
		assertEquals(25, resource.experience());
		assertEquals(1341, resource.depletedObjectId());
		assertEquals(4, resource.respawnTicks());
		assertEquals(0, resource.schemaVersion());
		assertEquals(com.rs2.script.definition.ModuleScope.LEGACY_SOURCE,
				resource.source());

		ExecutableRouteRecord route = ScriptHost.getInstance()
				.readActiveRegistry(state -> RouteRegistry.get(state,
						ExecutableRouteKey.object(1276, "first")));
		assertNotNull(route);
		assertFalse("the resource route must be a Java host consumer",
				route.isGuest());
	}

	@Test
	public void duplicateResourceIdRejectsTheCandidate() {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineGatheringResource()
					.accept(resource(canonical()));
			try {
				ScriptFunctions.getInstance().getDefineGatheringResource()
						.accept(resource(canonical()));
				fail("duplicate resource id must reject the candidate");
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains("duplicate"));
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	@Test
	public void missingToolsOrRewardsRejects() {
		expectFailure("{id:'tree',name:'Tree',objectId:1276,action:'first',"
				+ "skill:'woodcutting',level:1,tools:[],animation:879,"
				+ "intervalTicks:4,successChance:{numerator:3,denominator:4},"
				+ "rewards:[{itemId:1511,amount:1}],experience:25,"
				+ "depletedObjectId:1341,respawnTicks:4}", "at least one tool");
	}

	@Test
	public void invalidChanceAndEqualDepletedIdReject() {
		expectFailure("{id:'tree',name:'Tree',objectId:1276,action:'first',"
				+ "skill:'woodcutting',level:1,tools:[{itemId:1351}],"
				+ "animation:879,intervalTicks:4,"
				+ "successChance:{numerator:5,denominator:4},"
				+ "rewards:[{itemId:1511,amount:1}],experience:25,"
				+ "depletedObjectId:1341,respawnTicks:4}", "not exceed");
		expectFailure("{id:'tree',name:'Tree',objectId:1276,action:'first',"
				+ "skill:'woodcutting',level:1,tools:[{itemId:1351}],"
				+ "animation:879,intervalTicks:4,"
				+ "successChance:{numerator:3,denominator:4},"
				+ "rewards:[{itemId:1511,amount:1}],experience:25,"
				+ "depletedObjectId:1276,respawnTicks:4}", "differ");
	}

	@Test
	public void unloadedObjectOrItemIdRejects() {
		expectFailure("{id:'tree',name:'Tree',objectId:999,action:'first',"
				+ "skill:'woodcutting',level:1,tools:[{itemId:1351}],"
				+ "animation:879,intervalTicks:4,"
				+ "successChance:{numerator:3,denominator:4},"
				+ "rewards:[{itemId:1511,amount:1}],experience:25,"
				+ "depletedObjectId:1341,respawnTicks:4}",
				"no loaded definition");
		expectFailure("{id:'tree',name:'Tree',objectId:1276,action:'first',"
				+ "skill:'woodcutting',level:1,tools:[{itemId:999}],"
				+ "animation:879,intervalTicks:4,"
				+ "successChance:{numerator:3,denominator:4},"
				+ "rewards:[{itemId:1511,amount:1}],experience:25,"
				+ "depletedObjectId:1341,respawnTicks:4}",
				"no loaded definition");
	}

	@Test
	public void duplicateToolAndUnknownSkillReject() {
		expectFailure("{id:'tree',name:'Tree',objectId:1276,action:'first',"
				+ "skill:'woodcutting',level:1,tools:[{itemId:1351},"
				+ "{itemId:1351}],animation:879,intervalTicks:4,"
				+ "successChance:{numerator:3,denominator:4},"
				+ "rewards:[{itemId:1511,amount:1}],experience:25,"
				+ "depletedObjectId:1341,respawnTicks:4}", "duplicate tool");
		expectFailure("{id:'tree',name:'Tree',objectId:1276,action:'first',"
				+ "skill:'runecrafting',level:1,tools:[{itemId:1351}],"
				+ "animation:879,intervalTicks:4,"
				+ "successChance:{numerator:3,denominator:4},"
				+ "rewards:[{itemId:1511,amount:1}],experience:25,"
				+ "depletedObjectId:1341,respawnTicks:4}",
				"Unknown quest skill");
	}

	// ─── Helpers ────────────────────────────────────────────────────────────

	private void register(String resourceJs) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineGatheringResource()
					.accept(resource(resourceJs));
			ScriptHost.getInstance().publishForTesting(context, candidate);
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	private void expectFailure(String resourceJs, String messagePart) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			try {
				ScriptFunctions.getInstance().getDefineGatheringResource()
						.accept(resource(resourceJs));
				fail("expected defineGatheringResource rejection for: "
						+ resourceJs);
			} catch (IllegalArgumentException expected) {
				if (messagePart != null) {
					assertTrue("missing '" + messagePart + "' in: "
							+ expected.getMessage(),
							expected.getMessage().contains(messagePart));
				}
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	private Value resource(String js) {
		return context.eval("js", "(" + js + ")");
	}

	private static String canonical() {
		return "{id:'test-tree',name:'Tree',objectId:1276,action:'first',"
				+ "skill:'woodcutting',level:1,tools:[{itemId:1351}],"
				+ "animation:879,intervalTicks:4,"
				+ "successChance:{numerator:3,denominator:4},"
				+ "rewards:[{itemId:1511,amount:1}],experience:25,"
				+ "depletedObjectId:1341,respawnTicks:4}";
	}

	private static ItemDefinition named(int id, String name) {
		ItemDefinition definition = new ItemDefinition(id);
		definition.setName(name);
		return definition;
	}

}
