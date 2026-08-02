package com.rs2.world;

import com.rs2.game.players.Player;

/**
 * WP2's read-only object source of truth. A higher layer occupying a tile
 * masks every lower layer even when its id does not match the packet.
 */
public final class WorldObjectResolver {

	public static ResolvedWorldObject resolve(int x, int y, int plane) {
		return WorldObjectService.getInstance().resolve(x, y, plane);
	}

	public static ResolvedWorldObject resolve(Player player, int x, int y, int plane) {
		return WorldObjectService.getInstance().resolve(player, x, y, plane);
	}

	private WorldObjectResolver() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
