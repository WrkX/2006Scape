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
