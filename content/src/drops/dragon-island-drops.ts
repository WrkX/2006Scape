/**
 * Canonical named drop tables of Dragon Island content.
 *
 * Phase 5 WP4 migration: the tables moved out of the area module so the
 * loader can register drops, bosses, quests, and raids before the area
 * definition that references them. All ids are definition-backed 2006
 * items; the Dragon King boss, the Dragon Guardian and Elder Wizard spawns,
 * and the Ancient Chest object projection reference them by name.
 *
 * @module drops/dragon-island
 */

import { createDropTable } from "../core/drop-tables.js";

/**
 * Dragon Guardian loot table.
 * Always drops dragon bones, with rune and dragon equipment as weighted
 * extras and a rare dragon med helm.
 */
createDropTable({
  id: "dragon_guardian_loot",
  entries: [
    { itemId: 536, minAmount: 1, maxAmount: 1, weight: 0, always: true },
    { itemId: 1147, minAmount: 1, maxAmount: 1, weight: 128, always: false },
    { itemId: 1163, minAmount: 1, maxAmount: 1, weight: 32, always: false },
    { itemId: 995, minAmount: 5000, maxAmount: 15000, weight: 32, always: false },
    { itemId: 1127, minAmount: 1, maxAmount: 1, weight: 16, always: false },
    { itemId: 1215, minAmount: 1, maxAmount: 1, weight: 16, always: false },
    { itemId: 1149, minAmount: 1, maxAmount: 1, weight: 1, always: false },
  ],
});

/**
 * Elder Wizard loot table.
 * Always drops bones, with runes, robes, and a rare staff of fire.
 */
createDropTable({
  id: "elder_wizard_loot",
  entries: [
    { itemId: 526, minAmount: 1, maxAmount: 1, weight: 0, always: true },
    { itemId: 560, minAmount: 10, maxAmount: 30, weight: 128, always: false },
    { itemId: 565, minAmount: 5, maxAmount: 20, weight: 64, always: false },
    { itemId: 577, minAmount: 1, maxAmount: 1, weight: 32, always: false },
    { itemId: 579, minAmount: 1, maxAmount: 1, weight: 32, always: false },
    { itemId: 995, minAmount: 1000, maxAmount: 5000, weight: 32, always: false },
    { itemId: 1387, minAmount: 1, maxAmount: 1, weight: 4, always: false },
  ],
});

/**
 * Dragon King boss loot table (referenced by the boss definition).
 * Always drops dragon bones; high-value rune and dragon equipment.
 */
createDropTable({
  id: "dragon_king_loot",
  entries: [
    { itemId: 536, minAmount: 1, maxAmount: 1, weight: 0, always: true },
    { itemId: 995, minAmount: 20000, maxAmount: 50000, weight: 128, always: false },
    { itemId: 1127, minAmount: 1, maxAmount: 1, weight: 32, always: false },
    { itemId: 1079, minAmount: 1, maxAmount: 1, weight: 32, always: false },
    { itemId: 1215, minAmount: 1, maxAmount: 1, weight: 16, always: false },
    { itemId: 1305, minAmount: 1, maxAmount: 1, weight: 16, always: false },
    { itemId: 1149, minAmount: 1, maxAmount: 1, weight: 8, always: false },
  ],
});

/**
 * Ancient Chest loot table (first click of the area chest projection).
 * Public delivery: the exact identities are visible to everyone and expire
 * after the bounded public lifetime.
 */
createDropTable({
  id: "ancient_chest_loot",
  entries: [
    { itemId: 995, minAmount: 2500, maxAmount: 10000, weight: 128, always: false },
    { itemId: 536, minAmount: 5, maxAmount: 10, weight: 64, always: false },
    { itemId: 1147, minAmount: 1, maxAmount: 1, weight: 32, always: false },
    { itemId: 1127, minAmount: 1, maxAmount: 1, weight: 16, always: false },
    { itemId: 1215, minAmount: 1, maxAmount: 1, weight: 16, always: false },
    { itemId: 1305, minAmount: 1, maxAmount: 1, weight: 8, always: false },
  ],
});
