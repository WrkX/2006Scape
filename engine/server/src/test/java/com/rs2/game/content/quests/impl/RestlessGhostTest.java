package com.rs2.game.content.quests.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.game.content.StaticItemList;
import com.rs2.game.content.StaticNpcList;
import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.players.Client;
import com.rs2.game.players.Player;
import com.rs2.util.Stream;

public class RestlessGhostTest {

	private Npc[] previousNpcs;
	private Player player;

	@Before
	public void setUp() {
		previousNpcs = NpcHandler.npcs.clone();
		Arrays.fill(NpcHandler.npcs, null);
		io.netty.channel.embedded.EmbeddedChannel channel =
				new io.netty.channel.embedded.EmbeddedChannel();
		org.apollo.game.session.GameSession session =
				new org.apollo.game.session.GameSession(channel, null, false);
		player = new Client(session, 1);
		player.playerId = 1;
		player.playerName = "test";
		player.outStream = new Stream(new byte[com.rs2.Constants.BUFFER_SIZE]);
		player.outStream.packetEncryption = new org.apollo.util.security.IsaacRandom(new int[4]);
		player.restGhost = 2;
		player.objectX = 3249;
		player.objectY = 3192;
		player.absX = 3249;
		player.absY = 3191;
		player.heightLevel = 0;
	}

	@After
	public void tearDown() {
		System.arraycopy(previousNpcs, 0, NpcHandler.npcs, 0, previousNpcs.length);
	}

	@Test
	public void isGraveyardCoffinMatchesQuestCoffin() {
		assertTrue(RestlessGhost.isGraveyardCoffin(3249, 3192));
		assertFalse(RestlessGhost.isGraveyardCoffin(3249, 3193));
	}

	@Test
	public void handleCoffinOpenSpawnsGhost() {
		RestlessGhost.interactWithGraveyardCoffin(player,
				com.rs2.game.content.StaticObjectList.COFFIN_2145);

		Npc ghost = findRestlessGhost();
		assertNotNull(ghost);
		assertEquals(StaticNpcList.RESTLESS_GHOST, ghost.npcType);
		assertEquals(3249, ghost.absX);
		assertEquals(3193, ghost.absY);
	}

	@Test
	public void handleCoffinOpenDoesNotDuplicateGhost() {
		RestlessGhost.interactWithGraveyardCoffin(player,
				com.rs2.game.content.StaticObjectList.COFFIN_2146);
		RestlessGhost.interactWithGraveyardCoffin(player,
				com.rs2.game.content.StaticObjectList.COFFIN_2146);

		int count = 0;
		for (Npc npc : NpcHandler.npcs) {
			if (npc != null && npc.npcType == StaticNpcList.RESTLESS_GHOST) {
				count++;
			}
		}
		assertEquals(1, count);
	}

	@Test
	public void talkWithoutAmuletStartsWooWooDialogue() {
		RestlessGhost.talkToRestlessGhost(player, StaticNpcList.RESTLESS_GHOST);

		assertEquals(2, player.restGhost);
		assertEquals(6051, player.nextChat);
	}

	@Test
	public void talkWithAmuletStartsQuestDialogue() {
		player.playerEquipment[player.playerAmulet] = StaticItemList.GHOSTSPEAK_AMULET;
		RestlessGhost.talkToRestlessGhost(player, StaticNpcList.RESTLESS_GHOST);

		assertEquals(372, player.nextChat);
	}

	@Test
	public void coffinAtStageThreeShowsBonesMessage() {
		player.restGhost = 3;

		RestlessGhost.interactWithGraveyardCoffin(player,
				com.rs2.game.content.StaticObjectList.COFFIN_2145);

		assertNull(findRestlessGhost());
	}

	@Test
	public void handleSkullOnCoffinCompletesQuest() {
		player.restGhost = 4;
		player.playerItems[0] = StaticItemList.SKULL + 1;
		player.playerItemsN[0] = 1;

		RestlessGhost.handleSkullOnCoffin(player, 3249, 3192);

		assertEquals(5, player.restGhost);
		assertNotNull(findRestlessGhost());
	}

	private static Npc findRestlessGhost() {
		for (Npc npc : NpcHandler.npcs) {
			if (npc != null && npc.npcType == StaticNpcList.RESTLESS_GHOST) {
				return npc;
			}
		}
		return null;
	}
}
