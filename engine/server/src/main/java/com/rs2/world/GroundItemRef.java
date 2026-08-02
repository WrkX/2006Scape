package com.rs2.world;

import com.rs2.game.items.GroundItem;

/**
 * Immutable receipt for one exact ground-item creation.
 */
public final class GroundItemRef {

	public enum Source {
		ITEM_HANDLER,
		GLOBAL_DROP
	}

	private final Source source;
	private final Object backing;
	private final long token;
	private final long spawnGeneration;
	private final int itemId;
	private final int x;
	private final int y;
	private final int plane;
	private final int amount;
	private final boolean privateToPlayer;

	GroundItemRef(GroundItem item, boolean privateToPlayer) {
		this(Source.ITEM_HANDLER, item, item.getCreationToken(), 0L,
				item.getItemId(), item.getItemX(), item.getItemY(),
				item.getItemH(), item.getItemAmount(), privateToPlayer);
	}

	GroundItemRef(GlobalDropsHandler.GlobalDrop drop) {
		this(Source.GLOBAL_DROP, drop, drop.getCreationToken(),
				drop.getSpawnGeneration(), drop.getId(), drop.getX(),
				drop.getY(), drop.getHeight(), drop.getAmount(), false);
	}

	private GroundItemRef(Source source, Object backing, long token,
			long spawnGeneration, int itemId, int x, int y, int plane,
			int amount, boolean privateToPlayer) {
		this.source = source;
		this.backing = backing;
		this.token = token;
		this.spawnGeneration = spawnGeneration;
		this.itemId = itemId;
		this.x = x;
		this.y = y;
		this.plane = plane;
		this.amount = amount;
		this.privateToPlayer = privateToPlayer;
	}

	GroundItem backingItem() {
		return source == Source.ITEM_HANDLER ? (GroundItem) backing : null;
	}

	GlobalDropsHandler.GlobalDrop backingGlobalDrop() {
		return source == Source.GLOBAL_DROP
				? (GlobalDropsHandler.GlobalDrop) backing : null;
	}

	Object backingIdentity() {
		return backing;
	}

	boolean sameIdentity(GroundItemRef other) {
		return other != null && source == other.source && backing == other.backing
				&& token == other.token && spawnGeneration == other.spawnGeneration
				&& itemId == other.itemId && x == other.x && y == other.y
				&& plane == other.plane && amount == other.amount
				&& privateToPlayer == other.privateToPlayer;
	}

	public Source getSource() {
		return source;
	}

	public long getToken() {
		return token;
	}

	public long getSpawnGeneration() {
		return spawnGeneration;
	}

	public int getItemId() {
		return itemId;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public int getPlane() {
		return plane;
	}

	public int getAmount() {
		return amount;
	}

	public boolean isPrivateToPlayer() {
		return privateToPlayer;
	}
}
