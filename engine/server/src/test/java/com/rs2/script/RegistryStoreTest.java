package com.rs2.script;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Test;

import com.rs2.script.registries.CommandHandlerRegistry;
import com.rs2.script.registries.InteractionHandlerRegistry;
import com.rs2.script.registries.LifecycleRegistry;
import com.rs2.script.registries.RegistryStore;

public class RegistryStoreTest {

	private Context context;

	@After
	public void cleanUp() {
		ScriptRuntimeTestFixture.reset();
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void stagingIsInvisibleUntilOneAtomicCommit() {
		context = Context.create("js");
		Value oldHandler = context.eval("js", "(function () { return 'old'; })");
		Value newHandler = context.eval("js", "(function () { return 'new'; })");

		ScriptRuntimeTestFixture.publish(context,
				() -> CommandHandlerRegistry.put("old", oldHandler));
		RegistryStore.State candidate = RegistryStore.beginStaging();
		CommandHandlerRegistry.put("new", newHandler);

		assertSame(oldHandler, CommandHandlerRegistry.get("old"));
		assertNull(CommandHandlerRegistry.get("new"));

		ScriptHost.getInstance().publishForTesting(context, candidate);
		assertNull(CommandHandlerRegistry.get("old"));
		assertSame(newHandler, CommandHandlerRegistry.get("new"));
	}

	@Test
	public void rollbackRetainsTheCompleteActiveState() {
		context = Context.create("js");
		Value oldHandler = context.eval("js", "(function () {})");
		ScriptRuntimeTestFixture.publish(context,
				() -> CommandHandlerRegistry.put("old", oldHandler));

		RegistryStore.State candidate = RegistryStore.beginStaging();
		CommandHandlerRegistry.put("new", context.eval("js", "(function () {})"));
		RegistryStore.rollback(candidate);

		assertSame(oldHandler, CommandHandlerRegistry.get("old"));
		assertNull(CommandHandlerRegistry.get("new"));
	}

	@Test
	public void phaseFourRegistrationsPublishTogetherAndKeepOrderedKeys() {
		context = Context.create("js");
		Value handler = context.eval("js", "(function () {})");
		RegistryStore.State candidate = RegistryStore.beginStaging();
		InteractionHandlerRegistry.putButton(255255, handler);
		InteractionHandlerRegistry.putItemOnGroundItem(10, 20, handler);
		InteractionHandlerRegistry.putItemOnPlayer(10, handler);
		InteractionHandlerRegistry.putMagicOnItem(30, 10, handler);
		InteractionHandlerRegistry.putMagicOnObject(30, 40, handler);
		LifecycleRegistry.putPlayerDeath(handler);
		ScriptHost.getInstance().publishForTesting(context, candidate);

		assertSame(handler, InteractionHandlerRegistry.getButton(255255));
		assertSame(handler, InteractionHandlerRegistry.getItemOnGroundItem(10, 20));
		assertNull(InteractionHandlerRegistry.getItemOnGroundItem(20, 10));
		assertSame(handler, InteractionHandlerRegistry.getItemOnPlayer(10));
		assertSame(handler, InteractionHandlerRegistry.getMagicOnItem(30, 10));
		assertSame(handler, InteractionHandlerRegistry.getMagicOnObject(30, 40));
		assertSame(handler, LifecycleRegistry.getPlayerDeath());
	}

	@Test
	public void scriptArrayCopiesInputAndRejectsInvalidIndexes() {
		Object[] source = { "first", "second" };
		ScriptArray array = new ScriptArray(source);
		source[0] = "changed";

		assertEquals(2, array.length());
		assertEquals("first", array.get(0));
		assertEquals("second", array.get(1));
		assertNull(array.get(-1));
		assertNull(array.get(2));
		assertNull(array.get(0.5));
		assertNull(array.get(Double.NaN));
		assertNull(array.get(Double.POSITIVE_INFINITY));
		assertNotNull(array.get(0));
	}

	@Test
	public void committedStateRejectsJavaAndAggregateMutation() {
		context = Context.create("js");
		Value handler = context.eval("js", "(function () {})");
		ScriptRuntimeTestFixture.publish(context,
				() -> CommandHandlerRegistry.put("stable", handler));

		try {
			CommandHandlerRegistry.put("late", handler);
			fail("post-commit Java registration should fail");
		} catch (IllegalStateException expected) {
			assertNotNull(expected.getMessage());
		}
		try {
			CommandHandlerRegistry.all().put("late", handler);
			fail("committed command view should be immutable");
		} catch (UnsupportedOperationException expected) {
			assertNotNull(expected);
		}
		assertSame(handler, CommandHandlerRegistry.get("stable"));
		assertNull(CommandHandlerRegistry.get("late"));
	}
}
