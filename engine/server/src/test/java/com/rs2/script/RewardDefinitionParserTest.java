package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.apollo.cache.def.ItemDefinition;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.script.registries.RegistryStore;
import com.rs2.script.reward.RewardDefinition;
import com.rs2.script.reward.RewardRegistry;

/**
 * Proves the canonical {@code defineReward} schema-v1 contract: strict
 * members and bounds, item-name resolution, duplicate id/skill rejection,
 * and copied typed entries.
 */
public class RewardDefinitionParserTest {

	private Context context;
	private ItemDefinition[] previousDefinitions;

	@Before
	public void installDefinitions() throws Exception {
		context = Context.create("js");
		previousDefinitions = ItemDefinition.getDefinitions();
		ItemDefinition[] definitions = new ItemDefinition[1500];
		definitions[995] = new ItemDefinition(995);
		definitions[995].setName("Coins");
		definitions[536] = new ItemDefinition(536);
		definitions[536].setName("Dragon bones");
		Field field = ItemDefinition.class.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, definitions);
	}

	@After
	public void restore() throws Exception {
		ScriptRuntimeTestFixture.reset();
		if (context != null) {
			context.close();
		}
		Field field = ItemDefinition.class.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, previousDefinitions);
	}

	@Test
	public void canonicalRewardParsesWithCopiedTypedMembers() {
		register("{id:'full-reward',items:[{id:995,amount:100},"
				+ "{id:'dragon bones',amount:1}],"
				+ "experience:[{skill:'magic',amount:100}],"
				+ "questPoints:3,"
				+ "state:[{namespace:'demo',key:'started',value:true},"
				+ "{namespace:'demo',key:'score',value:7.5},"
				+ "{namespace:'demo',key:'note',value:'hi'}]}");

		RewardDefinition parsed = RewardRegistry.get("full-reward");
		assertNotNull(parsed);
		assertEquals(2, parsed.items().size());
		assertEquals(995, parsed.items().get(0).itemId());
		assertEquals(536, parsed.items().get(1).itemId());
		assertEquals(1, parsed.experience().size());
		assertEquals(6, parsed.experience().get(0).skillIndex());
		assertEquals(3, parsed.questPoints());
		assertEquals(3, parsed.stateMutations().size());
		assertTrue(parsed.stateMutations().get(0).isBoolean());
		assertTrue(parsed.stateMutations().get(1).isNumber());
		assertTrue(parsed.stateMutations().get(2).isString());
		assertEquals("demo", parsed.stateMutations().get(0).namespace());
		assertEquals(0, parsed.schemaVersion());
	}

	@Test
	public void invalidRewardsRejectWithMemberAndBoundDiagnostics() {
		String[] invalid = {
				"{id:'no-id',items:[{id:995,amount:1}]}",
				"{id:'unknown',bogus:1}",
				"{id:'dup-items',items:[{id:995,amount:1},{id:995,amount:2}]}",
				"{id:'dup-skills',experience:[{skill:'magic',amount:1},"
						+ "{skill:'magic',amount:2}]}",
				"{id:'unknown-skill',experience:[{skill:'dancing',amount:1}]}",
				"{id:'zero-amount',items:[{id:995,amount:0}]}",
				"{id:'xp-over',experience:[{skill:'magic',amount:200000001}]}",
				"{id:'fractional-xp',experience:[{skill:'magic',amount:1.5}]}",
				"{id:'unloaded-item',items:[{id:14999,amount:1}]}",
				"{id:'bad-namespace',state:[{namespace:'UPPER',key:'k',value:1}]}",
				"{id:'bad-state-member',state:[{namespace:'a',key:'k',value:{}}]}"
		};
		for (String source : invalid) {
			expectFailure(source);
		}
		assertNull(RewardRegistry.get("dup-items"));
		assertNull(RewardRegistry.get("unknown-skill"));
		assertNull(RewardRegistry.get("bad-namespace"));
	}

	private void register(String source) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineReward().accept(
					context.eval("js", "(" + source + ")"));
			ScriptHost.getInstance().publishForTesting(context, candidate);
		} catch (RuntimeException error) {
			RegistryStore.rollback(candidate);
			throw error;
		}
	}

	private void expectFailure(String source) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineReward().accept(
					context.eval("js", "(" + source + ")"));
			fail("Expected registration to fail: " + source);
		} catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("defineReward"));
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

}
