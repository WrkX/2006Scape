package com.rs2.script.quest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.rs2.Constants;

/**
 * Stable script skill names mapped to the legacy engine skill indexes.
 */
public enum QuestSkill {
	ATTACK("attack", Constants.ATTACK),
	DEFENCE("defence", Constants.DEFENCE),
	STRENGTH("strength", Constants.STRENGTH),
	HITPOINTS("hitpoints", Constants.HITPOINTS),
	RANGED("ranged", Constants.RANGED),
	PRAYER("prayer", Constants.PRAYER),
	MAGIC("magic", Constants.MAGIC),
	COOKING("cooking", Constants.COOKING),
	WOODCUTTING("woodcutting", Constants.WOODCUTTING),
	FLETCHING("fletching", Constants.FLETCHING),
	FISHING("fishing", Constants.FISHING),
	FIREMAKING("firemaking", Constants.FIREMAKING),
	CRAFTING("crafting", Constants.CRAFTING),
	SMITHING("smithing", Constants.SMITHING),
	MINING("mining", Constants.MINING),
	HERBLORE("herblore", Constants.HERBLORE),
	AGILITY("agility", Constants.AGILITY),
	THIEVING("thieving", Constants.THIEVING),
	SLAYER("slayer", Constants.SLAYER),
	FARMING("farming", Constants.FARMING),
	RUNECRAFT("runecraft", Constants.RUNECRAFTING);

	private static final Map<String, QuestSkill> BY_NAME;

	static {
		Map<String, QuestSkill> values = new LinkedHashMap<>();
		for (QuestSkill skill : values()) {
			values.put(skill.scriptName, skill);
		}
		BY_NAME = Collections.unmodifiableMap(values);
	}

	private final String scriptName;
	private final int index;

	QuestSkill(String scriptName, int index) {
		this.scriptName = scriptName;
		this.index = index;
	}

	public String getScriptName() {
		return scriptName;
	}

	public int getIndex() {
		return index;
	}

	public static QuestSkill fromScriptName(String name) {
		QuestSkill skill = BY_NAME.get(name);
		if (skill == null) {
			throw new QuestDefinitionException("Unknown quest skill: " + name);
		}
		return skill;
	}
}
