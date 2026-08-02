/**
 * NPC spawn definitions for Dragon Island.
 *
 * Phase 5 WP4 migration: the former custom ids 5001/5010/5011/5012 and
 * 1520 are absent from npc.json and cannot pass strict definition-backed
 * validation. They are replaced by loaded 2006 ids: blue dragon (55) for
 * the Dragon Guardians, Man (1)/Fisherman (219) for the villagers, and the
 * production Fishing spot (309). The island itself moved to the Crandor
 * island region (2830..2870, 9630..9670): the 2006 cache carries real map
 * data and objects there, so the layered object projections and collision
 * transactions activate through real engine paths. The Guardian spawns
 * bind the canonical {@code dragon_guardian_loot} table with
 * private-to-killer delivery; the town shopkeeper is stationary and opens
 * the scripted island general store through its exact allocation route.
 *
 * @module areas/dragon_island/npcs
 */

import type { AreaNpcSpawn } from "../types.js";

/**
 * Elder Wizard at the town entrance.  Starts the "Dragon Awakens" quest
 * when spoken to.
 */
export const elderWizard: AreaNpcSpawn = {
  key: "elder-wizard",
  npcId: 667,
  x: 2838,
  y: 9650,
  plane: 0,
  walkRadius: 2,
  direction: "south",
  dropTable: "elder_wizard_loot",
  dropPolicy: "private-to-killer",
  privateTicks: 200,
};

/**
 * Dragon Guardians patrolling the volcanic peaks (blue dragons, combat
 * 111). Each spawn binds the Guardian loot table with private delivery.
 */
export const dragonGuardians: readonly AreaNpcSpawn[] = [
  {
    key: "dragon-guardian-1",
    npcId: 55,
    x: 2840,
    y: 9660,
    plane: 0,
    walkRadius: 8,
    direction: "east",
    dropTable: "dragon_guardian_loot",
    dropPolicy: "private-to-killer",
    privateTicks: 200,
  },
  {
    key: "dragon-guardian-2",
    npcId: 55,
    x: 2850,
    y: 9660,
    plane: 0,
    walkRadius: 8,
    direction: "west",
    dropTable: "dragon_guardian_loot",
    dropPolicy: "private-to-killer",
    privateTicks: 200,
  },
  {
    key: "dragon-guardian-3",
    npcId: 55,
    x: 2835,
    y: 9640,
    plane: 0,
    walkRadius: 6,
    direction: "south",
    dropTable: "dragon_guardian_loot",
    dropPolicy: "private-to-killer",
    privateTicks: 200,
  },
  {
    key: "dragon-guardian-4",
    npcId: 55,
    x: 2860,
    y: 9640,
    plane: 0,
    walkRadius: 7,
    direction: "north",
    dropTable: "dragon_guardian_loot",
    dropPolicy: "private-to-killer",
    privateTicks: 200,
  },
];

/**
 * Island villagers providing shops and services. The town shopkeeper is
 * stationary so its exact allocation route can open the scripted shop.
 */
export const islandVillagers: readonly AreaNpcSpawn[] = [
  /** General store shopkeeper at the town center. */
  {
    key: "villager-shopkeeper",
    npcId: 1,
    x: 2845,
    y: 9640,
    plane: 0,
    direction: "east",
    openShop: "dragon_island_general",
  },
  /** Fisherman villager near the dock. */
  {
    key: "villager-fisherman",
    npcId: 219,
    x: 2855,
    y: 9640,
    plane: 0,
    walkRadius: 3,
    direction: "south",
  },
  /** Armourer near the town square. */
  {
    key: "villager-armourer",
    npcId: 24,
    x: 2865,
    y: 9640,
    plane: 0,
    walkRadius: 1,
    direction: "west",
  },
];

/**
 * Fishing spots along the island shoreline.
 */
export const fishingSpots: readonly AreaNpcSpawn[] = [
  {
    key: "fishing-spot-1",
    npcId: 309,
    x: 2830,
    y: 9630,
    plane: 0,
    direction: "south",
  },
  {
    key: "fishing-spot-2",
    npcId: 309,
    x: 2860,
    y: 9630,
    plane: 0,
    direction: "east",
  },
  {
    key: "fishing-spot-3",
    npcId: 309,
    x: 2848,
    y: 9660,
    plane: 0,
    direction: "west",
  },
  {
    key: "fishing-spot-4",
    npcId: 309,
    x: 2858,
    y: 9660,
    plane: 0,
    direction: "north",
  },
];

/** All NPCs on Dragon Island combined into a single array. */
export const allNpcs: readonly AreaNpcSpawn[] = [
  elderWizard,
  ...dragonGuardians,
  ...islandVillagers,
  ...fishingSpots,
];
