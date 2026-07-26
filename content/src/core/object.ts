/**
 * Object interaction type definitions.
 *
 * Register handlers that fire when a player interacts with a world object
 * (door, chest, altar, tree, rock, etc.).
 *
 * @module core/object
 */

import type { Player } from "./player.js";

/** Action verbs a player can perform on a world object. */
export type ObjectAction =
  | "use"
  | "open"
  | "close"
  | "climb"
  | "pick"
  | "chop"
  | "mine"
  | "bury"
  | "pray"
  | "search"
  | "enter"
  | "exit"
  | "light"
  | "extinguish"
  | "operate";

/** Metadata for a single world object. */
export interface GameObject {
  /** The object's numeric id. */
  readonly id: number;
  /** World X coordinate. */
  readonly x: number;
  /** World Y coordinate. */
  readonly y: number;
  /** Plane the object is on. */
  readonly plane: number;
  /** The object's type constant (matches client-side type). */
  readonly type: number;
  /** Orientation / rotation (0-3). */
  readonly rotation: number;
  /** The region that contains this object. */
  readonly region: number;
}

/**
 * Handler invoked when a player interacts with a world object.
 *
 * @param player  The player who triggered the interaction.
 * @param object  Metadata for the object they clicked.
 */
export type ObjectInteractionHandler = (
  player: Player,
  object: GameObject,
) => void;

/**
 * Register an object interaction handler.
 *
 * @param objectId The id of the object to watch (or -1 for any).
 * @param action   The action verb (e.g. "open", "chop").
 * @param handler  Called when the action is performed on a matching object.
 *
 * @example
 * ```ts
 * onObject(2213, "open", (player, chest) => {
 *   if (!player.quests.hasCompleted("dragon_awakens")) {
 *     player.message("The chest refuses to open.");
 *     return;
 *   }
 *   player.inventory.add("dragon_token", 1);
 * });
 * ```
 */
export type OnObject = (
  objectId: number,
  action: ObjectAction,
  handler: ObjectInteractionHandler,
) => void;

/** Global object interaction registry function exposed by the bridge. */
declare global {
  const onObject: OnObject;
}
