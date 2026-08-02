package com.rs2.script.area;

/**
 * Immutable NPC spawn of one area definition.
 *
 * <p>The {@code key} is the stable per-area spawn identity used for exact
 * allocation bindings; the numeric npc id must be definition-backed. An
 * optional named WP2 drop table with a delivery policy is rolled exactly
 * once through the area-session RNG when the exact allocation dies; the
 * allocation respawns after {@code respawnTicks} game cycles. An optional
 * {@code openShop} binds the exact allocation's first click to one scripted
 * shop.
 */
public final class AreaNpcSpawn {

	private final String key;
	private final int npcId;
	private final int x;
	private final int y;
	private final int plane;
	private final int walkRadius;
	private final String direction;
	private final int respawnTicks;
	private final int hp;
	private final int maxHit;
	private final int attack;
	private final int defence;
	private final String dropTable;
	private final AreaDropPolicy dropPolicy;
	private final int privateTicks;
	private final boolean hasDropTable;
	private final String openShop;

	public AreaNpcSpawn(String key, int npcId, int x, int y, int plane,
			int walkRadius, String direction, int respawnTicks, int hp,
			int maxHit, int attack, int defence, String dropTable,
			AreaDropPolicy dropPolicy, int privateTicks, boolean hasDropTable,
			String openShop) {
		this.key = key;
		this.npcId = npcId;
		this.x = x;
		this.y = y;
		this.plane = plane;
		this.walkRadius = walkRadius;
		this.direction = direction;
		this.respawnTicks = respawnTicks;
		this.hp = hp;
		this.maxHit = maxHit;
		this.attack = attack;
		this.defence = defence;
		this.dropTable = dropTable;
		this.dropPolicy = dropPolicy;
		this.privateTicks = privateTicks;
		this.hasDropTable = hasDropTable;
		this.openShop = openShop;
	}

	public String key() {
		return key;
	}

	public int npcId() {
		return npcId;
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int plane() {
		return plane;
	}

	/** 0 = stationary; otherwise the legacy random-walk radius intent. */
	public int walkRadius() {
		return walkRadius;
	}

	/** Legacy facing direction, or {@code null} for random walk/stationary. */
	public String direction() {
		return direction;
	}

	public int respawnTicks() {
		return respawnTicks;
	}

	public int hp() {
		return hp;
	}

	public int maxHit() {
		return maxHit;
	}

	public int attack() {
		return attack;
	}

	public int defence() {
		return defence;
	}

	public boolean hasDropTable() {
		return hasDropTable;
	}

	/** Named WP2 drop table; valid only with {@link #hasDropTable()}. */
	public String dropTable() {
		return dropTable;
	}

	public AreaDropPolicy dropPolicy() {
		return dropPolicy;
	}

	/** Private TTL in game cycles; valid only for private delivery. */
	public int privateTicks() {
		return privateTicks;
	}

	/** Scripted shop id opened by this allocation's first click, or {@code null}. */
	public String openShop() {
		return openShop;
	}

	@Override
	public String toString() {
		return "spawn '" + key + "' (npc " + npcId + " at " + x + "," + y
				+ "," + plane + ")";
	}

}
