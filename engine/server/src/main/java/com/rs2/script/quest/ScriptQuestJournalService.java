package com.rs2.script.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apollo.cache.def.ItemDefinition;

import com.rs2.game.content.quests.QuestAssistant;
import com.rs2.game.content.quests.QuestAssistant.Quests;
import com.rs2.game.players.Player;
import com.rs2.script.registries.QuestRegistry;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.quest.QuestDefinition.ItemAmount;
import com.rs2.script.quest.QuestDefinition.Requirements;
import com.rs2.script.quest.QuestDefinition.SkillRequirement;

/**
 * Projects scripted quests into the bounded pool of currently unimplemented
 * legacy quest-tab rows and renders the generic detail interface 8134.
 *
 * <p>The mapping is deterministic and generation-owned: sorted scripted
 * quest ids are paired with the pool rows sorted by button id, the candidate
 * is rejected when it exceeds the usable rows, and only an accepted reload
 * recomputes the mapping. Exact scripted {@code onButton} authority runs
 * before this service (unmapped buttons keep the legacy path unchanged).
 */
public final class ScriptQuestJournalService {

	/** The generic legacy quest-detail interface. */
	public static final int GENERIC_QUEST_INTERFACE = 8134;

	private static final int TITLE_COMPONENT = 8144;
	private static final int SUMMARY_COMPONENT = 8147;
	private static final int REQUIREMENTS_HEADER_COMPONENT = 8149;
	private static final int REQUIREMENTS_TEXT_COMPONENT = 8150;
	private static final int STATE_COMPONENT = 8152;
	private static final int OBJECTIVE_COMPONENT = 8154;
	private static final int CLEAR_FROM = 8144;
	private static final int CLEAR_TO = 8195;
	private static final int CLEAR_SECOND_FROM = 12174;
	private static final int CLEAR_SECOND_TO = 12223;
	private static final int CLEAR_THIRD_FROM = 14945;
	private static final int CLEAR_THIRD_TO = 15044;
	private static final int MAX_REQUIREMENTS_LINE_BYTES = 240;

	private static final ScriptQuestJournalService INSTANCE =
			new ScriptQuestJournalService();

	private final Map<Integer, String> questIdByButton = new HashMap<>();
	private final Map<String, JournalSlot> slotByQuest = new HashMap<>();
	private long activeGeneration;

	public static ScriptQuestJournalService getInstance() {
		return INSTANCE;
	}

	private ScriptQuestJournalService() {
	}

	/**
	 * Rejects a candidate whose scripted quests exceed the usable legacy
	 * quest-tab rows. Quest dependencies are already validated by
	 * {@link QuestRegistry#validateCandidate}.
	 */
	public void validateCandidate(RegistryStore.State candidate) {
		Map<String, QuestDefinition> quests = QuestRegistry.all(candidate);
		List<JournalSlot> pool = usableRows();
		if (quests.size() > pool.size()) {
			throw new QuestDefinitionException(
					"Candidate defines " + quests.size()
							+ " scripted quests but the legacy quest tab has only "
							+ pool.size() + " usable rows");
		}
	}

	/**
	 * Recomputes the deterministic mapping for a newly published generation.
	 * Only an accepted reload reaches this seam, so a rejected candidate
	 * leaves the previous mapping and UI state untouched.
	 */
	public synchronized void onGenerationPublished(long generation) {
		Map<String, QuestDefinition> quests = QuestRegistry.all();
		List<JournalSlot> pool = usableRows();
		slotByQuest.clear();
		questIdByButton.clear();
		int index = 0;
		for (String questId : new java.util.TreeSet<>(quests.keySet())) {
			JournalSlot slot = pool.get(index++);
			slotByQuest.put(questId, slot);
			questIdByButton.put(Integer.valueOf(slot.button), questId);
		}
		activeGeneration = generation;
	}

	public synchronized void closeGeneration(long generation) {
		if (generation == activeGeneration) {
			slotByQuest.clear();
			questIdByButton.clear();
		}
	}

	/** Engine-visible mapped scripted-quest row count for diagnostics. */
	public synchronized int mappedRowCount() {
		return slotByQuest.size();
	}

	public synchronized void resetForTesting() {
		slotByQuest.clear();
		questIdByButton.clear();
		activeGeneration = 0L;
	}

	/**
	 * Renders every mapped scripted quest row with the legacy color scheme
	 * (plain name, {@code @yel@} in progress, {@code @gre@} completed). Called
	 * from {@link QuestAssistant#sendStages} on login, dialogue, quest
	 * transitions, and successful reload.
	 */
	public void refreshTab(Player player) {
		List<MappedRow> rows;
		synchronized (this) {
			rows = new ArrayList<MappedRow>();
			for (Map.Entry<String, JournalSlot> entry
					: slotByQuest.entrySet()) {
				rows.add(new MappedRow(entry.getKey(), entry.getValue()));
			}
		}
		Collections.sort(rows, new Comparator<MappedRow>() {
			@Override
			public int compare(MappedRow first, MappedRow second) {
				return first.questId.compareTo(second.questId);
			}
		});
		for (MappedRow row : rows) {
			QuestDefinition definition = QuestRegistry.get(row.questId);
			if (definition == null) {
				continue;
			}
			QuestState state = QuestService.getInstance().state(player,
					row.questId);
			String prefix = state == QuestState.COMPLETED ? "@gre@"
					: state == QuestState.IN_PROGRESS ? "@yel@" : "";
			player.getPacketSender().sendString(prefix + definition.getName(),
					row.slot.stringId);
		}
	}

	/**
	 * Opens the generic detail interface for a mapped scripted quest button
	 * and returns {@code true}; returns {@code false} so the caller keeps the
	 * legacy {@code QuestAssistant.questButtons} path for unmapped buttons.
	 */
	public boolean handleButton(Player player, int buttonId) {
		String questId;
		synchronized (this) {
			questId = questIdByButton.get(Integer.valueOf(buttonId));
		}
		if (questId == null) {
			return false;
		}
		QuestDefinition definition = QuestRegistry.get(questId);
		if (definition == null) {
			return false;
		}
		showGenericJournal(player, definition);
		return true;
	}

	private void showGenericJournal(Player player,
			QuestDefinition definition) {
		for (int component = CLEAR_FROM; component <= CLEAR_TO; component++) {
			player.getPacketSender().sendString("", component);
		}
		for (int component = CLEAR_SECOND_FROM;
				component <= CLEAR_SECOND_TO; component++) {
			player.getPacketSender().sendString("", component);
		}
		for (int component = CLEAR_THIRD_FROM;
				component <= CLEAR_THIRD_TO; component++) {
			player.getPacketSender().sendString("", component);
		}
		player.getPacketSender()
				.sendString("@dre@" + definition.getName(), TITLE_COMPONENT);
		player.getPacketSender().sendString("", TITLE_COMPONENT + 1);
		player.getPacketSender().sendString(definition.getSummary(),
				SUMMARY_COMPONENT);
		player.getPacketSender().sendString("", SUMMARY_COMPONENT + 1);
		player.getPacketSender().sendString("@dre@Requirements:",
				REQUIREMENTS_HEADER_COMPONENT);
		player.getPacketSender().sendString(
				requirementsText(definition), REQUIREMENTS_TEXT_COMPONENT);
		player.getPacketSender().sendString("", REQUIREMENTS_TEXT_COMPONENT + 1);
		player.getPacketSender().sendString(
				stateText(player, definition), STATE_COMPONENT);
		player.getPacketSender().sendString("", STATE_COMPONENT + 1);
		String objective = QuestService.getInstance().objective(player,
				definition);
		if (objective != null) {
			String line = QuestService.COMPLETED_OBJECTIVE.equals(objective)
					? "@gre@" + objective
					: "Objective: " + objective;
			player.getPacketSender().sendString(line, OBJECTIVE_COMPONENT);
		}
		player.getPacketSender().showInterface(GENERIC_QUEST_INTERFACE);
	}

	private static String stateText(Player player,
			QuestDefinition definition) {
		QuestState state = QuestService.getInstance().state(player,
				definition.getId());
		if (state == QuestState.COMPLETED) {
			return "@gre@Completed";
		}
		if (state == QuestState.IN_PROGRESS) {
			return "@yel@In progress";
		}
		return "Not started";
	}

	private static String requirementsText(QuestDefinition definition) {
		Requirements requirements = definition.getRequirements();
		List<String> parts = new ArrayList<>();
		if (requirements.getQuestPoints() > 0) {
			parts.add(requirements.getQuestPoints() + " quest points");
		}
		for (String questId : requirements.getCompletedQuests()) {
			QuestDefinition dependency = QuestRegistry.get(questId);
			parts.add("Completed " + (dependency == null ? questId
					: dependency.getName()));
		}
		for (SkillRequirement skill : requirements.getSkills()) {
			parts.add("Level " + skill.getLevel() + " "
					+ capitalize(skill.getSkill().getScriptName()));
		}
		for (ItemAmount item : requirements.getItems()) {
			parts.add(itemName(item.getItemId()) + " x" + item.getAmount());
		}
		if (parts.isEmpty()) {
			return "None.";
		}
		return boundedJoin(parts, MAX_REQUIREMENTS_LINE_BYTES);
	}

	private static String boundedJoin(List<String> parts, int maxBytes) {
		StringBuilder joined = new StringBuilder();
		int bytes = 0;
		for (String part : parts) {
			int partBytes = part.getBytes(
					java.nio.charset.StandardCharsets.UTF_8).length;
			int separatorBytes = bytes == 0 ? 0 : 2;
			if (bytes > 0 && bytes + separatorBytes + partBytes + 4
					> maxBytes) {
				joined.append(" ...");
				break;
			}
			if (bytes > 0) {
				joined.append(", ");
			}
			joined.append(part);
			bytes += separatorBytes + partBytes;
		}
		return joined.toString();
	}

	private static String capitalize(String value) {
		if (value.isEmpty()) {
			return value;
		}
		return Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

	private static String itemName(int itemId) {
		ItemDefinition definition = ItemDefinition.lookup(itemId);
		String name = definition == null ? null : definition.getName();
		if (name == null || name.isEmpty()) {
			return "Item " + itemId;
		}
		return name;
	}

	/**
	 * The bounded pool of currently unimplemented legacy quest-tab rows:
	 * enum rows without a legacy quest status whose buttons are not handled
	 * by the legacy {@code QuestAssistant.questButtons} path, sorted by
	 * button id.
	 */
	private static List<JournalSlot> usableRows() {
		List<JournalSlot> rows = new ArrayList<>();
		for (Quests quest : Quests.values()) {
			if (!quest.questStatus()
					&& !QuestAssistant.isLegacyQuestButton(quest.getButton())) {
				rows.add(new JournalSlot(quest.getButton(),
						quest.getStringId()));
			}
		}
		Collections.sort(rows, new Comparator<JournalSlot>() {
			@Override
			public int compare(JournalSlot first, JournalSlot second) {
				return Integer.compare(first.button, second.button);
			}
		});
		return rows;
	}

	private static final class JournalSlot {
		private final int button;
		private final int stringId;

		private JournalSlot(int button, int stringId) {
			this.button = button;
			this.stringId = stringId;
		}
	}

	private static final class MappedRow {
		private final String questId;
		private final JournalSlot slot;

		private MappedRow(String questId, JournalSlot slot) {
			this.questId = questId;
			this.slot = slot;
		}
	}
}
