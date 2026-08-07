/**
 * World mob builders.
 *
 * {@link createMob} validates and deep-freezes a canonical schema-v1
 * {@link MobDefinition} against the bounds the Java `MobDefinitionParser`
 * enforces; {@link registerMob} registers it through `defineMob`. Registered
 * NPC ids suppress legacy `NpcCombat` switch cases; unregistered ids keep
 * legacy behavior.
 *
 * @module sdk/mob
 */

import type { MobDefinition } from "../core/runtime.js";
import { MAX_NPC_ID } from "../core/limits.js";

const ID_PATTERN = /^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$/;
const MAX_AGGRESSION = 64;
const MAX_ATTACK_SPEED = 100;
const MAX_HIT = 32767;
const MAX_ANIMATION = 65535;
const COMBAT_STYLES = new Set(["melee", "ranged", "magic"]);

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[sdk/mob] ${message}`);
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

/**
 * Create a validated, deeply frozen canonical {@link MobDefinition}.
 */
export function createMob(definition: MobDefinition): MobDefinition {
  assert(typeof definition.id === "string" && ID_PATTERN.test(definition.id),
    `invalid mob id '${String(definition.id)}': expected at most 64 ` +
      "characters of letters, digits, '.', '_', or '-'");
  assert(integral(definition.npcId, 0, MAX_NPC_ID),
    `mob.npcId must be an integer 0..${MAX_NPC_ID}, got ${definition.npcId}`);
  if (definition.name !== undefined) {
    assert(typeof definition.name === "string"
        && utf8Length(definition.name) >= 1
        && utf8Length(definition.name) <= 64,
      `mob.name must be 1..64 UTF-8 bytes, got '${String(definition.name)}'`);
  }
  assert(integral(definition.aggression, 0, MAX_AGGRESSION),
    `mob.aggression must be an integer 0..${MAX_AGGRESSION}, ` +
      `got ${definition.aggression}`);
  assert(COMBAT_STYLES.has(definition.combatStyle),
    `mob.combatStyle must be 'melee', 'ranged', or 'magic', ` +
      `got '${String(definition.combatStyle)}'`);
  assert(integral(definition.attackSpeed, 1, MAX_ATTACK_SPEED),
    `mob.attackSpeed must be an integer 1..${MAX_ATTACK_SPEED}, ` +
      `got ${definition.attackSpeed}`);
  assert(integral(definition.maxHit, 0, MAX_HIT),
    `mob.maxHit must be an integer 0..${MAX_HIT}, got ${definition.maxHit}`);
  if (definition.animation !== undefined) {
    assert(integral(definition.animation, -1, MAX_ANIMATION),
      `mob.animation must be an integer -1..${MAX_ANIMATION}, ` +
        `got ${definition.animation}`);
  }
  if (definition.onSpawn !== undefined) {
    assert(typeof definition.onSpawn === "function",
      "mob.onSpawn must be a function when present");
  }
  if (definition.onTick !== undefined) {
    assert(typeof definition.onTick === "function",
      "mob.onTick must be a function when present");
  }
  if (definition.onDeath !== undefined) {
    assert(typeof definition.onDeath === "function",
      "mob.onDeath must be a function when present");
  }

  return Object.freeze({
    id: definition.id,
    npcId: definition.npcId,
    ...(definition.name !== undefined ? { name: definition.name } : {}),
    aggression: definition.aggression,
    combatStyle: definition.combatStyle,
    attackSpeed: definition.attackSpeed,
    maxHit: definition.maxHit,
    ...(definition.animation !== undefined
      ? { animation: definition.animation }
      : {}),
    ...(definition.onSpawn !== undefined ? { onSpawn: definition.onSpawn } : {}),
    ...(definition.onTick !== undefined ? { onTick: definition.onTick } : {}),
    ...(definition.onDeath !== undefined ? { onDeath: definition.onDeath } : {}),
  });
}

/**
 * Validate a mob definition and register it via `defineMob()`.
 */
export function registerMob(definition: MobDefinition): void {
  defineMob(createMob(definition));
}

export type { MobDefinition, MobRuntimeContext } from "../core/runtime.js";
