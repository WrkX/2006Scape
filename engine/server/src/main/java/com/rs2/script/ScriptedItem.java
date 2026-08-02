package com.rs2.script;

import com.rs2.game.items.DeprecatedItems;
import org.apollo.cache.def.ItemDefinition;
import org.graalvm.polyglot.HostAccess;

public class ScriptedItem {

    private final int id;

    private ScriptedItem(int id) {
        this.id = id;
    }

    @HostAccess.Export
    public int getId() {
        return id;
    }

    @HostAccess.Export
    public String getName() {
        ItemDefinition definition = ItemDefinition.lookup(id);
        String name = definition == null ? null : definition.getName();
        return name == null ? "Unknown item" : name;
    }

    @HostAccess.Export
    public boolean isStackable() {
        ItemDefinition definition = ItemDefinition.lookup(id);
        return definition != null && definition.isStackable();
    }

    @HostAccess.Export
    public boolean isNoted() {
        ItemDefinition definition = ItemDefinition.lookup(id);
        return definition != null && definition.isNote();
    }

    @HostAccess.Export
    public static ScriptedItem byId(int id) {
        return new ScriptedItem(id);
    }

    @HostAccess.Export
    public static ScriptedItem byName(String name) {
        int id = DeprecatedItems.getItemId(name);
        if (id == -1) {
            return null;
        }
        return new ScriptedItem(id);
    }
}
