/**
 * Pure requirement predicates.
 *
 * Requirements are predicates over a narrow, structural runtime view that
 * the Java `ScriptedPlayer` wrapper satisfies directly — no rich domain
 * {@link import("../core/player.js").Player}, no engine internals, no
 * mutation. Combinators compose predicates without inventing engine
 * capabilities.
 *
 * @module sdk/requirements
 */

import type { ItemId } from "../core/types.js";
import type { ScriptedQuest } from "../core/runtime.js";
import { skillIndex } from "./skills.js";

/**
 * The narrow read-only view a requirement predicate may inspect.
 *
 * Every member is a subset of the Java `ScriptedPlayer` surface so real
 * runtime content can pass `ctx.player` directly; tests pass a plain
 * object with the same shape.
 */
export interface RequirementView {
  getSkills(): {
    /** Base (non-boosted) level of a legacy skill index `0..20`. */
    getBaseLevel(id: number): number;
  };
  getInventory(): {
    has(id: ItemId, amount: number): boolean;
    count(id: ItemId): number;
  };
  quest(id: string): ScriptedQuest | null;
  questPoints(): number;
}

/**
 * One pure requirement predicate: `true` when the player satisfies it.
 */
export type Requirement = (view: RequirementView) => boolean;

/** Identity predicate: every player satisfies an empty requirement set. */
export const always: Requirement = () => true;

/** Negation: satisfies when the wrapped requirement does not. */
export function not(requirement: Requirement): Requirement {
  return (view) => !requirement(view);
}

/** Conjunction: satisfies when every wrapped requirement does. */
export function all(...requirements: readonly Requirement[]): Requirement {
  return (view) => requirements.every((requirement) => requirement(view));
}

/** Disjunction: satisfies when at least one wrapped requirement does. */
export function any(...requirements: readonly Requirement[]): Requirement {
  return (view) => requirements.some((requirement) => requirement(view));
}

/**
 * Base level requirement: the player's base level in `skill` must be at
 * least `level` (`1..99`, mirroring the quest parser bound).
 */
export function hasSkillLevel(
  skill: string,
  level: number,
): Requirement {
  if (!Number.isInteger(level) || level < 1 || level > 99) {
    throw new Error(
      `[sdk/requirements] hasSkillLevel level must be an integer 1..99, ` +
        `got ${level}`,
    );
  }
  const index = skillIndex(skill);
  return (view) => view.getSkills().getBaseLevel(index) >= level;
}

/**
 * Inventory requirement: the player holds at least `amount` (`1..2^31-1`)
 * of `itemId` in their inventory.
 */
export function hasItem(itemId: ItemId, amount: number = 1): Requirement {
  if (!Number.isInteger(amount) || amount < 1) {
    throw new Error(
      `[sdk/requirements] hasItem amount must be a positive integer, ` +
        `got ${amount}`,
    );
  }
  return (view) => view.getInventory().has(itemId, amount);
}

/** Quest-state requirement: the named quest is completed. */
export function hasCompletedQuest(questId: string): Requirement {
  return (view) => {
    const quest = view.quest(questId);
    return quest !== null && quest.state() === "completed";
  };
}

/** Quest-state requirement: the named quest is currently in progress. */
export function hasQuestInProgress(questId: string): Requirement {
  return (view) => {
    const quest = view.quest(questId);
    return quest !== null && quest.state() === "in_progress";
  };
}

/** Quest-state requirement: the named quest has not been started. */
export function hasNotStartedQuest(questId: string): Requirement {
  return (view) => {
    const quest = view.quest(questId);
    return quest === null || quest.state() === "not_started";
  };
}

/**
 * Quest-point requirement: the player's quest-point total must be at least
 * `points` (`0..10000`, mirroring the quest parser bound).
 */
export function hasQuestPoints(points: number): Requirement {
  if (!Number.isInteger(points) || points < 0 || points > 10000) {
    throw new Error(
      `[sdk/requirements] hasQuestPoints points must be an integer ` +
        `0..10000, got ${points}`,
    );
  }
  return (view) => view.questPoints() >= points;
}

/**
 * Evaluate one requirement against a live player view and return the
 * bounded diagnostic text when it is not satisfied (or `null` when it is).
 *
 * @param view  The narrow player view (usually `ctx.player`).
 * @param requirement  The requirement to evaluate.
 */
export function unmetReason(
  view: RequirementView,
  requirement: Requirement,
): string | null {
  return requirement(view) ? null : "A requirement is not met.";
}
