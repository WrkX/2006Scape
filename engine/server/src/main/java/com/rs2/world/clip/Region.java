package com.rs2.world.clip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apollo.cache.def.ObjectDefinition;

import com.rs2.game.objects.Objects;

public class Region {
	
	private ArrayList<Objects> realObjects = new ArrayList<Objects>();
	private final int id;
	private final int[][][] clips = new int[4][][];
	private final int[][][] projectileClips = new int[4][][];
	private boolean members = false;

	/*
	 * Script-owned objects are layered over the cache.  Keep their clipping
	 * contributors separate from the legacy arrays so replacing one object can
	 * restore the exact lower-layer masks instead of clearing another object's
	 * or map data.  The service uses a monotonically increasing object token.
	 */
	private static final Map<Long, ScriptCollisionTransaction> scriptObjects =
			new HashMap<Long, ScriptCollisionTransaction>();
	private static final Map<ScriptCell, Long> scriptReservations =
			new HashMap<ScriptCell, Long>();
	private static final Map<Long, ObjectContributor> objectContributors =
			new HashMap<Long, ObjectContributor>();
	private static final Map<ObjectKey, Long> objectContributorKeys =
			new HashMap<ObjectKey, Long>();
	private static final Map<ScriptCell, CollisionLedger> collisionLedgers =
			new HashMap<ScriptCell, CollisionLedger>();
	private static final Map<Long, String> collisionQuarantine =
			new HashMap<Long, String>();
	private static long nextContributorIdentity = 1L;
	private static long nextContributorVersion = 1L;
	private static VerificationFailure verificationFailureForTesting =
			VerificationFailure.NONE;

	private enum VerificationFailure { NONE, APPLY, RESTORE }

	/** Exact identity/version receipt for one Region collision contributor. */
	public static final class ContributorReceipt {
		private final long identity, version;
		private final Objects object;
		private ContributorReceipt(ObjectContributor contributor) {
			identity = contributor.identity; version = contributor.version;
			object = copyObject(contributor.object);
		}
		public long identity() { return identity; }
		public long version() { return version; }
		public Objects object() { return copyObject(object); }
	}

	public Region(int id, boolean members) {
		this.id = id;
		this.members = members;
	}

	public int id() {
		return id;
	}

	public boolean members() {
		return members;
	}

	public static boolean isMembers(int x, int y) {
		if (x >= 3272 && x <= 3320 && y >= 2752 && y <= 2809) {
			return false;
		}
		if (x >= 2640 && x <= 2677 && y >= 2638 && y <= 2679) {
			return false;
		}
		return getRegion(x, y).members();
	}
	
	/**
	 * Takes X Y coordinates, gives a region object
	 * 
	 * @param x coordinate X
	 * @param y coordinate Y
	 * @return Region object
	 */
	public static Region getRegion(int x, int y) {
	    int regionId = getRegionId(x,y);
	    Region[] regions = RegionFactory.getRegions();
	    if (regions == null) return null;
	    for (Region region : regions) {
	        if (region != null && region.id() == regionId) {
	            return region;
	        }
	    }
	    return null;
	}
	
	/**
	 * Calculates regionId from X Y coordinates
	 * 
	 * @param x coordinate X
	 * @param y coordinate Y
	 * @return ID of target region
	 */
	public static int getRegionId(int x, int y) {
	    int regionX = x >> 3;
	    int regionY = y >> 3;
	    int regionId = (regionX / 8 << 8) + regionY / 8;
	    return regionId;
	}

	public static Objects getObject(int id, int x, int y, int z) {
		Region r = getRegion(x, y);
		if (r == null)
			return null;
		for (Objects o : r.realObjects) {
			if (o.objectId == id) {
				if (o.objectX == x && o.objectY == y && o.objectHeight == z) {
					return o;
				}
			}
		}
		return null;
	}

	public static Objects getObjectAt(int x, int y, int z) {
		Region region = getRegion(x, y);
		if (region == null) {
			return null;
		}
		for (Objects object : region.realObjects) {
			if (object.objectX == x && object.objectY == y
					&& object.objectHeight == z) {
				return object;
			}
		}
		return null;
	}

	/** Returns the cache object occupying the engine scene slot for this type. */
	public static Objects getObjectAt(int x, int y, int z, int type) {
		Region region = getRegion(x, y);
		if (region == null) return null;
		int slot = objectSlot(type);
		for (Objects object : region.realObjects) if (object.objectX == x
				&& object.objectY == y && object.objectHeight == z
				&& objectSlot(object.getObjectType()) == slot) return object;
		return null;
	}

	private static int objectSlot(int type) {
		if (type >= 0 && type <= 3) return 0;
		if (type >= 4 && type <= 8) return 1;
		return type == 22 ? 3 : 2;
	}

	public static boolean objectExists(int id, int x, int y, int z) {
	    Region r = getRegion(x, y);
	    if (r == null)
	        return false;
	    for (Objects o : r.realObjects) {
	        if (o.objectId == id) {
	            if (o.objectX == x && o.objectY == y && o.objectHeight == z) {
	                return true;
	            }
	        }
	    }
	    return false;
	}

	private void addClip(int x, int y, int height, int shift) {
		ScriptCell cell = new ScriptCell(x, y, height);
		CollisionLedger ledger = collisionLedgers.get(cell);
		if (ledger != null) {
			ledger.baseMovement |= shift;
			writeLedger(cell, ledger);
			return;
		}
		int regionAbsX = (id >> 8) * 64;
		int regionAbsY = (id & 0xff) * 64;
		if (clips[height] == null) {
			clips[height] = new int[64][64];
		}
		clips[height][x - regionAbsX][y - regionAbsY] |= shift;
	}

	private void removeClip(int x, int y, int height) {
		ScriptCell cell = new ScriptCell(x, y, height);
		CollisionLedger ledger = collisionLedgers.get(cell);
		if (ledger != null) {
			ledger.baseMovement = 0;
			writeLedger(cell, ledger);
			return;
		}
		final int regionAbsX = (id >> 8) * 64;
		final int regionAbsY = (id & 0xff) * 64;
		if (clips[height] == null) {
			clips[height] = new int[64][64];
		}
		clips[height][x - regionAbsX][y - regionAbsY] = 0;
	}

	/**
	 * Nothing calls this...
	 * 
	 * @param x
	 * @param y
	 * @param height
	 */
	public void removeClipping(int x, int y, int height) {
		for (Region r : RegionFactory.getRegions()) {
			if (r.id() == getRegionId(x,y)) {
				r.removeClip(x, y, height);
				break;
			}
		}
	}

	private void addProjectileClip(int x, int y, int height, int shift) {
		ScriptCell cell = new ScriptCell(x, y, height);
		CollisionLedger ledger = collisionLedgers.get(cell);
		if (ledger != null) {
			ledger.baseProjectile |= shift;
			writeLedger(cell, ledger);
			return;
		}
		int regionAbsX = (id >> 8) * 64;
		int regionAbsY = (id & 0xff) * 64;
		if (projectileClips[height] == null) {
			projectileClips[height] = new int[64][64];
		}
		projectileClips[height][x - regionAbsX][y - regionAbsY] |= shift;
	}

	public static boolean canMove(int x, int y, int z, int direction) {
		if (direction == 6) {
			if ((Region.getClipping(x, y - 1, z) & 0x1280102) == 0) {
				return true;
			}
		} else if (direction == 3) {
			if ((Region.getClipping(x - 1, y, z) & 0x1280108) == 0) {
				return true;
			}
		} else if (direction == 1) {
			if ((Region.getClipping(x, y + 1, z) & 0x1280120) == 0) {
				return true;
			}
		} else if (direction == 4) {
			if ((Region.getClipping(x + 1, y, z) & 0x1280180) == 0) {
				return true;
			}
		} else if (direction == 5) {
			if ((Region.getClipping(x - 1, y - 1, z) & 0x128010e) == 0
					&& (Region.getClipping(x - 1, y, z) & 0x1280108) == 0
					&& (Region.getClipping(x, y - 1, z) & 0x1280102) == 0) {
				return true;
			}
		} else if (direction == 0) {
			if ((Region.getClipping(x - 1, y + 1, z) & 0x1280138) == 0
					&& (Region.getClipping(x - 1, y, z) & 0x1280108) == 0
					&& (Region.getClipping(x, y + 1, z) & 0x1280120) == 0) {
				return true;
			}
		} else if (direction == 7) {
			if ((Region.getClipping(x + 1, y - 1, z) & 0x1280183) == 0
					&& (Region.getClipping(x + 1, y, z) & 0x1280180) == 0
					&& (Region.getClipping(x, y - 1, z) & 0x1280102) == 0) {
				return true;
			}
		} else if (direction == 2) {
			if ((Region.getClipping(x + 1, y + 1, z) & 0x12801e0) == 0
					&& (Region.getClipping(x + 1, y, z) & 0x1280180) == 0
					&& (Region.getClipping(x, y + 1, z) & 0x1280120) == 0) {
				return true;
			}
		} else if (direction == -1) {
			throw new IllegalArgumentException("Invalid direction: " + direction);
		}

		return false;
	}

	public static boolean canShoot(int x, int y, int z, int direction) {
		if (direction == 0) {
			return !projectileBlockedNorthWest(x, y, z) && !projectileBlockedNorth(x, y, z)
					&& !projectileBlockedWest(x, y, z);
		} else if (direction == 1) {
			return !projectileBlockedNorth(x, y, z);
		} else if (direction == 2) {
			return !projectileBlockedNorthEast(x, y, z) && !projectileBlockedNorth(x, y, z)
					&& !projectileBlockedEast(x, y, z);
		} else if (direction == 3) {
			return !projectileBlockedWest(x, y, z);
		} else if (direction == 4) {
			return !projectileBlockedEast(x, y, z);
		} else if (direction == 5) {
			return !projectileBlockedSouthWest(x, y, z) && !projectileBlockedSouth(x, y, z)
					&& !projectileBlockedWest(x, y, z);
		} else if (direction == 6) {
			return !projectileBlockedSouth(x, y, z);
		} else if (direction == 7) {
			return !projectileBlockedSouthEast(x, y, z) && !projectileBlockedSouth(x, y, z)
					&& !projectileBlockedEast(x, y, z);
		}
		return false;
	}

	public static boolean projectileBlockedNorth(int x, int y, int z) {
		return (getProjectileClipping(x, y + 1, z) & 0x1280120) != 0;
	}

	public static boolean projectileBlockedEast(int x, int y, int z) {
		return (getProjectileClipping(x + 1, y, z) & 0x1280180) != 0;
	}

	public static boolean projectileBlockedSouth(int x, int y, int z) {
		return (getProjectileClipping(x, y - 1, z) & 0x1280102) != 0;
	}

	public static boolean projectileBlockedWest(int x, int y, int z) {
		return (getProjectileClipping(x - 1, y, z) & 0x1280108) != 0;
	}

	public static boolean projectileBlockedNorthEast(int x, int y, int z) {
		return (getProjectileClipping(x + 1, y + 1, z) & 0x12801e0) != 0;
	}

	public static boolean projectileBlockedNorthWest(int x, int y, int z) {
		return (getProjectileClipping(x - 1, y + 1, z) & 0x1280138) != 0;
	}

	public static boolean projectileBlockedSouthEast(int x, int y, int z) {
		return (getProjectileClipping(x + 1, y - 1, z) & 0x1280183) != 0;
	}

	public static boolean projectileBlockedSouthWest(int x, int y, int z) {
		return (getProjectileClipping(x - 1, y - 1, z) & 0x128010e) != 0;
	}

	public static boolean canMove(int startX, int startY, int endX, int endY, int height, int xLength, int yLength) {
		int diffX = endX - startX;
		int diffY = endY - startY;
		int max = Math.max(Math.abs(diffX), Math.abs(diffY));
		for (int ii = 0; ii < max; ii++) {
			int currentX = endX - diffX;
			int currentY = endY - diffY;
			for (int i = 0; i < xLength; i++) {
				for (int i2 = 0; i2 < yLength; i2++)
					if (diffX < 0 && diffY < 0) {
						if ((getClipping((currentX + i) - 1,
								(currentY + i2) - 1, height) & 0x128010e) != 0
								|| (getClipping((currentX + i) - 1, currentY
										+ i2, height) & 0x1280108) != 0
								|| (getClipping(currentX + i,
										(currentY + i2) - 1, height) & 0x1280102) != 0)
							return false;
					} else if (diffX > 0 && diffY > 0) {
						if ((getClipping(currentX + i + 1, currentY + i2 + 1,
								height) & 0x12801e0) != 0
								|| (getClipping(currentX + i + 1,
										currentY + i2, height) & 0x1280180) != 0
								|| (getClipping(currentX + i,
										currentY + i2 + 1, height) & 0x1280120) != 0)
							return false;
					} else if (diffX < 0 && diffY > 0) {
						if ((getClipping((currentX + i) - 1, currentY + i2 + 1,
								height) & 0x1280138) != 0
								|| (getClipping((currentX + i) - 1, currentY
										+ i2, height) & 0x1280108) != 0
								|| (getClipping(currentX + i,
										currentY + i2 + 1, height) & 0x1280120) != 0)
							return false;
					} else if (diffX > 0 && diffY < 0) {
						if ((getClipping(currentX + i + 1, (currentY + i2) - 1,
								height) & 0x1280183) != 0
								|| (getClipping(currentX + i + 1,
										currentY + i2, height) & 0x1280180) != 0
								|| (getClipping(currentX + i,
										(currentY + i2) - 1, height) & 0x1280102) != 0)
							return false;
					} else if (diffX > 0 && diffY == 0) {
						if ((getClipping(currentX + i + 1, currentY + i2,
								height) & 0x1280180) != 0)
							return false;
					} else if (diffX < 0 && diffY == 0) {
						if ((getClipping((currentX + i) - 1, currentY + i2,
								height) & 0x1280108) != 0)
							return false;
					} else if (diffX == 0 && diffY > 0) {
						if ((getClipping(currentX + i, currentY + i2 + 1,
								height) & 0x1280120) != 0)
							return false;
					} else if (diffX == 0
							&& diffY < 0
							&& (getClipping(currentX + i, (currentY + i2) - 1,
									height) & 0x1280102) != 0)
						return false;

			}

			if (diffX < 0)
				diffX++;
			else if (diffX > 0)
				diffX--;
			if (diffY < 0)
				diffY++;
			else if (diffY > 0)
				diffY--;
		}

		return true;
	}

	private int getClip(int x, int y, int height) {
		int regionAbsX = (id >> 8) * 64;
		int regionAbsY = (id & 0xff) * 64;
		if (clips[height] == null) {
			return 0;
		}
		return clips[height][x - regionAbsX][y - regionAbsY];
	}

	private int getProjectileClip(int x, int y, int height) {
		int regionAbsX = (id >> 8) * 64;
		int regionAbsY = (id & 0xff) * 64;
		if (projectileClips[height] == null) {
			return 0;
		}
		return projectileClips[height][x - regionAbsX][y - regionAbsY];
	}

	/**
	 * Adds clipping to whichever region matches provided XYZ.
	 * 
	 * @param x coordinate X
	 * @param y coordinate Y
	 * @param height coordinate Z
	 * @param shift uuuuh shift?
	 */
	public static void addClipping(int x, int y, int height, int shift) {
		for (Region r : RegionFactory.getRegions()) {
			if (r.id() == getRegionId(x, y)) {
				r.addClip(x, y, height, shift);
				break;
			}
		}
	}

	/** Compatibility overload for an empty-to-object transaction. */
	public static synchronized void addScriptObjectCollision(long token,
			Objects object) {
		addScriptObjectCollision(token, (ContributorReceipt) null, object);
	}

	/**
	 * Atomically replaces the expected lower object's collision contributors.
	 * Every cell in the lower/replacement union is reserved, snapshotted,
	 * written and verified. Overlap is rejected instead of merging unrelated
	 * transactions, which keeps reversal exact and version-safe.
	 */
	public static synchronized void addScriptObjectCollision(long token,
			Objects lower, Objects replacement) {
		addScriptObjectCollision(token, lower == null ? null : contributorReceipt(lower),
				replacement);
	}

	/** Receipt-bearing encounter collision transaction used by every object layer. */
	public static synchronized void addScriptObjectCollision(long token,
			ContributorReceipt lowerReceipt, Objects replacement) {
		Long key = Long.valueOf(token);
		if (token <= 0 || scriptObjects.containsKey(key)) {
			throw new IllegalArgumentException("duplicate script object collision token");
		}
		ObjectContributor selectedLower = contributor(lowerReceipt);
		if (lowerReceipt != null && selectedLower == null) {
			throw new IllegalStateException("selected lower collision identity is stale");
		}
		ObjectContributor selectedReplacement = replacement == null ? null
				: newContributor(replacement, false);
		if (selectedReplacement != null) selectedReplacement.selected = true;
		Map<ScriptCell, ScriptMask> lowerMasks = selectedLower == null
				? new HashMap<ScriptCell, ScriptMask>() : selectedLower.masks;
		Map<ScriptCell, ScriptMask> replacementMasks = selectedReplacement == null
				? new HashMap<ScriptCell, ScriptMask>() : selectedReplacement.masks;
		Map<ScriptCell, ScriptMask> before = captureActual(lowerMasks, replacementMasks);
		Map<ScriptCell, ScriptMask> expectedApply = calculateExpected(
				lowerMasks, selectedLower, replacementMasks, selectedReplacement);
		for (ScriptCell cell : lowerMasks.keySet()) {
			if (scriptReservations.containsKey(cell)) {
				throw new IllegalStateException("script collision footprint already reserved");
			}
		}
		for (ScriptCell cell : replacementMasks.keySet()) {
			if (scriptReservations.containsKey(cell)) {
				throw new IllegalStateException("script collision footprint already reserved");
			}
		}
		ScriptCollisionTransaction transaction = new ScriptCollisionTransaction(token,
				selectedLower, selectedReplacement, before, expectedApply);
		try {
			for (ScriptCell cell : lowerMasks.keySet()) scriptReservations.put(cell, key);
			for (ScriptCell cell : replacementMasks.keySet()) scriptReservations.put(cell, key);
			if (selectedLower != null) removeContributor(selectedLower);
			if (selectedReplacement != null) addContributor(selectedReplacement, false);
			injectVerificationFailure(VerificationFailure.APPLY, expectedApply);
			verifyExpected(expectedApply, "script collision apply verification failed");
			scriptObjects.put(key, transaction);
		} catch (RuntimeException failure) {
			if (selectedReplacement != null && objectContributors.containsKey(
					Long.valueOf(selectedReplacement.identity))) removeContributor(selectedReplacement);
			if (selectedLower != null && !objectContributors.containsKey(
					Long.valueOf(selectedLower.identity))) addContributor(selectedLower,
							selectedLower.keyed);
			if (failure instanceof CollisionVerificationException) {
				transaction.quarantined = true;
				collisionQuarantine.put(key, failure.getMessage());
				scriptObjects.put(key, transaction);
			} else {
				for (ScriptCell cell : lowerMasks.keySet()) scriptReservations.remove(cell, key);
				for (ScriptCell cell : replacementMasks.keySet()) scriptReservations.remove(cell, key);
			}
			throw failure;
		}
	}

	/** Removes one versioned transaction and restores its exact pre-state. */
	public static synchronized void removeScriptObjectCollision(long token) {
		Long key = Long.valueOf(token);
		ScriptCollisionTransaction transaction = scriptObjects.get(key);
		if (transaction == null) return;
		if (transaction.quarantined) throw new IllegalStateException(
				"script collision transaction is quarantined");
		for (ScriptCell cell : transaction.expectedApply.keySet()) if (!key.equals(
				scriptReservations.get(cell))) throw new IllegalStateException(
					"script collision reservation version mismatch");
		try {
			verifyExpected(transaction.expectedApply, "script collision version mismatch");
		} catch (CollisionVerificationException mismatch) {
			transaction.quarantined = true;
			collisionQuarantine.put(key, mismatch.getMessage());
			throw mismatch;
		}
		if (transaction.replacement != null) {
			ObjectContributor live = objectContributors.get(Long.valueOf(
					transaction.replacement.identity));
			if (live == null || live.version != transaction.replacement.version)
				throw new IllegalStateException("script replacement contributor is stale");
			removeContributor(transaction.replacement);
		}
		if (transaction.lower != null) {
			if (objectContributors.containsKey(Long.valueOf(transaction.lower.identity)))
				throw new IllegalStateException("selected lower contributor already restored");
			addContributor(transaction.lower, transaction.lower.keyed);
		}
		injectVerificationFailure(VerificationFailure.RESTORE, transaction.before);
		try {
			verifyExpected(transaction.before, "script collision restore verification failed");
		} catch (CollisionVerificationException mismatch) {
			transaction.quarantined = true;
			collisionQuarantine.put(key, mismatch.getMessage());
			throw mismatch;
		}
		for (ScriptCell cell : transaction.expectedApply.keySet()) scriptReservations.remove(cell, key);
		scriptObjects.remove(key);
	}

	/** Applies a deferred lower-layer mutation after its encounter reservation releases. */
	public static synchronized void applyDeferredObjectMutation(Objects lower,
			Objects replacement) {
		ObjectContributor selected = lower == null ? null : findContributor(lower);
		if (lower != null && selected == null) throw new IllegalStateException(
				"deferred lower contributor is stale");
		ObjectContributor added = replacement == null ? null : newContributor(replacement, true);
		if (selected != null) for (ScriptCell cell : selected.masks.keySet())
			if (scriptReservations.containsKey(cell)) throw new IllegalStateException(
					"deferred mutation still overlaps a script reservation");
		if (added != null) for (ScriptCell cell : added.masks.keySet())
			if (scriptReservations.containsKey(cell)) throw new IllegalStateException(
					"deferred mutation still overlaps a script reservation");
		if (selected != null) removeContributor(selected);
		if (added != null) addContributor(added, true);
		Objects positioned = replacement == null ? lower : replacement;
		if (positioned == null) return;
		Region region = getRegion(positioned.getObjectX(), positioned.getObjectY());
		if (region == null) return;
		final int x = positioned.getObjectX(), y = positioned.getObjectY();
		final int plane = positioned.getObjectHeight();
		final int slot = objectSlot(positioned.getObjectType());
		region.realObjects.removeIf(object -> object.getObjectX() == x
				&& object.getObjectY() == y && object.getObjectHeight() == plane
				&& objectSlot(object.getObjectType()) == slot);
		if (replacement != null) region.realObjects.add(new Objects(
				replacement.getObjectId(), replacement.getObjectX(), replacement.getObjectY(),
				replacement.getObjectHeight(), replacement.getObjectFace(),
				replacement.getObjectType(), 0));
	}

	/** Restores every script overlay; used by deterministic test/lifecycle reset. */
	public static synchronized void clearScriptObjectCollisions() {
		for (ScriptCollisionTransaction transaction : new ArrayList<ScriptCollisionTransaction>(
				scriptObjects.values())) {
			if (!transaction.quarantined) removeScriptObjectCollision(transaction.token);
		}
		// A deterministic quarantine may intentionally leave one raw test cell
		// mismatched. Recompose from the retained contributor ledger before reset.
		for (Map.Entry<ScriptCell, CollisionLedger> entry : collisionLedgers.entrySet()) {
			writeLedger(entry.getKey(), entry.getValue());
		}
		scriptReservations.clear();
		scriptObjects.clear();
		collisionQuarantine.clear();
	}

	/** Test/lifecycle reset after RegionFactory has been replaced. */
	public static synchronized void clearObjectCollisionContributors() {
		scriptObjects.clear();
		scriptReservations.clear();
		objectContributors.clear();
		objectContributorKeys.clear();
		collisionLedgers.clear();
		collisionQuarantine.clear();
		verificationFailureForTesting = VerificationFailure.NONE;
		nextContributorIdentity = 1L;
		nextContributorVersion = 1L;
	}

	/** Registers one exact cache/global/timed contributor and returns its receipt. */
	public static synchronized ContributorReceipt registerObjectContributor(
			Objects object, boolean cacheKey) {
		return registerObjectContributor(object, cacheKey, true);
	}

	public static synchronized ContributorReceipt registerObjectContributor(
			Objects object, boolean cacheKey, boolean selected) {
		if (object == null) throw new IllegalArgumentException("object is required");
		ObjectContributor contributor = newContributor(object, cacheKey);
		contributor.selected = selected;
		addContributor(contributor, cacheKey);
		return new ContributorReceipt(contributor);
	}

	/** Atomically replaces one exact lower-layer contributor. */
	public static synchronized ContributorReceipt replaceObjectContributor(
			ContributorReceipt lowerReceipt, Objects replacement, boolean cacheKey) {
		ObjectContributor lower = contributor(lowerReceipt);
		return replaceObjectContributor(lowerReceipt, replacement, cacheKey,
				lower == null || lower.selected);
	}

	public static synchronized ContributorReceipt replaceObjectContributor(
			ContributorReceipt lowerReceipt, Objects replacement, boolean cacheKey,
			boolean selected) {
		ObjectContributor lower = contributor(lowerReceipt);
		if (lowerReceipt != null && lower == null) throw new IllegalStateException(
				"lower collision receipt is stale");
		ObjectContributor added = replacement == null ? null
				: newContributor(replacement, cacheKey);
		if (added != null) added.selected = selected;
		Map<ScriptCell, ScriptMask> lowerMasks = lower == null
				? new HashMap<ScriptCell, ScriptMask>() : lower.masks;
		Map<ScriptCell, ScriptMask> addedMasks = added == null
				? new HashMap<ScriptCell, ScriptMask>() : added.masks;
		for (ScriptCell cell : unionCells(lowerMasks, addedMasks)) {
			if (scriptReservations.containsKey(cell)) throw new IllegalStateException(
					"lower-layer mutation overlaps a script reservation");
		}
		Map<ScriptCell, ScriptMask> before = captureActual(lowerMasks, addedMasks);
		Map<ScriptCell, ScriptMask> expected = calculateExpected(
				lowerMasks, lower, addedMasks, added);
		try {
			if (lower != null) removeContributor(lower);
			if (added != null) addContributor(added, cacheKey);
			verifyExpected(expected, "lower-layer collision apply verification failed");
			return added == null ? null : new ContributorReceipt(added);
		} catch (RuntimeException failure) {
			if (added != null && objectContributors.containsKey(Long.valueOf(added.identity))) {
				removeContributor(added);
			}
			if (lower != null && !objectContributors.containsKey(Long.valueOf(lower.identity))) {
				addContributor(lower, lower.keyed);
			}
			verifyExpected(before, "lower-layer collision rollback verification failed");
			throw failure;
		}
	}

	/** Changes only collision selection; the versioned receipt remains live. */
	public static synchronized void setContributorSelected(ContributorReceipt receipt,
			boolean selected) {
		ObjectContributor value = contributor(receipt);
		if (value == null) throw new IllegalStateException("collision receipt is stale");
		if (value.selected == selected) return;
		if (selected) {
			value.selected = true;
			applyContributorMasks(value);
		} else {
			removeContributorMasks(value);
			value.selected = false;
		}
	}

	public static synchronized boolean isContributorSelected(ContributorReceipt receipt) {
		ObjectContributor value = contributor(receipt);
		return value != null && value.selected;
	}

	public static synchronized ContributorReceipt contributorReceipt(Objects object) {
		ObjectContributor contributor = findContributor(object);
		return contributor == null ? null : new ContributorReceipt(contributor);
	}

	public static synchronized boolean isContributorActive(ContributorReceipt receipt) {
		return contributor(receipt) != null;
	}

	public static synchronized boolean isCollisionQuarantined(long token) {
		return collisionQuarantine.containsKey(Long.valueOf(token));
	}

	public static synchronized int quarantinedCollisionCountForTesting() {
		return collisionQuarantine.size();
	}

	/** Deterministic one-shot verification fault used only by focused tests. */
	public static synchronized void failNextCollisionVerificationForTesting(
			boolean duringRestore) {
		verificationFailureForTesting = duringRestore
				? VerificationFailure.RESTORE : VerificationFailure.APPLY;
	}

	private static Map<ScriptCell, ScriptMask> scriptCollisionContributions(Objects object) {
		Map<ScriptCell, ScriptMask> result = new HashMap<ScriptCell, ScriptMask>();
		ObjectDefinition definition;
		try { definition = ObjectDefinition.lookup(object.getObjectId()); }
		catch (RuntimeException failure) { return result; }
		if (definition == null || !definition.isSolid()) return result;
		int type = object.getObjectType();
		int direction = object.getObjectFace() & 3;
		if (type == 22) {
			if (!definition.isInteractive()) return result;
			addMask(result, object.getObjectX(), object.getObjectY(), object.getObjectHeight(),
					0x200000, definition.isImpenetrable() ? 0x200000 : 0);
		} else if (type >= 9) {
			int xLength = (direction == 1 || direction == 3) ? definition.getLength() : definition.getWidth();
			int yLength = (direction == 1 || direction == 3) ? definition.getWidth() : definition.getLength();
			int movement = 256 | (definition.isClipped() ? 0x20000 : 0);
			int projectile = definition.isImpenetrable() ? movement : 0;
			for (int x = object.getObjectX(); x < object.getObjectX() + xLength; x++) {
				for (int y = object.getObjectY(); y < object.getObjectY() + yLength; y++) {
					addMask(result, x, y, object.getObjectHeight(), movement, projectile);
				}
			}
		} else if (type >= 0 && type <= 3) {
			addVariableMask(result, object.getObjectX(), object.getObjectY(),
					object.getObjectHeight(), type, direction, definition.isClipped(),
					definition.isImpenetrable());
		}
		return result;
	}

	private static void addVariableMask(Map<ScriptCell, ScriptMask> result, int x, int y,
			int height, int type, int direction, boolean clipped, boolean impenetrable) {
		Map<ScriptCell, ScriptMask> movement = new HashMap<ScriptCell, ScriptMask>();
		if (type == 0) {
			if (direction == 0) { addMask(movement, x, y, height, 128, 0); addMask(movement, x - 1, y, height, 8, 0); }
			if (direction == 1) { addMask(movement, x, y, height, 2, 0); addMask(movement, x, y + 1, height, 32, 0); }
			if (direction == 2) { addMask(movement, x, y, height, 8, 0); addMask(movement, x + 1, y, height, 128, 0); }
			if (direction == 3) { addMask(movement, x, y, height, 32, 0); addMask(movement, x, y - 1, height, 2, 0); }
		} else if (type == 1 || type == 3) {
			if (direction == 0) { addMask(movement, x, y, height, 1, 0); addMask(movement, x - 1, y, height, 16, 0); }
			if (direction == 1) { addMask(movement, x, y, height, 4, 0); addMask(movement, x + 1, y + 1, height, 64, 0); }
			if (direction == 2) { addMask(movement, x, y, height, 16, 0); addMask(movement, x + 1, y - 1, height, 1, 0); }
			if (direction == 3) { addMask(movement, x, y, height, 64, 0); addMask(movement, x - 1, y - 1, height, 4, 0); }
		} else if (type == 2) {
			if (direction == 0) { addMask(movement, x, y, height, 130, 0); addMask(movement, x - 1, y, height, 8, 0); addMask(movement, x, y + 1, height, 32, 0); }
			if (direction == 1) { addMask(movement, x, y, height, 10, 0); addMask(movement, x, y + 1, height, 32, 0); addMask(movement, x + 1, y, height, 128, 0); }
			if (direction == 2) { addMask(movement, x, y, height, 40, 0); addMask(movement, x + 1, y, height, 128, 0); addMask(movement, x, y - 1, height, 2, 0); }
			if (direction == 3) { addMask(movement, x, y, height, 160, 0); addMask(movement, x, y - 1, height, 2, 0); addMask(movement, x - 1, y, height, 8, 0); }
		}
		if (clipped) {
			if (type == 0) {
				if (direction == 0) { addMask(movement, x, y, height, 65536, 0); addMask(movement, x - 1, y, height, 4096, 0); }
				if (direction == 1) { addMask(movement, x, y, height, 1024, 0); addMask(movement, x, y + 1, height, 16384, 0); }
				if (direction == 2) { addMask(movement, x, y, height, 4096, 0); addMask(movement, x + 1, y, height, 65536, 0); }
				if (direction == 3) { addMask(movement, x, y, height, 16384, 0); addMask(movement, x, y - 1, height, 1024, 0); }
			} else if (type == 1 || type == 3) {
				if (direction == 0) { addMask(movement, x, y, height, 512, 0); addMask(movement, x - 1, y + 1, height, 8192, 0); }
				if (direction == 1) { addMask(movement, x, y, height, 2048, 0); addMask(movement, x + 1, y + 1, height, 32768, 0); }
				if (direction == 2) { addMask(movement, x, y, height, 8192, 0); addMask(movement, x + 1, y - 1, height, 512, 0); }
				if (direction == 3) { addMask(movement, x, y, height, 32768, 0); addMask(movement, x - 1, y - 1, height, 2048, 0); }
			} else if (type == 2) {
				if (direction == 0) { addMask(movement, x, y, height, 66560, 0); addMask(movement, x - 1, y, height, 4096, 0); addMask(movement, x, y + 1, height, 16384, 0); }
				if (direction == 1) { addMask(movement, x, y, height, 5120, 0); addMask(movement, x, y + 1, height, 16384, 0); addMask(movement, x + 1, y, height, 65536, 0); }
				if (direction == 2) { addMask(movement, x, y, height, 20480, 0); addMask(movement, x + 1, y, height, 65536, 0); addMask(movement, x, y - 1, height, 1024, 0); }
				if (direction == 3) { addMask(movement, x, y, height, 81920, 0); addMask(movement, x, y - 1, height, 1024, 0); addMask(movement, x - 1, y, height, 4096, 0); }
			}
		}
		for (Map.Entry<ScriptCell, ScriptMask> entry : movement.entrySet()) {
			ScriptMask value = entry.getValue();
			int projectile = impenetrable ? value.movement : 0;
			addMask(result, entry.getKey().x, entry.getKey().y, entry.getKey().plane,
					value.movement, projectile);
		}
	}

	private static void addMask(Map<ScriptCell, ScriptMask> map, int x, int y, int plane,
			int movement, int projectile) {
		if (x < 0 || y < 0 || plane < 0 || plane > 3) return;
		ScriptCell cell = new ScriptCell(x, y, plane);
		ScriptMask prior = map.get(cell);
		if (prior == null) map.put(cell, new ScriptMask(movement, projectile));
		else { prior.movement |= movement; prior.projectile |= projectile; }
	}

	private static ObjectContributor findContributor(Objects object) {
		if (object == null) return null;
		Long identity = objectContributorKeys.get(new ObjectKey(object));
		return identity == null ? null : objectContributors.get(identity);
	}

	private static ObjectContributor newContributor(Objects object) {
		return newContributor(object, true);
	}

	private static ObjectContributor newContributor(Objects object, boolean keyed) {
		Objects snapshot = copyObject(object);
		return new ObjectContributor(nextContributorIdentity++, nextContributorVersion++,
				snapshot, scriptCollisionContributions(snapshot), keyed);
	}

	private static ObjectContributor contributor(ContributorReceipt receipt) {
		if (receipt == null) return null;
		ObjectContributor contributor = objectContributors.get(Long.valueOf(receipt.identity));
		return contributor != null && contributor.version == receipt.version
				&& new ObjectKey(contributor.object).equals(new ObjectKey(receipt.object))
				? contributor : null;
	}

	private static void addContributor(ObjectContributor contributor, boolean keyed) {
		Long identity = Long.valueOf(contributor.identity);
		if (objectContributors.containsKey(identity)) {
			throw new IllegalStateException("collision contributor identity already active");
		}
		ObjectKey objectKey = new ObjectKey(contributor.object);
		if (keyed && objectContributorKeys.containsKey(objectKey)) {
			throw new IllegalStateException("collision contributor object already active");
		}
		objectContributors.put(identity, contributor);
		if (keyed) objectContributorKeys.put(objectKey, identity);
		try {
			if (contributor.selected) applyContributorMasks(contributor);
		} catch (RuntimeException failure) {
			if (contributor.selected) removeContributorMasks(contributor);
			objectContributors.remove(identity);
			if (keyed) objectContributorKeys.remove(objectKey, identity);
			throw failure;
		}
	}

	private static void applyContributorMasks(ObjectContributor contributor) {
		Long identity = Long.valueOf(contributor.identity);
		for (Map.Entry<ScriptCell, ScriptMask> entry : contributor.masks.entrySet()) {
				ScriptCell cell = entry.getKey();
				CollisionLedger ledger = collisionLedgers.get(cell);
				if (ledger == null) {
					ledger = new CollisionLedger(getClipping(cell.x, cell.y, cell.plane),
							getProjectileClipping(cell.x, cell.y, cell.plane));
					collisionLedgers.put(cell, ledger);
				}
				ledger.contributors.put(identity, entry.getValue());
				writeLedger(cell, ledger);
		}
	}

	private static void removeContributorMasks(ObjectContributor contributor) {
		Long identity = Long.valueOf(contributor.identity);
		for (ScriptCell cell : contributor.masks.keySet()) {
			CollisionLedger ledger = collisionLedgers.get(cell);
			if (ledger == null || ledger.contributors.remove(identity) == null) {
				throw new IllegalStateException("collision contributor cell is stale");
			}
			writeLedger(cell, ledger);
		}
	}

	private static void removeContributor(ObjectContributor contributor) {
		Long identity = Long.valueOf(contributor.identity);
		ObjectContributor live = objectContributors.get(identity);
		if (live == null || live.version != contributor.version) {
			throw new IllegalStateException("collision contributor identity/version is stale");
		}
		if (contributor.selected) removeContributorMasks(contributor);
		objectContributors.remove(identity);
		objectContributorKeys.remove(new ObjectKey(contributor.object), identity);
	}

	private static ArrayList<ScriptCell> unionCells(Map<ScriptCell, ScriptMask> first,
			Map<ScriptCell, ScriptMask> second) {
		Map<ScriptCell, ScriptCell> union = new HashMap<ScriptCell, ScriptCell>();
		for (ScriptCell cell : first.keySet()) union.put(cell, cell);
		for (ScriptCell cell : second.keySet()) union.put(cell, cell);
		return new ArrayList<ScriptCell>(union.keySet());
	}

	private static Map<ScriptCell, ScriptMask> captureActual(
			Map<ScriptCell, ScriptMask> first, Map<ScriptCell, ScriptMask> second) {
		Map<ScriptCell, ScriptMask> result = new HashMap<ScriptCell, ScriptMask>();
		for (ScriptCell cell : unionCells(first, second)) result.put(cell, new ScriptMask(
				getClipping(cell.x, cell.y, cell.plane),
				getProjectileClipping(cell.x, cell.y, cell.plane)));
		return result;
	}

	/** Computes the postcondition from immutable base + contributor receipts before writes. */
	private static Map<ScriptCell, ScriptMask> calculateExpected(
			Map<ScriptCell, ScriptMask> lowerMasks, ObjectContributor lower,
			Map<ScriptCell, ScriptMask> addedMasks, ObjectContributor added) {
		Map<ScriptCell, ScriptMask> result = new HashMap<ScriptCell, ScriptMask>();
		for (ScriptCell cell : unionCells(lowerMasks, addedMasks)) {
			CollisionLedger ledger = collisionLedgers.get(cell);
			int movement = ledger == null ? getClipping(cell.x, cell.y, cell.plane)
					: ledger.baseMovement;
			int projectile = ledger == null
					? getProjectileClipping(cell.x, cell.y, cell.plane)
					: ledger.baseProjectile;
			if (ledger != null) for (Map.Entry<Long, ScriptMask> entry
					: ledger.contributors.entrySet()) {
				if (lower != null && entry.getKey().longValue() == lower.identity) continue;
				movement |= entry.getValue().movement;
				projectile |= entry.getValue().projectile;
			}
			ScriptMask addedMask = addedMasks.get(cell);
			if (added != null && added.selected && addedMask != null) {
				movement |= addedMask.movement;
				projectile |= addedMask.projectile;
			}
			result.put(cell, new ScriptMask(movement, projectile));
		}
		return result;
	}

	private static void injectVerificationFailure(VerificationFailure phase,
			Map<ScriptCell, ScriptMask> expected) {
		if (verificationFailureForTesting != phase || expected.isEmpty()) return;
		verificationFailureForTesting = VerificationFailure.NONE;
		ScriptCell cell = expected.keySet().iterator().next();
		ScriptMask mask = expected.get(cell);
		writeExact(cell.x, cell.y, cell.plane, mask.movement ^ 1, mask.projectile);
	}

	private static void verifyExpected(Map<ScriptCell, ScriptMask> expected, String message) {
		for (Map.Entry<ScriptCell, ScriptMask> entry : expected.entrySet()) {
			ScriptCell cell = entry.getKey();
			ScriptMask mask = entry.getValue();
			if (getClipping(cell.x, cell.y, cell.plane) != mask.movement
					|| getProjectileClipping(cell.x, cell.y, cell.plane) != mask.projectile) {
				throw new CollisionVerificationException(message);
			}
		}
	}

	private static void writeLedger(ScriptCell cell, CollisionLedger ledger) {
		int movement = ledger.baseMovement;
		int projectile = ledger.baseProjectile;
		for (ScriptMask mask : ledger.contributors.values()) {
			movement |= mask.movement;
			projectile |= mask.projectile;
		}
		writeExact(cell.x, cell.y, cell.plane, movement, projectile);
	}

	private static Objects copyObject(Objects object) {
		return new Objects(object.getObjectId(), object.getObjectX(), object.getObjectY(),
				object.getObjectHeight(), object.getObjectFace(), object.getObjectType(), 0);
	}

	private static void writeExact(int x, int y, int plane, int movement, int projectile) {
		Region region = getRegion(x, y);
		if (region == null || plane < 0 || plane > 3) return;
		int rx = x - ((region.id >> 8) * 64), ry = y - ((region.id & 0xff) * 64);
		if (rx < 0 || rx >= 64 || ry < 0 || ry >= 64) return;
		if (region.clips[plane] == null) region.clips[plane] = new int[64][64];
		if (region.projectileClips[plane] == null) region.projectileClips[plane] = new int[64][64];
		region.clips[plane][rx][ry] = movement;
		region.projectileClips[plane][rx][ry] = projectile;
	}

	private static void addProjectileClipping(int x, int y, int height, int shift) {
		for (Region r : RegionFactory.getRegions()) {
			if (r.id() == getRegionId(x,y)) {
				r.addProjectileClip(x, y, height, shift);
				break;
			}
		}
	}

	private static void addClippingForVariableObject(int x, int y, int height,
			int type, int direction, boolean flag) {
		if (type == 0) {
			if (direction == 0) {
				addClipping(x, y, height, 128);
				addClipping(x - 1, y, height, 8);
			} else if (direction == 1) {
				addClipping(x, y, height, 2);
				addClipping(x, y + 1, height, 32);
			} else if (direction == 2) {
				addClipping(x, y, height, 8);
				addClipping(x + 1, y, height, 128);
			} else if (direction == 3) {
				addClipping(x, y, height, 32);
				addClipping(x, y - 1, height, 2);
			}
		} else if (type == 1 || type == 3) {
			if (direction == 0) {
				addClipping(x, y, height, 1);
				addClipping(x - 1, y, height, 16);
			} else if (direction == 1) {
				addClipping(x, y, height, 4);
				addClipping(x + 1, y + 1, height, 64);
			} else if (direction == 2) {
				addClipping(x, y, height, 16);
				addClipping(x + 1, y - 1, height, 1);
			} else if (direction == 3) {
				addClipping(x, y, height, 64);
				addClipping(x - 1, y - 1, height, 4);
			}
		} else if (type == 2) {
			if (direction == 0) {
				addClipping(x, y, height, 130);
				addClipping(x - 1, y, height, 8);
				addClipping(x, y + 1, height, 32);
			} else if (direction == 1) {
				addClipping(x, y, height, 10);
				addClipping(x, y + 1, height, 32);
				addClipping(x + 1, y, height, 128);
			} else if (direction == 2) {
				addClipping(x, y, height, 40);
				addClipping(x + 1, y, height, 128);
				addClipping(x, y - 1, height, 2);
			} else if (direction == 3) {
				addClipping(x, y, height, 160);
				addClipping(x, y - 1, height, 2);
				addClipping(x - 1, y, height, 8);
			}
		}
		if (flag) {
			if (type == 0) {
				if (direction == 0) {
					addClipping(x, y, height, 65536);
					addClipping(x - 1, y, height, 4096);
				} else if (direction == 1) {
					addClipping(x, y, height, 1024);
					addClipping(x, y + 1, height, 16384);
				} else if (direction == 2) {
					addClipping(x, y, height, 4096);
					addClipping(x + 1, y, height, 65536);
				} else if (direction == 3) {
					addClipping(x, y, height, 16384);
					addClipping(x, y - 1, height, 1024);
				}
			}
			if (type == 1 || type == 3) {
				if (direction == 0) {
					addClipping(x, y, height, 512);
					addClipping(x - 1, y + 1, height, 8192);
				} else if (direction == 1) {
					addClipping(x, y, height, 2048);
					addClipping(x + 1, y + 1, height, 32768);
				} else if (direction == 2) {
					addClipping(x, y, height, 8192);
					addClipping(x + 1, y + 1, height, 512);
				} else if (direction == 3) {
					addClipping(x, y, height, 32768);
					addClipping(x - 1, y - 1, height, 2048);
				}
			} else if (type == 2) {
				if (direction == 0) {
					addClipping(x, y, height, 66560);
					addClipping(x - 1, y, height, 4096);
					addClipping(x, y + 1, height, 16384);
				} else if (direction == 1) {
					addClipping(x, y, height, 5120);
					addClipping(x, y + 1, height, 16384);
					addClipping(x + 1, y, height, 65536);
				} else if (direction == 2) {
					addClipping(x, y, height, 20480);
					addClipping(x + 1, y, height, 65536);
					addClipping(x, y - 1, height, 1024);
				} else if (direction == 3) {
					addClipping(x, y, height, 81920);
					addClipping(x, y - 1, height, 1024);
					addClipping(x - 1, y, height, 4096);
				}
			}
		}
	}

	private static void addProjectileClippingForVariableObject(int x, int y, int height,
													 int type, int direction, boolean flag) {
		if (type == 0) {
			if (direction == 0) {
				addProjectileClipping(x, y, height, 128);
				addProjectileClipping(x - 1, y, height, 8);
			} else if (direction == 1) {
				addProjectileClipping(x, y, height, 2);
				addProjectileClipping(x, y + 1, height, 32);
			} else if (direction == 2) {
				addProjectileClipping(x, y, height, 8);
				addProjectileClipping(x + 1, y, height, 128);
			} else if (direction == 3) {
				addProjectileClipping(x, y, height, 32);
				addProjectileClipping(x, y - 1, height, 2);
			}
		} else if (type == 1 || type == 3) {
			if (direction == 0) {
				addProjectileClipping(x, y, height, 1);
				addProjectileClipping(x - 1, y, height, 16);
			} else if (direction == 1) {
				addProjectileClipping(x, y, height, 4);
				addProjectileClipping(x + 1, y + 1, height, 64);
			} else if (direction == 2) {
				addProjectileClipping(x, y, height, 16);
				addProjectileClipping(x + 1, y - 1, height, 1);
			} else if (direction == 3) {
				addProjectileClipping(x, y, height, 64);
				addProjectileClipping(x - 1, y - 1, height, 4);
			}
		} else if (type == 2) {
			if (direction == 0) {
				addProjectileClipping(x, y, height, 130);
				addProjectileClipping(x - 1, y, height, 8);
				addProjectileClipping(x, y + 1, height, 32);
			} else if (direction == 1) {
				addProjectileClipping(x, y, height, 10);
				addProjectileClipping(x, y + 1, height, 32);
				addProjectileClipping(x + 1, y, height, 128);
			} else if (direction == 2) {
				addProjectileClipping(x, y, height, 40);
				addProjectileClipping(x + 1, y, height, 128);
				addProjectileClipping(x, y - 1, height, 2);
			} else if (direction == 3) {
				addProjectileClipping(x, y, height, 160);
				addProjectileClipping(x, y - 1, height, 2);
				addProjectileClipping(x - 1, y, height, 8);
			}
		}
		if (flag) {
			if (type == 0) {
				if (direction == 0) {
					addProjectileClipping(x, y, height, 65536);
					addProjectileClipping(x - 1, y, height, 4096);
				} else if (direction == 1) {
					addProjectileClipping(x, y, height, 1024);
					addProjectileClipping(x, y + 1, height, 16384);
				} else if (direction == 2) {
					addProjectileClipping(x, y, height, 4096);
					addProjectileClipping(x + 1, y, height, 65536);
				} else if (direction == 3) {
					addProjectileClipping(x, y, height, 16384);
					addProjectileClipping(x, y - 1, height, 1024);
				}
			}
			if (type == 1 || type == 3) {
				if (direction == 0) {
					addProjectileClipping(x, y, height, 512);
					addProjectileClipping(x - 1, y + 1, height, 8192);
				} else if (direction == 1) {
					addProjectileClipping(x, y, height, 2048);
					addProjectileClipping(x + 1, y + 1, height, 32768);
				} else if (direction == 2) {
					addProjectileClipping(x, y, height, 8192);
					addProjectileClipping(x + 1, y + 1, height, 512);
				} else if (direction == 3) {
					addProjectileClipping(x, y, height, 32768);
					addProjectileClipping(x - 1, y - 1, height, 2048);
				}
			} else if (type == 2) {
				if (direction == 0) {
					addProjectileClipping(x, y, height, 66560);
					addProjectileClipping(x - 1, y, height, 4096);
					addProjectileClipping(x, y + 1, height, 16384);
				} else if (direction == 1) {
					addProjectileClipping(x, y, height, 5120);
					addProjectileClipping(x, y + 1, height, 16384);
					addProjectileClipping(x + 1, y, height, 65536);
				} else if (direction == 2) {
					addProjectileClipping(x, y, height, 20480);
					addProjectileClipping(x + 1, y, height, 65536);
					addProjectileClipping(x, y - 1, height, 1024);
				} else if (direction == 3) {
					addProjectileClipping(x, y, height, 81920);
					addProjectileClipping(x, y - 1, height, 1024);
					addProjectileClipping(x - 1, y, height, 4096);
				}
			}
		}
	}

	private static void addClippingForSolidObject(int x, int y, int height,
			int xLength, int yLength, boolean flag) {
		int clipping = 256;
		if (flag) {
			clipping += 0x20000;
		}
		for (int i = x; i < x + xLength; i++) {
			for (int i2 = y; i2 < y + yLength; i2++) {
				addClipping(i, i2, height, clipping);
			}
		}
	}

	private static void addProjectileClippingForSolidObject(int x, int y, int height,
												  int xLength, int yLength, boolean flag) {
		int clipping = 256;
		if (flag) {
			clipping += 0x20000;
		}
		for (int i = x; i < x + xLength; i++) {
			for (int i2 = y; i2 < y + yLength; i2++) {
				addProjectileClipping(i, i2, height, clipping);
			}
		}
	}

	/**
	 * 
	 * Adds object to region
	 * 
	 * @param objectId
	 * @param x
	 * @param y
	 * @param height
	 * @param type
	 * @param direction
	 * @param startUp
	 */
	public static void addObject(int objectId, int x, int y, int height, int type, int direction, boolean startUp) {
		Objects object = new Objects(objectId, x, y, height, direction, type, 0);
		com.rs2.world.WorldObjectService.getInstance().applyCacheAdd(object);
	}

	/** Backing-store half of an already accepted cache mutation. */
	public static synchronized void applyCacheBackingMutation(Objects lower,
			Objects replacement) {
		Objects positioned = replacement == null ? lower : replacement;
		if (positioned == null) return;
		Region r = getRegion(positioned.getObjectX(), positioned.getObjectY());
		if (r != null) {
			final int x = positioned.getObjectX(), y = positioned.getObjectY();
			final int plane = positioned.getObjectHeight();
			final int slot = objectSlot(positioned.getObjectType());
			r.realObjects.removeIf(object -> object.getObjectX() == x
					&& object.getObjectY() == y && object.getObjectHeight() == plane
					&& objectSlot(object.getObjectType()) == slot);
			if (replacement != null) r.realObjects.add(copyObject(replacement));
		}
	}

	/** Lossless backing half used only while decoding the cache archive. */
	public static synchronized void applyCacheLoadBacking(Objects object) {
		if (object == null) return;
		Region region = getRegion(object.getObjectX(), object.getObjectY());
		if (region == null) return;
		for (Objects existing : region.realObjects) {
			if (existing.getObjectId() == object.getObjectId()
					&& existing.getObjectX() == object.getObjectX()
					&& existing.getObjectY() == object.getObjectY()
					&& existing.getObjectHeight() == object.getObjectHeight()
					&& existing.getObjectFace() == object.getObjectFace()
					&& existing.getObjectType() == object.getObjectType()) return;
		}
		region.realObjects.add(copyObject(object));
	}

	private static final class ScriptCell {
		final int x, y, plane;
		ScriptCell(int x, int y, int plane) { this.x = x; this.y = y; this.plane = plane; }
		@Override public int hashCode() { return ((plane * 16384) + x) * 16384 + y; }
		@Override public boolean equals(Object value) {
			if (!(value instanceof ScriptCell)) return false;
			ScriptCell other = (ScriptCell) value;
			return x == other.x && y == other.y && plane == other.plane;
		}
	}

	private static final class ScriptMask {
		int movement, projectile;
		ScriptMask(int movement, int projectile) {
			this.movement = movement;
			this.projectile = projectile;
		}
	}

	private static final class ObjectKey {
		final int id, x, y, plane, face, type;
		ObjectKey(Objects object) {
			id = object.getObjectId(); x = object.getObjectX(); y = object.getObjectY();
			plane = object.getObjectHeight(); face = object.getObjectFace();
			type = object.getObjectType();
		}
		@Override public int hashCode() {
			int result = id;
			result = 31 * result + x; result = 31 * result + y;
			result = 31 * result + plane; result = 31 * result + face;
			return 31 * result + type;
		}
		@Override public boolean equals(Object value) {
			if (!(value instanceof ObjectKey)) return false;
			ObjectKey other = (ObjectKey) value;
			return id == other.id && x == other.x && y == other.y
					&& plane == other.plane && face == other.face && type == other.type;
		}
	}

	private static final class ObjectContributor {
		final long identity, version;
		final Objects object;
		final Map<ScriptCell, ScriptMask> masks;
		final boolean keyed;
		boolean selected;
		ObjectContributor(long identity, long version, Objects object,
				Map<ScriptCell, ScriptMask> masks, boolean keyed) {
			this.identity = identity; this.version = version;
			this.object = object; this.masks = masks; this.keyed = keyed;
		}
	}

	private static final class CollisionVerificationException
			extends IllegalStateException {
		private static final long serialVersionUID = 1L;
		CollisionVerificationException(String message) { super(message); }
	}

	private static final class CollisionLedger {
		int baseMovement, baseProjectile;
		final Map<Long, ScriptMask> contributors = new HashMap<Long, ScriptMask>();
		CollisionLedger(int baseMovement, int baseProjectile) {
			this.baseMovement = baseMovement; this.baseProjectile = baseProjectile;
		}
	}

	private static final class ScriptCollisionTransaction {
		final long token;
		final ObjectContributor lower, replacement;
		final Map<ScriptCell, ScriptMask> before, expectedApply;
		boolean quarantined;
		ScriptCollisionTransaction(long token, ObjectContributor lower,
				ObjectContributor replacement, Map<ScriptCell, ScriptMask> before,
				Map<ScriptCell, ScriptMask> expectedApply) {
			this.token = token; this.lower = lower;
			this.replacement = replacement; this.before = before;
			this.expectedApply = expectedApply;
		}
	}

	public static int getClipping(int x, int y, int height) {
		if (height > 3) {
			height = 0; //this doesn't seem good
		}
		for (Region r : RegionFactory.getRegions()) {
			if (r.id() == getRegionId(x,y)) {
				return r.getClip(x, y, height);
			}
		}
		return 0;
	}

	public static int getProjectileClipping(int x, int y, int height) {
		if (height > 3) {
			height = 0;
		}
		for (Region r : RegionFactory.getRegions()) {
			if (r.id() == getRegionId(x,y)) {
				return r.getProjectileClip(x, y, height);
			}
		}
		return 0;
	}

	public static boolean getClipping(int x, int y, int height, int moveTypeX,
			int moveTypeY) {
		try {
			if (height > 3) {
				height = 0;
			}
			int checkX = x + moveTypeX;
			int checkY = y + moveTypeY;
			if (moveTypeX == -1 && moveTypeY == 0) {
				return (getClipping(x, y, height) & 0x1280108) == 0;
			} else if (moveTypeX == 1 && moveTypeY == 0) {
				return (getClipping(x, y, height) & 0x1280180) == 0;
			} else if (moveTypeX == 0 && moveTypeY == -1) {
				return (getClipping(x, y, height) & 0x1280102) == 0;
			} else if (moveTypeX == 0 && moveTypeY == 1) {
				return (getClipping(x, y, height) & 0x1280120) == 0;
			} else if (moveTypeX == -1 && moveTypeY == -1) {
				return (getClipping(x, y, height) & 0x128010e) == 0
						&& (getClipping(checkX - 1, checkY, height) & 0x1280108) == 0
						&& (getClipping(checkX - 1, checkY, height) & 0x1280102) == 0;
			} else if (moveTypeX == 1 && moveTypeY == -1) {
				return (getClipping(x, y, height) & 0x1280183) == 0
						&& (getClipping(checkX + 1, checkY, height) & 0x1280180) == 0
						&& (getClipping(checkX, checkY - 1, height) & 0x1280102) == 0;
			} else if (moveTypeX == -1 && moveTypeY == 1) {
				return (getClipping(x, y, height) & 0x1280138) == 0
						&& (getClipping(checkX - 1, checkY, height) & 0x1280108) == 0
						&& (getClipping(checkX, checkY + 1, height) & 0x1280120) == 0;
			} else if (moveTypeX == 1 && moveTypeY == 1) {
				return (getClipping(x, y, height) & 0x12801e0) == 0
						&& (getClipping(checkX + 1, checkY, height) & 0x1280180) == 0
						&& (getClipping(checkX, checkY + 1, height) & 0x1280120) == 0;
			} else {
				System.out.println("[FATAL ERROR]: At getClipping: " + x + ", "
						+ y + ", " + height + ", " + moveTypeX + ", "
						+ moveTypeY);
				return false;
			}
		} catch (Exception e) {
			return true;
		}
	}

}
