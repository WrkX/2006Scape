package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.apollo.cache.def.ItemDefinition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.Constants;
import com.rs2.game.items.ItemConstants;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.util.Stream;
import org.apollo.util.security.IsaacRandom;

public class ScriptedPlayerTest {

	private ItemDefinition[] previousDefinitions;
	private boolean previousVariableXpRate;
	private double previousXpRate;
	private Player previousPlayer;
	private static final int PLAYER_SLOT = 120;

	@Before
	public void installDefinitions() throws Exception {
		Field definitions = ItemDefinition.class.getDeclaredField("definitions");
		definitions.setAccessible(true);
		previousDefinitions = (ItemDefinition[]) definitions.get(null);
		ItemDefinition[] testDefinitions = new ItemDefinition[1003];
		testDefinitions[1000] = definition(1000, true);
		testDefinitions[1001] = definition(1001, false);
		testDefinitions[1002] = definition(1002, false);
		definitions.set(null, testDefinitions);
		previousVariableXpRate = Constants.VARIABLE_XP_RATE;
		previousXpRate = Constants.XP_RATE;
		previousPlayer = PlayerHandler.players[PLAYER_SLOT];
	}

	@After
	public void restoreDefinitions() throws Exception {
		Field definitions = ItemDefinition.class.getDeclaredField("definitions");
		definitions.setAccessible(true);
		definitions.set(null, previousDefinitions);
		Constants.VARIABLE_XP_RATE = previousVariableXpRate;
		Constants.XP_RATE = previousXpRate;
		PlayerHandler.players[PLAYER_SLOT] = previousPlayer;
	}

	@Test
	public void inventoryAndBankRemovalArePreflightedAndTruthful() {
		Player player = authoritativePlayer();
		player.playerItems[0] = 101;
		player.playerItemsN[0] = 5;
		player.bankItems[0] = 201;
		player.bankItemsN[0] = 4;
		ScriptedPlayer scripted = new ScriptedPlayer(player);

		assertEquals(28, scripted.getInventory().getCapacity());
		assertEquals(27, scripted.getInventory().getFreeSlots());
		assertTrue(scripted.getInventory().canRemove(100, 3));
		assertFalse(scripted.getInventory().remove(100, 6));
		assertTrue(scripted.getInventory().remove(100, 3));
		assertEquals(2, scripted.getInventory().count(100));

		assertTrue(scripted.getBank().remove(200, 4));
		assertFalse(scripted.getBank().remove(200, 1));
		assertTrue(scripted.getBank().getCapacity() > 0);
	}

	@Test
	public void skillAndPresentationCapabilitiesValidateRanges() {
		Player player = authoritativePlayer();
		player.playerRights = 3;
		ScriptedPlayer scripted = new ScriptedPlayer(player);

		assertEquals(player.playerLevel[0], scripted.getSkills().getCurrentLevel(0));
		assertEquals(player.playerXP[0], scripted.getSkills().getExperience(0));
		assertEquals(0, scripted.getSkills().getBaseLevel(-1));
		assertFalse(scripted.getSkills().addExperience(-1, 10));
		assertFalse(scripted.getSkills().addExperience(0, Double.NaN));
		assertEquals(3, scripted.getRights());

		assertFalse(scripted.animate(65536));
		assertTrue(scripted.animate(829));
		assertEquals(829, player.animationRequest);
		assertFalse(scripted.graphic(-1));
		assertTrue(scripted.graphic(100));
		assertFalse(scripted.sound(65536));
		assertTrue(scripted.sound(100));
		assertFalse(scripted.showInterface(-1));
		assertTrue(scripted.showInterface(3213));
	}

	@Test
	public void inventoryAddIsAtomicAndTruthful() {
		Player player = authoritativePlayer();
		ScriptedPlayer.InventoryView inventory =
				new ScriptedPlayer(player).getInventory();

		assertFalse(inventory.add(999, 1));
		assertFalse(inventory.add(1000, 0));
		assertEquals(28, inventory.getFreeSlots());

		fillInventory(player, 1002);
		player.playerItems[0] = 1001;
		player.playerItemsN[0] = 5;
		assertTrue(inventory.add(1000, 4));
		assertEquals(9, inventory.count(1000));

		int[] idsBeforeOverflow = player.playerItems.clone();
		int[] amountsBeforeOverflow = player.playerItemsN.clone();
		player.playerItemsN[0] = ItemConstants.MAX_ITEM_AMOUNT;
		amountsBeforeOverflow[0] = ItemConstants.MAX_ITEM_AMOUNT;
		assertFalse(inventory.add(1000, 1));
		assertArrayEquals(idsBeforeOverflow, player.playerItems);
		assertArrayEquals(amountsBeforeOverflow, player.playerItemsN);

		player.playerItems[0] = 0;
		player.playerItemsN[0] = 0;
		int[] idsBeforePartial = player.playerItems.clone();
		int[] amountsBeforePartial = player.playerItemsN.clone();
		assertFalse(inventory.add(1001, 2));
		assertArrayEquals(idsBeforePartial, player.playerItems);
		assertArrayEquals(amountsBeforePartial, player.playerItemsN);

		player.playerItems[1] = 0;
		player.playerItemsN[1] = 0;
		assertTrue(inventory.add(1001, 2));
		assertEquals(2, inventory.count(1001));
	}

	@Test
	public void nullMessageIsIgnoredWithoutThrowing() {
		Player player = authoritativePlayer();
		ScriptedPlayer scripted = new ScriptedPlayer(player);
		scripted.message(null);
	}
		Constants.VARIABLE_XP_RATE = true;
		Player player = authoritativePlayer();
		player.tutorialProgress = 36;
		player.setXPRate(10);
		ScriptedPlayer.SkillView skills = new ScriptedPlayer(player).getSkills();

		assertTrue(skills.addExperience(0, 1));
		assertEquals(10, player.playerXP[0]);

		player.playerXP[0] = 199999995;
		int levelBefore = player.playerLevel[0];
		assertFalse(skills.addExperience(0, 1));
		assertEquals(199999995, player.playerXP[0]);
		assertEquals(levelBefore, player.playerLevel[0]);

		assertFalse(skills.addExperience(0, Double.POSITIVE_INFINITY));
		assertEquals(199999995, player.playerXP[0]);
	}

	private static ItemDefinition definition(int id, boolean stackable) {
		ItemDefinition definition = new ItemDefinition(id);
		definition.setName("test-" + id);
		definition.setStackable(stackable);
		return definition;
	}

	private Player authoritativePlayer() {
		Player player = new Player(PLAYER_SLOT) {
			@Override
			public void flushOutStream() {
				if (outStream != null) {
					outStream.currentOffset = 0;
				}
			}
		};
		player.initialized = true;
		player.isActive = true;
		player.disconnected = false;
		player.isDead = false;
		player.respawnTimer = 0;
		player.outStream = new Stream(new byte[Constants.BUFFER_SIZE]);
		player.outStream.packetEncryption = new IsaacRandom(new int[4]);
		PlayerHandler.players[PLAYER_SLOT] = player;
		return player;
	}

	private static void fillInventory(Player player, int itemId) {
		for (int slot = 0; slot < player.playerItems.length; slot++) {
			player.playerItems[slot] = itemId + 1;
			player.playerItemsN[slot] = 1;
		}
	}
}
