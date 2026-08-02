package com.rs2.script.registries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRecord;
import com.rs2.script.definition.DefinitionRegistry;
import com.rs2.script.quest.QuestDefinition;
import com.rs2.script.quest.QuestDefinitionException;
import com.rs2.script.ScriptHost;

/**
 * Typed facade over the common definition envelope for Java-owned quest
 * descriptors keyed by stable quest id.
 */
public final class QuestRegistry {

	/**
	 * Registers {@code definition} for the quest named {@code name}.
	 */
	public static void put(String name, QuestDefinition definition) {
		DefinitionRecord previous = DefinitionRegistry.putQuest(definition);
		if (previous != null) {
			throw new QuestDefinitionException(
					"Duplicate quest registration: " + name
							+ "; existing record: " + previous);
		}
	}

	/**
	 * Returns the definition registered for the quest named {@code name} or
	 * {@code null}.
	 */
	public static QuestDefinition get(String name) {
		return ScriptHost.getInstance().readActiveRegistry(
				state -> get(state, name));
	}

	public static QuestDefinition get(RegistryStore.State state, String name) {
		DefinitionRecord record = DefinitionRegistry.get(state,
				DefinitionKind.QUEST, name);
		return record == null ? null : record.questPayload();
	}

	public static Map<String, QuestDefinition> all() {
		return ScriptHost.getInstance().readActiveRegistry(state ->
				java.util.Collections.unmodifiableMap(
						new java.util.TreeMap<String, QuestDefinition>(
								all(state))));
	}

	public static Map<String, QuestDefinition> all(
			RegistryStore.State state) {
		Map<String, QuestDefinition> quests =
				new java.util.TreeMap<String, QuestDefinition>();
		for (DefinitionRecord record
				: DefinitionRegistry.all(state, DefinitionKind.QUEST)
						.values()) {
			quests.put(record.key(), record.questPayload());
		}
		return quests;
	}

	/**
	 * Validates dependencies entirely against the staged candidate.
	 */
	public static void validateCandidate(RegistryStore.State candidate) {
		Map<String, QuestDefinition> quests = all(candidate);
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
		java.util.Iterator<com.rs2.script.definition.DefinitionKey> keys =
				RegistryStore.writable().definitions.keySet().iterator();
		while (keys.hasNext()) {
			if (keys.next().kind() == DefinitionKind.QUEST) {
				keys.remove();
			}
		}
	}

	private QuestRegistry() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}

}
