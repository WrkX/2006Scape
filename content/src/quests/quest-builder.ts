/**
 * Author-side validation for the exact Java quest descriptor contract.
 * Java repeats every check and remains authoritative at script load.
 */

import type {
  QuestDefinition,
  QuestExperienceReward,
  QuestItemAmount,
  QuestRequirements,
  QuestRewards,
  QuestSkillRequirement,
  QuestStage,
} from "./types.js";

const ID = /^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/;
const SKILLS = new Set([
  "attack", "defence", "strength", "hitpoints", "ranged", "prayer",
  "magic", "cooking", "woodcutting", "fletching", "fishing",
  "firemaking", "crafting", "smithing", "mining", "herblore",
  "agility", "thieving", "slayer", "farming", "runecraft",
]);

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) throw new Error(`[quest-builder] ${message}`);
}

function safeInteger(value: number, label: string, min: number, max: number): void {
  assert(Number.isSafeInteger(value) && value >= min && value <= max,
    `${label} must be a safe integer in ${min}..${max}`);
}

function text(value: string, label: string, max: number): void {
  let bytes = 0;
  for (let index = 0; index < value.length; index++) {
    const code = value.charCodeAt(index);
    if (code <= 0x7f) bytes += 1;
    else if (code <= 0x7ff) bytes += 2;
    else if (code >= 0xd800 && code <= 0xdbff
      && index + 1 < value.length
      && value.charCodeAt(index + 1) >= 0xdc00
      && value.charCodeAt(index + 1) <= 0xdfff) {
      bytes += 4;
      index++;
    } else bytes += 3;
  }
  assert(bytes >= 1 && bytes <= max, `${label} must be 1-${max} UTF-8 bytes`);
}

function questId(value: string, label: string): void {
  text(value, label, 64);
  assert(ID.test(value), `${label} must be lower-case hyphenated ASCII`);
}

function unique<T>(values: readonly T[], key: (value: T) => unknown, label: string): void {
  const seen = new Set<unknown>();
  for (const value of values) {
    const id = key(value);
    assert(!seen.has(id), `duplicate ${label}: ${String(id)}`);
    seen.add(id);
  }
}

function validateItems(items: readonly QuestItemAmount[] | undefined, label: string): void {
  if (!items) return;
  assert(items.length <= 64, `${label} must contain at most 64 entries`);
  unique(items, (item) => item.itemId, `${label} item`);
  for (const item of items) {
    safeInteger(item.itemId, `${label}.itemId`, 1, 14999);
    safeInteger(item.amount, `${label}.amount`, 1, 2147483647);
  }
}

function validateRequirements(requirements: QuestRequirements | undefined): void {
  if (!requirements) return;
  if (requirements.questPoints !== undefined) {
    safeInteger(requirements.questPoints, "requirements.questPoints", 0, 10000);
  }
  if (requirements.completedQuests) {
    assert(requirements.completedQuests.length <= 64,
      "requirements.completedQuests must contain at most 64 entries");
    unique(requirements.completedQuests, (id) => id, "completed quest");
    requirements.completedQuests.forEach((id) => questId(id, "completed quest id"));
  }
  if (requirements.skills) {
    assert(requirements.skills.length <= 21,
      "requirements.skills must contain at most 21 entries");
    unique(requirements.skills, (entry) => entry.skill, "skill requirement");
    for (const entry of requirements.skills) {
      assert(SKILLS.has(entry.skill), `unknown skill ${entry.skill}`);
      safeInteger(entry.level, "skill level", 1, 99);
    }
  }
  validateItems(requirements.items, "requirements.items");
}

function validateRewards(rewards: QuestRewards | undefined): void {
  if (!rewards) return;
  if (rewards.questPoints !== undefined) {
    safeInteger(rewards.questPoints, "rewards.questPoints", 0, 10000);
  }
  validateItems(rewards.items, "rewards.items");
  if (rewards.experience) {
    assert(rewards.experience.length <= 21,
      "rewards.experience must contain at most 21 entries");
    unique(rewards.experience, (entry) => entry.skill, "experience reward");
    for (const entry of rewards.experience) {
      assert(SKILLS.has(entry.skill), `unknown skill ${entry.skill}`);
      safeInteger(entry.amount, "experience amount", 1, 200000000);
    }
  }
}

function freezeItems(values: readonly QuestItemAmount[] | undefined):
readonly QuestItemAmount[] | undefined {
  return values && Object.freeze(values.map((value) => Object.freeze({ ...value })));
}

function freezeSkills(values: readonly QuestSkillRequirement[] | undefined):
readonly QuestSkillRequirement[] | undefined {
  return values && Object.freeze(values.map((value) => Object.freeze({ ...value })));
}

function freezeExperience(values: readonly QuestExperienceReward[] | undefined):
readonly QuestExperienceReward[] | undefined {
  return values && Object.freeze(values.map((value) => Object.freeze({ ...value })));
}

export function createStage(stage: number, objective: string): QuestStage {
  safeInteger(stage, "stage", 0, 127);
  text(objective, "objective", 512);
  return Object.freeze({ stage, objective });
}

export function createQuest(definition: QuestDefinition): QuestDefinition {
  questId(definition.id, "quest.id");
  text(definition.name, "quest.name", 128);
  text(definition.summary, "quest.summary", 1024);
  assert(definition.stages.length >= 1 && definition.stages.length <= 128,
    "stages must contain 1-128 entries");
  definition.stages.forEach((stage, index) => {
    safeInteger(stage.stage, `stages[${index}].stage`, 0, 127);
    assert(stage.stage === index, "stages must be exactly numbered 0..n-1");
    text(stage.objective, `stages[${index}].objective`, 512);
  });
  validateRequirements(definition.requirements);
  validateRewards(definition.rewards);

  const requirements = definition.requirements && Object.freeze({
    ...definition.requirements,
    completedQuests: definition.requirements.completedQuests
      && Object.freeze([...definition.requirements.completedQuests]),
    skills: freezeSkills(definition.requirements.skills),
    items: freezeItems(definition.requirements.items),
  });
  const rewards = definition.rewards && Object.freeze({
    ...definition.rewards,
    items: freezeItems(definition.rewards.items),
    experience: freezeExperience(definition.rewards.experience),
  });
  return Object.freeze({
    id: definition.id,
    name: definition.name,
    summary: definition.summary,
    stages: Object.freeze(definition.stages.map((stage) => Object.freeze({ ...stage }))),
    requirements,
    rewards,
  });
}

export function registerQuest(definition: QuestDefinition): QuestDefinition {
  const quest = createQuest(definition);
  defineQuest(quest);
  return quest;
}
