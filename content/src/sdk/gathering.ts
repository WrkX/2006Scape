/**
 * Gathering resource builders.
 *
 * {@link createGatheringResource} validates and deep-freezes a canonical
 * schema-v1 {@link GatheringResourceDefinition} against the exact bounds the
 * Java `GatheringResourceDefinitionParser` enforces; {@link registerGatheringResource}
 * registers it through `defineGatheringResource`. The resource's object
 * route is a Java-owned host route; an equal-id cache or legacy object at
 * another tile keeps its complete legacy behavior.
 *
 * @module sdk/gathering
 */

import type {
  GatheringResourceDefinition,
  GatheringResourceReward,
  GatheringResourceTool,
} from "../core/runtime.js";
import { isScriptSkill, type ScriptSkillName } from "./skills.js";

const ID_PATTERN = /^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$/;
const ACTIONS = ["first", "second", "third", "fourth"] as const;

const MAX_OBJECT_ID = 65535;
const MAX_TOOLS = 16;
const MAX_REWARDS = 16;
const MAX_ITEM_ID = 14999;
const MAX_ITEM_AMOUNT = 2147483647;
const MAX_ANIMATION = 65535;
const MAX_INTERVAL_TICKS = 100000;
const MAX_CHANCE_DENOMINATOR = 1000000;
const MAX_EXPERIENCE = 200000000;
const MAX_RESPAWN_TICKS = 100000;

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[sdk/gathering] ${message}`);
  }
}

function integral(value: number, min: number, max: number): boolean {
  return Number.isSafeInteger(value) && value >= min && value <= max;
}

function utf8Length(value: string): number {
  // GraalJS exposes no `TextEncoder` (it is a Web/Node API, not ECMAScript),
  // so the byte length is computed directly over UTF-16 code units.
  let length = 0;
  for (let index = 0; index < value.length; index++) {
    const code = value.charCodeAt(index);
    if (code < 0x80) {
      length += 1;
    } else if (code < 0x800) {
      length += 2;
    } else if (code >= 0xd800 && code <= 0xdbff && index + 1 < value.length) {
      const next = value.charCodeAt(index + 1);
      if (next >= 0xdc00 && next <= 0xdfff) {
        length += 4;
        index++;
      } else {
        length += 3;
      }
    } else {
      length += 3;
    }
  }
  return length;
}

function validateItemId(itemId: number | string, label: string): void {
  const isNumeric = typeof itemId === "number";
  const isName = typeof itemId === "string" && itemId.length > 0;
  assert(isNumeric || isName,
    `${label} must be a numeric item id or a non-empty item name`);
  if (isNumeric) {
    assert(integral(itemId, 1, MAX_ITEM_ID),
      `${label} must be an integer 1..${MAX_ITEM_ID} or an item name`);
  }
}

function validateTools(tools: readonly GatheringResourceTool[]): void {
  assert(tools.length >= 1 && tools.length <= MAX_TOOLS,
    `tools must contain 1..${MAX_TOOLS} entries`);
  const seen = new Set<string>();
  for (const tool of tools) {
    validateItemId(tool.itemId, "tools entry itemId");
    const key = String(tool.itemId);
    assert(!seen.has(key), `duplicate tool item id '${key}'`);
    seen.add(key);
    if (tool.consume !== undefined) {
      assert(typeof tool.consume === "boolean",
        "tools entry consume must be a boolean when present");
    }
  }
}

function validateRewards(rewards: readonly GatheringResourceReward[]): void {
  assert(rewards.length >= 1 && rewards.length <= MAX_REWARDS,
    `rewards must contain 1..${MAX_REWARDS} entries`);
  for (const reward of rewards) {
    validateItemId(reward.itemId, "rewards entry itemId");
    assert(integral(reward.amount, 1, MAX_ITEM_AMOUNT),
      `rewards entry amount must be an integer 1..${MAX_ITEM_AMOUNT}, ` +
        `got ${reward.amount}`);
  }
}

/**
 * Create a validated, deeply frozen canonical
 * {@link GatheringResourceDefinition}.
 *
 * Mirrors the Java parser bounds: id of at most 64 identifier characters, a
 * name of at most 128 UTF-8 bytes, a definition-backed object id, one of the
 * four ordinal actions, a canonical skill and level `1..255`, `1..16`
 * ordered tools and `1..16` rewards, an animation `-1..65535`, an interval
 * `1..100000`, a success chance {@code 0..numerator..denominator..1000000},
 * experience
 * `1..200000000`, a definition-backed depleted object id, and respawn ticks
 * `1..100000`.
 *
 * @param definition  Raw gathering resource configuration.
 * @returns A frozen canonical {@link GatheringResourceDefinition}.
 */
export function createGatheringResource(
  definition: GatheringResourceDefinition,
): GatheringResourceDefinition {
  assert(typeof definition.id === "string" && ID_PATTERN.test(definition.id),
    `invalid resource id '${String(definition.id)}': expected at most 64 ` +
      "characters of letters, digits, '.', '_', or '-'");
  assert(typeof definition.name === "string"
      && utf8Length(definition.name) >= 1
      && utf8Length(definition.name) <= 128,
    `resource.name must be 1..128 UTF-8 bytes, got ` +
      `'${String(definition.name)}'`);
  assert(integral(definition.objectId, 0, MAX_OBJECT_ID),
    `resource.objectId must be an integer 0..${MAX_OBJECT_ID}, ` +
      `got ${definition.objectId}`);
  assert((ACTIONS as readonly string[]).includes(definition.action),
    `resource.action must be one of ${ACTIONS.join(", ")}`);
  const skill = definition.skill;
  assert(isScriptSkill(skill),
    `unknown skill '${String(skill)}'`);
  assert(integral(definition.level, 1, 255),
    `resource.level must be an integer 1..255, got ${definition.level}`);
  validateTools(definition.tools);
  assert(integral(definition.animation, -1, MAX_ANIMATION),
    `resource.animation must be an integer -1..${MAX_ANIMATION}, ` +
      `got ${definition.animation}`);
  assert(integral(definition.intervalTicks, 1, MAX_INTERVAL_TICKS),
    `resource.intervalTicks must be an integer 1..${MAX_INTERVAL_TICKS}, ` +
      `got ${definition.intervalTicks}`);
  assert(typeof definition.successChance === "object"
      && definition.successChance !== null,
    "resource.successChance must be an object");
  assert(integral(definition.successChance.numerator, 0,
      MAX_CHANCE_DENOMINATOR),
    `resource.successChance.numerator must be an integer 0..` +
      `${MAX_CHANCE_DENOMINATOR}`);
  assert(integral(definition.successChance.denominator, 1,
      MAX_CHANCE_DENOMINATOR),
    `resource.successChance.denominator must be an integer 1..` +
      `${MAX_CHANCE_DENOMINATOR}`);
  assert(definition.successChance.numerator <=
      definition.successChance.denominator,
    "resource.successChance.numerator must not exceed denominator");
  validateRewards(definition.rewards);
  assert(integral(definition.experience, 1, MAX_EXPERIENCE),
    `resource.experience must be an integer 1..${MAX_EXPERIENCE}, ` +
      `got ${definition.experience}`);
  assert(integral(definition.depletedObjectId, 0, MAX_OBJECT_ID),
    `resource.depletedObjectId must be an integer 0..${MAX_OBJECT_ID}, ` +
      `got ${definition.depletedObjectId}`);
  assert(definition.depletedObjectId !== definition.objectId,
    "resource.depletedObjectId must differ from the resource objectId");
  assert(integral(definition.respawnTicks, 1, MAX_RESPAWN_TICKS),
    `resource.respawnTicks must be an integer 1..${MAX_RESPAWN_TICKS}, ` +
      `got ${definition.respawnTicks}`);

  return Object.freeze({
    id: definition.id,
    name: definition.name,
    objectId: definition.objectId,
    action: definition.action,
    skill: skill as ScriptSkillName,
    level: definition.level,
    tools: Object.freeze(definition.tools.map(
      (tool) => Object.freeze({ ...tool }))),
    animation: definition.animation,
    intervalTicks: definition.intervalTicks,
    successChance: Object.freeze({
      numerator: definition.successChance.numerator,
      denominator: definition.successChance.denominator,
    }),
    rewards: Object.freeze(definition.rewards.map(
      (reward) => Object.freeze({ ...reward }))),
    experience: definition.experience,
    depletedObjectId: definition.depletedObjectId,
    respawnTicks: definition.respawnTicks,
  });
}

/**
 * Validate a gathering resource definition and immediately register it via
 * the global `defineGatheringResource()` bridge function.
 *
 * @param definition  Raw gathering resource configuration.
 */
export function registerGatheringResource(
  definition: GatheringResourceDefinition,
): void {
  defineGatheringResource(createGatheringResource(definition));
}

export type { GatheringResourceDefinition };
