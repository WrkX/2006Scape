package com.rs2.script;

import java.lang.reflect.Field;

import com.rs2.Constants;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.npcs.NpcList;
import com.rs2.game.players.Player;
import com.rs2.game.players.Client;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.util.Stream;
import com.rs2.world.clip.Region;
import com.rs2.world.clip.RegionFactory;
import org.apollo.util.security.IsaacRandom;
import org.apollo.cache.def.ObjectDefinition;
import org.apollo.cache.def.ItemDefinition;

/** Lightweight WP5 fixture that does not start Graal/Truffle. */
final class Wp5PlayerSupport {
    static final int X = 3200, Y = 3200;
    private static final int CUSTOM_ITEM_ID = 35000;
    private static final int CUSTOM_OBJECT_ID = 35000;
    private static final int CUSTOM_NPC_ID = 35000;
    static Player player(int slot) throws Exception {
        ensureObjectDefinitions();
		ensureItemDefinitions();
        ScriptEncounterService service = ScriptEncounterService.getInstance();
        service.resetForTesting();
        service.onGenerationPublished(1L);
        Field regions = RegionFactory.class.getDeclaredField("regions");
        regions.setAccessible(true);
        regions.set(null, new Region[] {
                new Region(Region.getRegionId(X, Y), false),
                new Region(Region.getRegionId(X - 2, Y - 2), false),
                new Region(Region.getRegionId(X - 2, Y + 2), false),
                new Region(Region.getRegionId(X + 2, Y - 2), false),
                new Region(Region.getRegionId(X + 2, Y + 2), false) });
        Player player = new RecordingPlayer(slot);
        player.playerName = "wp5-player-" + slot;
        player.initialized = true;
        player.isActive = true;
        player.disconnected = false;
        player.isDead = false;
        player.respawnTimer = 0;
        player.absX = X;
        player.absY = Y;
        player.heightLevel = 0;
        player.mapRegionX = (X >> 3) - 6;
        player.mapRegionY = (Y >> 3) - 6;
        player.currentX = X - player.mapRegionX * 8;
        player.currentY = Y - player.mapRegionY * 8;
        player.teleportToX = -1;
        player.teleportToY = -1;
        player.outStream = new Stream(new byte[Constants.BUFFER_SIZE]);
        player.outStream.packetEncryption = new IsaacRandom(new int[4]);
        PlayerHandler.players[slot] = player;
        return player;
    }

	/** Adds a second live player without resetting the active encounter fixture. */
	static Player additionalPlayer(int slot) throws Exception {
		Player player = new RecordingPlayer(slot);
		player.playerName = "wp5-player-" + slot;
		player.initialized = true;
		player.isActive = true;
		player.disconnected = false;
		player.isDead = false;
		player.absX = X;
		player.absY = Y;
		player.heightLevel = 0;
		player.mapRegionX = (X >> 3) - 6;
		player.mapRegionY = (Y >> 3) - 6;
		player.currentX = X - player.mapRegionX * 8;
		player.currentY = Y - player.mapRegionY * 8;
		player.teleportToX = -1;
		player.teleportToY = -1;
		player.outStream = new Stream(new byte[Constants.BUFFER_SIZE]);
		player.outStream.packetEncryption = new IsaacRandom(new int[4]);
		PlayerHandler.players[slot] = player;
		return player;
	}

    /** The production cache is intentionally absent in the lightweight gate; provide
     * definition-backed IDs so strict object validation remains exercised. */
    public static synchronized void ensureObjectDefinitions() {
        ObjectDefinition[] existing = ObjectDefinition.getDefinitions();
        int required = 7000;
        int length = existing == null ? required : Math.max(required, existing.length);
        ObjectDefinition[] definitions = existing;
        boolean missing = existing == null || existing.length < required;
        if (!missing) {
            for (int id : new int[] {2213, 2214, 2222, 2223, 2230}) {
                if (existing[id] == null) { missing = true; break; }
            }
        }
        if (missing) {
            definitions = new ObjectDefinition[length];
            for (int id = 0; id < length; id++) {
                definitions[id] = existing != null && id < existing.length && existing[id] != null
                        ? existing[id] : new ObjectDefinition(id);
            }
            ObjectDefinition.init(definitions);
        }
		ObjectDefinition.lookup(2213).setWidth(2);
		ObjectDefinition.lookup(2213).setLength(1);
		ObjectDefinition.lookup(2213).setSolid(true);
		ObjectDefinition.lookup(2213).setImpenetrable(true);
		ObjectDefinition.lookup(2214).setWidth(1);
		ObjectDefinition.lookup(2214).setLength(1);
		ObjectDefinition.lookup(2214).setSolid(true);
		ObjectDefinition.lookup(2214).setImpenetrable(false);
		ensureCustomNamespaceObject();
    }

	public static synchronized void ensureItemDefinitions() throws Exception {
		ItemDefinition[] existing = ItemDefinition.getDefinitions();
		int required = requiredItemDefinitionLength();
		if (existing != null && existing.length >= required
				&& existing[995] != null && existing[536] != null
				&& existing[1387] != null && existing[3144] != null
				&& existing[317] != null && existing[1265] != null
				&& existing[315] != null && existing[7954] != null
				&& existing[994] == null) {
			ensureCustomNamespaceItem();
			return;
		}
		ItemDefinition[] definitions = new ItemDefinition[required];
		for (int id : CONTENT_ITEM_IDS) {
			ItemDefinition definition = new ItemDefinition(id);
			definition.setStackable(id == 995 || id == 560 || id == 565
					|| id == 1387);
			definitions[id] = definition;
		}
		Field field = ItemDefinition.class.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, definitions);
		ensureCustomNamespaceItem();
	}

	private static int requiredItemDefinitionLength() {
		int maxId = 3199;
		for (int id : CONTENT_ITEM_IDS) {
			if (id > maxId) {
				maxId = id;
			}
		}
		return maxId + 1;
	}

	/** Every item id referenced by the compiled content modules. */
	private static final int[] CONTENT_ITEM_IDS = {
			526, 536, 560, 565, 577, 579, 995, 1079, 1127, 1147, 1149,
			1163, 1215, 1305, 1387,
			// Dragon Island scripted shop stock.
			372, 379, 590, 954, 1540, 2434, 3144,
			// Woodcutting gathering resources (bronze axe, logs, oak logs).
			1351, 1511, 1521,
			// Mining gathering resources (bronze pickaxe, copper ore).
			1265, 436,
			// Fishing gathering resources (small net, raw shrimps).
			303, 317,
			// Cooking shrimp port (cooked / burnt shrimp, cooking gauntlets).
			315, 7954, 775
	};

	/**
	 * Loads the production {@code data/cfg/npc.json} definition list so the
	 * compiled area content passes the strict definition-backed spawn
	 * validation and the area runtime can allocate real NPC spawns. The
	 * test working directory is the server module, matching the production
	 * loader path.
	 */
	public static synchronized void ensureNpcDefinitions() {
		if (!NpcHandler.hasNpcDefinitions()) {
			// The NpcHandler() constructor already loads npc.json into the
			// static table; calling loadNPCList() a second time would double-load
			// and fill the table to capacity, leaving no free slot for the custom
			// namespace id below.
			new NpcHandler();
		}
		ensureCustomNamespaceNpc();
	}

	/**
	 * Ensures custom-namespace ids used by overlay ports exist in the loaded
	 * cache definitions for hermetic tests.
	 */
	public static synchronized void ensureCustomNamespaceDefinitions()
			throws Exception {
		ensureItemDefinitions();
		ensureObjectDefinitions();
		ensureNpcDefinitions();
	}

	private static void ensureCustomNamespaceItem() throws Exception {
		ItemDefinition[] items = ItemDefinition.getDefinitions();
		if (items != null && items.length > CUSTOM_ITEM_ID
				&& items[CUSTOM_ITEM_ID] != null) {
			return;
		}
		int length = Math.max(items == null ? 0 : items.length,
				CUSTOM_ITEM_ID + 1);
		ItemDefinition[] expanded = new ItemDefinition[length];
		if (items != null) {
			System.arraycopy(items, 0, expanded, 0, items.length);
		}
		if (expanded[CUSTOM_ITEM_ID] == null) {
			expanded[CUSTOM_ITEM_ID] = new ItemDefinition(CUSTOM_ITEM_ID);
			expanded[CUSTOM_ITEM_ID].setName("Cache bronze sword");
		}
		setItemDefinitions(expanded);
	}

	private static void ensureCustomNamespaceObject() {
		ObjectDefinition[] objects = ObjectDefinition.getDefinitions();
		if (objects != null && objects.length > CUSTOM_OBJECT_ID
				&& objects[CUSTOM_OBJECT_ID] != null) {
			return;
		}
		int length = Math.max(objects == null ? 0 : objects.length,
				CUSTOM_OBJECT_ID + 1);
		ObjectDefinition[] expanded = new ObjectDefinition[length];
		if (objects != null) {
			System.arraycopy(objects, 0, expanded, 0, objects.length);
		}
		if (expanded[CUSTOM_OBJECT_ID] == null) {
			expanded[CUSTOM_OBJECT_ID] =
					new ObjectDefinition(CUSTOM_OBJECT_ID);
			expanded[CUSTOM_OBJECT_ID].setName("Cache signpost");
		}
		try {
			setObjectDefinitions(expanded);
		} catch (Exception e) {
			throw new IllegalStateException(
					"failed to install custom object definition", e);
		}
	}

	private static void ensureCustomNamespaceNpc() {
		if (NpcHandler.hasNpcDefinition(CUSTOM_NPC_ID)) {
			return;
		}
		// Insert directly into the static NpcList table at the first free slot.
		// Constructing a full NpcHandler() here would clear the shared static
		// NpcList[]/npcs[] and reload production spawn/drop data from disk,
		// breaking test hermeticity.
		for (int index = 0; index < NpcHandler.maxListedNPCs; index++) {
			if (NpcHandler.NpcList[index] == null) {
				NpcList entry = new NpcList(CUSTOM_NPC_ID);
				entry.npcName = "Cache guard";
				entry.npcCombat = 1;
				entry.npcHealth = 10;
				NpcHandler.NpcList[index] = entry;
				return;
			}
		}
		throw new IllegalStateException("no free npc list slot for custom id "
				+ CUSTOM_NPC_ID);
	}

	private static void setItemDefinitions(ItemDefinition[] definitions)
			throws Exception {
		java.lang.reflect.Field field =
				ItemDefinition.class.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, definitions);
	}

	private static void setObjectDefinitions(ObjectDefinition[] definitions)
			throws Exception {
		java.lang.reflect.Field field =
				ObjectDefinition.class.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, definitions);
	}

	/**
	 * Ensures the map regions of the compiled Dragon Island fixture exist in
	 * the region table. The layered object transaction writes its collision
	 * through the region grid, so a missing region object would silently
	 * no-op the write and fail the apply verification. Production loads the
	 * regions from the cache map index; hermetic tests materialize empty
	 * region shells.
	 */
	public static synchronized void ensureAreaRegions() throws Exception {
		int[] regionIds = {
				Region.getRegionId(2830, 9630),
				Region.getRegionId(2870, 9670)
		};
		Field field = RegionFactory.class.getDeclaredField("regions");
		field.setAccessible(true);
		Region[] existing = (Region[]) field.get(null);
		for (int regionId : regionIds) {
			boolean present = false;
			if (existing != null) {
				for (Region region : existing) {
					if (region != null && region.id() == regionId) {
						present = true;
						break;
					}
				}
			}
			if (present) {
				continue;
			}
			Region[] updated = existing == null
					? new Region[] { new Region(regionId, false) }
					: java.util.Arrays.copyOf(existing,
							existing.length + 1);
			updated[updated.length - 1] = new Region(regionId, false);
			field.set(null, updated);
			existing = updated;
		}
	}
    static ScriptedPlayer scripted(Player player) { return new ScriptedPlayer(player, 1L); }
	static RecordingPlayer recording(Player player) { return (RecordingPlayer) player; }
	static final class RecordingPlayer extends Client {
		int flushCount;
		RecordingPlayer(int slot) { super(null, slot); }
		@Override public void flushOutStream() {
			flushCount++;
			if (outStream != null) outStream.currentOffset = 0;
		}
		@Override public void updateWalkEntities() { }
		void clearPackets() { flushCount = 0; if (outStream != null) outStream.currentOffset = 0; }
	}
    static void cleanup(Player player) {
        if (player != null && player.playerId >= 0 && player.playerId < PlayerHandler.players.length)
            PlayerHandler.players[player.playerId] = null;
        ScriptEncounterService.getInstance().resetForTesting();
    }
}
