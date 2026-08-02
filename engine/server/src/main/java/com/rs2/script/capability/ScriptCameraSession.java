package com.rs2.script.capability;

import org.graalvm.polyglot.HostAccess;

import com.rs2.game.players.Player;
import com.rs2.script.world.ScriptEncounterService;

/** One composable camera lease. Position/look-at/shake share one token. */
public final class ScriptCameraSession {
    private final ScriptEncounterService service;
    private final Player player;
    private final long generation;
    private final long facadeEpoch;
    private final long token;

    public ScriptCameraSession(ScriptEncounterService service, Player player,
            long generation, long facadeEpoch, long token) {
        this.service = service;
        this.player = player;
        this.generation = generation;
        this.facadeEpoch = facadeEpoch;
        this.token = token;
    }

    @HostAccess.Export public String token() { return Long.toUnsignedString(token); }
    @HostAccess.Export public boolean isActive() { return service.isCameraActive(this); }

    @HostAccess.Export
    public boolean position(double localX, double localY, double height,
            double speed, double angle) {
        int[] values = cameraValues(localX, localY, height, speed, angle);
        if (values == null || !isActive() || player.getOutStream() == null) return false;
        player.getPlayerAssistant().sendCameraCutscene(values[0], values[1],
                values[2], values[3], values[4]);
        return true;
    }

    @HostAccess.Export
    public boolean lookAt(double localX, double localY, double height,
            double speed, double angle) {
        int[] values = cameraValues(localX, localY, height, speed, angle);
        if (values == null || !isActive() || player.getOutStream() == null) return false;
        player.getPlayerAssistant().sendCameraCutscene2(values[0], values[1],
                values[2], values[3], values[4]);
        return true;
    }

    @HostAccess.Export
    public boolean shake(double axis, double intensity, double speed,
            double frequency) {
        Integer axisValue = integral(axis, 0, 3);
        Integer intensityValue = integral(intensity, 0, 4);
        Integer speedValue = integral(speed, 0, 4);
        Integer frequencyValue = integral(frequency, 0, 4);
        if (axisValue == null || intensityValue == null || speedValue == null
                || frequencyValue == null || !isActive() || player.getOutStream() == null) return false;
        player.getPlayerAssistant().sendCameraShake(axisValue, intensityValue,
                speedValue, frequencyValue);
        return true;
    }

    @HostAccess.Export public boolean release() { return service.releaseCamera(this); }

    public Player player() { return player; }
    public long generation() { return generation; }
    public long facadeEpoch() { return facadeEpoch; }
    public long tokenValue() { return token; }

    private static int[] cameraValues(double localX, double localY, double height,
            double speed, double angle) {
        Integer x = integral(localX, 0, 103);
        Integer y = integral(localY, 0, 103);
        Integer h = integral(height, 0, 65535);
        Integer s = integral(speed, 0, 255);
        Integer a = integral(angle, 0, 255);
        return x == null || y == null || h == null || s == null || a == null
                ? null : new int[] {x, y, h, s, a};
    }

    private static Integer integral(double value, int min, int max) {
        return !Double.isFinite(value) || value != Math.rint(value)
                || value < min || value > max ? null : Integer.valueOf((int) value);
    }
}
