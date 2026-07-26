/**
 * Quest system type definitions.
 *
 * Quests are stateful content scripts that progress through named stages.
 * Each quest is defined once and the engine persists its state per player.
 *
 * @module quests/types
 */

import type { Player } from "../core/player.js";
import type { ItemId, SkillId, Dialogue } from "../core/types.js";

// ─── Quest Definition ──────────────────────────────────────────────────────

/**
 * Full definition of a quest.
 *
 * @example
 * ```ts
 * defineQuest({
 *   id: "dragon_awakens",
 *   name: "Dragon Awakens",
 *   difficulty: "master",
 *
 *   requirements: {
 *     quests: ["dragon_slayer"],
 *     skills: { defence: 70, magic: 60 }
 *   },
 *
 *   startPoint: { x: 3000, y: 5000, plane: 0 },
 *
 *   stages: [
 *     { id: "start", onEnter: (p) => p.message("Speak to the elder...") },
 *     { id: "collect_scales", onEnter: (p) => p.message("Collect 5 dragon scales.") },
 *     { id: "awaken_dragon", onEnter: (p) => p.message("Visit the dragon altar.") },
 *     { id: "completed", onEnter: (p) => p.message("Quest complete!") }
 *   ],
 *
 *   rewards: {
 *     experience: { defence: 50000, magic: 30000 },
 *     items: { dragon_token: 1 },
 *     unlocks: ["dragon_king_boss"]
 *   }
 * });
 * ```
 */
export interface QuestDefinition {
  /** Unique quest identifier. */
  readonly id: string;

  /** Display name shown in the quest journal. */
  readonly name: string;

  /** Difficulty classification. */
  readonly difficulty: QuestDifficulty;

  /** Requirements that must be met before the quest can be started. */
  readonly requirements: QuestRequirements;

  /** World coordinates where the quest is started. */
  readonly startPoint: import("../core/types.js").WorldPoint;

  /** Optional NPC that starts the quest via dialogue. */
  readonly startNpc?: {
    readonly npcId: number;
    readonly dialogue: Dialogue;
  };

  /** Ordered list of stages. */
  readonly stages: readonly QuestStage[];

  /** Rewards granted on completion. */
  readonly rewards: QuestRewards;

  /** Called once when any player completes the quest. */
  readonly onGlobalComplete?: (player: Player) => void;
}

/** Quest difficulty tiers. */
export type QuestDifficulty = "novice" | "intermediate" | "experienced" | "master" | "grandmaster";

/** Requirements that must be met before starting. */
export interface QuestRequirements {
  /** Quests that must be completed first. */
  readonly quests?: readonly string[];

  /** Skill levels that must be met. */
  readonly skills?: Partial<Record<SkillId, number>>;

  /** Items that must be in inventory or bank. */
  readonly items?: readonly ItemId[];

  /** Minimum combat level. */
  readonly combatLevel?: number;
}

// ─── Quest Stage ───────────────────────────────────────────────────────────

/**
 * A single stage within a quest.
 *
 * The quest progresses linearly through its stages array.  Each stage's
 * `onEnter` fires once when the player reaches it.  The stage's `condition`
 * is checked each tick to determine whether to advance to the next stage.
 */
export interface QuestStage {
  /** Unique id for this stage (must be unique within the quest). */
  readonly id: string;

  /** Brief description shown in the quest journal. */
  readonly description: string;

  /**
   * Called once when the player enters this stage.
   * Use for dialogue, instructions, NPC spawns, etc.
   */
  readonly onEnter: (player: Player) => void;

  /**
   * Called every tick while the player is on this stage.
   * @returns true if the stage is complete and the player should advance.
   */
  readonly condition: (player: Player) => boolean;

  /**
   * Called once when the stage is completed (before advancing to the next).
   * Use for cleanup, cutscenes, etc.
   */
  readonly onComplete?: (player: Player) => void;

  /**
   * Called every tick (after condition check) for ongoing stage logic.
   * Optional — most stages only need condition + onEnter/onComplete.
   */
  readonly onTick?: (player: Player) => void;
}

// ─── Quest Rewards ─────────────────────────────────────────────────────────

/** Rewards granted when a quest is completed. */
export interface QuestRewards {
  /** Experience granted per skill. */
  readonly experience?: Partial<Record<SkillId, number>>;

  /** Items granted to the player. */
  readonly items?: Partial<Record<string, number>>;

  /** Quest point reward. */
  readonly questPoints?: number;

  /** Unlock tokens (bosses, areas, shops, etc.). */
  readonly unlocks?: readonly string[];

  /** Access flags enabled on completion (e.g. "dragon_island_access"). */
  readonly accessFlags?: readonly string[];
}

// ─── Quest Registry ────────────────────────────────────────────────────────

/**
 * Register a quest definition with the engine.
 *
 * @param definition The quest definition.
 */
export type DefineQuest = (definition: QuestDefinition) => void;

declare global {
  const defineQuest: DefineQuest;
}
