package com.rs2.game.items;

import com.rs2.game.players.Player;
import com.rs2.world.GroundIdentityTokens;

public class GroundItem {

	public int itemId, itemX, itemY, itemH, itemAmount, itemController, hideTicks, removeTicks;
	
	public String ownerName;
	private final long creationToken;
	private final Player privateOwner;
	private long encounterToken;
	private boolean scriptPrivate;
	private boolean claimed;
	private boolean detached;
	private boolean ownerProjected;

	public GroundItem(int id, int x, int y, int h, int amount, int controller,
			int hideTicks, String name) {
		this(id, x, y, h, amount, controller, hideTicks, name, null);
	}

	public GroundItem(int id, int x, int y, int h, int amount, int controller,
			int hideTicks, String name, Player privateOwner) {
		creationToken = GroundIdentityTokens.next();
		itemId = id;
		itemX = x;
		itemY = y;
		itemH = h;
		itemAmount = amount;
		itemController = controller;
		this.hideTicks = hideTicks;
		ownerName = name;
		this.privateOwner = privateOwner;
	}

	public long getCreationToken() {
		return creationToken;
	}

	public boolean isPrivateTo(Player player) {
		return (hideTicks > 0 || scriptPrivate) && privateOwner != null
				&& privateOwner == player;
	}

	public synchronized void configureScript(long encounterToken,
			boolean scriptPrivate, boolean detached, int privateTicks) {
		this.encounterToken = encounterToken;
		this.scriptPrivate = scriptPrivate;
		this.detached = detached;
		this.hideTicks = Math.max(0, privateTicks);
		// Script-owned lifetimes are driven by hideTicks. Reusing the legacy
		// removeTicks counter would introduce an extra hard-coded expiry phase.
		this.removeTicks = 0;
	}

	public long getEncounterToken() { return encounterToken; }
	public boolean isScriptPrivate() { return scriptPrivate; }
	public boolean isDetached() { return detached; }
	public synchronized boolean isOwnerProjected() { return ownerProjected; }
	public synchronized void setOwnerProjected(boolean projected) {
		ownerProjected = projected;
	}
	public synchronized boolean isClaimed() { return claimed; }
	public synchronized boolean claim() {
		if (claimed) return false;
		claimed = true;
		return true;
	}

	/** Rolls back an optimistic claim when the subsequent inventory transfer fails. */
	public synchronized void unclaim() {
		claimed = false;
	}

	public boolean hasPrivateOwner() {
		return privateOwner != null;
	}

	public Player getPrivateOwner() {
		return privateOwner;
	}

	public int getItemId() {
		return itemId;
	}

	public int getItemX() {
		return itemX;
	}

	public int getItemY() {
		return itemY;
	}

	public int getItemH() {
		return itemH;
	}

	public int getItemAmount() {
		return itemAmount;
	}

	public int getItemController() {
		return itemController;
	}

	public String getName() {
		return ownerName;
	}

}
