package com.rs2.script.quest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Value;

import com.rs2.game.items.ItemConstants;
import com.rs2.script.quest.QuestDefinition.ExperienceReward;
import com.rs2.script.quest.QuestDefinition.ItemAmount;
import com.rs2.script.quest.QuestDefinition.Requirements;
import com.rs2.script.quest.QuestDefinition.Rewards;
import com.rs2.script.quest.QuestDefinition.SkillRequirement;
import com.rs2.script.quest.QuestDefinition.Stage;

/**
 * Strict one-way conversion from a guest descriptor to Java-owned data.
 */
public final class QuestDefinitionParser {

	private static final Pattern ID = Pattern.compile(
			"^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$");
	private static final double MAX_SAFE_INTEGER = 9007199254740991d;
	private static final int MAX_STAGES = 128;

	public QuestDefinition parse(Value definition) {
		requireObject(definition, "quest");
		only(definition, "quest", "id", "name", "summary", "stages",
				"requirements", "rewards");
		String id = boundedString(required(definition, "id"), "quest.id", 64);
		if (!ID.matcher(id).matches()) {
			throw failure("quest.id must be lower-case hyphenated ASCII");
		}
		String name = boundedString(required(definition, "name"),
				"quest.name", 128);
		String summary = boundedString(required(definition, "summary"),
				"quest.summary", 1024);
		List<Stage> stages = parseStages(required(definition, "stages"));
		Requirements requirements = definition.hasMember("requirements")
				? parseRequirements(definition.getMember("requirements"))
				: emptyRequirements();
		Rewards rewards = definition.hasMember("rewards")
				? parseRewards(definition.getMember("rewards")) : emptyRewards();
		return new QuestDefinition(id, name, summary, stages, requirements, rewards);
	}

	private List<Stage> parseStages(Value value) {
		requireArray(value, "quest.stages");
		long count = value.getArraySize();
		if (count < 1 || count > MAX_STAGES) {
			throw failure("quest.stages must contain 1-" + MAX_STAGES + " entries");
		}
		List<Stage> stages = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			Value stage = value.getArrayElement(i);
			requireObject(stage, "quest.stages[" + i + "]");
			only(stage, "quest.stages[" + i + "]", "stage", "objective");
			int number = safeInt(required(stage, "stage"),
					"quest.stages[" + i + "].stage", 0, MAX_STAGES - 1);
			if (number != i) {
				throw failure("quest stages must be exactly numbered 0..n-1");
			}
			String objective = boundedString(required(stage, "objective"),
					"quest.stages[" + i + "].objective", 512);
			stages.add(new Stage(number, objective));
		}
		return stages;
	}

	private Requirements parseRequirements(Value value) {
		requireObject(value, "quest.requirements");
		only(value, "quest.requirements", "questPoints", "completedQuests",
				"skills", "items");
		int points = optionalInt(value, "questPoints",
				"quest.requirements.questPoints", 0, 10000);
		List<String> quests = parseQuestIds(value, "completedQuests");
		List<SkillRequirement> skills = new ArrayList<>();
		if (present(value, "skills")) {
			Value array = value.getMember("skills");
			requireBoundedArray(array, "quest.requirements.skills", 21);
			Set<QuestSkill> seen = new HashSet<>();
			for (int i = 0; i < array.getArraySize(); i++) {
				Value item = array.getArrayElement(i);
				requireObject(item, "quest.requirements.skills[" + i + "]");
				only(item, "quest.requirements.skills[" + i + "]",
						"skill", "level");
				QuestSkill skill = QuestSkill.fromScriptName(boundedString(
						required(item, "skill"), "skill", 32));
				if (!seen.add(skill)) {
					throw failure("Duplicate skill requirement: "
							+ skill.getScriptName());
				}
				skills.add(new SkillRequirement(skill, safeInt(
						required(item, "level"), "skill level", 1, 99)));
			}
		}
		return new Requirements(points, quests, skills,
				parseItems(value, "items", "quest.requirements.items"));
	}

	private Rewards parseRewards(Value value) {
		requireObject(value, "quest.rewards");
		only(value, "quest.rewards", "questPoints", "items", "experience");
		int points = optionalInt(value, "questPoints",
				"quest.rewards.questPoints", 0, 10000);
		List<ExperienceReward> experience = new ArrayList<>();
		if (present(value, "experience")) {
			Value array = value.getMember("experience");
			requireBoundedArray(array, "quest.rewards.experience", 21);
			Set<QuestSkill> seen = new HashSet<>();
			for (int i = 0; i < array.getArraySize(); i++) {
				Value item = array.getArrayElement(i);
				requireObject(item, "quest.rewards.experience[" + i + "]");
				only(item, "quest.rewards.experience[" + i + "]",
						"skill", "amount");
				QuestSkill skill = QuestSkill.fromScriptName(boundedString(
						required(item, "skill"), "skill", 32));
				if (!seen.add(skill)) {
					throw failure("Duplicate experience reward: "
							+ skill.getScriptName());
				}
				experience.add(new ExperienceReward(skill, safeInt(
						required(item, "amount"), "experience amount",
						1, 200000000)));
			}
		}
		return new Rewards(points,
				parseItems(value, "items", "quest.rewards.items"), experience);
	}

	private List<ItemAmount> parseItems(Value parent, String member,
			String label) {
		List<ItemAmount> items = new ArrayList<>();
		if (!present(parent, member)) {
			return items;
		}
		Value array = parent.getMember(member);
		requireBoundedArray(array, label, 64);
		Set<Integer> seen = new HashSet<>();
		for (int i = 0; i < array.getArraySize(); i++) {
			Value item = array.getArrayElement(i);
			requireObject(item, label + "[" + i + "]");
			only(item, label + "[" + i + "]", "itemId", "amount");
			int id = safeInt(required(item, "itemId"), "itemId",
					1, ItemConstants.ITEM_LIMIT - 1);
			if (!seen.add(Integer.valueOf(id))) {
				throw failure("Duplicate item id: " + id);
			}
			items.add(new ItemAmount(id, safeInt(required(item, "amount"),
					"item amount", 1, Integer.MAX_VALUE)));
		}
		return items;
	}

	private List<String> parseQuestIds(Value parent, String member) {
		List<String> ids = new ArrayList<>();
		if (!present(parent, member)) {
			return ids;
		}
		Value array = parent.getMember(member);
		requireBoundedArray(array, "quest.requirements." + member, 64);
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < array.getArraySize(); i++) {
			String id = boundedString(array.getArrayElement(i),
					"completed quest id", 64);
			if (!ID.matcher(id).matches()) {
				throw failure("Invalid completed quest id: " + id);
			}
			if (!seen.add(id)) {
				throw failure("Duplicate completed quest id: " + id);
			}
			ids.add(id);
		}
		return ids;
	}

	private static int optionalInt(Value parent, String member, String label,
			int minimum, int maximum) {
		return present(parent, member) ? safeInt(parent.getMember(member), label,
				minimum, maximum) : 0;
	}

	private static int safeInt(Value value, String label, int minimum,
			int maximum) {
		if (value == null || value.isNull() || !value.isNumber()
				|| !value.fitsInDouble()) {
			throw failure(label + " must be a finite safe integer");
		}
		double number = value.asDouble();
		if (!Double.isFinite(number) || Math.rint(number) != number
				|| Math.abs(number) > MAX_SAFE_INTEGER
				|| number < minimum || number > maximum) {
			throw failure(label + " must be a finite safe integer in "
					+ minimum + ".." + maximum);
		}
		return (int) number;
	}

	private static String boundedString(Value value, String label, int maxBytes) {
		if (value == null || value.isNull() || !value.isString()) {
			throw failure(label + " must be a string");
		}
		String text = value.asString();
		int bytes = text.getBytes(StandardCharsets.UTF_8).length;
		if (bytes < 1 || bytes > maxBytes) {
			throw failure(label + " must be 1-" + maxBytes + " UTF-8 bytes");
		}
		return text;
	}

	private static Value required(Value parent, String member) {
		if (!present(parent, member)) {
			throw failure("Missing required member: " + member);
		}
		return parent.getMember(member);
	}

	private static boolean present(Value parent, String member) {
		return parent.hasMember(member) && parent.getMember(member) != null
				&& !parent.getMember(member).isNull();
	}

	private static void requireObject(Value value, String label) {
		if (value == null || value.isNull() || !value.hasMembers()
				|| value.hasArrayElements()) {
			throw failure(label + " must be an object");
		}
	}

	private static void requireArray(Value value, String label) {
		if (value == null || value.isNull() || !value.hasArrayElements()) {
			throw failure(label + " must be an array");
		}
	}

	private static void requireBoundedArray(Value value, String label,
			int maximum) {
		requireArray(value, label);
		if (value.getArraySize() > maximum) {
			throw failure(label + " must contain at most " + maximum + " entries");
		}
	}

	private static void only(Value value, String label, String... allowed) {
		Set<String> names = new HashSet<>(Arrays.asList(allowed));
		for (String member : value.getMemberKeys()) {
			if (!names.contains(member)) {
				throw failure(label + " has unsupported member: " + member);
			}
		}
	}

	private static Requirements emptyRequirements() {
		return new Requirements(0, new ArrayList<String>(),
				new ArrayList<SkillRequirement>(), new ArrayList<ItemAmount>());
	}

	private static Rewards emptyRewards() {
		return new Rewards(0, new ArrayList<ItemAmount>(),
				new ArrayList<ExperienceReward>());
	}

	private static QuestDefinitionException failure(String message) {
		return new QuestDefinitionException(message);
	}
}
