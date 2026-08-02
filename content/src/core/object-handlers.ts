/**
 * Object and NPC interaction registration helpers.
 *
 * Provides validated wrappers around the global `onObject()` bridge as well
 * as an `onNpc()` helper that creates an {@link NpcSpawn} with an interaction
 * handler wired in.
 *
 * @module core/object-handlers
 *
 * @example Object interaction
 * ```ts
 * import { registerInteraction, registerObject } from "./object-handlers.js";
 *
 * // Register a handler for a specific object's first interaction slot
 * registerObject({
 *   objectId: 2213,
 *   action: "first",
 *   handler: ({ player, target }) => {
 *     player.message(`You search ${target.getName()}.`);
 *     player.getInventory().add("dragon_token", 1);
 *   },
 * });
 *
 * // Optionally filter matching objects by runtime metadata
 * registerInteraction({
 *   objectId: 1276,
 *   predicate: (object) => object.getPlane() === 0,
 *   action: "first",
 *   handler: ({ player }) => {
 *     player.getInventory().add("logs", 1);
 *   },
 * });
 * ```
 *
 * @example NPC interaction
 * ```ts
 * import { createNpcInteraction } from "./object-handlers.js";
 *
 * // Build an NPC spawn with an interaction handler
 * const elderWizard = createNpcInteraction({
 *   id: 300,
 *   x: 3050,
 *   y: 4950,
 *   onInteract: (player, npc) => {
 *     player.openDialogue({
 *       type: "npc",
 *       title: "Elder Wizard",
 *       lines: ["Greetings, adventurer. I have a task for you."],
 *       options: [{
 *         text: "Tell me more.",
 *         handler: (p) => {
 *           p.message("The elder explains the quest...");
 *           p.quests.get("dragon_awakens");
 *         },
 *       }],
 *     });
 *   },
 * });
 * ```
 *
 * @example Batch registration
 * ```ts
 * import { registerObjects } from "./object-handlers.js";
 *
 * // Register many interactions at once
 * registerObjects([
 *   { objectId: 1, action: "first", handler: ({ player }) => player.message("You chop the tree.") },
 *   { objectId: 2, action: "second", handler: ({ player }) => player.message("You prospect the rock.") },
 *   { objectId: 3, action: "third", handler: ({ player }) => player.message("You inspect the door.") },
 * ]);
 * ```
 */

import type {
  NpcSpawn,
  NpcInteractionHandler,
  CardinalDirection,
} from "./types.js";
import type {
  ObjectAction,
  ObjectInteractionHandler,
} from "./object.js";
import type { ScriptedObject } from "./runtime.js";

// ─── Internal helpers ─────────────────────────────────────────────────────────

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[object-handlers] ${message}`);
  }
}

const VALID_ACTIONS: ReadonlySet<string> = new Set([
  "first", "second", "third", "fourth",
]);

const VALID_DIRECTIONS: ReadonlySet<string> = new Set([
  "north", "south", "east", "west",
]);

// ─── Object interaction ───────────────────────────────────────────────────────

/**
 * Configuration for a single object interaction registration.
 */
export interface ObjectInteractionConfig {
  /**
   * The exact object id to watch.
   */
  readonly objectId: number;

  /** The client interaction slot (`first` through `fourth`). */
  readonly action: ObjectAction;

  /**
   * The handler invoked when the action is performed on a matching object.
   */
  readonly handler: ObjectInteractionHandler;

  /**
   * Optional predicate to further filter matching objects.
   * Called before the handler; only fires the handler when true.
   */
  readonly predicate?: (object: ScriptedObject) => boolean;
}

/**
 * Register a single validated object interaction via the global `onObject()`.
 *
 * @param config  Interaction configuration.
 *
 * @example
 * ```ts
 * registerObject({
 *   objectId: 2213,
 *   action: "first",
 *   handler: ({ player }) => {
 *     player.getInventory().add("dragon_token", 1);
 *   },
 * });
 * ```
 */
export function registerObject(config: ObjectInteractionConfig): void {
  assert(Number.isInteger(config.objectId) && config.objectId >= 0,
    `objectId must be a non-negative integer, got ${config.objectId}`);
  assert(VALID_ACTIONS.has(config.action),
    `Unknown action "${config.action}". Must be one of: ${[...VALID_ACTIONS].join(", ")}`);
  assert(typeof config.handler === "function",
    "handler must be a function");

  if (config.predicate) {
    assert(typeof config.predicate === "function",
      "predicate must be a function");
  }

  if (config.predicate) {
    // Wrap the handler with the predicate check
    const wrappedHandler: ObjectInteractionHandler = (context) => {
      if (config.predicate!(context.target)) {
        config.handler(context);
      }
    };
    onObject(config.objectId, config.action, wrappedHandler);
  } else {
    onObject(config.objectId, config.action, config.handler);
  }
}

/**
 * Register multiple object interactions at once.
 *
 * @param configs  Array of interaction configurations.
 *
 * @example
 * ```ts
 * registerObjects([
 *   { objectId: 1, action: "first", handler: treeHandler },
 *   { objectId: 2, action: "second", handler: rockHandler },
 * ]);
 * ```
 */
export function registerObjects(configs: readonly ObjectInteractionConfig[]): void {
  for (const config of configs) {
    registerObject(config);
  }
}

// ─── Simplified shorthand ─────────────────────────────────────────────────────

/**
 * Lightweight object interaction config for simple cases (no predicate).
 */
export interface SimpleObjectInteraction {
  readonly objectId: number;
  readonly action: ObjectAction;
  readonly handler: ObjectInteractionHandler;
}

/**
 * Register a single object interaction directly.
 *
 * A more concise alias for `registerObject` without the predicate option.
 *
 * @deprecated Prefer {@link registerObject} for full validation; this
 * shorthand exists for concise content scripts.
 *
 * @example
 * ```ts
 * registerInteraction({ objectId: 1, action: "first", handler: myHandler });
 * ```
 */
export function registerInteraction(config: SimpleObjectInteraction): void {
  registerObject(config);
}

// ─── NPC interaction ──────────────────────────────────────────────────────────

/**
 * Configuration for creating an NPC spawn with an interaction handler.
 */
export interface NpcInteractionConfig {
  readonly id: number;
  readonly x: number;
  readonly y: number;
  readonly plane?: number;
  readonly walkRadius?: number;
  readonly direction?: CardinalDirection;
  readonly onInteract: NpcInteractionHandler;
}

/**
 * Create a validated {@link NpcSpawn} with an interaction handler.
 *
 * The returned spawn is suitable for direct inclusion in an
 * {@link import("../areas/types.js").AreaDefinition} `npcs` array.
 *
 * @param config  NPC spawn and interaction configuration.
 * @returns A frozen {@link NpcSpawn}.
 *
 * @example
 * ```ts
 * const guide = createNpcInteraction({
 *   id: 300,
 *   x: 2500,
 *   y: 3500,
 *   direction: "south",
 *   walkRadius: 2,
 *   onInteract: (player, npc) => {
 *     player.message(`Hello ${player.username}, welcome to the tutorial!`);
 *   },
 * });
 * ```
 */
export function createNpcInteraction(
  config: NpcInteractionConfig,
): NpcSpawn {
  assert(Number.isInteger(config.id) && config.id > 0,
    `NPC id must be a positive integer, got ${config.id}`);
  assert(Number.isFinite(config.x) && Number.isFinite(config.y),
    `NPC (id=${config.id}): position must be finite`);
  assert(typeof config.onInteract === "function",
    `NPC (id=${config.id}): onInteract must be a function`);

  if (config.plane !== undefined) {
    assert(Number.isInteger(config.plane),
      `NPC (id=${config.id}): plane must be an integer, got ${config.plane}`);
  }

  if (config.walkRadius !== undefined) {
    assert(Number.isInteger(config.walkRadius) && config.walkRadius >= 0,
      `NPC (id=${config.id}): walkRadius must be >= 0, got ${config.walkRadius}`);
  }

  if (config.direction !== undefined) {
    assert(VALID_DIRECTIONS.has(config.direction),
      `NPC (id=${config.id}): unknown direction "${config.direction}". ` +
      `Must be one of: ${[...VALID_DIRECTIONS].join(", ")}`);
  }

  return Object.freeze({
    id: config.id,
    x: config.x,
    y: config.y,
    plane: config.plane,
    walkRadius: config.walkRadius,
    direction: config.direction,
    onInteract: config.onInteract,
  });
}

/**
 * Create a simple NPC spawn without an interaction handler.
 *
 * Useful for ambient NPCs that don't need custom dialogue.
 *
 * @param id          NPC definition id.
 * @param x           World X coordinate.
 * @param y           World Y coordinate.
 * @param direction   Optional facing direction.
 * @param walkRadius  Optional wander radius.
 * @returns A frozen {@link NpcSpawn} without `onInteract`.
 */
export function createNpcSpawn(
  id: number,
  x: number,
  y: number,
  direction?: CardinalDirection,
  walkRadius?: number,
): NpcSpawn {
  assert(Number.isInteger(id) && id > 0,
    `NPC id must be positive, got ${id}`);
  assert(Number.isFinite(x) && Number.isFinite(y),
    `NPC (id=${id}): position must be finite`);

  if (direction !== undefined) {
    assert(VALID_DIRECTIONS.has(direction),
      `NPC (id=${id}): unknown direction "${direction}"`);
  }

  return Object.freeze({
    id,
    x,
    y,
    direction,
    walkRadius,
  });
}
