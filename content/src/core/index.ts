/**
 * Core type barrel — re-exports the public SDK plus shared domain types.
 *
 * For live Graal handlers, import {@link ScriptedPlayer} / {@link ScriptContext}
 * from `runtime` (via the SDK). {@link Player} and bot types below are
 * aspirational design sketches and are **not** wired to the Java bridge.
 *
 * @module core
 */

export * from "../sdk/index.js";

/** @deprecated Aspirational domain model — not injected into handlers. Prefer ScriptedPlayer. */
export type * from "./player.js";
export type * from "./object.js";
/** @deprecated Not wired to the Java host — profiles are design-only. */
export type * from "./bot.js";
export type * from "./dev.js";
export { MAX_ITEM_ID, MAX_NPC_ID, MAX_OBJECT_ID } from "./limits.js";
