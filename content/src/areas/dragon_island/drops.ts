/**
 * Drop tables for Dragon Island creatures.
 *
 * @module areas/dragon_island/drops
 */

import { dropTable, createLootTable } from "../../core/drop-tables.js";
import type { LootTable } from "../../core/types.js";

/**
 * Dragon Guardian loot table.
 * Always drops dragon bones.  Common loot includes dragon scales and
 * rune items, with a rare Dragon Token drop.
 */
export const dragonGuardianLoot: LootTable = dropTable("dragon_guardian_loot")
  .always("dragon_bones", 1)
  .common("dragon_scales", [3, 8])
  .uncommon("rune_med_helm", 1)
  .uncommon("rune_full_helm", 1, 25)
  .uncommon("coins", [5000, 15000])
  .rare("rune_platebody", 1)
  .rare("dragon_dagger", 1)
  .veryRare("dragon_token", 1)
  .rareMessage("A dragon guardian drops a Dragon Token!")
  .build();

/**
 * Elder Wizard loot table.
 * Always drops wizard bones variant.  Common runes and robes, with a
 * rare ancient book.
 */
export const elderWizardLoot: LootTable = dropTable("elder_wizard_loot")
  .always("bones", 1)
  .common("death_rune", [10, 30])
  .common("blood_rune", [5, 20])
  .uncommon("wizard_robe_top", 1)
  .uncommon("wizard_hat", 1)
  .uncommon("coins", [1000, 5000])
  .rare("ancient_book", 1)
  .rare("staff_of_fire", 1)
  .rareMessage("The elder wizard drops an Ancient Book!")
  .build();

/**
 * Dragon King boss loot table (referenced by the boss definition).
 * Always drops dragon bones and dragon scales.  High-value rune and
 * dragon equipment, with an extremely rare Draconic Visage.
 */
export const dragonKingLoot: LootTable = dropTable("dragon_king_loot")
  .always("dragon_bones", 1)
  .always("dragon_scales", [5, 15])
  .common("coins", [20000, 50000])
  .uncommon("rune_platebody", 1)
  .uncommon("rune_platelegs", 1)
  .uncommon("dragon_dagger", 1)
  .rare("dragon_longsword", 1)
  .rare("dragon_med_helm", 1)
  .veryRare("draconic_visage", 1)
  .rareMessage("The Dragon King drops a Draconic Visage!")
  .build();
