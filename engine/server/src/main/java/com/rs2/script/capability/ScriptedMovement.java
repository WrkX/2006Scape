package com.rs2.script.capability;

import org.graalvm.polyglot.HostAccess;

import com.rs2.game.players.Player;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.script.world.ScriptLockHandle;
import com.rs2.world.clip.Region;

/** Truthful scripted movement facade and movement-lock capability. */
public final class ScriptedMovement {

	private final Player player;
	private final long generation;
	private final long facadeEpoch;

	public ScriptedMovement(Player player, long generation, long facadeEpoch) {
		this.player = player;
		this.generation = generation;
		this.facadeEpoch = facadeEpoch;
	}

	@HostAccess.Export
	public ScriptLockHandle lock(double ticks) {
		return ScriptEncounterService.getInstance()
				.acquireMovementLock(player, generation, facadeEpoch, ticks);
	}

	@HostAccess.Export
	public boolean face(double xValue, double yValue) {
		Integer x = integral(xValue, 0, 16383);
		Integer y = integral(yValue, 0, 16383);
        if (x == null || y == null || !loaded(x, y) || !canMove()
                || !destination(x, y, player.heightLevel)) return false;
		player.turnPlayerTo(x.intValue(), y.intValue());
		return player.FocusPointX == 2 * x.intValue() + 1
				&& player.FocusPointY == 2 * y.intValue() + 1;
	}

	@HostAccess.Export
	public boolean walkTo(double xValue, double yValue) {
		Integer x = integral(xValue, 0, 16383);
		Integer y = integral(yValue, 0, 16383);
		if (x == null || y == null || !loaded(x, y) || !canMove()
				|| !destination(x, y, player.heightLevel)) return false;
		com.rs2.world.clip.PathFinder.RouteStep step =
				com.rs2.world.clip.PathFinder.findNextStep(player.absX, player.absY,
						player.heightLevel, x, y);
		if (step == null) return false;
        player.getPlayerAssistant().walkTo(step.x() - player.absX, step.y() - player.absY);
        int expectedX = player.getX() + step.x() - player.absX - player.mapRegionX * 8;
        int expectedY = player.getY() + step.y() - player.absY - player.mapRegionY * 8;
        return player.newWalkCmdSteps > 0
                && player.getNewWalkCmdX()[0] == expectedX
                && player.getNewWalkCmdY()[0] == expectedY;
	}

	@HostAccess.Export
	public boolean teleport(double xValue, double yValue, double planeValue) {
		Integer x = integral(xValue, 0, 16383);
		Integer y = integral(yValue, 0, 16383);
		Integer plane = integral(planeValue, 0, 3);
        if (x == null || y == null || plane == null || !loaded(x, y) || !canMove()
                || !destination(x, y, plane)) return false;
		return player.getPlayerAssistant().requestMovePlayer(
				x.intValue(), y.intValue(), plane.intValue());
	}

	@HostAccess.Export
	public int runEnergy() {
		return canMove() ? Math.max(0, Math.min(100, (int) Math.ceil(player.playerEnergy))) : 0;
	}

	@HostAccess.Export
	public boolean setRunEnergy(double value) {
		Integer energy = integral(value, 0, 100);
		if (energy == null || !canMove()) return false;
		player.playerEnergy = energy.intValue();
		player.getPlayerAssistant().writeEnergy();
		return true;
	}

	private boolean canMove() {
		ScriptEncounterService service = ScriptEncounterService.getInstance();
		return service.canMoveFacade(player, generation, facadeEpoch)
				&& !service.isMovementLocked(player);
	}

    private boolean destination(int x, int y, int plane) {
        return ScriptEncounterService.getInstance().canDestination(player, x, y, plane);
    }

    private static boolean loaded(int x, int y) {
        try { return Region.getRegion(x, y) != null; }
        catch (RuntimeException unavailable) { return false; }
    }

	private static Integer integral(double value, int min, int max) {
		return !Double.isFinite(value) || value != Math.rint(value)
				|| value < min || value > max ? null : Integer.valueOf((int) value);
	}
}
