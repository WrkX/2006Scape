/**
 * Dragon Island area definition.
 *
 * A custom volcanic island.  Home to the "Dragon Awakens" quest, a Dragon
 * King boss, and the Temple of Zaros raid.
 *
 * Phase 5 WP4 migration: the definition is now canonical schema-v1 with
 * exact spawn keys, definition-backed ids, a named chest drop binding, and
 * id references to the separately registered shop, quest, boss, and raid
 * definitions. The island moved to the Crandor island region (2830..2870,
 * 9630..9670): the 2006 cache carries real map data and objects there, so
 * the layered object projections and collision transactions activate
 * through real engine paths instead of empty map space. The Port Sarim
 * boat (outside the area bounds) is legacy travel content and is no longer
 * declared by the area.
 *
 * Phase 5 WP10: the area registers under the `dragon-island` content module;
 * the shop, quest, boss, and raid it references are registered by their own
 * modules earlier in the candidate.
 *
 * @module areas/dragon_island
 */

import { createArea, registerModule } from "../../sdk/index.js";
import type { AreaObject } from "../../sdk/index.js";
import { allNpcs } from "./npcs.js";
import "./shops.js";

// ─── Objects ─────────────────────────────────────────────────────────────────

const islandObjects: readonly AreaObject[] = [
  /** Return boat on Dragon Island dock. */
  { key: "return-boat", objectId: 6665, x: 2870, y: 9650, plane: 0 },
  /** Ancient chest near the volcano base; one-shot public loot. */
  {
    key: "ancient-chest",
    objectId: 2213,
    x: 2850,
    y: 9640,
    plane: 0,
    drops: [
      {
        action: "first",
        dropTable: "ancient_chest_loot",
        dropPolicy: "public",
      },
    ],
  },
  /** Dragon altar at the mountain peak. */
  { key: "dragon-altar", objectId: 409, x: 2862, y: 9645, plane: 0 },
  /** Rocks (mineable) near the volcano. */
  { key: "rocks-1", objectId: 2090, x: 2835, y: 9635, plane: 0 },
  { key: "rocks-2", objectId: 2090, x: 2848, y: 9635, plane: 0 },
  { key: "rocks-3", objectId: 2090, x: 2862, y: 9655, plane: 0 },
  /** Willow trees near the village. */
  { key: "willow-1", objectId: 1308, x: 2832, y: 9655, plane: 0 },
  { key: "willow-2", objectId: 1308, x: 2842, y: 9655, plane: 0 },
  /** Normal trees scattered around. */
  { key: "tree-1", objectId: 1276, x: 2858, y: 9660, plane: 0 },
  { key: "tree-2", objectId: 1278, x: 2830, y: 9640, plane: 0 },
];

// ─── Area Definition ─────────────────────────────────────────────────────────

/**
 * Dragon Island — a volcanic island in the Crandor region.
 *
 * Bounded region: x 2830-2870, y 9630-9670 (plane 0).
 * Home to dragon guardians, an elder wizard, fishing spots, shops,
 * the Dragon King boss, and the Temple of Zaros raid entrance.
 */
const dragonIsland = createArea({
  id: "dragon_island",
  name: "Dragon Island",
  bounds: {
    minX: 2830,
    minY: 9630,
    maxX: 2870,
    maxY: 9670,
    plane: 0,
  },
  npcs: allNpcs,
  objects: islandObjects,
  shops: ["dragon_island_general"],
  quests: ["dragon-awakens"],
  bosses: ["dragon-king"],
  raids: ["temple_of_zaros"],
});

// Register with the engine under the dragon-island module.
registerModule({ id: "dragon-island", schemaVersion: 1 }, () => {
  defineArea(dragonIsland);
});
