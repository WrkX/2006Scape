package com.rs2.world;


import java.util.ArrayList;
import java.util.List;
import com.rs2.GameEngine;
import com.rs2.game.content.skills.core.Mining;
import com.rs2.game.content.skills.woodcutting.Woodcutting;
import com.rs2.game.objects.Objects;
import com.rs2.game.players.Client;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.util.Misc;
import com.rs2.world.clip.Region;

/**
 * @author Sanity
 */

public class ObjectHandler {

	public List<Objects> globalObjects = new ArrayList<Objects>();

	public static List<Objects> mapObjects = new ArrayList<Objects>();
	public static List<Objects> removedObjects = new ArrayList<Objects>();

	public ObjectHandler() {
		
	}
	
	public Objects getObjectByPosition(int x, int y) {
		Objects match = null;
		for (Objects object : globalObjects) {
			if (object.getObjectX() != x || object.getObjectY() != y) continue;
			if (match != null) return null;
			match = object;
		}
		return match;
	}

	public Objects getObjectByPosition(int x, int y, int plane, int type) {
		Objects match = null;
		int requestedSlot = sceneSlot(type);
		for (Objects object : globalObjects) {
			if (object.getObjectX() != x || object.getObjectY() != y
					|| object.getObjectHeight() != plane
					|| sceneSlot(object.getObjectType()) != requestedSlot) continue;
			if (match != null) return null;
			match = object;
		}
		return match;
	}

	    public void createAnObject(int id, int x, int y, int face) {
	        Objects OBJECT = new Objects(id, x, y, 0, face, 10, 0);
	        mutateGlobal(OBJECT);
	    }
		

	public void createAnObject(Player c, int id, int x, int y) {
		Objects OBJECT = new Objects(id, x, y, c.heightLevel, 0, 10, 0);
		mutateGlobal(OBJECT);
	}

	public void createAnObject(Player player, int id, int x, int y, int h, int face) {
		Objects OBJECT = new Objects(id, x, y, h, face, 10, 0);
		mutateGlobal(OBJECT);
	}
	
	public void createAnObject(Player player, int id, int x, int y, int h) {
		Objects OBJECT = new Objects(id, x, y, h, 0, 10, 0);
		mutateGlobal(OBJECT);
	}

	
	public void createAnObject(int id, int x, int y, int h, int face, int type) {
		Objects OBJECT = new Objects(id, x, y, h, face, type, 0);
		mutateGlobal(OBJECT);
	}

	public void createAnObject(int id, int x, int y) {
		Objects OBJECT = new Objects(id, x, y, 0, 0, 10, 0);
		mutateGlobal(OBJECT);
	}

	private void mutateGlobal(Objects object) {
		if (object.getObjectId() < 0) removeObject(object); else addObject(object);
	}

	/**
	 * Adds object to list
	 **/
	public void addObject(Objects object) {
		WorldObjectService.getInstance().applyGlobalAdd(this, object);
	}

	/**
	 * Removes object from list
	 **/
	public void removeObject(Objects object) {
		if (object == null) return;
		Objects backing = exactBacking(object);
		WorldObjectService.getInstance().applyGlobalRemove(this,
				backing == null ? object : backing);
	}

	void applyAuthoritativeAdd(Objects object) {
		globalObjects.add(object);
	}

	void applyAuthoritativeRemove(Objects object) {
		globalObjects.remove(object);
	}

	/**
	 * Does object exist
	 **/
	public Objects objectExists(int objectX, int objectY, int objectHeight) {
		for (Objects o : globalObjects) {
			if (o.getObjectX() == objectX && o.getObjectY() == objectY
					&& o.getObjectHeight() == objectHeight) {
				return o;
			}
		}
		return null;
	}

	private Objects exactBacking(Objects requested) {
		int requestedSlot = sceneSlot(requested.getObjectType());
		Objects match = null;
		for (Objects candidate : globalObjects) {
			if (candidate.getObjectX() != requested.getObjectX()
					|| candidate.getObjectY() != requested.getObjectY()
					|| candidate.getObjectHeight() != requested.getObjectHeight()
					|| sceneSlot(candidate.getObjectType()) != requestedSlot) continue;
			if (requested.getObjectId() >= 0 && (candidate.getObjectId()
					!= requested.getObjectId() || candidate.getObjectType()
					!= requested.getObjectType() || candidate.getObjectFace()
					!= requested.getObjectFace())) continue;
			if (match != null) return null;
			match = candidate;
		}
		return match;
	}

	private static int sceneSlot(int type) {
		if (type >= 0 && type <= 3) return 0;
		if (type >= 4 && type <= 8) return 1;
		return type == 22 ? 3 : 2;
	}

	/**
	 * Update objects when entering a new region or logging in
	 **/
	public void updateObjects(Player c) {
		for (Objects o : globalObjects) {
			if (c != null) {
				if (!WorldObjectService.getInstance().shouldProjectLower(c, o)) continue;
				if (c.heightLevel == 0 && o.objectTicks == 0 && c.distanceToPoint(o.getObjectX(), o.getObjectY()) <= 60) {
					if (Woodcutting.playerTrees(c, o.getObjectId()) || Mining.rockExists(o.getObjectId())) {
						c.getPacketSender().object(o.getObjectId(), o.getObjectX(), o.getObjectY(), 0, o.getObjectFace(), o.getObjectType());
					}
				}
				if (c.heightLevel == o.getObjectHeight() && !Woodcutting.playerTrees(c, o.getObjectId()) && !Mining.rockExists(o.getObjectId()) && o.objectTicks == 0 && c.distanceToPoint(o.getObjectX(), o.getObjectY()) <= 60) {
					c.getPacketSender().object(o.getObjectId(), o.getObjectX(), o.getObjectY(), c.heightLevel, o.getObjectFace(), o.getObjectType());
				}
			}
		}
	}

	/**
	 * Creates the object for anyone who is within 60 squares of the object
	 **/
	public void placeObject(Objects o) {
		WorldObjectService.getInstance().applyCacheMutation(o);
	}

	public void removeAllObjects(Objects o) {
		Objects backing = exactBacking(o);
		if (backing != null) WorldObjectService.getInstance()
				.applyGlobalRemove(this, backing);
	}

	public void process() {
		for (Objects o : new ArrayList<Objects>(globalObjects)) {
			if (o != null && o.objectTicks > 0 && --o.objectTicks == 0) {
					removeObject(o);
					if (isObelisk(o.objectId)) {
						int index = getObeliskIndex(o.objectId);
						if (activated[index]) {
							activated[index] = false;
							teleportObelisk(index);
						}
					}
			}
		}
	}

	public final int IN_USE_ID = 14825;

	public boolean isObelisk(int id) {
		for (int obeliskId : obeliskIds) {
			if (obeliskId == id) {
				return true;
			}
		}
		return false;
	}

	public int[] obeliskIds = { 14829, 14830, 111235, 14828, 14826, 14831 };
	public int[][] obeliskCoords = { { 3154, 3618 }, { 3225, 3665 },
			{ 3033, 3730 }, { 3104, 3792 }, { 2978, 3864 }, { 3305, 3914 } };
	public boolean[] activated = { false, false, false, false, false, false };

	public void startObelisk(int obeliskId) {
		int index = getObeliskIndex(obeliskId);
		if (index >= 0) {
			if (!activated[index]) {
				activated[index] = true;
				Objects obby1 = new Objects(14825, obeliskCoords[index][0],
						obeliskCoords[index][1], 0, -1, 10, 0);
				Objects obby2 = new Objects(14825, obeliskCoords[index][0] + 4,
						obeliskCoords[index][1], 0, -1, 10, 0);
				Objects obby3 = new Objects(14825, obeliskCoords[index][0],
						obeliskCoords[index][1] + 4, 0, -1, 10, 0);
				Objects obby4 = new Objects(14825, obeliskCoords[index][0] + 4,
						obeliskCoords[index][1] + 4, 0, -1, 10, 0);
				addObject(obby1);
				addObject(obby2);
				addObject(obby3);
				addObject(obby4);
				Objects obby5 = new Objects(obeliskIds[index],
						obeliskCoords[index][0], obeliskCoords[index][1], 0,
						-1, 10, 10);
				Objects obby6 = new Objects(obeliskIds[index],
						obeliskCoords[index][0] + 4, obeliskCoords[index][1],
						0, -1, 10, 10);
				Objects obby7 = new Objects(obeliskIds[index],
						obeliskCoords[index][0], obeliskCoords[index][1] + 4,
						0, -1, 10, 10);
				Objects obby8 = new Objects(obeliskIds[index],
						obeliskCoords[index][0] + 4,
						obeliskCoords[index][1] + 4, 0, -1, 10, 10);
				addObject(obby5);
				addObject(obby6);
				addObject(obby7);
				addObject(obby8);
			}
		}
	}

	public int getObeliskIndex(int id) {
		for (int j = 0; j < obeliskIds.length; j++) {
			if (obeliskIds[j] == id) {
				return j;
			}
		}
		return -1;
	}

	public void teleportObelisk(int port) {
		int random = Misc.random(5);
		while (random == port) {
			random = Misc.random(5);
		}
		for (Player player : PlayerHandler.players) {
			if (player != null) {
				Client c = (Client) player;
				if (Misc.goodDistance(c.getX(), c.getY(),
						obeliskCoords[port][0] + 2, obeliskCoords[port][1] + 2,
						1)) {
					c.getPlayerAssistant().startTeleport(
							obeliskCoords[random][0] + 2,
							obeliskCoords[random][1] + 2, 0, "null");
				}
			}
		}
	}
}
