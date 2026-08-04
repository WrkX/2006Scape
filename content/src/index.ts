/**
 * SingleScape TypeScript SDK — barrel entry point.
 *
 * Import the public content kit from the SDK barrel:
 *
 * ```ts
 * import { createQuest, createBoss, dropTable, grantReward } from "./sdk/index.js";
 * ```
 *
 * Global functions (`defineBoss`, `onObject`, `onNpc`, `defineRaid`,
 * `defineArea`, `defineQuest`, `registerContentModule`, `dev`) are
 * available without import — they are provided by the Java-to-TypeScript
 * bridge at runtime. Executable `onNpc`, `onObject`, and `onCommand`
 * handlers receive a single `ScriptContext` containing narrow Java
 * wrappers; `Player` remains the richer declarative domain model and is
 * not the runtime handler object.
 *
 * This root barrel re-exports the public SDK and, for source
 * compatibility, the remaining type-only domain models (bots, dev
 * inspection, object helpers).
 *
 * @module index
 */

export * from "./sdk/index.js";

// Source-compatible legacy surface: the richer domain models (bots, dev
// inspection, object helpers, the aspirational rich `Player` domain
// types) remain importable from this root barrel but are not part of the
// public SDK surface in `sdk/index.ts`.
export type * from "./core/player.js";
export type * from "./core/object.js";
export type * from "./core/bot.js";
export type * from "./core/dev.js";
export type * from "./core/object-handlers.js";
export type * from "./core/types.js";
export type * from "./bots/types.js";
export type * from "./bosses/types.js";
export type * from "./raids/types.js";
