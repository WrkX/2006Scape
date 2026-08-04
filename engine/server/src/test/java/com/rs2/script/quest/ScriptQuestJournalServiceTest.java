package com.rs2.script.quest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Test;

import com.rs2.Constants;
import com.rs2.game.content.quests.QuestAssistant;
import com.rs2.game.content.quests.QuestAssistant.Quests;
import com.rs2.game.players.Client;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerAssistant;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.ScriptHost;
import com.rs2.script.ScriptRuntimeTestFixture;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.registries.QuestRegistry;
import com.rs2.script.registries.RegistryStore;
import com.rs2.util.Stream;
import org.apollo.util.security.IsaacRandom;

/**
 * Generation-owned scripted-quest journal: deterministic unused-row mapping,
 * generic detail interface 8134, transition/login/reload refresh, and
 * candidate rejection when the usable row pool is exceeded.
 */
public class ScriptQuestJournalServiceTest {

	private static final int PLAYER_SLOT = 123;

	private String previousContentDir;
	private Player previousPlayer;

	@After
	public void tearDown() {
		ScriptRuntimeTestFixture.reset();
		PlayerHandler.players[PLAYER_SLOT] = previousPlayer;
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
	}

	@Test
	public void sortedQuestIdsMapToSortedUnusedRowsAndOpenTheGenericJournal()
			throws Exception {
		publishAll(definition("alpha-quest", "Alpha Quest", "alpha-summary",
				Collections.singletonList(
						new QuestDefinition.Stage(0, "alpha-stage-0")),
				emptyRequirements(), emptyRewards()),
				definition("beta-quest", "Beta Quest", "beta-summary",
						Collections.singletonList(
								new QuestDefinition.Stage(0, "beta-stage-0")),
						emptyRequirements(), emptyRewards()),
				definition("gamma-quest", "Gamma Quest", "gamma-summary",
						Collections.singletonList(
								new QuestDefinition.Stage(0, "gamma-stage-0")),
						emptyRequirements(), emptyRewards()));
		Player player = installedPlayer();

		QuestAssistant.sendStages(player);
		List<int[]> rows = usableRows();
		assertEquals("Alpha Quest", interfaceText(player, rows.get(0)[1]));
		assertEquals("Beta Quest", interfaceText(player, rows.get(1)[1]));
		assertEquals("Gamma Quest", interfaceText(player, rows.get(2)[1]));
		assertEquals("", interfaceText(player, rows.get(3)[1]));

		ScriptQuestJournalService journal = ScriptQuestJournalService
				.getInstance();
		assertTrue(journal.handleButton(player, rows.get(0)[0]));
		assertEquals("@dre@Alpha Quest", interfaceText(player, 8144));
		assertEquals("alpha-summary", interfaceText(player, 8147));
		assertEquals("@dre@Requirements:", interfaceText(player, 8149));
		assertEquals("None.", interfaceText(player, 8150));
		assertEquals("Not started", interfaceText(player, 8152));
		assertEquals("", interfaceText(player, 8154));
		assertEquals(8134, player.lastMainFrameInterface);

		assertTrue(journal.handleButton(player, rows.get(1)[0]));
		assertEquals("@dre@Beta Quest", interfaceText(player, 8144));
		assertTrue(journal.handleButton(player, rows.get(2)[0]));
		assertEquals("@dre@Gamma Quest", interfaceText(player, 8144));

		assertFalse(journal.handleButton(player, rows.get(3)[0]));
		assertFalse(journal.handleButton(player, 28164));
		assertFalse(journal.handleButton(player, 28165));
	}

	@Test
	public void rowColorsAndObjectiveFollowQuestTransitions() throws Exception {
		publish(definition("alpha-quest", "Alpha Quest", "alpha-summary",
				Arrays.asList(new QuestDefinition.Stage(0, "alpha-stage-0"),
						new QuestDefinition.Stage(1, "alpha-stage-1")),
				emptyRequirements(), emptyRewards()));
		Player player = installedPlayer();
		List<int[]> rows = usableRows();
		ScriptedQuest quest = new ScriptedPlayer(player)
				.quest("alpha-quest");

		QuestAssistant.sendStages(player);
		assertEquals("Alpha Quest", interfaceText(player, rows.get(0)[1]));
		assertTrue(quest.start().changed());
		QuestAssistant.sendStages(player);
		assertEquals("@yel@Alpha Quest", interfaceText(player, rows.get(0)[1]));
		assertTrue(quest.advance(0).changed());
		QuestAssistant.sendStages(player);
		assertEquals("@yel@Alpha Quest", interfaceText(player, rows.get(0)[1]));
		assertTrue(quest.complete(1).changed());
		QuestAssistant.sendStages(player);
		assertEquals("@gre@Alpha Quest", interfaceText(player, rows.get(0)[1]));

		ScriptQuestJournalService journal = ScriptQuestJournalService
				.getInstance();
		assertTrue(journal.handleButton(player, rows.get(0)[0]));
		assertEquals("@gre@Completed", interfaceText(player, 8152));
		assertEquals("@gre@Quest complete.", interfaceText(player, 8154));
	}

	@Test
	public void genericJournalShowsSummaryRequirementsStateAndObjective()
			throws Exception {
		publish(definition("alpha-quest", "Alpha Quest",
				"The alpha summary.",
				Arrays.asList(new QuestDefinition.Stage(0, "First step."),
						new QuestDefinition.Stage(1, "Second step.")),
				new QuestDefinition.Requirements(3,
						Collections.<String>emptyList(),
						Arrays.asList(new QuestDefinition.SkillRequirement(
								QuestSkill.MAGIC, 5)),
						Arrays.asList(new QuestDefinition.ItemAmount(995, 1000))),
				emptyRewards()));
		Player player = installedPlayer();
		ScriptQuestJournalService journal = ScriptQuestJournalService
				.getInstance();
		List<int[]> rows = usableRows();
		ScriptedQuest quest = new ScriptedPlayer(player)
				.quest("alpha-quest");

		assertTrue(journal.handleButton(player, rows.get(0)[0]));
		assertEquals("Not started", interfaceText(player, 8152));

		player.questPoints = 3;
		player.playerXP[Constants.MAGIC] = PlayerAssistant.getXPForLevel(5);
		player.playerItems[0] = 996;
		player.playerItemsN[0] = 1000;
		assertTrue(quest.start().changed());
		assertTrue(journal.handleButton(player, rows.get(0)[0]));
		assertEquals("@dre@Alpha Quest", interfaceText(player, 8144));
		assertEquals("The alpha summary.", interfaceText(player, 8147));
		assertEquals("3 quest points, Level 5 Magic, Item 995 x1000",
				interfaceText(player, 8150));
		assertEquals("@yel@In progress", interfaceText(player, 8152));
		assertEquals("Objective: First step.", interfaceText(player, 8154));

		assertTrue(quest.advance(0).changed());
		assertTrue(journal.handleButton(player, rows.get(0)[0]));
		assertEquals("Objective: Second step.", interfaceText(player, 8154));
	}

	@Test
	public void longRequirementListsAreBoundedDeterministically()
			throws Exception {
		List<QuestDefinition.ItemAmount> items = new ArrayList<>();
		for (int i = 0; i < 64; i++) {
			items.add(new QuestDefinition.ItemAmount(995 + i, 1));
		}
		publish(definition("alpha-quest", "Alpha Quest", "summary",
				Collections.singletonList(new QuestDefinition.Stage(0, "Done.")),
				new QuestDefinition.Requirements(0,
						Collections.<String>emptyList(),
						Collections.<QuestDefinition.SkillRequirement>emptyList(),
						items),
				emptyRewards()));
		Player player = installedPlayer();
		ScriptQuestJournalService journal = ScriptQuestJournalService
				.getInstance();
		List<int[]> rows = usableRows();

		assertTrue(journal.handleButton(player, rows.get(0)[0]));
		String requirements = interfaceText(player, 8150);
		assertTrue("requirements line must be bounded",
				requirements.getBytes(StandardCharsets.UTF_8).length <= 240);
		assertTrue(requirements.endsWith(" ..."));
		assertTrue(requirements.startsWith("Item 995 x1, Item 996 x1"));
		String second = interfaceText(player, 8150);
		assertEquals(requirements, second);
	}

	@Test
	public void legacyQuestButtonsAndDetailsRemainUnchanged() throws Exception {
		publish("alpha-quest", "Alpha Quest", "alpha-summary",
				"alpha-stage-0");
		Player player = installedPlayer();
		List<int[]> rows = usableRows();

		QuestAssistant.questButtons(player, 28164);
		assertEquals("Black Knights' Fortress",
				interfaceText(player, 8144));
		assertEquals(8134, player.lastMainFrameInterface);
		QuestAssistant.questButtons(player, rows.get(0)[0]);
		assertEquals("@dre@Alpha Quest", interfaceText(player, 8144));
	}

	@Test
	public void validateCandidateRejectsMoreScriptedQuestsThanUsableRows() {
		int usable = usableRows().size();
		assertTrue(usable >= 80);
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			for (int i = 0; i < usable; i++) {
				QuestRegistry.put("q" + String.format("%03d", i),
						definition("q" + String.format("%03d", i),
								"Quest " + i, "summary",
								Collections.singletonList(
										new QuestDefinition.Stage(0, "Done.")),
								emptyRequirements(), emptyRewards()));
			}
			ScriptQuestJournalService.getInstance().validateCandidate(candidate);
			QuestRegistry.put("q" + String.format("%03d", usable),
					definition("q" + String.format("%03d", usable),
							"Quest " + usable, "summary",
							Collections.singletonList(
									new QuestDefinition.Stage(0, "Done.")),
							emptyRequirements(), emptyRewards()));
			try {
				ScriptQuestJournalService.getInstance()
						.validateCandidate(candidate);
				fail("candidate exceeding the usable row pool must be rejected");
			} catch (QuestDefinitionException expected) {
				assertTrue(expected.getMessage().contains("usable rows"));
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	@Test
	public void rejectedCandidateAndStaleGenerationLeaveMappingAndUiUntouched()
			throws Exception {
		publish("alpha-quest", "Alpha Quest", "alpha-summary",
				"alpha-stage-0");
		Player player = installedPlayer();
		List<int[]> rows = usableRows();
		ScriptQuestJournalService journal = ScriptQuestJournalService
				.getInstance();

		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			QuestRegistry.put("beta-quest", definition("beta-quest",
					"Beta Quest", "beta-summary",
					Collections.singletonList(
							new QuestDefinition.Stage(0, "Done.")),
					emptyRequirements(), emptyRewards()));
			journal.validateCandidate(candidate);
		} finally {
			RegistryStore.rollback(candidate);
		}
		QuestAssistant.sendStages(player);
		assertEquals("Alpha Quest", interfaceText(player, rows.get(0)[1]));
		assertEquals("", interfaceText(player, rows.get(1)[1]));
		assertFalse(journal.handleButton(player, rows.get(1)[0]));
		assertTrue(journal.handleButton(player, rows.get(0)[0]));

		journal.closeGeneration(-1L);
		assertTrue(journal.handleButton(player, rows.get(0)[0]));
		journal.closeGeneration(ScriptHost.getInstance().getActiveGeneration());
		assertFalse(journal.handleButton(player, rows.get(0)[0]));
	}

	@Test
	public void acceptedReloadRemapsRowsAndRejectedReloadKeepsPriorRows()
			throws Exception {
		Path root = Files.createTempDirectory("journal-reload");
		Path loader = root.resolve("loader.js");
		File contentFile = root.toFile();
		previousContentDir = System.getProperty("singlescape.contentDir");
		System.setProperty("singlescape.contentDir",
				contentFile.getAbsolutePath());
		Files.write(loader, quest("alpha-quest", "Alpha Quest", "alpha-stage-0")
				.getBytes(StandardCharsets.UTF_8));
		ScriptHost.getInstance().reload();
		Player player = installedPlayer();
		List<int[]> rows = usableRows();
		QuestAssistant.sendStages(player);
		assertEquals("Alpha Quest", interfaceText(player, rows.get(0)[1]));
		assertEquals("", interfaceText(player, rows.get(1)[1]));

		Files.write(loader, (quest("alpha-quest", "Alpha Quest",
				"alpha-stage-0") + quest("beta-quest", "Beta Quest",
						"beta-stage-0"))
				.getBytes(StandardCharsets.UTF_8));
		ScriptHost.getInstance().reload();
		QuestAssistant.sendStages(player);
		assertEquals("Alpha Quest", interfaceText(player, rows.get(0)[1]));
		assertEquals("Beta Quest", interfaceText(player, rows.get(1)[1]));

		Files.write(loader, "throw new Error('rejected');"
				.getBytes(StandardCharsets.UTF_8));
		ScriptHost.getInstance().reload();
		QuestAssistant.sendStages(player);
		assertEquals("Alpha Quest", interfaceText(player, rows.get(0)[1]));
		assertEquals("Beta Quest", interfaceText(player, rows.get(1)[1]));
		assertTrue(ScriptQuestJournalService.getInstance()
				.handleButton(player, rows.get(1)[0]));
	}

	private static String quest(String id, String name, String objective) {
		return "defineQuest({id:'" + id + "',name:'" + name
				+ "',summary:'summary',stages:[{stage:0,objective:'"
				+ objective + "'}]});";
	}

	private void publish(String id, String name, String summary,
			String objective) throws Exception {
		publish(definition(id, name, summary,
				Collections.singletonList(
						new QuestDefinition.Stage(0, objective)),
				emptyRequirements(), emptyRewards()));
	}

	private void publish(QuestDefinition definition) throws Exception {
		publishAll(definition);
	}

	private void publishAll(QuestDefinition... definitions) throws Exception {
		Context context = Context.create("js");
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			for (QuestDefinition definition : definitions) {
				QuestRegistry.put(definition.getId(), definition);
			}
			QuestRegistry.validateCandidate(candidate);
			ScriptRuntimeTestFixture.publishCandidate(context, candidate);
		} catch (RuntimeException error) {
			RegistryStore.rollback(candidate);
			context.close();
			throw error;
		}
	}

	private Player installedPlayer() {
		previousPlayer = PlayerHandler.players[PLAYER_SLOT];
		TestClient player = new TestClient();
		player.outStream = new Stream(new byte[Constants.BUFFER_SIZE]);
		player.outStream.packetEncryption = new IsaacRandom(new int[4]);
		player.initialized = true;
		player.isActive = true;
		player.disconnected = false;
		player.isDead = false;
		player.respawnTimer = 0;
		PlayerHandler.players[PLAYER_SLOT] = player;
		return player;
	}

	private static final class TestClient extends Client {
		private TestClient() {
			super(null, PLAYER_SLOT);
		}

		@Override
		public void flushOutStream() {
			if (outStream != null) {
				outStream.currentOffset = 0;
			}
		}
	}

	private static String interfaceText(Player player, int component) {
		return player.interfaceText(component);
	}

	/**
	 * The bounded pool of usable legacy rows, mirroring the service filter:
	 * rows without a legacy quest status whose buttons are not handled by
	 * {@code QuestAssistant.questButtons}, sorted by button id.
	 */
	private static List<int[]> usableRows() {
		List<int[]> rows = new ArrayList<>();
		for (Quests quest : Quests.values()) {
			if (!quest.questStatus()
					&& !QuestAssistant.isLegacyQuestButton(quest.getButton())) {
				rows.add(new int[] { quest.getButton(), quest.getStringId() });
			}
		}
		Collections.sort(rows, new Comparator<int[]>() {
			@Override
			public int compare(int[] first, int[] second) {
				return Integer.compare(first[0], second[0]);
			}
		});
		return rows;
	}

	private static QuestDefinition definition(String id, String name,
			String summary, List<QuestDefinition.Stage> stages,
			QuestDefinition.Requirements requirements,
			QuestDefinition.Rewards rewards) {
		return new QuestDefinition(id, name, summary, stages,
				requirements, rewards);
	}

	private static QuestDefinition.Requirements emptyRequirements() {
		return new QuestDefinition.Requirements(0,
				Collections.<String>emptyList(),
				Collections.<QuestDefinition.SkillRequirement>emptyList(),
				Collections.<QuestDefinition.ItemAmount>emptyList());
	}

	private static QuestDefinition.Rewards emptyRewards() {
		return new QuestDefinition.Rewards(0,
				Collections.<QuestDefinition.ItemAmount>emptyList(),
				Collections.<QuestDefinition.ExperienceReward>emptyList());
	}
}
