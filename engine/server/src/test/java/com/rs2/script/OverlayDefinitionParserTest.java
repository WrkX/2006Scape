package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.apollo.cache.def.ItemDefinition;
import org.apollo.cache.def.ObjectDefinition;

import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.npcs.NpcList;
import com.rs2.script.overlay.ItemOverlayDefinition;
import com.rs2.script.overlay.ItemOverlayDefinitionRegistry;
import com.rs2.script.overlay.NpcOverlayDefinition;
import com.rs2.script.overlay.NpcOverlayDefinitionRegistry;
import com.rs2.script.overlay.ObjectOverlayDefinition;
import com.rs2.script.overlay.ObjectOverlayDefinitionRegistry;
import com.rs2.script.registries.RegistryStore;

/**
 * Proves the canonical overlay schema-v1 contracts.
 */
public class OverlayDefinitionParserTest {

	private static final int CUSTOM_ITEM = 35000;
	private static final int CUSTOM_NPC = 35000;
	private static final int CUSTOM_OBJECT = 35000;

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
		NpcHandler.NpcList = new NpcList[NpcHandler.maxListedNPCs];
		ensureCustomDefinitions();
	}

	@After
	public void restore() throws Exception {
		ScriptRuntimeTestFixture.reset();
		if (context != null) {
			context.close();
		}
		setDefinitions(ItemDefinition.class, previousItems);
		setDefinitions(ObjectDefinition.class, previousObjects);
		System.arraycopy(previousNpcList, 0, NpcHandler.NpcList, 0,
				previousNpcList.length);
	}

	@Test
	public void canonicalItemOverlayParsesIntoJavaOwnedDescriptor() {
		registerItem(canonicalItem());

		ItemOverlayDefinition overlay = ItemOverlayDefinitionRegistry
				.get(CUSTOM_ITEM);
		assertNotNull(overlay);
		assertEquals("ported-bronze-sword", overlay.id());
		assertEquals(CUSTOM_ITEM, overlay.itemId());
		assertEquals("Ported bronze sword", overlay.name());
		assertEquals("A sword from the custom asset namespace.",
				overlay.examine());
		assertEquals("weapon", overlay.equipSlot());
		assertNotNull(overlay.requirements());
		assertEquals(4, overlay.bonuses()[0]);
	}

	@Test
	public void unknownItemIdRejectsTheCandidate() {
		expectItemFailure(canonicalItem()
				.replace("itemId:" + CUSTOM_ITEM, "itemId:34999"),
				"no loaded definition");
	}

	@Test
	public void duplicateItemIdRejectsTheCandidate() {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineItemOverlay()
					.accept(overlay(canonicalItem()));
			try {
				ScriptFunctions.getInstance().getDefineItemOverlay()
						.accept(overlay(canonicalItem()
								.replace("id:'ported-bronze-sword'",
										"id:'ported-bronze-sword-alt'")));
				fail("expected duplicate itemId rejection");
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains("duplicate"));
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	@Test
	public void canonicalNpcOverlayParsesIntoJavaOwnedDescriptor() {
		NpcHandler.NpcList[0] = new NpcList(CUSTOM_NPC);
		NpcHandler.NpcList[0].npcName = "Cache guard";
		registerNpc(canonicalNpc());

		NpcOverlayDefinition overlay = NpcOverlayDefinitionRegistry
				.get(CUSTOM_NPC);
		assertNotNull(overlay);
		assertEquals("ported-town-guard", overlay.id());
		assertEquals(Integer.valueOf(21), overlay.combatLevel());
		assertEquals(Integer.valueOf(22), overlay.hitpoints());
	}

	@Test
	public void canonicalObjectOverlayParsesIntoJavaOwnedDescriptor() {
		registerObject(canonicalObject());

		ObjectOverlayDefinition overlay = ObjectOverlayDefinitionRegistry
				.get(CUSTOM_OBJECT);
		assertNotNull(overlay);
		assertEquals("ported-signpost", overlay.id());
		assertEquals("Read", overlay.actions()[0]);
	}

	@Test
	public void emptyOverlayRejectsTheCandidate() {
		expectItemFailure("{id:'empty',itemId:" + CUSTOM_ITEM + "}",
				"at least one field");
	}

	private void expectItemFailure(String overlayJs, String messagePart) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			try {
				ScriptFunctions.getInstance().getDefineItemOverlay()
						.accept(overlay(overlayJs));
				fail("expected defineItemOverlay rejection for: " + overlayJs);
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains(messagePart));
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	private void registerItem(String overlayJs) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		ScriptFunctions.getInstance().getDefineItemOverlay()
				.accept(overlay(overlayJs));
		ScriptRuntimeTestFixture.publishCandidate(context, candidate);
	}

	private void registerNpc(String overlayJs) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		ScriptFunctions.getInstance().getDefineNpcOverlay()
				.accept(overlay(overlayJs));
		ScriptRuntimeTestFixture.publishCandidate(context, candidate);
	}

	private void registerObject(String overlayJs) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		ScriptFunctions.getInstance().getDefineObjectOverlay()
				.accept(overlay(overlayJs));
		ScriptRuntimeTestFixture.publishCandidate(context, candidate);
	}

	private Value overlay(String body) {
		return context.eval("js", "(" + body + ")");
	}

	private static String canonicalItem() {
		return "{id:'ported-bronze-sword',itemId:" + CUSTOM_ITEM
				+ ",name:'Ported bronze sword',examine:'A sword from the "
				+ "custom asset namespace.',equipSlot:'weapon',"
				+ "requirements:{attack:1},bonuses:{attackStab:4,"
				+ "attackSlash:3,strength:5}}";
	}

	private static String canonicalNpc() {
		return "{id:'ported-town-guard',npcId:" + CUSTOM_NPC
				+ ",name:'Ported town guard',combatLevel:21,hitpoints:22}";
	}

	private static String canonicalObject() {
		return "{id:'ported-signpost',objectId:" + CUSTOM_OBJECT
				+ ",name:'Ported signpost',examine:'A signpost shipped "
				+ "through the asset pipeline.',actions:['Read']}";
	}

	private static void ensureCustomDefinitions() throws Exception {
		ItemDefinition[] items = new ItemDefinition[CUSTOM_ITEM + 1];
		items[CUSTOM_ITEM] = new ItemDefinition(CUSTOM_ITEM);
		items[CUSTOM_ITEM].setName("Cache bronze sword");
		setDefinitions(ItemDefinition.class, items);

		ObjectDefinition[] objects = new ObjectDefinition[CUSTOM_OBJECT + 1];
		objects[CUSTOM_OBJECT] = new ObjectDefinition(CUSTOM_OBJECT);
		objects[CUSTOM_OBJECT].setName("Cache signpost");
		setDefinitions(ObjectDefinition.class, objects);
	}

	private static void setDefinitions(Class<?> type, Object[] definitions)
			throws Exception {
		java.lang.reflect.Field field = type.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, definitions);
	}
}
