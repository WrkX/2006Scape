/**
 * Processing skill builders.
 *
 * {@link createProcessingSkill} validates and deep-freezes a canonical
 * schema-v1 {@link ProcessingSkillDefinition} against the bounds the Java
 * `ProcessingSkillDefinitionParser` enforces;
 * {@link registerProcessingSkill} registers it through
 * `defineProcessingSkill`. The skill's item-on-object route is a Java-owned
 * host route; unregistered item/object pairs keep legacy behavior.
 *
 * @module sdk/processing
 */

import type { ProcessingSkillDefinition } from "../core/runtime.js";
import { MAX_ITEM_ID, MAX_OBJECT_ID } from "../core/limits.js";
import { isScriptSkill, type ScriptSkillName } from "./skills.js";

const ID_PATTERN = /^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$/;
const MAX_ANIMATION = 65535;
const MAX_SOUND = 65535;
const MAX_INTERVAL_TICKS = 100000;
const MAX_EXPERIENCE = 200000000;
const DEFAULT_BURN_BONUS = 3;

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[sdk/processing] ${message}`);
  }
}

function integral(value: number, min: number, max: number): boolean {
  return Number.isSafeInteger(value) && value >= min && value <= max;
}

function utf8Length(value: string): number {
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

/**
 * Create a validated, deeply frozen canonical
 * {@link ProcessingSkillDefinition}.
 */
export function createProcessingSkill(
  definition: ProcessingSkillDefinition,
): ProcessingSkillDefinition {
  assert(typeof definition.id === "string" && ID_PATTERN.test(definition.id),
    `invalid processing id '${String(definition.id)}': expected at most 64 ` +
      "characters of letters, digits, '.', '_', or '-'");
  assert(typeof definition.name === "string"
      && utf8Length(definition.name) >= 1
      && utf8Length(definition.name) <= 128,
    `processing.name must be 1..128 UTF-8 bytes, got ` +
      `'${String(definition.name)}'`);
  assert(isScriptSkill(definition.skill),
    `unknown skill '${String(definition.skill)}'`);
  assert(integral(definition.level, 1, 255),
    `processing.level must be an integer 1..255, got ${definition.level}`);
  validateItemId(definition.inputItemId, "processing.inputItemId");
  assert(integral(definition.objectId, 0, MAX_OBJECT_ID),
    `processing.objectId must be an integer 0..${MAX_OBJECT_ID}, ` +
      `got ${definition.objectId}`);
  validateItemId(definition.productItemId, "processing.productItemId");
  assert(String(definition.productItemId) !== String(definition.inputItemId),
    "processing.productItemId must differ from inputItemId");
  if (definition.failProductItemId !== undefined) {
    validateItemId(definition.failProductItemId,
      "processing.failProductItemId");
    assert(String(definition.failProductItemId)
        !== String(definition.inputItemId)
      && String(definition.failProductItemId)
        !== String(definition.productItemId),
      "processing.failProductItemId must differ from input and product");
  }
  assert(integral(definition.experience, 1, MAX_EXPERIENCE),
    `processing.experience must be an integer 1..${MAX_EXPERIENCE}, ` +
      `got ${definition.experience}`);
  assert(integral(definition.animation, -1, MAX_ANIMATION),
    `processing.animation must be an integer -1..${MAX_ANIMATION}, ` +
      `got ${definition.animation}`);
  if (definition.sound !== undefined) {
    assert(integral(definition.sound, 0, MAX_SOUND),
      `processing.sound must be an integer 0..${MAX_SOUND}, ` +
        `got ${definition.sound}`);
  }
  assert(integral(definition.intervalTicks, 1, MAX_INTERVAL_TICKS),
    `processing.intervalTicks must be an integer 1..${MAX_INTERVAL_TICKS}, ` +
      `got ${definition.intervalTicks}`);
  assert(integral(definition.stopBurnLevel, 1, 255),
    `processing.stopBurnLevel must be an integer 1..255, ` +
      `got ${definition.stopBurnLevel}`);
  assert(definition.stopBurnLevel >= definition.level,
    "processing.stopBurnLevel must be >= level");
  const hasGloves = definition.glovesItemId !== undefined;
  const hasGlovesStop = definition.stopBurnLevelWithGloves !== undefined;
  assert(hasGloves === hasGlovesStop,
    "processing.glovesItemId and stopBurnLevelWithGloves must be set together");
  if (hasGloves) {
    validateItemId(definition.glovesItemId as number | string,
      "processing.glovesItemId");
    assert(integral(definition.stopBurnLevelWithGloves as number, 1, 255),
      `processing.stopBurnLevelWithGloves must be an integer 1..255`);
    assert((definition.stopBurnLevelWithGloves as number) >= definition.level,
      "processing.stopBurnLevelWithGloves must be >= level");
  }
  const burnBonus = definition.burnBonus ?? DEFAULT_BURN_BONUS;
  assert(integral(burnBonus, 0, 55),
    `processing.burnBonus must be an integer 0..55, got ${burnBonus}`);

  return Object.freeze({
    id: definition.id,
    name: definition.name,
    skill: definition.skill as ScriptSkillName,
    level: definition.level,
    inputItemId: definition.inputItemId,
    objectId: definition.objectId,
    productItemId: definition.productItemId,
    ...(definition.failProductItemId !== undefined
      ? { failProductItemId: definition.failProductItemId }
      : {}),
    experience: definition.experience,
    animation: definition.animation,
    ...(definition.sound !== undefined ? { sound: definition.sound } : {}),
    intervalTicks: definition.intervalTicks,
    stopBurnLevel: definition.stopBurnLevel,
    ...(hasGloves
      ? {
          stopBurnLevelWithGloves: definition.stopBurnLevelWithGloves,
          glovesItemId: definition.glovesItemId,
        }
      : {}),
    ...(definition.burnBonus !== undefined ? { burnBonus } : {}),
  });
}

/**
 * Validate a processing skill definition and register it via
 * `defineProcessingSkill()`.
 */
export function registerProcessingSkill(
  definition: ProcessingSkillDefinition,
): void {
  defineProcessingSkill(createProcessingSkill(definition));
}

export type { ProcessingSkillDefinition };
