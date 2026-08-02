package com.rs2.script.quest;

public enum QuestState {
	NOT_STARTED("not_started"),
	IN_PROGRESS("in_progress"),
	COMPLETED("completed");

	private final String scriptName;

	QuestState(String scriptName) {
		this.scriptName = scriptName;
	}

	public String getScriptName() {
		return scriptName;
	}
}
