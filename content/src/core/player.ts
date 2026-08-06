/**
 * Aspirational player domain model for future declarative / bot designs.
 *
 * **Not a live bridge API.** Executable GraalJS handlers receive
 * {@link import("./runtime.js").ScriptedPlayer} only. Methods and properties
 * on this interface may not exist on the Java wrapper — do not use this type
 * in `onNpc` / `onObject` / `onItem` / lifecycle callbacks.
 *
 * @module core/player
 */

import type {
  Bank,
  Equipment,
  Inventory,
  ItemId,
  Quests,
  Skills,
  WorldPoint,
} from "./types.js";

/**
 * The richer Player contract used to model content systems.
 *
 * Both {@link NetworkPlayer} (human, driven by client packets) and
 * {@link SimulatedPlayer} (bot, driven by a {@link BotBrain}) share this
 * interface — only the action source differs.
 *
 * This interface is not the Java object passed to NPC, object, or command
 * handlers. Use {@link import("./runtime.js").ScriptContext} in those
 * executable bridge callbacks.
 *
 * @see {@link https://github.com/2006-Scape}
 */
export interface Player {
  // ── Identity ────────────────────────────────────────────────────────────

  /** The player's display name. Immutable once set. */
  readonly username: string;

  /** The player's current world position. */
  readonly location: WorldPoint;

  // ── Chat ────────────────────────────────────────────────────────────────

  /**
   * Send a server message (appears in the player's chat box).
   * @param text Message content. Supports colour tags if the client supports them.
   */
  message(text: string): void;

  // ── Movement ────────────────────────────────────────────────────────────

  /**
   * Instantly move the player to a new location.
   * @param x Destination X coordinate.
   * @param y Destination Y coordinate.
   * @param plane Destination plane (defaults to 0 if omitted).
   */
  teleport(x: number, y: number, plane?: number): void;

  // ── Inventories ─────────────────────────────────────────────────────────

  /** The player's carried inventory (28 slots). */
  readonly inventory: Inventory;

  /** The player's bank. */
  readonly bank: Bank;

  /** The player's equipped items. */
  readonly equipment: Equipment;

  // ── Skills ──────────────────────────────────────────────────────────────

  /** The player's skill stats. */
  readonly skills: Skills;

  // ── Quests ──────────────────────────────────────────────────────────────

  /** Quest state tracker. */
  readonly quests: Quests;

  // ── Combat ──────────────────────────────────────────────────────────────

  /** Current hitpoints as a fraction of the maximum. Range [0, 1]. */
  readonly hpPercent: number;

  /** The player's combat level. */
  readonly combatLevel: number;

  /** Whether the player is currently in combat. */
  readonly inCombat: boolean;

  // ── State ───────────────────────────────────────────────────────────────

  /** Whether the player is currently online / active. */
  readonly isOnline: boolean;

  // ── Utilities ───────────────────────────────────────────────────────────

  /**
   * Check whether the player meets a skill requirement.
   * @param skill The skill to check.
   * @param level The minimum required base level.
   */
  hasSkillLevel(skill: import("./types.js").SkillId, level: number): boolean;

  /**
   * Award experience to a skill.
   * @param skill The skill to award experience in.
   * @param amount The amount of experience to grant.
   */
  addExperience(
    skill: import("./types.js").SkillId,
    amount: number,
  ): void;

  /**
   * Give an item to the player, falling back to bank if inventory is full.
   * @returns true if the item was placed in inventory or bank.
   */
  giveItem(id: ItemId, amount?: number): boolean;

  /**
   * Open a dialogue interface for this player.
   * @param dialogue The dialogue to display.
   */
  openDialogue(dialogue: import("./types.js").Dialogue): void;

  /**
   * Start a cutscene or locked camera sequence.
   * @param region The camera-focus region.
   * @param durationTicks How many game ticks the lock persists.
   */
  lockCamera(region: import("./types.js").WorldRegion, durationTicks: number): void;

  /** Release a camera lock early. */
  unlockCamera(): void;
}
