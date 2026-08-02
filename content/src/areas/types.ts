/**
 * Area / region content module type definitions.
 *
 * An area is a named region of the game world with its own NPCs, objects,
 * drops, shops, and quests.  Areas are the primary organisational unit for
 * TypeScript-authored world content.
 *
 * The current bridge stores area definitions as data; it does not populate
 * the live world or invoke lifecycle hooks.
 *
 * @module areas/types
 */

import type {
  NpcSpawn,
  NpcDropTable,
  ObjectDropTable,
  Shop,
  WorldRegion,
  LootTable,
} from "../core/types.js";
import type { BossDefinition } from "../core/boss.js";
import type { RaidDefinition } from "../core/raid.js";

// ─── Area Definition ──────────────────────────────────────────────────────

/**
 * Complete definition for a named area / region.
 *
 * @example
 * ```ts
 * defineArea({
 *   id: "dragon_island",
 *   name: "Dragon Island",
 *   bounds: {
 *     northWest: { x: 3000, y: 5000, plane: 0 },
 *     southEast: { x: 3100, y: 4900, plane: 0 }
 *   },
 *   npcs: [fireDragonNpc, babyDragonNpc],
 *   objects: [dungeonEntrance, ancientChest],
 *   drops: [dragonNpcDrops],
 *   shops: [islandGeneralStore],
 *   quests: [dragonAwakensQuest],
 *   bosses: [dragonKingDefinition],
 *   raids: [templeOfZarosRaid]
 * });
 * ```
 */
export interface AreaDefinition {
  /** Unique string id for this area. */
  readonly id: string;

  /** Human-readable area name. */
  readonly name: string;

  /** Axis-aligned bounding box for the area. */
  readonly bounds: WorldRegion;

  /** NPCs that spawn in this area. */
  readonly npcs: readonly NpcSpawn[];

  /** Interactive objects placed in this area. */
  readonly objects: readonly AreaObject[];

  /** NPC drop tables specific to this area. */
  readonly drops: readonly NpcDropTable[];

  /** Object drop tables (e.g. chest loot). */
  readonly objectDrops?: readonly ObjectDropTable[];

  /** Shops that exist in this area. */
  readonly shops: readonly Shop[];

  /** Quests that are started or progressed in this area. */
  readonly quests: readonly import("../quests/types.js").QuestDefinition[];

  /** Custom boss encounters in this area. */
  readonly bosses?: readonly BossDefinition[];

  /** Raid entrance points in this area. */
  readonly raids?: readonly Omit<RaidDefinition, "id">[];

  /** Aspirational hook for a future area consumer; not currently dispatched. */
  readonly onLoad?: () => void;

  /** Aspirational hook for a future area consumer; not currently dispatched. */
  readonly onUnload?: () => void;
}

/** An interactive world object placed in an area. */
export interface AreaObject {
  readonly id: number;
  readonly x: number;
  readonly y: number;
  readonly plane?: number;
  readonly type?: number;
  readonly rotation?: number;
}

/**
 * Register an area definition with the engine.
 *
 * @param area The area definition.
 */
export type DefineArea = (area: Omit<AreaDefinition, "id"> & { readonly id: string }) => void;

declare global {
  const defineArea: DefineArea;
}
