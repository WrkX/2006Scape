package com.rs2.script.drop;

/**
 * Java-only game-cycle-owned deterministic RNG owner used by
 * {@link DropTransaction}.
 *
 * <p>The owner provides the exact state plus a monotonic version under its
 * own mutex. The transaction acquires the owner lock, snapshots state and
 * version into a local WP6 RNG, revalidates the version immediately before
 * commit, publishes the resulting state once, and releases. Invalid input or
 * abort never advances it and never increments the version.
 *
 * <p>No member is guest-visible. The encounter adapter supplies the
 * encounter RNG and owner lock; later work packages supply area-session and
 * raid-session owners without pretending they are encounters.
 */
public interface DropRngTransactionOwner {

	/** Acquires the owner lock. Reentrant where the caller already holds it. */
	void lock();

	/** Releases the owner lock. */
	void unlock();

	/** Current monotonic state version; unchanged on abort. */
	long version();

	/** Exact current SplitMix64 state. */
	long state();

	/**
	 * Publishes the resulting state and increments the version exactly once.
	 * By contract this is part of the no-throw joint commit and must not
	 * fail.
	 */
	void publishState(long nextState);

}
