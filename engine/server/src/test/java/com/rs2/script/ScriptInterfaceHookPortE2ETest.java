package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.interfacehook.InterfaceHookDefinitionRegistry;
import com.rs2.script.interfacehook.ScriptInterfaceHookRuntime;
import com.rs2.script.registries.RegistryStore;

/**
 * End-to-end interface hook dispatch and reload behavior.
 */
public class ScriptInterfaceHookPortE2ETest {

	private static final int COOKING_GUIDE_INTERFACE = 8134;
	private static final int CLOSE_BUTTON = 55096;

	private String previousContentDir;

	@Before
	public void setUp() throws Exception {
		ScriptRuntimeTestFixture.reset();
		previousContentDir = System.getProperty("singlescape.contentDir");
	}

	@After
	public void tearDown() throws Exception {
		ScriptRuntimeTestFixture.reset();
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
	}

	@Test
	public void compiledCookingGuideRegistersHook() throws Exception {
		File contentDir = findCompiledContent();
		assertTrue("Run pnpm build:content before Maven tests",
				contentDir.isDirectory());
		System.setProperty("singlescape.contentDir",
				contentDir.getAbsolutePath());
		Wp5PlayerSupport.ensureItemDefinitions();
		Wp5PlayerSupport.ensureObjectDefinitions();
		Wp5PlayerSupport.ensureNpcDefinitions();
		Wp5PlayerSupport.ensureCustomNamespaceDefinitions();
		Wp5PlayerSupport.ensureAreaRegions();
		ScriptHost.getInstance().reload();

		assertNotNull(InterfaceHookDefinitionRegistry.get("cooking-guide"));
		assertEquals(COOKING_GUIDE_INTERFACE,
				InterfaceHookDefinitionRegistry.get("cooking-guide")
						.interfaceId());
	}

	@Test
	public void scopedButtonConsumesOnlyForRegisteredInterface()
			throws Exception {
		File contentDir = findCompiledContent();
		System.setProperty("singlescape.contentDir",
				contentDir.getAbsolutePath());
		Wp5PlayerSupport.ensureItemDefinitions();
		Wp5PlayerSupport.ensureObjectDefinitions();
		Wp5PlayerSupport.ensureNpcDefinitions();
		Wp5PlayerSupport.ensureCustomNamespaceDefinitions();
		Wp5PlayerSupport.ensureAreaRegions();
		ScriptHost.getInstance().reload();

		Player player = Wp5PlayerSupport.player(94);
		player.lastMainFrameInterface = COOKING_GUIDE_INTERFACE;
		player.scriptHookArmed = true;

		assertTrue(ScriptInterfaceHookRuntime.getInstance().handleButton(
				player, CLOSE_BUTTON));
	}

	@Test
	public void legacyOpenedInterfaceDoesNotConsumeHookButton()
			throws Exception {
		File contentDir = findCompiledContent();
		System.setProperty("singlescape.contentDir",
				contentDir.getAbsolutePath());
		Wp5PlayerSupport.ensureItemDefinitions();
		Wp5PlayerSupport.ensureObjectDefinitions();
		Wp5PlayerSupport.ensureNpcDefinitions();
		Wp5PlayerSupport.ensureCustomNamespaceDefinitions();
		Wp5PlayerSupport.ensureAreaRegions();
		ScriptHost.getInstance().reload();

		// Legacy quest code opens 8134 via PacketSender.showInterface without
		// arming the hook; the hook must not swallow the shared Close button.
		Player player = Wp5PlayerSupport.player(95);
		player.lastMainFrameInterface = COOKING_GUIDE_INTERFACE;
		player.scriptHookArmed = false;

		assertFalse(ScriptInterfaceHookRuntime.getInstance().handleButton(
				player, CLOSE_BUTTON));
	}

	@Test
	public void scopedButtonFallsThroughWhenAnotherInterfaceIsOpen()
			throws Exception {
		File contentDir = findCompiledContent();
		System.setProperty("singlescape.contentDir",
				contentDir.getAbsolutePath());
		Wp5PlayerSupport.ensureItemDefinitions();
		Wp5PlayerSupport.ensureObjectDefinitions();
		Wp5PlayerSupport.ensureNpcDefinitions();
		Wp5PlayerSupport.ensureCustomNamespaceDefinitions();
		Wp5PlayerSupport.ensureAreaRegions();
		ScriptHost.getInstance().reload();

		Player player = Wp5PlayerSupport.player(96);
		player.lastMainFrameInterface = 1000;
		player.scriptHookArmed = true;

		assertFalse(ScriptInterfaceHookRuntime.getInstance().handleButton(
				player, CLOSE_BUTTON));
	}

	@Test
	public void scriptedShowArmsHookAndReplacementFiresClose()
			throws Exception {
		File contentDir = findCompiledContent();
		System.setProperty("singlescape.contentDir",
				contentDir.getAbsolutePath());
		Wp5PlayerSupport.ensureItemDefinitions();
		Wp5PlayerSupport.ensureObjectDefinitions();
		Wp5PlayerSupport.ensureNpcDefinitions();
		Wp5PlayerSupport.ensureCustomNamespaceDefinitions();
		Wp5PlayerSupport.ensureAreaRegions();
		ScriptHost.getInstance().reload();

		Player player = Wp5PlayerSupport.player(97);

		// Opening the cooking guide through the scripted path arms the hook.
		assertTrue(Wp5PlayerSupport.scripted(player).getPresentation()
				.showInterface(COOKING_GUIDE_INTERFACE));
		assertTrue("scripted show must arm the hook",
				player.scriptHookArmed);
		assertTrue("armed hook must consume the Close button",
				ScriptInterfaceHookRuntime.getInstance().handleButton(
						player, CLOSE_BUTTON));

		// Showing a different scripted interface must run the old onClose and
		// re-arm for the new one.
		assertTrue(Wp5PlayerSupport.scripted(player).getPresentation()
				.showInterface(1000));
		assertTrue("replacement show must keep the hook armed",
				player.scriptHookArmed);
		assertFalse("the Close button no longer maps to the cooking guide",
				ScriptInterfaceHookRuntime.getInstance().handleButton(
						player, CLOSE_BUTTON));
	}

	@Test
	public void replacementShowFiresPreviousHookOnClose() throws Exception {
		Path root = Files.createTempDirectory("script-hook-close");
		Files.write(root.resolve("loader.js"), (
				"defineInterfaceHook({id:'alpha',interfaceId:8134,"
				+ "onOpen:function(ctx){ctx.player.message('alpha-open');},"
				+ "onClose:function(ctx){ctx.player.message('alpha-close');}});"
				+ "defineInterfaceHook({id:'beta',interfaceId:1000,"
				+ "onOpen:function(ctx){ctx.player.message('beta-open');}});")
				.getBytes(StandardCharsets.UTF_8));
		System.setProperty("singlescape.contentDir",
				root.toFile().getAbsolutePath());
		Wp5PlayerSupport.ensureItemDefinitions();
		Wp5PlayerSupport.ensureObjectDefinitions();
		Wp5PlayerSupport.ensureNpcDefinitions();
		Wp5PlayerSupport.ensureCustomNamespaceDefinitions();
		ScriptHost.getInstance().reload();
		assertNotNull(InterfaceHookDefinitionRegistry.get("alpha"));

		Player player = Wp5PlayerSupport.player(98);
		com.rs2.script.ScriptedPlayer scripted =
				Wp5PlayerSupport.scripted(player);

		scripted.getPresentation().showInterface(8134);
		assertTrue("opening alpha must arm the hook", player.scriptHookArmed);

		// Replacing alpha with beta must fire alpha's onClose and re-arm beta.
		scripted.getPresentation().showInterface(1000);
		assertTrue("replacement must keep a hook armed", player.scriptHookArmed);
		assertFalse("the Close button no longer maps to a 8134 hook",
				ScriptInterfaceHookRuntime.getInstance().handleButton(
						player, CLOSE_BUTTON));

		// Closing beta through the scripted path fires beta's onClose.
		scripted.getPresentation().closeInterfaces();
		assertFalse("closing must disarm the hook", player.scriptHookArmed);
	}

	@Test
	public void tradeBlocksDuplicateScriptedShowFromReArmingHook()
			throws Exception {
		File contentDir = findCompiledContent();
		System.setProperty("singlescape.contentDir",
				contentDir.getAbsolutePath());
		Wp5PlayerSupport.ensureItemDefinitions();
		Wp5PlayerSupport.ensureObjectDefinitions();
		Wp5PlayerSupport.ensureNpcDefinitions();
		Wp5PlayerSupport.ensureCustomNamespaceDefinitions();
		Wp5PlayerSupport.ensureAreaRegions();
		ScriptHost.getInstance().reload();

		Player player = Wp5PlayerSupport.player(99);
		Wp5PlayerSupport.RecordingPlayer recording =
				Wp5PlayerSupport.recording(player);
		com.rs2.script.ScriptedPlayer scripted =
				Wp5PlayerSupport.scripted(player);

		assertTrue(scripted.getPresentation().showInterface(
				COOKING_GUIDE_INTERFACE));
		assertTrue("scripted show must arm the hook", player.scriptHookArmed);

		recording.clearPackets();
		player.inTrade = true;
		assertFalse("trade must block re-showing the same interface",
				scripted.getPresentation().showInterface(
						COOKING_GUIDE_INTERFACE));
		assertTrue("failed re-show must leave the hook armed",
				player.scriptHookArmed);
		assertEquals("blocked show must not send another open packet", 0,
				recording.flushCount);
	}

	@Test
	public void legacyCloseAllWindowsFiresHookOnClose() throws Exception {
		final boolean[] closed = { false };
		Context context = Context.newBuilder("js")
				.allowHostAccess(HostAccess.ALL)
				.build();
		context.getBindings("js").putMember("markClosed",
				(Runnable) () -> closed[0] = true);

		RegistryStore.State candidate = RegistryStore.beginStaging();
		ScriptFunctions.getInstance().getDefineInterfaceHook().accept(
				context.eval("js",
						"({id:'alpha',interfaceId:8134,"
						+ "onClose:function(ctx){ markClosed.run(); }})"));
		ScriptRuntimeTestFixture.publishCandidate(context, candidate);

		Player player = Wp5PlayerSupport.player(100);
		Wp5PlayerSupport.scripted(player).getPresentation().showInterface(8134);
		assertTrue("opening alpha must arm the hook", player.scriptHookArmed);

		player.getPacketSender().closeAllWindows();

		assertFalse("legacy close must disarm the hook",
				player.scriptHookArmed);
		assertTrue("legacy close must fire hook onClose", closed[0]);
	}

	private static File findCompiledContent() {
		File fromWorkspace = new File(System.getProperty("user.dir"),
				"content/dist");
		if (fromWorkspace.isDirectory()) {
			return fromWorkspace;
		}
		return new File(System.getProperty("user.dir"), "../../content/dist");
	}
}
