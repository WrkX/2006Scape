package com.rs2.game.content.skills.crafting;

import com.rs2.game.content.StaticItemList;
import com.rs2.game.content.StaticNpcList;
import com.rs2.game.players.Player;

public final class SplitbarkArmour {

	public static final int DIALOGUE_START = 12630;
	public static final int DIALOGUE_MORE = 12632;
	public static final int DIALOGUE_COSTS = 12633;
	public static final int DIALOGUE_ACTION_MAIN = 10008;
	public static final int DIALOGUE_ACTION_MORE = 10009;

	private enum Piece {
		HELM(StaticItemList.SPLITBARK_HELM, 2, 2, 6000, "splitbark helm"),
		BODY(StaticItemList.SPLITBARK_BODY, 4, 4, 37000, "splitbark body"),
		LEGS(StaticItemList.SPLITBARK_LEGS, 3, 3, 32000, "splitbark legs"),
		GAUNTLETS(StaticItemList.SPLITBARK_GAUNTLETS, 1, 1, 1000, "splitbark gauntlets"),
		BOOTS(StaticItemList.SPLITBARK_GREAVES, 1, 1, 1000, "splitbark boots");

		private final int productId;
		private final int bark;
		private final int fineCloth;
		private final int coins;
		private final String label;

		Piece(int productId, int bark, int fineCloth, int coins, String label) {
			this.productId = productId;
			this.bark = bark;
			this.fineCloth = fineCloth;
			this.coins = coins;
			this.label = label;
		}
	}

	private SplitbarkArmour() {
	}

	public static boolean isSplitbarkWizard(int npcId) {
		return npcId == StaticNpcList.WIZARD_1263;
	}

	public static void startDialogue(Player player) {
		player.getDialogueHandler().sendDialogues(DIALOGUE_START, StaticNpcList.WIZARD_1263);
	}

	public static boolean handleDialogue(Player player, int dialogue) {
		switch (dialogue) {
		case DIALOGUE_START:
			player.getDialogueHandler().sendNpcChat3(
					"I can make splitbark armour for you if you bring bark,",
					"fine cloth and the right payment. The bark comes from",
					"hollow trees; fine cloth from Shades of Mort'ton.",
					player.talkingNpc, "Wizard");
			player.nextChat = DIALOGUE_START + 1;
			return true;
		case DIALOGUE_START + 1:
			player.getDialogueHandler().sendOption(
					"I'd like a splitbark helm.",
					"I'd like a splitbark body.",
					"I'd like splitbark legs.",
					"More options...");
			player.dialogueAction = DIALOGUE_ACTION_MAIN;
			return true;
		case DIALOGUE_MORE:
			player.getDialogueHandler().sendOption(
					"I'd like splitbark gauntlets.",
					"I'd like splitbark boots.",
					"What materials do I need?",
					"Never mind.");
			player.dialogueAction = DIALOGUE_ACTION_MORE;
			return true;
		case DIALOGUE_COSTS:
			player.getDialogueHandler().sendNpcChat4(
					"Boots or gauntlets cost 1 bark, 1 fine cloth and 1,000 coins.",
					"A helm costs 2 of each material and 6,000 coins.",
					"Legs cost 3 of each and 32,000 coins. A body costs 4 of each",
					"and 37,000 coins.",
					player.talkingNpc, "Wizard");
			player.nextChat = 0;
			return true;
		default:
			return false;
		}
	}

	public static boolean handleOption(Player player, int buttonId) {
		if (player.dialogueAction == DIALOGUE_ACTION_MAIN) {
			switch (buttonId) {
			case 9167:
				craft(player, Piece.HELM);
				return true;
			case 9168:
				craft(player, Piece.BODY);
				return true;
			case 9169:
				craft(player, Piece.LEGS);
				return true;
			case 9170:
				player.getDialogueHandler().sendDialogues(DIALOGUE_MORE, player.talkingNpc);
				return true;
			default:
				return false;
			}
		}
		if (player.dialogueAction == DIALOGUE_ACTION_MORE) {
			switch (buttonId) {
			case 9167:
				craft(player, Piece.GAUNTLETS);
				return true;
			case 9168:
				craft(player, Piece.BOOTS);
				return true;
			case 9169:
				player.getDialogueHandler().sendDialogues(DIALOGUE_COSTS, player.talkingNpc);
				return true;
			case 9170:
				player.getDialogueHandler().endDialogue();
				player.getPacketSender().closeAllWindows();
				return true;
			default:
				return false;
			}
		}
		return false;
	}

	private static void craft(Player player, Piece piece) {
		player.getDialogueHandler().endDialogue();
		player.getPacketSender().closeAllWindows();
		if (player.getItemAssistant().freeSlots() < 1) {
			player.getPacketSender().sendMessage("You don't have enough inventory space.");
			return;
		}
		if (!player.getItemAssistant().playerHasItem(StaticItemList.BARK, piece.bark)) {
			player.getPacketSender().sendMessage(
					"You need " + piece.bark + " bark for a " + piece.label + ".");
			return;
		}
		if (!player.getItemAssistant().playerHasItem(StaticItemList.FINE_CLOTH, piece.fineCloth)) {
			player.getPacketSender().sendMessage(
					"You need " + piece.fineCloth + " fine cloth for a " + piece.label + ".");
			return;
		}
		if (!player.getItemAssistant().playerHasItem(995, piece.coins)) {
			player.getPacketSender().sendMessage(
					"You need " + piece.coins + " coins for a " + piece.label + ".");
			return;
		}
		player.getItemAssistant().deleteItem(StaticItemList.BARK, piece.bark);
		player.getItemAssistant().deleteItem(StaticItemList.FINE_CLOTH, piece.fineCloth);
		player.getItemAssistant().deleteItem(995, piece.coins);
		player.getItemAssistant().addItem(piece.productId, 1);
		player.getPacketSender().sendMessage("There you go, enjoy your new armour!");
	}

}
