package com.rs2.game.content.quests.impl;

import com.rs2.game.content.StaticItemList;
import com.rs2.game.content.StaticNpcList;
import com.rs2.game.content.StaticObjectList;
import com.rs2.game.content.quests.QuestRewards;
import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.players.Player;
import com.rs2.world.clip.Region;

/**
 * Restless Ghost 
 * @author Andrew (Mr Extremez)
 */

public class RestlessGhost {

	private static final int COFFIN_X = 3249;
	private static final int COFFIN_Y = 3192;
	private static final int GHOST_SPAWN_X = 3249;
	private static final int GHOST_SPAWN_Y = 3193;
	private static final int GHOST_PROXIMITY = 5;

	public static boolean isGraveyardCoffin(int objectX, int objectY) {
		return objectX == COFFIN_X && objectY == COFFIN_Y;
	}

	public static void interactWithGraveyardCoffin(Player player, int objectType) {
		if (!isGraveyardCoffin(player.objectX, player.objectY)) {
			return;
		}
		boolean coffinOpen = objectType == StaticObjectList.COFFIN_2146;

		switch (player.restGhost) {
			case 2:
				handleTalkToGhostStage(player);
				return;
			case 3:
				setCoffinOpen(player, true);
				player.getPacketSender().sendMessage(
						"You search the coffin and find some bones.");
				return;
			case 4:
				setCoffinOpen(player, true);
				completeWithSkull(player);
				return;
			case 0:
				player.getPacketSender().sendMessage("You have not started this quest yet.");
				toggleCoffin(player, coffinOpen);
				return;
			case 5:
				player.getPacketSender().sendMessage("You have already finished this quest.");
				toggleCoffin(player, coffinOpen);
				return;
			default:
				toggleCoffin(player, coffinOpen);
		}
	}

	/** @deprecated Use {@link #interactWithGraveyardCoffin(Player, int)} */
	public static void handleCoffinOpen(Player player) {
		interactWithGraveyardCoffin(player, StaticObjectList.COFFIN_2145);
	}

	public static void handleSkullOnCoffin(Player player, int objectX, int objectY) {
		if (!isGraveyardCoffin(objectX, objectY)) {
			return;
		}
		if (player.restGhost == 4) {
			setCoffinOpen(player, true);
			completeWithSkull(player);
		} else if (player.restGhost == 0) {
			player.getPacketSender().sendMessage("You have not started this quest yet.");
		} else if (player.restGhost == 5) {
			player.getPacketSender().sendMessage("You have already finished this quest.");
		}
	}

	private static void handleTalkToGhostStage(Player player) {
		setCoffinOpen(player, true);
		spawnRestlessGhost(player);
		player.getPlayerAssistant().requestUpdates();
		player.getPacketSender().sendMessage("You open the coffin.");
	}

	public static boolean hasGhostspeakAmuletEquipped(Player player) {
		int amulet = player.playerEquipment[player.playerAmulet];
		return amulet == StaticItemList.GHOSTSPEAK_AMULET
				|| amulet == StaticItemList.GHOSTSPEAK_AMULET_4250;
	}

	public static void talkToRestlessGhost(Player player, int npcType) {
		if (player.restGhost != 2) {
			return;
		}
		if (hasGhostspeakAmuletEquipped(player)) {
			player.getDialogueHandler().sendDialogues(371, npcType);
		} else {
			player.getDialogueHandler().sendDialogues(6050, npcType);
		}
	}

	private static void completeWithSkull(Player player) {
		if (player.getItemAssistant().playerHasItem(StaticItemList.SKULL, 1)) {
			player.getItemAssistant().deleteItem(StaticItemList.SKULL, 1);
			player.getPacketSender().sendMessage("You have freed the ghost!");
			QuestRewards.restFinish(player);
			spawnRestlessGhost(player);
		} else {
			player.getDialogueHandler().sendStatement("You need the skull for this part.");
			player.nextChat = 0;
		}
	}

	private static void setCoffinOpen(Player player, boolean open) {
		int objectId = open ? StaticObjectList.COFFIN_2146
				: StaticObjectList.COFFIN_2145;
		player.getPacketSender().object(objectId, COFFIN_X, COFFIN_Y, 0, 0, 10);
		Region.addObject(objectId, COFFIN_X, COFFIN_Y, 0, 10, 0, false);
	}

	private static void toggleCoffin(Player player, boolean coffinOpen) {
		setCoffinOpen(player, !coffinOpen);
	}

	private static void spawnRestlessGhost(Player player) {
		clearNearbyRestlessGhosts(player.heightLevel);
		NpcHandler.spawnNpc(player, StaticNpcList.RESTLESS_GHOST, GHOST_SPAWN_X,
				GHOST_SPAWN_Y, player.heightLevel, 0, 0, 0, 0, 0, false, false);
		Npc spawned = findNearbyRestlessGhost(player.heightLevel);
		if (spawned == null) {
			return;
		}
		spawned.randomWalk = false;
		spawned.facePlayer(player);
		spawned.updateRequired = true;
	}

	private static void clearNearbyRestlessGhosts(int heightLevel) {
		for (int i = 0; i < NpcHandler.npcs.length; i++) {
			Npc npc = NpcHandler.npcs[i];
			if (npc != null && npc.npcType == StaticNpcList.RESTLESS_GHOST
					&& Math.abs(npc.absX - GHOST_SPAWN_X) <= GHOST_PROXIMITY
					&& Math.abs(npc.absY - GHOST_SPAWN_Y) <= GHOST_PROXIMITY
					&& npc.heightLevel == heightLevel) {
				NpcHandler.npcs[i] = null;
			}
		}
	}

	private static Npc findNearbyRestlessGhost(int heightLevel) {
		for (Npc npc : NpcHandler.npcs) {
			if (npc != null && npc.npcType == StaticNpcList.RESTLESS_GHOST
					&& npc.absX == GHOST_SPAWN_X && npc.absY == GHOST_SPAWN_Y
					&& npc.heightLevel == heightLevel) {
				return npc;
			}
		}
		return null;
	}

	public static void showInformation(Player client) {
		for (int i = 8144; i < 8196; i++) {
			client.getPacketSender().sendString("", i);
		}
		for (int i = 12174; i < (12174 + 50); i++) {
			client.getPacketSender().sendString( "", i);
		}
		for (int i = 14945; i < (14945 + 100); i++) {
			client.getPacketSender().sendString("", i);
		}
		client.getPacketSender().sendString("@dre@Restless Ghost", 8144);
		client.getPacketSender().sendString("", 8145);
		if (client.restGhost == 0) {
			client.getPacketSender().sendString("Restless Ghost", 8144);
			client.getPacketSender().sendString("I can start this quest by speaking to Father Aereck in",	8147);
			client.getPacketSender().sendString("Lumbridge", 8148);
			client.getPacketSender().sendString("Minimum Requirements:", 8149);
			client.getPacketSender().sendString("None.", 8150);
		} else if (client.restGhost == 1) {
			client.getPacketSender().sendString("Restless Ghost", 8144);
			client.getPacketSender().sendString(
					"@str@I've talked to Father Aereck", 8147);
			client.getPacketSender().sendString(
					"I should speak to Father Urhey", 8148);
		} else if (client.restGhost == 2) {
			client.getPacketSender().sendString("Restless Ghost", 8144);
			client.getPacketSender().sendString(
					"@str@I've talked Father Urhey", 8147);
			client.getPacketSender().sendString("@str@He gave me an amulet",
					8148);
			client.getPacketSender().sendString(
					"I should speak to the ghost", 8149);
		} else if (client.restGhost == 3) {
			client.getPacketSender().sendString("Restless Ghost", 8144);
			client.getPacketSender().sendString(
					"@str@I've talked to the Ghost", 8147);
			client.getPacketSender().sendString("I should travel to the wizards tower and kill the skeleton", 8148);
			client.getPacketSender().sendString(
					"I should find the ghosts skull", 8149);
		} else if (client.restGhost == 4) {
			client.getPacketSender().sendString("Restless Ghost", 8144);
			client.getPacketSender().sendString("@str@I've found the skull",
					8147);
			client.getPacketSender().sendString(
					"@str@I killed the skeleton", 8148);
			client.getPacketSender().sendString(
					"I should travel back to the ghost", 8149);
		} else if (client.restGhost == 5) {
			client.getPacketSender().sendString("Restless Ghost", 8144);
			client.getPacketSender().sendString(
					"@str@I've set the skull in the coffin", 8147);
			client.getPacketSender().sendString(
					"@str@I've freed the ghost.", 8148);
			client.getPacketSender().sendString("@red@     QUEST COMPLETE",
					8150);
			client.getPacketSender().sendString(
					"As a reward, I gained 125 Prayer Exp.", 8151);
			client.getPacketSender().sendString("And 1 Quest Point", 8152);
			client.getPacketSender().sendString("", 8152);
		}
		client.getPacketSender().showInterface(8134);
	}

}
