/**
 * Declarative raid system type definitions.
 *
 * {@link defineRaid} registers a canonical schema-v1 raid definition that is
 * parsed into an immutable Java-owned descriptor and consumed by the WP5
 * raid runtime: the exact host command route drives the bounded
 * create/invite/join/leave/start lobby, start freezes an immutable
 * owner-first/join-FIFO roster and begins exactly one encounter, rooms
 * advance in declared order (a boss room embeds a {@link defineBoss}
 * controller that borrows the raid's sole handle), the final room's
 * completion enters the reward barrier, and the named rewards commit
 * roster-wide and atomically before {@code onComplete} runs once. Every
 * callback receives the narrow {@link RaidRoomContext} composed only of
 * accepted wrappers and handles.
 *
 * @module core/raid
 */

import type {
  ScriptArray,
  ScriptEncounterHandle,
  ScriptedPlayer,
  ScriptedPosition,
} from "./runtime.js";

/** Inclusive rectangle of one room or the whole raid on one plane. */
export interface RaidBounds {
  readonly minX: number;
  readonly minY: number;
  readonly maxX: number;
  readonly maxY: number;
  readonly plane: number;
}

/** Bounded muster rectangle on the raid plane for the start check. */
export interface RaidMuster {
  readonly minX: number;
  readonly minY: number;
  readonly maxX: number;
  readonly maxY: number;
}

/** World coordinates where the members are teleported on start. */
export interface RaidEntrance {
  readonly x: number;
  readonly y: number;
  readonly plane: number;
}

/**
 * Narrow runtime context passed to every executable raid callback.
 *
 * This is the only callback context for declarative raids: the borrowed
 * encounter handle, the raid owner, the active participant view, the room
 * identity, the room-relative elapsed ticks, the room center, and a bounded
 * announce broadcast. There is deliberately no rich domain {@link Player}
 * and no registry or engine access.
 */
export interface RaidRoomContext {
  id(): string;
  /** The active room id; {@code null} for raid-level callbacks. */
  roomId(): string | null;
  /** Zero-based room index; {@code -1} for raid-level callbacks. */
  roomIndex(): number;
  participants(): ScriptArray<ScriptedPlayer>;
  /** Game cycles since this room was entered (0 on entry). */
  elapsedTicks(): number;
  position(): ScriptedPosition;
  /** Broadcasts a message to every active live member. */
  announce(text: string): boolean;
  readonly encounter: ScriptEncounterHandle;
  readonly owner: ScriptedPlayer;
}

/** Result of a room tick. */
export type RoomResult =
  | { readonly status: "in_progress" }
  | { readonly status: "completed" }
  | { readonly status: "wiped"; readonly reason: string };

/** Boss-room reference to a {@link defineBoss} stable id. */
export interface RaidBossRoom {
  readonly bossId: string;
}

/**
 * One room of a declarative raid. Rooms must lie inside the raid bounds and
 * must not overlap. A boss room completes only when the embedded boss
 * controller reports DEFEATED; its own room tick result is ignored while
 * the boss is alive.
 */
export interface RaidRoomDefinition {
  readonly id: string;
  readonly name: string;
  readonly bounds: RaidBounds;
  readonly onEnter: (ctx: RaidRoomContext) => void;
  readonly onTick: (ctx: RaidRoomContext) => RoomResult;
  readonly onComplete: (ctx: RaidRoomContext) => void;
  /** Optional boss reference resolved at candidate validation. */
  readonly boss?: RaidBossRoom;
}

/**
 * Canonical schema-v1 declarative raid definition consumed by the raid
 * runtime.
 *
 * The command route must not be a reserved admin alias. Player limits must
 * be possible ({@code minPlayers <= maxPlayers}, each 1..8), the time limit
 * 1..100000 ticks, and every boss/reward/drop-table reference must name a
 * definition registered earlier in the same candidate. Named rewards are
 * required (1..8) and commit roster-wide and atomically at completion; the
 * optional reward table rolls once after that commit as private ground
 * deliveries.
 *
 * @example
 * ```ts
 * defineRaid({
 *   id: "temple_of_zaros",
 *   command: "temple-of-zaros",
 *   bounds: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4711, plane: 1 },
 *   muster: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4695 },
 *   entrance: { x: 2268, y: 4690, plane: 1 },
 *   minPlayers: 2,
 *   maxPlayers: 5,
 *   timeLimitTicks: 7200,
 *   rewards: ["zaros_raid_reward"],
 *   rooms: [
 *     {
 *       id: "guardian",
 *       name: "Hall of Guardians",
 *       bounds: { minX: 2264, minY: 4688, maxX: 2275, maxY: 4695, plane: 1 },
 *       onEnter(ctx) {
 *         ctx.announce("Ancient guardians stir from their slumber...");
 *       },
 *       onTick(ctx) {
 *         return ctx.elapsedTicks() >= 3
 *           ? { status: "completed" }
 *           : { status: "in_progress" };
 *       },
 *       onComplete(ctx) {
 *         ctx.announce("The guardians crumble to dust.");
 *       },
 *     },
 *     {
 *       id: "crypt",
 *       name: "Crypt of the Fallen",
 *       bounds: { minX: 2264, minY: 4696, maxX: 2287, maxY: 4711, plane: 1 },
 *       onEnter(ctx) {
 *         ctx.announce("A fallen Zarosian priest rises from its sarcophagus!");
 *       },
 *       onTick() {
 *         return { status: "in_progress" };
 *       },
 *       onComplete(ctx) {
 *         ctx.announce("The crypt falls silent.");
 *       },
 *       boss: { bossId: "dragon-king" },
 *     },
 *   ],
 * });
 * ```
 */
export interface RaidDefinition {
  /** Stable string id referenced by areas and diagnostics. */
  readonly id: string;
  /** Optional display name. */
  readonly name?: string;
  /** Exact WP1 host command route (never a reserved admin alias). */
  readonly command: string;
  /** The single-plane rectangle the encounter reserves for the raid. */
  readonly bounds: RaidBounds;
  /** Bounded muster rectangle on the raid plane for the start check. */
  readonly muster: RaidMuster;
  readonly entrance: RaidEntrance;
  readonly minPlayers: number;
  readonly maxPlayers: number;
  readonly timeLimitTicks: number;
  /** Ordered non-overlapping rooms; the last completion starts the barrier. */
  readonly rooms: readonly RaidRoomDefinition[];
  /** Named reward definitions applied roster-wide at completion (1..8). */
  readonly rewards: readonly string[];
  /** Optional named drop table rolled once after the roster commit. */
  readonly rewardTable?: string;
  /** Private TTL of the reward-table roll; required together with it. */
  readonly privateTicks?: number;
  /** Called once when the raid starts, before the first room enters. */
  readonly onStart?: (ctx: RaidRoomContext) => void;
  /** Called once after the roster rewards commit. */
  readonly onComplete?: (ctx: RaidRoomContext) => void;
  /** Called once on any wipe; awards nobody. */
  readonly onWipe?: (ctx: RaidRoomContext, reason: string) => void;
}

/**
 * Register a declarative raid definition with the engine.
 *
 * The definition is parsed into an immutable Java-owned descriptor and its
 * entry command is registered as an exact WP1 host route consumed by the
 * raid runtime. Duplicate stable ids, unloaded cross-references, impossible
 * player limits, overlapping rooms, and reserved command aliases reject the
 * candidate.
 *
 * @param definition The canonical schema-v1 raid definition.
 */
export type DefineRaid = (definition: RaidDefinition) => void;

declare global {
  const defineRaid: DefineRaid;
}
