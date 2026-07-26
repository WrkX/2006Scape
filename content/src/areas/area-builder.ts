/**
 * Area builder — validated factory for {@link AreaDefinition} that lets
 * content authors declare regions, NPCs, objects, drops, shops, quests,
 * bosses, and raids in one place.
 *
 * Areas are the primary organisational unit for TypeScript-authored world
 * content.  The engine loads all area modules and wires them into the live
 * world during startup or when the `--dev` `reloadContent()` trigger fires.
 *
 * @module areas/area-builder
 *
 * @example Manual construction
 * ```ts
 * import { createArea } from "./area-builder.js";
 * import type { NpcSpawn, Shop } from "../core/types.js";
 *
 * const fireDragon: NpcSpawn = {
 *   id: 50, x: 3050, y: 4950, walkRadius: 5, direction: "south",
 * };
 *
 * const generalStore: Shop = {
 *   id: "dragon_isle_store",
 *   name: "Island Supplies",
 *   items: [{ id: "lobster", amount: 10, price: 150 }],
 *   shared: false,
 * };
 *
 * const dragonIsland = createArea({
 *   id: "dragon_island",
 *   name: "Dragon Island",
 *   bounds: {
 *     northWest: { x: 3000, y: 5000, plane: 0 },
 *     southEast: { x: 3100, y: 4900, plane: 0 },
 *   },
 *   npcs: [fireDragon, babyDragon],
 *   objects: [{ id: 2213, x: 3050, y: 4950 }],
 *   drops: [{ npcId: 50, table: dragonDropTable }],
 *   shops: [generalStore],
 *   quests: [],
 * });
 *
 * defineArea(dragonIsland);
 * ```
 *
 * @example Fluent builder
 * ```ts
 * import { areaBuilder } from "./area-builder.js";
 *
 * const area = areaBuilder("haunted_woods")
 *   .name("Haunted Woods")
 *   .bounds(
 *     { x: 4000, y: 3000, plane: 0 },
 *     { x: 4200, y: 2800, plane: 0 },
 *   )
 *   .npc({ id: 100, x: 4100, y: 2900, walkRadius: 3 })
 *   .npc({ id: 101, x: 4150, y: 2850, direction: "west" })
 *   .object({ id: 500, x: 4100, y: 2950 })
 *   .shop({
 *     id: "woods_general",
 *     name: "Woods Outpost",
 *     items: [{ id: "shark", amount: 5, price: 200 }],
 *     shared: false,
 *   })
 *   .dropTable(100, ghostDropTable)
 *   .onLoad(() => console.log("Haunted Woods loaded"))
 *   .build();
 *
 * defineArea(area);
 * ```
 */

import type {
  NpcSpawn,
  NpcDropTable,
  ObjectDropTable,
  Shop,
  WorldRegion,
  LootTable,
  WorldPoint,
} from "../core/types.js";
import type { BossDefinition } from "../core/boss.js";
import type { RaidDefinition } from "../core/raid.js";
import type { QuestDefinition } from "../quests/types.js";
import type { AreaDefinition, AreaObject } from "./types.js";

// ─── Internal helpers ─────────────────────────────────────────────────────────

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[area-builder] ${message}`);
  }
}

function isValidPoint(pt: WorldPoint): boolean {
  return (
    pt !== undefined &&
    Number.isFinite(pt.x) &&
    Number.isFinite(pt.y) &&
    Number.isFinite(pt.plane ?? 0)
  );
}

// ─── Area options ─────────────────────────────────────────────────────────────

/**
 * Options for building an area definition.
 */
export interface AreaOptions {
  readonly id: string;
  readonly name: string;
  readonly bounds: WorldRegion;
  readonly npcs: readonly NpcSpawn[];
  readonly objects: readonly AreaObject[];
  readonly drops: readonly NpcDropTable[];
  readonly objectDrops?: readonly ObjectDropTable[];
  readonly shops: readonly Shop[];
  readonly quests: readonly QuestDefinition[];
  readonly bosses?: readonly BossDefinition[];
  readonly raids?: readonly Omit<RaidDefinition, "id">[];
  readonly onLoad?: () => void;
  readonly onUnload?: () => void;
}

/**
 * Create a fully validated {@link AreaDefinition}.
 *
 * Validation rules:
 * - `id` must be non-empty, lower_snake_case.
 * - `name` must be non-empty.
 * - `bounds` must have valid northWest and southEast points with
 *   northWest.y > southEast.y and northWest.x < southEast.x.
 * - All NPC spawns must have valid coordinates.
 * - All objects must have valid positions and positive ids.
 * - All shops must have unique ids and non-empty names.
 * - All drop tables must reference a valid NPC id and loot table.
 *
 * @param options  Area configuration.
 * @returns A frozen {@link AreaDefinition}.
 */
export function createArea(options: AreaOptions): AreaDefinition & { readonly id: string } {
  assert(typeof options.id === "string" && options.id.length > 0,
    "Area id must be a non-empty string");
  assert(/^[a-z][a-z0-9_]*$/.test(options.id),
    `Area id "${options.id}" must be lower_snake_case`);
  assert(typeof options.name === "string" && options.name.length > 0,
    `Area "${options.id}": name must be a non-empty string`);

  // Validate bounds
  const b = options.bounds;
  assert(b !== undefined &&
    isValidPoint(b.northWest) && isValidPoint(b.southEast),
    `Area "${options.id}": bounds must have valid northWest and southEast`);
  assert(b.northWest.x <= b.southEast.x,
    `Area "${options.id}": bounds northWest.x (${b.northWest.x}) must be <= southEast.x (${b.southEast.x})`);
  assert(b.northWest.y >= b.southEast.y,
    `Area "${options.id}": bounds northWest.y (${b.northWest.y}) must be >= southEast.y (${b.southEast.y})`);

  // Validate NPCs
  assert(Array.isArray(options.npcs),
    `Area "${options.id}": npcs must be an array`);
  for (let i = 0; i < options.npcs.length; i++) {
    const npc = options.npcs[i];
    assert(Number.isInteger(npc.id) && npc.id > 0,
      `Area "${options.id}": npcs[${i}] must have a positive id`);
    assert(Number.isFinite(npc.x) && Number.isFinite(npc.y),
      `Area "${options.id}": npcs[${i}] (id=${npc.id}) position must be finite`);
  }

  // Validate objects
  assert(Array.isArray(options.objects),
    `Area "${options.id}": objects must be an array`);
  for (let i = 0; i < options.objects.length; i++) {
    const obj = options.objects[i];
    assert(Number.isInteger(obj.id) && obj.id > 0,
      `Area "${options.id}": objects[${i}] must have a positive id`);
    assert(Number.isFinite(obj.x) && Number.isFinite(obj.y),
      `Area "${options.id}": objects[${i}] (id=${obj.id}) position must be finite`);
  }

  // Validate NPC drop tables
  assert(Array.isArray(options.drops),
    `Area "${options.id}": drops must be an array`);
  for (let i = 0; i < options.drops.length; i++) {
    const dt = options.drops[i];
    assert(Number.isInteger(dt.npcId) && dt.npcId > 0,
      `Area "${options.id}": drops[${i}] must have a positive npcId`);
    assert(dt.table !== undefined && typeof dt.table.id === "string",
      `Area "${options.id}": drops[${i}] must have a valid loot table`);
  }

  // Validate object drop tables
  if (options.objectDrops) {
    for (let i = 0; i < options.objectDrops.length; i++) {
      const odt = options.objectDrops[i];
      assert(Number.isInteger(odt.objectId) && odt.objectId > 0,
        `Area "${options.id}": objectDrops[${i}] must have a positive objectId`);
      assert(odt.table !== undefined && typeof odt.table.id === "string",
        `Area "${options.id}": objectDrops[${i}] must have a valid loot table`);
    }
  }

  // Validate shops
  assert(Array.isArray(options.shops),
    `Area "${options.id}": shops must be an array`);
  const shopIds = new Set<string>();
  for (let i = 0; i < options.shops.length; i++) {
    const shop = options.shops[i];
    assert(typeof shop.id === "string" && shop.id.length > 0,
      `Area "${options.id}": shops[${i}] must have a non-empty id`);
    assert(!shopIds.has(shop.id),
      `Area "${options.id}": duplicate shop id "${shop.id}"`);
    shopIds.add(shop.id);
  }

  // Validate quests
  assert(Array.isArray(options.quests),
    `Area "${options.id}": quests must be an array`);
  for (let i = 0; i < options.quests.length; i++) {
    const q = options.quests[i];
    assert(typeof q.id === "string" && q.id.length > 0,
      `Area "${options.id}": quests[${i}] must have a valid id`);
  }

  // Validate bosses
  if (options.bosses) {
    for (let i = 0; i < options.bosses.length; i++) {
      const boss = options.bosses[i];
      assert(Number.isInteger(boss.npcId) && boss.npcId > 0,
        `Area "${options.id}": bosses[${i}] must have a positive npcId`);
    }
  }

  // Validate optional hooks
  if (options.onLoad !== undefined) {
    assert(typeof options.onLoad === "function",
      `Area "${options.id}": onLoad must be a function`);
  }
  if (options.onUnload !== undefined) {
    assert(typeof options.onUnload === "function",
      `Area "${options.id}": onUnload must be a function`);
  }

  return Object.freeze({
    id: options.id,
    name: options.name,
    bounds: options.bounds,
    npcs: options.npcs,
    objects: options.objects,
    drops: options.drops,
    objectDrops: options.objectDrops,
    shops: options.shops,
    quests: options.quests,
    bosses: options.bosses,
    raids: options.raids,
    onLoad: options.onLoad,
    onUnload: options.onUnload,
  });
}

/**
 * Validate an area definition and immediately register it via the global
 * `defineArea()` bridge function.
 *
 * Equivalent to calling `defineArea(createArea(options))`.
 *
 * @param options  Area configuration.
 */
export function registerArea(options: AreaOptions): void {
  defineArea(createArea(options));
}

// ─── Fluent builder ───────────────────────────────────────────────────────────

/**
 * Fluent builder for constructing an {@link AreaDefinition}.
 *
 * Accumulates NPCs, objects, drops, shops, quests, bosses, and raids
 * through chainable methods.
 *
 * @example
 * ```ts
 * areaBuilder("my_zone")
 *   .name("My Zone")
 *   .bounds(nw, se)
 *   .npc({ id: 1, x: 10, y: 20 })
 *   .object({ id: 100, x: 15, y: 25 })
 *   .dropTable(1, myLootTable)
 *   .shop(myShop)
 *   .quest(myQuest)
 *   .boss(myBoss)
 *   .build();
 * ```
 */
export class AreaBuilder {
  private _id: string;
  private _name: string | null = null;
  private _bounds: WorldRegion | null = null;
  private _npcs: NpcSpawn[] = [];
  private _objects: AreaObject[] = [];
  private _drops: NpcDropTable[] = [];
  private _objectDrops: ObjectDropTable[] = [];
  private _shops: Shop[] = [];
  private _quests: QuestDefinition[] = [];
  private _bosses: BossDefinition[] = [];
  private _raids: Omit<RaidDefinition, "id">[] = [];
  private _onLoad: (() => void) | undefined;
  private _onUnload: (() => void) | undefined;

  constructor(id: string) {
    assert(typeof id === "string" && id.length > 0,
      "Area id must be a non-empty string");
    this._id = id;
  }

  /** Set the human-readable area name. */
  name(name: string): this {
    assert(typeof name === "string" && name.length > 0,
      "Area name must be a non-empty string");
    this._name = name;
    return this;
  }

  /** Set the axis-aligned bounding box. */
  bounds(northWest: WorldPoint, southEast: WorldPoint): this {
    assert(isValidPoint(northWest) && isValidPoint(southEast),
      "bounds must have valid northwest and southeast points");
    assert(northWest.x <= southEast.x,
      `northWest.x (${northWest.x}) must be <= southEast.x (${southEast.x})`);
    assert(northWest.y >= southEast.y,
      `northWest.y (${northWest.y}) must be >= southEast.y (${southEast.y})`);
    this._bounds = { northWest, southEast };
    return this;
  }

  /** Add an NPC spawn to this area. */
  npc(spawn: NpcSpawn): this {
    assert(Number.isInteger(spawn.id) && spawn.id > 0,
      "NPC spawn must have a positive id");
    assert(Number.isFinite(spawn.x) && Number.isFinite(spawn.y),
      `NPC (id=${spawn.id}) position must be finite`);
    this._npcs.push(spawn);
    return this;
  }

  /** Add an interactive object to this area. */
  object(obj: AreaObject): this {
    assert(Number.isInteger(obj.id) && obj.id > 0,
      "Area object must have a positive id");
    assert(Number.isFinite(obj.x) && Number.isFinite(obj.y),
      `Object (id=${obj.id}) position must be finite`);
    this._objects.push(obj);
    return this;
  }

  /** Link an NPC to a loot table. */
  dropTable(npcId: number, table: LootTable): this {
    assert(Number.isInteger(npcId) && npcId > 0,
      `dropTable: npcId must be positive, got ${npcId}`);
    assert(table !== undefined && typeof table.id === "string",
      "dropTable: table must have a non-empty id");
    this._drops.push({ npcId, table });
    return this;
  }

  /** Link an object (e.g. chest) to a loot table. */
  objectDropTable(objectId: number, table: LootTable): this {
    assert(Number.isInteger(objectId) && objectId > 0,
      `objectDropTable: objectId must be positive, got ${objectId}`);
    assert(table !== undefined && typeof table.id === "string",
      "objectDropTable: table must have a non-empty id");
    this._objectDrops.push({ objectId, table });
    return this;
  }

  /** Register a shop in this area. */
  shop(shop: Shop): this {
    assert(typeof shop.id === "string" && shop.id.length > 0,
      "Shop must have a non-empty id");
    assert(!this._shops.some(s => s.id === shop.id),
      `Duplicate shop id "${shop.id}"`);
    this._shops.push(shop);
    return this;
  }

  /** Register a quest that starts or progresses in this area. */
  quest(quest: QuestDefinition): this {
    assert(typeof quest.id === "string" && quest.id.length > 0,
      "Quest must have a valid id");
    this._quests.push(quest);
    return this;
  }

  /** Register a custom boss encounter in this area. */
  boss(boss: BossDefinition): this {
    assert(Number.isInteger(boss.npcId) && boss.npcId > 0,
      "Boss must have a positive npcId");
    this._bosses.push(boss);
    return this;
  }

  /** Register a raid entrance point in this area. */
  raid(raid: Omit<RaidDefinition, "id">): this {
    this._raids.push(raid);
    return this;
  }

  /** Set the onLoad hook. */
  onLoad(handler: () => void): this {
    this._onLoad = handler;
    return this;
  }

  /** Set the onUnload hook. */
  onUnload(handler: () => void): this {
    this._onUnload = handler;
    return this;
  }

  /** Build the validated {@link AreaDefinition}. */
  build(): AreaDefinition & { readonly id: string } {
    assert(this._name !== null, "name is required (call .name())");
    assert(this._bounds !== null, "bounds is required (call .bounds())");

    return createArea({
      id: this._id,
      name: this._name!,
      bounds: this._bounds!,
      npcs: this._npcs,
      objects: this._objects,
      drops: this._drops,
      objectDrops: this._objectDrops.length > 0
        ? this._objectDrops : undefined,
      shops: this._shops,
      quests: this._quests,
      bosses: this._bosses.length > 0 ? this._bosses : undefined,
      raids: this._raids.length > 0 ? this._raids : undefined,
      onLoad: this._onLoad,
      onUnload: this._onUnload,
    });
  }
}

/**
 * Entry point for the fluent area builder.
 *
 * @param id  Unique area identifier (lower_snake_case).
 * @returns A new {@link AreaBuilder}.
 */
export function areaBuilder(id: string): AreaBuilder {
  return new AreaBuilder(id);
}
