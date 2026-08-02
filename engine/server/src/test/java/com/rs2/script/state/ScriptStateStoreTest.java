package com.rs2.script.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ScriptStateStoreTest {

	@Test
	public void typedValuesDefaultsAndWrongTypesAreExplicit() {
		PlayerStateNamespace state = new PlayerStateNamespace(
				new ScriptStateStore(), "feature.demo");
		assertNull(state.getBoolean("enabled"));
		assertFalse(state.getBooleanOr("enabled", false));
		assertTrue(state.setBoolean("enabled", true));
		assertTrue(state.getBoolean("enabled").booleanValue());
		assertFalse(state.setBoolean("enabled", true));
		try {
			state.getString("enabled");
			fail("wrong type should throw");
		} catch (ScriptStateTypeException expected) {
			assertTrue(expected.getMessage().contains("boolean"));
		}
		assertTrue(state.remove("enabled"));
		assertFalse(state.remove("enabled"));
	}

	@Test
	public void rejectedMutationLeavesPreviousSnapshotUntouched() {
		ScriptStateStore store = new ScriptStateStore();
		PlayerStateNamespace state = new PlayerStateNamespace(store, "feature");
		state.setString("stable", "yes");
		ScriptStateSnapshot before = store.snapshot();
		try {
			state.setString("oversized", repeat('x',
					ScriptStateLimits.MAX_STRING_BYTES + 1));
			fail("oversized value should fail");
		} catch (ScriptStateException expected) {
			assertEquals(before.getNamespaces(), store.snapshot().getNamespaces());
		}
	}

	@Test
	public void publicNamesRejectReservedAndNonAsciiForms() {
		String[] invalid = {"__quest", "_", "$", "sys.admin", "Upper", "café"};
		for (String namespace : invalid) {
			try {
				new PlayerStateNamespace(new ScriptStateStore(), namespace);
				fail("accepted " + namespace);
			} catch (ScriptStateException expected) {
				// expected
			}
		}
	}

	@Test
	public void exactEntryLimitIsEnforcedWithoutPartialWrite() {
		ScriptStateStore store = new ScriptStateStore();
		PlayerStateNamespace state = new PlayerStateNamespace(store, "limit");
		for (int i = 0; i < ScriptStateLimits.MAX_ENTRIES_PER_NAMESPACE; i++) {
			state.setNumber("k" + i, i);
		}
		try {
			state.setNumber("overflow", 1);
			fail("entry overflow should fail");
		} catch (ScriptStateException expected) {
			assertFalse(state.has("overflow"));
			assertEquals(ScriptStateLimits.MAX_ENTRIES_PER_NAMESPACE,
					store.snapshot().entryCount());
		}
	}

	@Test
	public void namespaceAndTotalEntryLimitsAreExact() {
		ScriptStateStore namespaces = new ScriptStateStore();
		for (int i = 0; i < ScriptStateLimits.MAX_NAMESPACES; i++) {
			namespaces.set("n" + i, "value", ScriptStateValue.of(true));
		}
		try {
			namespaces.set("overflow", "value", ScriptStateValue.of(true));
			fail("namespace overflow should fail");
		} catch (ScriptStateException expected) {
			assertFalse(namespaces.has("overflow", "value"));
		}

		ScriptStateStore entries = new ScriptStateStore();
		for (int i = 0; i < ScriptStateLimits.MAX_TOTAL_ENTRIES; i++) {
			entries.set("n" + (i / ScriptStateLimits.MAX_ENTRIES_PER_NAMESPACE),
					"k" + i, ScriptStateValue.of(true));
		}
		try {
			entries.set("extra", "value", ScriptStateValue.of(true));
			fail("total entry overflow should fail");
		} catch (ScriptStateException expected) {
			assertEquals(ScriptStateLimits.MAX_TOTAL_ENTRIES,
					entries.snapshot().entryCount());
		}
	}

	private static String repeat(char value, int count) {
		StringBuilder builder = new StringBuilder(count);
		for (int i = 0; i < count; i++) {
			builder.append(value);
		}
		return builder.toString();
	}
}
