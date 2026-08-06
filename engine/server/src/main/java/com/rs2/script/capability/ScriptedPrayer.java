package com.rs2.script.capability;

import org.graalvm.polyglot.HostAccess;

import com.rs2.game.content.combat.prayer.ActivatePrayers;
import com.rs2.game.content.combat.prayer.PrayerData;
import com.rs2.game.content.combat.prayer.PrayerDrain;
import com.rs2.game.players.Player;
import com.rs2.script.world.ScriptEncounterService;

/**
 * Prayer activate/deactivate view over the legacy prayer package.
 *
 * <p>Prayer indexes match the client prayer book (0..25). Activation reuses
 * {@link ActivatePrayers} so mutual exclusivity and glow configs stay host-owned.
 */
public final class ScriptedPrayer {

	private static final int MAX_PRAYER_INDEX = 25;

	private final Player player;
	private final long generation;
	private final long facadeEpoch;

	public ScriptedPrayer(Player player, long generation, long facadeEpoch) {
		this.player = player;
		this.generation = generation;
		this.facadeEpoch = facadeEpoch;
	}

	@HostAccess.Export
	public boolean isActive(double prayerValue) {
		Integer prayer = integral(prayerValue);
		if (prayer == null || !live()) {
			return false;
		}
		return player.getPrayer().prayerActive[prayer.intValue()];
	}

	@HostAccess.Export
	public boolean activate(double prayerValue) {
		Integer prayer = integral(prayerValue);
		if (prayer == null || !canMutate()) {
			return false;
		}
		if (player.getPrayer().prayerActive[prayer.intValue()]) {
			return true;
		}
		ActivatePrayers.activatePrayer(player, prayer.intValue());
		return player.getPrayer().prayerActive[prayer.intValue()];
	}

	@HostAccess.Export
	public boolean deactivate(double prayerValue) {
		Integer prayer = integral(prayerValue);
		if (prayer == null || !canMutate()) {
			return false;
		}
		if (!player.getPrayer().prayerActive[prayer.intValue()]) {
			return true;
		}
		ActivatePrayers.activatePrayer(player, prayer.intValue());
		return !player.getPrayer().prayerActive[prayer.intValue()];
	}

	@HostAccess.Export
	public boolean deactivateAll() {
		if (!canMutate()) {
			return false;
		}
		PrayerDrain.resetPrayers(player);
		PrayerData data = player.getPrayer();
		for (boolean active : data.prayerActive) {
			if (active) {
				return false;
			}
		}
		return true;
	}

	@HostAccess.Export
	public String name(double prayerValue) {
		Integer prayer = integral(prayerValue);
		if (prayer == null) {
			return null;
		}
		return player.getPrayer().PRAYER_NAME[prayer.intValue()];
	}

	@HostAccess.Export
	public int requiredLevel(double prayerValue) {
		Integer prayer = integral(prayerValue);
		if (prayer == null) {
			return -1;
		}
		return player.getPrayer().PRAYER_LEVEL_REQUIRED[prayer.intValue()];
	}

	private boolean live() {
		return ScriptEncounterService.getInstance().canUseFacade(player,
				generation, facadeEpoch, false);
	}

	private boolean canMutate() {
		return ScriptEncounterService.getInstance().canMutate(player,
				generation, facadeEpoch);
	}

	private static Integer integral(double value) {
		return !Double.isFinite(value) || value != Math.rint(value)
				|| value < 0 || value > MAX_PRAYER_INDEX
						? null : Integer.valueOf((int) value);
	}
}
