package com.rs2.script.area;

import com.rs2.script.drop.DropRngTransactionOwner;
import com.rs2.script.world.ScriptEncounterRng;

/**
 * Java-only game-cycle-owned deterministic RNG owner of one area session.
 *
 * <p>Derived from the accepted SplitMix64 seed algorithm with the session's
 * immutable owner token and activation ordinal, never guest-visible, and
 * serialized by the game-cycle FIFO event order. It implements the WP2
 * {@link DropRngTransactionOwner} contract: invalid input or an aborted
 * transaction never advances state or version; the no-throw commit publishes
 * state and version together. Lock/unlock are no-ops exactly like the
 * accepted encounter adapter because every consumer already runs on the
 * single game-cycle thread while holding the area runtime's monitor.
 */
public final class AreaSessionRng implements DropRngTransactionOwner {

	private final long ownerToken;
	private final long ordinal;
	private final ScriptEncounterRng rng;
	private long version;

	AreaSessionRng(long processSeed, long generation, long ownerToken,
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

}
