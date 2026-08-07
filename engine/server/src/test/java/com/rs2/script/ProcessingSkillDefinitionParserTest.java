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

import com.rs2.script.processing.ProcessingSkillDefinition;
import com.rs2.script.processing.ProcessingSkillRegistry;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.ExecutableRouteRecord;
import com.rs2.script.route.RouteRegistry;

/**
 * Proves the canonical {@code defineProcessingSkill} schema-v1 contract.
 */
public class ProcessingSkillDefinitionParserTest {

	private Context context;
	private ItemDefinition[] previousItems;
	private ObjectDefinition[] previousObjects;

	@Before
	public void setUp() throws Exception {
		context = Context.create("js");
		previousItems = ItemDefinition.getDefinitions();
		previousObjects = ObjectDefinition.getDefinitions();
		ItemDefinition[] items = new ItemDefinition[8000];
		items[317] = named(317, "Raw shrimps");
		items[315] = named(315, "Shrimps");
		items[7954] = named(7954, "Burnt shrimp");
		items[775] = named(775, "Cooking gauntlets");
		Field field = ItemDefinition.class.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, items);
		ObjectDefinition[] objects = new ObjectDefinition[2500];
		objects[114] = new ObjectDefinition(114);
		Field objectField = ObjectDefinition.class.getDeclaredField(
				"definitions");
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
		Field objectField = ObjectDefinition.class.getDeclaredField(
				"definitions");
		objectField.setAccessible(true);
		objectField.set(null, previousObjects);
	}

	@Test
	public void canonicalSkillParsesIntoJavaOwnedDescriptorWithRoute() {
		register(canonical());

		ProcessingSkillDefinition skill = ProcessingSkillRegistry
				.get("cook-shrimp-range");
		assertNotNull(skill);
		assertEquals("cook-shrimp-range", skill.id());
		assertEquals("shrimp", skill.name());
		assertEquals(com.rs2.Constants.COOKING, skill.skill());
		assertEquals(1, skill.level());
		assertEquals(317, skill.inputItemId());
		assertEquals(114, skill.objectId());
		assertEquals(315, skill.productItemId());
		assertEquals(7954, skill.failProductItemId());
		assertEquals(30, skill.experience());
		assertEquals(896, skill.animation());
		assertEquals(357, skill.sound());
		assertEquals(4, skill.intervalTicks());
		assertEquals(34, skill.stopBurnLevel());
		assertEquals(30, skill.stopBurnLevelWithGloves());
		assertEquals(775, skill.glovesItemId());
		assertEquals(3, skill.burnBonus());

		ExecutableRouteRecord route = ScriptHost.getInstance()
				.readActiveRegistry(state -> RouteRegistry.get(state,
						ExecutableRouteKey.itemOnObject(317, 114)));
		assertNotNull(route);
		assertFalse("the processing route must be a Java host consumer",
				route.isGuest());
	}

	@Test
	public void duplicateSkillIdRejectsTheCandidate() {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineProcessingSkill()
					.accept(skill(canonical()));
			try {
				ScriptFunctions.getInstance().getDefineProcessingSkill()
						.accept(skill(canonical()));
				fail("expected duplicate processing id rejection");
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains("duplicate"));
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	@Test
	public void malformedDefinitionsFailWithClearErrors() {
		expectFailure(canonical().replace("stopBurnLevel:34",
				"stopBurnLevel:0"), "stopBurnLevel");
		expectFailure(canonical().replace("failProductItemId:7954,",
				"failProductItemId:317,"), "failProductItemId");
		expectFailure(canonical().replace("objectId:114", "objectId:999"),
				"no loaded definition");
		expectFailure(canonical().replace(",glovesItemId:775", ""),
				"requires 'glovesItemId'");
	}

	private void expectFailure(String skillJs, String messagePart) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			try {
				ScriptFunctions.getInstance().getDefineProcessingSkill()
						.accept(skill(skillJs));
				fail("expected defineProcessingSkill rejection for: "
						+ skillJs);
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains(messagePart));
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	private void register(String skillJs) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		ScriptFunctions.getInstance().getDefineProcessingSkill()
				.accept(skill(skillJs));
		ScriptRuntimeTestFixture.publishCandidate(context, candidate);
	}

	private Value skill(String body) {
		return context.eval("js", "(" + body + ")");
	}

	private static String canonical() {
		return "{id:'cook-shrimp-range',name:'shrimp',skill:'cooking',"
				+ "level:1,inputItemId:317,objectId:114,productItemId:315,"
				+ "failProductItemId:7954,experience:30,animation:896,"
				+ "sound:357,intervalTicks:4,stopBurnLevel:34,"
				+ "stopBurnLevelWithGloves:30,glovesItemId:775}";
	}

	private static ItemDefinition named(int id, String name) {
		ItemDefinition definition = new ItemDefinition(id);
		definition.setName(name);
		return definition;
	}
}
