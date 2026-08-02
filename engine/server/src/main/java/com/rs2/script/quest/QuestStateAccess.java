package com.rs2.script.quest;

import com.rs2.game.players.Player;
import com.rs2.script.state.ScriptStateLimits;
import com.rs2.script.state.ScriptStateValue;

final class QuestStateAccess {

	static QuestState state(Player player, String id) {
		ScriptStateValue value = player.getScriptState().getInternal(
				ScriptStateLimits.QUEST_NAMESPACE, stateKey(id));
		if (value == null) {
			return QuestState.NOT_STARTED;
		}
		String state = value.asString();
		if (QuestState.IN_PROGRESS.getScriptName().equals(state)) {
			return QuestState.IN_PROGRESS;
		}
		if (QuestState.COMPLETED.getScriptName().equals(state)) {
			return QuestState.COMPLETED;
		}
		throw new IllegalStateException("Invalid persisted quest state for " + id);
	}

	static Integer stage(Player player, String id) {
		ScriptStateValue value = player.getScriptState().getInternal(
				ScriptStateLimits.QUEST_NAMESPACE, stageKey(id));
		if (value == null) {
			return null;
		}
		double stage = value.asNumber();
		if (stage < 0 || stage > Integer.MAX_VALUE || Math.rint(stage) != stage) {
			throw new IllegalStateException("Invalid persisted quest stage for " + id);
		}
		return (int) stage;
	}

	static void setState(Player player, String id, QuestState state) {
		player.getScriptState().setInternal(ScriptStateLimits.QUEST_NAMESPACE,
				stateKey(id), ScriptStateValue.of(state.getScriptName()));
	}

	static void setStage(Player player, String id, int stage) {
		player.getScriptState().setInternal(ScriptStateLimits.QUEST_NAMESPACE,
				stageKey(id), ScriptStateValue.of((double) stage));
	}

	private static String stateKey(String id) {
		return "state." + id;
	}

	private static String stageKey(String id) {
		return "stage." + id;
	}

	private QuestStateAccess() {
	}
}
