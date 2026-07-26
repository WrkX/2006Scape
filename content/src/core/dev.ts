/**
 * Development-mode inspection types.
 *
 * These are only available when the engine is running in dev mode.
 * They allow content authors to inspect the live world state.
 *
 * @module core/dev
 */

import type { WorldPoint, CardinalDirection } from "./types.js";

// ─── Object Inspection ────────────────────────────────────────────────────

/** A lightweight snapshot of a world object for dev inspection. */
export interface InspectableObject {
  /** The object's numeric id. */
  readonly id: number;

  /** World X coordinate. */
  readonly x: number;

  /** World Y coordinate. */
  readonly y: number;

  /** Plane the object is on. */
  readonly plane: number;

  /** Object type constant (matches client-side type byte). */
  readonly type: number;

  /** Orientation / rotation value (0-3). */
  readonly rotation: number;

  /** The region id (rx, ry pair packed into an int). */
  readonly region: number;
}

// ─── NPC Inspection ───────────────────────────────────────────────────────

/** A lightweight snapshot of an NPC for dev inspection. */
export interface InspectableNpc {
  /** The NPC's numeric definition id. */
  readonly id: number;

  /** World X coordinate. */
  readonly x: number;

  /** World Y coordinate. */
  readonly y: number;

  /** The NPC's combat level. */
  readonly combat: number;

  /** Radius (in tiles) the NPC can wander. */
  readonly spawnRadius: number;

  /** Direction the NPC currently faces. */
  readonly direction: CardinalDirection;

  /** Whether the NPC is currently in combat. */
  readonly inCombat: boolean;
}

// ─── Dev Console ──────────────────────────────────────────────────────────

/**
 * Dev-mode console API.
 *
 * Exposed as a global `dev` object when the engine is started with
 * `--dev` flag.  Content authors use this to query the world state and
 * manipulate entities for testing.
 */
export interface DevConsole {
  /** Get all objects at a specific world coordinate. */
  getObjectsAt(point: WorldPoint): readonly InspectableObject[];

  /** Get all objects of a specific id in a region. */
  getObjectsById(objectId: number): readonly InspectableObject[];

  /** Get all NPCs at a specific coordinate. */
  getNpcsAt(point: WorldPoint): readonly InspectableNpc[];

  /** Get all NPCs of a specific id. */
  getNpcsById(npcId: number): readonly InspectableNpc[];

  /** Get all NPCs currently in combat. */
  getNpcsInCombat(): readonly InspectableNpc[];

  /** Teleport the player to a given position. */
  teleport(playerName: string, point: WorldPoint): void;

  /** Spawn a temporary NPC at a location (does not persist). */
  spawnNpc(npcId: number, point: WorldPoint): void;

  /** Remove all temporary NPCs from the world. */
  clearTempNpcs(): void;

  /** Reload all TypeScript content modules. */
  reloadContent(): void;

  /** Whether dev mode is active. */
  readonly isDev: boolean;
}

declare global {
  /**
   * Dev console — only available when the engine is started with `--dev`.
   * Undefined in production builds.
   */
  const dev: DevConsole | undefined;
}
