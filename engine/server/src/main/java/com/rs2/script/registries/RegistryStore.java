package com.rs2.script.registries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Value;

import com.rs2.script.definition.DefinitionKey;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.definition.ModuleRecord;
import com.rs2.script.quest.QuestDefinition;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.ExecutableRouteRecord;

/**
 * Candidate builder and immutable registry snapshot.
 *
 * <p>The active snapshot is owned exclusively by {@code ScriptHost}. This
 * class never publishes a second active state. One candidate owns one
 * definition envelope map, one unified executable-route map, the ordered
 * content-module manifest, and the lifecycle observer maps. Lifecycle
 * observers are deliberately not routes: they never own a
 * consumed-versus-legacy decision.
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

	/** Returns {@code true} while a candidate is being loaded. */
	public static boolean isStagingActive() {
		return STAGING.get() != null;
	}

	/** Records one registered content module in the active candidate. */
	public static void recordModule(ModuleRecord record) {
		writable().manifest.add(record);
	}

	/** Returns {@code true} when the active candidate already registered the id. */
	public static boolean hasModuleRecord(String id) {
		State state = STAGING.get();
		if (state == null) {
			return false;
		}
		for (ModuleRecord record : state.manifest) {
			if (record.id().equals(id)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Candidate registration gate used by every definition and route facade.
	 * Only the loading thread may mutate the staged snapshot.
	 */
	public static State writable() {
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
		state.definitions = Collections.unmodifiableMap(
				new HashMap<DefinitionKey, DefinitionRecord>(state.definitions));
		state.routes = Collections.unmodifiableMap(
				new HashMap<ExecutableRouteKey, ExecutableRouteRecord>(state.routes));
		state.manifest = Collections.unmodifiableList(
				new ArrayList<ModuleRecord>(state.manifest));
		state.lifecycleHandlers = immutable(state.lifecycleHandlers);
		state.npcDeathHandlers = immutable(state.npcDeathHandlers);
		state.itemPickupHandlers = immutable(state.itemPickupHandlers);
		state.lifecycleAreas = immutable(state.lifecycleAreas);
		state.areaEnterHandlers = immutable(state.areaEnterHandlers);
		state.areaLeaveHandlers = immutable(state.areaLeaveHandlers);
		state.frozen = true;
		return state;
	}

	private static <K, V> Map<K, V> immutable(Map<K, V> source) {
		return Collections.unmodifiableMap(new HashMap<K, V>(source));
	}

	public static final class State {

		/** Every registered definition envelope, keyed by kind and stable key. */
		public Map<DefinitionKey, DefinitionRecord> definitions =
				new HashMap<DefinitionKey, DefinitionRecord>();

		/** Every executable guest or host route, keyed by exact key. */
		public Map<ExecutableRouteKey, ExecutableRouteRecord> routes =
				new HashMap<ExecutableRouteKey, ExecutableRouteRecord>();

		/** Ordered registered content modules; hooks run in this order. */
		public List<ModuleRecord> manifest = new ArrayList<ModuleRecord>();

		Map<String, Value> lifecycleHandlers = new HashMap<String, Value>();
		Map<Integer, Value> npcDeathHandlers = new HashMap<Integer, Value>();
		Map<Integer, Value> itemPickupHandlers = new HashMap<Integer, Value>();
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
