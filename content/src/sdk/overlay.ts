/**
 * Cache definition overlay builders.
 *
 * {@link createItemOverlay}, {@link createNpcOverlay}, and
 * {@link createObjectOverlay} validate schema-v1 overlays against the Java
 * parsers; {@link register*} helpers register through `define*Overlay`.
 * Overlays merge over loaded cache definitions at `::scripts` activation.
 *
 * @module sdk/overlay
 */

import type {
  ItemOverlayDefinition,
  NpcOverlayDefinition,
  ObjectOverlayDefinition,
  RuntimeEquipmentSlot,
} from "../core/runtime.js";
import {
  CUSTOM_ITEM_START,
  CUSTOM_NPC_START,
  CUSTOM_OBJECT_START,
  MAX_CUSTOM_ID,
  MAX_ITEM_ID,
  MAX_NPC_ID,
  MAX_OBJECT_ID,
} from "../core/limits.js";
import { EQUIPMENT_SLOTS } from "./equipment.js";

const ID_PATTERN = /^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$/;
const SLOT_SET = new Set<string>(EQUIPMENT_SLOTS);
const REQUIREMENT_KEYS = [
  "attack", "strength", "defence", "hitpoints", "ranged", "prayer", "magic",
] as const;
const BONUS_KEYS = [
  "attackStab", "attackSlash", "attackCrush", "attackMagic", "attackRange",
  "defenceStab", "defenceSlash", "defenceCrush", "defenceMagic",
  "defenceRange", "strength", "prayer",
] as const;

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[sdk/overlay] ${message}`);
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

function boundedString(
  value: string | undefined,
  field: string,
  maxBytes: number,
): string | undefined {
  if (value === undefined) {
    return undefined;
  }
  assert(typeof value === "string" && utf8Length(value) >= 1
      && utf8Length(value) <= maxBytes,
    `${field} must be 1..${maxBytes} UTF-8 bytes, got '${String(value)}'`);
  return value;
}

function validateOverlayId(id: string, label: string): void {
  assert(typeof id === "string" && ID_PATTERN.test(id),
    `invalid ${label} id '${String(id)}': expected at most 64 characters ` +
      "of letters, digits, '.', '_', or '-'");
}

function hasOverlayFields(
  fields: readonly (string | number | boolean | readonly string[] | object | undefined)[],
): boolean {
  return fields.some((field) => field !== undefined);
}

/**
 * Create a validated, deeply frozen {@link ItemOverlayDefinition}.
 */
export function createItemOverlay(
  definition: ItemOverlayDefinition,
): ItemOverlayDefinition {
  validateOverlayId(definition.id, "item overlay");
  assert(integral(definition.itemId, 0, MAX_ITEM_ID),
    `itemOverlay.itemId must be an integer 0..${MAX_ITEM_ID}, ` +
      `got ${definition.itemId}`);
  const name = boundedString(definition.name, "itemOverlay.name", 64);
  const examine = boundedString(definition.examine, "itemOverlay.examine", 256);
  if (definition.stackable !== undefined) {
    assert(typeof definition.stackable === "boolean",
      "itemOverlay.stackable must be a boolean when present");
  }
  if (definition.equipSlot !== undefined) {
    assert(SLOT_SET.has(definition.equipSlot),
      `itemOverlay.equipSlot must be one of ${EQUIPMENT_SLOTS.join(", ")}, ` +
        `got '${String(definition.equipSlot)}'`);
  }
  let requirements: ItemOverlayDefinition["requirements"];
  if (definition.requirements !== undefined) {
    requirements = {};
    for (const key of REQUIREMENT_KEYS) {
      const value = definition.requirements[key];
      if (value !== undefined) {
        assert(integral(value, 1, 99),
          `itemOverlay.requirements.${key} must be an integer 1..99, ` +
            `got ${value}`);
        requirements = { ...requirements, [key]: value };
      }
    }
  }
  let bonuses: ItemOverlayDefinition["bonuses"];
  if (definition.bonuses !== undefined) {
    bonuses = {};
    for (const key of BONUS_KEYS) {
      const value = definition.bonuses[key];
      if (value !== undefined) {
        assert(integral(value, -32768, 32767),
          `itemOverlay.bonuses.${key} must be an integer -32768..32767, ` +
            `got ${value}`);
        bonuses = { ...bonuses, [key]: value };
      }
    }
  }
  assert(hasOverlayFields([
    name, examine, definition.stackable, definition.equipSlot,
    requirements, bonuses,
  ]), "item overlay must set at least one field");
  return Object.freeze({
    id: definition.id,
    itemId: definition.itemId,
    ...(name !== undefined ? { name } : {}),
    ...(examine !== undefined ? { examine } : {}),
    ...(definition.stackable !== undefined
      ? { stackable: definition.stackable }
      : {}),
    ...(definition.equipSlot !== undefined
      ? { equipSlot: definition.equipSlot }
      : {}),
    ...(requirements !== undefined ? { requirements } : {}),
    ...(bonuses !== undefined ? { bonuses } : {}),
  });
}

/**
 * Create a validated, deeply frozen {@link NpcOverlayDefinition}.
 */
export function createNpcOverlay(
  definition: NpcOverlayDefinition,
): NpcOverlayDefinition {
  validateOverlayId(definition.id, "npc overlay");
  assert(integral(definition.npcId, 0, MAX_NPC_ID),
    `npcOverlay.npcId must be an integer 0..${MAX_NPC_ID}, ` +
      `got ${definition.npcId}`);
  const name = boundedString(definition.name, "npcOverlay.name", 64);
  if (definition.combatLevel !== undefined) {
    assert(integral(definition.combatLevel, 1, 65535),
      `npcOverlay.combatLevel must be an integer 1..65535, ` +
        `got ${definition.combatLevel}`);
  }
  if (definition.hitpoints !== undefined) {
    assert(integral(definition.hitpoints, 1, 32767),
      `npcOverlay.hitpoints must be an integer 1..32767, ` +
        `got ${definition.hitpoints}`);
  }
  assert(hasOverlayFields([
    name, definition.combatLevel, definition.hitpoints,
  ]), "npc overlay must set at least one field");
  return Object.freeze({
    id: definition.id,
    npcId: definition.npcId,
    ...(name !== undefined ? { name } : {}),
    ...(definition.combatLevel !== undefined
      ? { combatLevel: definition.combatLevel }
      : {}),
    ...(definition.hitpoints !== undefined
      ? { hitpoints: definition.hitpoints }
      : {}),
  });
}

/**
 * Create a validated, deeply frozen {@link ObjectOverlayDefinition}.
 */
export function createObjectOverlay(
  definition: ObjectOverlayDefinition,
): ObjectOverlayDefinition {
  validateOverlayId(definition.id, "object overlay");
  assert(integral(definition.objectId, 0, MAX_OBJECT_ID),
    `objectOverlay.objectId must be an integer 0..${MAX_OBJECT_ID}, ` +
      `got ${definition.objectId}`);
  const name = boundedString(definition.name, "objectOverlay.name", 64);
  const examine = boundedString(
    definition.examine,
    "objectOverlay.examine",
    256,
  );
  let actions: readonly string[] | undefined;
  if (definition.actions !== undefined) {
    assert(Array.isArray(definition.actions),
      "objectOverlay.actions must be an array when present");
    assert(definition.actions.length <= 5,
      "objectOverlay.actions must have at most 5 entries");
    const normalized: string[] = [];
    for (let index = 0; index < definition.actions.length; index++) {
      const action = definition.actions[index];
      if (action === undefined || action === null) {
        continue;
      }
      assert(typeof action === "string" && utf8Length(action) >= 1
          && utf8Length(action) <= 32,
        `objectOverlay.actions[${index}] must be 1..32 UTF-8 bytes`);
      normalized[index] = action;
    }
    actions = Object.freeze(normalized);
  }
  assert(hasOverlayFields([name, examine, actions]),
    "object overlay must set at least one field");
  return Object.freeze({
    id: definition.id,
    objectId: definition.objectId,
    ...(name !== undefined ? { name } : {}),
    ...(examine !== undefined ? { examine } : {}),
    ...(actions !== undefined ? { actions } : {}),
  });
}

/** Validate and register an item overlay via `defineItemOverlay()`. */
export function registerItemOverlay(
  definition: ItemOverlayDefinition,
): void {
  defineItemOverlay(createItemOverlay(definition));
}

/** Validate and register an NPC overlay via `defineNpcOverlay()`. */
export function registerNpcOverlay(definition: NpcOverlayDefinition): void {
  defineNpcOverlay(createNpcOverlay(definition));
}

/** Validate and register an object overlay via `defineObjectOverlay()`. */
export function registerObjectOverlay(
  definition: ObjectOverlayDefinition,
): void {
  defineObjectOverlay(createObjectOverlay(definition));
}

export {
  CUSTOM_ITEM_START,
  CUSTOM_NPC_START,
  CUSTOM_OBJECT_START,
  MAX_CUSTOM_ID,
};
export type {
  ItemOverlayDefinition,
  NpcOverlayDefinition,
  ObjectOverlayDefinition,
  RuntimeEquipmentSlot,
} from "../core/runtime.js";
