package com.rs2.script.drop;

import java.util.ArrayList;
import java.util.List;

import org.apollo.cache.def.ItemDefinition;

import com.rs2.script.ScriptedPlayer;
import com.rs2.script.world.ScriptDropEntry;
import com.rs2.script.world.ScriptDropResult;
import com.rs2.script.world.ScriptEncounterRng;
import com.rs2.script.world.ScriptGroundItemHandle;

/**
 * Owner-neutral transactional drop roll extracted from the Phase 4 WP6
 * encounter algorithm.
 *
 * <p>The transaction acquires the explicit RNG-owner lock, snapshots exact
 * state plus version into a local WP6 RNG, preflights the worst-case identity
 * budget, selects the weighted winner and amount rolls on the local RNG,
 * stages every selected identity invisibly through the delivery policy,
 * performs all capacity/version/identity checks, arms the private detach,
 * and reaches one no-throw commit that publishes the staged identities and
 * the final RNG state together. Any earlier parse, selection, allocation,
 * creation, verification, owner-version, or delivery failure removes every
 * staged identity and leaves the RNG owner and visible ground state exact.
 */
public final class DropTransaction {

	private DropTransaction() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}

	/**
	 * Rolls {@code entries} through the owner and delivery policy.
	 *
	 * @param recipient the exact live recipient, or {@code null} for public
	 *            delivery
	 * @return the immutable results, or {@code null} when any stage failed
	 *         (nothing committed)
	 */
	public static List<ScriptDropResult> execute(
			DropRngTransactionOwner rngOwner, GroundDeliveryPolicy delivery,
			List<ScriptDropEntry> entries, ScriptedPlayer recipient) {
		if (rngOwner == null || delivery == null || entries == null
				|| entries.isEmpty()) {
			return null;
		}
		List<ScriptGroundItemHandle> staged = new ArrayList<>();
		rngOwner.lock();
		try {
			if (!delivery.eligible()) {
				return null;
			}
			long ownerVersion = rngOwner.version();
			ScriptEncounterRng local = new ScriptEncounterRng(rngOwner.state());
			long worstCaseIdentities = 0L;
			long worstCaseAmount = 0L;
			for (ScriptDropEntry entry : entries) {
				if (!ItemDefinition.exists(entry.itemId())) {
					return null;
				}
				ItemDefinition definition = ItemDefinition.lookup(
						entry.itemId());
				worstCaseIdentities += definition.isStackable()
						? 1L : entry.maxAmount();
				worstCaseAmount += entry.maxAmount();
			}
			if (worstCaseIdentities > delivery.identityBudgetRemaining()
					|| worstCaseAmount > Integer.MAX_VALUE) {
				return null;
			}

			ScriptDropEntry weightedWinner = selectWeighted(entries, local);
			List<ScriptDropEntry> selected = new ArrayList<>();
			List<Integer> amounts = new ArrayList<>();
			for (ScriptDropEntry entry : entries) {
				if (!entry.always() && entry != weightedWinner) {
					continue;
				}
				int amount = entry.minAmount() + local.nextInt(
						entry.maxAmount() - entry.minAmount() + 1);
				ScriptGroundItemHandle handle = delivery.stage(recipient,
						entry.itemId(), amount);
				if (handle == null) {
					throw new IllegalStateException("reward staging failed");
				}
				selected.add(entry);
				amounts.add(Integer.valueOf(amount));
				staged.add(handle);
			}
			if (rngOwner.version() != ownerVersion) {
				throw new IllegalStateException(
						"RNG owner version changed during the transaction");
			}
			delivery.verifyStaged();
			if (!delivery.detach(staged)) {
				throw new IllegalStateException("reward detach failed");
			}

			// No-throw joint commit: final RNG state and the staged
			// identities become visible together.
			rngOwner.publishState(local.state());
			delivery.publish(staged);

			List<ScriptDropResult> results = new ArrayList<>();
			for (int index = 0; index < selected.size(); index++) {
				results.add(new ScriptDropResult(selected.get(index).itemId(),
						amounts.get(index).intValue(), staged.get(index)));
			}
			return results;
		} catch (RuntimeException failure) {
			delivery.removeExact(staged);
			return null;
		} finally {
			rngOwner.unlock();
		}
	}

	private static ScriptDropEntry selectWeighted(List<ScriptDropEntry> entries,
			ScriptEncounterRng rng) {
		long sum = 0L;
		for (ScriptDropEntry entry : entries) {
			sum += entry.weight();
		}
		if (sum == 0L) {
			return null;
		}
		int pick = rng.nextInt((int) sum);
		long cumulative = 0L;
		for (ScriptDropEntry entry : entries) {
			if (entry.weight() == 0) {
				continue;
			}
			cumulative += entry.weight();
			if (pick < cumulative) {
				return entry;
			}
		}
		return null;
	}

}
