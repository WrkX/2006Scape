/**
 * NPC spawn definitions for Dragon Island.
 *
 * NPC ids use the well-known RS convention where possible, with custom
 * ids for island-specific creatures.
 *
 * @module areas/dragon_island/npcs
 */

import type { NpcSpawn } from "../../core/types.js";
import type { Player } from "../../core/player.js";

/**
 * Elder Wizard at the town entrance.  Starts the "Dragon Awakens" quest
 * when spoken to.
 */
export const elderWizard: NpcSpawn = {
  id: 667,
  x: 7000,
  y: 7020,
  plane: 0,
  walkRadius: 2,
  direction: "south",
};

/**
 * Dragon Guardians patrolling the volcanic peaks.
 * Combat level approximately 100.
 */
export const dragonGuardians: readonly NpcSpawn[] = [
  {
    id: 5001,
    x: 7060,
    y: 7080,
    plane: 0,
    walkRadius: 8,
    direction: "east",
  },
  {
    id: 5001,
    x: 7075,
    y: 7065,
    plane: 0,
    walkRadius: 8,
    direction: "west",
  },
  {
    id: 5001,
    x: 7045,
    y: 7090,
    plane: 0,
    walkRadius: 6,
    direction: "south",
  },
  {
    id: 5001,
    x: 7080,
    y: 7040,
    plane: 0,
    walkRadius: 7,
    direction: "north",
  },
];

/**
 * Island villagers providing shops and services.
 */
export const islandVillagers: readonly NpcSpawn[] = [
  /** General store shopkeeper at the town center. */
  {
    id: 5010,
    x: 7005,
    y: 7010,
    plane: 0,
    walkRadius: 1,
    direction: "east",
  },
  /** Fisherman villager near the dock. */
  {
    id: 5011,
    x: 6990,
    y: 7005,
    plane: 0,
    walkRadius: 3,
    direction: "south",
  },
  /** Armourer near the town square. */
  {
    id: 5012,
    x: 7010,
    y: 7015,
    plane: 0,
    walkRadius: 1,
    direction: "west",
  },
];

/**
 * Fishing spots along the island shoreline.
 */
export const fishingSpots: readonly NpcSpawn[] = [
  {
    id: 1520,
    x: 6965,
    y: 7010,
    plane: 0,
    direction: "south",
  },
  {
    id: 1520,
    x: 6970,
    y: 7005,
    plane: 0,
    direction: "east",
  },
  {
    id: 1520,
    x: 7090,
    y: 6970,
    plane: 0,
    direction: "west",
  },
  {
    id: 1520,
    x: 7095,
    y: 6975,
    plane: 0,
    direction: "north",
  },
];

/** All NPCs on Dragon Island combined into a single array. */
export const allNpcs: readonly NpcSpawn[] = [
  elderWizard,
  ...dragonGuardians,
  ...islandVillagers,
  ...fishingSpots,
];
