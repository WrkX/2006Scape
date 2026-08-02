/**
 * Area / region content module type definitions.
 *
 * An area is a named region of the game world with its own NPC spawns,
 * object projections, drop bindings, shops, and lifecycle behavior.
 * Areas are the primary organisational unit for TypeScript-authored world
 * content and are activated through the WP4 area runtime: NPC allocations
 * with exact spawn keys, layered object projections, named WP2 drop
 * bindings, scripted-shop references, and enter/leave observers.
 *
 * @module areas/types
 */

/**
 * NPC spawn of one area. The {@code key} is the stable per-area spawn
 * identity used for exact allocation bindings; the numeric {@code npcId}
 * must be definition-backed.
 */
export interface AreaNpcSpawn {
  /** Stable lower-case per-area spawn key (1..64 characters). */
  readonly key: string;
  /** Definition-backed NPC id. */
  readonly npcId: number;
  readonly x: number;
  readonly y: number;
  readonly plane?: number;
  /** 0 = stationary; otherwise the legacy random-walk radius intent. */
  readonly walkRadius?: number;
  /** Legacy facing direction. */
  readonly direction?: "north" | "south" | "east" | "west";
  /** Game cycles until the allocation respawns after death (1..100000). */
  readonly respawnTicks?: number;
  /** Optional HP override; defaults to the loaded NPC definition. */
  readonly hp?: number;
  readonly maxHit?: number;
  readonly attack?: number;
  readonly defence?: number;
  /** Named WP2 drop table rolled exactly once when this allocation dies. */
  readonly dropTable?: string;
  /**
   * Delivery policy of the bound drop table:
   * "private-to-killer" (requires `privateTicks`) or "public".
   */
  readonly dropPolicy?: "private-to-killer" | "public";
  /** Private TTL in game cycles; required together with private delivery. */
  readonly privateTicks?: number;
  /**
   * Scripted shop id opened by this exact allocation's first click. The
   * spawn must be stationary so the exact allocation stays at its tile.
   */
  readonly openShop?: string;
}

/**
 * One per-action drop binding of an area object projection.
 */
export interface AreaObjectDrop {
  readonly action: "first" | "second" | "third" | "fourth";
  /** Named WP2 drop table rolled once when the exact projection is clicked. */
  readonly dropTable: string;
  readonly dropPolicy: "private-to-killer" | "public";
  /** Private TTL in game cycles; required together with private delivery. */
  readonly privateTicks?: number;
}

/**
 * Layered object projection of an area. Replaces the tile's visible object
 * through the exact object transaction; optional per-action drop bindings
 * register exact tile-position host routes.
 */
export interface AreaObject {
  /** Stable lower-case per-area object key (1..64 characters). */
  readonly key: string;
  /** Definition-backed object id. */
  readonly objectId: number;
  readonly x: number;
  readonly y: number;
  readonly plane?: number;
  readonly type?: number;
  readonly rotation?: number;
  /** Optional one-shot drop bindings per ordinal action. */
  readonly drops?: readonly AreaObjectDrop[];
}

/** Canonical inclusive bounds of one area. */
export interface AreaBounds {
  readonly minX: number;
  readonly minY: number;
  readonly maxX: number;
  readonly maxY: number;
  readonly plane: number;
}

/**
 * Complete canonical schema-v1 definition of a named area.
 *
 * @example
 * ```ts
 * defineArea({
 *   id: "dragon_island",
 *   name: "Dragon Island",
 *   bounds: { minX: 6950, minY: 6900, maxX: 7100, maxY: 7100, plane: 0 },
 *   npcs: [{
 *     key: "dragon-guardian-1",
 *     npcId: 55,
 *     x: 7060,
 *     y: 7080,
 *     walkRadius: 8,
 *     dropTable: "dragon_guardian_loot",
 *     dropPolicy: "private-to-killer",
 *     privateTicks: 200,
 *   }],
 *   objects: [{ key: "ancient-chest", objectId: 2213, x: 7050, y: 7050 }],
 *   shops: ["dragon_island_general"],
 *   quests: [],
 *   bosses: [],
 *   raids: [],
 * });
 * ```
 */
export interface AreaDefinition {
  /** Unique stable string id. */
  readonly id: string;
  /** Human-readable area name. */
  readonly name: string;
  /** Inclusive canonical bounds. */
  readonly bounds: AreaBounds;
  /** NPC spawns with exact per-area spawn keys. */
  readonly npcs: readonly AreaNpcSpawn[];
  /** Layered object projections with exact per-area keys. */
  readonly objects: readonly AreaObject[];
  /** Referenced scripted shop ids (defineShop runs first). */
  readonly shops?: readonly string[];
  /** Referenced quest ids (defineQuest runs first). */
  readonly quests?: readonly string[];
  /** Referenced boss stable ids (defineBoss runs first). */
  readonly bosses?: readonly string[];
  /** Referenced raid ids (defineRaid runs first). */
  readonly raids?: readonly string[];
  /** Optional enter observer; runs on the lifecycle area transition. */
  readonly onEnter?: (context: import("../core/runtime.js").AreaTransitionScriptContext) => void;
  /** Optional leave observer; runs on the lifecycle area transition. */
  readonly onLeave?: (context: import("../core/runtime.js").AreaTransitionScriptContext) => void;
}

/**
 * Register an area definition with the engine.
 *
 * The definition is parsed into an immutable Java-owned descriptor and
 * activated through the two-phase runtime activation transaction. Nested
 * definitions are not canonical: shops and other families must be
 * registered separately and referenced by id.
 *
 * @param area The canonical schema-v1 area definition.
 */
export type DefineArea = (area: AreaDefinition) => void;

declare global {
  const defineArea: DefineArea;
}
