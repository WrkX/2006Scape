/**
 * Canonical drop tables for Dragon Island creatures.
 *
 * Phase 5 WP2 migration: the former author-side {@code LootTable} builder
 * objects were inert (nothing consumed them) and used fantasy item names
 * with no cache definition. They are now canonical schema-v1 named drop
 * tables registered through the bridge, using real 2006 item ids only.
 *
 * Migrated entries (documented, not silent loss):
 * - "dragon_bones" -> 536 Dragon bones
 * - "dragon_scales", "dragon_token", "ancient_book", "draconic_visage",
 *   "staff_of_fire" variants without a real cache item were replaced by the
 *   closest real items below; "draconic_visage" (2007 item) was dropped.
 *
 * @module areas/dragon_island/drops
 */

import { createDropTable } from "../../core/drop-tables.js";

/**
 * Dragon Guardian loot table.
 * Always drops dragon bones, with rune and dragon equipment as weighted
 * extras and a rare Dragon Token-equivalent (dragon med helm).
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
