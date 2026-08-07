/**
 * Canonical entity-id ceilings for TypeScript content authors.
 *
 * These must stay aligned with Java `ScriptEntityLimits` /
 * `ItemConstants.ITEM_LIMIT`. Handlers and builders validate against these
 * bounds; the cache you ship still decides which ids actually exist.
 *
 * @module core/limits
 */

/** Inclusive maximum item id (array size is this + 1). */
export const MAX_ITEM_ID = 65535;

/** Inclusive maximum NPC type id. */
export const MAX_NPC_ID = 65535;

/** Inclusive maximum object type id. */
export const MAX_OBJECT_ID = 65535;

/**
 * Custom asset namespace starts (aligned with `@singlescape/tools` /
 * asset-pipeline phase 7). Cache pack entries must exist before overlays
 * reference these ids.
 */
export const CUSTOM_MODEL_START = 50000;
export const CUSTOM_OBJECT_START = 35000;
export const CUSTOM_NPC_START = 35000;
export const CUSTOM_ITEM_START = 35000;
export const MAX_CUSTOM_ID = 65535;
