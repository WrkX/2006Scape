/**
 * Scripted shop builder tests.
 *
 * Proves the exact `ShopDefinitionParser` bounds, deep freezing, and the
 * explicit scripted-vs-static shop reference routing.
 */

import { test } from "node:test";
import assert from "node:assert/strict";
import {
  createShop,
  registerShop,
  scriptedShop,
  staticShop,
  openShop,
} from "../shops.js";
import type { ShopDefinition } from "../../core/shop.js";

function shop(overrides: Partial<ShopDefinition> = {}): ShopDefinition {
  return {
    id: "test_shop",
    name: "Test Shop",
    items: [{ itemId: 379, amount: 10, price: 150 }],
    buys: true,
    restockTicks: 250,
    ...overrides,
  };
}

test("canonical shop validates and deep-freezes", () => {
  const definition = createShop(shop());
  assert.equal(definition.id, "test_shop");
  assert.ok(Object.isFrozen(definition));
  assert.ok(Object.isFrozen(definition.items));
  assert.ok(Object.isFrozen(definition.items[0]));
  assert.throws(() => {
    (definition.items[0] as { amount: number }).amount = 1;
  }, TypeError);
});

test("rejects invalid ids and names", () => {
  assert.throws(() => createShop(shop({ id: "" })), /shop id/);
  assert.throws(() => createShop(shop({ id: "has space" })), /shop id/);
  assert.throws(() => createShop(shop({ id: "a".repeat(65) })), /shop id/);
  assert.throws(() => createShop(shop({ name: "" })), /shop\.name/);
  assert.throws(() => createShop(shop({ name: "x".repeat(129) })), /shop\.name/);
});

test("rejects invalid stock entries", () => {
  assert.throws(() => createShop(shop({ items: [] })), /1\.\.40/);
  assert.throws(() => createShop(shop({
    items: Array.from({ length: 41 }, (_, index) =>
      ({ itemId: index + 1, amount: 1, price: 1 })),
  })), /1\.\.40/);
  assert.throws(() => createShop(shop({
    items: [{ itemId: 0, amount: 1, price: 1 }],
  })), /item id/);
  assert.throws(() => createShop(shop({
    items: [{ itemId: 15000, amount: 1, price: 1 }],
  })), /item id/);
  assert.throws(() => createShop(shop({
    items: [{ itemId: 379, amount: 0, price: 1 }],
  })), /amount/);
  assert.throws(() => createShop(shop({
    items: [{ itemId: 379, amount: 100001, price: 1 }],
  })), /amount/);
  assert.throws(() => createShop(shop({
    items: [{ itemId: 379, amount: 1, price: 0 }],
  })), /price/);
  assert.throws(() => createShop(shop({
    items: [{ itemId: 379, amount: 1, price: 100000001 }],
  })), /price/);
  assert.throws(() => createShop(shop({
    items: [{ itemId: 379, amount: 1, price: 1 },
      { itemId: 379, amount: 2, price: 2 }],
  })), /duplicate stock entry/);
  assert.throws(() => createShop(shop({ restockTicks: 0 })), /restockTicks/);
  assert.throws(() => createShop(shop({ restockTicks: 100001 })), /restockTicks/);
  assert.throws(() => createShop(shop({ buys: 1 as unknown as boolean })),
    /buys/);
});

test("registerShop forwards through defineShop", () => {
  const seen: ShopDefinition[] = [];
  (globalThis as Record<string, unknown>).defineShop = (definition: ShopDefinition) => {
    seen.push(definition);
  };
  registerShop(shop({ id: "registered_shop" }));
  assert.equal(seen.length, 1);
  assert.equal(seen[0].id, "registered_shop");
  assert.ok(Object.isFrozen(seen[0]));
  delete (globalThis as Record<string, unknown>).defineShop;
});

test("shop references distinguish scripted from static", () => {
  assert.deepEqual(scriptedShop("island_store"),
    { kind: "scripted", id: "island_store" });
  assert.deepEqual(staticShop(190), { kind: "static", number: 190 });
  assert.throws(() => scriptedShop(""), /shop id/);
  assert.throws(() => staticShop(-1), /non-negative/);
  assert.throws(() => staticShop(1.5), /non-negative/);
});

test("openShop routes through the exact capability", () => {
  const opened: string[] = [];
  const player = {
    getPresentation() {
      return {
        openScriptShop(id: string): boolean {
          opened.push(`scripted:${id}`);
          return true;
        },
        openStaticShop(number: number): boolean {
          opened.push(`static:${number}`);
          return true;
        },
      };
    },
  };
  assert.ok(openShop(player as never, scriptedShop("island_store")));
  assert.ok(openShop(player as never, staticShop(190)));
  assert.deepEqual(opened, ["scripted:island_store", "static:190"]);
});
