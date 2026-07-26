/**
 * Drop table builder — validated factory for {@link LootTable} definitions
 * with weighted entries, amount ranges, and auto-normalised weights.
 *
 * Drop tables are used by bosses, NPCs, chests, and raid reward chambers.
 * The `DropTableBuilder` provides a chainable API that validates entries and
 * auto-computes normalised probabilities.
 *
 * @module core/drop-tables
 *
 * @example Manual construction
 * ```ts
 * import { createLootTable } from "./drop-tables.js";
 *
 * const dragonLoot = createLootTable({
 *   id: "dragon_king_loot",
 *   drops: [
 *     { id: "dragon_bones", amount: 1, weight: 100 },
 *     { id: "dragon_platebody", amount: 1, weight: 1 },
 *     { id: "coins", amount: [10000, 50000], weight: 30 },
 *   ],
 *   rareDropMessage: "The dragon king drops a legendary item!",
 * });
 * ```
 *
 * @example Builder pattern
 * ```ts
 * import { dropTable } from "./drop-tables.js";
 *
 * const guardLoot = dropTable("guard_loot")
 *   .common("bones", 1)
 *   .uncommon("iron_med_helm", 1, 5)
 *   .rare("rune_med_helm", 1, 1)
 *   .always("bones", 1)  // guaranteed drop, independent of other rolls
 *   .rareMessage("A guard drops something valuable!")
 *   .build();
 * ```
 *
 * @example Using convenience weight presets
 * ```ts
 * import { DropTableBuilder } from "./drop-tables.js";
 *
 * const table = new DropTableBuilder("treasure_chest")
 *   .entry("coins", [50, 200], DropTableBuilder.COMMON)     // weight 128
 *   .entry("uncut_sapphire", 1, DropTableBuilder.UNCOMMON)   // weight 32
 *   .entry("uncut_diamond", 1, DropTableBuilder.RARE)        // weight 1
 *   .build();
 * ```
 */

import type { ItemId, LootEntry, LootTable } from "./types.js";

// ─── Internal helpers ─────────────────────────────────────────────────────────

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[drop-tables] ${message}`);
  }
}

function isValidItemId(id: ItemId): boolean {
  return (typeof id === "number" && id > 0) ||
    (typeof id === "string" && id.length > 0);
}

// ─── Drop table options ───────────────────────────────────────────────────────

/**
 * Options for creating a loot table manually.
 */
export interface LootTableOptions {
  readonly id: string;
  readonly drops: readonly LootEntry[];
  readonly rareDropMessage?: string;
}

/**
 * Create a validated {@link LootTable}.
 *
 * Validation rules:
 * - `id` must be a non-empty string.
 * - `drops` must have at least one entry.
 * - Every entry must have a valid item id, positive weight, and valid amount
 *   (positive number or `[min, max]` range where `min >= 1` and `max >= min`).
 * - Total weight must be greater than zero.
 *
 * @param options  Loot table configuration.
 * @returns A frozen {@link LootTable}.
 */
export function createLootTable(options: LootTableOptions): LootTable {
  assert(typeof options.id === "string" && options.id.length > 0,
    "LootTable id must be a non-empty string");
  assert(Array.isArray(options.drops) && options.drops.length > 0,
    `LootTable "${options.id}": drops must be a non-empty array`);

  let totalWeight = 0;
  for (let i = 0; i < options.drops.length; i++) {
    const entry = options.drops[i];
    const label = `LootTable "${options.id}" drops[${i}]`;

    assert(isValidItemId(entry.id),
      `${label}: id must be a positive number or non-empty string`);
    assert(typeof entry.weight === "number" && entry.weight > 0,
      `${label}: weight must be positive, got ${entry.weight}`);

    if (Array.isArray(entry.amount)) {
      assert(entry.amount.length === 2,
        `${label}: amount range must have exactly 2 elements`);
      assert(Number.isInteger(entry.amount[0]) && entry.amount[0] >= 1,
        `${label}: amount range min must be >= 1, got ${entry.amount[0]}`);
      assert(Number.isInteger(entry.amount[1]) && entry.amount[1] >= entry.amount[0],
        `${label}: amount range max must be >= min (${entry.amount[1]} vs ${entry.amount[0]})`);
    } else {
      assert(typeof entry.amount === "number" && Number.isInteger(entry.amount) && entry.amount >= 1,
        `${label}: amount must be a positive integer, got ${entry.amount}`);
    }

    totalWeight += entry.weight;
  }

  assert(totalWeight > 0,
    `LootTable "${options.id}": total weight must be > 0`);

  return Object.freeze({
    id: options.id,
    drops: options.drops,
    rareDropMessage: options.rareDropMessage,
  });
}

// ─── Convenience weight constants ─────────────────────────────────────────────

/**
 * Common weight presets for drop tables.
 *
 * These produce roughly the following probabilities in a weight-128 system:
 *
 * | Constant    | Weight | ~Probability |
 * |-------------|--------|--------------|
 * | `COMMON`    | 128    | ~50%         |
 * | `UNCOMMON`  | 32     | ~12.5%       |
 * | `RARE`      | 1      | ~0.4%        |
 * | `VERY_RARE` | 0.25   | ~0.1%        |
 */
export const DropWeights = {
  COMMON: 128,
  UNCOMMON: 32,
  RARE: 1,
  VERY_RARE: 0.25,
} as const;

/**
 * Builder for constructing a {@link LootTable} with a chainable API.
 *
 * Entries are added with convenience methods (`common`, `uncommon`, `rare`,
 * `veryRare`, `always`) or the generic `entry` method.
 *
 * "Always" entries are not subject to the weight-roll system; they are
 * guaranteed to drop alongside whatever the weight roll produces. The
 * engine interprets an `always` entry as having weight `Infinity`.
 *
 * @example
 * ```ts
 * const table = new DropTableBuilder("demon_loot")
 *   .always("ashes", 1)
 *   .common("coins", [500, 2000])
 *   .uncommon("rune_scimitar", 1)
 *   .rare("dragon_med_helm", 1)
 *   .veryRare("abyssal_whip", 1)
 *   .build();
 * ```
 */
export class DropTableBuilder {
  /** Weight preset: common (~50% in a weight-128 system). */
  static readonly COMMON = DropWeights.COMMON;
  /** Weight preset: uncommon (~12.5% in a weight-128 system). */
  static readonly UNCOMMON = DropWeights.UNCOMMON;
  /** Weight preset: rare (~0.4% in a weight-128 system). */
  static readonly RARE = DropWeights.RARE;
  /** Weight preset: very rare (~0.1% in a weight-128 system). */
  static readonly VERY_RARE = DropWeights.VERY_RARE;

  private _id: string;
  private _entries: LootEntry[] = [];
  private _rareMessage: string | undefined;
  private _seenIds: Set<string> = new Set();

  constructor(id: string) {
    assert(typeof id === "string" && id.length > 0,
      "DropTable id must be a non-empty string");
    this._id = id;
  }

  /**
   * Add a weight-based entry to the drop table.
   *
   * @param id      Item id (number or string constant).
   * @param amount  Fixed amount or `[min, max]` range.
   * @param weight  Drop weight (higher = more likely). Use the static
   *                weight constants for readable presets.
   * @returns This builder (chainable).
   */
  entry(id: ItemId, amount: number | [number, number], weight: number): this {
    assert(isValidItemId(id), `Drop entry: invalid item id "${String(id)}"`);
    assert(weight > 0 || weight === Infinity,
      `Drop entry "${String(id)}": weight must be positive, got ${weight}`);

    if (Array.isArray(amount)) {
      assert(amount.length === 2 &&
        Number.isInteger(amount[0]) && amount[0] >= 1 &&
        Number.isInteger(amount[1]) && amount[1] >= amount[0],
        `Drop entry "${String(id)}": invalid amount range`);
    } else {
      assert(typeof amount === "number" && Number.isInteger(amount) && amount >= 1,
        `Drop entry "${String(id)}": amount must be a positive integer`);
    }

    const key = String(id);
    assert(!this._seenIds.has(key),
      `Duplicate drop entry for item "${String(id)}"`);
    this._seenIds.add(key);

    this._entries.push({
      id,
      amount,
      weight,
    });

    return this;
  }

  /**
   * Add a common drop (weight 128, ~50% in a typical table).
   */
  common(id: ItemId, amount: number | [number, number]): this {
    return this.entry(id, amount, DropWeights.COMMON);
  }

  /**
   * Add an uncommon drop (weight 32, ~12.5% in a typical table).
   */
  uncommon(id: ItemId, amount: number | [number, number], weight?: number): this {
    return this.entry(id, amount, weight ?? DropWeights.UNCOMMON);
  }

  /**
   * Add a rare drop (weight 1, ~0.4% in a typical table).
   */
  rare(id: ItemId, amount: number | [number, number]): this {
    return this.entry(id, amount, DropWeights.RARE);
  }

  /**
   * Add a very rare drop (weight 0.25, ~0.1% in a typical table).
   */
  veryRare(id: ItemId, amount: number | [number, number]): this {
    return this.entry(id, amount, DropWeights.VERY_RARE);
  }

  /**
   * Add a guaranteed drop (always given, independent of the weight roll).
   *
   * Represented internally as `weight: Infinity` so the engine can recognise
   * guaranteed entries.  Use this for bones, ashes, keys, or tokens that
   * always drop.
   */
  always(id: ItemId, amount: number | [number, number]): this {
    return this.entry(id, amount, Infinity);
  }

  /**
   * Set a message broadcast to all players when a rare drop occurs.
   * The engine uses a weight threshold (default: <= 1) to determine rarity.
   */
  rareMessage(message: string): this {
    assert(typeof message === "string" && message.length > 0,
      "rareMessage must be a non-empty string");
    this._rareMessage = message;
    return this;
  }

  /**
   * Build the validated {@link LootTable}.
   */
  build(): LootTable {
    assert(this._entries.length > 0,
      `DropTable "${this._id}": at least one entry is required`);

    return createLootTable({
      id: this._id,
      drops: this._entries,
      rareDropMessage: this._rareMessage,
    });
  }
}

/**
 * Entry point for the fluent drop table builder.
 *
 * @param id  Unique loot table identifier.
 * @returns A new {@link DropTableBuilder}.
 *
 * @example
 * ```ts
 * const table = dropTable("goblin_drops")
 *   .always("bones", 1)
 *   .common("coins", [5, 25])
 *   .uncommon("bronze_spear", 1)
 *   .rare("goblin_mail", 1)
 *   .build();
 * ```
 */
export function dropTable(id: string): DropTableBuilder {
  return new DropTableBuilder(id);
}

// ─── Table merging utilities ──────────────────────────────────────────────────

/**
 * Merge multiple loot tables into one.
 *
 * All entries from all source tables are combined into a single table.
 * The `rareDropMessage` is taken from the first table that has one.
 *
 * @param id      Id for the merged table.
 * @param tables  Source tables to merge.
 * @returns A new frozen {@link LootTable}.
 */
export function mergeTables(
  id: string,
  tables: readonly LootTable[],
): LootTable {
  assert(typeof id === "string" && id.length > 0,
    "Merged table id must be a non-empty string");
  assert(tables.length > 0,
    "At least one source table is required to merge");

  const allEntries: LootEntry[] = [];
  for (const table of tables) {
    allEntries.push(...table.drops);
  }

  const rareMessage = tables.find(t => t.rareDropMessage !== undefined)
    ?.rareDropMessage;

  return createLootTable({
    id,
    drops: allEntries,
    rareDropMessage: rareMessage,
  });
}

/**
 * Compute the normalised probability for each entry in a table.
 *
 * Returns entries sorted by probability (highest first) with the
 * percentage chance of hitting each drop on a single roll.
 *
 * Entries with weight `Infinity` (guaranteed drops) get probability 1.
 *
 * @param table  The loot table to analyse.
 * @returns Array of `{ entry, probability }` objects.
 */
export function analyseTable(
  table: LootTable,
): readonly { entry: LootEntry; probability: number }[] {
  const guaranteed = table.drops.filter(e => e.weight === Infinity);
  const weighted = table.drops.filter(e => e.weight !== Infinity &&
    Number.isFinite(e.weight));

  const totalWeight = weighted.reduce((sum, e) => sum + e.weight, 0);

  const weightedResults = weighted.map(entry => ({
    entry,
    probability: totalWeight > 0 ? entry.weight / totalWeight : 0,
  }));

  const guaranteedResults = guaranteed.map(entry => ({
    entry,
    probability: 1,
  }));

  return [...guaranteedResults, ...weightedResults]
    .sort((a, b) => b.probability - a.probability);
}
