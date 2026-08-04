package com.rs2.script.quest;

import com.rs2.game.content.quests.QuestAssistant;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerAssistant;
import com.rs2.script.quest.QuestDefinition.ItemAmount;
import com.rs2.script.quest.QuestDefinition.SkillRequirement;
import com.rs2.script.state.ScriptStateSnapshot;

/**
 * Java-owned quest state machine.
 */
public final class QuestService {

	public static final String COMPLETED_OBJECTIVE = "Quest complete.";

	private static final QuestService INSTANCE =
			new QuestService(new QuestRewardTransaction());

	private final QuestRewardTransaction rewards;

	QuestService(QuestRewardTransaction rewards) {
		this.rewards = rewards;
	}

	public static QuestService getInstance() {
		return INSTANCE;
	}

	public QuestState state(Player player, String id) {
		synchronized (player) {
			return QuestStateAccess.state(player, id);
		}
	}

	public Integer stage(Player player, String id) {
		synchronized (player) {
			return QuestStateAccess.stage(player, id);
		}
	}

	/**
	 * Read-only projection of the active quest objective: {@code null} while
	 * not started or when no stage is stored, the current stage text while in
	 * progress, and the stable completion summary once completed.
	 */
	public String objective(Player player, QuestDefinition definition) {
		synchronized (player) {
			QuestState state = QuestStateAccess.state(player, definition.getId());
			if (state == QuestState.COMPLETED) {
				return COMPLETED_OBJECTIVE;
			}
			if (state != QuestState.IN_PROGRESS) {
				return null;
			}
			Integer stage = QuestStateAccess.stage(player, definition.getId());
			if (stage == null) {
				return null;
			}
			for (QuestDefinition.Stage candidate
					: definition.getStages()) {
				if (candidate.getStage() == stage.intValue()) {
					return candidate.getObjective();
				}
			}
			return null;
		}
	}

	public QuestResult canStart(Player player, QuestDefinition definition) {
		synchronized (player) {
			QuestState state = QuestStateAccess.state(player, definition.getId());
			if (state == QuestState.COMPLETED) {
				return QuestResult.unchanged(false,
						QuestResultCode.ALREADY_COMPLETED);
			}
			if (state != QuestState.NOT_STARTED) {
				return QuestResult.unchanged(false,
						QuestResultCode.ALREADY_STARTED);
			}
			if (!requirementsMet(player, definition)) {
				return QuestResult.unchanged(false,
						QuestResultCode.REQUIREMENTS_NOT_MET);
			}
			return QuestResult.unchanged(true, QuestResultCode.CAN_START);
		}
	}

	public QuestResult start(Player player, QuestDefinition definition) {
		synchronized (player) {
			QuestState state = QuestStateAccess.state(player, definition.getId());
			if (state == QuestState.COMPLETED) {
				return QuestResult.unchanged(true,
						QuestResultCode.ALREADY_COMPLETED);
			}
			if (state != QuestState.NOT_STARTED) {
				return QuestResult.unchanged(true,
						QuestResultCode.ALREADY_STARTED);
			}
			if (!requirementsMet(player, definition)) {
				return QuestResult.unchanged(false,
						QuestResultCode.REQUIREMENTS_NOT_MET);
			}
			ScriptStateSnapshot snapshot = player.getScriptState().snapshot();
			try {
				QuestStateAccess.setStage(player, definition.getId(), 0);
				QuestStateAccess.setState(player, definition.getId(),
						QuestState.IN_PROGRESS);
				QuestAssistant.sendStages(player);
				return QuestResult.changed(QuestResultCode.STARTED);
			} catch (RuntimeException failure) {
				player.getScriptState().replace(snapshot);
				return QuestResult.unchanged(false,
						QuestResultCode.STATE_FAILED);
			}
		}
	}

	public QuestResult setStage(Player player, QuestDefinition definition,
			int expectedCurrent, int nextStage) {
		synchronized (player) {
			if (QuestStateAccess.state(player, definition.getId())
					!= QuestState.IN_PROGRESS) {
				return QuestResult.unchanged(false,
						QuestResultCode.NOT_IN_PROGRESS);
			}
			Integer current = QuestStateAccess.stage(player, definition.getId());
			if (current == null || current.intValue() != expectedCurrent) {
				return QuestResult.unchanged(false,
						QuestResultCode.STAGE_MISMATCH);
			}
			if (nextStage != expectedCurrent + 1
					|| nextStage < 0 || nextStage > definition.getFinalStage()) {
				return QuestResult.unchanged(false,
						QuestResultCode.INVALID_STAGE);
			}
			QuestStateAccess.setStage(player, definition.getId(), nextStage);
			QuestAssistant.sendStages(player);
			return QuestResult.changed(QuestResultCode.ADVANCED);
		}
	}

	public QuestResult advance(Player player, QuestDefinition definition,
			int expectedCurrent) {
		return setStage(player, definition, expectedCurrent,
				expectedCurrent + 1);
	}

	public QuestResult complete(Player player, QuestDefinition definition,
			int expectedFinalStage) {
		synchronized (player) {
			QuestState state = QuestStateAccess.state(player, definition.getId());
			if (state == QuestState.COMPLETED) {
				return QuestResult.unchanged(true,
						QuestResultCode.ALREADY_COMPLETED);
			}
			if (state != QuestState.IN_PROGRESS) {
				return QuestResult.unchanged(false,
						QuestResultCode.NOT_IN_PROGRESS);
			}
			Integer current = QuestStateAccess.stage(player, definition.getId());
			if (current == null || current.intValue() != expectedFinalStage) {
				return QuestResult.unchanged(false,
						QuestResultCode.STAGE_MISMATCH);
			}
			if (expectedFinalStage != definition.getFinalStage()) {
				return QuestResult.unchanged(false,
						QuestResultCode.NOT_FINAL_STAGE);
			}
			return rewards.complete(player, definition);
		}
	}

	private boolean requirementsMet(Player player, QuestDefinition definition) {
		if (player.questPoints < definition.getRequirements().getQuestPoints()) {
			return false;
		}
		for (String dependency
				: definition.getRequirements().getCompletedQuests()) {
			if (QuestStateAccess.state(player, dependency)
					!= QuestState.COMPLETED) {
				return false;
			}
		}
		for (SkillRequirement requirement
				: definition.getRequirements().getSkills()) {
			int skill = requirement.getSkill().getIndex();
			if (skill < 0 || skill >= player.playerXP.length
					|| PlayerAssistant.getLevelForXP(player.playerXP[skill])
							< requirement.getLevel()) {
				return false;
			}
		}
		for (ItemAmount item : definition.getRequirements().getItems()) {
			if (player.getItemAssistant().getItemAmount(item.getItemId())
					< item.getAmount()) {
				return false;
			}
		}
		return true;
	}
}
