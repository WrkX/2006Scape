/**
 * SingleScape TypeScript SDK — barrel entry point.
 *
 * Import everything you need from `@singlescape/content`:
 *
 * ```ts
 * import type {
 *   Player,
 *   BossDefinition,
 *   QuestDefinition,
 *   SimulatedPlayer,
 *   // ...
 * } from "@singlescape/content";
 * ```
 *
 * Global functions (`defineBoss`, `onObject`, `defineRaid`, `defineArea`,
 * `defineQuest`, `dev`) are available without import — they are provided by
 * the Java-to-TypeScript bridge at runtime.
 *
 * @module index
 */

// Re-export all type-only content so that `import type { X }` works from the
// SDK root.  Importing this barrel also activates the `declare global` blocks
// in each module (global functions, dev console, etc.).

export type * from "./core/types.js";
export type * from "./core/player.js";
export type * from "./core/boss.js";
export type * from "./core/object.js";
export type * from "./core/raid.js";
export type * from "./core/bot.js";
export type * from "./core/dev.js";

export type * from "./areas/types.js";
export type * from "./bosses/types.js";
export type * from "./bots/types.js";
export type * from "./quests/types.js";
export type * from "./raids/types.js";
