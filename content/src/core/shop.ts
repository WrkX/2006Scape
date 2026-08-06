/**
 * Scripted shop definitions.
 *
 * {@link defineShop} registers a canonical schema-v1 scripted shop that is
 * parsed into an immutable Java-owned descriptor. Scripted shops are
 * separate from legacy numeric static shops and player shops: stock, prices,
 * and the restock policy are declared in the definition and owned by the
 * Java runtime. Opening routes are bound to exact area NPC allocations by
 * the area runtime (an area NPC spawn declares {@code openShop}).
 *
 * @module core/shop
 */

/**
 * One declared stock entry of a scripted shop.
 */
export interface ShopStockEntry {
  /**
   * Numeric item id (must be definition-backed) or an exact item name
   * resolved once at candidate load; ambiguous names reject the candidate.
   */
  readonly itemId: number | string;
  /** Declared start stock (1..100000). */
  readonly amount: number;
  /** Buy price in coins (1..100000000). */
  readonly price: number;
}

/**
 * Canonical schema-v1 scripted shop definition consumed by the shop
 * runtime.
 *
 * @example
 * ```ts
 * defineShop({
 *   id: "dragon_island_general",
 *   name: "Island Supplies",
 *   items: [
 *     { itemId: 379, amount: 10, price: 150 },
 *     { itemId: "tinderbox", amount: 10, price: 1 },
 *   ],
 *   buys: true,
 *   restockTicks: 250,
 * });
 * ```
 */
export interface ShopDefinition {
  /** Stable string id referenced by areas and the shop runtime. */
  readonly id: string;
  /** Display name shown in the shop interface. */
  readonly name: string;
  /** Declared stock in registration order (1..40 entries). */
  readonly items: readonly ShopStockEntry[];
  /**
   * Whether the shop buys back declared items at 85% of their price,
   * capped at the declared stock amounts.
   */
  readonly buys?: boolean;
  /** Restock interval in game cycles; one unit per item per interval. */
  readonly restockTicks?: number;
}

/**
 * Register a scripted shop definition with the engine.
 *
 * The definition is parsed into an immutable Java-owned descriptor; item
 * ids must be definition-backed (string names resolve exactly once at
 * candidate load). Duplicate shop ids reject the candidate.
 *
 * @param definition The canonical schema-v1 shop definition.
 */
export type DefineShop = (definition: ShopDefinition) => void;
