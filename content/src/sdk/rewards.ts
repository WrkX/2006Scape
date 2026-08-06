/**
 * Named reward builders and grant helpers.
 *
 * {@link createReward} validates and deep-freezes a canonical schema-v1
 * {@link RewardDefinition} against the exact bounds the Java
 * `RewardDefinitionParser` enforces; {@link registerReward} registers it
 * through `defineReward`. {@link grantReward} applies a named reward
 * through the shared player-local transactional consumer and returns its
 * narrow result code.
 *
 * @module sdk/rewards
 */

import type {
  RewardDefinition,
  RewardExperience,
  RewardGrantCode,
  RewardGrantResult,
  RewardItem,
  RewardStateMutation,
  ScriptedPlayer,
} from "../core/runtime.js";
import { MAX_ITEM_ID } from "../core/limits.js";
import { isScriptSkill } from "./skills.js";

const ID_PATTERN = /^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$/;
const STATE_PATTERN = /^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*$/;

const MAX_ITEMS = 28;
const MAX_EXPERIENCE = 21;
const MAX_STATE_MUTATIONS = 32;
const MAX_ITEM_AMOUNT = 2147483647;
const MAX_XP = 200000000;
const MAX_QUEST_POINTS = 10000;
const MAX_NAMESPACE_BYTES = 48;
const MAX_KEY_BYTES = 96;
const MAX_STRING_BYTES = 4096;

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[sdk/rewards] ${message}`);
  }
}

function integral(value: number, min: number, max: number): boolean {
  return Number.isSafeInteger(value) && value >= min && value <= max;
}

function utf8Length(value: string): number {
  return new TextEncoder().encode(value).length;
}

function validateId(id: string): void {
  assert(typeof id === "string" && ID_PATTERN.test(id),
    `invalid reward id '${String(id)}': expected at most 64 characters of ` +
      "letters, digits, '.', '_', or '-'");
}

function validateItems(items: readonly RewardItem[]): void {
  assert(items.length <= MAX_ITEMS,
    `items must contain at most ${MAX_ITEMS} entries`);
  const seen = new Set<string>();
  for (const item of items) {
    const isNumeric = typeof item.id === "number";
    const isName = typeof item.id === "string" && item.id.length > 0;
    assert(isNumeric || isName,
      `items entry must carry a numeric item id or a non-empty item name`);
    if (isNumeric) {
      assert(integral(item.id, 1, MAX_ITEM_ID),
        `items entry item id must be an integer 1..${MAX_ITEM_ID} or an item name`);
    }
    assert(integral(item.amount, 1, MAX_ITEM_AMOUNT),
      `items entry amount must be an integer 1..${MAX_ITEM_AMOUNT}, ` +
        `got ${item.amount}`);
    const key = String(item.id);
    assert(!seen.has(key), `duplicate item grant '${key}'`);
    seen.add(key);
  }
}

function validateExperience(experience: readonly RewardExperience[]): void {
  assert(experience.length <= MAX_EXPERIENCE,
    `experience must contain at most ${MAX_EXPERIENCE} entries`);
  const seen = new Set<string>();
  for (const entry of experience) {
    assert(typeof entry.skill === "string" && isScriptSkill(entry.skill),
      `unknown skill '${entry.skill}'`);
    assert(integral(entry.amount, 1, MAX_XP),
      `experience amount must be an integer 1..${MAX_XP}, ` +
        `got ${entry.amount}`);
    assert(!seen.has(entry.skill), `duplicate experience skill '${entry.skill}'`);
    seen.add(entry.skill);
  }
}

function validateState(state: readonly RewardStateMutation[]): void {
  assert(state.length <= MAX_STATE_MUTATIONS,
    `state must contain at most ${MAX_STATE_MUTATIONS} mutations`);
  const seen = new Set<string>();
  for (const mutation of state) {
    assert(typeof mutation.namespace === "string"
        && STATE_PATTERN.test(mutation.namespace)
        && utf8Length(mutation.namespace) <= MAX_NAMESPACE_BYTES,
      `state namespace '${String(mutation.namespace)}' must be 1..` +
        `${MAX_NAMESPACE_BYTES} UTF-8 bytes of lower-case ASCII ` +
        "letters/digits separated by '.' or '-'");
    assert(typeof mutation.namespace !== "string"
        || !mutation.namespace.startsWith("sys."),
      `state namespace '${mutation.namespace}' is reserved for engine state`);
    assert(typeof mutation.key === "string"
        && STATE_PATTERN.test(mutation.key)
        && utf8Length(mutation.key) <= MAX_KEY_BYTES,
      `state key '${String(mutation.key)}' must be 1..${MAX_KEY_BYTES} ` +
        "UTF-8 bytes of lower-case ASCII letters/digits separated by " +
        "'.' or '-'");
    const key = `${mutation.namespace}.${mutation.key}`;
    assert(!seen.has(key), `duplicate state mutation '${key}'`);
    seen.add(key);
    const value = mutation.value;
    if (typeof value === "string") {
      assert(utf8Length(value) <= MAX_STRING_BYTES,
        `state string value must be at most ${MAX_STRING_BYTES} UTF-8 bytes`);
    } else if (typeof value === "number") {
      assert(Number.isFinite(value),
        "state number value must be finite");
    } else {
      assert(typeof value === "boolean",
        "state value must be a boolean, finite number, or string");
    }
  }
}

/**
 * Create a validated, deeply frozen canonical {@link RewardDefinition}.
 *
 * Mirrors the Java parser bounds: at most 28 unique item grants (amounts
 * `1..2^31-1`), at most 21 unique skill experience grants (`1..200000000`),
 * quest points in `-10000..10000`, and at most 32 state mutations with
 * bounded lower-case namespaces/keys and finite/boolean/string values.
 *
 * @param definition  Raw reward configuration.
 * @returns A frozen canonical {@link RewardDefinition}.
 */
export function createReward(definition: RewardDefinition): RewardDefinition {
  validateId(definition.id);
  validateItems(definition.items ?? []);
  validateExperience(definition.experience ?? []);
  assert(integral(definition.questPoints ?? 0,
      -MAX_QUEST_POINTS, MAX_QUEST_POINTS),
    `questPoints must be an integer -${MAX_QUEST_POINTS}..` +
      `${MAX_QUEST_POINTS}, got ${definition.questPoints}`);
  validateState(definition.state ?? []);

  return Object.freeze({
    id: definition.id,
    items: Object.freeze((definition.items ?? []).map(
      (item) => Object.freeze({ ...item }))),
    experience: Object.freeze((definition.experience ?? []).map(
      (entry) => Object.freeze({ ...entry }))),
    questPoints: definition.questPoints ?? 0,
    state: Object.freeze((definition.state ?? []).map(
      (mutation) => Object.freeze({ ...mutation }))),
  });
}

/**
 * Validate a reward definition and immediately register it via the global
 * `defineReward()` bridge function.
 *
 * @param definition  Raw reward configuration.
 */
export function registerReward(definition: RewardDefinition): void {
  defineReward(createReward(definition));
}

/**
 * Apply one named reward through the shared player-local transactional
 * consumer.
 *
 * The engine preflights definitions, capacity, stack limits, XP cap, quest
 * points, and weight consistency and either commits the complete reward or
 * rolls it back; this helper only forwards the narrow result code.
 *
 * @param player    The live runtime player wrapper.
 * @param rewardId  A named reward registered in the active generation.
 * @returns The closed grant result code.
 */
export function grantReward(
  player: ScriptedPlayer,
  rewardId: string,
): RewardGrantCode {
  const result: RewardGrantResult = player.grantReward(rewardId);
  return result.code();
}

/** True when the grant result code represents a committed reward. */
export function isRewarded(code: RewardGrantCode): boolean {
  return code === "rewarded";
}
