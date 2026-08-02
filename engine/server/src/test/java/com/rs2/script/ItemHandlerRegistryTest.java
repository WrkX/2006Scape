package com.rs2.script;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Test;

import com.rs2.script.registries.ItemHandlerRegistry;
import com.rs2.script.registries.RegistryStore;

public class ItemHandlerRegistryTest {

	private Context context;

	@After
	public void cleanUp() {
		ScriptRuntimeTestFixture.reset();
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void itemOnItemLookupIsSymmetric() {
		context = Context.create("js");
		Value handler = context.eval("js", "(function () {})");
		ScriptRuntimeTestFixture.publish(context,
				() -> ItemHandlerRegistry.putItemOnItem(100, 200, handler));

		assertSame(handler, ItemHandlerRegistry.getItemOnItem(100, 200));
		assertSame(handler, ItemHandlerRegistry.getItemOnItem(200, 100));
	}

	@Test
	public void candidateItemRegistrationsAreInvisibleUntilCommit() {
		context = Context.create("js");
		Value oldHandler = context.eval("js", "(function () {})");
		Value newHandler = context.eval("js", "(function () {})");
		ScriptRuntimeTestFixture.publish(context,
				() -> ItemHandlerRegistry.putItem(100, "first", oldHandler));

		RegistryStore.State candidate = RegistryStore.beginStaging();
		ItemHandlerRegistry.putItem(200, "second", newHandler);
		assertSame(oldHandler, ItemHandlerRegistry.getItem(100, "first"));
		assertNull(ItemHandlerRegistry.getItem(200, "second"));

		ScriptHost.getInstance().publishForTesting(context, candidate);
		assertNull(ItemHandlerRegistry.getItem(100, "first"));
		assertSame(newHandler, ItemHandlerRegistry.getItem(200, "second"));
	}

	@Test
	public void registrationFacadeRejectsInvalidIdsAndActions() {
		context = Context.create("js");
		Value handler = context.eval("js", "(function () {})");
		ScriptFunctions functions = ScriptFunctions.getInstance();
		RegistryStore.State candidate = RegistryStore.beginStaging();

		expectRegistrationFailure(() -> functions.getOnItem().apply(-1, "first", handler),
				"item id must be non-negative");
		expectRegistrationFailure(() -> functions.getOnItem().apply(100, "open", handler),
				"unsupported action");
		expectRegistrationFailure(() -> functions.getOnItemOnObject().apply(-1, 10, handler),
				"ids must be non-negative");
		expectRegistrationFailure(() -> functions.getOnItemOnNpc().apply(10, 20, null),
				"handler is not executable");

		assertNull(ItemHandlerRegistry.getItem(-1, "first"));
		assertNull(ItemHandlerRegistry.getItem(100, "open"));
		assertNull(ItemHandlerRegistry.getItemOnObject(-1, 10));
		assertNull(ItemHandlerRegistry.getItemOnNpc(10, 20));

		functions.getOnItem().apply(100, "third", handler);
		ScriptHost.getInstance().publishForTesting(context, candidate);
		assertNotNull(ItemHandlerRegistry.getItem(100, "third"));
	}

	@Test
	public void facadeRejectsDuplicatesWithoutReplacingFirstHandler() {
		context = Context.create("js");
		Value first = context.eval("js", "(function () { return 'first'; })");
		Value second = context.eval("js", "(function () { return 'second'; })");
		ScriptFunctions functions = ScriptFunctions.getInstance();
		RegistryStore.State candidate = RegistryStore.beginStaging();

		functions.getOnItem().apply(100, "first", first);
		expectRegistrationFailure(
				() -> functions.getOnItem().apply(100, "first", second),
				"duplicate registration");

		functions.getOnItemOnItem().apply(100, 200, first);
		expectRegistrationFailure(
				() -> functions.getOnItemOnItem().apply(200, 100, second),
				"duplicate registration");

		functions.getOnItemOnObject().apply(100, 300, first);
		expectRegistrationFailure(
				() -> functions.getOnItemOnObject().apply(100, 300, second),
				"duplicate registration");

		functions.getOnItemOnNpc().apply(100, 400, first);
		expectRegistrationFailure(
				() -> functions.getOnItemOnNpc().apply(100, 400, second),
				"duplicate registration");
		ScriptHost.getInstance().publishForTesting(context, candidate);
		assertSame(first, ItemHandlerRegistry.getItem(100, "first"));
		assertSame(first, ItemHandlerRegistry.getItemOnItem(100, 200));
		assertSame(first, ItemHandlerRegistry.getItemOnObject(100, 300));
		assertSame(first, ItemHandlerRegistry.getItemOnNpc(100, 400));
	}

	@Test
	public void directRegistryPutReportsDuplicateWithoutThrowingOrReplacing() {
		context = Context.create("js");
		Value first = context.eval("js", "(function () {})");
		Value second = context.eval("js", "(function () {})");
		RegistryStore.State candidate = RegistryStore.beginStaging();

		assertNull(ItemHandlerRegistry.putItemOnItem(100, 200, first));
		assertSame(first, ItemHandlerRegistry.putItemOnItem(200, 100, second));
		ScriptHost.getInstance().publishForTesting(context, candidate);
		assertSame(first, ItemHandlerRegistry.getItemOnItem(100, 200));
	}

	private static void expectRegistrationFailure(Runnable registration,
			String expectedMessage) {
		try {
			registration.run();
			fail("Expected registration to fail");
		} catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains(expectedMessage));
		}
	}
}
