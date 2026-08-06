/**
 * Equipment helper tests.
 *
 * Proves the 11 canonical runtime slots, the legacy-name migration
 * errors, and the narrow equipment queries.
 */

import { test } from "node:test";
import assert from "node:assert/strict";
import {
  EQUIPMENT_SLOTS,
  EQUIPMENT_BONUS_NAMES,
  normalizeSlot,
  isEquipmentSlot,
  equipped,
  hasEquipped,
  equipmentBonus,
  equipmentBonusName,
  equipmentSummary,
} from "../equipment.js";
import type { RuntimeEquipmentSlot } from "../../core/runtime.js";

test("exposes exactly the 11 canonical runtime slots", () => {
  assert.deepEqual(EQUIPMENT_SLOTS, [
    "hat", "cape", "amulet", "weapon", "chest", "shield", "legs", "hands",
    "feet", "ring", "arrows",
  ]);
  for (const slot of EQUIPMENT_SLOTS) {
    assert.ok(isEquipmentSlot(slot));
    assert.equal(normalizeSlot(slot), slot);
  }
});

test("legacy domain slot names fail with a migration message", () => {
  assert.throws(() => normalizeSlot("head"), /'head'.*'hat'/);
  assert.throws(() => normalizeSlot("neck"), /'neck'.*'amulet'/);
  assert.throws(() => normalizeSlot("body"), /'body'.*'chest'/);
  assert.throws(() => normalizeSlot("ammo"), /'ammo'.*'arrows'/);
  assert.throws(() => normalizeSlot("helmet"), /unknown equipment slot/);
  assert.ok(!isEquipmentSlot("head"));
});

test("equipped and hasEquipped read through the facade", () => {
  const player = {
    getEquipment() {
      return {
        get(slot: RuntimeEquipmentSlot) {
          return slot === "weapon" ? 1305 : slot === "hat" ? 0 : null;
        },
        amount: () => 1,
      };
    },
  };
  assert.equal(equipped(player as never, "weapon"), 1305);
  assert.equal(equipped(player as never, "ring"), null);
  assert.equal(equipped(player as never, "hat"), 0);
  assert.ok(hasEquipped(player as never, "weapon", 1305));
  assert.ok(!hasEquipped(player as never, "weapon", 1306));
});

test("equipmentSummary omits empty and missing slots", () => {
  const summary = equipmentSummary({
    get(slot: RuntimeEquipmentSlot) {
      return slot === "weapon" ? 1305 : null;
    },
    amount: () => 1,
    equip: () => false,
    unequip: () => false,
    bonus: () => 0,
    bonusName: () => null,
  });
  assert.equal(summary.size, 1);
  assert.equal(summary.get("weapon"), 1305);
});

test("equipment bonus helpers read through the facade", () => {
  const player = {
    getEquipment() {
      return {
        bonus(index: number) {
          return index === 10 ? 42 : 0;
        },
        bonusName(index: number) {
          return EQUIPMENT_BONUS_NAMES[index] ?? null;
        },
      };
    },
  };
  assert.equal(equipmentBonus(player as never, 10), 42);
  assert.equal(equipmentBonusName(player as never, 10), "Strength");
  assert.throws(() => equipmentBonus(player as never, 12 as never), /0\.\.11/);
});

test("EQUIPMENT_BONUS_NAMES lists twelve combat bonuses", () => {
  assert.equal(EQUIPMENT_BONUS_NAMES.length, 12);
  assert.equal(EQUIPMENT_BONUS_NAMES[10], "Strength");
});
