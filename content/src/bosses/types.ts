/**
 * Boss content type barrel.
 *
 * Re-exports boss system types for content authors working in the bosses/
 * directory.  Boss definitions created here are picked up automatically
 * by the engine's content loader.
 *
 * @module bosses/types
 */

export type {
  BossContext,
  BossDefinition,
  BossPhase,
  BossSpecial,
  BossSpecials,
  DefineBoss,
} from "../core/boss.js";
