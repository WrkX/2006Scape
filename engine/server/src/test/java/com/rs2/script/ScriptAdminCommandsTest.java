package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Test;

import com.rs2.game.players.Client;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.net.Packet;
import com.rs2.net.packets.impl.Commands;
import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRegistry;
import com.rs2.script.definition.ModuleRecord;
import com.rs2.script.registries.CommandHandlerRegistry;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.RouteRegistry;
import com.rs2.util.Stream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Phase 5 WP9 operator-diagnostics command tests.
 *
 * <p>Drives the production {@code Commands.processPacket} path so the
 * permission, parsing, status, list, reload, and reserved {@code scriptdir}
 * behavior is proven exactly as a player sees it. The recording player's out
 * stream captures every {@code sendMessage} string without decoding packet
 * frames.
 */
public class ScriptAdminCommandsTest {

	private static final int PACKET_ID = 103;

	private Context context;

	@After
	public void cleanUp() {
		ScriptRuntimeTestFixture.reset();
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void statusRequiresAdminRightsAndReportsActiveGeneration() {
		context = Context.create("js");
		publishWithModule();
		Player player = player(90, 0);
		command(player, "scripts status");
		assertTrue(messages(player).isEmpty());
		command(player, "scripts");
		assertTrue(messages(player).isEmpty());

		Player admin = player(91, 2);
		command(admin, "scripts status");
		String joined = String.join("\n", messages(admin));
		assertTrue(joined.contains("active generation"));
		assertTrue(joined.contains("modules"));
		assertTrue(joined.contains("definitions"));
		assertTrue(joined.contains("routes"));
		assertTrue(joined.contains("Scripts:"));
	}

	@Test
	public void listShowsModuleAndDefinitionSourcesInStableOrder() {
		context = Context.create("js");
		publishWithModule();
		Player admin = player(92, 2);
		command(admin, "scripts list");
		String modules = String.join("\n", messages(admin));
		assertTrue(modules.contains("demo-module"));
		assertTrue(modules.contains("Content modules"));

		clear(admin);
		command(admin, "scripts list boss");
		String boss = String.join("\n", messages(admin));
		assertTrue(boss.contains("Definitions: boss"));
		assertTrue(boss.contains("demo-boss"));

		// The documented "drop" alias resolves to the DROP_TABLE kind even
		// though the enum name is DROP_TABLE (not "drop").
		clear(admin);
		command(admin, "scripts list drop");
		String drop = String.join("\n", messages(admin));
		assertTrue(drop.contains("Definitions: drop_table"));
		assertFalse(drop.contains("Unknown definition kind"));
	}

	@Test
	public void unknownKindAndOutOfRangePageAreHandledBounded() {
		context = Context.create("js");
		publishWithModule();
		Player admin = player(93, 2);
		command(admin, "scripts list nope");
		String unknown = String.join("\n", messages(admin));
		assertTrue(unknown.contains("Unknown definition kind"));
		clear(admin);
		command(admin, "scripts list modules 999");
		String paged = String.join("\n", messages(admin));
		assertTrue(paged.contains("page 999"));
	}

	@Test
	public void listRequiresAdminRights() {
		context = Context.create("js");
		publishWithModule();
		Player player = player(94, 0);
		command(player, "scripts list");
		assertTrue(messages(player).isEmpty());
		Player moderator = player(95, 1);
		command(moderator, "scripts list");
		assertTrue(messages(moderator).isEmpty());
	}

	@Test
	public void reloadIsTruthfulOnSuccessAndFailure() throws Exception {
		context = Context.create("js");
		ensureCompiledFixtureSupport();
		ScriptRuntimeTestFixture.reset();
		// Load the compiled loader once so a reload has a real predecessor.
		com.rs2.script.ScriptHost.getInstance().reload();
		Player admin = player(96, 2);
		long before = com.rs2.script.ScriptHost.getInstance()
				.getActiveGeneration();
		command(admin, "scripts reload");
		String success = String.join("\n", messages(admin));
		assertTrue(success.contains("Scripts reloaded"));
		long after = com.rs2.script.ScriptHost.getInstance()
				.getActiveGeneration();
		assertTrue(after > before);

		Player admin2 = player(97, 2);
		java.nio.file.Path root = java.nio.file.Files.createTempDirectory(
				"wp9-reload-fail");
		java.nio.file.Files.write(root.resolve("loader.js"),
				"this is not valid javascript;".getBytes(StandardCharsets.UTF_8));
		String previousDir = System.getProperty("singlescape.contentDir");
		System.setProperty("singlescape.contentDir", root.toAbsolutePath().toString());
		try {
			long stable = com.rs2.script.ScriptHost.getInstance()
					.getActiveGeneration();
			command(admin2, "scripts reload");
			String failed = String.join("\n", messages(admin2));
			assertTrue(failed.contains("Script reload failed"));
			assertEquals(stable, com.rs2.script.ScriptHost.getInstance()
					.getActiveGeneration());
		} finally {
			if (previousDir == null) {
				System.clearProperty("singlescape.contentDir");
			} else {
				System.setProperty("singlescape.contentDir", previousDir);
			}
		}
	}

	@Test
	public void scriptdirIsDeprecatedSanitizedAndReserved() throws Exception {
		context = Context.create("js");
		publishWithModule();
		Player admin = player(98, 2);
		command(admin, "scriptdir");
		String joined = String.join("\n", messages(admin));
		assertTrue(joined.contains("deprecated"));
		assertTrue(joined.contains("active generation"));
		assertFalse("no absolute host path may leak",
				joined.contains("/Users/")
						|| joined.contains("content/dist")
						|| joined.contains("Developer"));

		clear(admin);
		command(admin, "scriptdir /tmp/somewhere");
		String rejected = String.join("\n", messages(admin));
		assertTrue(rejected.contains("no arguments"));

		// After a failed reload, scriptdir still emits only bounded logical
		// status and the bounded failure reason — never a host path.
		clear(admin);
		java.nio.file.Path root = java.nio.file.Files.createTempDirectory(
				"wp9-scriptdir-fail");
		java.nio.file.Files.write(root.resolve("loader.js"),
				"this is not valid javascript;".getBytes(StandardCharsets.UTF_8));
		String previousDir = System.getProperty("singlescape.contentDir");
		System.setProperty("singlescape.contentDir", root.toAbsolutePath().toString());
		try {
			command(admin, "scripts reload");
			String failed = String.join("\n", messages(admin));
			assertTrue(failed.contains("Script reload failed"));
			clear(admin);
			command(admin, "scriptdir");
			String after = String.join("\n", messages(admin));
			assertTrue(after.contains("deprecated"));
			assertTrue(after.contains("Last reload failed"));
			assertFalse("no absolute path may leak on failure",
					after.contains("/Users/")
							|| after.contains("content/dist")
							|| after.contains("Developer"));
		} finally {
			if (previousDir == null) {
				System.clearProperty("singlescape.contentDir");
			} else {
				System.setProperty("singlescape.contentDir", previousDir);
			}
		}
	}

	@Test
	public void scriptdirRequiresAdminRights() {
		context = Context.create("js");
		publishWithModule();
		Player player = player(99, 0);
		command(player, "scriptdir");
		assertTrue(messages(player).isEmpty());
	}

	@Test
	public void reservedAliasesCannotBeRegisteredByContent() {
		context = Context.create("js");
		try {
			ScriptRuntimeTestFixture.publish(context, () ->
					RouteRegistry.putHost(ExecutableRouteKey.command("scripts"),
							arguments -> { }));
			assertTrue("scripts must reject", false);
		} catch (RuntimeException expected) {
			assertTrue(expected.getMessage().contains("reserved"));
		}
	}

	@Test
	public void bareReloadDelegatesToTruthfulReload() throws Exception {
		context = Context.create("js");
		ensureCompiledFixtureSupport();
		ScriptRuntimeTestFixture.reset();
		com.rs2.script.ScriptHost.getInstance().reload();
		Player admin = player(91, 2);
		long before = com.rs2.script.ScriptHost.getInstance()
				.getActiveGeneration();
		command(admin, "reload");
		long after = com.rs2.script.ScriptHost.getInstance()
				.getActiveGeneration();
		assertTrue(after > before);
		assertTrue(messages(admin).stream().anyMatch(
				m -> m.contains("Scripts reloaded")));
	}

	@Test
	public void statusReportsReloadDiagnosticsAndDuplicateRouteFailure()
			throws Exception {
		context = Context.create("js");
		ensureCompiledFixtureSupport();
		ScriptRuntimeTestFixture.reset();
		java.nio.file.Path root = java.nio.file.Files.createTempDirectory(
				"wp9-dup-route");
		java.nio.file.Files.write(root.resolve("loader.js"),
				("onCommand('ok', function () {});"
						+ "onCommand('dup', function () {});"
						+ "onCommand('dup', function () {});")
						.getBytes(StandardCharsets.UTF_8));
		String previousDir = System.getProperty("singlescape.contentDir");
		System.setProperty("singlescape.contentDir", root.toAbsolutePath().toString());
		try {
			com.rs2.script.ScriptHost.getInstance().reload();
			Player admin = player(100, 2);
			command(admin, "scripts status");
			String joined = String.join("\n", messages(admin));
			assertTrue(joined.contains("Last reload failed"));
			assertTrue(joined.contains("command:dup"));
			assertTrue(joined.contains("Reload diagnostic"));
		} finally {
			if (previousDir == null) {
				System.clearProperty("singlescape.contentDir");
			} else {
				System.setProperty("singlescape.contentDir", previousDir);
			}
		}
	}

	// ─── Helpers ────────────────────────────────────────────────────────────

	private void ensureCompiledFixtureSupport() throws Exception {
		Wp5PlayerSupport.ensureItemDefinitions();
		Wp5PlayerSupport.ensureObjectDefinitions();
		Wp5PlayerSupport.ensureNpcDefinitions();
		Wp5PlayerSupport.ensureAreaRegions();
	}

	private void publishWithModule() {
		ScriptRuntimeTestFixture.publish(context, () -> {
			RegistryStore.recordModule(new ModuleRecord("demo-module", 1,
					null, null));
			CommandHandlerRegistry.put("hello", context.eval("js",
					"(function () { return 'hi'; })"));
			DefinitionRegistry.put(DefinitionKind.BOSS, "demo-boss",
					context.eval("js", "({id:'demo-boss'})"));
			DefinitionRegistry.put(DefinitionKind.QUEST, "demo-quest",
					context.eval("js", "({id:'demo-quest'})"));
		});
	}

	private static Player player(int slot, int rights) {
		RecordingPlayer player = new RecordingPlayer(slot);
		player.playerName = "diag-player-" + slot;
		player.playerRights = rights;
		player.initialized = true;
		player.isActive = true;
		player.disconnected = false;
		// outStream is created lazily by getOutStream() so the recording
		// stream captures every sendMessage payload.
		PlayerHandler.players[slot] = player;
		return player;
	}

	private static void command(Player player, String text) {
		ByteBuf payload = Unpooled.buffer(text.length() + 1);
		payload.writeBytes(text.getBytes(StandardCharsets.UTF_8));
		payload.writeByte(10);
		new Commands().processPacket(player,
				new Packet(PACKET_ID, Packet.Type.FIXED, payload));
	}

	private static List<String> messages(Player player) {
		return ((RecordingPlayer) player).messages;
	}

	private static void clear(Player player) {
		((RecordingPlayer) player).messages.clear();
	}

	private static final class RecordingPlayer extends Client {
		private final List<String> messages = new ArrayList<>();
		private final RecordingStream recording;

		RecordingPlayer(int slot) {
			super(null, slot);
			recording = new RecordingStream();
			outStream = recording;
			outStream.packetEncryption =
					new org.apollo.util.security.IsaacRandom(new int[4]);
		}

		@Override
		public Stream getOutStream() {
			return recording;
		}

		@Override
		public void flushOutStream() {
			if (outStream != null) {
				outStream.currentOffset = 0;
			}
		}

		private final class RecordingStream extends Stream {
			RecordingStream() {
				super(new byte[65536]);
			}

			@Override
			public void writeString(String value) {
				messages.add(value);
				super.writeString(value);
			}
		}
	}
}
