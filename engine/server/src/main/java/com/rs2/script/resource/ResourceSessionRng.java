package com.rs2.script.resource;

import com.rs2.script.drop.DropRngTransactionOwner;
import com.rs2.script.world.ScriptEncounterRng;

/**
 * Java-only game-cycle-owned deterministic RNG owner of one resource
 * session.
 *
 * <p>Derived from the accepted SplitMix64 seed algorithm with the session's
 * immutable owner token and activation ordinal, never guest-visible, and
 * serialized by the game-cycle FIFO event order. It implements the WP2
 * {@link DropRngTransactionOwner} contract so the resource session can roll
 * deterministic success checks and, on a later consumer, drop transactions:
 * invalid input or an aborted attempt never advances state or version; the
 * no-throw commit publishes state and version together. Lock/unlock are
 * no-ops exactly like the accepted encounter and area adapters because every
 * consumer already runs on the single game-cycle thread while holding the
 * resource runtime's monitor.
 */
public final class ResourceSessionRng implements DropRngTransactionOwner {

	private final long ownerToken;
	private final long ordinal;
	private final ScriptEncounterRng rng;
	private long version;

	ResourceSessionRng(long processSeed, long generation, long ownerToken,
			long ordinal) {
		this.ownerToken = ownerToken;
		this.ordinal = ordinal;
		this.rng = ScriptEncounterRng.derive(processSeed, generation,
				ownerToken, ordinal);
	}

	@Override
	public void lock() {
		// Game-cycle serialized; the session monitor is already held.
	}

	@Override
	public void unlock() {
		// Game-cycle serialized; the session monitor is already held.
	}

	@Override
	public long version() {
		return version;
	}

	@Override
	public long state() {
		return rng.state();
	}

	@Override
	public void publishState(long nextState) {
		rng.restore(nextState);
		version++;
	}

	/** Immutable owner token used by the seed material. */
	public long ownerToken() {
		return ownerToken;
	}

	/** Monotonic activation ordinal used by the seed material. */
	public long ordinal() {
		return ordinal;
	}

	/**
	 * Bounded {@code 0..bound-1} using the accepted SplitMix64 algorithm.
	 * Invalid bounds return {@code -1} without advancing state.
	 */
	public int nextInt(int bound) {
		return rng.nextInt(bound);
	}

	/**
	 * Rational chance {@code numerator/denominator}. Invalid values and a
	 * zero numerator return {@code false} without advancing state; equality
	 * returns {@code true} without advancing state.
	 */
	public boolean chance(int numerator, int denominator) {
		return rng.chance(numerator, denominator);
	}

}
