/**
 * Magic helpers over the runtime {@link ScriptedMagic} facade.
 *
 * Spell ids are **client button ids** (`MagicData.MAGIC_SPELLS[i][0]`), the
 * same values used by `onMagicOnNpc` / `onMagicOnPlayer` routes and
 * {@link MagicOnNpcScriptContext.spellId}.
 *
 * @module sdk/magic
 */

import type { ScriptedMagic, ScriptedPlayer } from "../core/runtime.js";

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[sdk/magic] ${message}`);
  }
}

function assertSpellButtonId(spellButtonId: number): number {
  assert(Number.isSafeInteger(spellButtonId) && spellButtonId >= 1
      && spellButtonId <= 65535,
    "spellButtonId must be an integer 1..65535");
  return spellButtonId;
}

/** Wind strike (modern spell book). */
export const WIND_STRIKE = 1152;

/**
 * Spell table index for a client button id, or `-1` when unknown.
 */
export function spellIndex(
  player: ScriptedPlayer,
  spellButtonId: number,
): number {
  return player.getMagic().findIndex(assertSpellButtonId(spellButtonId));
}

/**
 * Silent rune check for one spell (inventory plus staff substitutions).
 */
export function hasSpellRunes(
  player: ScriptedPlayer,
  spellButtonId: number,
): boolean {
  return player.getMagic().hasRunes(assertSpellButtonId(spellButtonId));
}

/**
 * Deletes runes for one spell when the host allows mutation.
 */
export function consumeSpellRunes(
  player: ScriptedPlayer,
  spellButtonId: number,
): boolean {
  return player.getMagic().consumeRunes(assertSpellButtonId(spellButtonId));
}

/**
 * Required magic level for one spell, or `-1` when unknown.
 */
export function spellRequiredLevel(
  player: ScriptedPlayer,
  spellButtonId: number,
): number {
  return player.getMagic().requiredLevel(assertSpellButtonId(spellButtonId));
}

/**
 * True when the player's magic level meets the spell requirement.
 */
export function hasSpellLevel(
  player: ScriptedPlayer,
  spellButtonId: number,
): boolean {
  return player.getMagic().hasLevel(assertSpellButtonId(spellButtonId));
}

export type { ScriptedMagic };
