package com.rs2.script.activation;

/**
 * World projection seam of the runtime activation transaction.
 *
 * <p>The transaction drives one exact two-phase handoff: prepare and reserve
 * without mutation, apply inactive shadow state, verify, retire the
 * predecessor into an idempotent undo ledger, verify retirement, pass the
 * final injectable checkpoint, then reach the no-throw commit. Any failure
 * before the checkpoint aborts in reverse order through
 * {@link #restorePredecessor()}, {@link #removeShadow()}, and
 * {@link #releaseReservations()}; a failure after the checkpoint can only be
 * reported, never rolled back.
 *
 * <p>Phase 4 ships a no-op adapter; the work package that activates real
 * world state (areas) must implement this contract over real NPC slots,
 * object footprints, shop and drop bindings, and report identities. WP1
 * proves the protocol with a synthetic adapter.
 */
public interface ProjectionAdapter {

	/**
	 * Validates candidate projection intents without touching live state.
	 * Throwing here reserves nothing and stages nothing.
	 */
	void prepare(RuntimeSnapshot candidate);

	/**
	 * Acquires a handoff reservation over every predecessor/replacement
	 * runtime key, blocking third-party writers while allowing the exact
	 * old/new pair to share a logical footprint. Throwing here stages
	 * nothing.
	 */
	void reserve(RuntimeSnapshot predecessor, RuntimeSnapshot candidate);

	/**
	 * Applies candidate intents as inactive shadow state. On failure the
	 * transaction reverses already-applied intents in LIFO order.
	 */
	void applyShadow(RuntimeSnapshot candidate);

	/** Verifies the shadow state without guest visibility. */
	void verifyShadow(RuntimeSnapshot candidate);

	/**
	 * Retires predecessor projections into an idempotent undo ledger.
	 * Throwing midway leaves the ledger able to restore every retired
	 * identity.
	 */
	void retirePredecessor(RuntimeSnapshot predecessor);

	/** Verifies both the retirement and the candidate shadow state. */
	void verifyRetirement();

	/**
	 * Final injectable pre-publication checkpoint. This is the last operation
	 * allowed to fail or abort; every earlier abort point already passed.
	 */
	void checkpoint();

	/**
	 * Makes the new projections visible. By contract this method must not
	 * throw and must run with nothing fallible before or after it until the
	 * host publishes the new runtime state.
	 */
	void commitSelection(RuntimeSnapshot candidate);

	/**
	 * Restores the predecessor from the undo ledger. Must be idempotent and
	 * non-guest; returns {@code true} only when restoration is complete. The
	 * transaction retries this while the handoff reservation is held and
	 * quarantines a persistent failure as fatal rather than claiming a clean
	 * rollback.
	 */
	boolean restorePredecessor();

	/** Removes candidate shadow projections during abort. */
	void removeShadow();

	/** Releases handoff reservations during abort. */
	void releaseReservations();

	/**
	 * Post-commit disposal: discards the predecessor undo/shadow state and
	 * releases handoff reservations. Failure is non-authoritative and is
	 * quarantined or retried without reverting the published candidate.
	 */
	void dispose(RuntimeSnapshot predecessor);

}
