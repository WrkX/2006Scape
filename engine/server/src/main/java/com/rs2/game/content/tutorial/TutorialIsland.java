package com.rs2.game.content.tutorial;

import com.rs2.Constants;
import com.rs2.game.players.Player;
/**
 * Tutorial Island state restoration and helpers.
 */
public final class TutorialIsland {

	private TutorialIsland() {
	}

	public static void restoreState(Player player) {
		if (!Constants.TUTORIAL_ISLAND || player.tutorialProgress >= 36) {
			return;
		}

		player.getPlayerAssistant().hideAllSideBars();
		player.canWalkTutorial = true;

		switch (player.tutorialProgress) {
			case 0:
				player.getPacketSender().createArrow(1, 1);
				showChatbox(player,
						"To start the tutorial use your left mouse button to click on the",
						Constants.SERVER_NAME + " in this room. He is indicated by a flashing",
						"yellow arrow above his head. If you can't see him, use your",
						"keyboard's arrow keys to rotate the view.",
						"@blu@Getting started");
				break;
			case 1:
				player.getPacketSender().setSidebarInterface(11, 904);
				player.getPacketSender().flashSideBarIcon(-11);
				showChatbox(player,
						"Please click on the flashing wrench icon found at the bottom",
						"right of your screen. This will display your player controls.",
						"", "",
						"Player controls");
				break;
			case 2:
				showChatbox(player,
						"You can interact with many items of scenery by simply clicking",
						"on them. Right clicking will also give more options. Feel free to",
						"try it with the things in this room, then click on the door",
						"indicated with the yellow arrow to go through to the next instructor.",
						"Interacting with scenery");
				player.getPacketSender().createArrow(3098, 3107, player.getH(), 2);
				break;
			case 3:
				player.getPacketSender().setSidebarInterface(3, 3213);
				player.getPacketSender().flashSideBarIcon(-3);
				showChatbox(player,
						"Click on the flashing backpack icons to the right hand side of",
						"the main window to view your inventory. Your inventory is a list",
						"of everything you have on your backpack.", "",
						"Viewing the items that you were given");
				break;
			case 4:
				showChatbox(player,
						"Well done! You managed to cut some logs from the tree! Next,",
						"use the tinderbox in your inventory to light the logs.",
						"First click on the tinderbox to use it.",
						"Then click on the logs in your inventory to light them.",
						"Making a fire");
				break;
			case 5:
				player.getPacketSender().setSidebarInterface(1, 3917);
				player.getPacketSender().flashSideBarIcon(-1);
				showChatbox(player,
						"Click on the flashing bar graph icon near the inventory button",
						"to see your skill stats.", "", "",
						"You gained some experience.");
				break;
			case 6:
				showChatbox(player,
						"Click on the sparkling fishing spot indicated by the flashing",
						"arrow. Remember, you can check your inventory by clicking the",
						"backpack icon.", "",
						"Catch some Shrimp");
				player.getPacketSender().createArrow(3101, 3092, player.getH(), 2);
				break;
			case 9:
				player.getPacketSender().flashSideBarIcon(-13);
				break;
			case 11:
				player.getPacketSender().flashSideBarIcon(-12);
				showChatbox(player,
						"It's only a short distance to the next guide.",
						"Why not try running there? Start by opening the player",
						"settings, that's the flashing icon of a wrench.", "",
						"Running");
				player.getPacketSender().createArrow(3086, 3126, player.getH(), 2);
				break;
			case 12:
				player.getPacketSender().createArrow(1, 4);
				showChatbox(player, "Talk with the Quest Guide.", "",
						"He will tell you all about quests.", "",
						"Quest Guide");
				break;
			case 13:
				player.getPacketSender().setSidebarInterface(2, 638);
				player.getPacketSender().flashSideBarIcon(-2);
				showChatbox(player, "Open the Quest Journal.", "",
						"Click on the flashing icon next to your inventory.", "",
						"Quest Journal");
				break;
			case 14:
				showChatbox(player,
						"",
						"It's time to enter some caves. Click on the ladder to go down to",
						"the next area.", "",
						"Moving on");
				player.getPacketSender().createArrow(3088, 3119, player.getH(), 2);
				break;
			case 20:
				showChatbox(player,
						"To smith you'll need a hammer - like the one you were given by",
						"Dezzick - access to an anvil like the one with the arrow over it",
						"and enough metal bars to make what you are trying to smith.",
						"",
						"Smithing a dagger");
				player.getPacketSender().createArrow(3082, 9499, player.getH(), 2);
				break;
			case 21:
				showChatbox(player,
						"So let's move on. Go through the gates shown by the arrow.",
						"Remember you may need to move the camera to see your",
						"surroundings. Speak to the guide for a recap at any time.",
						"",
						"You've finished in this area");
				player.getPacketSender().createArrow(3094, 9503, player.getH(), 2);
				break;
			case 22:
				player.getPacketSender().setSidebarInterface(4, 1644);
				player.getPacketSender().flashSideBarIcon(-4);
				showChatbox(player,
						"",
						"You now have access to a new interface. Click on the flashing",
						"icon of a man the one to the right of your backpack icon.",
						"",
						"Wielding weapons");
				break;
			case 24:
				player.getPacketSender().createArrow(3111, 9518, player.getH(), 2);
				break;
			case 28:
				showChatbox(player, "", "Continue through the next door.", "", "",
						"Moving on");
				player.getPacketSender().createArrow(3129, 3124, player.getH(), 2);
				player.getPacketSender().createArrow(1, 8);
				break;
			case 29:
				player.getPacketSender().setSidebarInterface(5, 5608);
				player.getPacketSender().flashSideBarIcon(-5);
				showChatbox(player,
						"",
						"Click on the flashing icon to open the Prayer menu.", "",
						"",
						"Your Prayer menu");
				break;
			case 30:
				player.getPacketSender().setSidebarInterface(9, 5715);
				player.getPacketSender().flashSideBarIcon(-9);
				showChatbox(player,
						"This will be explaing by Brother Brace shortly, but first click",
						"on the other flashing face to the right of your screen.",
						"", "",
						"This is your friends list");
				break;
			case 33:
				player.getPacketSender().flashSideBarIcon(-6);
				break;
			default:
				break;
		}
	}

	private static void showChatbox(Player player, String line1, String line2,
			String line3, String line4, String title) {
		player.getPacketSender().chatbox(6180);
		player.getDialogueHandler().chatboxText(line1, line2, line3, line4, title);
		player.getPacketSender().chatbox(6179);
	}
}
