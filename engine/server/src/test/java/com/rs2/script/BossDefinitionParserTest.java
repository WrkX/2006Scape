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
import com.rs2.script.boss.BossDefinition;
import com.rs2.script.boss.BossDefinitionRegistry;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.ExecutableRouteRecord;
import com.rs2.script.route.RouteRegistry;

/**
 * Proves the canonical {@code defineBoss} schema-v1 contract: strict members
 * and bounds, exactly one production entry route (command or object),
 * definition-backed npc/object ids when definitions are loaded, ordered
 * phases and special cooldowns, candidate-scoped named drop-table
 * resolution with private-TTL coupling, duplicate npc/stable-id rejection,
 * and the exact WP1 host routes registered by the standalone adapter.
 */
public class BossDefinitionParserTest {

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
		Field field = ItemDefinition.class.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, items);
		ObjectDefinition[] objects = new ObjectDefinition[500];
		objects[409] = new ObjectDefinition(409);
		Field objectField = ObjectDefinition.class.getDeclaredField("definitions");
		objectField.setAccessible(true);
		objectField.set(null, objects);
		// Deterministic unloaded-npc state: the strict boss parser enforces
		// definition-backed npc ids only while the npc.json list is loaded,
		// and other suites load it without restoring.
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
	public void canonicalCommandBossParsesIntoJavaOwnedDescriptor() {
		register(canonical(153, "test-boss", "test-boss"));

		BossDefinition boss = BossDefinitionRegistry.get(153);
		assertNotNull(boss);
		assertEquals("test-boss", boss.id());
		assertEquals(153, boss.npcId());
		assertEquals("Test Boss", boss.name());
		assertEquals(450, boss.combatLevel());
		assertEquals(600, boss.maxHitpoints());
		assertEquals(40, boss.maxHit());
		assertEquals(350, boss.attack());
		assertEquals(350, boss.defence());
		assertEquals(3200, boss.arena().minX());
		assertEquals(3210, boss.arena().maxX());
		assertEquals(0, boss.arena().plane());
		assertEquals(3205, boss.spawnX());
		assertEquals(3205, boss.spawnY());
		assertEquals("test-boss", boss.command());
		assertEquals("test-boss-close", boss.closeCommand());
		assertFalse(boss.hasObjectEntry());
		assertTrue(boss.hasEntryTeleport());
		assertEquals(3200, boss.entryTeleportX());
		assertEquals(3200, boss.entryTeleportY());
		assertNotNull(boss.onSpawn());
		assertNull(boss.onTick());
		assertNull(boss.onDeath());
		assertEquals(0, boss.phases().size());
		assertEquals(0, boss.specials().size());
		assertFalse(boss.hasDropTable());
		assertEquals(com.rs2.script.boss.BossCleanupPolicy.CLOSE_ON_TERMINAL,
				boss.cleanupPolicy());
		assertEquals(0, boss.schemaVersion());
		assertEquals(com.rs2.script.definition.ModuleScope.LEGACY_SOURCE,
				boss.source());

		// The exact WP1 host routes are registered with the candidate.
		ExecutableRouteRecord entry = ScriptHost.getInstance()
				.readActiveRegistry(state -> RouteRegistry.get(state,
						ExecutableRouteKey.command("test-boss")));
		assertNotNull(entry);
		assertFalse("the boss entry must be a Java host route",
				entry.isGuest());
		ExecutableRouteRecord close = ScriptHost.getInstance()
				.readActiveRegistry(state -> RouteRegistry.get(state,
						ExecutableRouteKey.command("test-boss-close")));
		assertNotNull(close);
		assertFalse(close.isGuest());
	}

	@Test
	public void objectEntryBossParsesAndRegistersExactObjectRoute() {
		register("{id:'object-boss',npcId:153,name:'Object Boss',"
				+ "combatLevel:100,maxHitpoints:200,maxHit:20,attack:100,"
				+ "defence:100,arena:{minX:3200,minY:3200,maxX:3210,"
				+ "maxY:3210,plane:0},spawn:{x:3205,y:3205},"
				+ "objectEntry:{objectId:409,action:'first'},"
				+ "onSpawn:function(){}}");

		BossDefinition boss = BossDefinitionRegistry.get(153);
		assertNotNull(boss);
		assertNull(boss.command());
		assertTrue(boss.hasObjectEntry());
		assertEquals(409, boss.objectEntryId());
		assertEquals("first", boss.objectEntryAction());
		ExecutableRouteRecord route = ScriptHost.getInstance()
				.readActiveRegistry(state -> RouteRegistry.get(state,
						ExecutableRouteKey.object(409, "first")));
		assertNotNull(route);
		assertFalse(route.isGuest());
	}

	@Test
	public void phasesAndSpecialsParseInOrderWithCooldowns() {
		register("{id:'phased-boss',npcId:153,combatLevel:100,"
				+ "maxHitpoints:200,maxHit:20,attack:100,defence:100,"
				+ "arena:{minX:3200,minY:3200,maxX:3210,maxY:3210,plane:0},"
				+ "spawn:{x:3205,y:3205},command:'phased-boss',"
				+ "onSpawn:function(){},onTick:function(){},"
				+ "onDeath:function(){},"
				+ "phases:["
				+ "{name:'Melee',hpPercentThreshold:100,onEnter:function(){}},"
				+ "{name:'Enrage',hpPercentThreshold:50,onEnter:function(){}}],"
				+ "specials:{fire_wave:{cooldownTicks:12,"
				+ "handler:function(){}}}}");

		BossDefinition boss = BossDefinitionRegistry.get(153);
		assertEquals(2, boss.phases().size());
		assertEquals("Melee", boss.phases().get(0).name());
		assertEquals(100, boss.phases().get(0).hpPercentThreshold());
		assertEquals("Enrage", boss.phases().get(1).name());
		assertEquals(50, boss.phases().get(1).hpPercentThreshold());
		assertNotNull(boss.onTick());
		assertNotNull(boss.onDeath());
		assertEquals(1, boss.specials().size());
		assertEquals(12, boss.specials().get("fire_wave").cooldownTicks());
		assertNotNull(boss.specials().get("fire_wave").handler());
	}

	@Test
	public void namedDropTableResolvesInCandidateAndCouplesPrivateTicks() {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineDropTable().accept(table(
					"{id:'boss_loot',entries:["
							+ "{itemId:536,minAmount:1,maxAmount:1,weight:0,"
							+ "always:true}]}"));
			ScriptFunctions.getInstance().getDefineBoss().accept(boss(
					"{id:'loot-boss',npcId:153,combatLevel:100,"
							+ "maxHitpoints:200,maxHit:20,attack:100,"
							+ "defence:100,arena:{minX:3200,minY:3200,"
							+ "maxX:3210,maxY:3210,plane:0},"
							+ "spawn:{x:3205,y:3205},command:'loot-boss',"
							+ "dropTable:'boss_loot',privateTicks:200,"
							+ "onSpawn:function(){}}"));
			ScriptHost.getInstance().publishForTesting(context, candidate);
		} finally {
			RegistryStore.rollback(candidate);
		}
		BossDefinition boss = BossDefinitionRegistry.get(153);
		assertTrue(boss.hasDropTable());
		assertEquals("boss_loot", boss.dropTable());
		assertEquals(200, boss.privateTicks());
	}

	@Test
	public void duplicateNpcIdRejectsWithBothRecords() {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineBoss().accept(
					canonical(153, "first-boss", "first-boss"));
			try {
				ScriptFunctions.getInstance().getDefineBoss().accept(
						canonical(153, "second-boss", "second-boss"));
				fail("duplicate npc id should reject");
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains(
						"duplicate registration"));
				assertTrue(expected.getMessage().contains("153"));
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	@Test
	public void duplicateStableIdAcrossNpcIdsRejectsTheCandidate() {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineBoss().accept(
					canonical(153, "shared-id", "shared-a"));
			try {
				ScriptFunctions.getInstance().getDefineBoss().accept(
						canonical(154, "shared-id", "shared-b"));
				fail("duplicate stable boss id should reject");
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains(
						"duplicate boss id 'shared-id'"));
				assertTrue(expected.getMessage().contains("already registered"));
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	@Test
	public void missingNamedDropTableRejectsWithOrderDiagnostic() {
		expectFailure("{id:'orphan-loot-boss',npcId:153,combatLevel:100,"
				+ "maxHitpoints:200,maxHit:20,attack:100,defence:100,"
				+ "arena:{minX:3200,minY:3200,maxX:3210,maxY:3210,plane:0},"
				+ "spawn:{x:3205,y:3205},command:'orphan-loot-boss',"
				+ "dropTable:'missing_loot',privateTicks:200,"
				+ "onSpawn:function(){}}",
				"named drop table 'missing_loot' is not registered in the "
						+ "loading candidate",
				"defineDropTable must run before defineBoss");
	}

	@Test
	public void unloadedNpcIdRejectsWhenDefinitionsAreLoaded()
			throws Exception {
		NpcList[] list = new NpcList[NpcHandler.maxListedNPCs];
		list[0] = new NpcList(153);
		list[0].npcName = "test_npc";
		NpcHandler.NpcList = list;
		try {
			register(canonical(153, "loaded-boss", "loaded-boss"));
			assertNotNull(BossDefinitionRegistry.get(153));
			expectFailure(canonical(154, "unloaded-boss", "unloaded-boss"),
					"npc id 154 has no loaded definition", null);
		} finally {
			ScriptRuntimeTestFixture.reset();
		}
	}

	@Test
	public void entryTeleportOutsideArenaRejectsAsAuthoringError() {
		expectFailure(canonicalWithTeleport(153, "teleport-boss",
				"teleport-boss", 3211, 3205),
				"entryTeleport (3211, 3205) must lie inside the declared "
						+ "arena", null);
	}

	@Test
	public void unloadedObjectEntryRejectsWhenDefinitionsAreLoaded() {
		expectFailure("{id:'unloaded-object',npcId:153,combatLevel:100,"
				+ "maxHitpoints:200,maxHit:20,attack:100,defence:100,"
				+ "arena:{minX:3200,minY:3200,maxX:3210,maxY:3210,plane:0},"
				+ "spawn:{x:3205,y:3205},"
				+ "objectEntry:{objectId:9999,action:'first'},"
				+ "onSpawn:function(){}}",
				"objectEntry.objectId 9999 has no loaded definition", null);
	}

	@Test
	public void reservedAdminCommandRejectsViaRouteRegistration() {
		expectFailure(canonical(153, "reserved-boss", "reload"),
				"command alias is reserved for the engine admin transport",
				null);
	}

	@Test
	public void invalidDefinitionsRejectWithSourceDiagnostics() {
		String[] invalid = {
				// Unknown member
				"{id:'x',npcId:153,combatLevel:1,maxHitpoints:10,maxHit:1,"
						+ "attack:1,defence:1,arena:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},spawn:{x:3205,y:3205},"
						+ "command:'x',onSpawn:function(){},extra:1}",
				// Missing onSpawn
				"{id:'x',npcId:153,combatLevel:1,maxHitpoints:10,maxHit:1,"
						+ "attack:1,defence:1,arena:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},spawn:{x:3205,y:3205},"
						+ "command:'x'}",
				// Missing entry route
				"{id:'x',npcId:153,combatLevel:1,maxHitpoints:10,maxHit:1,"
						+ "attack:1,defence:1,arena:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},spawn:{x:3205,y:3205},"
						+ "onSpawn:function(){}}",
				// Both entry routes
				"{id:'x',npcId:153,combatLevel:1,maxHitpoints:10,maxHit:1,"
						+ "attack:1,defence:1,arena:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},spawn:{x:3205,y:3205},"
						+ "command:'x',objectEntry:{objectId:409,"
						+ "action:'first'},onSpawn:function(){}}",
				// Non-executable onSpawn
				"{id:'x',npcId:153,combatLevel:1,maxHitpoints:10,maxHit:1,"
						+ "attack:1,defence:1,arena:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},spawn:{x:3205,y:3205},"
						+ "command:'x',onSpawn:42}",
				// Inverted arena
				"{id:'x',npcId:153,combatLevel:1,maxHitpoints:10,maxHit:1,"
						+ "attack:1,defence:1,arena:{minX:3210,minY:3200,"
						+ "maxX:3200,maxY:3210,plane:0},spawn:{x:3205,y:3205},"
						+ "command:'x',onSpawn:function(){}}",
				// Oversized arena side
				"{id:'x',npcId:153,combatLevel:1,maxHitpoints:10,maxHit:1,"
						+ "attack:1,defence:1,arena:{minX:3200,minY:3200,"
						+ "maxX:3264,maxY:3210,plane:0},spawn:{x:3205,y:3205},"
						+ "command:'x',onSpawn:function(){}}",
				// Spawn outside arena
				"{id:'x',npcId:153,combatLevel:1,maxHitpoints:10,maxHit:1,"
						+ "attack:1,defence:1,arena:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},spawn:{x:3211,y:3205},"
						+ "command:'x',onSpawn:function(){}}",
				// Uppercase command
				"{id:'x',npcId:153,combatLevel:1,maxHitpoints:10,maxHit:1,"
						+ "attack:1,defence:1,arena:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},spawn:{x:3205,y:3205},"
						+ "command:'Boss-Cmd',onSpawn:function(){}}",
				// Non-descending phases
				"{id:'x',npcId:153,combatLevel:1,maxHitpoints:10,maxHit:1,"
						+ "attack:1,defence:1,arena:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},spawn:{x:3205,y:3205},"
						+ "command:'x',onSpawn:function(){},phases:["
						+ "{name:'Low',hpPercentThreshold:50,"
						+ "onEnter:function(){}},"
						+ "{name:'High',hpPercentThreshold:100,"
						+ "onEnter:function(){}}]}",
				// Duplicate phase names
				"{id:'x',npcId:153,combatLevel:1,maxHitpoints:10,maxHit:1,"
						+ "attack:1,defence:1,arena:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},spawn:{x:3205,y:3205},"
						+ "command:'x',onSpawn:function(){},phases:["
						+ "{name:'Same',hpPercentThreshold:100,"
						+ "onEnter:function(){}},"
						+ "{name:'Same',hpPercentThreshold:50,"
						+ "onEnter:function(){}}]}",
				// Phase threshold out of bounds
				"{id:'x',npcId:153,combatLevel:1,maxHitpoints:10,maxHit:1,"
						+ "attack:1,defence:1,arena:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},spawn:{x:3205,y:3205},"
						+ "command:'x',onSpawn:function(){},phases:["
						+ "{name:'Bad',hpPercentThreshold:101,"
						+ "onEnter:function(){}}]}",
				// Zero cooldown special
				"{id:'x',npcId:153,combatLevel:1,maxHitpoints:10,maxHit:1,"
						+ "attack:1,defence:1,arena:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},spawn:{x:3205,y:3205},"
						+ "command:'x',onSpawn:function(){},specials:{"
						+ "fire:{cooldownTicks:0,handler:function(){}}}}",
				// Invalid special name
				"{id:'x',npcId:153,combatLevel:1,maxHitpoints:10,maxHit:1,"
						+ "attack:1,defence:1,arena:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},spawn:{x:3205,y:3205},"
						+ "command:'x',onSpawn:function(){},specials:{"
						+ "Fire_Wave:{cooldownTicks:1,handler:function(){}}}}",
				// privateTicks without dropTable
				"{id:'x',npcId:153,combatLevel:1,maxHitpoints:10,maxHit:1,"
						+ "attack:1,defence:1,arena:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},spawn:{x:3205,y:3205},"
						+ "command:'x',onSpawn:function(){},privateTicks:200}",
				// dropTable without privateTicks
				"{id:'x',npcId:153,combatLevel:1,maxHitpoints:10,maxHit:1,"
						+ "attack:1,defence:1,arena:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},spawn:{x:3205,y:3205},"
						+ "command:'x',onSpawn:function(){},dropTable:'boss_loot'}",
				// Invalid cleanup policy
				"{id:'x',npcId:153,combatLevel:1,maxHitpoints:10,maxHit:1,"
						+ "attack:1,defence:1,arena:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},spawn:{x:3205,y:3205},"
						+ "command:'x',onSpawn:function(){},"
						+ "cleanupPolicy:'retain-forever'}"
		};
		for (String source : invalid) {
			expectFailure(source, null, null);
		}
	}

	private void register(String source) {
		register(boss(source));
	}

	private void register(Value definition) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineBoss().accept(definition);
			ScriptHost.getInstance().publishForTesting(context, candidate);
		} catch (RuntimeException error) {
			RegistryStore.rollback(candidate);
			throw error;
		}
	}

	private void expectFailure(Value definition, String expectedMessage,
			String expectedDetail) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineBoss().accept(definition);
			fail("Expected boss registration to fail");
		} catch (IllegalArgumentException expected) {
			if (expectedMessage != null) {
				assertTrue(expected.getMessage().contains(expectedMessage));
			}
			if (expectedDetail != null) {
				assertTrue(expected.getMessage().contains(expectedDetail));
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	private void expectFailure(String source, String expectedMessage,
			String expectedDetail) {
		expectFailure(boss(source), expectedMessage, expectedDetail);
	}

	/** Compact canonical boss source with command entry. */
	private Value canonical(int npcId, String id, String command) {
		return canonicalWithTeleport(npcId, id, command, 3200, 3200);
	}

	private Value canonicalWithTeleport(int npcId, String id, String command,
			int teleportX, int teleportY) {
		return boss("{id:'" + id + "',npcId:" + npcId
				+ ",name:'Test Boss',combatLevel:450,maxHitpoints:600,"
				+ "maxHit:40,attack:350,defence:350,"
				+ "arena:{minX:3200,minY:3200,maxX:3210,maxY:3210,plane:0},"
				+ "spawn:{x:3205,y:3205},command:'" + command
				+ "',closeCommand:'" + command
				+ "-close',entryTeleport:{x:" + teleportX + ",y:" + teleportY
				+ "},onSpawn:function(){}}");
	}

	private Value boss(String source) {
		return context.eval("js", "(" + source + ")");
	}

	private Value table(String source) {
		return context.eval("js", "(" + source + ")");
	}

	private static ItemDefinition named(int id, String name) {
		ItemDefinition definition = new ItemDefinition(id);
		definition.setName(name);
		return definition;
	}

}
