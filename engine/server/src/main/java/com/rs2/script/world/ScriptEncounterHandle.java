package com.rs2.script.world;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import com.rs2.script.ScriptArray;
import com.rs2.script.ScriptEntityLimits;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.ScriptedPosition;
import com.rs2.script.scheduler.ScriptTaskHandle;
import com.rs2.world.WorldObjectService;

/** Narrow WP3 encounter surface exposed to TypeScript. */
public final class ScriptEncounterHandle {

	private final ScriptEncounterService service;
	private final long token;
	private final String id;
	private final ScriptedPlayer owner;

	ScriptEncounterHandle(ScriptEncounterService service, long token,
			String id, ScriptedPlayer owner) {
		this.service = service;
		this.token = token;
		this.id = id;
		this.owner = owner;
	}

	@HostAccess.Export
	public String id() {
		return id;
	}

	/** Test-only opaque token accessor; never exported to guest code. */
	public long token() {
		return token;
	}

	@HostAccess.Export
	public ScriptedPlayer owner() {
		return owner;
	}

	@HostAccess.Export
	public boolean isOpen() {
		return service.isOpen(token);
	}

	@HostAccess.Export
	public boolean addParticipant(ScriptedPlayer player) {
		return service.addParticipant(token, player);
	}

	@HostAccess.Export
	public boolean removeParticipant(ScriptedPlayer player) {
		return service.removeParticipant(token, player);
	}

	@HostAccess.Export
	public ScriptArray participants() {
		return service.participants(token);
	}

	@HostAccess.Export
	public ScriptNpcHandle spawnNpc(double npcIdValue, double xValue,
			double yValue, double planeValue, double hpValue,
			double maxHitValue, double attackValue, double defenceValue) {
		Integer npcId = integral(npcIdValue, 0, ScriptEntityLimits.MAX_NPC_ID);
		Integer x = integral(xValue, 0, 16383);
		Integer y = integral(yValue, 0, 16383);
		Integer plane = integral(planeValue, 0, 3);
		Integer hp = integral(hpValue, 1, 32767);
		Integer maxHit = integral(maxHitValue, 0, 32767);
		Integer attack = integral(attackValue, 0, 32767);
		Integer defence = integral(defenceValue, 0, 32767);
		if (npcId == null || x == null || y == null || plane == null
				|| hp == null || maxHit == null || attack == null
				|| defence == null) {
			return null;
		}
		return service.spawnNpc(token, owner.generation(), owner,
				npcId.intValue(), x.intValue(), y.intValue(), plane.intValue(),
				hp.intValue(), maxHit.intValue(), attack.intValue(),
				defence.intValue());
	}

	/** Binary/source compatibility for engine-side Java callers. */
	public ScriptNpcHandle spawnNpc(int npcId, int x, int y, int plane,
			int hp, int maxHit, int attack, int defence) {
		return spawnNpc((double) npcId, (double) x, (double) y,
				(double) plane, (double) hp, (double) maxHit, (double) attack,
				(double) defence);
	}

	@HostAccess.Export
	public boolean onNpcDeath(ScriptNpcHandle npc, Value callback) {
		return service.onNpcDeath(token, npc, callback);
	}

	/**
	 * Java-owned death listener for host consumers (declarative boss
	 * controller). Never exported to guest code.
	 */
	public boolean onNpcDeath(ScriptNpcHandle npc,
			ScriptNpcService.EncounterDeathListener listener) {
		return service.onNpcDeath(token, npc, listener);
	}

	@HostAccess.Export
	public ScriptObjectHandle replaceObject(double xValue, double yValue,
			double planeValue, double expectedIdValue, double expectedTypeValue,
			double expectedRotationValue, double replacementIdValue,
			double replacementTypeValue, double replacementRotationValue) {
		Integer x = integral(xValue, 0, 16383);
		Integer y = integral(yValue, 0, 16383);
		Integer plane = integral(planeValue, 0, 3);
		Integer expectedId = integral(expectedIdValue, -1, ScriptEntityLimits.MAX_OBJECT_ID);
		Integer expectedType = integral(expectedTypeValue, -1, 22);
		Integer expectedRotation = integral(expectedRotationValue, -1, 3);
		Integer replacementId = integral(replacementIdValue, -1, ScriptEntityLimits.MAX_OBJECT_ID);
		Integer replacementType = integral(replacementTypeValue, -1, 22);
		Integer replacementRotation = integral(replacementRotationValue, -1, 3);
		if (x == null || y == null || plane == null || expectedId == null
				|| expectedType == null || expectedRotation == null || replacementId == null
				|| replacementType == null || replacementRotation == null
				|| !isOpen() || !contains(x, y, plane)
				|| (expectedId.intValue() == -1 && (expectedType.intValue() != -1
						|| expectedRotation.intValue() != -1))
				|| (replacementId.intValue() == -1 && (replacementType.intValue() != -1
						|| replacementRotation.intValue() != -1))) return null;
		return WorldObjectService.getInstance().replace(token, x, y, plane,
				expectedId, expectedType, expectedRotation, replacementId,
				replacementType, replacementRotation);
	}

	public ScriptObjectHandle replaceObject(int x, int y, int plane, int expectedId,
			int expectedType, int expectedRotation, int replacementId,
			int replacementType, int replacementRotation) {
		return replaceObject((double) x, (double) y, (double) plane,
				(double) expectedId, (double) expectedType, (double) expectedRotation,
				(double) replacementId, (double) replacementType, (double) replacementRotation);
	}

	@HostAccess.Export
	public ScriptObjectHandle removeObject(double xValue, double yValue,
			double planeValue, double expectedIdValue, double expectedTypeValue,
			double expectedRotationValue) {
		return replaceObject(xValue, yValue, planeValue, expectedIdValue,
				expectedTypeValue, expectedRotationValue, -1, -1, -1);
	}

	@HostAccess.Export
	public ScriptGroundItemHandle dropFor(ScriptedPlayer player, double itemIdValue,
			double amountValue, double xValue, double yValue, double planeValue) {
		Integer itemId = integral(itemIdValue, 1, ScriptEntityLimits.MAX_ITEM_ID);
		Integer amount = integral(amountValue, 1, 1_000_000);
		Integer x = integral(xValue, 0, 16383);
		Integer y = integral(yValue, 0, 16383);
		Integer plane = integral(planeValue, 0, 3);
		if (itemId == null || amount == null || x == null || y == null || plane == null) return null;
		return service.dropFor(token, owner.generation(), player, itemId, amount,
				x, y, plane);
	}

	@HostAccess.Export
	public ScriptTaskHandle after(double ticks, Value callback) {
		return service.schedule(token, ticks, false, callback);
	}

	@HostAccess.Export
	public ScriptTaskHandle every(double ticks, Value callback) {
		return service.schedule(token, ticks, true, callback);
	}

	/**
	 * Java-owned repeating task for host consumers (declarative boss poll).
	 * Registered in the encounter task list; never exported to guest code.
	 */
	public ScriptTaskHandle everyJava(double ticks, Runnable action,
			Runnable failureAction) {
		return service.scheduleJava(token, ticks, true, action, failureAction);
	}

	@HostAccess.Export
	public boolean contains(double x, double y, double plane) {
		return service.contains(token, x, y, plane);
	}

	@HostAccess.Export
	public int nextInt(double boundValue) {
		Integer bound = integral(boundValue, 1, 1_000_000);
		return bound == null ? -1 : service.nextInt(token, bound.intValue());
	}

	@HostAccess.Export
	public boolean chance(double numeratorValue, double denominatorValue) {
		Integer numerator = integral(numeratorValue, 0, 1_000_000);
		Integer denominator = integral(denominatorValue, 1, 1_000_000);
		if (numerator == null || denominator == null) {
			return false;
		}
		return service.chance(token, numerator.intValue(),
				denominator.intValue());
	}

	@HostAccess.Export
	public ScriptArray rollDrops(ScriptedPlayer player, double xValue,
			double yValue, double planeValue, double privateTicksValue,
			Value entries) {
		Integer x = integral(xValue, 0, 16383);
		Integer y = integral(yValue, 0, 16383);
		Integer plane = integral(planeValue, 0, 3);
		Integer privateTicks = integral(privateTicksValue, 1, 1000);
		if (player == null || x == null || y == null || plane == null
				|| privateTicks == null) {
			return new ScriptArray(new Object[0]);
		}
		return service.rollDrops(token, owner.generation(), player,
				x.intValue(), y.intValue(), plane.intValue(),
				privateTicks.intValue(), entries);
	}

	/**
	 * Java-owned drop roll over a copied typed entry list (host consumers).
	 * Uses the exact same owner-neutral transaction as the guest path; never
	 * exported to guest code.
	 */
	public ScriptArray rollDrops(ScriptedPlayer player, int x, int y, int plane,
			int privateTicks, java.util.List<ScriptDropEntry> entries) {
		return service.rollDrops(token, owner.generation(), player, x, y, plane,
				privateTicks, entries);
	}

	@HostAccess.Export
	public int distance(ScriptedPosition first, ScriptedPosition second) {
		return service.distance(token, first, second);
	}

	@HostAccess.Export
	public boolean isWalkable(double x, double y, double plane) {
		return service.isWalkable(token, x, y, plane);
	}

	@HostAccess.Export
	public boolean hasProjectilePath(double fromX, double fromY,
			double toX, double toY, double plane) {
		return service.hasProjectilePath(token, fromX, fromY, toX, toY,
				plane);
	}

	@HostAccess.Export
	public boolean close() {
		return service.close(token);
	}

	private static Integer integral(double value, int min, int max) {
		if (!Double.isFinite(value) || value != Math.rint(value)
				|| value < min || value > max) {
			return null;
		}
		return Integer.valueOf((int) value);
	}
}
