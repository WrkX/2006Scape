package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Test;

import com.rs2.script.registries.CommandHandlerRegistry;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.ExecutableRouteRecord;
import com.rs2.script.route.RouteRegistry;

/**
 * Proves the unified guest/host executable route contract: candidate-wide
 * uniqueness with both records identified, reserved admin aliases, identical
 * guest/host authority through the production command dispatch, and the
 * exact consumed/unmatched behavior.
 */
public class RouteRegistryTest {

	private String previousContentDir;
	private Context context;

	@After
	public void cleanUp() {
		ScriptRuntimeTestFixture.reset();
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void reservedAdminAliasesRejectGuestRegistrationInEitherSource()
			throws Exception {
		Path root = Files.createTempDirectory("route-reserved-guest");
		setContentDir(root);
		Files.write(root.resolve("loader.js"),
				("onCommand('stable', function () {});")
						.getBytes(StandardCharsets.UTF_8));
		ScriptHost host = ScriptHost.getInstance();
		host.reload();
		Context stable = host.getContext();
		assertNotNull(stable);

		String[] reserved = { "scripts", "reload", "scriptdir" };
		for (String alias : reserved) {
			Files.write(root.resolve("loader.js"),
					("onCommand('stable', function () {});"
							+ "onCommand('" + alias + "', function () {});")
							.getBytes(StandardCharsets.UTF_8));
			host.reload();
			assertSame(stable, host.getContext());
			assertNotNull(CommandHandlerRegistry.get("stable"));
			assertNull(CommandHandlerRegistry.get(alias));
		}

		Files.write(root.resolve("loader.js"),
				("registerContentModule({id:'module-a',schemaVersion:1},"
						+ "function () { onCommand('reload', function () {}); });")
						.getBytes(StandardCharsets.UTF_8));
		host.reload();
		assertSame(stable, host.getContext());
	}

	@Test
	public void reservedAdminAliasesRejectHostRegistration() {
		context = Context.create("js");
		for (String alias : RouteRegistry.RESERVED_COMMANDS) {
			RegistryStore.State candidate = RegistryStore.beginStaging();
			try {
				RouteRegistry.putHost(ExecutableRouteKey.command(alias),
						() -> { });
				fail("reserved alias " + alias + " should reject a host route");
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains("reserved"));
				assertTrue(expected.getMessage().contains(alias));
			} finally {
				RegistryStore.rollback(candidate);
			}
		}
	}

	@Test
	public void duplicateGuestRouteInOneSourceRejectsWithBothRecords() {
		context = Context.create("js");
		Value handler = context.eval("js", "(function () {})");
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			CommandHandlerRegistry.put("dup", handler);
			try {
				CommandHandlerRegistry.put("dup", handler);
				fail("duplicate guest route should reject");
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains("duplicate"));
				assertTrue(expected.getMessage().contains("existing record"));
				assertTrue(expected.getMessage().contains("dup"));
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	@Test
	public void duplicateGuestRouteFromTwoModulesRejectsTheCandidate()
			throws Exception {
		Path root = Files.createTempDirectory("route-two-modules");
		setContentDir(root);
		Files.write(root.resolve("loader.js"),
				("registerContentModule({id:'alpha',schemaVersion:1},"
						+ "function () { onCommand('dup', function () {}); });"
						+ "registerContentModule({id:'beta',schemaVersion:1},"
						+ "function () { onCommand('dup', function () {}); });")
						.getBytes(StandardCharsets.UTF_8));
		ScriptHost host = ScriptHost.getInstance();
		host.reload();
		Context stable = host.getContext();
		assertNull(stable);

		Files.write(root.resolve("loader.js"),
				"onCommand('ok', function () {});".getBytes(
						StandardCharsets.UTF_8));
		host.reload();
		assertNotNull(host.getContext());
		assertNotNull(CommandHandlerRegistry.get("ok"));
		assertNull(CommandHandlerRegistry.get("dup"));
	}

	@Test
	public void guestVsHostConflictRejectsInBothRegistrationOrders() {
		context = Context.create("js");
		Value guest = context.eval("js", "(function () {})");
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			RouteRegistry.put(ExecutableRouteKey.command("shared"), guest);
			try {
				RouteRegistry.putHost(ExecutableRouteKey.command("shared"),
						() -> { });
				fail("host route over guest route should reject");
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains("duplicate"));
				assertTrue(expected.getMessage().contains("guest"));
			}
		} finally {
			RegistryStore.rollback(candidate);
		}

		candidate = RegistryStore.beginStaging();
		try {
			RouteRegistry.putHost(ExecutableRouteKey.command("shared"),
					() -> { });
			try {
				RouteRegistry.put(ExecutableRouteKey.command("shared"), guest);
				fail("guest route over host route should reject");
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains("duplicate"));
				assertTrue(expected.getMessage().contains("host"));
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	@Test
	public void twoHostConsumersClaimingOneKeyRejectTheCandidate() {
		context = Context.create("js");
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			RouteRegistry.putHost(ExecutableRouteKey.object(409, "first"),
					() -> { });
			try {
				RouteRegistry.putHost(ExecutableRouteKey.object(409, "first"),
						() -> { });
				fail("two host consumers for one key should reject");
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains("duplicate"));
				assertTrue(expected.getMessage().contains("409/first"));
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	@Test
	public void exactRouteRecordIsReadableFromTheActiveSnapshot() {
		context = Context.create("js");
		Value guest = context.eval("js", "(function () {})");
		ScriptRuntimeTestFixture.publish(context, () ->
				CommandHandlerRegistry.put("visible", guest));

		ExecutableRouteRecord record = RouteRegistry.get(
				ExecutableRouteKey.command("visible"));
		assertNotNull(record);
		assertTrue(record.isGuest());
		assertSame(guest, record.guestInvoker());
		assertEquals("legacy-unscoped", record.source());
		assertNull(RouteRegistry.get(ExecutableRouteKey.command("absent")));
	}

	private void setContentDir(Path root) {
		previousContentDir = System.getProperty("singlescape.contentDir");
		System.setProperty("singlescape.contentDir",
				root.toAbsolutePath().toString());
	}

}
