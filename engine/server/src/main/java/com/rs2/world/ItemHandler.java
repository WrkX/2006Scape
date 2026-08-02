package com.rs2.world;

import com.rs2.game.items.DeprecatedItems;
import com.rs2.game.items.GroundItem;
import com.rs2.game.items.ItemDefinitions;
import com.rs2.game.items.ItemConstants;
import com.rs2.game.players.Client;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.util.GameLogger;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles ground items
 **/

public class ItemHandler {

    public              List<GroundItem> items      = new ArrayList<GroundItem>();
    public static final int              HIDE_TICKS = 100;
	private final Map<ProjectionKey, ProjectedGroundItem> projections =
			new HashMap<ProjectionKey, ProjectedGroundItem>();

    public ItemHandler() {
    }

    /**
     * Adds item to list
     **/
    public void addItem(GroundItem item) {
        items.add(item);
    }

    /** Creates one encounter-owned identity per non-stackable item. */
    public synchronized com.rs2.script.world.ScriptGroundItemHandle createScriptGroundItems(
            Player owner, long encounterToken, int itemId, int amount, int x,
            int y, int plane, int privateTicks) {
        if (owner == null || itemId <= 0 || amount <= 0
                || x < 0 || x > 16383 || y < 0 || y > 16383
                || plane < 0 || plane > 3 || privateTicks < 0 || privateTicks > 100000) {
            return null;
        }
        List<GroundItem> created = new ArrayList<GroundItem>();
        boolean stackable;
        try {
            if (!org.apollo.cache.def.ItemDefinition.exists(itemId)) return null;
            org.apollo.cache.def.ItemDefinition definition =
                    org.apollo.cache.def.ItemDefinition.lookup(itemId);
            stackable = definition.isStackable();
        } catch (RuntimeException failure) {
            return null;
        }
        if ((!stackable && amount > 128) || (stackable && amount > 1_000_000)) return null;
        int count = stackable ? 1 : amount;
        for (int i = 0; i < count; i++) {
            GroundItem item = new GroundItem(itemId, x, y, plane,
                    stackable ? amount : 1, owner.playerId, privateTicks,
                    owner.playerName, owner);
            // Attached encounter rewards remain private until claimed or the
            // encounter closes; only detach() arms a private TTL.
            item.configureScript(encounterToken, true, false, 0);
            items.add(item);
            created.add(item);
        }
		if (!created.isEmpty()) reconcileProjection(owner, created.get(0).getItemId(),
				x, y, plane, false);
        return new com.rs2.script.world.ScriptGroundItemHandle(this, created);
    }

    public synchronized boolean containsExact(GroundItem item) {
        return item != null && items.contains(item) && !item.isClaimed();
    }

    public synchronized boolean removeExact(GroundItem item) {
        if (!containsExact(item)) return false;
        ArrayList<GroundItem> exact = new ArrayList<GroundItem>();
        exact.add(item);
        return removePrivateScriptItems(exact);
    }

    public synchronized boolean removeExact(List<GroundItem> exactItems) {
        if (exactItems == null || exactItems.isEmpty()) return false;
        for (GroundItem item : exactItems) if (!containsExact(item)) return false;
        return removePrivateScriptItems(exactItems);
    }

    public synchronized boolean detachExact(GroundItem item, int privateTicks) {
        if (!containsExact(item) || privateTicks < 1 || privateTicks > 1000) return false;
        item.configureScript(item.getEncounterToken(), true, true, privateTicks);
		reconcileProjection(item.getPrivateOwner(), item.getItemId(), item.getItemX(),
				item.getItemY(), item.getItemH(), false);
        return true;
    }

    public synchronized boolean detachExact(List<GroundItem> exactItems, int privateTicks) {
        if (exactItems == null || exactItems.isEmpty() || privateTicks < 1 || privateTicks > 1000) return false;
        for (GroundItem item : exactItems) if (!containsExact(item)) return false;
        HashMap<ProjectionKey, ProjectionKey> affected = new HashMap<ProjectionKey, ProjectionKey>();
        for (GroundItem item : exactItems) {
            item.configureScript(item.getEncounterToken(), true, true, privateTicks);
			ProjectionKey key = keyFor(item.getPrivateOwner(), item);
			affected.put(key, key);
        }
        reconcile(affected, false);
        return true;
    }

    public synchronized void closeEncounterRewards(long encounterToken) {
        ArrayList<GroundItem> owned = new ArrayList<GroundItem>();
        for (GroundItem item : items) {
            if (item != null && item.getEncounterToken() == encounterToken
                    && !item.isDetached()) owned.add(item);
        }
        if (!owned.isEmpty()) removeExact(owned);
    }

    /**
     * Removes item from list
     **/
    public void removeItem(GroundItem item) {
        items.remove(item);
    }

    /**
     * Item amount
     **/

    public int itemAmount(String name, int itemId, int itemX, int itemY) {
        for (GroundItem i : items) {
            if (i.getName().equalsIgnoreCase(name)) {
                if (i.getItemId() == itemId && i.getItemX() == itemX && i.getItemY() == itemY) {
                    return i.getItemAmount();
                }
            }
        }
        return 0;
    }

    /**
     * Item exists
     **/
    public boolean itemExists(int itemId, int itemX, int itemY) {
        for (GroundItem i : items) {
            if (i.getItemId() == itemId && i.getItemX() == itemX && i.getItemY() == itemY) {
                return true;
            }
        }
        if (GlobalDropsHandler.itemExists(itemId, itemX, itemY, true)) {
            return true;
        }
        return false;
    }

    /**
     * Resolves the exact currently-visible creation at a tile. Private
     * identities belonging to the player take precedence over public
     * identities; the oldest creation token deterministically breaks ties.
     */
    public synchronized GroundItemRef resolveVisibleGroundItem(Player player,
            int itemId, int itemX, int itemY, int plane) {
        GroundItem privateMatch = null;
        GroundItem publicMatch = null;
        for (GroundItem item : items) {
			if (item == null || item.isClaimed() || item.getItemId() != itemId
                    || item.getItemX() != itemX || item.getItemY() != itemY
                    || item.getItemH() != plane) {
                continue;
            }
            if (item.isPrivateTo(player)) {
                if (privateMatch == null || item.getCreationToken()
                        < privateMatch.getCreationToken()) {
                    privateMatch = item;
                }
            } else if (!item.isScriptPrivate() && item.hideTicks <= 0
                    && (publicMatch == null || item.getCreationToken()
                            < publicMatch.getCreationToken())) {
                publicMatch = item;
            }
        }
        if (privateMatch != null) {
            return new GroundItemRef(privateMatch, true);
        }
        GroundItemRef itemHandlerPublic = publicMatch == null ? null
                : new GroundItemRef(publicMatch, false);
        GroundItemRef configuredPublic =
                GlobalDropsHandler.resolveVisibleGroundItem(
                        itemId, itemX, itemY, plane);
        if (itemHandlerPublic == null) {
            return configuredPublic;
        }
        if (configuredPublic == null
                || itemHandlerPublic.getToken() < configuredPublic.getToken()) {
            return itemHandlerPublic;
        }
        return configuredPublic;
    }

    private GroundItemRef resolveVisibleGroundItem(Player player,
            GroundItemRef reference) {
        if (reference == null) {
            return null;
        }
        if (reference.getSource() == GroundItemRef.Source.GLOBAL_DROP) {
            return GlobalDropsHandler.resolveVisibleGroundItem(reference);
        }
        for (GroundItem item : items) {
            if (item != null && item == reference.backingItem()
                    && item.getCreationToken() == reference.getToken()
                    && (item.isPrivateTo(player) || (!item.isScriptPrivate() && item.hideTicks <= 0))) {
                return new GroundItemRef(item, item.isPrivateTo(player));
            }
        }
        return null;
    }

    /**
     * Revalidates and removes only the creation represented by {@code ref}.
     */
    public synchronized int consumeGroundItemExact(Player player,
            GroundItemRef ref, boolean addToInventory, int maximumDistance) {
        if (player == null || ref == null || player.heightLevel != ref.getPlane()
                || Math.max(Math.abs(player.absX - ref.getX()),
                        Math.abs(player.absY - ref.getY())) > maximumDistance) {
            return 0;
        }
		GroundItemRef selected = resolveVisibleGroundItem(player, ref.getItemId(),
				ref.getX(), ref.getY(), ref.getPlane());
		if (!ref.sameIdentity(selected)) return 0;
        if (ref.getSource() == GroundItemRef.Source.GLOBAL_DROP) {
            return GlobalDropsHandler.consumeGroundItemExact(player, ref,
                    addToInventory, maximumDistance);
        }
        GroundItemRef current = resolveVisibleGroundItem(player, ref);
        if (current == null || current.backingItem() != ref.backingItem()
                || current.getItemId() != ref.getItemId()
                || current.getX() != ref.getX() || current.getY() != ref.getY()
                || current.getPlane() != ref.getPlane()) {
            return 0;
        }
        GroundItem item = current.backingItem();
        int amount = item.getItemAmount();
        // Claim the exact identity before touching inventory. The claim is
        // rolled back if the all-or-nothing inventory transfer cannot fit.
        if (!item.claim()) {
            return 0;
        }
        if (addToInventory) {
            if (!player.getItemAssistant().specialCase(item.getItemId())) {
                if (!InventoryTransfer.addCompletely(player, item.getItemId(),
                        amount)) {
                    item.unclaim();
                    return 0;
                }
            }
        }
        if (item.isPrivateTo(player)) {
            removeControllersItem(item, player, item.getItemId(),
                    item.getItemX(), item.getItemY(), item.getItemAmount());
        } else {
            removeGlobalItem(item, item.getItemId(), item.getItemX(),
                    item.getItemY(), item.getItemAmount());
        }
        return addToInventory && player.getItemAssistant().specialCase(
                item.getItemId()) ? 0 : amount;
    }

    public void moveItem(GroundItem item, int itemX, int itemY) {
        if (items.remove(item)) {
            int oldX = item.itemX;
            int oldY = item.itemY;
			for (Player player : PlayerHandler.players) if (player != null) {
				reconcileProjection(player, item.getItemId(), oldX, oldY,
						item.getItemH(), false);
			}
            item.itemX = itemX;
            item.itemY = itemY;
            items.add(item);
			for (Player p: PlayerHandler.players) if (p != null) reconcileProjection(p,
					item.getItemId(), itemX, itemY, item.getItemH(), false);
        }
    }

    /**
     * Reloads any items if you enter a new region
     **/
	public synchronized void reloadItems(Player c) {
		if (c == null) return;
		Map<ProjectionKey, ProjectionKey> keys = new HashMap<ProjectionKey, ProjectionKey>();
		for (ProjectionKey key : projections.keySet()) if (key.owner == c) keys.put(key, key);
		for (GroundItem item : items) {
			if (item == null || item.isClaimed()) continue;
			if (item.isPrivateTo(c) || (!item.isScriptPrivate() && item.hideTicks <= 0)) {
				ProjectionKey key = new ProjectionKey(c, item.getItemId(), item.getItemX(),
						item.getItemY(), item.getItemH());
				keys.put(key, key);
			}
		}
		for (GroundItemRef item : GlobalDropsHandler.visibleGroundItems()) {
			ProjectionKey key = new ProjectionKey(c, item.getItemId(), item.getX(),
					item.getY(), item.getPlane());
			keys.put(key, key);
		}
		for (ProjectionKey key : keys.keySet()) reconcileProjection(c, key.itemId,
				key.x, key.y, key.plane, true);
	}

	public synchronized void configuredDropChanged(int itemId, int x, int y,
			int plane) {
		configuredDropChanged(itemId, x, y, plane, null);
	}

	public synchronized void configuredDropChanged(int itemId, int x, int y,
			int plane, Player excluded) {
		for (Player player : PlayerHandler.players) if (player != null
				&& player != excluded) {
			reconcileProjection(player, itemId, x, y, plane, false);
		}
	}

    public void process() {
        ArrayList<GroundItem> toRemove = new ArrayList<GroundItem>();
        for (int j = 0; j < items.size(); j++) {
            if (items.get(j) != null) {
                GroundItem i = items.get(j);
				if (i.isScriptPrivate() && i.isDetached()) {
					if (i.hideTicks > 0) i.hideTicks--;
					if (i.hideTicks == 0) toRemove.add(i);
					continue;
				}
                if (i.hideTicks > 0) {
                    i.hideTicks--;
                }
                if (i.hideTicks == 1) { // private TTL has elapsed
                    i.hideTicks = 0;
                    if (!i.isScriptPrivate()) {
                        createGlobalItem(i);
                        i.removeTicks = HIDE_TICKS;
                    }
                }
                if (i.removeTicks > 0) {
                    i.removeTicks--;
                }
                if (i.removeTicks == 1) {
                    i.removeTicks = 0;
                    toRemove.add(i);
                }
            }

        }

        removeExpiredItems(toRemove);
    }

	/** Removes a private exact identity for its owner only, then redraws any
	 * equal identity that remains visible at that tile. */
	private synchronized void removePrivateScriptItem(GroundItem item) {
		if (item == null || !items.contains(item)) return;
		ArrayList<GroundItem> exact = new ArrayList<GroundItem>();
		exact.add(item);
		removePrivateScriptItems(exact);
	}

	private boolean removePrivateScriptItems(List<GroundItem> exactItems) {
		HashMap<ProjectionKey, ProjectionKey> affected = new HashMap<ProjectionKey, ProjectionKey>();
		boolean removed = false;
		for (GroundItem item : exactItems) {
			if (item == null || !items.contains(item)) continue;
			ProjectionKey key = keyFor(item.getPrivateOwner(), item);
			affected.put(key, key);
			item.claim();
			item.setOwnerProjected(false);
			removed |= items.remove(item);
		}
		reconcile(affected, false);
		return removed;
	}

	private void removeExpiredItems(List<GroundItem> expired) {
		if (expired == null || expired.isEmpty()) return;
		HashMap<ProjectionKey, ProjectionKey> affected = new HashMap<ProjectionKey, ProjectionKey>();
		for (GroundItem item : expired) {
			if (item == null || !items.contains(item)) continue;
			if (item.isScriptPrivate()) {
				affected.put(keyFor(item.getPrivateOwner(), item),
						keyFor(item.getPrivateOwner(), item));
				item.claim();
				item.setOwnerProjected(false);
			} else {
				for (Player player : PlayerHandler.players) if (player != null) {
					ProjectionKey key = keyFor(player, item);
					affected.put(key, key);
				}
			}
			items.remove(item);
		}
		reconcile(affected, false);
	}

	private ProjectionKey keyFor(Player owner, GroundItem item) {
		return new ProjectionKey(owner, item.getItemId(), item.getItemX(),
				item.getItemY(), item.getItemH());
	}

	private void reconcile(Map<ProjectionKey, ProjectionKey> affected,
			boolean forceRebuild) {
		for (ProjectionKey key : affected.keySet()) reconcileProjection(key.owner,
				key.itemId, key.x, key.y, key.plane, forceRebuild);
	}

	private void reconcileProjection(Player owner, int itemId, int x, int y, int plane,
			boolean forceRebuild) {
		if (owner == null) return;
		ProjectionKey key = new ProjectionKey(owner, itemId, x, y, plane);
		ProjectedGroundItem previous = projections.get(key);
		GroundItemRef desired = resolveVisibleGroundItem(owner, itemId, x, y, plane);
		boolean same = previous != null && desired != null
				&& previous.matches(desired);
		if (same && !forceRebuild) return;
		boolean output = owner.getOutStream() != null && owner.getH() == plane
				&& owner.distanceToPoint(x, y) <= 60;
		if (previous != null && output) owner.getPacketSender().removeGroundItem(
				previous.itemId, x, y, previous.amount);
		for (GroundItem item : items) if (item != null && item.getItemId() == itemId
				&& item.getItemX() == x && item.getItemY() == y && item.getItemH() == plane
				&& item.getPrivateOwner() == owner) item.setOwnerProjected(false);
		if (desired == null || !output) {
			projections.remove(key);
			return;
		}
		owner.getPacketSender().createGroundItem(desired.getItemId(), desired.getX(),
				desired.getY(), desired.getAmount());
		projections.put(key, new ProjectedGroundItem(desired));
		GroundItem backing = desired.backingItem();
		if (backing != null && backing.isPrivateTo(owner)) backing.setOwnerProjected(true);
	}

	public synchronized long projectedTokenForTesting(Player owner, int itemId,
			int x, int y, int plane) {
		ProjectedGroundItem projected = projections.get(new ProjectionKey(owner,
				itemId, x, y, plane));
		return projected == null ? 0L : projected.token;
	}

	public synchronized void resetProjectionsForTesting() {
		projections.clear();
	}

    /**
     * Creates the ground item
     **/
    public int[][] brokenBarrows = { { 4708, 4860 }, { 4710, 4866 },
            { 4712, 4872 }, { 4714, 4878 }, { 4716, 4884 }, { 4720, 4896 },
            { 4718, 4890 }, { 4720, 4896 }, { 4722, 4902 }, { 4732, 4932 },
            { 4734, 4938 }, { 4736, 4944 }, { 4738, 4950 }, { 4724, 4908 },
            { 4726, 4914 }, { 4728, 4920 }, { 4730, 4926 }, { 4745, 4956 },
            { 4747, 4926 }, { 4749, 4968 }, { 4751, 4994 }, { 4753, 4980 },
            { 4755, 4986 }, { 4757, 4992 }, { 4759, 4998 } };

    public void createGroundItem(Player c, int itemId, int itemX, int itemY,
            int itemAmount, int playerId) {
        createGroundItemRef(c, itemId, itemX, itemY, itemAmount, playerId);
    }

    public GroundItemRef createGroundItemRef(Player c, int itemId, int itemX,
            int itemY, int itemAmount, int playerId) {
        GroundItemRef firstCreated = null;
        if (itemId > 0) {
            if (itemId >= 2412 && itemId <= 2414) {
                c.getPacketSender().sendMessage("The cape vanishes as it touches the ground.");
                return null;
            }
            if (itemId >= 4708 && itemId <= 4759) {
                for (int[] brokenBarrow : brokenBarrows) {
                    if (brokenBarrow[0] == itemId) {
                        itemId = brokenBarrow[1];
                        break;
                    }
                }
            }
            if (!org.apollo.cache.def.ItemDefinition.lookup(itemId).isStackable() && itemAmount > 0) {
                for (int j = 0; j < itemAmount; j++) {
                    Player owner = playerId >= 0 && playerId < PlayerHandler.players.length
                            ? PlayerHandler.players[playerId] : c;
                    GroundItem item = new GroundItem(itemId, itemX, itemY,
                            c.getH(), 1, c.playerId, HIDE_TICKS,
                            owner == null ? c.playerName : owner.playerName, owner);
                    addItem(item);
                    if (firstCreated == null) {
                        firstCreated = new GroundItemRef(item, true);
                    }
                    String itemName = DeprecatedItems.getItemName(itemId).toLowerCase();
                    if (c.isDead == false && itemId != 526) {
                        if (c.getPlayerAssistant().isPlayer()) {
                            GameLogger.writeLog(c.playerName, "dropitem", c.playerName + " dropped " + itemAmount + " " + itemName + " absX: " + c.absX + " absY: " + c.absY + "");
                        }
                    }
                }
            } else {
                Player owner = playerId >= 0 && playerId < PlayerHandler.players.length
                        ? PlayerHandler.players[playerId] : c;
                GroundItem item = new GroundItem(itemId, itemX, itemY,
                        c.getH(), itemAmount, c.playerId, HIDE_TICKS,
                        owner == null ? c.playerName : owner.playerName, owner);
                addItem(item);
                firstCreated = new GroundItemRef(item, true);
                String itemName = DeprecatedItems.getItemName(itemId).toLowerCase();
                if (c.isDead == false && itemId != 526) {
                    if (c.getPlayerAssistant().isPlayer()) {
                        GameLogger.writeLog(c.playerName, "dropitem", c.playerName + " dropped " + itemAmount + " " + itemName + " absX: " + c.absX + " absY: " + c.absY + "");
                    }
                }
            }
            reloadItems(c);
        }
        return firstCreated;
    }

    /**
     * Shows items for everyone who is within 60 squares
     **/
    public void createGlobalItem(GroundItem i) {
        if (!itemExists(i.getItemId(), i.getItemX(), i.getItemY())) {
            addItem(i);
        }
		for (Player p : PlayerHandler.players) if (p != null) reconcileProjection(p,
				i.getItemId(), i.getItemX(), i.getItemY(), i.getItemH(), false);
    }

    /**
     * Removing the ground item
     **/

    public int removeGroundItem(Player c, int itemId, int itemX, int itemY, boolean add) {
        for (GroundItem i : items) {
            if (i.getItemId() == itemId && i.getItemX() == itemX
                    && i.getItemY() == itemY) {
                if (i.isPrivateTo(c)) {
                    if (add) {
                        if (!c.getItemAssistant().specialCase(itemId)) {
                            if (InventoryTransfer.addCompletely(c, i.getItemId(),
                                    i.getItemAmount())) {
                                removeControllersItem(i, c, i.getItemId(),
                                        i.getItemX(), i.getItemY(),
                                        i.getItemAmount());
                                return i.getItemAmount();
                            }
                        } else {
                            removeControllersItem(i, c, i.getItemId(),
                                    i.getItemX(), i.getItemY(),
                                    i.getItemAmount());
                            return 0;
                        }
                    } else {
                        removeControllersItem(i, c, i.getItemId(),
                                i.getItemX(), i.getItemY(), i.getItemAmount());
                        return i.getItemAmount();
                    }
                } else if (i.hideTicks <= 0) {
                    if (add) {
                        if (InventoryTransfer.addCompletely(c, i.getItemId(),
                                i.getItemAmount())) {
                            removeGlobalItem(i, i.getItemId(), i.getItemX(),
                                    i.getItemY(), i.getItemAmount());
                            return i.getItemAmount();
                        }
                    } else {
                        removeGlobalItem(i, i.getItemId(), i.getItemX(),
                                i.getItemY(), i.getItemAmount());
                        return i.getItemAmount();
                    }
                }
            }
        }
        return 0;
    }

    /**
     * Remove item for just the item controller (item not global yet)
     **/

    public void removeControllersItem(GroundItem i, Player c, int itemId,
                                      int itemX, int itemY, int itemAmount) {
		if (i != null && i.isScriptPrivate()) {
			removePrivateScriptItem(i);
			return;
		}
        c.getPacketSender().removeGroundItem(itemId, itemX, itemY,
                itemAmount);
        removeItem(i);
    }

    /**
     * Remove item for everyone within 60 squares
     **/

    public void removeGlobalItem(GroundItem i, int itemId, int itemX,
                                 int itemY, int itemAmount) {
		if (!items.remove(i)) return;
		for (Player p : PlayerHandler.players) if (p != null) reconcileProjection(p,
				itemId, itemX, itemY, i.getItemH(), false);
    }

	private static final class ProjectionKey {
		final Player owner;
		final int itemId, x, y, plane;
		ProjectionKey(Player owner, int itemId, int x, int y, int plane) {
			this.owner = owner; this.itemId = itemId; this.x = x; this.y = y;
			this.plane = plane;
		}
		@Override public int hashCode() {
			int result = System.identityHashCode(owner);
			result = 31 * result + itemId; result = 31 * result + x;
			result = 31 * result + y; return 31 * result + plane;
		}
		@Override public boolean equals(Object value) {
			if (!(value instanceof ProjectionKey)) return false;
			ProjectionKey other = (ProjectionKey) value;
			return owner == other.owner && itemId == other.itemId && x == other.x
					&& y == other.y && plane == other.plane;
		}
	}

	private static final class ProjectedGroundItem {
		final GroundItemRef.Source source;
		final long token;
		final long spawnGeneration;
		final Object backing;
		final int itemId, amount;
		ProjectedGroundItem(GroundItemRef reference) {
			source = reference.getSource(); token = reference.getToken();
			spawnGeneration = reference.getSpawnGeneration();
			backing = reference.backingIdentity();
			itemId = reference.getItemId(); amount = reference.getAmount();
		}
		boolean matches(GroundItemRef reference) {
			return source == reference.getSource() && token == reference.getToken()
					&& spawnGeneration == reference.getSpawnGeneration()
					&& backing == reference.backingIdentity();
		}
	}
}
