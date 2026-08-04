/**
 * Raid builder — validated factory for canonical schema-v1
 * {@link RaidDefinition} values with room registration, boss references,
 * muster/bounds, and reward linking.
 *
 * @module raids/raid-builder
 *
 * @example
 * ```ts
 * const raid = createRaid("temple_of_zaros", {
 *   command: "temple-of-zaros",
 *   bounds: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4711, plane: 1 },
 *   muster: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4695 },
 *   entrance: { x: 2268, y: 4690, plane: 1 },
 *   minPlayers: 2,
 *   maxPlayers: 5,
 *   timeLimitTicks: 7200,
 *   rewards: ["zaros_raid_reward"],
 *   rooms: [guardianRoom, cryptRoom],
 *   onStart: (ctx) => ctx.announce("The temple doors seal behind you."),
 *   onComplete: (ctx) => ctx.announce("The Temple of Zaros has been cleared!"),
 *   onWipe: (ctx, reason) => ctx.announce(`The raid has failed: ${reason}`),
 * });
 *
 * defineRaid(raid);
 * ```
 */

import type {
  DefineRaid,
  RaidBossRoom,
  RaidBounds,
  RaidDefinition,
  RaidEntrance,
  RaidMuster,
  RaidRoomContext,
  RaidRoomDefinition,
  RoomResult,
} from "../core/raid.js";

// ─── Internal helpers ─────────────────────────────────────────────────────────

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[raid-builder] ${message}`);
  }
}

/** Command aliases owned by the Java admin transport (WP1 route registry). */
const RESERVED_COMMANDS: ReadonlySet<string> = new Set([
  "scripts", "reload", "scriptdir",
]);

function rejectReserved(command: string, raidId: string): void {
  assert(!RESERVED_COMMANDS.has(command),
    `Raid "${raidId}": command alias '${command}' is reserved for the ` +
      "engine admin transport and cannot be registered by content");
}

// ─── Boss room factory ────────────────────────────────────────────────────────

/**
 * Options for a boss room reference.
 */
export interface BossRoomOptions {
  /** Stable id of a {@link defineBoss} definition registered earlier. */
  readonly bossId: string;
}

/**
 * Create a validated {@link RaidBossRoom} reference.
 *
 * @param options  Boss reference configuration.
 * @returns A frozen {@link RaidBossRoom}.
 */
export function createBossRoom(options: BossRoomOptions): RaidBossRoom {
  assert(typeof options.bossId === "string" && options.bossId.length > 0,
    "Boss room: bossId must be a non-empty string");
  return Object.freeze({ bossId: options.bossId });
}

// ─── Room factory ─────────────────────────────────────────────────────────────

/**
 * Options for building a raid room.
 */
export interface RaidRoomOptions {
  readonly id: string;
  readonly name: string;
  readonly bounds: RaidBounds;
  readonly onEnter: (ctx: RaidRoomContext) => void;
  readonly onTick: (ctx: RaidRoomContext) => RoomResult;
  readonly onComplete: (ctx: RaidRoomContext) => void;
  readonly boss?: RaidBossRoom;
}

/**
 * Create a validated {@link RaidRoomDefinition}.
 *
 * @param options  Room configuration.
 * @returns A frozen {@link RaidRoomDefinition}.
 */
export function createRaidRoom(options: RaidRoomOptions): RaidRoomDefinition {
  assert(typeof options.id === "string" && options.id.length > 0,
    "Room: id must be a non-empty string");
  assert(typeof options.name === "string" && options.name.length > 0,
    `Room "${options.id}": name must be a non-empty string`);
  assert(options.bounds !== undefined &&
    Number.isInteger(options.bounds.minX) && Number.isInteger(options.bounds.minY) &&
    Number.isInteger(options.bounds.maxX) && Number.isInteger(options.bounds.maxY) &&
    Number.isInteger(options.bounds.plane) &&
    options.bounds.minX <= options.bounds.maxX &&
    options.bounds.minY <= options.bounds.maxY,
    `Room "${options.id}": bounds must be an ordered integer rectangle`);
  assert(typeof options.onEnter === "function",
    `Room "${options.id}": onEnter must be a function`);
  assert(typeof options.onTick === "function",
    `Room "${options.id}": onTick must be a function`);
  assert(typeof options.onComplete === "function",
    `Room "${options.id}": onComplete must be a function`);

  return Object.freeze({
    id: options.id,
    name: options.name,
    bounds: Object.freeze({
      minX: options.bounds.minX,
      minY: options.bounds.minY,
      maxX: options.bounds.maxX,
      maxY: options.bounds.maxY,
      plane: options.bounds.plane,
    }),
    onEnter: options.onEnter,
    onTick: options.onTick,
    onComplete: options.onComplete,
    boss: options.boss ? Object.freeze({ ...options.boss }) : undefined,
  });
}

// ─── Raid factory ─────────────────────────────────────────────────────────────

/**
 * Options for building a complete raid (everything except `id`, which is
 * passed as the first argument to {@link createRaid}).
 */
export interface RaidOptions {
  readonly command: string;
  readonly bounds: RaidBounds;
  readonly muster: RaidMuster;
  readonly entrance: RaidEntrance;
  readonly minPlayers: number;
  readonly maxPlayers: number;
  readonly timeLimitTicks: number;
  readonly rooms: readonly RaidRoomDefinition[];
  readonly rewards: readonly string[];
  readonly name?: string;
  readonly rewardTable?: string;
  readonly privateTicks?: number;
  readonly onStart?: (ctx: RaidRoomContext) => void;
  readonly onComplete?: (ctx: RaidRoomContext) => void;
  readonly onWipe?: (ctx: RaidRoomContext, reason: string) => void;
}

function validBounds(bounds: RaidBounds): boolean {
  return bounds !== undefined &&
    Number.isInteger(bounds.minX) && Number.isInteger(bounds.minY) &&
    Number.isInteger(bounds.maxX) && Number.isInteger(bounds.maxY) &&
    Number.isInteger(bounds.plane) &&
    bounds.minX <= bounds.maxX && bounds.minY <= bounds.maxY;
}

/**
 * Create a fully validated {@link RaidDefinition}.
 *
 * Validation rules:
 * - `id` must be a non-empty, lower_snake_case string and `command` a
 *   lower-case command name.
 * - `bounds` must be an ordered integer rectangle; `muster` an ordered
 *   rectangle inside it; `entrance` inside the bounds on the raid plane.
 * - `rooms` must be non-empty with unique ids and ordered rectangles inside
 *   the raid bounds (overlap validation is enforced by the Java parser).
 * - `rewards` must be a non-empty array of non-empty string ids.
 * - `minPlayers`/`maxPlayers` must satisfy `1 <= min <= max <= 8`.
 * - `timeLimitTicks` must be a positive integer.
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

  assert(typeof options.command === "string" &&
    /^[a-z0-9][a-z0-9._-]*$/.test(options.command),
    `Raid "${id}": command must be a lower-case command name`);
  rejectReserved(options.command, id);
  assert(validBounds(options.bounds),
    `Raid "${id}": bounds must be an ordered integer rectangle`);

  const muster = options.muster;
  assert(muster !== undefined &&
    Number.isInteger(muster.minX) && Number.isInteger(muster.minY) &&
    Number.isInteger(muster.maxX) && Number.isInteger(muster.maxY) &&
    muster.minX <= muster.maxX && muster.minY <= muster.maxY,
    `Raid "${id}": muster must be an ordered integer rectangle`);
  assert(muster.minX >= options.bounds.minX && muster.maxX <= options.bounds.maxX &&
    muster.minY >= options.bounds.minY && muster.maxY <= options.bounds.maxY,
    `Raid "${id}": muster must lie inside the raid bounds`);

  const entrance = options.entrance;
  assert(entrance !== undefined &&
    Number.isInteger(entrance.x) && Number.isInteger(entrance.y) &&
    Number.isInteger(entrance.plane) &&
    entrance.x >= options.bounds.minX && entrance.x <= options.bounds.maxX &&
    entrance.y >= options.bounds.minY && entrance.y <= options.bounds.maxY &&
    entrance.plane === options.bounds.plane,
    `Raid "${id}": entrance must lie inside the raid bounds on its plane`);

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
    assert(validBounds(room.bounds),
      `Raid "${id}": room "${room.id}" bounds must be an ordered integer rectangle`);
    assert(room.bounds.minX >= options.bounds.minX &&
      room.bounds.maxX <= options.bounds.maxX &&
      room.bounds.minY >= options.bounds.minY &&
      room.bounds.maxY <= options.bounds.maxY &&
      room.bounds.plane === options.bounds.plane,
      `Raid "${id}": room "${room.id}" bounds must lie inside the raid bounds on its plane`);
  }

  assert(Array.isArray(options.rewards) && options.rewards.length > 0,
    `Raid "${id}": rewards must be a non-empty array of named reward ids`);
  const rewardIds = new Set<string>();
  for (let i = 0; i < options.rewards.length; i++) {
    assert(typeof options.rewards[i] === "string" && options.rewards[i].length > 0,
      `Raid "${id}": rewards[${i}] must be a non-empty string id`);
    assert(!rewardIds.has(options.rewards[i]),
      `Raid "${id}": duplicate reward reference '${options.rewards[i]}'`);
    rewardIds.add(options.rewards[i]);
  }

  assert(Number.isInteger(options.minPlayers) && options.minPlayers >= 1 &&
    options.minPlayers <= 8,
    `Raid "${id}": minPlayers must be 1..8, got ${options.minPlayers}`);
  assert(Number.isInteger(options.maxPlayers) && options.maxPlayers >= 1 &&
    options.maxPlayers <= 8,
    `Raid "${id}": maxPlayers must be 1..8, got ${options.maxPlayers}`);
  assert(options.minPlayers <= options.maxPlayers,
    `Raid "${id}": minPlayers (${options.minPlayers}) must be <= maxPlayers (${options.maxPlayers})`);

  assert(Number.isInteger(options.timeLimitTicks) && options.timeLimitTicks > 0,
    `Raid "${id}": timeLimitTicks must be positive, got ${options.timeLimitTicks}`);

  if (options.name !== undefined) {
    assert(typeof options.name === "string" && options.name.length > 0,
      `Raid "${id}": name must be a non-empty string`);
  }
  if (options.rewardTable !== undefined) {
    assert(typeof options.rewardTable === "string" && options.rewardTable.length > 0,
      `Raid "${id}": rewardTable must be a non-empty string`);
    assert(Number.isInteger(options.privateTicks) && (options.privateTicks ?? 0) >= 1,
      `Raid "${id}": a named rewardTable requires privateTicks >= 1`);
  }
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
    name: options.name,
    command: options.command,
    bounds: Object.freeze({ ...options.bounds }),
    muster: Object.freeze({ ...muster }),
    entrance: Object.freeze({ ...entrance }),
    minPlayers: options.minPlayers,
    maxPlayers: options.maxPlayers,
    timeLimitTicks: options.timeLimitTicks,
    rooms: Object.freeze(options.rooms.map((room) => Object.freeze({
      id: room.id,
      name: room.name,
      bounds: Object.freeze({ ...room.bounds }),
      onEnter: room.onEnter,
      onTick: room.onTick,
      onComplete: room.onComplete,
      boss: room.boss ? Object.freeze({ ...room.boss }) : undefined,
    }))),
    rewards: Object.freeze([...options.rewards]),
    rewardTable: options.rewardTable,
    privateTicks: options.privateTicks,
    onStart: options.onStart,
    onComplete: options.onComplete,
    onWipe: options.onWipe,
  });
}

/**
 * Validate a raid definition and immediately register it via the global
 * `defineRaid()` bridge function.
 *
 * Equivalent to calling `defineRaid(createRaid(id, options))`.
 *
 * @param id       Unique raid identifier.
 * @param options  Raid configuration (without id).
 */
export function registerRaid(id: string, options: RaidOptions): void {
  defineRaid(createRaid(id, options));
}

// ─── Fluent builder ───────────────────────────────────────────────────────────

/**
 * Fluent builder for constructing a {@link RaidDefinition}.
 */
export class RaidBuilder {
  private _id: string;
  private _command: string | null = null;
  private _bounds: RaidBounds | null = null;
  private _muster: RaidMuster | null = null;
  private _entrance: RaidEntrance | null = null;
  private _minPlayers: number | undefined;
  private _maxPlayers: number | undefined;
  private _timeLimitTicks: number | undefined;
  private _rooms: RaidRoomDefinition[] = [];
  private _rewards: string[] = [];
  private _rewardTable: string | undefined;
  private _privateTicks: number | undefined;
  private _name: string | undefined;
  private _onStart: ((ctx: RaidRoomContext) => void) | undefined;
  private _onComplete: ((ctx: RaidRoomContext) => void) | undefined;
  private _onWipe: ((ctx: RaidRoomContext, reason: string) => void) | undefined;

  constructor(id: string) {
    assert(typeof id === "string" && id.length > 0,
      "Raid id must be a non-empty string");
    this._id = id;
  }

  /** Set the exact host command route. */
  command(name: string): this {
    assert(typeof name === "string" && name.length > 0,
      "command must be a non-empty string");
    this._command = name;
    return this;
  }

  /** Set the raid bounds rectangle. */
  bounds(bounds: RaidBounds): this {
    assert(validBounds(bounds), "bounds must be an ordered integer rectangle");
    this._bounds = bounds;
    return this;
  }

  /** Set the bounded muster rectangle on the raid plane. */
  muster(muster: RaidMuster): this {
    assert(Number.isInteger(muster.minX) && Number.isInteger(muster.minY) &&
      Number.isInteger(muster.maxX) && Number.isInteger(muster.maxY) &&
      muster.minX <= muster.maxX && muster.minY <= muster.maxY,
      "muster must be an ordered integer rectangle");
    this._muster = muster;
    return this;
  }

  /** Set the world coordinates where the members teleport on start. */
  entrance(point: RaidEntrance): this {
    assert(Number.isInteger(point.x) && Number.isInteger(point.y) &&
      Number.isInteger(point.plane),
      "entrance coordinates must be integers");
    this._entrance = point;
    return this;
  }

  /** Set minimum players required. */
  minPlayers(n: number): this {
    assert(Number.isInteger(n) && n >= 1 && n <= 8,
      `minPlayers must be 1..8, got ${n}`);
    this._minPlayers = n;
    return this;
  }

  /** Set maximum players allowed. */
  maxPlayers(n: number): this {
    assert(Number.isInteger(n) && n >= 1 && n <= 8,
      `maxPlayers must be 1..8, got ${n}`);
    this._maxPlayers = n;
    return this;
  }

  /** Set the raid time limit in game ticks. */
  timeLimitTicks(ticks: number): this {
    assert(Number.isInteger(ticks) && ticks > 0,
      `timeLimitTicks must be positive, got ${ticks}`);
    this._timeLimitTicks = ticks;
    return this;
  }

  /** Set the named reward ids applied roster-wide at completion. */
  rewards(ids: readonly string[]): this {
    assert(Array.isArray(ids) && ids.length > 0,
      "rewards must be a non-empty array of named reward ids");
    this._rewards = [...ids];
    return this;
  }

  /** Set the optional completion reward table id. */
  rewardTable(id: string, privateTicks?: number): this {
    assert(typeof id === "string" && id.length > 0,
      "rewardTable must be a non-empty string");
    this._rewardTable = id;
    this._privateTicks = privateTicks;
    return this;
  }

  /** Set an optional display name. */
  name(name: string): this {
    assert(typeof name === "string" && name.length > 0,
      "name must be a non-empty string");
    this._name = name;
    return this;
  }

  /** Register a raid room. */
  room(
    id: string,
    name: string,
    bounds: RaidBounds,
    onEnter: (ctx: RaidRoomContext) => void,
    onTick: (ctx: RaidRoomContext) => RoomResult,
    onComplete: (ctx: RaidRoomContext) => void,
    boss?: RaidBossRoom,
  ): this {
    assert(!this._rooms.some(r => r.id === id),
      `Duplicate room id "${id}"`);
    this._rooms.push(createRaidRoom({
      id,
      name,
      bounds,
      onEnter,
      onTick,
      onComplete,
      boss,
    }));
    return this;
  }

  /** Set the onStart hook. */
  onStart(handler: (ctx: RaidRoomContext) => void): this {
    this._onStart = handler;
    return this;
  }

  /** Set the onComplete hook. */
  onComplete(handler: (ctx: RaidRoomContext) => void): this {
    this._onComplete = handler;
    return this;
  }

  /** Set the onWipe hook. */
  onWipe(handler: (ctx: RaidRoomContext, reason: string) => void): this {
    this._onWipe = handler;
    return this;
  }

  /** Build the validated {@link RaidDefinition}. */
  build(): Omit<RaidDefinition, "id"> {
    assert(this._command !== null,
      "command is required (call .command())");
    assert(this._bounds !== null,
      "bounds is required (call .bounds())");
    assert(this._muster !== null,
      "muster is required (call .muster())");
    assert(this._entrance !== null,
      "entrance is required (call .entrance())");
    assert(this._rooms.length > 0,
      "At least one room is required (call .room())");
    assert(this._rewards.length > 0,
      "rewards is required (call .rewards())");

    return createRaid(this._id, {
      command: this._command!,
      bounds: this._bounds!,
      muster: this._muster!,
      entrance: this._entrance!,
      minPlayers: this._minPlayers ?? 1,
      maxPlayers: this._maxPlayers ?? 8,
      timeLimitTicks: this._timeLimitTicks ?? 6000,
      rooms: this._rooms,
      rewards: this._rewards,
      name: this._name,
      rewardTable: this._rewardTable,
      privateTicks: this._privateTicks,
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

export type { DefineRaid };
