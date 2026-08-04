/**
 * Canonical named drop tables.
 *
 * {@link createDropTable} validates and deep-freezes a schema-v1
 * {@link DropTableDefinition} against the exact bounds the Java
 * `DropTableDefinitionParser` enforces and registers it through
 * `defineDropTable`. The fluent {@link DropTableBuilder} emits the same
 * canonical output: integral amounts and weights only, `always` entries
 * with weight `0`, and no `Infinity` or fractional weights — the legacy
 * author forms fail with a migration message instead of being silently
 * converted.
 *
 * @module core/drop-tables
 *
 * @example
 * ```ts
 * createDropTable({
 *   id: "dragon_king_loot",
 *   entries: [
 *     { itemId: 536, minAmount: 1, maxAmount: 1, weight: 0, always: true },
 *     { itemId: 995, minAmount: 20000, maxAmount: 50000, weight: 128, always: false },
 *   ],
 * });
 * ```
 *
 * @example Fluent builder
 * ```ts
 * const table = dropTable("guard_loot")
 *   .always("bones", 1)
 *   .common("coins", [5, 25])
 *   .uncommon("bronze_spear", 1)
 *   .rare("goblin_mail", 1)
 *   .build();
 * defineDropTable(table);
 * ```
 */

import type { ItemId } from "./types.js";
import type { DropTableDefinition, DropTableEntry } from "./runtime.js";

const ID_PATTERN = /^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$/;

const MAX_ENTRIES = 64;
const MAX_AMOUNT = 1000000;
const MAX_WEIGHT = 1000000;
const MAX_ITEM_ID = 14999;

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[drop-tables] ${message}`);
  }
}

function integral(value: number, min: number, max: number): boolean {
  return Number.isSafeInteger(value) && value >= min && value <= max;
}

function validateEntry(entry: DropTableEntry, index: number): void {
  const label = `entries[${index}]`;
  const isNumeric = typeof entry.itemId === "number";
  const isName = typeof entry.itemId === "string" && entry.itemId.length > 0;
  assert(isNumeric || isName,
    `${label} must carry a numeric item id or a non-empty item name`);
  if (isNumeric) {
    assert(integral(entry.itemId, 1, MAX_ITEM_ID),
      `${label} item id must be an integer 1..${MAX_ITEM_ID} or an item name`);
  }
  assert(integral(entry.minAmount, 1, MAX_AMOUNT),
    `${label} minAmount must be an integer 1..${MAX_AMOUNT}, ` +
      `got ${entry.minAmount}`);
  assert(integral(entry.maxAmount, 1, MAX_AMOUNT),
    `${label} maxAmount must be an integer 1..${MAX_AMOUNT}, ` +
      `got ${entry.maxAmount}`);
  assert(entry.minAmount <= entry.maxAmount,
    `${label} minAmount must not exceed maxAmount`);
  assert(integral(entry.weight, 0, MAX_WEIGHT),
    `${label} weight must be an integer 0..${MAX_WEIGHT}, ` +
      `got ${entry.weight}`);
  assert(typeof entry.always === "boolean",
    `${label} always must be a boolean`);
  assert(entry.always === (entry.weight === 0),
    `${label} always requires weight 0 and non-always entries require a ` +
      "positive weight");
}

/**
 * Validate, deep-freeze, and register a canonical
 * {@link DropTableDefinition} through `defineDropTable`.
 *
 * Mirrors the Java parser bounds: `1..64` entries with exactly the
 * declared item id, `minAmount`/`maxAmount` (`1..1000000`,
 * `minAmount <= maxAmount`), integral `weight` (`0..1000000`), and
 * `always`; `always` requires weight `0`, an all-always table is valid,
 * and the weighted weight sum must stay `1..1000000`. String item ids
 * resolve once at candidate load by the engine; this factory validates
 * the shape only.
 *
 * @param definition  Raw drop table configuration.
 * @returns The frozen canonical {@link DropTableDefinition} that was
 *          registered.
 */
export function createDropTable(definition: DropTableDefinition): DropTableDefinition {
  assert(typeof definition.id === "string" && ID_PATTERN.test(definition.id),
    `invalid drop table id '${String(definition.id)}': expected at most 64 ` +
      "characters of letters, digits, '.', '_', or '-'");
  assert(definition.entries.length >= 1 && definition.entries.length <= MAX_ENTRIES,
    `Drop table '${definition.id}' must contain 1..${MAX_ENTRIES} entries`);

  let weightedSum = 0;
  const hasWeighted = definition.entries.some((entry) => !entry.always);
  const entries = definition.entries.map((entry, index) => {
    validateEntry(entry, index);
    if (!entry.always) {
      weightedSum += entry.weight;
    }
    return Object.freeze({ ...entry });
  });
  assert(weightedSum <= MAX_WEIGHT && (weightedSum > 0 || !hasWeighted),
    `Drop table '${definition.id}' weighted weight sum must be ` +
      `1..${MAX_WEIGHT}`);

  const frozen = Object.freeze({
    id: definition.id,
    entries: Object.freeze(entries),
  });
  defineDropTable(frozen);
  return frozen;
}

// ─── Fluent builder ──────────────────────────────────────────────────────────

/** Weight preset: common (~50% in a weight-128 table). */
export const COMMON_WEIGHT = 128;
/** Weight preset: uncommon (~12.5% in a weight-128 table). */
export const UNCOMMON_WEIGHT = 32;
/** Weight preset: rare (~0.4% in a weight-128 table). */
export const RARE_WEIGHT = 1;

/**
 * Builder for constructing a canonical {@link DropTableDefinition} with a
 * chainable API.
 *
 * Entries are added with convenience methods (`common`, `uncommon`,
 * `rare`, `always`) or the generic `entry` method. Weights are exact
 * integral amounts; the legacy `Infinity`/fractional forms are rejected
 * with a migration message because the canonical Java parser only accepts
 * integral weights.
 *
 * @example
 * ```ts
 * const table = dropTable("demon_loot")
 *   .always("ashes", 1)
 *   .common("coins", [500, 2000])
 *   .uncommon("rune_scimitar", 1)
 *   .rare("dragon_med_helm", 1)
 *   .build();
 * ```
 */
export class DropTableBuilder {
  /** Weight preset: common (~50% in a weight-128 system). */
  static readonly COMMON = COMMON_WEIGHT;
  /** Weight preset: uncommon (~12.5% in a weight-128 system). */
  static readonly UNCOMMON = UNCOMMON_WEIGHT;
  /** Weight preset: rare (~0.4% in a weight-128 system). */
  static readonly RARE = RARE_WEIGHT;

  private _id: string;
  private _entries: DropTableEntry[] = [];

  constructor(id: string) {
    assert(typeof id === "string" && ID_PATTERN.test(id),
      `invalid drop table id '${id}': expected at most 64 characters of ` +
        "letters, digits, '.', '_', or '-'");
    this._id = id;
  }

  private normalizeAmount(
    id: ItemId,
    amount: number | readonly [number, number],
  ): [number, number] {
    if (typeof amount === "number") {
      assert(integral(amount, 1, MAX_AMOUNT),
        `Drop entry "${String(id)}": amount must be an integer 1..` +
          `${MAX_AMOUNT}, got ${amount}`);
      return [amount, amount];
    }
    assert(amount.length === 2,
      `Drop entry "${String(id)}": amount range must have exactly 2 elements`);
    assert(integral(amount[0], 1, MAX_AMOUNT) && integral(amount[1], 1, MAX_AMOUNT),
      `Drop entry "${String(id)}": amount range bounds must be integers ` +
        `1..${MAX_AMOUNT}`);
    assert(amount[1] >= amount[0],
      `Drop entry "${String(id)}": amount range max must be >= min`);
    return [amount[0], amount[1]];
  }

  /**
   * Add a weighted entry to the drop table.
   *
   * @param id      Item id (number or item name).
   * @param amount  Fixed amount or `[min, max]` range.
   * @param weight  Integral drop weight `1..1000000`.
   * @returns This builder (chainable).
   */
  entry(id: ItemId, amount: number | readonly [number, number], weight: number): this {
    assert(integral(weight, 1, MAX_WEIGHT),
      `Drop entry "${String(id)}": weight must be an integer 1..` +
        `${MAX_WEIGHT}, got ${weight}`);
    const [minAmount, maxAmount] = this.normalizeAmount(id, amount);
    this._entries.push({
      itemId: id,
      minAmount,
      maxAmount,
      weight,
      always: false,
    });
    return this;
  }

  /**
   * Add a guaranteed drop (always given, independent of the weight roll).
   * Represented canonically as `always: true` with weight `0`.
   *
   * @param id      Item id (number or item name).
   * @param amount  Fixed amount or `[min, max]` range.
   * @returns This builder (chainable).
   */
  always(id: ItemId, amount: number | readonly [number, number]): this {
    const [minAmount, maxAmount] = this.normalizeAmount(id, amount);
    this._entries.push({
      itemId: id,
      minAmount,
      maxAmount,
      weight: 0,
      always: true,
    });
    return this;
  }

  /** Add a common drop (weight 128, ~50% in a typical table). */
  common(id: ItemId, amount: number | readonly [number, number]): this {
    return this.entry(id, amount, COMMON_WEIGHT);
  }

  /** Add an uncommon drop (weight 32, ~12.5% in a typical table). */
  uncommon(id: ItemId, amount: number | readonly [number, number], weight?: number): this {
    return this.entry(id, amount, weight ?? UNCOMMON_WEIGHT);
  }

  /** Add a rare drop (weight 1, ~0.4% in a typical table). */
  rare(id: ItemId, amount: number | readonly [number, number]): this {
    return this.entry(id, amount, RARE_WEIGHT);
  }

  /**
   * Legacy very-rare preset. The former `0.25` fractional weight has no
   * canonical representation; this method fails with a migration message
   * instead of silently changing the odds.
   */
  veryRare(_id: ItemId, _amount: number | readonly [number, number]): this {
    throw new Error(
      "[drop-tables] veryRare() is not supported: the legacy 0.25 " +
        "fractional weight has no canonical representation. Use " +
        ".rare(id, amount) or an explicit integral weight with .entry().",
    );
  }

  /**
   * Build the validated canonical {@link DropTableDefinition}.
   */
  build(): DropTableDefinition {
    assert(this._entries.length > 0,
      `DropTable "${this._id}": at least one entry is required`);
    return createDropTable({
      id: this._id,
      entries: this._entries,
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
