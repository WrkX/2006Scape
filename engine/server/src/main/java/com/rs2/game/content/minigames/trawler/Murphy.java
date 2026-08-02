package com.rs2.game.content.minigames.trawler;

import com.rs2.Constants;
import com.rs2.GameEngine;
import com.rs2.game.content.StaticItemList;
import com.rs2.game.players.Player;

import static com.rs2.game.content.StaticNpcList.*;

/**
 * Murphy – Fishing Trawler NPC at Port Khazard.
 */
public final class Murphy {

	public static final int DIALOGUE_ACTION = 4600;
	public static final int DIVING_DIALOGUE_ACTION = 4601;

	private Murphy() {
	}

	public static boolean isMurphy(int npcId) {
		return npcId == MURPHY || npcId == MURPHY_464 || npcId == MURPHY_465
				|| npcId == MURPHY_466;
	}

	public static void talk(Player player, int npcId) {
		player.talkingNpc = npcId;
		player.npcType = npcId;

		if (player.inTrawlerBoat()) {
			talkOnBoat(player, npcId);
		} else {
			showDockMenu(player, npcId);
		}
	}

	public static void handleMainOption(Player player, int option) {
		int npcId = player.npcType;
		switch (option) {
			case 1:
				explainTrawler(player, npcId);
				break;
			case 2:
				giveSextant(player, npcId);
				break;
			case 3:
				showDivingMenu(player, npcId);
				break;
			case 4:
				endDialogue(player);
				break;
			default:
				endDialogue(player);
				break;
		}
	}

	public static void handleDivingOption(Player player, int option) {
		int npcId = player.npcType;
		switch (option) {
			case 1:
				giveFishbowlHelmet(player, npcId);
				break;
			case 2:
				giveDivingApparatus(player, npcId);
				break;
			case 3:
				showDockMenu(player, npcId);
				break;
			default:
				endDialogue(player);
				break;
		}
	}

	private static void talkOnBoat(Player player, int npcId) {
		if (GameEngine.trawler.inProgress()) {
			player.getDialogueHandler().sendNpcChat1(
					"Whoooahh sailor! Hold on tight, it's a fierce sea today.",
					npcId, "Murphy");
		} else {
			player.getDialogueHandler().sendNpcChat2(
					"The ship's ready to depart.",
					"Climb down the ladder when you're ready to set sail.",
					npcId, "Murphy");
		}
	}

	private static void showDockMenu(Player player, int npcId) {
		player.getDialogueHandler().sendNpcChat1(
				"Good day to you land lover. Fancy hitting the high seas?",
				npcId, "Murphy");
		player.getDialogueHandler().sendOption(
				"Could you tell me about the trawler?",
				"Could I have a sextant?",
				"Could I have diving equipment?",
				"Nothing, thanks.");
		player.dialogueAction = DIALOGUE_ACTION;
	}

	private static void explainTrawler(Player player, int npcId) {
		player.getDialogueHandler().sendNpcChat4(
				"Well of course you can help! The seas are merciless though.",
				"You need fishing level 15+ to catch fish on the trawler.",
				"Bring rope for torn nets, swamp paste for leaks, and an axe and hammer for the kraken.",
				"Board via the gangplank and grab supplies from the general store on the pier.",
				npcId, "Murphy");
		if (player.playerLevel[Constants.FISHING] < 15) {
			player.getPacketSender().sendMessage(
					"You need a fishing level of 15 or above to play Fishing Trawler.");
		}
	}

	private static void giveSextant(Player player, int npcId) {
		if (player.getItemAssistant().playerHasItem(StaticItemList.SEXTANT)) {
			player.getDialogueHandler().sendNpcChat2(
					"You've already got a sextant, m'hearty!",
					"I can tell from the taste of the sea spray where I am!",
					npcId, "Murphy");
			return;
		}
		if (!player.getItemAssistant().addItem(StaticItemList.SEXTANT, 1)) {
			player.getDialogueHandler().sendNpcChat1(
					"You're carrying too much to take my old sextant.",
					npcId, "Murphy");
			return;
		}
		player.getDialogueHandler().sendNpcChat3(
				"Hmm. I used to use a sextant when I was a young fella.",
				"I can tell from the taste of the sea spray where I am, m'hearty!",
				"Here, take my old one.",
				npcId, "Murphy");
		player.getPacketSender().sendMessage("Murphy has given you a sextant.");
	}

	private static void showDivingMenu(Player player, int npcId) {
		player.getDialogueHandler().sendNpcChat1(
				"Ahoy there! All set for a dive?",
				npcId, "Murphy");
		player.getDialogueHandler().sendOption(
				"Could I get a diving helmet?",
				"Could I get a diving apparatus?",
				"Go back.");
		player.dialogueAction = DIVING_DIALOGUE_ACTION;
	}

	private static void giveFishbowlHelmet(Player player, int npcId) {
		if (player.getItemAssistant().playerHasItem(StaticItemList.FISHBOWL_HELMET)) {
			player.getDialogueHandler().sendNpcChat1(
					"You've already got a diving helmet.",
					npcId, "Murphy");
			return;
		}
		if (!player.getItemAssistant().addItem(StaticItemList.FISHBOWL_HELMET, 1)) {
			player.getDialogueHandler().sendNpcChat1(
					"You're carrying too much to take a diving helmet.",
					npcId, "Murphy");
			return;
		}
		player.getDialogueHandler().sendNpcChat2(
				"Sure, here you go. Try not to lose this one.",
				"I'm not a very good glassblower.",
				npcId, "Murphy");
		player.getPacketSender().sendMessage("Murphy has given you a fishbowl helmet.");
	}

	private static void giveDivingApparatus(Player player, int npcId) {
		if (player.getItemAssistant().playerHasItem(StaticItemList.DIVING_APPARATUS)) {
			player.getDialogueHandler().sendNpcChat1(
					"You've already got a diving apparatus.",
					npcId, "Murphy");
			return;
		}
		if (!player.getItemAssistant().addItem(StaticItemList.DIVING_APPARATUS, 1)) {
			player.getDialogueHandler().sendNpcChat1(
					"You're carrying too much to take a diving apparatus.",
					npcId, "Murphy");
			return;
		}
		player.getDialogueHandler().sendNpcChat2(
				"Sure, I have plenty after the... little incident.",
				"Here, have this one. Only dropped once!",
				npcId, "Murphy");
		player.getPacketSender().sendMessage("Murphy has given you a diving apparatus.");
	}

	private static void endDialogue(Player player) {
		player.nextChat = 0;
		player.dialogueAction = 0;
		player.getPacketSender().closeAllWindows();
	}
}
