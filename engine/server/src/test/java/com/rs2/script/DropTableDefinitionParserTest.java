package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;

import org.apollo.cache.def.ItemDefinition;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.script.drop.DropTableDefinition;
import com.rs2.script.drop.DropTableRegistry;
import com.rs2.script.registries.RegistryStore;

/**
 * Proves the canonical {@code defineDropTable} schema-v1 contract: strict
 * members and bounds, exact deterministic item-name resolution (missing and
 * ambiguous names fail with source and field path), duplicate registration
 * rejection, and copied numeric entries.
 */
public class DropTableDefinitionParserTest {

	private Context context;
	private ItemDefinition[] previousDefinitions;

	@Before
	public void installDefinitions() throws Exception {
		context = Context.create("js");
		previousDefinitions = ItemDefinition.getDefinitions();
		ItemDefinition[] definitions = new ItemDefinition[1500];
		definitions[536] = named(536, "Dragon bones");
		definitions[995] = named(995, "Coins");
		definitions[526] = named(526, "Bones");
		definitions[1147] = named(1147, "Rune med helm");
		definitions[1148] = named(1148, "Ambiguous item");
		definitions[1149] = named(1149, "AMBIGUOUS ITEM");
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
	public void canonicalNumericTableParsesWithCopiedEntries() {
		register(table(
				"{id:'bones_and_coins',entries:["
						+ "{itemId:536,minAmount:1,maxAmount:1,weight:0,always:true},"
						+ "{itemId:995,minAmount:500,maxAmount:500,weight:100,"
						+ "always:false}]}"));

		DropTableDefinition parsed = DropTableRegistry.get("bones_and_coins");
		assertNotNull(parsed);
		assertEquals(2, parsed.entries().size());
		assertEquals(536, parsed.entries().get(0).itemId());
		assertEquals(1, parsed.entries().get(0).minAmount());
		assertEquals(1, parsed.entries().get(0).maxAmount());
		assertEquals(0, parsed.entries().get(0).weight());
		assertTrue(parsed.entries().get(0).always());
		assertEquals(995, parsed.entries().get(1).itemId());
		assertEquals(100, parsed.entries().get(1).weight());
		assertEquals(0, parsed.schemaVersion());
		assertEquals(com.rs2.script.definition.ModuleScope.LEGACY_SOURCE,
				parsed.source());
	}

	@Test
	public void stringItemNamesResolveExactlyAndCaseInsensitively() {
		register(table(
				"{id:'named_table',entries:["
						+ "{itemId:'dragon bones',minAmount:1,maxAmount:1,"
						+ "weight:0,always:true},"
						+ "{itemId:'COINS',minAmount:1,maxAmount:1,"
						+ "weight:1,always:false}]}"));

		DropTableDefinition parsed = DropTableRegistry.get("named_table");
		assertNotNull(parsed);
		assertEquals(536, parsed.entries().get(0).itemId());
		assertEquals(995, parsed.entries().get(1).itemId());
	}

	@Test
	public void missingItemNameFailsWithSourceAndFieldPath() {
		expectFailure(table(
				"{id:'missing_name',entries:["
						+ "{itemId:'Dragon scales',minAmount:1,maxAmount:1,"
						+ "weight:0,always:true}]}"),
				"no loaded item matches name 'Dragon scales'",
				"entries[0].itemId");
		assertNull(DropTableRegistry.get("missing_name"));
	}

	@Test
	public void ambiguousItemNameFailsWithMatchCount() {
		expectFailure(table(
				"{id:'ambiguous_name',entries:["
						+ "{itemId:'ambiguous item',minAmount:1,maxAmount:1,"
						+ "weight:0,always:true}]}"),
				"is ambiguous (2 matches)", "entries[0].itemId");
		assertNull(DropTableRegistry.get("ambiguous_name"));
	}

	@Test
	public void duplicateRegistrationRejectsWithBothRecords() {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineDropTable().accept(table(
					"{id:'dup_table',entries:["
							+ "{itemId:536,minAmount:1,maxAmount:1,weight:0,"
							+ "always:true}]}"));
			try {
				ScriptFunctions.getInstance().getDefineDropTable().accept(table(
						"{id:'dup_table',entries:["
								+ "{itemId:995,minAmount:1,maxAmount:1,weight:1,"
								+ "always:false}]}"));
				fail("duplicate table id should reject");
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains("duplicate registration"));
				assertTrue(expected.getMessage().contains(
						"existing record: drop_table:dup_table"));
			}
			ScriptHost.getInstance().publishForTesting(context, candidate);
		} finally {
			RegistryStore.rollback(candidate);
		}
		assertNotNull(DropTableRegistry.get("dup_table"));
		assertEquals(536, DropTableRegistry.get("dup_table")
				.entries().get(0).itemId());
	}

	@Test
	public void invalidTablesRejectWithEntryAndMemberDiagnostics() {
		String[] invalid = {
				"{id:'empty',entries:[]}",
				"{id:'too_many',entries:Array.from({length:65},"
						+ "()=>({itemId:536,minAmount:1,maxAmount:1,weight:0,"
						+ "always:true}))}",
				"{id:'fractional_weight',entries:["
						+ "{itemId:536,minAmount:1,maxAmount:1,weight:0.25,always:false}]}",
				"{id:'infinity_weight',entries:["
						+ "{itemId:536,minAmount:1,maxAmount:1,weight:Infinity,always:false}]}",
				"{id:'always_with_weight',entries:["
						+ "{itemId:536,minAmount:1,maxAmount:1,weight:1,always:true}]}",
				"{id:'weighted_zero',entries:["
						+ "{itemId:536,minAmount:1,maxAmount:1,weight:0,always:false}]}",
				"{id:'inverted_amounts',entries:["
						+ "{itemId:536,minAmount:5,maxAmount:1,weight:0,always:true}]}",
				"{id:'unknown_member',entries:["
						+ "{itemId:536,minAmount:1,maxAmount:1,weight:0,always:true,"
						+ "rare:true}]}",
				"{id:'unknown_entry_member',entries:["
						+ "{itemId:536,minAmount:1,maxAmount:1,weight:0,always:true,"
						+ "extra:1}]}",
				"{id:'unloaded_numeric',entries:["
						+ "{itemId:14999,minAmount:1,maxAmount:1,weight:0,always:true}]}",
				"{entries:[{itemId:536,minAmount:1,maxAmount:1,weight:0,"
						+ "always:true}]}"
		};
		for (String source : invalid) {
			expectFailure(table(source), null, null);
		}
		assertNull(DropTableRegistry.get("empty"));
		assertNull(DropTableRegistry.get("fractional_weight"));
		assertNull(DropTableRegistry.get("always_with_weight"));
		assertNull(DropTableRegistry.get("unknown_member"));
		assertNull(DropTableRegistry.get("unloaded_numeric"));
	}

	private void register(Value table) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineDropTable().accept(table);
			ScriptHost.getInstance().publishForTesting(context, candidate);
		} catch (RuntimeException error) {
			RegistryStore.rollback(candidate);
			throw error;
		}
	}

	private void expectFailure(Value table, String expectedMessage,
			String expectedDetail) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineDropTable().accept(table);
			fail("Expected registration to fail");
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

	private static ItemDefinition named(int id, String name) {
		ItemDefinition definition = new ItemDefinition(id);
		definition.setName(name);
		return definition;
	}

	private Value table(String source) {
		return context.eval("js", "(" + source + ")");
	}

}
