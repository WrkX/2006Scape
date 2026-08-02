package com.rs2.script.registries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.rs2.script.quest.QuestDefinition;
import com.rs2.script.quest.QuestDefinitionException;
import com.rs2.script.ScriptHost;

/**
 * Stores scripted quest definitions keyed by quest name.
 */
public final class QuestRegistry {

	/**
	 * Registers {@code definition} for the quest named {@code name}.
	 */
	public static void put(String name, QuestDefinition definition) {
		QuestDefinition previous = RegistryStore.writable().quests.putIfAbsent(
				name, definition);
		if (previous != null) {
			throw new QuestDefinitionException(
					"Duplicate quest registration: " + name);
		}
	}

	/**
	 * Returns the definition registered for the quest named {@code name} or
	 * {@code null}.
	 */
	public static QuestDefinition get(String name) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> state.quests.get(name));
	}

	public static Map<String, QuestDefinition> all() {
		return ScriptHost.getInstance().readActiveRegistry(state ->
				java.util.Collections.unmodifiableMap(
						new java.util.TreeMap<String, QuestDefinition>(state.quests)));
	}

	/**
	 * Validates dependencies entirely against the staged candidate.
	 */
	public static void validateCandidate(RegistryStore.State candidate) {
		Map<String, QuestDefinition> quests = candidate.quests;
		for (QuestDefinition quest : quests.values()) {
			for (String dependency
					: quest.getRequirements().getCompletedQuests()) {
				if (quest.getId().equals(dependency)) {
					throw new QuestDefinitionException(
							"Quest depends on itself: " + quest.getId());
				}
				if (!quests.containsKey(dependency)) {
					throw new QuestDefinitionException("Quest " + quest.getId()
							+ " depends on missing quest " + dependency);
				}
			}
		}
		Map<String, Integer> marks = new HashMap<>();
		for (String id : new java.util.TreeSet<>(quests.keySet())) {
			visit(id, quests, marks, new HashSet<String>());
		}
	}

	private static void visit(String id, Map<String, QuestDefinition> quests,
			Map<String, Integer> marks, Set<String> path) {
		Integer mark = marks.get(id);
		if (mark != null && mark.intValue() == 2) {
			return;
		}
		if (mark != null && mark.intValue() == 1) {
			path.add(id);
			throw new QuestDefinitionException(
					"Quest dependency cycle: " + path);
		}
		marks.put(id, Integer.valueOf(1));
		path.add(id);
		for (String dependency
				: quests.get(id).getRequirements().getCompletedQuests()) {
			visit(dependency, quests, marks, new HashSet<>(path));
		}
		marks.put(id, Integer.valueOf(2));
	}

	/**
	 * Removes every registered quest. Intended for hot-reload.
	 */
	public static void clear() {
		RegistryStore.writable().quests.clear();
	}

	private QuestRegistry() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}

}
