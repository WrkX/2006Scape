/**
 * Quest builder — validated factory for {@link QuestDefinition} with ergonomic
 * stage, requirement, and reward authoring.
 *
 * Content authors use `createQuest()` to build a validated definition, then
 * pass it to the global `defineQuest()` bridge.  The `questBuilder()` fluent
 * interface lets you construct quests step by step with automatic validation.
 *
 * @module quests/quest-builder
 *
 * @example Manual construction
 * ```ts
 * import { createQuest, createStage } from "./quest-builder.js";
 * import type { Player } from "../core/player.js";
 *
 * const dragonAwakens = createQuest({
 *   id: "dragon_awakens",
 *   name: "Dragon Awakens",
 *   difficulty: "master",
 *   requirements: {
 *     quests: ["dragon_slayer"],
 *     skills: { defence: 70, magic: 60 },
 *     combatLevel: 85,
 *   },
 *   startPoint: { x: 3000, y: 5000, plane: 0 },
 *   stages: [
 *     createStage({
 *       id: "start",
 *       description: "Speak to the elder wizard.",
 *       onEnter: (p) => p.message("Find the elder near the volcano."),
 *       condition: (p) => p.quests.getStage("dragon_awakens") > 0,
 *     }),
 *     createStage({
 *       id: "collect_scales",
 *       description: "Collect 5 dragon scales.",
 *       onEnter: (p) => p.message("Dragons have been spotted to the east."),
 *       condition: (p) => p.inventory.contains("dragon_scale", 5),
 *     }),
 *     createStage({
 *       id: "completed",
 *       description: "Quest complete!",
 *       onEnter: (_p) => {},
 *       condition: () => true,
 *     }),
 *   ],
 *   rewards: {
 *     experience: { defence: 50000, magic: 30000 },
 *     items: { dragon_token: 1 },
 *     questPoints: 3,
 *     unlocks: ["dragon_island_access"],
 *   },
 * });
 *
 * defineQuest(dragonAwakens);
 * ```
 *
 * @example Fluent builder
 * ```ts
 * import { questBuilder } from "./quest-builder.js";
 *
 * const quest = questBuilder("lost_artifacts")
 *   .name("Lost Artifacts")
 *   .difficulty("intermediate")
 *   .requiresQuest("goblin_diplomacy")
 *   .requiresSkill("thieving", 40)
 *   .requiresCombatLevel(50)
 *   .startsAt({ x: 2500, y: 3500, plane: 0 })
 *   .stage("investigate", "Search the ruins for clues.",
 *     (p) => p.message("The ruins look ancient..."))
 *   .stageCompleteCondition("investigate", (p) =>
 *     p.inventory.contains("ancient_clue"))
 *   .stage("retrieve", "Retrieve the artifact from the cave.",
 *     (p) => p.message("The cave is dark and damp."))
 *   .stageCompleteCondition("retrieve", (p) =>
 *     p.inventory.contains("lost_artifact"))
 *   .reward({ experience: { thieving: 15000 }, questPoints: 2 })
 *   .build();
 *
 * defineQuest(quest);
 * ```
 */

import type { Player } from "../core/player.js";
import type {
  ItemId,
  SkillId,
  WorldPoint,
  Dialogue,
} from "../core/types.js";
import type {
  QuestDefinition,
  QuestDifficulty,
  QuestRequirements,
  QuestRewards,
  QuestStage,
} from "./types.js";

// ─── Internal helpers ─────────────────────────────────────────────────────────

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[quest-builder] ${message}`);
  }
}

// ─── Stage factory ────────────────────────────────────────────────────────────

/**
 * Options for building a single quest stage.
 *
 * Mirrors {@link QuestStage} but makes every field explicit so the factory
 * can apply validation and defaults.
 */
export interface StageOptions {
  readonly id: string;
  readonly description: string;
  readonly onEnter: (player: Player) => void;
  readonly condition: (player: Player) => boolean;
  readonly onComplete?: (player: Player) => void;
  readonly onTick?: (player: Player) => void;
}

/**
 * Create a single validated quest stage.
 *
 * @param options  Stage configuration.
 * @returns A frozen {@link QuestStage}.
 */
export function createStage(options: StageOptions): QuestStage {
  assert(typeof options.id === "string" && options.id.length > 0,
    `Stage id must be a non-empty string`);
  assert(typeof options.description === "string" && options.description.length > 0,
    `Stage "${options.id}": description must be a non-empty string`);
  assert(typeof options.onEnter === "function",
    `Stage "${options.id}": onEnter must be a function`);
  assert(typeof options.condition === "function",
    `Stage "${options.id}": condition must be a function`);

  if (options.onComplete !== undefined) {
    assert(typeof options.onComplete === "function",
      `Stage "${options.id}": onComplete must be a function`);
  }
  if (options.onTick !== undefined) {
    assert(typeof options.onTick === "function",
      `Stage "${options.id}": onTick must be a function`);
  }

  return Object.freeze({
    id: options.id,
    description: options.description,
    onEnter: options.onEnter,
    condition: options.condition,
    onComplete: options.onComplete,
    onTick: options.onTick,
  });
}

// ─── Quest factory ────────────────────────────────────────────────────────────

/**
 * Options accepted by {@link createQuest}.
 */
export interface QuestOptions {
  readonly id: string;
  readonly name: string;
  readonly difficulty: QuestDifficulty;
  readonly requirements: QuestRequirements;
  readonly startPoint: WorldPoint;
  readonly startNpc?: {
    readonly npcId: number;
    readonly dialogue: Dialogue;
  };
  readonly stages: readonly QuestStage[];
  readonly rewards: QuestRewards;
  readonly onGlobalComplete?: (player: Player) => void;
}

const VALID_DIFFICULTIES: ReadonlySet<string> = new Set([
  "novice",
  "intermediate",
  "experienced",
  "master",
  "grandmaster",
]);

const VALID_SKILL_IDS: ReadonlySet<string> = new Set([
  "attack", "defence", "strength", "hitpoints", "ranged", "prayer",
  "magic", "cooking", "woodcutting", "fletching", "fishing", "firemaking",
  "crafting", "smithing", "mining", "herblore", "agility", "thieving",
  "slayer", "farming", "runecraft",
]);

/**
 * Create a fully validated {@link QuestDefinition}.
 *
 * Validation rules:
 * - `id` must be a non-empty, lower_snake_case string.
 * - `name` must be non-empty.
 * - `difficulty` must be one of the five difficulty tiers.
 * - Skill requirements must use valid skill ids with positive levels.
 * - `stages` must have at least one entry and unique stage ids.
 * - Experience rewards must use valid skill ids with positive amounts.
 * - `startPoint` coordinates must be finite numbers.
 *
 * @param options  Quest configuration.
 * @returns A frozen {@link QuestDefinition}.
 */
export function createQuest(options: QuestOptions): QuestDefinition {
  assert(typeof options.id === "string" && options.id.length > 0,
    "Quest id must be a non-empty string");
  assert(/^[a-z][a-z0-9_]*$/.test(options.id),
    `Quest id "${options.id}" must be lower_snake_case`);
  assert(typeof options.name === "string" && options.name.length > 0,
    `Quest "${options.id}": name must be a non-empty string`);
  assert(VALID_DIFFICULTIES.has(options.difficulty),
    `Quest "${options.id}": difficulty "${options.difficulty}" is not valid. ` +
    `Must be one of: ${[...VALID_DIFFICULTIES].join(", ")}`);

  // Validate requirements
  const reqs = options.requirements;
  assert(reqs !== undefined && typeof reqs === "object",
    `Quest "${options.id}": requirements must be an object`);

  if (reqs.quests) {
    assert(Array.isArray(reqs.quests),
      `Quest "${options.id}": requirements.quests must be an array`);
    for (let i = 0; i < reqs.quests.length; i++) {
      const q = reqs.quests[i];
      assert(typeof q === "string" && q.length > 0,
        `Quest "${options.id}": requirements.quests[${i}] must be a non-empty string`);
    }
  }

  if (reqs.skills) {
    for (const [skill, level] of Object.entries(reqs.skills)) {
      assert(VALID_SKILL_IDS.has(skill),
        `Quest "${options.id}": unknown skill "${skill}" in requirements`);
      assert(typeof level === "number" && level > 0 && level <= 99,
        `Quest "${options.id}": skill requirement "${skill}" must be 1-99, got ${level}`);
    }
  }

  if (reqs.combatLevel !== undefined) {
    assert(typeof reqs.combatLevel === "number" && reqs.combatLevel > 0,
      `Quest "${options.id}": combatLevel requirement must be positive, got ${reqs.combatLevel}`);
  }

  if (reqs.items) {
    assert(Array.isArray(reqs.items),
      `Quest "${options.id}": requirements.items must be an array`);
    for (let i = 0; i < reqs.items.length; i++) {
      const item = reqs.items[i];
      assert((typeof item === "number" && item > 0) || (typeof item === "string" && item.length > 0),
        `Quest "${options.id}": requirements.items[${i}] must be a positive number or non-empty string`);
    }
  }

  // Validate startPoint
  const sp = options.startPoint;
  assert(sp !== undefined &&
    Number.isFinite(sp.x) && Number.isFinite(sp.y) &&
    Number.isFinite(sp.plane ?? 0),
    `Quest "${options.id}": startPoint must have finite x, y, and plane values`);

  // Validate startNpc
  if (options.startNpc) {
    assert(Number.isInteger(options.startNpc.npcId) && options.startNpc.npcId > 0,
      `Quest "${options.id}": startNpc.npcId must be a positive integer`);
    assert(options.startNpc.dialogue !== undefined &&
      typeof options.startNpc.dialogue.type === "string",
      `Quest "${options.id}": startNpc.dialogue must be a valid Dialogue`);
  }

  // Validate stages
  assert(Array.isArray(options.stages) && options.stages.length > 0,
    `Quest "${options.id}": stages must be a non-empty array`);
  const stageIds = new Set<string>();
  for (let i = 0; i < options.stages.length; i++) {
    const stage = options.stages[i];
    assert(typeof stage.id === "string" && stage.id.length > 0,
      `Quest "${options.id}": stages[${i}] must have a non-empty id`);
    assert(!stageIds.has(stage.id),
      `Quest "${options.id}": duplicate stage id "${stage.id}"`);
    stageIds.add(stage.id);
  }

  // Validate rewards
  const rewards = options.rewards;
  assert(rewards !== undefined && typeof rewards === "object",
    `Quest "${options.id}": rewards must be an object`);

  if (rewards.experience) {
    for (const [skill, xp] of Object.entries(rewards.experience)) {
      assert(VALID_SKILL_IDS.has(skill),
        `Quest "${options.id}": unknown skill "${skill}" in rewards.experience`);
      assert(typeof xp === "number" && xp > 0,
        `Quest "${options.id}": experience reward for "${skill}" must be positive, got ${xp}`);
    }
  }

  if (rewards.questPoints !== undefined) {
    assert(typeof rewards.questPoints === "number" && rewards.questPoints > 0,
      `Quest "${options.id}": questPoints must be positive, got ${rewards.questPoints}`);
  }

  if (rewards.unlocks) {
    assert(Array.isArray(rewards.unlocks),
      `Quest "${options.id}": rewards.unlocks must be an array`);
    for (let i = 0; i < rewards.unlocks.length; i++) {
      assert(typeof rewards.unlocks[i] === "string" && rewards.unlocks[i].length > 0,
        `Quest "${options.id}": rewards.unlocks[${i}] must be a non-empty string`);
    }
  }

  if (rewards.accessFlags) {
    assert(Array.isArray(rewards.accessFlags),
      `Quest "${options.id}": rewards.accessFlags must be an array`);
    for (let i = 0; i < rewards.accessFlags.length; i++) {
      assert(typeof rewards.accessFlags[i] === "string" && rewards.accessFlags[i].length > 0,
        `Quest "${options.id}": rewards.accessFlags[${i}] must be a non-empty string`);
    }
  }

  // Validate onGlobalComplete
  if (options.onGlobalComplete !== undefined) {
    assert(typeof options.onGlobalComplete === "function",
      `Quest "${options.id}": onGlobalComplete must be a function`);
  }

  return Object.freeze({
    id: options.id,
    name: options.name,
    difficulty: options.difficulty,
    requirements: options.requirements,
    startPoint: options.startPoint,
    startNpc: options.startNpc,
    stages: options.stages,
    rewards: options.rewards,
    onGlobalComplete: options.onGlobalComplete,
  });
}

/**
 * Validate a quest definition and immediately register it via the global
 * `defineQuest()` bridge function.
 *
 * Equivalent to calling `defineQuest(createQuest(options))`.
 *
 * @param options  Quest configuration.
 */
export function registerQuest(options: QuestOptions): void {
  defineQuest(createQuest(options));
}

// ─── Fluent builder ───────────────────────────────────────────────────────────

/**
 * Intermediate state for building a quest stage within the fluent builder.
 * Stage ids are tracked via the generic `S` parameter so completions and
 * conditions remain type-checked.
 */
interface StageBlueprint {
  id: string;
  description: string;
  onEnter: (player: Player) => void;
  condition: ((player: Player) => boolean) | null;
  onComplete?: (player: Player) => void;
  onTick?: (player: Player) => void;
}

/**
 * Fluent builder for constructing a {@link QuestDefinition} step by step.
 *
 * Tracks which fields have been populated to catch missing data at build time.
 *
 * @typeParam S  Union of stage ids that have been registered so far.
 *
 * @example
 * ```ts
 * questBuilder("my_quest")
 *   .name("My Quest")
 *   .difficulty("novice")
 *   .startsAt({ x: 0, y: 0, plane: 0 })
 *   .stage("one", "Do the thing.", p => p.message("Go!"))
 *   .stageCompleteCondition("one", p => p.inventory.contains("thing"))
 *   .reward({ questPoints: 1 })
 *   .build();
 * ```
 */
export class QuestBuilder<S extends string = never> {
  private _id: string;
  private _name: string | null = null;
  private _difficulty: QuestDifficulty | null = null;
  private _requirements: QuestRequirements = {};
  private _startPoint: WorldPoint | null = null;
  private _startNpc: { npcId: number; dialogue: Dialogue } | null = null;
  private _stages: StageBlueprint[] = [];
  private _rewards: QuestRewards | null = null;
  private _onGlobalComplete: ((player: Player) => void) | null = null;

  constructor(id: string) {
    assert(typeof id === "string" && id.length > 0,
      "Quest id must be a non-empty string");
    this._id = id;
  }

  /** Set the display name shown in the quest journal. */
  name(name: string): this {
    assert(typeof name === "string" && name.length > 0,
      "Quest name must be a non-empty string");
    this._name = name;
    return this;
  }

  /** Set the difficulty classification. */
  difficulty(d: QuestDifficulty): this {
    assert(VALID_DIFFICULTIES.has(d),
      `Unknown difficulty "${d}". Must be one of: ${[...VALID_DIFFICULTIES].join(", ")}`);
    this._difficulty = d;
    return this;
  }

  /** Require another quest to be completed first. */
  requiresQuest(questId: string): this {
    assert(typeof questId === "string" && questId.length > 0,
      "Quest requirement must be a non-empty string");
    const currentQuests = this._requirements.quests ?? [];
    this._requirements = { ...this._requirements, quests: [...currentQuests, questId] };
    return this;
  }

  /** Require a minimum skill level. */
  requiresSkill(skill: SkillId, level: number): this {
    assert(VALID_SKILL_IDS.has(skill), `Unknown skill "${skill}"`);
    assert(typeof level === "number" && level > 0 && level <= 99,
      `Skill requirement must be 1-99, got ${level}`);
    this._requirements = {
      ...this._requirements,
      skills: { ...this._requirements.skills, [skill]: level },
    };
    return this;
  }

  /** Require a minimum combat level. */
  requiresCombatLevel(level: number): this {
    assert(typeof level === "number" && level > 0,
      `Combat level requirement must be positive, got ${level}`);
    this._requirements = { ...this._requirements, combatLevel: level };
    return this;
  }

  /** Set the world coordinates where the quest begins. */
  startsAt(point: WorldPoint): this {
    assert(Number.isFinite(point.x) && Number.isFinite(point.y),
      "startPoint coordinates must be finite numbers");
    this._startPoint = point;
    return this;
  }

  /** Set an NPC that starts the quest via dialogue. */
  startNpc(npcId: number, dialogue: Dialogue): this {
    assert(Number.isInteger(npcId) && npcId > 0,
      `startNpc.npcId must be a positive integer, got ${npcId}`);
    this._startNpc = { npcId, dialogue };
    return this;
  }

  /**
   * Add a quest stage.
   *
   * The stage id becomes part of the generic parameter `S` so later calls
   * (e.g. `stageCompleteCondition`) can reference it safely.
   */
  stage(
    id: string,
    description: string,
    onEnter: (player: Player) => void,
  ): QuestBuilder<S | string> {
    assert(typeof id === "string" && id.length > 0,
      "Stage id must be a non-empty string");
    assert(!this._stages.some(s => s.id === id),
      `Duplicate stage id "${id}"`);
    assert(typeof onEnter === "function",
      `Stage "${id}": onEnter must be a function`);

    this._stages.push({ id, description, onEnter, condition: null });
    return this as QuestBuilder<S | string>;
  }

  /**
   * Set the completion condition for the most recently registered stage,
   * or for a named stage.
   */
  stageCompleteCondition(
    condition: (player: Player) => boolean,
  ): this;
  stageCompleteCondition(
    stageId: S,
    condition: (player: Player) => boolean,
  ): this;
  stageCompleteCondition(
    stageIdOrCondition: S | ((player: Player) => boolean),
    condition?: (player: Player) => boolean,
  ): this {
    let targetId: string;
    let cond: (player: Player) => boolean;

    if (typeof stageIdOrCondition === "function") {
      // Overload 1: condition only, applies to last stage
      assert(this._stages.length > 0,
        "Cannot set condition: no stages registered yet");
      targetId = this._stages[this._stages.length - 1].id;
      cond = stageIdOrCondition;
    } else {
      // Overload 2: stageId + condition
      assert(typeof stageIdOrCondition === "string" && condition !== undefined,
        "Must provide a stage id and condition function");
      targetId = stageIdOrCondition as string;
      cond = condition!;
    }

    const stage = this._stages.find(s => s.id === targetId);
    assert(stage !== undefined, `Stage "${targetId}" not found`);
    assert(typeof cond === "function",
      `condition for stage "${targetId}" must be a function`);
    stage.condition = cond;
    return this;
  }

  /** Set rewards granted on completion. */
  reward(rewards: QuestRewards): this {
    this._rewards = rewards;
    return this;
  }

  /** Set a handler called when any player completes this quest. */
  onComplete(handler: (player: Player) => void): this {
    this._onGlobalComplete = handler;
    return this;
  }

  /**
   * Build the final validated {@link QuestDefinition}.
   *
   * Throws if any required field is missing or any stage lacks a condition.
   */
  build(): QuestDefinition {
    assert(this._name !== null, "Quest name is required (call .name())");
    assert(this._difficulty !== null,
      "Quest difficulty is required (call .difficulty())");
    assert(this._startPoint !== null,
      "Quest startPoint is required (call .startsAt())");
    assert(this._stages.length > 0,
      "At least one stage is required (call .stage())");

    for (const stage of this._stages) {
      assert(stage.condition !== null,
        `Stage "${stage.id}" is missing a condition (call .stageCompleteCondition())`);
    }

    assert(this._rewards !== null,
      "Quest rewards are required (call .reward())");

    const stages: readonly QuestStage[] = this._stages.map(s =>
      createStage({
        id: s.id,
        description: s.description,
        onEnter: s.onEnter,
        condition: s.condition!,
        onComplete: s.onComplete,
        onTick: s.onTick,
      }),
    );

    return createQuest({
      id: this._id,
      name: this._name!,
      difficulty: this._difficulty!,
      requirements: this._requirements,
      startPoint: this._startPoint!,
      startNpc: this._startNpc ?? undefined,
      stages,
      rewards: this._rewards!,
      onGlobalComplete: this._onGlobalComplete ?? undefined,
    });
  }
}

/**
 * Entry point for the fluent quest builder.
 *
 * @param id  Unique quest identifier (lower_snake_case).
 * @returns A new {@link QuestBuilder} ready for method chaining.
 */
export function questBuilder(id: string): QuestBuilder<never> {
  return new QuestBuilder(id);
}
