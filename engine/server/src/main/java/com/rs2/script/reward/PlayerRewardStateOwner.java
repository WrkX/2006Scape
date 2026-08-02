package com.rs2.script.reward;

import com.rs2.game.players.Player;

/**
 * Java-only per-player reward-state owner.
 *
 * <p>Provides the exact live player/session token, the reward mutex, and a
 * monotonic version. The player-local transaction holds {@link #mutex()}
 * across the complete apply, reads {@link #version()} under it, and calls
 * {@link #commit()} exactly once on success. Abort and rollback leave the
 * version unchanged. Roster-wide coordination acquires owners in ascending
 * player slot order.
 */
public final class PlayerRewardStateOwner {

	private final Player player;
	private final Object mutex = new Object();
	private long version;

	PlayerRewardStateOwner(Player player) {
		this.player = player;
	}

	/** Exact live player object this owner token belongs to. */
	public Player player() {
		return player;
	}

	/** The reward mutex; hold it across one complete transaction. */
	public Object mutex() {
		return mutex;
	}

	/** Current monotonic version; unchanged on abort. */
	public long version() {
		synchronized (mutex) {
			return version;
		}
	}

	/** Increments the version exactly once; part of the no-throw commit. */
	public void commit() {
		synchronized (mutex) {
			version++;
		}
	}

}
