/**
 * Prayer helpers over the runtime {@link ScriptedPrayer} facade.
 *
 * Indexes match the classic prayer book (0 Thick Skin … 25 Piety).
 *
 * @module sdk/prayer
 */

import type { ScriptedPlayer, ScriptedPrayer } from "../core/runtime.js";

const MAX_PRAYER = 25;

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[sdk/prayer] ${message}`);
  }
}

function prayerIndex(prayer: number): number {
  assert(Number.isSafeInteger(prayer) && prayer >= 0 && prayer <= MAX_PRAYER,
    `prayer must be an integer 0..${MAX_PRAYER}`);
  return prayer;
}

export function isPrayerActive(player: ScriptedPlayer, prayer: number): boolean {
  return player.getPrayer().isActive(prayerIndex(prayer));
}

export function activatePrayer(player: ScriptedPlayer, prayer: number): boolean {
  return player.getPrayer().activate(prayerIndex(prayer));
}

export function deactivatePrayer(
  player: ScriptedPlayer,
  prayer: number,
): boolean {
  return player.getPrayer().deactivate(prayerIndex(prayer));
}

export function deactivateAllPrayers(player: ScriptedPlayer): boolean {
  return player.getPrayer().deactivateAll();
}

export function prayerName(player: ScriptedPlayer, prayer: number): string | null {
  return player.getPrayer().name(prayerIndex(prayer));
}

export type { ScriptedPrayer };
