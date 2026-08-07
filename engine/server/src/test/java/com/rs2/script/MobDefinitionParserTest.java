package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.npcs.NpcList;
import com.rs2.script.mob.MobCombatStyle;
import com.rs2.script.mob.MobDefinition;
import com.rs2.script.mob.MobDefinitionRegistry;
import com.rs2.script.registries.RegistryStore;

/**
 * Proves the canonical {@code defineMob} schema-v1 contract.
 */
public class MobDefinitionParserTest {

	private Context context;
	private NpcList[] previousNpcList;

	@Before
	public void setUp() throws Exception {
		context = Context.create("js");
		previousNpcList = NpcHandler.NpcList.clone();
		NpcHandler.NpcList = new NpcList[NpcHandler.maxListedNPCs];
	}

	@After
	public void restore() throws Exception {
		ScriptRuntimeTestFixture.reset();
		if (context != null) {
			context.close();
		}
		System.arraycopy(previousNpcList, 0, NpcHandler.NpcList, 0,
				previousNpcList.length);
	}

	@Test
	public void canonicalMobParsesIntoJavaOwnedDescriptor() {
		register(canonical());

		MobDefinition mob = MobDefinitionRegistry.get(100);
		assertNotNull(mob);
		assertEquals("goblin", mob.id());
		assertEquals(100, mob.npcId());
		assertEquals("Goblin", mob.name());
		assertEquals(0, mob.aggression());
		assertEquals(MobCombatStyle.MELEE, mob.combatStyle());
		assertEquals(4, mob.attackSpeed());
		assertEquals(1, mob.maxHit());
		assertEquals(-1, mob.animation());
		assertNull(mob.onSpawn());
		assertNull(mob.onTick());
		assertNull(mob.onDeath());
	}

	@Test
	public void duplicateNpcIdRejectsTheCandidate() {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineMob()
					.accept(mob(canonical()));
			try {
				ScriptFunctions.getInstance().getDefineMob()
						.accept(mob(canonical().replace("id:'goblin'",
								"id:'goblin-alt'")));
				fail("expected duplicate npcId rejection");
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains("duplicate"));
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	@Test
	public void malformedDefinitionsFailWithClearErrors() {
		expectFailure(canonical().replace("aggression:0", "aggression:65"),
				"aggression");
		expectFailure(canonical().replace("combatStyle:'melee'",
				"combatStyle:'fire'"), "combatStyle");
		expectFailure(canonical().replace("attackSpeed:4", "attackSpeed:0"),
				"attackSpeed");
		expectFailure("{id:'goblin',npcId:100,aggression:0,"
				+ "combatStyle:'melee',attackSpeed:4,maxHit:1,"
				+ "unknown:true}", "unknown member");
	}

	@Test
	public void callbacksAreCapturedWhenPresent() {
		register("{id:'goblin',npcId:100,name:'Goblin',aggression:0,"
				+ "combatStyle:'melee',attackSpeed:4,maxHit:1,"
				+ "onTick:function(ctx){},onDeath:function(ctx){}}");
		MobDefinition mob = MobDefinitionRegistry.get(100);
		assertNotNull(mob);
		assertNotNull(mob.onTick());
		assertNotNull(mob.onDeath());
		assertNull(mob.onSpawn());
	}

	private void expectFailure(String mobJs, String messagePart) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			try {
				ScriptFunctions.getInstance().getDefineMob()
						.accept(mob(mobJs));
				fail("expected defineMob rejection for: " + mobJs);
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains(messagePart));
			} catch (Exception expected) {
				assertTrue(expected.getMessage() != null
						&& expected.getMessage().contains(messagePart));
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	private void register(String mobJs) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		ScriptFunctions.getInstance().getDefineMob().accept(mob(mobJs));
		ScriptRuntimeTestFixture.publishCandidate(context, candidate);
	}

	private Value mob(String body) {
		return context.eval("js", "(" + body + ")");
	}

	private static String canonical() {
		return "{id:'goblin',npcId:100,name:'Goblin',aggression:0,"
				+ "combatStyle:'melee',attackSpeed:4,maxHit:1}";
	}
}
