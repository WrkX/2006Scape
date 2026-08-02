/**
 * Boss system type definitions.
 *
 * Use {@link defineBoss} to store a custom boss definition in the bridge's
 * data registry. The current server does not dispatch these lifecycle hooks.
 *
 * @module core/boss
 */

import type { ItemId, LootTable } from "./types.js";
import type { Player } from "./player.js";

/**
 * Aspirational execution context described by boss lifecycle data.
 * The current bridge stores this data but does not invoke these hooks.
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
   * Roll on a named loot table and distribute loot to the top damage
   * dealers (or all participants if damage tracking is unavailable).
   *
   * @param lootTableId The id of a {@link LootTable} to roll on.
   */
  rollLoot(lootTableId: string): void;

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

/** A special attack definition. */
export interface BossSpecial {
  /** Cooldown in game ticks before the special can be used again. */
  readonly cooldownTicks: number;

  /** Called when the special fires. */
  readonly handler: (ctx: BossContext) => void;
}

/** Map of named specials available to the boss. */
export type BossSpecials = Readonly<Record<string, BossSpecial>>;

/** Triggers for boss fight phases. */
export interface BossPhase {
  /** Name shown in debug/log output. */
  readonly name: string;

  /** HP threshold (0-100) at which this phase activates. */
  readonly hpPercentThreshold: number;

  /** Called once when the phase begins. */
  readonly onEnter: (ctx: BossContext) => void;
}

/**
 * The full definition of a custom boss encounter.
 *
 * @example
 * ```ts
 * defineBoss({
 *   npcId: 1234,
 *   combatLevel: 450,
 *   maxHitpoints: 600,
 *
 *   onSpawn(ctx) {
 *     ctx.say("You dare disturb me?");
 *   },
 *
 *   onTick(ctx) {
 *     if (ctx.hpPercent < 50) {
 *       ctx.useSpecial("fire_wave");
 *     }
 *   },
 *
 *   onDeath(ctx) {
 *     ctx.rollLoot("dragon_king_loot");
 *   }
 * });
 * ```
 */
export interface BossDefinition {
  /** The NPC id that serves as the boss entity. */
  readonly npcId: number;

  /** Displayed combat level. */
  readonly combatLevel: number;

  /** Maximum hitpoints. */
  readonly maxHitpoints: number;

  /** Optional custom name override. */
  readonly displayName?: string;

  /** Called once when the boss spawns or respawns. */
  readonly onSpawn: (ctx: BossContext) => void;

  /**
   * Called every combat tick while the boss is engaged.
   * Primary hook for executing specials, phase logic, minion spawning, etc.
   */
  readonly onTick: (ctx: BossContext) => void;

  /** Called when the boss reaches 0 hitpoints. */
  readonly onDeath: (ctx: BossContext) => void;

  /** Optional map of named special attacks. */
  readonly specials?: BossSpecials;

  /** Optional phase transitions triggered by HP thresholds. */
  readonly phases?: readonly BossPhase[];

  /**
   * Respawn delay in game ticks. If omitted the boss does not respawn
   * (world-event / instance-only boss).
   */
  readonly respawnTicks?: number;
}

/**
 * Register a boss definition with the engine.
 *
 * Definitions are keyed by `npcId` and retained as data. Registering one does
 * not currently wire its hooks into NPC combat.
 *
 * @param definition The boss encounter definition.
 */
export type DefineBoss = (definition: BossDefinition) => void;

/** Global boss registry function exposed by the bridge. */
declare global {
  const defineBoss: DefineBoss;
}
