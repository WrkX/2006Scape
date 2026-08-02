package com.rs2.script.area;

import java.util.ArrayList;
import java.util.List;

import com.rs2.GameEngine;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.drop.GroundDeliveryPolicy;
import com.rs2.script.world.ScriptGroundItemHandle;
import com.rs2.script.world.ScriptEncounterService;

/**
 * Ground delivery policy of one area drop claim.
 *
 * <p>Owns eligibility (the captured killer slot must still contain the exact
 * live player object at commit, on the captured plane), the captured tile,
 * private/public visibility with the configured private TTL or a bounded
 * public lifetime, the per-session identity budget, invisible staging,
 * verification, the no-throw publication of exact identities, and exact
 * abort removal. Public identities carry the area token so area close and
 * reload can compare-remove only its own unclaimed identities.
 */
final class AreaDeliveryPolicy implements GroundDeliveryPolicy {

	private final ScriptAreaRuntime runtime;
	private final long areaToken;
	private final ScriptedPlayer recipient;
	private final Player killer;
	private final int x;
	private final int y;
	private final int plane;
	private final AreaDropPolicy policy;
	private final int privateTicks;

	AreaDeliveryPolicy(ScriptAreaRuntime runtime, long areaToken,
			ScriptedPlayer recipient, Player killer, int x, int y, int plane,
			AreaDropPolicy policy, int privateTicks) {
		this.runtime = runtime;
		this.areaToken = areaToken;
		this.recipient = recipient;
		this.killer = killer;
		this.x = x;
		this.y = y;
		this.plane = plane;
		this.policy = policy;
		this.privateTicks = privateTicks;
	}

	@Override
	public boolean eligible() {
		if (!runtime.isSessionActive(areaToken)) {
			return false;
		}
		if (killer == null || killer.playerId < 0
				|| killer.playerId >= PlayerHandler.players.length
				|| PlayerHandler.players[killer.playerId] != killer) {
			return false;
		}
		if (killer.heightLevel != plane
				|| !ScriptEncounterService.isAuthoritativeLive(killer, true)) {
			return false;
		}
		return recipient != null
				&& recipient.backingPlayer() == killer;
	}

	@Override
	public int x() {
		return x;
	}

	@Override
	public int y() {
		return y;
	}

	@Override
	public int plane() {
		return plane;
	}

	@Override
	public boolean isPrivate() {
		return policy == AreaDropPolicy.PRIVATE_TO_KILLER;
	}

	@Override
	public int privateTicks() {
		return privateTicks;
	}

	@Override
	public long identityBudgetRemaining() {
		return ScriptAreaRuntime.MAX_GROUND_IDENTITIES_PER_AREA
				- runtime.groundIdentityCount(areaToken);
	}

	@Override
	public ScriptGroundItemHandle stage(ScriptedPlayer target, int itemId,
			int amount) {
		return GameEngine.itemHandler.createScriptGroundItems(
				target.backingPlayer(), areaToken, itemId, amount, x, y,
				plane, 0);
	}

	@Override
	public void verifyStaged() {
		// Staging is exact and the session monitor serializes all claims.
	}

	@Override
	public boolean detach(List<ScriptGroundItemHandle> staged) {
		List<com.rs2.game.items.GroundItem> identities =
				new ArrayList<com.rs2.game.items.GroundItem>();
		for (ScriptGroundItemHandle handle : staged) {
			identities.addAll(handle.identities());
		}
		if (identities.isEmpty()) {
			return false;
		}
		if (runtime.consumeFailDetachForTesting()) {
			return false;
		}
		if (policy == AreaDropPolicy.PRIVATE_TO_KILLER) {
			return GameEngine.itemHandler.detachExact(identities,
					privateTicks);
		}
		return GameEngine.itemHandler.armPublicLifetime(identities,
				com.rs2.world.ItemHandler.HIDE_TICKS);
	}

	@Override
	public void publish(List<ScriptGroundItemHandle> staged) {
		runtime.publishGroundIdentities(areaToken, staged);
	}

	@Override
	public void removeExact(List<ScriptGroundItemHandle> staged) {
		List<com.rs2.game.items.GroundItem> identities =
				new ArrayList<com.rs2.game.items.GroundItem>();
		for (ScriptGroundItemHandle handle : staged) {
			identities.addAll(handle.identities());
		}
		if (!identities.isEmpty()) {
			GameEngine.itemHandler.removeExact(identities);
		}
	}

}
