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

import com.rs2.script.interfacehook.InterfaceHookDefinition;
import com.rs2.script.interfacehook.InterfaceHookDefinitionRegistry;
import com.rs2.script.registries.RegistryStore;

/**
 * Proves the canonical {@code defineInterfaceHook} schema-v1 contract.
 */
public class InterfaceHookDefinitionParserTest {

	private Context context;

	@Before
	public void setUp() {
		ScriptRuntimeTestFixture.reset();
		context = Context.create("js");
	}

	@After
	public void restore() {
		ScriptRuntimeTestFixture.reset();
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void canonicalHookParsesIntoJavaOwnedDescriptor() {
		register(canonical());

		InterfaceHookDefinition hook = InterfaceHookDefinitionRegistry
				.get("cooking-guide");
		assertNotNull(hook);
		assertEquals("cooking-guide", hook.id());
		assertEquals(8134, hook.interfaceId());
		assertEquals(1, hook.buttons().size());
		assertNotNull(hook.buttons().get(Integer.valueOf(55096)));
		assertNotNull(hook.onOpen());
		assertNull(hook.onClose());
	}

	@Test
	public void duplicateInterfaceIdRejectsCandidate() {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineInterfaceHook()
					.accept(hook(canonical()));
			ScriptFunctions.getInstance().getDefineInterfaceHook()
					.accept(hook("{id:'other-hook',interfaceId:8134,"
							+ "buttons:{55097:function(){}}}"));
			fail("expected duplicate interface id rejection");
		} catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("interface id 8134"));
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	@Test
	public void malformedButtonIdRejectsCandidate() {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineInterfaceHook()
					.accept(hook("{id:'bad-button',interfaceId:100,"
							+ "buttons:{foo:function(){}}}"));
			fail("expected malformed button key rejection");
		} catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("numeric id"));
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	@Test
	public void emptyHookRejectsCandidate() {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineInterfaceHook()
					.accept(hook("{id:'empty',interfaceId:100}"));
			fail("expected empty hook rejection");
		} catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("at least one"));
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	private void register(String hookJs) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		ScriptFunctions.getInstance().getDefineInterfaceHook()
				.accept(hook(hookJs));
		ScriptRuntimeTestFixture.publishCandidate(context, candidate);
	}

	private Value hook(String body) {
		return context.eval("js", "(" + body + ")");
	}

	private static String canonical() {
		return "{id:'cooking-guide',interfaceId:8134,"
				+ "onOpen:function(ctx){},"
				+ "buttons:{55096:function(ctx){}}}";
	}
}
