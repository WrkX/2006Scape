package com.rs2.game.content.minigames.brimhavenagilityarena;

import com.rs2.game.content.StaticObjectList;
import com.rs2.game.players.Player;

/**
 * Brimhaven Agility Arena minigame handlers.
 */
public final class BrimhavenAgilityArena {

	private static final int ENTRANCE_LADDER_X = 2809;
	private static final int ENTRANCE_LADDER_Y = 3194;
	private static final int ARENA_EXIT_X = 2805;
	private static final int ARENA_EXIT_Y = 9590;
	private static final int ARENA_LANDING_X = 2805;
	private static final int ARENA_LANDING_Y = 9589;
	private static final int ARENA_PLANE = 3;
	private static final int SURFACE_PLANE = 0;
	private static final int ENTRANCE_HUT_X = 2809;
	private static final int ENTRANCE_HUT_Y = 3193;

	private BrimhavenAgilityArena() {
	}

	public static boolean handleObject(Player player, int objectId, int objectX, int objectY) {
		switch (objectId) {
			case StaticObjectList.LADDER_3617:
				return handleEntranceLadderDown(player, objectX, objectY);
			case StaticObjectList.LADDER_3618:
				return handleArenaLadderUp(player, objectX, objectY);
			default:
				return false;
		}
	}

	private static boolean handleEntranceLadderDown(Player player, int objectX, int objectY) {
		if (objectX != ENTRANCE_LADDER_X || objectY != ENTRANCE_LADDER_Y) {
			return false;
		}
		player.startAnimation(827);
		player.getPacketSender().closeAllWindows();
		player.getPlayerAssistant().movePlayer(ARENA_LANDING_X, ARENA_LANDING_Y, ARENA_PLANE);
		player.getPacketSender().sendMessage("You climb down.");
		return true;
	}

	private static boolean handleArenaLadderUp(Player player, int objectX, int objectY) {
		if (objectX != ARENA_EXIT_X || objectY != ARENA_EXIT_Y) {
			return false;
		}
		player.startAnimation(828);
		player.getPacketSender().closeAllWindows();
		player.getPlayerAssistant().movePlayer(ENTRANCE_HUT_X, ENTRANCE_HUT_Y, SURFACE_PLANE);
		player.getPacketSender().sendMessage("You climb up.");
		return true;
	}
}
