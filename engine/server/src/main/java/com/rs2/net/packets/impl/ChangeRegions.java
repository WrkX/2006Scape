package com.rs2.net.packets.impl;

import com.rs2.Constants;
import com.rs2.GameEngine;
import com.rs2.game.content.music.Music;
import com.rs2.game.globalworldobjects.Doors;
import com.rs2.game.players.Player;
import com.rs2.net.Packet;
import com.rs2.net.packets.PacketType;
import com.rs2.world.GlobalDropsHandler;
import com.rs2.world.WorldObjectService;

/**
 * Change Regions
 */
public class ChangeRegions implements PacketType {

	@Override
	public void processPacket(Player player, Packet packet) {
		if (Constants.SOUND && player.musicOn) {
			Music.playMusic(player);
		}
		WorldObjectService.getInstance().rebuildObjects(player);
		if (player instanceof com.rs2.game.players.Client) {
			GlobalDropsHandler.load((com.rs2.game.players.Client) player);
		}
		GameEngine.itemHandler.reloadItems(player);
		Doors.getSingleton().load();
		player.getPlayerAssistant().removeObjects();// testing
		player.saveFile = true;
		if (player.skullTimer > 0) {
			player.isSkulled = true;
			player.headIconPk = 0;
			player.getPlayerAssistant().requestUpdates();
		}
	}
}
