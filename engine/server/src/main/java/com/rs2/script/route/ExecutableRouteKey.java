package com.rs2.script.route;

/**
 * Immutable exact key of one executable route.
 *
 * <p>The canonical key string is the exact registration identity used by the
 * packet and command adapters, for example {@code "409/first"} for an object
 * action or {@code "encounter-warden"} for a command.
 */
public final class ExecutableRouteKey {

	private final RouteKind kind;
	private final String key;

	public ExecutableRouteKey(RouteKind kind, String key) {
		if (kind == null) {
			throw new IllegalArgumentException("route kind must not be null");
		}
		if (key == null || key.trim().isEmpty()) {
			throw new IllegalArgumentException("route key must not be empty");
		}
		this.kind = kind;
		this.key = key;
	}

	public static ExecutableRouteKey command(String command) {
		return new ExecutableRouteKey(RouteKind.COMMAND, command);
	}

	public static ExecutableRouteKey object(int objectId, String action) {
		return new ExecutableRouteKey(RouteKind.OBJECT,
				objectId + "/" + action);
	}

	/**
	 * Exact route of one generation-owned object projection: the projected
	 * object id and action at one canonical tile. A cache or legacy object
	 * with the same id/action at any other tile has no such key and falls
	 * through to the plain {@link #object(int, String)} lookup.
	 */
	public static ExecutableRouteKey objectAt(int objectId, String action,
			int x, int y, int plane) {
		return new ExecutableRouteKey(RouteKind.OBJECT,
				objectId + "/" + action + "@" + x + "," + y + "," + plane);
	}

	public static ExecutableRouteKey npc(int npcId, String action) {
		return new ExecutableRouteKey(RouteKind.NPC, npcId + "/" + action);
	}

	/**
	 * Exact route of one generation-owned area NPC allocation: the npc id,
	 * ordinal action, and the owning area spawn key. Only a live allocation
	 * of that exact spawn carries this key; an equal-id legacy NPC has no
	 * such key and falls through to the plain {@link #npc(int, String)}
	 * lookup.
	 */
	public static ExecutableRouteKey npcAllocated(int npcId, String action,
			String areaId, String spawnKey) {
		return new ExecutableRouteKey(RouteKind.NPC,
				npcId + "/" + action + "#" + areaId + "/" + spawnKey);
	}

	public static ExecutableRouteKey item(int itemId, String action) {
		return new ExecutableRouteKey(RouteKind.ITEM, itemId + "/" + action);
	}

	public static ExecutableRouteKey itemOnItem(int first, int second) {
		return new ExecutableRouteKey(RouteKind.ITEM_ON_ITEM,
				orderedPair(first, second));
	}

	public static ExecutableRouteKey itemOnObject(int itemId, int objectId) {
		return new ExecutableRouteKey(RouteKind.ITEM_ON_OBJECT,
				itemId + ":" + objectId);
	}

	public static ExecutableRouteKey itemOnNpc(int itemId, int npcId) {
		return new ExecutableRouteKey(RouteKind.ITEM_ON_NPC,
				itemId + ":" + npcId);
	}

	public static ExecutableRouteKey button(int buttonId) {
		return new ExecutableRouteKey(RouteKind.BUTTON,
				String.valueOf(buttonId));
	}

	public static ExecutableRouteKey itemOnGroundItem(int itemId, int groundItemId) {
		return new ExecutableRouteKey(RouteKind.ITEM_ON_GROUND_ITEM,
				itemId + ":" + groundItemId);
	}

	public static ExecutableRouteKey itemOnPlayer(int itemId) {
		return new ExecutableRouteKey(RouteKind.ITEM_ON_PLAYER,
				String.valueOf(itemId));
	}

	public static ExecutableRouteKey magicOnItem(int spellId, int itemId) {
		return new ExecutableRouteKey(RouteKind.MAGIC_ON_ITEM,
				spellId + ":" + itemId);
	}

	public static ExecutableRouteKey magicOnObject(int spellId, int objectId) {
		return new ExecutableRouteKey(RouteKind.MAGIC_ON_OBJECT,
				spellId + ":" + objectId);
	}

	public RouteKind kind() {
		return kind;
	}

	public String key() {
		return key;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ExecutableRouteKey)) {
			return false;
		}
		ExecutableRouteKey that = (ExecutableRouteKey) other;
		return kind == that.kind && key.equals(that.key);
	}

	@Override
	public int hashCode() {
		return 31 * kind.hashCode() + key.hashCode();
	}

	@Override
	public String toString() {
		return kind.name().toLowerCase(java.util.Locale.ROOT) + ":" + key;
	}

	private static String orderedPair(int first, int second) {
		return first <= second ? first + ":" + second : second + ":" + first;
	}

}
