/**
 * SingleScape public TypeScript content SDK.
 *
 * This is the documented author-facing barrel. It exports the stable
 * content-kit builders and helpers — quests, bosses, areas, raids, drop
 * tables, named rewards, scripted shops, requirements, equipment,
 * dialogue/cutscene sessions, gathering resources, and content-module
 * registration — plus the canonical definition and runtime types they
 * operate on.
 *
 * Every exported builder emits canonical schema-v1 values that the Java
 * parsers accept, validates exact bounds, and deep-freezes all
 * arrays/maps. No exported surface depends on engine internals; executable
 * callbacks receive the narrow runtime wrappers, never a rich domain
 * `Player`.
 *
 * ```ts
 * import { createQuest, registerModule, sayNpc } from "./sdk/index.js";
 * ```
 *
 * @module sdk
 */

// ─── Content-kit builders and helpers ────────────────────────────────────────

export * from "./skills.js";
export * from "./requirements.js";
export * from "./rewards.js";
export * from "./shops.js";
export * from "./equipment.js";
export * from "./magic.js";
export * from "./prayer.js";
export * from "./dialogue.js";
export * from "./drop-tables.js";
export * from "./gathering.js";
export * from "./processing.js";

export * from "../manifest.js";

// ─── Family builders (aligned with the proven consumers) ─────────────────────

export * from "../bosses/boss-builder.js";
export * from "../quests/quest-builder.js";
export * from "../areas/area-builder.js";
export * from "../raids/raid-builder.js";

// ─── Canonical definition and runtime types ──────────────────────────────────

export type {
  ItemId,
  ItemStack,
  WorldPoint,
  WorldRegion,
  SkillId,
  SkillStat,
  QuestState,
  QuestEntry,
  CardinalDirection,
  Result,
} from "../core/types.js";
export { MAX_ITEM_ID, MAX_NPC_ID, MAX_OBJECT_ID } from "../core/limits.js";
export type * from "../core/runtime.js";
export type {
  BossArena,
  BossCleanupPolicy,
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
export type * from "../core/raid.js";
export type * from "../core/shop.js";
export type * from "../quests/types.js";
export type * from "../areas/types.js";
