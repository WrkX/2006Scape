package com.rs2.script.shop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apollo.cache.def.ItemDefinition;

import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.net.PacketSender;
import com.rs2.script.ScriptHost;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.util.LoggerUtils;

/**
 * Java-owned runtime of scripted shop definitions.
 *
 * <p>Each registered {@code defineShop} is an immutable definition with
 * bounded declared stock, prices, and restock policy. The runtime owns one
 * mutable stock per shop id (game-cycle serialized), opens the legacy shop
 * interface through the same frames the engine uses for static shops, and
 * owns the buy/sell paths for a player in a scripted shop. Opening routes
 * are bound by the area runtime to exact area NPC allocations; the legacy
 * numeric static shops and player shops are untouched.
 */
public final class ScriptShopRuntime {

	private static final int MAX_INVENTORY_CAPACITY = 28;
	private static final int MAX_SHOP_SLOTS = 40;
	private static final int CURRENCY = 995;

	private static final ScriptShopRuntime INSTANCE = new ScriptShopRuntime();

	private final Map<String, Stock> stocks = new HashMap<String, Stock>();
	private long currentTick;

	public static ScriptShopRuntime getInstance() {
		return INSTANCE;
	}

	private ScriptShopRuntime() {
	}

	/** Game-cycle restock processing. */
	public synchronized void processGameTick() {
		currentTick++;
		for (Stock stock : stocks.values()) {
			for (int index = 0; index < stock.amounts.length; index++) {
				if (stock.amounts[index] >= stock.declared[index]
						|| currentTick < stock.restockAt[index]) {
					continue;
				}
				stock.amounts[index]++;
				stock.restockAt[index] = currentTick
						+ stock.definition.restockTicks();
			}
		}
	}

	/** Closes every session and discards every stock of a closed generation. */
	public synchronized void closeGeneration(long generation) {
		if (generation == 0L) {
			return;
		}
		stocks.clear();
		for (Player player : PlayerHandler.players) {
			if (player != null) {
				player.scriptShopId = null;
			}
		}
	}

	/** Clears the session of one player on logout/removal. */
	public void onPlayerRemoved(Player player) {
		if (player != null) {
			player.scriptShopId = null;
		}
	}

	/** Test-only lifecycle reset. */
	public synchronized void resetForTesting() {
		stocks.clear();
		currentTick = 0L;
	}

	/**
	 * Opens one scripted shop for one live player through the legacy shop
	 * interface. The shop id must be active in the current generation and
	 * the player must be a valid live identity outside trade/duel.
	 */
	public boolean open(Player player, String shopId) {
		if (player == null || shopId == null
				|| !ScriptEncounterService.isAuthoritativeLive(player, true)
				|| player.getOutStream() == null || player.inTrade
				|| player.inDuel || player.openDuel) {
			return false;
		}
		ShopDefinition definition = ShopDefinitionRegistry.get(shopId);
		if (definition == null) {
			return false;
		}
		synchronized (this) {
			stock(definition);
		}
		PacketSender sender = player.getPacketSender();
		sender.sendSound(1465, 100, 0);
		player.getItemAssistant().resetItems(3823);
		player.isShopping = true;
		player.shopId = -1;
		player.scriptShopId = shopId;
		render(player, definition);
		sender.sendFrame248(3824, 3822);
		sender.sendString(definition.name(), 3901);
		return player.isShopping && shopId.equals(player.scriptShopId);
	}

	/**
	 * Renders the current stock into the shop item list frame. Called after
	 * every buy/sell mutation.
	 */
	private void render(Player player, ShopDefinition definition) {
		Stock stock;
		synchronized (this) {
			stock = stock(definition);
		}
		synchronized (player) {
			player.totalShopItems = stock.activeCount();
			player.getOutStream().createFrameVarSizeWord(53);
			player.getOutStream().writeWord(3900);
			player.getOutStream().writeWord(player.totalShopItems);
			for (int index = 0; index < stock.amounts.length; index++) {
				int amount = stock.amounts[index];
				int itemId = stock.definition.items().get(index).itemId();
				if (amount <= 0) {
					continue;
				}
				if (amount > 254) {
					player.getOutStream().writeByte(255);
					player.getOutStream().writeDWord_v2(amount);
				} else {
					player.getOutStream().writeByte(amount);
				}
				player.getOutStream().writeWordBigEndianA(itemId + 1);
			}
			player.getOutStream().endFrameVarSizeWord();
			player.flushOutStream();
		}
	}

	/** Exact declared-price buy path for one live player. */
	public boolean buy(Player player, int itemId, int amount) {
		if (!inScriptShop(player)) {
			return false;
		}
		Stock stock;
		ShopDefinition definition = ShopDefinitionRegistry
				.get(player.scriptShopId);
		long unitPrice;
		synchronized (this) {
			if (definition == null || (stock = stocks.get(definition.id())) == null) {
				return false;
			}
			int slot = stock.slotOf(itemId);
			if (slot < 0 || stock.amounts[slot] <= 0) {
				player.getPacketSender().sendMessage(
						"You can't buy that right now!");
				return false;
			}
			unitPrice = stock.definition.items().get(slot).price();
			if (amount > stock.amounts[slot]) {
				amount = stock.amounts[slot];
			}
			boolean stackable = isStackable(itemId);
			int freeSlots = player.getItemAssistant().freeSlots();
			if (!stackable && amount > freeSlots) {
				amount = freeSlots;
			}
			if (amount <= 0) {
				player.getPacketSender().sendMessage(
						"You don't have enough space in your inventory.");
				return false;
			}
			long totalValue = unitPrice * (long) amount;
			if (totalValue > Integer.MAX_VALUE
					|| !player.getItemAssistant().playerHasItem(CURRENCY,
							(int) totalValue)) {
				player.getPacketSender().sendMessage(
						"You don't have enough coins to buy that.");
				return false;
			}
			if (freeSlots <= 0 && !(stackable
					&& player.getItemAssistant().playerHasItem(itemId, 1))) {
				player.getPacketSender().sendMessage(
						"You don't have enough space in your inventory.");
				return false;
			}
			player.getItemAssistant().deleteItem(CURRENCY, (int) totalValue);
			if (!player.getItemAssistant().addItem(itemId, amount)) {
				player.getItemAssistant().addItem(CURRENCY, (int) totalValue);
				return false;
			}
			stock.amounts[slot] -= amount;
			stock.restockAt[slot] = currentTick
					+ stock.definition.restockTicks();
		}
		player.getPacketSender().sendMessage("You bought " + amount + " "
				+ itemName(itemId) + " for " + (unitPrice * amount)
				+ " coins.");
		player.getItemAssistant().resetItems(3823);
		render(player, definition);
		return true;
	}

	/** Exact stock-capped sell path for one live player. */
	public boolean sell(Player player, int itemId, int amount) {
		if (!inScriptShop(player)) {
			return false;
		}
		int unNoted = unNoted(itemId);
		Stock stock;
		ShopDefinition definition = ShopDefinitionRegistry
				.get(player.scriptShopId);
		synchronized (this) {
			if (definition == null || (stock = stocks.get(definition.id())) == null
					|| !definition.buys()) {
				player.getPacketSender().sendMessage(
						"You can't sell " + itemName(itemId)
								+ " to this store.");
				return false;
			}
			int slot = stock.slotOf(unNoted);
			if (slot < 0) {
				player.getPacketSender().sendMessage(
						"You can't sell " + itemName(itemId)
								+ " to this store.");
				return false;
			}
			if (stock.amounts[slot] >= stock.declared[slot]) {
				player.getPacketSender().sendMessage(
						"This shop is out of space!");
				return false;
			}
			int inventoryAmount = player.getItemAssistant()
					.getItemAmount(itemId);
			if (inventoryAmount <= 0) {
				return false;
			}
			int capacity = stock.declared[slot] - stock.amounts[slot];
			if (amount > capacity) {
				amount = capacity;
			}
			if (amount > inventoryAmount) {
				amount = inventoryAmount;
			}
			if (amount <= 0) {
				return false;
			}
			long totalValue = (long) stock.definition.items().get(slot).price()
					* 85L / 100L * (long) amount;
			if (totalValue > Integer.MAX_VALUE) {
				totalValue = Integer.MAX_VALUE;
			}
			if (!player.getItemAssistant().playerHasItem(itemId, amount)) {
				return false;
			}
			player.getItemAssistant().deleteItem(itemId, amount);
			if (!player.getItemAssistant().addItem(CURRENCY,
					(int) totalValue)) {
				player.getItemAssistant().addItem(itemId, amount);
				return false;
			}
			stock.amounts[slot] += amount;
			stock.restockAt[slot] = currentTick
					+ stock.definition.restockTicks();
		}
		player.getPacketSender().sendMessage("You sold " + amount + " "
				+ itemName(itemId) + " for " + ((long) stockPrice(definition,
						unNoted) * 85L / 100L * amount) + " coins.");
		player.getItemAssistant().resetItems(3823);
		render(player, definition);
		return true;
	}

	/** Buy-price message shown when a player clicks a shop item. */
	public void buyPriceMessage(Player player, int itemId) {
		if (!inScriptShop(player)) {
			return;
		}
		ShopDefinition definition = ShopDefinitionRegistry
				.get(player.scriptShopId);
		int price = itemPrice(definition, itemId);
		if (price <= 0) {
			player.getPacketSender().sendMessage(
					"You can't buy that right now!");
			return;
		}
		player.getPacketSender().sendMessage(itemName(itemId)
				+ ": currently costs " + price + " coins.");
	}

	/** Sell-price message shown when a player clicks a shop item. */
	public void sellPriceMessage(Player player, int itemId) {
		if (!inScriptShop(player)) {
			return;
		}
		ShopDefinition definition = ShopDefinitionRegistry
				.get(player.scriptShopId);
		if (definition == null || !definition.buys()) {
			player.getPacketSender().sendMessage(
					"You can't sell items to this store.");
			return;
		}
		int price = itemPrice(definition, unNoted(itemId));
		if (price <= 0) {
			player.getPacketSender().sendMessage(
					"You can't sell items to this store.");
			return;
		}
		player.getPacketSender().sendMessage(itemName(itemId)
				+ ": shop will buy for " + (price * 85L / 100L) + " coins.");
	}

	private boolean inScriptShop(Player player) {
		return player != null && player.isShopping
				&& player.scriptShopId != null
				&& ScriptEncounterService.isAuthoritativeLive(player, true);
	}

	private static int itemPrice(ShopDefinition definition, int itemId) {
		if (definition == null) {
			return 0;
		}
		for (ShopItemDefinition item : definition.items()) {
			if (item.itemId() == itemId) {
				return item.price();
			}
		}
		return 0;
	}

	private long stockPrice(ShopDefinition definition, int itemId) {
		return itemPrice(definition, itemId);
	}

	private synchronized Stock stock(ShopDefinition definition) {
		Stock stock = stocks.get(definition.id());
		if (stock == null) {
			stock = new Stock(definition);
			stocks.put(definition.id(), stock);
		}
		return stock;
	}

	private static boolean isStackable(int itemId) {
		try {
			ItemDefinition definition = ItemDefinition.lookup(itemId);
			return definition != null && definition.isStackable();
		} catch (RuntimeException unavailable) {
			return false;
		}
	}

	private static int unNoted(int itemId) {
		try {
			if (ItemDefinition.exists(itemId)) {
				ItemDefinition definition = ItemDefinition.lookup(itemId);
				if (definition.isNote()) {
					return ItemDefinition.noteToItem(itemId);
				}
			}
			// Classic noted-pair heuristic (same name as id-1), but only when
			// both ids are real definitions with non-empty names. Hermetic
			// fixtures leave names unset; itemName() then falls back to
			// "item" for every missing name and would falsely un-note.
			if (itemId - 1 < 0 || !ItemDefinition.exists(itemId)
					|| !ItemDefinition.exists(itemId - 1)) {
				return itemId;
			}
			String name = ItemDefinition.lookup(itemId).getName();
			String previous = ItemDefinition.lookup(itemId - 1).getName();
			if (name != null && !name.isEmpty()
					&& name.equalsIgnoreCase(previous)) {
				return itemId - 1;
			}
		} catch (RuntimeException unavailable) {
			// Noted-name comparison unavailable; keep the raw id.
		}
		return itemId;
	}

	private static String itemName(int itemId) {
		try {
			ItemDefinition definition = ItemDefinition.lookup(itemId);
			String name = definition == null ? null : definition.getName();
			return name == null || name.isEmpty() ? "item" : name.toLowerCase();
		} catch (RuntimeException unavailable) {
			return "item";
		}
	}

	/** Mutable game-cycle-owned stock of one scripted shop definition. */
	private static final class Stock {
		private final ShopDefinition definition;
		private final int[] declared;
		private final int[] amounts;
		private final long[] restockAt;

		Stock(ShopDefinition definition) {
			this.definition = definition;
			int size = Math.min(MAX_SHOP_SLOTS, definition.items().size());
			this.declared = new int[size];
			this.amounts = new int[size];
			this.restockAt = new long[size];
			for (int index = 0; index < size; index++) {
				this.declared[index] = definition.items().get(index).amount();
				this.amounts[index] = this.declared[index];
			}
		}

		int slotOf(int itemId) {
			for (int index = 0; index < definition.items().size(); index++) {
				if (definition.items().get(index).itemId() == itemId) {
					return index;
				}
			}
			return -1;
		}

		int activeCount() {
			int count = 0;
			for (int amount : amounts) {
				if (amount > 0) {
					count++;
				}
			}
			return count;
		}
	}

}
