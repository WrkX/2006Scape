package com.rs2.game.objects.impl;

import com.rs2.game.players.Player;

/**
 * Staircases in Keldagrim and the Blast Furnace.
 * Palace stairs change height level; the Blast Furnace uses a separate map region.
 */
public final class KeldagrimStairs {

	private static final int STAIR_MIN = 6087;
	private static final int STAIR_MAX = 6115;
	private static final int BLAST_FURNACE_STAIR = 9138;
	private static final int BLAST_FURNACE_STAIR_OBJECT = 6108;

	private static final int BF_SURFACE_X = 2913;
	private static final int BF_SURFACE_Y = 10167;
	private static final int BF_BASEMENT_X = 1939;
	private static final int BF_BASEMENT_Y = 4956;

	private KeldagrimStairs() {
	}

	public static boolean isStaircase(int objectId) {
		return objectId >= STAIR_MIN && objectId <= STAIR_MAX
				|| objectId == BLAST_FURNACE_STAIR;
	}

	public static boolean isStaircase(int objectId, int x, int y, int height) {
		if (objectId == BLAST_FURNACE_STAIR) {
			return isBlastFurnaceBuilding(x, y) || isBlastFurnaceBasementArea(x, y);
		}
		if (objectId < STAIR_MIN || objectId > STAIR_MAX) {
			return false;
		}
		if (isPalaceArea(x, y)) {
			return true;
		}
		if (isBlastFurnaceBuilding(x, y)) {
			return objectId == BLAST_FURNACE_STAIR_OBJECT;
		}
		if (isBlastFurnaceBasementArea(x, y)) {
			return objectId == BLAST_FURNACE_STAIR_OBJECT;
		}
		return false;
	}

	public static void climbUp(Player player) {
		if (isBlastFurnaceBasement(player)) {
			enterBlastFurnaceSurface(player);
			return;
		}
		if (isBlastFurnaceBuilding(player.objectX, player.objectY)
				&& player.heightLevel == 0) {
			player.getPacketSender().sendMessage("The stairs only lead down.");
			return;
		}
		Climbing.climbUp(player);
	}

	public static void climbDown(Player player) {
		if (isBlastFurnaceBasement(player)) {
			player.getPacketSender().sendMessage("The stairs only lead up.");
			return;
		}
		if (isBlastFurnaceSurfaceStair(player)) {
			enterBlastFurnaceBasement(player);
			return;
		}
		Climbing.climbDown(player);
	}

	private static boolean isPalaceArea(int x, int y) {
		return x >= 2838 && x <= 2902 && y >= 10168 && y <= 10218;
	}

	private static boolean isBlastFurnaceBuilding(int x, int y) {
		return x >= 2903 && x <= 2935 && y >= 10160 && y <= 10230;
	}

	private static boolean isBlastFurnaceSurfaceStair(Player player) {
		if (player.heightLevel != 0) {
			return false;
		}
		if (!isBlastFurnaceStairObject(player.objectId)) {
			return false;
		}
		return isBlastFurnaceBuilding(player.objectX, player.objectY);
	}

	private static boolean isBlastFurnaceStairObject(int objectId) {
		return objectId == BLAST_FURNACE_STAIR
				|| objectId == BLAST_FURNACE_STAIR_OBJECT;
	}

	private static boolean isBlastFurnaceBasement(Player player) {
		return isBlastFurnaceBasementArea(player.absX, player.absY)
				&& player.heightLevel == 0;
	}

	private static boolean isBlastFurnaceBasementArea(int x, int y) {
		return x >= 1920 && x <= 1960 && y >= 4940 && y <= 4980;
	}

	private static void enterBlastFurnaceBasement(Player player) {
		if (System.currentTimeMillis() - player.climbDelay < 1200) {
			return;
		}
		player.startAnimation(827);
		player.getPacketSender().closeAllWindows();
		player.getPlayerAssistant().movePlayer(BF_BASEMENT_X, BF_BASEMENT_Y, 0);
		player.climbDelay = System.currentTimeMillis();
		player.getPacketSender().sendMessage("You climb down.");
		player.resetWalkingQueue();
	}

	private static void enterBlastFurnaceSurface(Player player) {
		if (System.currentTimeMillis() - player.climbDelay < 1200) {
			return;
		}
		player.startAnimation(828);
		player.getPacketSender().closeAllWindows();
		player.getPlayerAssistant().movePlayer(BF_SURFACE_X, BF_SURFACE_Y, 0);
		player.climbDelay = System.currentTimeMillis();
		player.getPacketSender().sendMessage("You climb up.");
		player.resetWalkingQueue();
	}
}
