/**
 * Runtime logging bridge types.
 *
 * The current Java bridge exposes logging in every script context. The
 * inspection snapshot types below are domain types only; they are not methods
 * on the runtime `dev` object until the Java host implements them.
 *
 * @module core/dev
 */

import type { CardinalDirection } from "./types.js";

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
 * Console API currently installed by the Java bridge.
 */
export interface DevConsole {
  /** Write a message through the server's script logger. */
  log(message: string): void;
}

declare global {
  /** Script logger installed in every bridge context. */
  const dev: DevConsole;

  /** Convenience alias for `dev.log`. */
  function log(message: string): void;
}
