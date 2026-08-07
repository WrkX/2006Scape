package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.graalvm.polyglot.Context;
import org.apollo.cache.def.EquipmentDefinition;
import org.apollo.cache.def.ItemDefinition;
import org.apollo.cache.def.ObjectDefinition;

import com.rs2.game.items.ItemData;
import com.rs2.game.items.ItemDefinitions;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.npcs.NpcList;
import com.rs2.script.overlay.ItemOverlayDefinition;
import com.rs2.script.overlay.ItemOverlayDefinitionRegistry;
import com.rs2.script.overlay.NpcOverlayDefinition;
import com.rs2.script.overlay.NpcOverlayDefinitionRegistry;
import com.rs2.script.overlay.ObjectOverlayDefinition;
import com.rs2.script.overlay.ObjectOverlayDefinitionRegistry;
import com.rs2.script.overlay.ScriptOverlayRuntime;

/**
 * End-to-end overlay merge and reload behavior.
 */
public class ScriptOverlayPortE2ETest {

	private static final int CUSTOM_ITEM = 35000;
	private static final int CUSTOM_NPC = 35000;
	private static final int CUSTOM_OBJECT = 35000;

	private ItemDefinition[] previousItems;
	private ObjectDefinition[] previousObjects;
	private NpcList[] previousNpcList;
	private String previousContentDir;

	@Before
	public void setUp() throws Exception {
		previousContentDir = System.getProperty("singlescape.contentDir");
		previousItems = ItemDefinition.getDefinitions();
		previousObjects = ObjectDefinition.getDefinitions();
		previousNpcList = NpcHandler.NpcList.clone();
		ScriptOverlayRuntime.installForTesting();
	}

	@After
	public void tearDown() throws Exception {
		ScriptRuntimeTestFixture.reset();
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
		setDefinitions(ItemDefinition.class, previousItems);
		setDefinitions(ObjectDefinition.class, previousObjects);
		System.arraycopy(previousNpcList, 0, NpcHandler.NpcList, 0,
				previousNpcList.length);
	}

	@Test
	public void compiledOverlayModuleMergesCustomNamespaceDefinitions()
			throws Exception {
		File contentDir = findCompiledContent();
		assertTrue("Run pnpm build:content before Maven tests",
				contentDir.isDirectory());
		System.setProperty("singlescape.contentDir",
				contentDir.getAbsolutePath());
		Wp5PlayerSupport.ensureItemDefinitions();
		Wp5PlayerSupport.ensureObjectDefinitions();
		Wp5PlayerSupport.ensureNpcDefinitions();
		Wp5PlayerSupport.ensureCustomNamespaceDefinitions();
		Wp5PlayerSupport.ensureAreaRegions();
		ScriptHost.getInstance().reload();

		assertNotNull(ItemOverlayDefinitionRegistry.get(CUSTOM_ITEM));
		assertNotNull(NpcOverlayDefinitionRegistry.get(CUSTOM_NPC));
		assertNotNull(ObjectOverlayDefinitionRegistry.get(CUSTOM_OBJECT));

		ItemDefinition item = ItemDefinition.lookup(CUSTOM_ITEM);
		assertEquals("Ported bronze sword", item.getName());
		assertEquals("A sword from the custom asset namespace.",
				item.getDescription());
		assertEquals(4, ItemDefinitions.getBonus(CUSTOM_ITEM)[0]);
		assertEquals(ItemData.targetSlots[CUSTOM_ITEM],
				EquipmentDefinition.lookup(CUSTOM_ITEM).getSlot());

		NpcList npc = findNpc(CUSTOM_NPC);
		assertNotNull(npc);
		assertEquals("Ported town guard", npc.npcName);
		assertEquals(21, npc.npcCombat);
		assertEquals(22, npc.npcHealth);

		ObjectDefinition object = ObjectDefinition.lookup(CUSTOM_OBJECT);
		assertEquals("Ported signpost", object.getName());
		assertEquals("A signpost shipped through the asset pipeline.",
				object.getDescription());
		assertEquals("Read", object.getMenuActions()[0]);
		assertTrue(object.isInteractive());
	}

	@Test
	public void midPublishThrowStillRevertsAppliedOverlays() throws Exception {
		Wp5PlayerSupport.ensureItemDefinitions();
		Wp5PlayerSupport.ensureObjectDefinitions();
		Wp5PlayerSupport.ensureNpcDefinitions();
		Wp5PlayerSupport.ensureCustomNamespaceDefinitions();

		String baseline = ItemDefinition.lookup(CUSTOM_ITEM).getName();
		int outOfBounds = ObjectDefinition.getDefinitions().length;

		// Publish a generation that applies a valid item overlay, then throws
		// mid-apply on an out-of-bounds object overlay registered directly into
		// staging (bypassing the parse-time cache gate). The item mutation
		// applied before the throw must be recorded so the next close reverts it.
		Context context = Context.create("js");
		try {
			ScriptRuntimeTestFixture.publish(context, new Runnable() {
				@Override
				public void run() {
					ItemOverlayDefinitionRegistry.put(new ItemOverlayDefinition(
							"mid-item", CUSTOM_ITEM, "Mid-publish sword", null,
							null, null, null, null, "test", 1));
					ObjectOverlayDefinitionRegistry.put(
							new ObjectOverlayDefinition("oob-object",
									outOfBounds, "OOB", null,
									new String[] { "Read" }, "test", 1));
				}
			});
		} catch (IndexOutOfBoundsException expected) {
			// The publish hook applied the item overlay before hitting the
			// out-of-bounds object id.
		}

		// The failed generation must not have reverted the item overlay yet; the
		// next publish's close-before-publish step reverts it.
		assertEquals("the item overlay must have been applied before the throw",
				"Mid-publish sword", ItemDefinition.lookup(CUSTOM_ITEM).getName());

		// Re-publish with no overlays: the previous generation's close reverts
		// the item to its pre-overlay baseline.
		ScriptRuntimeTestFixture.publishEmpty(context);
		assertEquals("the failed publish must revert overlays applied before "
				+ "the mid-publish throw", baseline,
				ItemDefinition.lookup(CUSTOM_ITEM).getName());
	}

	@Test
	public void reloadRevertsAndReappliesOverlays() throws Exception {
		File contentDir = findCompiledContent();
		assertTrue("Run pnpm build:content before Maven tests",
				contentDir.isDirectory());
		System.setProperty("singlescape.contentDir",
				contentDir.getAbsolutePath());
		Wp5PlayerSupport.ensureItemDefinitions();
		Wp5PlayerSupport.ensureObjectDefinitions();
		Wp5PlayerSupport.ensureNpcDefinitions();
		Wp5PlayerSupport.ensureCustomNamespaceDefinitions();
		Wp5PlayerSupport.ensureAreaRegions();
		ScriptHost.getInstance().reload();
		assertEquals("Ported bronze sword",
				ItemDefinition.lookup(CUSTOM_ITEM).getName());

		ScriptHost.getInstance().reload();
		assertEquals("Ported bronze sword",
				ItemDefinition.lookup(CUSTOM_ITEM).getName());
	}

	private static NpcList findNpc(int npcId) {
		for (int index = 0; index < NpcHandler.maxListedNPCs; index++) {
			NpcList entry = NpcHandler.NpcList[index];
			if (entry != null && entry.npcId == npcId) {
				return entry;
			}
		}
		return null;
	}

	private static void setDefinitions(Class<?> type, Object[] definitions)
			throws Exception {
		java.lang.reflect.Field field = type.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, definitions);
	}

	private static File findCompiledContent() {
		File cwd = new File(System.getProperty("user.dir"));
		File[] candidates = {
				new File(cwd, "../../content/dist"),
				new File(cwd, "../content/dist"),
				new File(cwd, "content/dist"),
		};
		for (File candidate : candidates) {
			if (candidate.isDirectory()) {
				return candidate;
			}
		}
		return candidates[0];
	}
}
