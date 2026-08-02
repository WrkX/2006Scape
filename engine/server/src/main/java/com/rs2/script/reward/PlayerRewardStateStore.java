package com.rs2.script.reward;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.rs2.game.players.Player;

/**
 * Per-player reward-state owner store.
 *
 * <p>Each live player object owns one reward state with a mutex and a
 * monotonic version. Reward commits increment the version exactly once;
 * abort or rollback leaves it unchanged. The owner is keyed by the exact
 * live player object, so a replacement player object (login/logout) gets a
 * fresh owner.
 */
public final class PlayerRewardStateStore {

	private static final Map<Player, WeakReference<PlayerRewardStateOwner>>
			OWNERS = Collections.synchronizedMap(
					new WeakHashMap<Player, WeakReference<PlayerRewardStateOwner>>());

	private PlayerRewardStateStore() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}

	public static PlayerRewardStateOwner ownerOf(Player player) {
		synchronized (OWNERS) {
			WeakReference<PlayerRewardStateOwner> reference = OWNERS.get(player);
			PlayerRewardStateOwner owner =
					reference == null ? null : reference.get();
			if (owner == null) {
				owner = new PlayerRewardStateOwner(player);
				OWNERS.put(player, new WeakReference<PlayerRewardStateOwner>(owner));
			}
			return owner;
		}
	}

	/** Package-private test seam: drops every retained owner. */
	static void resetForTesting() {
		synchronized (OWNERS) {
			OWNERS.clear();
		}
	}

}
