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
  BossArena,
  BossCleanupPolicy,
  BossContext,
  BossDefinition,
  BossEntryTeleport,
  BossObjectEntry,
  BossPhase,
  BossRuntimeContext,
  BossSpawn,
  BossSpecial,
  BossSpecials,
  DefineBoss,
} from "../core/boss.js";
