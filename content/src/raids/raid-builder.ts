/**
 * Raid builder — validated factory for {@link RaidDefinition} with room
 * registration, reward-table linking, and boss-room configuration.
 *
 * The fluent `raidBuilder()` lets content authors assemble a raid room by
 * room, with type-safe references to room ids.
 *
 * @module raids/raid-builder
 *
 * @example Manual construction
 * ```ts
 * import { createRaid, createRaidRoom, createBossRoom } from "./raid-builder.js";
 *
 * const guardianRoom = createRaidRoom({
 *   id: "guardians",
 *   name: "Hall of Guardians",
 *   onEnter: (ctx) => ctx.announce("The guardians awaken!"),
 *   onTick: (ctx) => ctx.players.every(p => p.inCombat === false)
 *     ? { status: "completed" }
 *     : { status: "in_progress" },
 *   onComplete: (ctx) => ctx.announce("Guardians defeated!"),
 * });
 *
 * const throneRoom = createRaidRoom({
 *   id: "throne",
 *   name: "Throne of Zaros",
 *   onEnter: (ctx) => ctx.announce("Zaros awaits..."),
 *   onTick: (_ctx) => ({ status: "in_progress" }),
 *   onComplete: (ctx) => ctx.announce("Victory!"),
 *   boss: createBossRoom({
 *     npcId: 7001,
 *     combatLevel: 500,
 *     maxHitpoints: 1200,
 *     onTick: (ctx) => { if (ctx.hpPercent < 0.4) ctx.useSpecial("doom"); },
 *     onDeath: (ctx) => ctx.say("Impossible..."),
 *   }),
 * });
 *
 * const templeRaid = createRaid("temple_of_zaros", {
 *   entrance: { x: 7000, y: 7000, plane: 0 },
 *   rooms: [guardianRoom, throneRoom],
 *   rewardTable: "zaros_raid_loot",
 *   minPlayers: 3,
 *   maxPlayers: 5,
 *   timeLimitTicks: 7200,
 *   onStart: (ctx) => ctx.announce("The temple doors seal behind you."),
 *   onComplete: (ctx) => ctx.announce("The temple crumbles. Escape!"),
 *   onWipe: (ctx, reason) => ctx.announce(`Raid failed: ${reason}`),
 * });
 *
 * defineRaid(templeRaid.id, templeRaid);
 * ```
 *
 * @example Fluent builder
 * ```ts
 * import { raidBuilder } from "./raid-builder.js";
 *
 * const raid = raidBuilder("sunken_temple")
 *   .entrance({ x: 5000, y: 4000, plane: 0 })
 *   .minPlayers(2)
 *   .maxPlayers(4)
 *   .rewardTable("sunken_temple_loot")
 *   .timeLimitTicks(6000)
 *   .onStart(ctx => ctx.announce("The temple floods with seawater!"))
 *   .room("kraken_pool", "Pool of the Kraken",
 *     ctx => ctx.announce("Tentacles rise from the depths!"),
 *     ctx => ctx.players.every(p => !p.inCombat)
 *       ? { status: "completed" }
 *       : { status: "in_progress" },
 *     ctx => ctx.announce("The kraken retreats."))
 *   .room("treasure_chamber", "Treasure Chamber",
 *     ctx => ctx.announce("Chests line the walls..."),
 *     (_ctx) => ({ status: "completed" }),
 *     ctx => ctx.announce("The chamber is yours!"))
 *   .onComplete(ctx => ctx.announce("The temple is cleared!"))
 *   .build();
 *
 * defineRaid(raid.id, raid);
 * ```
 */

import type { BossContext } from "../core/boss.js";
import type {
  RaidDefinition,
  RaidRoom,
  RaidBossRoom,
  RoomContext,
  RoomResult,
} from "../core/raid.js";
import type { WorldPoint } from "../core/types.js";

// ─── Internal helpers ─────────────────────────────────────────────────────────

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[raid-builder] ${message}`);
  }
}

// ─── Boss room factory ────────────────────────────────────────────────────────

/**
 * Options for a boss encounter within a raid room.
 */
export interface BossRoomOptions {
  readonly npcId: number;
  readonly combatLevel: number;
  readonly maxHitpoints: number;
  readonly onTick: (ctx: BossContext) => void;
  readonly onDeath: (ctx: BossContext) => void;
}

/**
 * Create a validated {@link RaidBossRoom} sub-definition.
 *
 * @param options  Boss configuration.
 * @returns A frozen {@link RaidBossRoom}.
 */
export function createBossRoom(options: BossRoomOptions): RaidBossRoom {
  assert(Number.isInteger(options.npcId) && options.npcId > 0,
    `Boss room: npcId must be a positive integer, got ${options.npcId}`);
  assert(Number.isInteger(options.combatLevel) && options.combatLevel > 0,
    `Boss room: combatLevel must be positive, got ${options.combatLevel}`);
  assert(Number.isInteger(options.maxHitpoints) && options.maxHitpoints > 0,
    `Boss room: maxHitpoints must be positive, got ${options.maxHitpoints}`);
  assert(typeof options.onTick === "function",
    "Boss room: onTick must be a function");
  assert(typeof options.onDeath === "function",
    "Boss room: onDeath must be a function");

  return Object.freeze({
    npcId: options.npcId,
    combatLevel: options.combatLevel,
    maxHitpoints: options.maxHitpoints,
    onTick: options.onTick,
    onDeath: options.onDeath,
  });
}

// ─── Room factory ─────────────────────────────────────────────────────────────

/**
 * Options for building a raid room.
 */
export interface RaidRoomOptions {
  readonly id: string;
  readonly name: string;
  readonly onEnter: (ctx: RoomContext) => void;
  readonly onTick: (ctx: RoomContext) => RoomResult;
  readonly onComplete: (ctx: RoomContext) => void;
  readonly boss?: RaidBossRoom;
}

/**
 * Create a validated {@link RaidRoom}.
 *
 * @param options  Room configuration.
 * @returns A frozen {@link RaidRoom}.
 */
export function createRaidRoom(options: RaidRoomOptions): RaidRoom {
  assert(typeof options.id === "string" && options.id.length > 0,
    "Room: id must be a non-empty string");
  assert(typeof options.name === "string" && options.name.length > 0,
    `Room "${options.id}": name must be a non-empty string`);
  assert(typeof options.onEnter === "function",
    `Room "${options.id}": onEnter must be a function`);
  assert(typeof options.onTick === "function",
    `Room "${options.id}": onTick must be a function`);
  assert(typeof options.onComplete === "function",
    `Room "${options.id}": onComplete must be a function`);

  return Object.freeze({
    id: options.id,
    name: options.name,
    onEnter: options.onEnter,
    onTick: options.onTick,
    onComplete: options.onComplete,
    boss: options.boss,
  });
}

// ─── Raid factory ─────────────────────────────────────────────────────────────

/**
 * Options for building a complete raid (everything except `id`, which is
 * passed as the first argument to {@link createRaid}).
 */
export interface RaidOptions {
  readonly entrance: WorldPoint;
  readonly minPlayers?: number;
  readonly maxPlayers?: number;
  readonly rooms: readonly RaidRoom[];
  readonly rewardTable: string;
  readonly timeLimitTicks?: number;
  readonly onStart?: (ctx: RoomContext) => void;
  readonly onComplete?: (ctx: RoomContext) => void;
  readonly onWipe?: (ctx: RoomContext, reason: string) => void;
}

/**
 * Create a fully validated {@link RaidDefinition}.
 *
 * Validation rules:
 * - `id` must be a non-empty, lower_snake_case string.
 * - `entrance` must have finite x, y, and plane values.
 * - `rooms` must have at least one entry with unique room ids.
 * - `rewardTable` must be a non-empty string.
 * - `minPlayers`/`maxPlayers` if set must be positive and min <= max.
 * - `timeLimitTicks` if set must be positive.
 *
 * @param id       Unique raid identifier.
 * @param options  Raid configuration (without id).
 * @returns A frozen {@link RaidDefinition} including id.
 */
export function createRaid(
  id: string,
  options: RaidOptions,
): RaidDefinition {
  assert(typeof id === "string" && id.length > 0,
    "Raid id must be a non-empty string");
  assert(/^[a-z][a-z0-9_]*$/.test(id),
    `Raid id "${id}" must be lower_snake_case`);

  // Validate entrance
  const ent = options.entrance;
  assert(ent !== undefined &&
    Number.isFinite(ent.x) && Number.isFinite(ent.y) &&
    Number.isFinite(ent.plane ?? 0),
    `Raid "${id}": entrance must have finite x, y, and plane values`);

  // Validate rooms
  assert(Array.isArray(options.rooms) && options.rooms.length > 0,
    `Raid "${id}": rooms must be a non-empty array`);
  const roomIds = new Set<string>();
  for (let i = 0; i < options.rooms.length; i++) {
    const room = options.rooms[i];
    assert(typeof room.id === "string" && room.id.length > 0,
      `Raid "${id}": rooms[${i}] must have a non-empty id`);
    assert(!roomIds.has(room.id),
      `Raid "${id}": duplicate room id "${room.id}"`);
    roomIds.add(room.id);
  }

  // Validate reward table
  assert(typeof options.rewardTable === "string" &&
    options.rewardTable.length > 0,
    `Raid "${id}": rewardTable must be a non-empty string`);

  // Validate player counts
  if (options.minPlayers !== undefined) {
    assert(Number.isInteger(options.minPlayers) && options.minPlayers >= 1,
      `Raid "${id}": minPlayers must be >= 1, got ${options.minPlayers}`);
  }
  if (options.maxPlayers !== undefined) {
    assert(Number.isInteger(options.maxPlayers) && options.maxPlayers >= 1,
      `Raid "${id}": maxPlayers must be >= 1, got ${options.maxPlayers}`);
  }
  if (options.minPlayers !== undefined && options.maxPlayers !== undefined) {
    assert(options.minPlayers <= options.maxPlayers,
      `Raid "${id}": minPlayers (${options.minPlayers}) must be <= maxPlayers (${options.maxPlayers})`);
  }

  // Validate time limit
  if (options.timeLimitTicks !== undefined) {
    assert(Number.isInteger(options.timeLimitTicks) &&
      options.timeLimitTicks > 0,
      `Raid "${id}": timeLimitTicks must be positive, got ${options.timeLimitTicks}`);
  }

  // Validate optional hooks
  if (options.onStart !== undefined) {
    assert(typeof options.onStart === "function",
      `Raid "${id}": onStart must be a function`);
  }
  if (options.onComplete !== undefined) {
    assert(typeof options.onComplete === "function",
      `Raid "${id}": onComplete must be a function`);
  }
  if (options.onWipe !== undefined) {
    assert(typeof options.onWipe === "function",
      `Raid "${id}": onWipe must be a function`);
  }

  return Object.freeze({
    id,
    entrance: options.entrance,
    minPlayers: options.minPlayers,
    maxPlayers: options.maxPlayers,
    rooms: options.rooms,
    rewardTable: options.rewardTable,
    timeLimitTicks: options.timeLimitTicks,
    onStart: options.onStart,
    onComplete: options.onComplete,
    onWipe: options.onWipe,
  });
}

/**
 * Validate a raid definition and immediately register it via the global
 * `defineRaid()` bridge function.
 *
 * Equivalent to calling `defineRaid(id, createRaid(id, options))`.
 *
 * @param id       Unique raid identifier.
 * @param options  Raid configuration (without id).
 */
export function registerRaid(id: string, options: RaidOptions): void {
  const def = createRaid(id, options);
  defineRaid(def.id, {
    entrance: def.entrance,
    minPlayers: def.minPlayers,
    maxPlayers: def.maxPlayers,
    rooms: def.rooms,
    rewardTable: def.rewardTable,
    timeLimitTicks: def.timeLimitTicks,
    onStart: def.onStart,
    onComplete: def.onComplete,
    onWipe: def.onWipe,
  });
}

// ─── Fluent builder ───────────────────────────────────────────────────────────

/**
 * Fluent builder for constructing a {@link RaidDefinition}.
 *
 * @example
 * ```ts
 * raidBuilder("my_raid")
 *   .entrance({ x: 0, y: 0, plane: 0 })
 *   .rewardTable("my_loot")
 *   .room("one", "First Room", enter, tick, complete)
 *   .room("two", "Second Room", enter, tick, complete)
 *   .build();
 * ```
 */
export class RaidBuilder {
  private _id: string;
  private _entrance: WorldPoint | null = null;
  private _minPlayers: number | undefined;
  private _maxPlayers: number | undefined;
  private _rooms: RaidRoom[] = [];
  private _rewardTable: string | null = null;
  private _timeLimitTicks: number | undefined;
  private _onStart: ((ctx: RoomContext) => void) | undefined;
  private _onComplete: ((ctx: RoomContext) => void) | undefined;
  private _onWipe: ((ctx: RoomContext, reason: string) => void) | undefined;

  constructor(id: string) {
    assert(typeof id === "string" && id.length > 0,
      "Raid id must be a non-empty string");
    this._id = id;
  }

  /** Set the world coordinates where players enter the raid. */
  entrance(point: WorldPoint): this {
    assert(Number.isFinite(point.x) && Number.isFinite(point.y),
      "entrance coordinates must be finite numbers");
    this._entrance = point;
    return this;
  }

  /** Set minimum players required. */
  minPlayers(n: number): this {
    assert(Number.isInteger(n) && n >= 1,
      `minPlayers must be >= 1, got ${n}`);
    this._minPlayers = n;
    return this;
  }

  /** Set maximum players allowed. */
  maxPlayers(n: number): this {
    assert(Number.isInteger(n) && n >= 1,
      `maxPlayers must be >= 1, got ${n}`);
    this._maxPlayers = n;
    return this;
  }

  /** Set the reward loot table id. */
  rewardTable(id: string): this {
    assert(typeof id === "string" && id.length > 0,
      "rewardTable must be a non-empty string");
    this._rewardTable = id;
    return this;
  }

  /** Set the raid time limit in game ticks. */
  timeLimitTicks(ticks: number): this {
    assert(Number.isInteger(ticks) && ticks > 0,
      `timeLimitTicks must be positive, got ${ticks}`);
    this._timeLimitTicks = ticks;
    return this;
  }

  /** Register a raid room. */
  room(
    id: string,
    name: string,
    onEnter: (ctx: RoomContext) => void,
    onTick: (ctx: RoomContext) => RoomResult,
    onComplete: (ctx: RoomContext) => void,
    boss?: RaidBossRoom,
  ): this {
    assert(!this._rooms.some(r => r.id === id),
      `Duplicate room id "${id}"`);
    this._rooms.push(createRaidRoom({
      id,
      name,
      onEnter,
      onTick,
      onComplete,
      boss,
    }));
    return this;
  }

  /** Set the onStart hook. */
  onStart(handler: (ctx: RoomContext) => void): this {
    this._onStart = handler;
    return this;
  }

  /** Set the onComplete hook. */
  onComplete(handler: (ctx: RoomContext) => void): this {
    this._onComplete = handler;
    return this;
  }

  /** Set the onWipe hook. */
  onWipe(handler: (ctx: RoomContext, reason: string) => void): this {
    this._onWipe = handler;
    return this;
  }

  /** Build the validated {@link RaidDefinition}. */
  build(): Omit<RaidDefinition, "id"> {
    assert(this._entrance !== null,
      "entrance is required (call .entrance())");
    assert(this._rewardTable !== null,
      "rewardTable is required (call .rewardTable())");
    assert(this._rooms.length > 0,
      "At least one room is required (call .room())");

    return createRaid(this._id, {
      entrance: this._entrance!,
      minPlayers: this._minPlayers,
      maxPlayers: this._maxPlayers,
      rooms: this._rooms,
      rewardTable: this._rewardTable!,
      timeLimitTicks: this._timeLimitTicks,
      onStart: this._onStart,
      onComplete: this._onComplete,
      onWipe: this._onWipe,
    });
  }
}

/**
 * Entry point for the fluent raid builder.
 *
 * @param id  Unique raid identifier (lower_snake_case).
 * @returns A new {@link RaidBuilder}.
 */
export function raidBuilder(id: string): RaidBuilder {
  return new RaidBuilder(id);
}
