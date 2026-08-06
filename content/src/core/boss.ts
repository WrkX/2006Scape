/**
 * Declarative boss system type definitions.
 *
 * {@link defineBoss} registers a canonical schema-v1 boss definition that is
 * parsed into an immutable Java-owned descriptor and consumed by the WP3
 * boss runtime: the standalone adapter begins one encounter through the
 * definition's exact command/object entry route, an encounter-agnostic
 * controller drives spawn, ordered phases, armed special cooldowns, named
 * drops, death, and cleanup, and every callback receives the narrow
 * {@link BossRuntimeContext} composed only of accepted wrappers and handles.
 *
 * @module core/boss
 */

import type { Player } from "./player.js";
import type {
  ScriptArray,
  ScriptEncounterHandle,
  ScriptNpcHandle,
  ScriptedPlayer,
  ScriptedPosition,
} from "./runtime.js";

/**
 * Bounded inclusive arena slice of one declarative boss.
 * Sides are validated to 1..64 tiles; the plane is 0..3.
 */
export interface BossArena {
  readonly minX: number;
  readonly minY: number;
  readonly maxX: number;
  readonly maxY: number;
  readonly plane: number;
}

/** Boss spawn point; must lie inside the declared arena. */
export interface BossSpawn {
  readonly x: number;
  readonly y: number;
}

/** Optional owner teleport applied by the standalone adapter on entry. */
export interface BossEntryTeleport {
  readonly x: number;
  readonly y: number;
}

/** Object-entry route: an exact static object id plus ordinal action. */
export interface BossObjectEntry {
  readonly objectId: number;
  readonly action: "first" | "second" | "third" | "fourth";
}

/**
 * Narrow runtime context passed to every executable boss callback.
 *
 * This is the only callback context for declarative bosses: the spawned
 * boss NPC handle, the borrowed encounter handle, the owner and participant
 * view, live position/HP, `say`, and `useSpecial`. There is deliberately no
 * rich domain {@link Player} and no registry or engine access; combat and
 * pathfinding stay in the engine and are reached through the accepted
 * capability handles.
 */
export interface BossRuntimeContext {
  id(): string;
  readonly boss: ScriptNpcHandle;
  readonly encounter: ScriptEncounterHandle;
  readonly owner: ScriptedPlayer;
  participants(): ScriptArray<ScriptedPlayer>;
  position(): ScriptedPosition;
  /** Current HP fraction 0..1 of the spawned boss. */
  hpPercent(): number;
  alive(): boolean;
  /** Broadcasts a forced chat through the boss NPC. */
  say(text: string): boolean;
  /**
   * Arms one declared named special. Once armed, the special fires first
   * after its declared cooldown and then every cooldown game cycles while
   * the boss is alive. Arming is idempotent.
   */
  useSpecial(name: string): boolean;
}

/** One phase of a declarative boss; thresholds run strictly descending. */
export interface BossPhase {
  readonly name: string;
  /** HP threshold in percent (0..100) at which this phase activates once. */
  readonly hpPercentThreshold: number;
  readonly onEnter: (ctx: BossRuntimeContext) => void;
}

/** One named special of a declarative boss. */
export interface BossSpecial {
  /** Repeat interval in game cycles (1..100000). */
  readonly cooldownTicks: number;
  readonly handler: (ctx: BossRuntimeContext) => void;
}

export type BossSpecials = Readonly<Record<string, BossSpecial>>;

/**
 * Cleanup contract of a declarative boss. Only
 * {@code "close-on-terminal"} is canonical: the standalone adapter closes
 * its owned encounter on any terminal controller result.
 */
export type BossCleanupPolicy = "close-on-terminal";

/**
 * Canonical schema-v1 declarative boss definition consumed by the boss
 * runtime.
 *
 * Exactly one entry source is required (command XOR objectEntry) so no
 * canonical boss can be inert. The numeric `npcId` must be definition-backed
 * when NPC definitions are loaded and remains the duplicate registry key for
 * combat ownership. `dropTable` must name a table registered earlier in the
 * same candidate and requires `privateTicks`.
 *
 * @example
 * ```ts
 * defineBoss({
 *   id: "dragon-king",
 *   npcId: 54,
 *   name: "Dragon King",
 *   combatLevel: 450,
 *   maxHitpoints: 600,
 *   maxHit: 40,
 *   attack: 300,
 *   defence: 300,
 *   arena: { minX: 3200, minY: 3200, maxX: 3210, maxY: 3210, plane: 0 },
 *   spawn: { x: 3205, y: 3205 },
 *   command: "dragon-king",
 *   dropTable: "dragon_king_loot",
 *   privateTicks: 200,
 *   onSpawn(ctx) {
 *     ctx.say("You dare enter my domain?");
 *   },
 *   phases: [
 *     {
 *       name: "Enrage",
 *       hpPercentThreshold: 50,
 *       onEnter(ctx) {
 *         ctx.say("Now you will feel true dragon fire!");
 *         ctx.useSpecial("fire_wave");
 *       },
 *     },
 *   ],
 *   specials: {
 *     fire_wave: {
 *       cooldownTicks: 12,
 *       handler(ctx) {
 *         ctx.owner.message("The dragon's fire wave engulfs you!");
 *       },
 *     },
 *   },
 * });
 * ```
 */
export interface BossDefinition {
  /** Stable string id referenced by diagnostics and raid definitions. */
  readonly id: string;
  /** Numeric NPC id; definition-backed and the duplicate combat-ownership key. */
  readonly npcId: number;
  /** Optional display name. */
  readonly name?: string;
  readonly combatLevel: number;
  readonly maxHitpoints: number;
  readonly maxHit: number;
  readonly attack: number;
  readonly defence: number;
  readonly arena: BossArena;
  readonly spawn: BossSpawn;
  /** Entry command route; mutually exclusive with `objectEntry`. */
  readonly command?: string;
  /** Optional explicit-close command route. */
  readonly closeCommand?: string;
  /** Object entry route; mutually exclusive with `command`. */
  readonly objectEntry?: BossObjectEntry;
  /** Optional owner teleport applied by the standalone adapter on entry. */
  readonly entryTeleport?: BossEntryTeleport;
  /** Called once when the boss spawns. */
  readonly onSpawn: (ctx: BossRuntimeContext) => void;
  /** Called on every poll tick after phases and specials while alive. */
  readonly onTick?: (ctx: BossRuntimeContext) => void;
  /** Called once when the boss dies, before the named drops are rolled. */
  readonly onDeath?: (ctx: BossRuntimeContext) => void;
  /** Phases in strictly descending `hpPercentThreshold` order. */
  readonly phases?: readonly BossPhase[];
  /** Named specials armed through {@link BossRuntimeContext.useSpecial}. */
  readonly specials?: BossSpecials;
  /** Named drop table (registered earlier in the candidate) rolled on death. */
  readonly dropTable?: string;
  /** Private TTL in game cycles; required together with `dropTable`. */
  readonly privateTicks?: number;
  readonly cleanupPolicy?: BossCleanupPolicy;
}

/**
 * Register a declarative boss definition with the engine.
 *
 * The definition is parsed into an immutable Java-owned descriptor; its
 * entry route is registered as an exact WP1 host route and the standalone
 * adapter consumes it at runtime. Duplicate npc ids, duplicate stable ids,
 * unloaded NPC ids, and unresolvable named references reject the candidate.
 *
 * @param definition The canonical schema-v1 boss definition.
 */
export type DefineBoss = (definition: BossDefinition) => void;

/**
 * @deprecated Data-only context type retained by the raid room builder
 * (`createBossRoom`) until Phase 5 WP5 migrates raid rooms. Executable
 * declarative bosses receive {@link BossRuntimeContext} instead; this
 * interface is never passed to a runtime callback.
 */
export interface BossContext {
  /** The NPC id of this boss. */
  readonly npcId: number;

  /** The boss's current hitpoints as a fraction of maximum [0, 1]. */
  readonly hpPercent: number;

  /** All players currently engaged in the fight. */
  readonly engagedPlayers: readonly Player[];

  /**
   * Make the boss NPC broadcast a message to all engaged players.
   * @param text The message text.
   */
  say(text: string): void;

  /**
   * Trigger a named boss special attack.
   *
   * Specials are defined in the boss definition's
   * {@link BossSpecials|specials map} and invoked by name.
   *
   * @param attackName The key of the special attack to execute.
   */
  useSpecial(attackName: string): void;

  /**
   * Roll on a named drop table and distribute loot to the top damage
   * dealers (or all participants if damage tracking is unavailable).
   *
   * @param dropTableId The id of a named drop table to roll on.
   */
  rollLoot(dropTableId: string): void;

  /**
   * Heal the boss by a flat amount (capped at max HP).
   * @param amount Hitpoints to restore.
   */
  heal(amount: number): void;

  /**
   * Spawn minion NPCs around the boss arena.
   * @param npcId The NPC id for minions.
   * @param count How many minions to spawn.
   */
  spawnMinions(npcId: number, count: number): void;

  /**
   * Force the boss to switch targets.
   * @param player The player to target, or omit for a random engaged player.
   */
  switchTarget(player?: Player): void;
}
