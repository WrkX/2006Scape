package com.rs2.script.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.graalvm.polyglot.HostAccess;

import com.rs2.game.items.GroundItem;
import com.rs2.script.ScriptedPosition;
import com.rs2.world.ItemHandler;

/** Exact logical ground-reward handle; non-stackable rewards own many identities. */
public final class ScriptGroundItemHandle {
    private final ItemHandler handler;
    private final List<GroundItem> identities;
    private final String token;
    private final int id;
    private final int initialAmount;
    private final int initialIdentityCount;
    private final ScriptedPosition position;

    public ScriptGroundItemHandle(ItemHandler handler, List<GroundItem> identities) {
        this.handler = handler;
        this.identities = Collections.unmodifiableList(new ArrayList<GroundItem>(identities));
        GroundItem first = this.identities.isEmpty() ? null : this.identities.get(0);
        this.token = first == null ? "0" : Long.toUnsignedString(first.getCreationToken());
        this.id = first == null ? 0 : first.getItemId();
        int amount = 0;
        for (GroundItem item : this.identities) amount += Math.max(0, item.getItemAmount());
        this.initialAmount = amount;
        this.initialIdentityCount = this.identities.size();
        this.position = first == null ? new ScriptedPosition(0, 0, 0)
                : new ScriptedPosition(first.getItemX(), first.getItemY(), first.getItemH());
    }

    @HostAccess.Export public String token() { return token; }
    @HostAccess.Export public int id() { return id; }
    @HostAccess.Export public int amount() { return initialAmount; }
    @HostAccess.Export public ScriptedPosition position() { return position; }
    @HostAccess.Export public int identityCount() { return initialIdentityCount; }
    @HostAccess.Export public boolean isAttached() {
        List<GroundItem> remaining = remaining();
        if (remaining.isEmpty()) return false;
        for (GroundItem item : remaining) if (item.isDetached()) return false;
        return true;
    }
    @HostAccess.Export public boolean isClaimed() {
        if (identities.isEmpty()) return true;
        for (GroundItem item : identities) if (!item.isClaimed()) return false;
        return true;
    }
    @HostAccess.Export public boolean detach(double ticksValue) {
        Integer ticks = integral(ticksValue, 1, 1000);
        List<GroundItem> remaining = remaining();
        if (ticks == null || remaining.isEmpty()) return false;
        return handler.detachExact(remaining, ticks.intValue());
    }
    @HostAccess.Export public boolean remove() {
        List<GroundItem> remaining = remaining();
        return !remaining.isEmpty() && handler.removeExact(remaining);
    }

    public List<GroundItem> identities() { return identities; }

    /** Snapshot of the exact identities still attached and unclaimed. */
    private List<GroundItem> remaining() {
        List<GroundItem> result = new ArrayList<GroundItem>();
        for (GroundItem item : identities) if (handler.containsExact(item)) result.add(item);
        return result;
    }
    private static Integer integral(double value, int min, int max) {
        return !Double.isFinite(value) || value != Math.rint(value) || value < min || value > max
                ? null : Integer.valueOf((int) value);
    }
}
