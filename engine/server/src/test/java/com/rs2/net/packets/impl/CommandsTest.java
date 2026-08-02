package com.rs2.net.packets.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.junit.After;
import org.junit.Test;

import com.rs2.game.players.Player;
import com.rs2.script.registries.CommandHandlerRegistry;
import com.rs2.script.ScriptRuntimeTestFixture;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.RouteRegistry;

public class CommandsTest {

	private Context context;

	@After
	public void cleanUp() {
		ScriptRuntimeTestFixture.reset();
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void registeredCommandIsAuthoritativeAtOuterDispatchBoundary() {
		context = Context.create("js");
		Player player = new Player(-1) { };
		ScriptRuntimeTestFixture.publish(context, () ->
				CommandHandlerRegistry.put("scripted", context.eval("js",
						"(function () { globalThis.commandCalls = "
								+ "(globalThis.commandCalls || 0) + 1; })")));

		assertTrue(Commands.executeScriptCommand(player, "ScRiPtEd"));
		assertEquals(1, context.getBindings("js").getMember("commandCalls").asInt());
		assertFalse(Commands.executeScriptCommand(player, "missing"));
	}

	@Test
	public void throwingCommandStillConsumesDispatch() {
		context = Context.create("js");
		Player player = new Player(-1) { };
		ScriptRuntimeTestFixture.publish(context, () ->
				CommandHandlerRegistry.put("boom", context.eval("js",
						"(function () { throw new Error('boom'); })")));

		assertTrue(Commands.executeScriptCommand(player, "boom"));
	}

	@Test
	public void commandReceivesCanonicalAndLosslessInvocationMetadata() {
		context = Context.newBuilder("js")
				.allowHostAccess(HostAccess.newBuilder(HostAccess.EXPLICIT)
						.allowArrayAccess(true).build())
				.build();
		Player player = new Player(-1) { };
		player.playerRights = 2;
		ScriptRuntimeTestFixture.publish(context, () ->
				CommandHandlerRegistry.put("echo", context.eval("js",
						"(function (ctx) { globalThis.commandMetadata = "
								+ "ctx.getName() + '|' + ctx.getRawInput() + '|' + "
								+ "ctx.getArguments().join(',') + '|' + ctx.getRights(); })")));

		assertTrue(Commands.executeScriptCommand(player, "EcHo",
				"EcHo one  two", new String[] {"one", "two"}));
		assertEquals("echo|EcHo one  two|one,two|2",
				context.getBindings("js").getMember("commandMetadata").asString());
	}

	@Test
	public void hostCommandRouteIsInvokedAndConsumedThroughProductionDispatch() {
		context = Context.create("js");
		Player player = new Player(-1) { };
		java.util.concurrent.atomic.AtomicInteger calls =
				new java.util.concurrent.atomic.AtomicInteger();
		ScriptRuntimeTestFixture.publish(context, () ->
				RouteRegistry.putHost(ExecutableRouteKey.command("host-cmd"),
						arguments -> calls.incrementAndGet()));

		assertTrue(Commands.executeScriptCommand(player, "host-cmd"));
		assertEquals(1, calls.get());
		assertFalse(Commands.executeScriptCommand(player, "missing"));
		assertEquals(1, calls.get());
	}

	@Test
	public void throwingHostRouteIsContainedAndStillConsumed() {
		context = Context.create("js");
		Player player = new Player(-1) { };
		ScriptRuntimeTestFixture.publish(context, () ->
				RouteRegistry.putHost(ExecutableRouteKey.command("boom-host"),
						arguments -> { throw new IllegalStateException("host boom"); }));

		assertTrue(Commands.executeScriptCommand(player, "boom-host"));
	}

	@Test
	public void reservedAdminAliasCannotBeRegisteredAsHostRoute() {
		context = Context.create("js");
		try {
			ScriptRuntimeTestFixture.publish(context, () ->
					RouteRegistry.putHost(ExecutableRouteKey.command("reload"),
							arguments -> { }));
			fail("reserved alias should reject a host route");
		} catch (RuntimeException expected) {
			assertTrue(expected.getMessage().contains("reserved"));
		}
	}
}
