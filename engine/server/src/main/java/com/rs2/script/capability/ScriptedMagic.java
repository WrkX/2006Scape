package com.rs2.script.capability;

import org.graalvm.polyglot.HostAccess;

import com.rs2.Constants;
import com.rs2.game.content.combat.magic.MagicData;
import com.rs2.game.content.combat.magic.MagicRequirements;
import com.rs2.game.players.Player;
import com.rs2.script.world.ScriptEncounterService;

/**
 * Spell rune and level checks over {@link MagicData#MAGIC_SPELLS}, keyed by
 * client spell button ids ({@code MAGIC_SPELLS[i][0]}).
 */
public final class ScriptedMagic {

	private final Player player;
	private final long generation;
	private final long facadeEpoch;

	public ScriptedMagic(Player player, long generation, long facadeEpoch) {
		this.player = player;
		this.generation = generation;
		this.facadeEpoch = facadeEpoch;
	}

	@HostAccess.Export
	public int findIndex(double spellButtonIdValue) {
		Integer spellButtonId = spellButtonId(spellButtonIdValue);
		if (spellButtonId == null) {
			return -1;
		}
		for (int i = 0; i < MagicData.MAGIC_SPELLS.length; i++) {
			if (MagicData.MAGIC_SPELLS[i][0] == spellButtonId.intValue()) {
				return i;
			}
		}
		return -1;
	}

	@HostAccess.Export
	public boolean hasRunes(double spellButtonIdValue) {
		Integer index = spellIndex(spellButtonIdValue);
		return index != null && live() && hasRunesForSpell(index.intValue());
	}

	@HostAccess.Export
	public boolean consumeRunes(double spellButtonIdValue) {
		Integer index = spellIndex(spellButtonIdValue);
		if (index == null || !canMutate()) {
			return false;
		}
		if (!hasRunesForSpell(index.intValue())) {
			return false;
		}
		int[] spellData = MagicData.MAGIC_SPELLS[index.intValue()];
		consumeRune(spellData[8], spellData[9]);
		consumeRune(spellData[10], spellData[11]);
		consumeRune(spellData[12], spellData[13]);
		consumeRune(spellData[14], spellData[15]);
		return true;
	}

	@HostAccess.Export
	public int requiredLevel(double spellButtonIdValue) {
		Integer index = spellIndex(spellButtonIdValue);
		if (index == null) {
			return -1;
		}
		return MagicData.MAGIC_SPELLS[index.intValue()][1];
	}

	@HostAccess.Export
	public boolean hasLevel(double spellButtonIdValue) {
		Integer index = spellIndex(spellButtonIdValue);
		if (index == null || !live()) {
			return false;
		}
		return player.playerLevel[Constants.MAGIC]
				>= MagicData.MAGIC_SPELLS[index.intValue()][1];
	}

	private boolean hasRunesForSpell(int spellIndex) {
		int[] spellData = MagicData.MAGIC_SPELLS[spellIndex];
		return hasRune(spellData[8], spellData[9])
				&& hasRune(spellData[10], spellData[11])
				&& hasRune(spellData[12], spellData[13])
				&& hasRune(spellData[14], spellData[15]);
	}

	private boolean hasRune(int runeId, int amount) {
		if (runeId <= 0) {
			return true;
		}
		if (MagicRequirements.wearingStaff(player, runeId)) {
			return true;
		}
		return player.getItemAssistant().playerHasItem(runeId, amount);
	}

	private void consumeRune(int runeId, int amount) {
		if (runeId <= 0 || MagicRequirements.wearingStaff(player, runeId)) {
			return;
		}
		player.getItemAssistant().deleteItem(runeId,
				player.getItemAssistant().getItemSlot(runeId), amount);
	}

	private Integer spellIndex(double spellButtonIdValue) {
		Integer spellButtonId = spellButtonId(spellButtonIdValue);
		if (spellButtonId == null) {
			return null;
		}
		int index = findIndex(spellButtonIdValue);
		return index < 0 ? null : Integer.valueOf(index);
	}

	private static Integer spellButtonId(double value) {
		return !Double.isFinite(value) || value != Math.rint(value)
				|| value < 1 || value > 65535 ? null
						: Integer.valueOf((int) value);
	}

	private boolean live() {
		return ScriptEncounterService.getInstance().canUseFacade(player,
				generation, facadeEpoch, false);
	}

	private boolean canMutate() {
		return ScriptEncounterService.getInstance().canMutate(player,
				generation, facadeEpoch);
	}
}
