package com.rs2.script.mob;

import com.rs2.game.content.combat.AttackType;

/**
 * Declarative combat style for a world mob. Maps onto the engine
 * {@link AttackType} used by hit application.
 */
public enum MobCombatStyle {

	MELEE("melee", AttackType.MELEE),
	RANGED("ranged", AttackType.RANGE),
	MAGIC("magic", AttackType.MAGIC);

	private final String scriptName;
	private final AttackType attackType;

	MobCombatStyle(String scriptName, AttackType attackType) {
		this.scriptName = scriptName;
		this.attackType = attackType;
	}

	public String scriptName() {
		return scriptName;
	}

	public AttackType attackType() {
		return attackType;
	}

	public static MobCombatStyle fromScriptName(String name) {
		if (name == null) {
			return null;
		}
		for (MobCombatStyle style : values()) {
			if (style.scriptName.equals(name)) {
				return style;
			}
		}
		return null;
	}
}
