package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Test;

import org.apollo.cache.def.ItemDefinition;
import org.apollo.cache.def.ObjectDefinition;

import com.rs2.script.registries.AreaRegistry;
import com.rs2.script.registries.BossRegistry;
import com.rs2.script.registries.CommandHandlerRegistry;
import com.rs2.script.registries.NpcHandlerRegistry;
import com.rs2.script.registries.ItemHandlerRegistry;
import com.rs2.script.registries.InteractionHandlerRegistry;
import com.rs2.script.registries.LifecycleRegistry;
import com.rs2.script.registries.ObjectHandlerRegistry;
import com.rs2.script.registries.QuestRegistry;
import com.rs2.script.registries.RaidRegistry;
import com.rs2.script.quest.QuestDefinition;

public class ScriptHostTest {

	private String previousContentDir;

	@After
	public void restoreProperty() {
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
	}

	@Test
	public void currentCompiledLoaderRegistersEverySupportedCategory()
			throws Exception {
		File contentDir = findCompiledContent();
		assertTrue("Run pnpm build:content before Maven tests", contentDir.isDirectory());
		setContentDir(contentDir);
		ItemDefinition[] previousItems = ItemDefinition.getDefinitions();
		try {
			Wp5PlayerSupport.ensureItemDefinitions();

			ScriptHost.getInstance().reload();

			assertNotNull(BossRegistry.get(12001));
		assertNotNull(QuestRegistry.get("dragon-awakens"));
		assertNotNull(RaidRegistry.get("temple_of_zaros"));
		assertNotNull(AreaRegistry.get("dragon_island"));
		assertNotNull(NpcHandlerRegistry.get(1, "first"));
		assertNotNull(CommandHandlerRegistry.get("hello"));
		assertNotNull(ItemHandlerRegistry.getItem(14990, "first"));
		assertNotNull(ItemHandlerRegistry.getItemOnItem(14990, 14991));
		assertNotNull(ItemHandlerRegistry.getItemOnObject(14990, 14992));
		assertNotNull(ItemHandlerRegistry.getItemOnNpc(14990, 14993));
		assertNotNull(LifecycleRegistry.getNpcDeath(14994));
		assertNotNull(LifecycleRegistry.getItemPickup(14995));
		assertNotNull(LifecycleRegistry.getAreaHandler(
				"enter", "bridge-example-lumbridge-courtyard"));
		assertNotNull(LifecycleRegistry.getAreaHandler(
				"leave", "bridge-example-lumbridge-courtyard"));

		com.rs2.script.definition.DefinitionRecord dropTable =
				com.rs2.script.definition.DefinitionRegistry.get(
						com.rs2.script.definition.DefinitionKind.DROP_TABLE,
						"dragon_king_loot");
		assertNotNull(dropTable);
		assertEquals(0, dropTable.schemaVersion());
		assertEquals(com.rs2.script.definition.ModuleScope.LEGACY_SOURCE,
				dropTable.source());
		assertNotNull(com.rs2.script.drop.DropTableRegistry
				.get("dragon_guardian_loot"));
		assertNotNull(com.rs2.script.drop.DropTableRegistry
				.get("elder_wizard_loot"));
		assertNotNull(com.rs2.script.drop.DropTableRegistry
				.get("zaros_raid_loot"));
		assertEquals(7, com.rs2.script.drop.DropTableRegistry
				.get("dragon_guardian_loot").entries().size());
		assertEquals(7, com.rs2.script.drop.DropTableRegistry
				.get("elder_wizard_loot").entries().size());
		assertEquals(7, com.rs2.script.drop.DropTableRegistry
				.get("dragon_king_loot").entries().size());
		assertEquals(6, com.rs2.script.drop.DropTableRegistry
				.get("zaros_raid_loot").entries().size());
		} finally {
			setDefinitions(ItemDefinition.class, previousItems);
		}
	}

	@Test
	public void compiledLoaderRecordsEveryCategoryAsSourceAwareEnvelopes()
			throws Exception {
		File contentDir = findCompiledContent();
		assertTrue("Run pnpm build:content before Maven tests", contentDir.isDirectory());
		setContentDir(contentDir);
		ItemDefinition[] previousItems = ItemDefinition.getDefinitions();
		try {
			Wp5PlayerSupport.ensureItemDefinitions();

			ScriptHost.getInstance().reload();

			assertNotNull(BossRegistry.get(12001));
			assertNotNull(QuestRegistry.get("dragon-awakens"));
			assertNotNull(RaidRegistry.get("temple_of_zaros"));
			assertNotNull(AreaRegistry.get("dragon_island"));
			assertNotNull(NpcHandlerRegistry.get(1, "first"));
			assertNotNull(CommandHandlerRegistry.get("hello"));
			assertNotNull(ItemHandlerRegistry.getItem(14990, "first"));
			assertNotNull(ItemHandlerRegistry.getItemOnItem(14990, 14991));
			assertNotNull(ItemHandlerRegistry.getItemOnObject(14990, 14992));
			assertNotNull(ItemHandlerRegistry.getItemOnNpc(14990, 14993));
			assertNotNull(LifecycleRegistry.getNpcDeath(14994));
			assertNotNull(LifecycleRegistry.getItemPickup(14995));
			assertNotNull(LifecycleRegistry.getAreaHandler(
					"enter", "bridge-example-lumbridge-courtyard"));
			assertNotNull(LifecycleRegistry.getAreaHandler(
					"leave", "bridge-example-lumbridge-courtyard"));

			com.rs2.script.definition.DefinitionRecord boss =
					com.rs2.script.definition.DefinitionRegistry.get(
							com.rs2.script.definition.DefinitionKind.BOSS,
							"12001");
			assertNotNull(boss);
			assertEquals(0, boss.schemaVersion());
			assertEquals(com.rs2.script.definition.ModuleScope.LEGACY_SOURCE,
					boss.source());
			com.rs2.script.definition.DefinitionRecord quest =
					com.rs2.script.definition.DefinitionRegistry.get(
							com.rs2.script.definition.DefinitionKind.QUEST,
							"dragon-awakens");
			assertNotNull(quest);
			assertNotNull(quest.questPayload());
			com.rs2.script.definition.DefinitionRecord raid =
					com.rs2.script.definition.DefinitionRegistry.get(
							com.rs2.script.definition.DefinitionKind.RAID,
							"temple_of_zaros");
			assertNotNull(raid);
			assertTrue(raid.isGuestPayload());
			com.rs2.script.definition.DefinitionRecord area =
					com.rs2.script.definition.DefinitionRegistry.get(
							com.rs2.script.definition.DefinitionKind.AREA,
							"dragon_island");
			assertNotNull(area);
			assertTrue(area.isGuestPayload());
			assertNotNull(com.rs2.script.drop.DropTableRegistry
					.get("dragon_king_loot"));

			assertEquals(0, ScriptHost.getInstance().getRuntimeReport()
					.moduleCount());
			assertTrue(ScriptHost.getInstance().getRuntimeReport()
					.routeCount() > 0);
		} finally {
			setDefinitions(ItemDefinition.class, previousItems);
		}
	}

	@Test
	public void moduleScopeRecordsManifestAndSourceAwareEnvelopes()
			throws Exception {
		Path root = Files.createTempDirectory("script-host-module-scope");
		Path loader = root.resolve("loader.js");
		setContentDir(root.toFile());
		Files.write(loader, (
				"registerContentModule({id:'demo-module',schemaVersion:1},"
						+ "function () {"
						+ "defineBoss({npcId:9500});"
						+ "defineRaid({id:'demo-raid'});"
						+ "onCommand('demo-command', function () {});"
						+ "});"
						+ "onCommand('legacy-command', function () {});")
				.getBytes(StandardCharsets.UTF_8));
		ScriptHost host = ScriptHost.getInstance();
		host.reload();

		assertNotNull(CommandHandlerRegistry.get("demo-command"));
		assertNotNull(CommandHandlerRegistry.get("legacy-command"));
		assertEquals(1, host.getRuntimeReport().moduleCount());
		com.rs2.script.definition.DefinitionRecord boss =
				com.rs2.script.definition.DefinitionRegistry.get(
						com.rs2.script.definition.DefinitionKind.BOSS, "9500");
		assertNotNull(boss);
		assertEquals("demo-module", boss.source());
		assertEquals(1, boss.schemaVersion());
		com.rs2.script.definition.DefinitionRecord raid =
				com.rs2.script.definition.DefinitionRegistry.get(
						com.rs2.script.definition.DefinitionKind.RAID,
						"demo-raid");
		assertNotNull(raid);
		assertEquals("demo-module", raid.source());
		com.rs2.script.definition.DefinitionRecord legacy =
				com.rs2.script.definition.DefinitionRegistry.get(
						com.rs2.script.definition.DefinitionKind.BOSS, "12001");
		assertNull(legacy);
		com.rs2.script.route.ExecutableRouteRecord legacyCommand =
				ScriptHost.getInstance().readActiveRegistry(state ->
						com.rs2.script.route.RouteRegistry.get(state,
								com.rs2.script.route.ExecutableRouteKey
										.command("legacy-command")));
		assertNotNull(legacyCommand);
		assertEquals(com.rs2.script.definition.ModuleScope.LEGACY_SOURCE,
				legacyCommand.source());
	}

	@Test
	public void nestedAndDuplicateModuleScopesRejectTheCandidate()
			throws Exception {
		Path root = Files.createTempDirectory("script-host-module-reject");
		Path loader = root.resolve("loader.js");
		setContentDir(root.toFile());
		Files.write(loader, (
				"onCommand('stable-module-base', function () {});")
				.getBytes(StandardCharsets.UTF_8));
		ScriptHost host = ScriptHost.getInstance();
		host.reload();
		Context stable = host.getContext();
		long generation = host.getActiveGeneration();

		Files.write(loader, (
				"registerContentModule({id:'outer',schemaVersion:1},"
						+ "function () {"
						+ "registerContentModule({id:'inner',schemaVersion:1},"
						+ "function () { onCommand('inner-cmd', function () {}); });"
						+ "});").getBytes(StandardCharsets.UTF_8));
		host.reload();
		assertSame(stable, host.getContext());
		assertEquals(generation, host.getActiveGeneration());
		assertNull(CommandHandlerRegistry.get("inner-cmd"));

		Files.write(loader, (
				"registerContentModule({id:'same',schemaVersion:1},"
						+ "function () { onCommand('a', function () {}); });"
						+ "registerContentModule({id:'same',schemaVersion:1},"
						+ "function () { onCommand('b', function () {}); });")
				.getBytes(StandardCharsets.UTF_8));
		host.reload();
		assertSame(stable, host.getContext());
		assertEquals(generation, host.getActiveGeneration());
		assertNull(CommandHandlerRegistry.get("a"));
		assertNull(CommandHandlerRegistry.get("b"));

		Files.write(loader, (
				"registerContentModule({id:'bad',schemaVersion:0},"
						+ "function () { onCommand('c', function () {}); });")
				.getBytes(StandardCharsets.UTF_8));
		host.reload();
		assertSame(stable, host.getContext());
		assertEquals(generation, host.getActiveGeneration());
		assertNull(CommandHandlerRegistry.get("c"));
	}

	@Test
	public void questDependenciesAreValidatedBeforeCandidateCommit()
			throws Exception {
		Path root = Files.createTempDirectory("script-host-quest-validation");
		Path loader = root.resolve("loader.js");
		setContentDir(root.toFile());
		Files.write(loader, quest("stable-quest", "").getBytes(
				StandardCharsets.UTF_8));
		ScriptHost host = ScriptHost.getInstance();
		host.reload();
		Context stableContext = host.getContext();
		QuestDefinition stable = QuestRegistry.get("stable-quest");
		assertNotNull(stable);

		Files.write(loader, quest("broken-quest",
				",requirements:{completedQuests:['missing-quest']}")
				.getBytes(StandardCharsets.UTF_8));
		host.reload();
		assertSame(stableContext, host.getContext());
		assertSame(stable, QuestRegistry.get("stable-quest"));
		assertNull(QuestRegistry.get("broken-quest"));

		Files.write(loader, (quest("cycle-one",
				",requirements:{completedQuests:['cycle-two']}")
				+ quest("cycle-two",
				",requirements:{completedQuests:['cycle-one']}"))
				.getBytes(StandardCharsets.UTF_8));
		host.reload();
		assertSame(stableContext, host.getContext());
		assertSame(stable, QuestRegistry.get("stable-quest"));
	}

	private static String quest(String id, String optional) {
		return "defineQuest({id:'" + id + "',name:'Quest',summary:'Summary',"
				+ "stages:[{stage:0,objective:'Done'}]" + optional + "});";
	}

	@Test
	public void invalidAndDuplicateLifecycleRegistrationsRetainLastGoodGeneration()
			throws Exception {
		Path root = Files.createTempDirectory("script-host-lifecycle-rollback");
		Path loader = root.resolve("loader.js");
		setContentDir(root.toFile());
		Files.write(loader, (
				"onLogin(function () {});"
				+ "onNpcDeath(7000, function () {});"
				+ "onEnterArea({id:'stable',minX:1,minY:2,maxX:3,maxY:4},function(){});")
				.getBytes(StandardCharsets.UTF_8));
		ScriptHost host = ScriptHost.getInstance();
		host.reload();
		Context stable = host.getContext();
		Value stableLogin = LifecycleRegistry.getSingleton("login");
		assertNotNull(stableLogin);

		Files.write(loader, (
				"onLogout(function () {});"
				+ "onNpcDeath(7100, function () {});"
				+ "onNpcDeath(7100, function () {});")
				.getBytes(StandardCharsets.UTF_8));
		host.reload();
		assertSame(stable, host.getContext());
		assertSame(stableLogin, LifecycleRegistry.getSingleton("login"));
		assertNull(LifecycleRegistry.getSingleton("logout"));
		assertNull(LifecycleRegistry.getNpcDeath(7100));

		Files.write(loader, (
				"onLogout(function () {});"
				+ "onEnterArea({id:'bad',minX:3,minY:2,maxX:1,maxY:4},function(){});")
				.getBytes(StandardCharsets.UTF_8));
		host.reload();
		assertSame(stable, host.getContext());
		assertNull(LifecycleRegistry.getSingleton("logout"));
	}

	@Test
	public void failedReloadRetainsLastGoodContextAndSuccessfulReloadRemovesOldHandlers()
			throws Exception {
		Path root = Files.createTempDirectory("script-host-reload");
		Path loader = root.resolve("loader.js");
		setContentDir(root.toFile());
		Files.write(loader,
				"onCommand('stable', function () { return 'ok'; });".getBytes(StandardCharsets.UTF_8));

		ScriptHost host = ScriptHost.getInstance();
		host.reload();
		Context goodContext = host.getContext();
		Value goodHandler = CommandHandlerRegistry.get("stable");
		assertNotNull(goodHandler);

		Files.write(loader, "this is not valid javascript !!!".getBytes(StandardCharsets.UTF_8));
		host.reload();
		assertSame(goodContext, host.getContext());
		assertSame(goodHandler, CommandHandlerRegistry.get("stable"));
		assertTrue(goodHandler.canExecute());

		Files.write(loader,
				"onCommand('replacement', function () { return 'new'; });".getBytes(StandardCharsets.UTF_8));
		host.reload();
		assertFalse(goodContext == host.getContext());
		assertNull(CommandHandlerRegistry.get("stable"));
		assertNotNull(CommandHandlerRegistry.get("replacement"));
	}

	@Test
	public void invalidAndDuplicateItemRegistrationsRollBackTheWholeCandidate()
			throws Exception {
		Path root = Files.createTempDirectory("script-host-registration-rollback");
		Path loader = root.resolve("loader.js");
		setContentDir(root.toFile());
		Files.write(loader, (
				"defineBoss({ npcId: 9100 });"
				+ "defineQuest({id:'stable-quest',name:'Stable Quest',"
				+ "summary:'Stable.',stages:[{stage:0,objective:'Stay stable.'}]});"
				+ "defineRaid({ id: 'stable-raid' });"
				+ "defineArea({ id: 'stable-area' });"
				+ "onNpc(9101, 'first', function () {});"
				+ "onObject(9102, 'first', function () {});"
				+ "onCommand('stable-command', function () {});"
				+ "onItem(9103, 'first', function () {});"
				+ "onItemOnItem(9103, 9104, function () {});"
				+ "onItemOnObject(9103, 9105, function () {});"
				+ "onItemOnNpc(9103, 9106, function () {});")
				.getBytes(StandardCharsets.UTF_8));

		ScriptHost host = ScriptHost.getInstance();
		host.reload();
		AggregateSnapshot stable = AggregateSnapshot.capture(host);

		Files.write(loader, (
				"onCommand('candidate-command', function () {});"
				+ "onItem(-1, 'first', function () {});")
				.getBytes(StandardCharsets.UTF_8));
		host.reload();
		stable.assertStillActive(host);
		assertNull(CommandHandlerRegistry.get("candidate-command"));

		Files.write(loader, (
				"onCommand('callback-candidate-command', function () {});"
				+ "onItem(9300, 'first', 42);")
				.getBytes(StandardCharsets.UTF_8));
		host.reload();
		stable.assertStillActive(host);
		assertNull(CommandHandlerRegistry.get("callback-candidate-command"));

		Files.write(loader, (
				"onCommand('action-candidate-command', function () {});"
				+ "onItem(9300, 'open', function () {});")
				.getBytes(StandardCharsets.UTF_8));
		host.reload();
		stable.assertStillActive(host);
		assertNull(CommandHandlerRegistry.get("action-candidate-command"));

		Files.write(loader, (
				"onCommand('duplicate-candidate-command', function () {});"
				+ "onItemOnItem(9200, 9201, function () {});"
				+ "onItemOnItem(9201, 9200, function () {});")
				.getBytes(StandardCharsets.UTF_8));
		host.reload();
		stable.assertStillActive(host);
		assertNull(CommandHandlerRegistry.get("duplicate-candidate-command"));
		assertNull(ItemHandlerRegistry.getItemOnItem(9200, 9201));
	}

	@Test
	public void contextExposesOnlyExplicitBridgeAndReadOnlyContentImports() throws Exception {
		Path root = Files.createTempDirectory("script-host-access");
		Path imported = root.resolve("imported.js");
		Files.write(imported, "export const answer = 42;".getBytes(StandardCharsets.UTF_8));

		try (Context context = ScriptHost.buildContext(root.toFile())) {
			Value capabilities = context.eval("js",
					"({ worker: typeof Worker, process: typeof process })");
			assertTrue(capabilities.getMember("worker").asString().equals("undefined"));
			assertTrue(capabilities.getMember("process").asString().equals("undefined"));
			try {
				context.eval("js", "Java.type('java.lang.System')");
				fail("Expected host class lookup to fail");
			} catch (PolyglotException expected) {
				assertTrue(expected.getMessage().contains("Access to host class")
						|| expected.getMessage().contains("java.lang.System"));
			}

			Source allowed = Source.newBuilder("js",
					"import { answer } from './imported.js'; globalThis.loadedAnswer = answer;",
					"allowed.mjs").uri(root.resolve("allowed.mjs").toUri())
					.mimeType("application/javascript+module").build();
			context.eval(allowed);
			assertTrue(context.getBindings("js").getMember("loadedAnswer").asInt() == 42);

			Source denied = Source.newBuilder("js", "import '/etc/passwd';", "denied.mjs")
					.uri(root.resolve("denied.mjs").toUri())
					.mimeType("application/javascript+module").build();
			try {
				context.eval(denied);
				fail("Expected import outside the content root to fail");
			} catch (PolyglotException expected) {
				assertTrue(expected.getMessage().contains("passwd")
						|| expected.getMessage().contains("denied"));
			}
		}
	}

	@Test
	public void scriptArrayExportsOnlyImmutableLengthAndIndexedRead()
			throws Exception {
		Path root = Files.createTempDirectory("script-array-access");
		try (Context context = ScriptHost.buildContext(root.toFile())) {
			context.getBindings("js").putMember("scriptArray",
					new ScriptArray(new Object[] { "first", "second" }));
			Value result = context.eval("js",
					"({"
					+ "length: scriptArray.length(),"
					+ "first: scriptArray.get(0),"
					+ "fraction: scriptArray.get(0.5),"
					+ "iterator: typeof scriptArray.iterator,"
					+ "getClass: typeof scriptArray.getClass,"
					+ "assigned: (() => { try { scriptArray[0] = 'changed'; }"
					+ " catch (_) {} return scriptArray.get(0); })()"
					+ "})");
			assertTrue(result.getMember("length").asInt() == 2);
			assertTrue("first".equals(result.getMember("first").asString()));
			assertTrue(result.getMember("fraction").isNull());
			assertTrue("undefined".equals(result.getMember("iterator").asString()));
			assertTrue("undefined".equals(result.getMember("getClass").asString()));
			assertTrue("first".equals(result.getMember("assigned").asString()));
		}
	}

	@Test
	public void objectRegistrationAcceptsOnlyCanonicalOrdinalActions() {
		Path root;
		try {
			root = Files.createTempDirectory("script-object-actions");
		} catch (Exception e) {
			throw new AssertionError(e);
		}
		try (Context context = ScriptHost.buildContext(root.toFile())) {
			com.rs2.script.registries.RegistryStore.State candidate =
					com.rs2.script.registries.RegistryStore.beginStaging();
			try {
				context.eval("js", "onObject(100, 'open', function () {});");
				fail("unknown object action should reject the candidate");
			} catch (PolyglotException expected) {
				com.rs2.script.registries.RegistryStore.rollback(candidate);
			}
			candidate = com.rs2.script.registries.RegistryStore.beginStaging();
			context.eval("js", "onObject(100, 'first', function () {});");
			ScriptRuntimeTestFixture.publishCandidate(context, candidate);
			assertNull(ObjectHandlerRegistry.get(100, "open"));
			assertNotNull(ObjectHandlerRegistry.get(100, "first"));
		}
	}

	@Test
	public void sparseButtonRegistrationValidatesBeforeNarrowingAndRollsBack()
			throws Exception {
		Path root = Files.createTempDirectory("script-button-registration");
		Path loader = root.resolve("loader.js");
		setContentDir(root.toFile());
		Files.write(loader,
				"onButton(255255, function () {});".getBytes(StandardCharsets.UTF_8));
		ScriptHost host = ScriptHost.getInstance();
		host.reload();
		Context stable = host.getContext();
		long generation = host.getActiveGeneration();
		assertNotNull(InteractionHandlerRegistry.getButton(255255));

		String[] invalid = {
				"onButton(256, function () {});",
				"onButton(999, function () {});",
				"onButton(1.5, function () {});",
				"onButton(NaN, function () {});",
				"onButton(Infinity, function () {});",
				"onButton(255255, function () {});"
						+ "onButton(255255, function () {});"
		};
		for (String source : invalid) {
			Files.write(loader, source.getBytes(StandardCharsets.UTF_8));
			host.reload();
			assertSame(stable, host.getContext());
			assertTrue(generation == host.getActiveGeneration());
			assertNotNull(InteractionHandlerRegistry.getButton(255255));
		}
	}

	@Test
	public void guestCannotRegisterAfterItsCandidateHasCommitted()
			throws Exception {
		Path root = Files.createTempDirectory("script-post-commit-registration");
		Path loader = root.resolve("loader.js");
		setContentDir(root.toFile());
		Files.write(loader, (
				"onCommand('stable', function () {});"
				+ "globalThis.registerLate = function () {"
				+ "onCommand('late', function () {});"
				+ "};").getBytes(StandardCharsets.UTF_8));
		ScriptHost host = ScriptHost.getInstance();
		host.reload();
		Context stable = host.getContext();
		long generation = host.getActiveGeneration();

		try {
			stable.getBindings("js").getMember("registerLate").execute();
			fail("post-commit guest registration should fail");
		} catch (PolyglotException expected) {
			assertTrue(expected.getMessage().contains("candidate")
					|| expected.getMessage().contains("registrations"));
		}
		assertSame(stable, host.getContext());
		assertTrue(generation == host.getActiveGeneration());
		assertNotNull(CommandHandlerRegistry.get("stable"));
		assertNull(CommandHandlerRegistry.get("late"));
	}

	@Test
	public void definitionBackedRegistrationsRejectUnknownIdsAndRollBack()
			throws Exception {
		ItemDefinition[] previousItems = ItemDefinition.getDefinitions();
		ObjectDefinition[] previousObjects = ObjectDefinition.getDefinitions();
		setDefinitions(ItemDefinition.class, new ItemDefinition[] {
				new ItemDefinition(0), new ItemDefinition(1), new ItemDefinition(2)
		});
		setDefinitions(ObjectDefinition.class, new ObjectDefinition[] {
				new ObjectDefinition(0), new ObjectDefinition(1)
		});
		try {
			Path root = Files.createTempDirectory("script-definition-registration");
			Path loader = root.resolve("loader.js");
			setContentDir(root.toFile());
			Files.write(loader, (
					"onCommand('stable-definitions', function () {});"
					+ "onItemOnGroundItem(1, 2, function () {});"
					+ "onItemOnPlayer(1, function () {});"
					+ "onMagicOnItem(10, 2, function () {});"
					+ "onMagicOnObject(10, 1, function () {});")
					.getBytes(StandardCharsets.UTF_8));
			ScriptHost host = ScriptHost.getInstance();
			host.reload();
			Context stable = host.getContext();
			long generation = host.getActiveGeneration();
			assertNotNull(InteractionHandlerRegistry.getItemOnGroundItem(1, 2));
			assertNotNull(InteractionHandlerRegistry.getItemOnPlayer(1));
			assertNotNull(InteractionHandlerRegistry.getMagicOnItem(10, 2));
			assertNotNull(InteractionHandlerRegistry.getMagicOnObject(10, 1));

			String[] invalid = {
					"onItemOnGroundItem(1, 3, function () {});",
					"onItemOnPlayer(3, function () {});",
					"onMagicOnItem(10, 3, function () {});",
					"onMagicOnObject(10, 2, function () {});"
			};
			for (int index = 0; index < invalid.length; index++) {
				String command = "unknown-definition-" + index;
				Files.write(loader, ("onCommand('" + command
						+ "', function () {});" + invalid[index])
						.getBytes(StandardCharsets.UTF_8));
				host.reload();
				assertSame(stable, host.getContext());
				assertTrue(generation == host.getActiveGeneration());
				assertNull(CommandHandlerRegistry.get(command));
			}
		} finally {
			setDefinitions(ItemDefinition.class, previousItems);
			setDefinitions(ObjectDefinition.class, previousObjects);
		}
	}

	@Test
	public void duplicateInOneCategoryRollsBackEveryCandidateCategory()
			throws Exception {
		Path root = Files.createTempDirectory("script-cross-category-rollback");
		Path loader = root.resolve("loader.js");
		setContentDir(root.toFile());
		Files.write(loader,
				"onCommand('stable-cross-category', function () {});"
						.getBytes(StandardCharsets.UTF_8));
		ScriptHost host = ScriptHost.getInstance();
		host.reload();
		Context stable = host.getContext();
		long generation = host.getActiveGeneration();

		Files.write(loader, (
				"defineBoss({npcId:9400});"
				+ "defineRaid({id:'candidate-raid'});"
				+ "defineArea({id:'candidate-area'});"
				+ "onNpc(9401,'first',function(){});"
				+ "onObject(9402,'first',function(){});"
				+ "onCommand('candidate-command',function(){});"
				+ "onItem(9403,'first',function(){});"
				+ "defineArea({id:'candidate-area'});")
				.getBytes(StandardCharsets.UTF_8));
		host.reload();

		assertSame(stable, host.getContext());
		assertTrue(generation == host.getActiveGeneration());
		assertNull(BossRegistry.get(9400));
		assertNull(RaidRegistry.get("candidate-raid"));
		assertNull(AreaRegistry.get("candidate-area"));
		assertNull(NpcHandlerRegistry.get(9401, "first"));
		assertNull(ObjectHandlerRegistry.get(9402, "first"));
		assertNull(CommandHandlerRegistry.get("candidate-command"));
		assertNull(ItemHandlerRegistry.getItem(9403, "first"));
	}

	private void setContentDir(File contentDir) {
		previousContentDir = System.getProperty("singlescape.contentDir");
		System.setProperty("singlescape.contentDir", contentDir.getAbsolutePath());
	}

	private static File findCompiledContent() {
		File fromWorkspace = new File(System.getProperty("user.dir"), "content/dist");
		if (fromWorkspace.isDirectory()) {
			return fromWorkspace;
		}
		return new File(System.getProperty("user.dir"), "../../content/dist");
	}

	private static void setDefinitions(Class<?> definitionType, Object value)
			throws Exception {
		Field field = definitionType.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, value);
	}

	private static final class AggregateSnapshot {
		private final Context context;
		private final Value boss;
		private final QuestDefinition quest;
		private final Value raid;
		private final Value area;
		private final Value npc;
		private final Value object;
		private final Value command;
		private final Value item;
		private final Value itemPair;
		private final Value itemObject;
		private final Value itemNpc;

		private AggregateSnapshot(ScriptHost host) {
			context = host.getContext();
			boss = BossRegistry.get(9100);
			quest = QuestRegistry.get("stable-quest");
			raid = RaidRegistry.get("stable-raid");
			area = AreaRegistry.get("stable-area");
			npc = NpcHandlerRegistry.get(9101, "first");
			object = ObjectHandlerRegistry.get(9102, "first");
			command = CommandHandlerRegistry.get("stable-command");
			item = ItemHandlerRegistry.getItem(9103, "first");
			itemPair = ItemHandlerRegistry.getItemOnItem(9103, 9104);
			itemObject = ItemHandlerRegistry.getItemOnObject(9103, 9105);
			itemNpc = ItemHandlerRegistry.getItemOnNpc(9103, 9106);
		}

		static AggregateSnapshot capture(ScriptHost host) {
			AggregateSnapshot snapshot = new AggregateSnapshot(host);
			assertNotNull(snapshot.context);
			assertNotNull(snapshot.boss);
			assertNotNull(snapshot.quest);
			assertNotNull(snapshot.raid);
			assertNotNull(snapshot.area);
			assertNotNull(snapshot.npc);
			assertNotNull(snapshot.object);
			assertNotNull(snapshot.command);
			assertNotNull(snapshot.item);
			assertNotNull(snapshot.itemPair);
			assertNotNull(snapshot.itemObject);
			assertNotNull(snapshot.itemNpc);
			return snapshot;
		}

		void assertStillActive(ScriptHost host) {
			assertSame(context, host.getContext());
			assertSame(boss, BossRegistry.get(9100));
			assertSame(quest, QuestRegistry.get("stable-quest"));
			assertSame(raid, RaidRegistry.get("stable-raid"));
			assertSame(area, AreaRegistry.get("stable-area"));
			assertSame(npc, NpcHandlerRegistry.get(9101, "first"));
			assertSame(object, ObjectHandlerRegistry.get(9102, "first"));
			assertSame(command, CommandHandlerRegistry.get("stable-command"));
			assertSame(item, ItemHandlerRegistry.getItem(9103, "first"));
			assertSame(itemPair, ItemHandlerRegistry.getItemOnItem(9103, 9104));
			assertSame(itemObject, ItemHandlerRegistry.getItemOnObject(9103, 9105));
			assertSame(itemNpc, ItemHandlerRegistry.getItemOnNpc(9103, 9106));
		}
	}
}
