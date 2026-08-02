package com.rs2.script.registries;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.polyglot.Value;

import com.rs2.script.quest.QuestDefinition;

/**
 * Candidate builder and immutable registry snapshot.
 *
 * <p>The active snapshot is owned exclusively by {@code ScriptHost}. This
 * class never publishes a second active state.
 */
public final class RegistryStore {

	private static final ThreadLocal<State> STAGING = new ThreadLocal<>();
	private static final State EMPTY = freeze(new State());

	public static State beginStaging() {
		if (STAGING.get() != null) {
			throw new IllegalStateException(
					"A script registry transaction is already active");
		}
		State candidate = new State();
		STAGING.set(candidate);
		return candidate;
	}

	public static State finish(State candidate) {
		if (candidate == null || STAGING.get() != candidate) {
			throw new IllegalStateException(
					"Cannot finish an inactive script registry transaction");
		}
		STAGING.remove();
		return freeze(candidate);
	}

	public static void rollback(State candidate) {
		if (STAGING.get() == candidate) {
			STAGING.remove();
		}
	}

	public static State emptyState() {
		return EMPTY;
	}

	static State writable() {
		State candidate = STAGING.get();
		if (candidate == null) {
			throw new IllegalStateException(
					"Script registrations are allowed only while a candidate is loading");
		}
		return candidate;
	}

	private static State freeze(State state) {
		if (state.frozen) {
			return state;
		}
		state.bosses = immutable(state.bosses);
		state.quests = immutable(state.quests);
		state.raids = immutable(state.raids);
		state.areas = immutable(state.areas);
		state.npcHandlers = immutableNested(state.npcHandlers);
		state.objectHandlers = immutableNested(state.objectHandlers);
		state.itemHandlers = immutableNested(state.itemHandlers);
		state.itemOnItemHandlers = immutable(state.itemOnItemHandlers);
		state.itemOnObjectHandlers = immutable(state.itemOnObjectHandlers);
		state.itemOnNpcHandlers = immutable(state.itemOnNpcHandlers);
		state.commandHandlers = immutable(state.commandHandlers);
		state.lifecycleHandlers = immutable(state.lifecycleHandlers);
		state.npcDeathHandlers = immutable(state.npcDeathHandlers);
		state.itemPickupHandlers = immutable(state.itemPickupHandlers);
		state.buttonHandlers = immutable(state.buttonHandlers);
		state.itemOnGroundItemHandlers = immutable(state.itemOnGroundItemHandlers);
		state.itemOnPlayerHandlers = immutable(state.itemOnPlayerHandlers);
		state.magicOnItemHandlers = immutable(state.magicOnItemHandlers);
		state.magicOnObjectHandlers = immutable(state.magicOnObjectHandlers);
		state.lifecycleAreas = immutable(state.lifecycleAreas);
		state.areaEnterHandlers = immutable(state.areaEnterHandlers);
		state.areaLeaveHandlers = immutable(state.areaLeaveHandlers);
		state.frozen = true;
		return state;
	}

	private static <K, V> Map<K, V> immutable(Map<K, V> source) {
		return Collections.unmodifiableMap(new HashMap<K, V>(source));
	}

	private static <K, L, V> Map<K, Map<L, V>> immutableNested(
			Map<K, Map<L, V>> source) {
		Map<K, Map<L, V>> copy = new HashMap<K, Map<L, V>>();
		for (Map.Entry<K, Map<L, V>> entry : source.entrySet()) {
			copy.put(entry.getKey(), immutable(entry.getValue()));
		}
		return Collections.unmodifiableMap(copy);
	}

	public static final class State {
		Map<Integer, Value> bosses = new HashMap<Integer, Value>();
		Map<String, QuestDefinition> quests = new HashMap<String, QuestDefinition>();
		Map<String, Value> raids = new HashMap<String, Value>();
		Map<String, Value> areas = new HashMap<String, Value>();
		Map<Integer, Map<String, Value>> npcHandlers =
				new HashMap<Integer, Map<String, Value>>();
		Map<Integer, Map<String, Value>> objectHandlers =
				new HashMap<Integer, Map<String, Value>>();
		Map<Integer, Map<String, Value>> itemHandlers =
				new HashMap<Integer, Map<String, Value>>();
		Map<Long, Value> itemOnItemHandlers = new HashMap<Long, Value>();
		Map<Long, Value> itemOnObjectHandlers = new HashMap<Long, Value>();
		Map<Long, Value> itemOnNpcHandlers = new HashMap<Long, Value>();
		Map<String, Value> commandHandlers = new HashMap<String, Value>();
		Map<String, Value> lifecycleHandlers = new HashMap<String, Value>();
		Map<Integer, Value> npcDeathHandlers = new HashMap<Integer, Value>();
		Map<Integer, Value> itemPickupHandlers = new HashMap<Integer, Value>();
		Map<Integer, Value> buttonHandlers = new HashMap<Integer, Value>();
		Map<Long, Value> itemOnGroundItemHandlers = new HashMap<Long, Value>();
		Map<Integer, Value> itemOnPlayerHandlers = new HashMap<Integer, Value>();
		Map<Long, Value> magicOnItemHandlers = new HashMap<Long, Value>();
		Map<Long, Value> magicOnObjectHandlers = new HashMap<Long, Value>();
		Value playerDeathHandler;
		Map<String, ScriptArea> lifecycleAreas = new HashMap<String, ScriptArea>();
		Map<String, Value> areaEnterHandlers = new HashMap<String, Value>();
		Map<String, Value> areaLeaveHandlers = new HashMap<String, Value>();
		private boolean frozen;

		private State() {
		}
	}

	private RegistryStore() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
