/**
 * Dragon Island area definition.
 *
 * A custom island accessible by boat from Port Sarim.  Home to the
 * "Dragon Awakens" quest, a Dragon King boss, and the Temple of Zaros raid.
 *
 * @module areas/dragon_island
 */

import { createArea } from "../area-builder.js";
import type { AreaObject } from "../types.js";
import type { Shop } from "../../core/types.js";
import { allNpcs } from "./npcs.js";
import { dragonGuardianLoot, elderWizardLoot } from "./drops.js";

// ─── Objects ─────────────────────────────────────────────────────────────────

const islandObjects: readonly AreaObject[] = [
  /** Boat at Port Sarim that transports players to the island. */
  { id: 6665, x: 3029, y: 3217, plane: 0 },
  /** Return boat on Dragon Island dock. */
  { id: 6665, x: 6985, y: 7000, plane: 0 },
  /** Ancient chest near the volcano base. */
  { id: 2213, x: 7050, y: 7050, plane: 0 },
  /** Dragon altar at the mountain peak. */
  { id: 409, x: 7065, y: 7075, plane: 0 },
  /** Rocks (mineable) near the volcano. */
  { id: 2090, x: 7040, y: 7060, plane: 0 },
  { id: 2090, x: 7045, y: 7065, plane: 0 },
  { id: 2090, x: 7035, y: 7070, plane: 0 },
  /** Willow trees near the village. */
  { id: 1308, x: 6990, y: 7020, plane: 0 },
  { id: 1308, x: 6995, y: 7025, plane: 0 },
  /** Normal trees scattered around. */
  { id: 1276, x: 6980, y: 7030, plane: 0 },
  { id: 1278, x: 7015, y: 7020, plane: 0 },
];

// ─── Shops ───────────────────────────────────────────────────────────────────

const islandGeneralStore: Shop = {
  id: "dragon_island_general",
  name: "Island Supplies",
  items: [
    { id: "lobster", amount: 10, price: 150 },
    { id: "swordfish", amount: 10, price: 200 },
    { id: "cooked_karambwan", amount: 5, price: 400 },
    { id: "prayer_potion", amount: 3, price: 2500 },
    { id: "anti_dragon_shield", amount: 5, price: 500 },
    { id: "tinderbox", amount: 10, price: 1 },
    { id: "rope", amount: 10, price: 15 },
  ],
  shared: false,
};

// ─── Area Definition ─────────────────────────────────────────────────────────

/**
 * Dragon Island — a custom volcanic island reachable by boat from Port Sarim.
 *
 * Bounded region: x 6950-7100, y 6900-7100 (plane 0).
 * Home to dragon guardians, an elder wizard, fishing spots, shops,
 * the Dragon King boss, and the Temple of Zaros raid entrance.
 */
export const dragonIsland = createArea({
  id: "dragon_island",
  name: "Dragon Island",
  bounds: {
    northWest: { x: 6950, y: 7100, plane: 0 },
    southEast: { x: 7100, y: 6900, plane: 0 },
  },
  npcs: allNpcs as readonly import("../../core/types.js").NpcSpawn[],
  objects: islandObjects,
  drops: [
    { npcId: 5001, table: dragonGuardianLoot },
    { npcId: 667, table: elderWizardLoot },
  ],
  shops: [islandGeneralStore],
  quests: [],
});

// Register with the engine.
defineArea(dragonIsland);
