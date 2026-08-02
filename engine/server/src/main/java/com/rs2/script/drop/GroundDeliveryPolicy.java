package com.rs2.script.drop;

import java.util.List;

import com.rs2.script.ScriptedPlayer;
import com.rs2.script.world.ScriptGroundItemHandle;

/**
 * Java-only ground-delivery policy consumed by {@link DropTransaction}.
 *
 * <p>The policy owns eligibility, the source location and plane, private
 * recipient or public visibility, the private TTL, the identity budget,
 * invisible staging, verification, publication, and exact removal. The
 * transaction holds the RNG-owner and ground-owner locks in the fixed order
 * RNG owner then ground projection and reaches one no-throw commit that
 * publishes the staged identities and the final RNG state together.
 */
public interface GroundDeliveryPolicy {

	/** Exact eligibility of the delivery (recipient, location, plane, TTL). */
	boolean eligible();

	int x();

	int y();

	int plane();

	/** {@code true} when identities are private to one recipient. */
	boolean isPrivate();

	/** Bounded private TTL in game ticks; unused for public delivery. */
	int privateTicks();

	/** Remaining identity budget this delivery may consume. */
	long identityBudgetRemaining();

	/**
	 * Creates one invisible staged identity set for {@code amount} of
	 * {@code itemId}. Returns {@code null} when staging is impossible; the
	 * transaction then removes every already-staged identity and leaves the
	 * RNG owner unchanged.
	 */
	ScriptGroundItemHandle stage(ScriptedPlayer recipient, int itemId,
			int amount);

	/**
	 * Final pre-commit verification of every staged identity (capacity,
	 * version, visibility). Throwing aborts the transaction.
	 */
	void verifyStaged();

	/**
	 * Arms the private TTL (private deliveries) or otherwise finalizes the
	 * staged identities before the joint commit. Returns {@code false} to
	 * abort.
	 */
	boolean detach(List<ScriptGroundItemHandle> staged);

	/**
	 * No-throw publication: makes the staged identities visible/counted
	 * together with the RNG owner's state commit. Must not throw.
	 */
	void publish(List<ScriptGroundItemHandle> staged);

	/** Exact removal of every staged identity during abort. */
	void removeExact(List<ScriptGroundItemHandle> staged);

}
