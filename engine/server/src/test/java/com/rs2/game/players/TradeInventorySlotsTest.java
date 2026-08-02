package com.rs2.game.players;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.Before;
import org.junit.Test;

import com.rs2.Constants;
import com.rs2.game.items.GameItem;
import com.rs2.util.Stream;

public class TradeInventorySlotsTest {

	private static final int COINS = 995;
	private static final int BONES = 526;
	private static final int IRON_SWORD = 1293;

	private Client player;

	@Before
	public void setUp() {
		io.netty.channel.embedded.EmbeddedChannel channel =
				new io.netty.channel.embedded.EmbeddedChannel();
		org.apollo.game.session.GameSession session =
				new org.apollo.game.session.GameSession(channel, null, false);
		player = new Client(session, 1);
		player.playerId = 1;
		player.playerName = "trade-test";
		player.outStream = new Stream(new byte[Constants.BUFFER_SIZE]);
		player.outStream.packetEncryption =
				new org.apollo.util.security.IsaacRandom(new int[4]);
		initItemDefinitions();
	}

	private static void initItemDefinitions() {
		org.apollo.cache.def.ItemDefinition[] defs =
				new org.apollo.cache.def.ItemDefinition[IRON_SWORD + 1];
		for (int id = 0; id < defs.length; id++) {
			defs[id] = new org.apollo.cache.def.ItemDefinition(id);
		}
		defs[COINS].setStackable(true);
		defs[BONES].setStackable(true);
		org.apollo.cache.def.ItemDefinition.init(defs);
	}

	@Test
	public void stackableOfferNeedsNoSlotWhenPlayerAlreadyHasItem() {
		fillInventoryExceptSlot(0);
		player.playerItems[0] = COINS + 1;
		player.playerItemsN[0] = 500;

		CopyOnWriteArrayList<GameItem> offer = new CopyOnWriteArrayList<>();
		offer.add(new GameItem(COINS, 1000));

		assertEquals(0, player.getItemAssistant().tradeReceiveSlotsRequired(offer));
	}

	@Test
	public void stackableOfferNeedsSlotWhenPlayerDoesNotHaveItem() {
		fillInventory();

		CopyOnWriteArrayList<GameItem> offer = new CopyOnWriteArrayList<>();
		offer.add(new GameItem(COINS, 1000));

		assertEquals(1, player.getItemAssistant().tradeReceiveSlotsRequired(offer));
	}

	@Test
	public void nonStackableOfferAlwaysNeedsOneSlotPerEntry() {
		fillInventoryExceptSlot(0);
		player.playerItems[0] = BONES + 1;
		player.playerItemsN[0] = 10;

		CopyOnWriteArrayList<GameItem> offer = new CopyOnWriteArrayList<>();
		offer.add(new GameItem(IRON_SWORD, 1));

		assertEquals(1, player.getItemAssistant().tradeReceiveSlotsRequired(offer));
	}

	@Test
	public void multipleStackablesOnlyCountNewItemTypes() {
		fillInventoryExceptSlot(0);
		player.playerItems[0] = COINS + 1;
		player.playerItemsN[0] = 100;

		CopyOnWriteArrayList<GameItem> offer = new CopyOnWriteArrayList<>();
		offer.add(new GameItem(COINS, 500));
		offer.add(new GameItem(IRON_SWORD, 1));

		assertEquals(1, player.getItemAssistant().tradeReceiveSlotsRequired(offer));
	}

	private void fillInventory() {
		for (int i = 0; i < player.playerItems.length; i++) {
			player.playerItems[i] = BONES + 1;
			player.playerItemsN[i] = 1;
		}
	}

	private void fillInventoryExceptSlot(int emptySlot) {
		for (int i = 0; i < player.playerItems.length; i++) {
			if (i == emptySlot) {
				player.playerItems[i] = 0;
				player.playerItemsN[i] = 0;
			} else {
				player.playerItems[i] = BONES + 1;
				player.playerItemsN[i] = 1;
			}
		}
	}
}
