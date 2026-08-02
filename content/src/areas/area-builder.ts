/**
 * Area builder — validated factory for canonical {@link AreaDefinition}
 * values that lets content authors declare regions, NPC spawns, object
 * projections, drop bindings, shops, and lifecycle behavior in one place.
 *
 * The engine parses the built definition again into an immutable Java-owned
 * descriptor and activates it through the two-phase runtime activation
 * transaction. Nested definitions are not canonical: shops, drop tables,
 * quests, bosses, and raids must be registered separately and referenced by
 * id; the builder fails with an actionable source/path diagnostic instead of
 * silently ignoring a field.
 *
 * @module areas/area-builder
 */

import type { AreaDefinition, AreaNpcSpawn, AreaObject } from "./types.js";

// ─── Internal helpers ─────────────────────────────────────────────────────────

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[area-builder] ${message}`);
  }
}

function isIntegral(value: number, min: number, max: number): boolean {
  return Number.isFinite(value) && Number.isInteger(value) && value >= min
    && value <= max;
}

/**
 * Validated factory for a canonical {@link AreaDefinition}.
 *
 * Validation rules:
 * - `id` must be lower-case, hyphenated/snake ASCII; `name` non-empty.
 * - `bounds` must be canonical (min/max) or the legacy northWest/southEast
 *   shape (normalized); sides are bounded to 1..512 tiles.
 * - Every NPC spawn carries a unique lower-case key, a definition-backed
 *   `npcId`, valid coordinates, and a bounded respawn interval; a named
 *   `dropTable` requires a drop policy and private delivery requires
 *   `privateTicks`.
 * - Every object projection carries a unique key, a definition-backed
 *   `objectId`, valid coordinates, and unique per-action drop bindings.
 * - `onLoad`/`onUnload` are not canonical: use `registerContentModule`
 *   module hooks; the builder fails with a migration message.
 *
 * @param options  Area configuration.
 * @returns A frozen {@link AreaDefinition}.
 */
export function createArea(
  options: AreaDefinition & {
    readonly onLoad?: never;
    readonly onUnload?: never;
  },
): AreaDefinition {
  assert(typeof options.id === "string" && /^[a-z][a-z0-9_-]*$/.test(options.id),
    `Area id "${options.id}" must be lower-case ASCII (letters, digits, '_', '-')`);
  assert(typeof options.name === "string" && options.name.length > 0,
    `Area "${options.id}": name must be a non-empty string`);

  const bounds = normalizeBounds(options.id, options.bounds);

  assert(Array.isArray(options.npcs), `Area "${options.id}": npcs must be an array`);
  const npcList = options.npcs as readonly AreaNpcSpawn[];
  const npcKeys = new Set<string>();
  const npcs: readonly AreaNpcSpawn[] = npcList.map((npc, index) => {
    assert(typeof npc.key === "string" && /^[a-z][a-z0-9_-]{0,63}$/.test(npc.key),
      `Area "${options.id}": npcs[${index}] must carry a lower-case 'key' (1..64)`);
    assert(!npcKeys.has(npc.key),
      `Area "${options.id}": duplicate NPC spawn key "${npc.key}"`);
    npcKeys.add(npc.key);
    assert(isIntegral(npc.npcId, 0, 14999),
      `Area "${options.id}": npcs[${index}] must carry an integral 'npcId'`);
    assert(isIntegral(npc.x, 0, 16383) && isIntegral(npc.y, 0, 16383),
      `Area "${options.id}": npcs[${index}] (key=${npc.key}) position must be in range`);
    assert(isIntegral(npc.plane ?? 0, 0, 3),
      `Area "${options.id}": npcs[${index}] (key=${npc.key}) plane must be 0..3`);
    assert(isIntegral(npc.walkRadius ?? 0, 0, 64),
      `Area "${options.id}": npcs[${index}] (key=${npc.key}) walkRadius must be 0..64`);
    assert(npc.respawnTicks === undefined || isIntegral(npc.respawnTicks, 1, 100000),
      `Area "${options.id}": npcs[${index}] (key=${npc.key}) respawnTicks must be 1..100000`);
    if (npc.dropTable !== undefined) {
      assert(npc.dropPolicy === "private-to-killer" || npc.dropPolicy === "public",
        `Area "${options.id}": npcs[${index}] (key=${npc.key}) dropTable requires a 'dropPolicy' of 'private-to-killer' or 'public'`);
      if (npc.dropPolicy === "private-to-killer") {
        assert(isIntegral(npc.privateTicks ?? 0, 1, 1000),
          `Area "${options.id}": npcs[${index}] (key=${npc.key}) 'private-to-killer' delivery requires 'privateTicks' 1..1000`);
      } else {
        assert(npc.privateTicks === undefined,
          `Area "${options.id}": npcs[${index}] (key=${npc.key}) 'privateTicks' is not allowed for 'public' delivery`);
      }
    } else {
      assert(npc.dropPolicy === undefined,
        `Area "${options.id}": npcs[${index}] (key=${npc.key}) 'dropPolicy' requires a named 'dropTable'`);
      assert(npc.privateTicks === undefined,
        `Area "${options.id}": npcs[${index}] (key=${npc.key}) 'privateTicks' requires a named 'dropTable'`);
    }
    if (npc.openShop !== undefined) {
      assert(typeof npc.openShop === "string" && npc.openShop.length > 0,
        `Area "${options.id}": npcs[${index}] (key=${npc.key}) openShop must be a non-empty shop id`);
      assert(npc.walkRadius === undefined || npc.walkRadius === 0,
        `Area "${options.id}": npcs[${index}] (key=${npc.key}) a shop-opening spawn must not walk away from its exact allocation tile`);
    }
    return Object.freeze({ ...npc });
  });

  assert(Array.isArray(options.objects), `Area "${options.id}": objects must be an array`);
  const objectList = options.objects as readonly AreaObject[];
  const objectKeys = new Set<string>();
  const objectTiles = new Set<string>();
  const objects: readonly AreaObject[] = objectList.map((object, index) => {
    assert(typeof object.key === "string" && /^[a-z][a-z0-9_-]{0,63}$/.test(object.key),
      `Area "${options.id}": objects[${index}] must carry a lower-case 'key' (1..64)`);
    assert(!objectKeys.has(object.key),
      `Area "${options.id}": duplicate object key "${object.key}"`);
    objectKeys.add(object.key);
    assert(isIntegral(object.objectId, 0, 65535),
      `Area "${options.id}": objects[${index}] must carry an integral 'objectId'`);
    assert(isIntegral(object.x, 0, 16383) && isIntegral(object.y, 0, 16383),
      `Area "${options.id}": objects[${index}] (key=${object.key}) position must be in range`);
    assert(isIntegral(object.plane ?? 0, 0, 3),
      `Area "${options.id}": objects[${index}] (key=${object.key}) plane must be 0..3`);
    assert(isIntegral(object.type ?? 10, 0, 22),
      `Area "${options.id}": objects[${index}] (key=${object.key}) type must be 0..22`);
    assert(isIntegral(object.rotation ?? 0, 0, 3),
      `Area "${options.id}": objects[${index}] (key=${object.key}) rotation must be 0..3`);
    const tile = `${object.x},${object.y},${object.plane ?? 0}`;
    assert(!objectTiles.has(tile),
      `Area "${options.id}": duplicate object tile ${tile}`);
    objectTiles.add(tile);
    let drops: readonly import("./types.js").AreaObjectDrop[] | undefined;
    if (object.drops !== undefined) {
      assert(Array.isArray(object.drops) && object.drops.length <= 4,
        `Area "${options.id}": objects[${index}] (key=${object.key}) drops must be 0..4 entries`);
      const actions = new Set<string>();
      drops = object.drops.map((drop, dropIndex) => {
        assert(["first", "second", "third", "fourth"].includes(drop.action),
          `Area "${options.id}": objects[${index}] (key=${object.key}) drops[${dropIndex}] must use an ordinal action`);
        assert(!actions.has(drop.action),
          `Area "${options.id}": objects[${index}] (key=${object.key}) duplicate drop action "${drop.action}"`);
        actions.add(drop.action);
        assert(typeof drop.dropTable === "string" && drop.dropTable.length > 0,
          `Area "${options.id}": objects[${index}] (key=${object.key}) drops[${dropIndex}] requires a named 'dropTable'`);
        assert(drop.dropPolicy === "private-to-killer" || drop.dropPolicy === "public",
          `Area "${options.id}": objects[${index}] (key=${object.key}) drops[${dropIndex}] requires a 'dropPolicy'`);
        if (drop.dropPolicy === "private-to-killer") {
          assert(isIntegral(drop.privateTicks ?? 0, 1, 1000),
            `Area "${options.id}": objects[${index}] (key=${object.key}) drops[${dropIndex}] 'private-to-killer' requires 'privateTicks' 1..1000`);
        } else {
          assert(drop.privateTicks === undefined,
            `Area "${options.id}": objects[${index}] (key=${object.key}) drops[${dropIndex}] 'privateTicks' is not allowed for 'public' delivery`);
        }
        return Object.freeze({ ...drop });
      });
    }
    return Object.freeze({ ...object, drops });
  });

  for (const family of [
    ["shops", options.shops],
    ["quests", options.quests],
    ["bosses", options.bosses],
    ["raids", options.raids],
  ] as const) {
    const [name, values] = family;
    if (values === undefined) continue;
    assert(Array.isArray(values) && values.length <= 16,
      `Area "${options.id}": ${name} must be 0..16 references`);
    const seen = new Set<string>();
    values.forEach((reference, index) => {
      assert(typeof reference === "string" && reference.length > 0,
        `Area "${options.id}": ${name}[${index}] must be a non-empty definition id`);
      assert(!seen.has(reference),
        `Area "${options.id}": duplicate ${name} reference "${reference}"`);
      seen.add(reference);
    });
  }

  if (options.onEnter !== undefined) {
    assert(typeof options.onEnter === "function",
      `Area "${options.id}": onEnter must be a function when present`);
  }
  if (options.onLeave !== undefined) {
    assert(typeof options.onLeave === "function",
      `Area "${options.id}": onLeave must be a function when present`);
  }

  return Object.freeze({
    id: options.id,
    name: options.name,
    bounds: Object.freeze(bounds),
    npcs,
    objects,
    shops: options.shops === undefined ? [] : Object.freeze([...options.shops]),
    quests: options.quests === undefined ? [] : Object.freeze([...options.quests]),
    bosses: options.bosses === undefined ? [] : Object.freeze([...options.bosses]),
    raids: options.raids === undefined ? [] : Object.freeze([...options.raids]),
    onEnter: options.onEnter,
    onLeave: options.onLeave,
  });
}

/**
 * Compatibility transform of the legacy {@code northWest}/{@code southEast}
 * bounds shape into the canonical min/max bounds. Any other shape fails
 * with an actionable path diagnostic.
 */
function normalizeBounds(
  areaId: string,
  bounds: unknown,
): { minX: number; minY: number; maxX: number; maxY: number; plane: number } {
  if (bounds === undefined || bounds === null || typeof bounds !== "object") {
    throw new Error(
      `[area-builder] Area "${areaId}": bounds must be an object`,
    );
  }
  const raw = bounds as Record<string, unknown>;
  if (raw.minX !== undefined || raw.maxX !== undefined) {
    const candidate = bounds as { minX?: number; minY?: number; maxX?: number; maxY?: number; plane?: number };
    assert(isIntegral(candidate.minX ?? 0, 0, 16383)
        && isIntegral(candidate.minY ?? 0, 0, 16383)
        && isIntegral(candidate.maxX ?? 0, 0, 16383)
        && isIntegral(candidate.maxY ?? 0, 0, 16383)
        && isIntegral(candidate.plane ?? 0, 0, 3),
      `Area "${areaId}": bounds must be canonical {minX,minY,maxX,maxY,plane}`);
    const normalized = {
      minX: candidate.minX as number,
      minY: candidate.minY as number,
      maxX: candidate.maxX as number,
      maxY: candidate.maxY as number,
      plane: (candidate.plane ?? 0) as number,
    };
    assert(normalized.minX <= normalized.maxX && normalized.minY <= normalized.maxY,
      `Area "${areaId}": bounds are inverted`);
    assert(normalized.maxX - normalized.minX + 1 <= 512
        && normalized.maxY - normalized.minY + 1 <= 512,
      `Area "${areaId}": bounds sides must be 1..512 tiles`);
    return normalized;
  }
  const legacy = bounds as { northWest?: Record<string, unknown>; southEast?: Record<string, unknown> };
  assert(legacy.northWest !== undefined && legacy.southEast !== undefined,
    `Area "${areaId}": bounds must be canonical {minX,minY,maxX,maxY,plane} ` +
      "or legacy {northWest,southEast}");
  const nw = legacy.northWest as { x?: number; y?: number; plane?: number };
  const se = legacy.southEast as { x?: number; y?: number; plane?: number };
  assert(isIntegral(nw.x ?? 0, 0, 16383) && isIntegral(nw.y ?? 0, 0, 16383)
      && isIntegral(se.x ?? 0, 0, 16383) && isIntegral(se.y ?? 0, 0, 16383)
      && isIntegral(nw.plane ?? 0, 0, 3),
    `Area "${areaId}": legacy bounds points must be {x,y,plane}`);
  assert((nw.plane ?? 0) === (se.plane ?? 0),
    `Area "${areaId}": legacy bounds planes must match`);
  const normalized = {
    minX: Math.min(nw.x as number, se.x as number),
    minY: Math.min(nw.y as number, se.y as number),
    maxX: Math.max(nw.x as number, se.x as number),
    maxY: Math.max(nw.y as number, se.y as number),
    plane: (nw.plane ?? 0) as number,
  };
  assert(normalized.maxX - normalized.minX + 1 <= 512
      && normalized.maxY - normalized.minY + 1 <= 512,
    `Area "${areaId}": bounds sides must be 1..512 tiles`);
  return normalized;
}

/**
 * Validate an area definition and immediately register it via the global
 * `defineArea()` bridge function.
 *
 * Equivalent to calling `defineArea(createArea(options))`.
 *
 * @param options  Area configuration.
 */
export function registerArea(options: Parameters<typeof createArea>[0]): void {
  defineArea(createArea(options));
}
