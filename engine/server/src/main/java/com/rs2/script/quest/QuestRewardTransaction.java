package com.rs2.script.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.rs2.game.content.quests.QuestAssistant;
import com.rs2.game.players.Player;
import com.rs2.script.quest.QuestDefinition.ExperienceReward;
import com.rs2.script.quest.QuestDefinition.ItemAmount;
import com.rs2.script.reward.PlayerRewardTransaction;
import com.rs2.script.reward.RewardDefinition;

/**
 * Quest completion rewards executed through the shared player-local reward
 * transaction.
 *
 * <p>The quest stage/state assignment and its postcondition run inside the
 * shared mutation block; result codes and retry behavior are preserved
 * exactly: {@code completed}, {@code inventory_full}, {@code xp_cap},
 * {@code quest_points_overflow}, and {@code reward_failed} with the complete
 * previous state restored on any refusal or failure.
 */
final class QuestRewardTransaction {

	interface PostMutationHook {
		void run(Player player);
	}

	interface Presentation extends PlayerRewardTransaction.Presentation {
	}

	private static final Presentation LIVE_PRESENTATION = new Presentation() {
		@Override
		public void refreshInventory(Player player) {
			player.getItemAssistant().resetItems(3214);
		}

		@Override
		public void refreshWeight(Player player) {
			player.getPacketSender().writeWeight((int) player.weight);
		}

		@Override
		public void refreshSkill(Player player, int skill) {
			player.getPlayerAssistant().refreshSkill(skill);
		}

		@Override
		public void refreshQuestStages(Player player) {
			QuestAssistant.sendStages(player);
		}
	};

	private final PostMutationHook hook;
	private final Presentation presentation;

	QuestRewardTransaction() {
		this(player -> { }, LIVE_PRESENTATION);
	}

	QuestRewardTransaction(PostMutationHook hook) {
		this(hook, LIVE_PRESENTATION);
	}

	QuestRewardTransaction(PostMutationHook hook, Presentation presentation) {
		this.hook = hook;
		this.presentation = presentation;
	}

	QuestResult complete(Player player, QuestDefinition definition) {
		RewardDefinition reward = toReward(definition);
		PlayerRewardTransaction.Result result =
				PlayerRewardTransaction.apply(player, reward,
						player2 -> {
							QuestStateAccess.setStage(player2,
									definition.getId(),
									definition.getFinalStage());
							QuestStateAccess.setState(player2,
									definition.getId(), QuestState.COMPLETED);
							if (QuestStateAccess.state(player2,
									definition.getId())
											!= QuestState.COMPLETED
									|| QuestStateAccess.stage(player2,
											definition.getId()).intValue()
											!= definition.getFinalStage()) {
								throw new IllegalStateException(
										"Quest reward postcondition failed");
							}
							hook.run(player2);
						},
						presentation);
		switch (result) {
			case OK:
				return QuestResult.changed(QuestResultCode.COMPLETED);
			case INVENTORY_FULL:
				return QuestResult.unchanged(false,
						QuestResultCode.INVENTORY_FULL);
			case XP_CAP:
				return QuestResult.unchanged(false, QuestResultCode.XP_CAP);
			case QUEST_POINTS_OVERFLOW:
				return QuestResult.unchanged(false,
						QuestResultCode.QUEST_POINTS_OVERFLOW);
			default:
				return QuestResult.unchanged(false,
						QuestResultCode.REWARD_FAILED);
		}
	}

	private static RewardDefinition toReward(QuestDefinition definition) {
		List<RewardDefinition.ItemReward> items =
				new ArrayList<RewardDefinition.ItemReward>();
		for (ItemAmount item : definition.getRewards().getItems()) {
			items.add(new RewardDefinition.ItemReward(item.getItemId(),
					item.getAmount()));
		}
		List<RewardDefinition.ExperienceReward> experience =
				new ArrayList<RewardDefinition.ExperienceReward>();
		for (ExperienceReward grant
				: definition.getRewards().getExperience()) {
			experience.add(new RewardDefinition.ExperienceReward(
					grant.getSkill().getIndex(), grant.getAmount()));
		}
		return new RewardDefinition(definition.getId(), "quest", 1, items,
				experience, definition.getRewards().getQuestPoints(),
				Collections.<RewardDefinition.StateMutation>emptyList());
	}

}
