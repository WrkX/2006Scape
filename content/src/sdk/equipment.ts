/**
 * Equipment helpers over the 11 accepted runtime slot names.
 *
 * The Java equipment facade exposes exactly the 11 canonical slots
 * (`hat`, `cape`, `amulet`, `weapon`, `chest`, `shield`, `legs`, `hands`,
 * `feet`, `ring`, `arrows`). Legacy domain names used by aspirational
 * models (`head`, `neck`, `body`, `ammo`) fail with a migration message
 * instead of being silently accepted.
 *
 * @module sdk/equipment
 */

import type {
  RuntimeEquipmentSlot,
  ScriptedEquipment,
  ScriptedPlayer,
} from "../core/runtime.js";

/** The 11 canonical equipment slots exposed by the runtime facade. */
export const EQUIPMENT_SLOTS: readonly RuntimeEquipmentSlot[] = [
  "hat", "cape", "amulet", "weapon", "chest", "shield", "legs", "hands",
  "feet", "ring", "arrows",
] as const;

const SLOT_SET: ReadonlySet<string> = new Set(EQUIPMENT_SLOTS);

/** Legacy domain slot names mapped to their canonical runtime name. */
const LEGACY_SLOT_NAMES: ReadonlyMap<string, RuntimeEquipmentSlot> = new Map([
  ["head", "hat"],
  ["neck", "amulet"],
  ["body", "chest"],
  ["ammo", "arrows"],
]);

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[sdk/equipment] ${message}`);
  }
}

/**
 * Resolve one equipment slot name to a canonical runtime slot.
 *
 * Canonical names pass through; the legacy domain names `head`, `neck`,
 * `body`, and `ammo` fail with a migration message naming the canonical
 * replacement rather than being silently mapped.
 *
 * @param slot  The slot name to resolve.
 * @returns The canonical {@link RuntimeEquipmentSlot}.
 */
export function normalizeSlot(slot: string): RuntimeEquipmentSlot {
  if (SLOT_SET.has(slot)) {
    return slot as RuntimeEquipmentSlot;
  }
  const legacy = LEGACY_SLOT_NAMES.get(slot);
  if (legacy !== undefined) {
    throw new Error(
      `[sdk/equipment] legacy equipment slot '${slot}' is not a runtime ` +
        `slot; use '${legacy}'`,
    );
  }
  throw new Error(
    `[sdk/equipment] unknown equipment slot '${slot}': expected one of ` +
      EQUIPMENT_SLOTS.join(", "),
  );
}

/** True when `slot` is one of the 11 canonical runtime slot names. */
export function isEquipmentSlot(slot: string): slot is RuntimeEquipmentSlot {
  return SLOT_SET.has(slot);
}

/**
 * The item id equipped in one slot (`null` when the slot is empty).
 *
 * @param player  The live runtime player wrapper.
 * @param slot    A canonical runtime slot name.
 */
export function equipped(
  player: ScriptedPlayer,
  slot: RuntimeEquipmentSlot,
): number | null {
  return player.getEquipment().get(slot);
}

/**
 * True when the exact item id is equipped in the given slot.
 *
 * @param player  The live runtime player wrapper.
 * @param slot    A canonical runtime slot name.
 * @param itemId  The item id to match.
 */
export function hasEquipped(
  player: ScriptedPlayer,
  slot: RuntimeEquipmentSlot,
  itemId: number,
): boolean {
  return player.getEquipment().get(slot) === itemId;
}

/**
 * Read-only slot summary of one player's equipment.
 *
 * @param equipment  The runtime equipment facade.
 */
export function equipmentSummary(
  equipment: ScriptedEquipment,
): ReadonlyMap<RuntimeEquipmentSlot, number> {
  const summary = new Map<RuntimeEquipmentSlot, number>();
  for (const slot of EQUIPMENT_SLOTS) {
    const id = equipment.get(slot);
    if (id !== null && id !== 0) {
      summary.set(slot, id);
    }
  }
  return summary;
}

export type { RuntimeEquipmentSlot };
