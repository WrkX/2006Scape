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
  normalizeSlot,
  isEquipmentSlot,
  equipped,
  hasEquipped,
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
  });
  assert.equal(summary.size, 1);
  assert.equal(summary.get("weapon"), 1305);
});
