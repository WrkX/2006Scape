package com.rs2.script.registries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.graalvm.polyglot.Value;
import com.rs2.script.ScriptHost;

/** Exact lifecycle handlers owned by the active script generation. */
public final class LifecycleRegistry {

	public static Value putPlayerDeath(Value handler) {
		RegistryStore.State state = RegistryStore.writable();
		synchronized (state) {
			Value previous = state.playerDeathHandler;
			if (previous == null) {
				state.playerDeathHandler = handler;
			}
			return previous;
		}
	}

	public static Value getPlayerDeath(RegistryStore.State state) {
		return state.playerDeathHandler;
	}

	public static Value getPlayerDeath() {
		return ScriptHost.getInstance().readActiveRegistry(
				LifecycleRegistry::getPlayerDeath);
	}

	public static Value putSingleton(String event, Value handler) {
		return RegistryStore.writable().lifecycleHandlers.putIfAbsent(event, handler);
	}

	public static Value getSingleton(String event) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> state.lifecycleHandlers.get(event));
	}

	public static Value putNpcDeath(int npcId, Value handler) {
		return RegistryStore.writable().npcDeathHandlers.putIfAbsent(npcId, handler);
	}

	public static Value getNpcDeath(int npcId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> state.npcDeathHandlers.get(npcId));
	}

	public static Value putItemPickup(int itemId, Value handler) {
		return RegistryStore.writable().itemPickupHandlers.putIfAbsent(itemId, handler);
	}

	public static Value getItemPickup(int itemId) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> state.itemPickupHandlers.get(itemId));
	}

	public static Value putArea(String event, ScriptArea area, Value handler) {
		RegistryStore.State state = RegistryStore.writable();
		Value previous = "enter".equals(event)
				? state.areaEnterHandlers.putIfAbsent(area.getId(), handler)
				: state.areaLeaveHandlers.putIfAbsent(area.getId(), handler);
		if (previous != null) {
			return previous;
		}
		ScriptArea existing = state.lifecycleAreas.putIfAbsent(area.getId(), area);
		if (existing != null && !sameArea(existing, area)) {
			if ("enter".equals(event)) {
				state.areaEnterHandlers.remove(area.getId(), handler);
			} else {
				state.areaLeaveHandlers.remove(area.getId(), handler);
			}
			throw new IllegalArgumentException(
					"area id '" + area.getId() + "' is already registered with different bounds");
		}
		return null;
	}

	public static Value getAreaHandler(String event, String areaId) {
		return ScriptHost.getInstance().readActiveRegistry(state ->
				"enter".equals(event)
						? state.areaEnterHandlers.get(areaId)
						: state.areaLeaveHandlers.get(areaId));
	}

	public static List<ScriptArea> areas() {
		List<ScriptArea> areas = ScriptHost.getInstance().readActiveRegistry(
				state -> new ArrayList<ScriptArea>(state.lifecycleAreas.values()));
		Collections.sort(areas, new Comparator<ScriptArea>() {
			@Override
			public int compare(ScriptArea first, ScriptArea second) {
				return first.getId().compareTo(second.getId());
			}
		});
		return Collections.unmodifiableList(areas);
	}

	private static boolean sameArea(ScriptArea first, ScriptArea second) {
		return first.getMinX() == second.getMinX()
				&& first.getMinY() == second.getMinY()
				&& first.getMaxX() == second.getMaxX()
				&& first.getMaxY() == second.getMaxY()
				&& (first.getPlane() == null ? second.getPlane() == null
						: first.getPlane().equals(second.getPlane()));
	}

	private LifecycleRegistry() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}
}
