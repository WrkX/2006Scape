/**
 * Object interaction type definitions.
 *
 * Register handlers that fire when a player interacts with a world object
 * (door, chest, altar, tree, rock, etc.).
 *
 * @module core/object
 */

import type {
  ScriptContext,
  ScriptedObject,
} from "./runtime.js";

/** Object interaction slots dispatched by the engine. */
export type ObjectAction =
  | "first"
  | "second"
  | "third"
  | "fourth";

/** Context passed to an object interaction handler. */
export type ObjectScriptContext = ScriptContext<
  ScriptedObject,
  ObjectAction
>;

/**
 * Handler invoked when a player interacts with a world object.
 *
 * The handler receives the same single {@link ObjectScriptContext} object as
 * the Java bridge. Its `player` and `target` members are narrow runtime
 * wrappers, not the richer declarative domain models.
 */
export type ObjectInteractionHandler = (
  context: ObjectScriptContext,
) => void;

/**
 * Register an object interaction handler.
 *
 * @param objectId The exact id of the object to watch.
 * @param action   The client interaction slot (`first` through `fourth`).
 * @param handler  Called when that slot is used on a matching object.
 *
 * @example
 * ```ts
 * onObject(2213, "first", ({ player, target }) => {
 *   player.message(`You search ${target.getName()}.`);
 *   player.getInventory().add("dragon_token", 1);
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
