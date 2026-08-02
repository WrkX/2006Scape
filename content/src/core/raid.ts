/**
 * Raid system type definitions.
 *
 * Raids are instanced group encounters built from a sequence of rooms.
 * Each room is a self-contained module with its own spawn logic, completion
 * condition, and optional boss.
 *
 * The current bridge stores raid definitions as data and does not dispatch
 * their lifecycle hooks.
 *
 * @module core/raid
 */

import type { WorldPoint, LootTable, ItemId } from "./types.js";
import type { Player } from "./player.js";
import type { BossContext } from "./boss.js";

// ─── Room ──────────────────────────────────────────────────────────────────

/** Aspirational context described by room lifecycle data. */
export interface RoomContext {
  /** The raid instance this room belongs to. */
  readonly raidId: string;

  /** All players in the raid instance. */
  readonly players: readonly Player[];

  /** The room's position in the raid sequence (0-indexed). */
  readonly roomIndex: number;

  /**
   * Broadcast a message to all players in the raid instance.
   * @param text Message text.
   */
  announce(text: string): void;
}

/**
 * A single room within a raid.
 *
 * Rooms describe an intended processing order. The current server stores this
 * definition but does not execute it.
 */
export interface RaidRoom {
  /** Unique identifier for this room layout. */
  readonly id: string;

  /** Human-readable name shown to players on entry. */
  readonly name: string;

  /**
   * Called when the room is loaded for a raid instance.
   * Use this to spawn NPCs, configure puzzles, etc.
   */
  readonly onEnter: (ctx: RoomContext) => void;

  /**
   * Called every tick while the room is active.
   * Use this to tick puzzle logic, check for completion, etc.
   *
   * @returns A {@link RoomResult} indicating whether the room is in-progress,
   *          complete, or the raid is wiped.
   */
  readonly onTick: (ctx: RoomContext) => RoomResult;

  /**
   * Called when the room is completed (all players advance to the next room
   * or the reward chamber).
   */
  readonly onComplete: (ctx: RoomContext) => void;

  /** Optional boss fight that takes place in this room. */
  readonly boss?: RaidBossRoom;
}

/** Result of a room tick. */
export type RoomResult =
  | { readonly status: "in_progress" }
  | { readonly status: "completed" }
  | { readonly status: "wiped"; readonly reason: string };

/** Sub-definition for a room that also contains a boss fight. */
export interface RaidBossRoom {
  /** NPC id of the boss. */
  readonly npcId: number;

  /** Boss combat level. */
  readonly combatLevel: number;

  /** Boss maximum hitpoints. */
  readonly maxHitpoints: number;

  /** Called each combat tick while the boss is alive. */
  readonly onTick: (ctx: BossContext) => void;

  /** Called when the boss dies. */
  readonly onDeath: (ctx: BossContext) => void;
}

// ─── Raid Definition ──────────────────────────────────────────────────────

/**
 * The full definition of a raid encounter.
 *
 * @example
 * ```ts
 * defineRaid({
 *   id: "temple_of_zaros",
 *   entrance: { x: 7000, y: 7000, plane: 0 },
 *   rooms: [guardianRoom, puzzleRoom, cryptRoom, zarosBossRoom],
 *   rewardTable: "zaros_raid_loot"
 * });
 * ```
 */
export interface RaidDefinition {
  /** Unique string id for this raid. */
  readonly id: string;

  /** World coordinates where players enter the raid. */
  readonly entrance: WorldPoint;

  /**
   * Minimum number of players required to start the raid.
   * @default 1
   */
  readonly minPlayers?: number;

  /**
   * Maximum number of players allowed in the raid.
   * @default 5
   */
  readonly maxPlayers?: number;

  /**
   * Ordered list of rooms.  Players progress through them sequentially.
   * The final room's completion triggers the reward phase.
   */
  readonly rooms: readonly RaidRoom[];

  /** The id of the {@link LootTable} used for rewards. */
  readonly rewardTable: string;

  /**
   * Time limit in game ticks.  If the raid takes longer it is automatically
   * wiped.
   * @default 6000 (roughly 1 hour at 600ms ticks)
   */
  readonly timeLimitTicks?: number;

  /**
   * Called once when the raid instance is first created (before any room
   * enters).  Use for global setup.
   */
  readonly onStart?: (ctx: RoomContext) => void;

  /**
   * Called when the raid is fully completed (all rooms cleared, rewards
   * distributed).
   */
  readonly onComplete?: (ctx: RoomContext) => void;

  /**
   * Called when the raid is wiped or the time limit expires.
   */
  readonly onWipe?: (ctx: RoomContext, reason: string) => void;
}

/**
 * Register a raid definition with the engine.
 *
 * @param definition The raid definition.
 */
export type DefineRaid = (definition: RaidDefinition) => void;

declare global {
  const defineRaid: DefineRaid;
}
