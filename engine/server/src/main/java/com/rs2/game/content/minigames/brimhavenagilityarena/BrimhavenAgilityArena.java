package com.rs2.game.content.minigames.brimhavenagilityarena;

import com.rs2.Constants;
import com.rs2.game.content.StaticItemList;
import com.rs2.game.content.StaticObjectList;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.util.Misc;

/**
 * Brimhaven Agility Arena minigame handlers.
 */
public final class BrimhavenAgilityArena {

	public static final int ARENA_PLANE = 3;
	private static final int SURFACE_PLANE = 0;
	private static final int ENTRANCE_LADDER_X = 2809;
	private static final int ENTRANCE_LADDER_Y = 3194;
	private static final int ARENA_EXIT_X = 2805;
	private static final int ARENA_EXIT_Y = 9590;
	private static final int ARENA_LANDING_X = 2805;
	private static final int ARENA_LANDING_Y = 9589;
	private static final int ENTRANCE_HUT_X = 2809;
	private static final int ENTRANCE_HUT_Y = 3193;
	private static final int GRID_ORIGIN_X = 2761;
	private static final int GRID_ORIGIN_Y = 9546;
	private static final int GRID_SPACING = 11;
	private static final int GRID_SIZE = 5;
	private static final int EXIT_TILE_X = 2805;
	private static final int EXIT_TILE_Y = 9590;
	private static final int DISPENSER_ROTATION_TICKS = 100;
	private static final int[][] DISPENSER_TILES = buildDispenserTiles();

	private static int dispenserTimer = 1;
	private static int activeDispenserX = -1;
	private static int activeDispenserY = -1;

	private BrimhavenAgilityArena() {
	}

	public static void process() {
		if (dispenserTimer > 0) {
			dispenserTimer--;
			return;
		}
		dispenserTimer = DISPENSER_ROTATION_TICKS;
		rotateActiveDispenser();
	}

	public static boolean isInArena(Player player) {
		return player.heightLevel == ARENA_PLANE
				&& player.absX >= GRID_ORIGIN_X - 5
				&& player.absX <= EXIT_TILE_X + 5
				&& player.absY >= GRID_ORIGIN_Y - 5
				&& player.absY <= EXIT_TILE_Y + 5;
	}

	public static boolean handleObject(Player player, int objectId, int objectX, int objectY) {
		if (BrimhavenAgilityCourse.handleObject(player, objectId, objectX, objectY)) {
			return true;
		}
		switch (objectId) {
			case StaticObjectList.LADDER_3617:
				return handleEntranceLadderDown(player, objectX, objectY);
			case StaticObjectList.LADDER_3618:
				return handleArenaLadderUp(player, objectX, objectY);
			case StaticObjectList.TICKET_DISPENSER:
			case StaticObjectList.TICKET_DISPENSER_3608:
				return handleTicketDispenser(player, objectX, objectY);
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
		onEnterArena(player);
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
		onExitArena(player);
		return true;
	}

	public static boolean isDispenserTile(int objectX, int objectY) {
		for (int[] tile : DISPENSER_TILES) {
			if (tile[0] == objectX && tile[1] == objectY) {
				return true;
			}
		}
		return false;
	}

	private static boolean handleTicketDispenser(Player player, int objectX, int objectY) {
		if (!isInArena(player)) {
			return false;
		}
		if (!isDispenserTile(objectX, objectY)) {
			return false;
		}
		if (activeDispenserX < 0 || objectX != activeDispenserX || objectY != activeDispenserY) {
			player.getPacketSender().sendMessage(
					"You can only get a ticket when the flashing arrow is above the pillar.");
			return true;
		}
		if (player.brimhavenDispenserTagged) {
			player.getPacketSender().sendMessage(
					"You can only get one ticket at a time, wait till the arrow moves again.");
			return true;
		}
		if (player.getItemAssistant().freeSlots() < 1
				&& !player.getItemAssistant().playerHasItem(StaticItemList.AGILITY_ARENA_TICKET, 1)) {
			player.getPacketSender().sendMessage("Not enough space in your inventory.");
			return true;
		}
		player.brimhavenDispenserTagged = true;
		int agilityLevel = player.playerLevel[Constants.AGILITY];
		int tagXp = Math.min(300, 30 * (agilityLevel / 10));
		player.getPlayerAssistant().addSkillXP(tagXp, Constants.AGILITY);
		player.getItemAssistant().addItem(StaticItemList.AGILITY_ARENA_TICKET, 1);
		player.getPacketSender().sendMessage("You have received an Agility Arena Ticket!");
		return true;
	}

	public static void onPlayerInitialized(Player player) {
		if (!isInArena(player)) {
			return;
		}
		onEnterArena(player);
	}

	private static void onEnterArena(Player player) {
		player.inBrimhavenAgilityArena = true;
		player.brimhavenDispenserTagged = false;
		if (activeDispenserX < 0) {
			rotateActiveDispenser();
		} else {
			sendDispenserHint(player);
		}
	}

	private static void onExitArena(Player player) {
		player.inBrimhavenAgilityArena = false;
		player.brimhavenDispenserTagged = false;
		clearDispenserHint(player);
	}

	private static void rotateActiveDispenser() {
		int previousX = activeDispenserX;
		int previousY = activeDispenserY;
		while (true) {
			int[] tile = DISPENSER_TILES[Misc.random(DISPENSER_TILES.length - 1)];
			if (tile[0] != previousX || tile[1] != previousY) {
				activeDispenserX = tile[0];
				activeDispenserY = tile[1];
				break;
			}
		}
		for (Player player : PlayerHandler.players) {
			if (player == null || !player.inBrimhavenAgilityArena) {
				continue;
			}
			player.brimhavenDispenserTagged = false;
			sendDispenserHint(player);
		}
	}

	private static void sendDispenserHint(Player player) {
		if (activeDispenserX < 0) {
			return;
		}
		player.getPacketSender().createObjectHints(activeDispenserX, activeDispenserY, ARENA_PLANE, 2);
	}

	private static void clearDispenserHint(Player player) {
		player.getPacketSender().createObjectHints(0, 0, 0, -1);
	}

	private static int[][] buildDispenserTiles() {
		int[][] tiles = new int[GRID_SIZE * GRID_SIZE - 1][2];
		int index = 0;
		for (int xIndex = 0; xIndex < GRID_SIZE; xIndex++) {
			for (int yIndex = 0; yIndex < GRID_SIZE; yIndex++) {
				int x = GRID_ORIGIN_X + GRID_SPACING * xIndex;
				int y = GRID_ORIGIN_Y + GRID_SPACING * yIndex;
				if (x == EXIT_TILE_X && y == EXIT_TILE_Y) {
					continue;
				}
				tiles[index][0] = x;
				tiles[index][1] = y;
				index++;
			}
		}
		return tiles;
	}
}
