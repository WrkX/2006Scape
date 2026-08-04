/**
 * Scripted shop builders and opening helpers.
 *
 * {@link createShop} validates and deep-freezes a canonical schema-v1
 * {@link ShopDefinition} against the exact bounds the Java
 * `ShopDefinitionParser` enforces. Shop references are explicitly typed:
 * a scripted shop is opened by stable id (`openScriptShop`), a legacy
 * numeric static shop by its number (`openStaticShop`); the two are never
 * conflated.
 *
 * @module sdk/shops
 */

import type { ShopDefinition, ShopStockEntry } from "../core/shop.js";
import type { ScriptedPlayer } from "../core/runtime.js";

const ID_PATTERN = /^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$/;

const MAX_ITEMS = 40;
const MAX_AMOUNT = 100000;
const MAX_PRICE = 100000000;
const MAX_RESTOCK_TICKS = 100000;

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[sdk/shops] ${message}`);
  }
}

function integral(value: number, min: number, max: number): boolean {
  return Number.isSafeInteger(value) && value >= min && value <= max;
}

function utf8Length(value: string): number {
  return new TextEncoder().encode(value).length;
}

function validateItems(items: readonly ShopStockEntry[]): void {
  assert(items.length >= 1 && items.length <= MAX_ITEMS,
    `items must contain 1..${MAX_ITEMS} entries`);
  const seen = new Set<string>();
  for (const entry of items) {
    const isNumeric = typeof entry.itemId === "number";
    const isName = typeof entry.itemId === "string" && entry.itemId.length > 0;
    assert(isNumeric || isName,
      "items entry must carry a numeric item id or a non-empty item name");
    if (isNumeric) {
      assert(integral(entry.itemId, 1, 14999),
        "items entry item id must be an integer 1..14999 or an item name");
    }
    assert(integral(entry.amount, 1, MAX_AMOUNT),
      `items entry amount must be an integer 1..${MAX_AMOUNT}, ` +
        `got ${entry.amount}`);
    assert(integral(entry.price, 1, MAX_PRICE),
      `items entry price must be an integer 1..${MAX_PRICE}, ` +
        `got ${entry.price}`);
    const key = String(entry.itemId);
    assert(!seen.has(key), `duplicate stock entry '${key}'`);
    seen.add(key);
  }
}

/**
 * Create a validated, deeply frozen canonical {@link ShopDefinition}.
 *
 * Mirrors the Java parser bounds: id of at most 64 identifier characters,
 * a display name of at most 128 UTF-8 bytes, `1..40` stock entries with
 * integral amounts `1..100000` and prices `1..100000000`, and a restock
 * interval of `1..100000` game cycles.
 *
 * @param definition  Raw shop configuration.
 * @returns A frozen canonical {@link ShopDefinition}.
 */
export function createShop(definition: ShopDefinition): ShopDefinition {
  assert(typeof definition.id === "string" && ID_PATTERN.test(definition.id),
    `invalid shop id '${String(definition.id)}': expected at most 64 ` +
      "characters of letters, digits, '.', '_', or '-'");
  assert(typeof definition.name === "string"
      && utf8Length(definition.name) >= 1
      && utf8Length(definition.name) <= 128,
    `shop.name must be 1..128 UTF-8 bytes, got '${String(definition.name)}'`);
  validateItems(definition.items);
  if (definition.buys !== undefined) {
    assert(typeof definition.buys === "boolean",
      "shop.buys must be a boolean when present");
  }
  if (definition.restockTicks !== undefined) {
    assert(integral(definition.restockTicks, 1, MAX_RESTOCK_TICKS),
      `shop.restockTicks must be an integer 1..${MAX_RESTOCK_TICKS}, ` +
        `got ${definition.restockTicks}`);
  }

  return Object.freeze({
    id: definition.id,
    name: definition.name,
    items: Object.freeze(definition.items.map(
      (entry) => Object.freeze({ ...entry }))),
    buys: definition.buys,
    restockTicks: definition.restockTicks,
  });
}

/**
 * Validate a shop definition and immediately register it via the global
 * `defineShop()` bridge function.
 *
 * @param definition  Raw shop configuration.
 */
export function registerShop(definition: ShopDefinition): void {
  defineShop(createShop(definition));
}

/**
 * One typed shop reference: a Java-owned scripted shop by stable id, or a
 * legacy numeric static shop by its shop number. The runtime opening
 * routes are distinct (`openScriptShop` versus `openStaticShop`), so the
 * reference kind is never inferred from the value.
 */
export type ShopReference =
  | { readonly kind: "scripted"; readonly id: string }
  | { readonly kind: "static"; readonly number: number };

/** Reference a Java-owned scripted shop by its stable definition id. */
export function scriptedShop(id: string): ShopReference {
  assert(typeof id === "string" && ID_PATTERN.test(id),
    `invalid scripted shop id '${String(id)}'`);
  return Object.freeze({ kind: "scripted" as const, id });
}

/** Reference a legacy numeric static shop by its shop number. */
export function staticShop(number: number): ShopReference {
  assert(Number.isSafeInteger(number) && number >= 0,
    `static shop number must be a non-negative integer, got ${number}`);
  return Object.freeze({ kind: "static" as const, number });
}

/**
 * Open one typed shop reference through the player's presentation
 * capability.
 *
 * @param player  The live runtime player wrapper.
 * @param ref     A scripted or legacy static shop reference.
 * @returns The capability's success result.
 */
export function openShop(
  player: ScriptedPlayer,
  ref: ShopReference,
): boolean {
  return ref.kind === "scripted"
    ? player.getPresentation().openScriptShop(ref.id)
    : player.getPresentation().openStaticShop(ref.number);
}
