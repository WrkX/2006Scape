package com.rs2.script.capability;

import org.graalvm.polyglot.HostAccess;

import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.net.PacketSender;
import com.rs2.script.BridgeValidation;
import com.rs2.script.ScriptEntityLimits;
import com.rs2.script.interfacehook.ScriptInterfaceHookRuntime;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.game.shops.ShopHandler;
import com.rs2.world.clip.PathFinder;
import com.rs2.world.clip.Region;
import org.apollo.cache.def.ItemDefinition;

/** Packet-backed presentation facade with validation and audience filtering. */
public final class ScriptedPresentation {
    private final Player player;
    private final long generation;
    private final long facadeEpoch;

    public ScriptedPresentation(Player player, long generation, long facadeEpoch) {
        this.player = player;
        this.generation = generation;
        this.facadeEpoch = facadeEpoch;
    }

    @HostAccess.Export
    public boolean animate(double animationValue, double delayValue) {
        Integer animation = integral(animationValue, -1, 65535);
        Integer delay = integral(delayValue, 0, 255);
        if (animation == null || delay == null || !mutate()) return false;
        player.startAnimation(animation.intValue(), delay.intValue());
        return true;
    }

    @HostAccess.Export
    public boolean graphic(double graphicValue, String height) {
        Integer graphic = integral(graphicValue, 0, 65535);
        if (graphic == null || (!"low".equals(height)
                && !"high".equals(height)) || !mutate()) return false;
        if ("high".equals(height)) player.gfx100(graphic.intValue());
        else player.gfx0(graphic.intValue());
        return true;
    }

    @HostAccess.Export
    public boolean forcedChat(String text) {
        if (text == null || text.length() == 0 || text.length() > 80 || !mutate()) return false;
        player.forcedChat(text);
        return true;
    }

    @HostAccess.Export
    public boolean sound(double soundValue, double volumeValue, double delayValue) {
        Integer sound = integral(soundValue, 0, 65535);
        Integer volume = integral(volumeValue, 0, 100);
        Integer delay = integral(delayValue, 0, 255);
        if (sound == null || volume == null || delay == null || !mutate()) return false;
        player.getPacketSender().sendSound(sound.intValue(), volume.intValue(), delay.intValue());
        return true;
    }

    @HostAccess.Export
    public boolean showInterface(double interfaceValue) {
        Integer id = integral(interfaceValue, 0, 65535);
        if (id == null || !mutate() || player.getOutStream() == null) return false;
        int previous = player.lastMainFrameInterface;
        if (previous >= 0 && previous != id.intValue() && player.scriptHookArmed) {
            // A scripted interface replacing another scripted one must run the
            // old hook's onClose teardown.
            ScriptInterfaceHookRuntime.getInstance().notifyClose(player,
                    previous);
        }
        boolean shown = player.getPacketSender().showInterface(id.intValue());
        if (shown) {
            // Only interfaces shown through this scripted path arm their hook
            // buttons; a legacy showInterface of the same id does not.
            player.scriptHookArmed = true;
            ScriptInterfaceHookRuntime.getInstance().notifyOpen(player,
                    id.intValue());
        }
        return shown;
    }

    @HostAccess.Export
    public boolean closeInterfaces() {
        if (!mutate() || player.getOutStream() == null) return false;
        player.getPacketSender().closeAllWindows();
        return true;
    }

    @HostAccess.Export
    public boolean setText(double componentValue, String text) {
        Integer component = integral(componentValue, 0, 65535);
        if (component == null || text == null || text.length() > 512 || !mutate()
                || player.getOutStream() == null) return false;
        player.getPacketSender().sendString(text, component.intValue());
        return text.equals(player.interfaceText(component.intValue()));
    }

    @HostAccess.Export
    public boolean setItemModel(double componentValue, double itemValue, double zoomValue) {
        Integer component = integral(componentValue, 0, 65535);
        Integer item = integral(itemValue, 0, ScriptEntityLimits.MAX_ITEM_ID);
        Integer zoom = integral(zoomValue, 1, 2000);
        if (component == null || item == null || zoom == null || !definition(item.intValue()) || !mutate()
                || player.getOutStream() == null) return false;
        player.getPacketSender().sendItemOnInterface(item.intValue(), zoom.intValue(), component.intValue());
        return true;
    }

    @HostAccess.Export
    public boolean setConfig(double configValue, double stateValue) {
        Integer config = integral(configValue, 0, 65535);
        Integer state = BridgeValidation.integral(stateValue, -1_000_000,
                1_000_000);
        if (config == null || state == null || !mutate()
                || player.getOutStream() == null) return false;
        player.getPacketSender().sendConfig(config.intValue(), state.intValue());
        return true;
    }

    @HostAccess.Export
    public boolean setChildHidden(double componentValue, boolean hidden) {
        Integer component = integral(componentValue, 0, 65535);
        if (component == null || !mutate() || player.getOutStream() == null) {
            return false;
        }
        player.getPacketSender().sendHideInterfaceLayer(component.intValue(),
                hidden);
        return true;
    }

    @HostAccess.Export
    public boolean openStaticShop(double shopValue) {
        Integer id = integral(shopValue, 0, ShopHandler.MAX_SHOPS - 1);
        if (id == null || !mutate() || !ShopHandler.isStaticShop(id.intValue())
                || player.inTrade || player.inDuel || player.openDuel
                || ShopHandler.playerOwnsStore(id.intValue(), player)) return false;
        player.getShopAssistant().openShop(id.intValue());
        return player.shopId == id.intValue() && player.isShopping;
    }

    @HostAccess.Export
    public boolean openScriptShop(String shopId) {
        if (shopId == null || shopId.length() > 64 || !mutate()
                || player.getOutStream() == null) return false;
        return com.rs2.script.shop.ScriptShopRuntime.getInstance()
                .open(player, shopId);
    }

    @HostAccess.Export
    public boolean stillGraphic(double graphicValue, double xValue, double yValue,
            double planeValue, double heightValue, double delayValue, String audience) {
        Integer graphic = integral(graphicValue, 0, 65535);
        Integer x = integral(xValue, 0, 16383);
        Integer y = integral(yValue, 0, 16383);
        Integer plane = integral(planeValue, 0, 3);
        Integer height = integral(heightValue, 0, 255);
        Integer delay = integral(delayValue, 0, 255);
        if (graphic == null || x == null || y == null || plane == null || height == null
                || delay == null || plane.intValue() != player.heightLevel
                || !near(x.intValue(), y.intValue()) || !loaded(x.intValue(), y.intValue())
                || !ScriptEncounterService.getInstance().canPresentAt(player, generation,
                        facadeEpoch, x.intValue(), y.intValue(), plane.intValue())
                || !audience(audience) || !mutate()) return false;
        for (Player target : recipients(audience, plane.intValue(), x.intValue(), y.intValue())) {
            if (target.getOutStream() != null) target.getPacketSender().createStillGfx(
                    graphic.intValue(), x.intValue(), y.intValue(), height.intValue(), delay.intValue());
        }
        return true;
    }

    @HostAccess.Export
    public boolean projectile(double graphicValue, double fromXValue, double fromYValue,
            double toXValue, double toYValue, double planeValue, double angleValue,
            double speedValue, double startHeightValue, double endHeightValue,
            double delayValue, String audience) {
        Integer graphic = integral(graphicValue, 0, 65535);
        Integer fromX = integral(fromXValue, 0, 16383);
        Integer fromY = integral(fromYValue, 0, 16383);
        Integer toX = integral(toXValue, 0, 16383);
        Integer toY = integral(toYValue, 0, 16383);
        Integer plane = integral(planeValue, 0, 3);
        Integer angle = integral(angleValue, 0, 255);
        Integer speed = integral(speedValue, 1, 255);
        Integer start = integral(startHeightValue, 0, 255);
        Integer end = integral(endHeightValue, 0, 255);
        Integer delay = integral(delayValue, 0, 255);
        if (graphic == null || fromX == null || fromY == null || toX == null || toY == null
                || plane == null || angle == null || speed == null || start == null || end == null
                || delay == null || plane.intValue() != player.heightLevel
                || !near(fromX.intValue(), fromY.intValue()) || !near(toX.intValue(), toY.intValue())
                || Math.max(Math.abs(toX.intValue() - fromX.intValue()),
                        Math.abs(toY.intValue() - fromY.intValue())) > 25
                || !loaded(fromX.intValue(), fromY.intValue())
                || !loaded(toX.intValue(), toY.intValue())
                || !ScriptEncounterService.getInstance().canPresentAt(player, generation,
                        facadeEpoch, fromX.intValue(), fromY.intValue(), plane.intValue())
                || !ScriptEncounterService.getInstance().canPresentAt(player, generation,
                        facadeEpoch, toX.intValue(), toY.intValue(), plane.intValue())
                || !PathFinder.isProjectilePathClear(fromX.intValue(), fromY.intValue(),
                        plane.intValue(), toX.intValue(), toY.intValue())
                || !audience(audience) || !mutate()) return false;
        int offX = toX.intValue() - fromX.intValue();
        int offY = toY.intValue() - fromY.intValue();
        for (Player target : recipients(audience, plane.intValue(), fromX.intValue(), fromY.intValue())) {
            if (target.getOutStream() != null) target.getPacketSender().createProjectile(
                    fromX.intValue(), fromY.intValue(), offX, offY, angle.intValue(), speed.intValue(),
                    graphic.intValue(), start.intValue(), end.intValue(), 0, delay.intValue());
        }
        return true;
    }

    @HostAccess.Export
    public ScriptCameraSession beginCamera(double ticksValue) {
        Integer ticks = integral(ticksValue, 1, 100000);
        return ticks == null ? null : ScriptEncounterService.getInstance().beginCamera(
                player, generation, facadeEpoch, ticks.intValue());
    }

    @HostAccess.Export
    public boolean resetCamera() {
        return ScriptEncounterService.getInstance().resetCamera(player, generation, facadeEpoch);
    }

    private boolean mutate() {
        return ScriptEncounterService.getInstance().canMutate(player, generation, facadeEpoch);
    }

    private boolean audience(String value) { return "self".equals(value) || "nearby".equals(value); }

    private boolean near(int x, int y) {
        return Math.max(Math.abs(player.absX - x), Math.abs(player.absY - y)) <= 25;
    }

    private static boolean loaded(int x, int y) {
        try { return Region.getRegion(x, y) != null; }
        catch (RuntimeException unavailable) { return false; }
    }

    private static boolean definition(int id) {
        try { return ItemDefinition.exists(id); }
        catch (RuntimeException failure) { return false; }
    }

    private java.util.List<Player> recipients(String value, int plane, int x, int y) {
        java.util.List<Player> out = new java.util.ArrayList<Player>();
        if ("self".equals(value)) { out.add(player); return out; }
        ScriptEncounterService service = ScriptEncounterService.getInstance();
        for (Player candidate : PlayerHandler.players) {
            if (candidate != null && candidate.heightLevel == plane
                    && Math.max(Math.abs(candidate.absX - x), Math.abs(candidate.absY - y)) <= 25
                    && service.canObserve(player, candidate)) out.add(candidate);
        }
        return out;
    }

    private static Integer integral(double value, int min, int max) {
        return !Double.isFinite(value) || value != Math.rint(value) || value < min || value > max
                ? null : Integer.valueOf((int) value);
    }
}
