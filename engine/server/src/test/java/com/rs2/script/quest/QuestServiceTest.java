package com.rs2.script.quest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import com.rs2.game.players.Player;

public class QuestServiceTest {

	@Test
	public void requirementsAndSequentialCasTransitionsAreEnforced() {
		Player player = new Player(-1) { };
		QuestDefinition definition = definition("cas-quest", 2,
				new QuestDefinition.Requirements(3,
						Collections.<String>emptyList(),
						Arrays.asList(new QuestDefinition.SkillRequirement(
								QuestSkill.MAGIC, 1)),
						Collections.<QuestDefinition.ItemAmount>emptyList()),
				emptyRewards());
		QuestService service = new QuestService(new QuestRewardTransaction());

		QuestResult blocked = service.canStart(player, definition);
		assertFalse(blocked.ok());
		assertFalse(blocked.changed());
		assertEquals("requirements_not_met", blocked.code());
		assertEquals("requirements_not_met",
				service.start(player, definition).code());
		player.questPoints = 3;
		QuestResult eligible = service.canStart(player, definition);
		assertTrue(eligible.ok());
		assertFalse(eligible.changed());
		assertEquals("can_start", eligible.code());
		assertTrue(service.start(player, definition).changed());
		assertEquals(Integer.valueOf(0),
				service.stage(player, definition.getId()));
		QuestResult alreadyStarted = service.canStart(player, definition);
		assertFalse(alreadyStarted.ok());
		assertFalse(alreadyStarted.changed());
		assertEquals("already_started", alreadyStarted.code());
		assertEquals("stage_mismatch",
				service.advance(player, definition, 1).code());
		assertEquals("invalid_stage",
				service.setStage(player, definition, 0, 2).code());
		assertTrue(service.advance(player, definition, 0).changed());
		assertEquals("not_final_stage",
				service.complete(player, definition, 1).code());
		assertTrue(service.advance(player, definition, 1).changed());
	}

	@Test
	public void absentStageIsNullAndCompletedQuestCannotStartAgain() {
		Player player = new Player(-1) { };
		QuestDefinition definition = definition("nullable-stage", 0,
				emptyRequirements(), emptyRewards());
		QuestService service = new QuestService(new QuestRewardTransaction());

		assertNull(service.stage(player, definition.getId()));
		service.start(player, definition);
		service.complete(player, definition, 0);
		QuestResult completed = service.canStart(player, definition);
		assertFalse(completed.ok());
		assertFalse(completed.changed());
		assertEquals("already_completed", completed.code());
	}

	@Test
	public void resultCodesAreAClosedStableWireSet() {
		java.util.Set<String> expected = new java.util.LinkedHashSet<>(
				Arrays.asList("can_start", "started", "already_completed",
						"already_started", "requirements_not_met", "state_failed",
						"not_in_progress", "stage_mismatch", "invalid_stage",
						"advanced", "not_final_stage", "quest_points_overflow",
						"inventory_full", "xp_cap", "reward_failed", "completed"));
		java.util.Set<String> actual = new java.util.LinkedHashSet<>();
		for (QuestResultCode code : QuestResultCode.values()) {
			actual.add(code.wireCode());
		}
		assertEquals(expected, actual);
	}

	@Test
	public void completionRequiresExpectedFinalStageAndIsIdempotent() {
		Player player = new Player(-1) { };
		QuestDefinition definition = definition("complete-quest", 0,
				emptyRequirements(), emptyRewards());
		QuestService service = new QuestService(new QuestRewardTransaction());
		service.start(player, definition);
		assertEquals("stage_mismatch",
				service.complete(player, definition, 1).code());
		QuestResult completed = service.complete(player, definition, 0);
		assertTrue(completed.ok());
		assertTrue(completed.changed());
		QuestResult again = service.complete(player, definition, 0);
		assertTrue(again.ok());
		assertFalse(again.changed());
		assertEquals("already_completed", again.code());
	}

	@Test
	public void objectiveProjectsNullStageTextAndStableCompletionSummary() {
		Player player = new Player(-1) { };
		QuestDefinition definition = definition("objective-quest", 1,
				emptyRequirements(), emptyRewards());
		QuestService service = new QuestService(new QuestRewardTransaction());

		assertNull(service.objective(player, definition));
		service.start(player, definition);
		assertEquals("Stage 0", service.objective(player, definition));
		service.advance(player, definition, 0);
		assertEquals("Stage 1", service.objective(player, definition));
		service.complete(player, definition, 1);
		assertEquals(QuestService.COMPLETED_OBJECTIVE,
				service.objective(player, definition));
	}

	@Test
	public void inProgressQuestWithoutStoredStageHasNoObjective() {
		Player player = new Player(-1) { };
		QuestDefinition definition = definition("stage-less", 0,
				emptyRequirements(), emptyRewards());
		QuestService service = new QuestService(new QuestRewardTransaction());
		QuestStateAccess.setState(player, definition.getId(),
				QuestState.IN_PROGRESS);
		assertNull(service.objective(player, definition));
		assertNull(service.stage(player, definition.getId()));
	}

	static QuestDefinition definition(String id, int finalStage,
			QuestDefinition.Requirements requirements,
			QuestDefinition.Rewards rewards) {
		java.util.List<QuestDefinition.Stage> stages = new java.util.ArrayList<>();
		for (int i = 0; i <= finalStage; i++) {
			stages.add(new QuestDefinition.Stage(i, "Stage " + i));
		}
		return new QuestDefinition(id, id, "Summary", stages,
				requirements, rewards);
	}

	static QuestDefinition.Requirements emptyRequirements() {
		return new QuestDefinition.Requirements(0,
				Collections.<String>emptyList(),
				Collections.<QuestDefinition.SkillRequirement>emptyList(),
				Collections.<QuestDefinition.ItemAmount>emptyList());
	}

	static QuestDefinition.Rewards emptyRewards() {
		return new QuestDefinition.Rewards(0,
				Collections.<QuestDefinition.ItemAmount>emptyList(),
				Collections.<QuestDefinition.ExperienceReward>emptyList());
	}
}
