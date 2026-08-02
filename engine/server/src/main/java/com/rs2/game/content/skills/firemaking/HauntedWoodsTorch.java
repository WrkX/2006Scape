package com.rs2.game.content.skills.firemaking;

import com.rs2.Constants;
import com.rs2.game.content.StaticItemList;
import com.rs2.game.content.StaticObjectList;
import com.rs2.game.content.skills.SkillHandler;
import com.rs2.game.players.Player;

public final class HauntedWoodsTorch {

	private static final int FIREMAKING_LEVEL = 47;
	private static final int FIREMAKING_XP = 100;
	private static final int PRAYER_RESTORE = 10;
	private static final int[] TINDERBOXES = { 590, 7329, 7330, 7331 };
	private static final int[] TORCHES = {
			StaticObjectList.TORCH,
			StaticObjectList.TORCH_13201,
			StaticObjectList.TORCH_13202,
			StaticObjectList.TORCH_13203,
			StaticObjectList.TORCH_13204,
			StaticObjectList.TORCH_13205,
			StaticObjectList.TORCH_13206,
			StaticObjectList.TORCH_13207
	};

	private HauntedWoodsTorch() {
	}

	public static boolean isTorch(int objectId) {
		for (int torchId : TORCHES) {
			if (objectId == torchId) {
				return true;
			}
		}
		return false;
	}

	public static boolean light(Player player) {
		if (!SkillHandler.FIREMAKING) {
			player.getPacketSender().sendMessage("This skill is currently disabled.");
			return false;
		}
		if (player.playerLevel[Constants.FIREMAKING] < FIREMAKING_LEVEL) {
			player.getPacketSender().sendMessage(
					"You need a Firemaking level of " + FIREMAKING_LEVEL + " to light this torch.");
			return false;
		}
		if (!hasTinderbox(player)) {
			player.getPacketSender().sendMessage("You need a tinderbox to light the torch.");
			return false;
		}
		if (!player.getItemAssistant().playerHasItem(StaticItemList.BARK, 1)) {
			player.getPacketSender().sendMessage("You need some bark to light the torch.");
			return false;
		}
		int maxPrayer = player.getPlayerAssistant()
				.getLevelForXP(player.playerXP[Constants.PRAYER]);
		if (player.playerLevel[Constants.PRAYER] >= maxPrayer) {
			player.getPacketSender().sendMessage("You already have full prayer points.");
			return false;
		}
		player.getItemAssistant().deleteItem(StaticItemList.BARK, 1);
		player.startAnimation(733);
		player.getPlayerAssistant().addSkillXP(FIREMAKING_XP, Constants.FIREMAKING);
		player.playerLevel[Constants.PRAYER] = Math.min(maxPrayer,
				player.playerLevel[Constants.PRAYER] + PRAYER_RESTORE);
		player.getPlayerAssistant().refreshSkill(Constants.PRAYER);
		player.getPacketSender().sendMessage("You light the torch with the bark.");
		player.getPacketSender().sendMessage("You feel restored.");
		return true;
	}

	private static boolean hasTinderbox(Player player) {
		for (int tinderboxId : TINDERBOXES) {
			if (player.getItemAssistant().playerHasItem(tinderboxId)) {
				return true;
			}
		}
		return false;
	}

}
