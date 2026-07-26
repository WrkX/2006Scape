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
 * // Register a handler for a specific object
 * registerObject({
 *   objectId: 2213,
 *   action: "open",
 *   handler: (player, obj) => {
 *     if (!player.quests.hasCompleted("dragon_awakens")) {
 *       player.message("The chest refuses to open.");
 *       return;
 *     }
 *     player.inventory.add("dragon_token", 1);
 *   },
 * });
 *
 * // Register a handler that fires on any object matching the condition
 * registerInteraction({
 *   objectId: -1,
 *   predicate: (obj) => obj.type === 10,
 *   action: "chop",
 *   handler: (player, obj) => {
 *     player.addExperience("woodcutting", 25);
 *     player.inventory.add("logs", 1);
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
 *   { objectId: 1, action: "chop", handler: (p) => p.message("You chop the tree.") },
 *   { objectId: 2, action: "mine", handler: (p) => p.message("You mine the rock.") },
 *   { objectId: 3, action: "open", handler: (p) => p.message("You open the door.") },
 * ]);
 * ```
 */

import type { Player } from "./player.js";
import type {
  NpcSpawn,
  NpcInteractionHandler,
  CardinalDirection,
} from "./types.js";
import type {
  ObjectAction,
  ObjectInteractionHandler,
  GameObject,
} from "./object.js";

// ─── Internal helpers ─────────────────────────────────────────────────────────

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[object-handlers] ${message}`);
  }
}

const VALID_ACTIONS: ReadonlySet<string> = new Set([
  "use", "open", "close", "climb", "pick", "chop", "mine",
  "bury", "pray", "search", "enter", "exit", "light", "extinguish", "operate",
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
   * The object id to watch.
   * Use -1 to match any object (must also provide `predicate`).
   */
  readonly objectId: number;

  /** The action verb (e.g. "open", "chop", "mine"). */
  readonly action: ObjectAction;

  /**
   * The handler invoked when the action is performed on a matching object.
   */
  readonly handler: ObjectInteractionHandler;

  /**
   * Optional predicate to filter objects when `objectId` is -1.
   * Called before the handler; only fires the handler when true.
   */
  readonly predicate?: (object: GameObject) => boolean;
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
 *   action: "open",
 *   handler: (player, chest) => {
 *     player.inventory.add("dragon_token", 1);
 *   },
 * });
 * ```
 */
export function registerObject(config: ObjectInteractionConfig): void {
  assert(Number.isInteger(config.objectId),
    `objectId must be an integer, got ${config.objectId}`);
  assert(VALID_ACTIONS.has(config.action),
    `Unknown action "${config.action}". Must be one of: ${[...VALID_ACTIONS].join(", ")}`);
  assert(typeof config.handler === "function",
    "handler must be a function");

  if (config.predicate) {
    assert(typeof config.predicate === "function",
      "predicate must be a function");
  }

  if (config.objectId === -1 && !config.predicate) {
    throw new Error(
      "[object-handlers] objectId is -1 (any object): a predicate function is required " +
      "to avoid firing on every interaction in the game.",
    );
  }

  if (config.predicate) {
    // Wrap the handler with the predicate check
    const wrappedHandler: ObjectInteractionHandler = (player, object) => {
      if (config.predicate!(object)) {
        config.handler(player, object);
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
 *   { objectId: 1, action: "chop", handler: treeHandler },
 *   { objectId: 2, action: "mine", handler: rockHandler },
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
 * registerInteraction({ objectId: 1, action: "chop", handler: myHandler });
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
