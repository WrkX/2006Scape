package com.rs2.script;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.junit.Test;

public class ScriptExecutorTest {

	@Test
	public void missingHandlerFallsThroughButThrowingHandlerRemainsAuthoritative() {
		assertFalse(ScriptExecutor.execute(null, "command", "missing", "missing"));
		try (Context context = Context.create("js")) {
			assertTrue(ScriptExecutor.execute(
					context.eval("js", "(function () { throw new Error('boom'); })"),
					"command", "boom", "boom"));
		}
	}

	@Test
	public void handlerFromClosedContextIsContained() {
		Context context = Context.create("js");
		org.graalvm.polyglot.Value handler =
				context.eval("js", "(function () { return 'stale'; })");
		context.close();

		assertTrue(ScriptExecutor.execute(handler, "command", "stale", "stale"));
	}
}
