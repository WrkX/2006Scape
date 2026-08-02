package com.rs2.script;

import java.lang.reflect.Field;

import com.rs2.Constants;
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
    private static synchronized void ensureObjectDefinitions() {
        ObjectDefinition[] existing = ObjectDefinition.getDefinitions();
        int required = 2231;
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
    }

	private static synchronized void ensureItemDefinitions() throws Exception {
		ItemDefinition[] existing = ItemDefinition.getDefinitions();
		if (existing != null && existing.length > 995 && existing[995] != null
				&& existing.length > 536 && existing[536] != null) return;
		int length = existing == null ? 996 : Math.max(996, existing.length);
		ItemDefinition[] definitions = new ItemDefinition[length];
		if (existing != null) System.arraycopy(existing, 0, definitions, 0, existing.length);
		definitions[995] = new ItemDefinition(995);
		definitions[995].setStackable(true);
		definitions[536] = new ItemDefinition(536);
		definitions[536].setStackable(false);
		Field field = ItemDefinition.class.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, definitions);
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
