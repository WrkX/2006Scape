/**
 * Canonical drop-table builder tests.
 *
 * Proves the exact Java-parser bounds, deep freezing, duplicate
 * acceptance (same item with different weights is legitimate), and the
 * migration errors for the legacy `Infinity`/fractional weight forms.
 */

import { test, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import {
  createDropTable,
  dropTable,
  DropTableBuilder,
} from "../drop-tables.js";
import type { DropTableDefinition } from "../../core/runtime.js";

beforeEach(() => {
  (globalThis as Record<string, unknown>).defineDropTable = () => {};
});
afterEach(() => {
  delete (globalThis as Record<string, unknown>).defineDropTable;
});

function table(overrides: Partial<DropTableDefinition> = {}): DropTableDefinition {
  return {
    id: "test_loot",
    entries: [
      { itemId: 536, minAmount: 1, maxAmount: 1, weight: 0, always: true },
      { itemId: 995, minAmount: 100, maxAmount: 500, weight: 128, always: false },
    ],
    ...overrides,
  };
}

test("canonical table validates and deep-freezes", () => {
  const definition = createDropTable(table());
  assert.equal(definition.id, "test_loot");
  assert.equal(definition.entries.length, 2);
  assert.ok(Object.isFrozen(definition));
  assert.ok(Object.isFrozen(definition.entries));
  assert.ok(Object.isFrozen(definition.entries[0]));
  assert.throws(() => {
    (definition.entries as unknown as unknown[])[0] = {
      itemId: 1, minAmount: 1, maxAmount: 1, weight: 0, always: true,
    };
  }, TypeError);
  assert.throws(() => {
    (definition.entries[0] as { minAmount: number }).minAmount = 999;
  }, TypeError);
});

test("createDropTable registers the frozen definition", () => {
  const seen: DropTableDefinition[] = [];
  (globalThis as Record<string, unknown>).defineDropTable =
    (definition: DropTableDefinition) => {
      seen.push(definition);
    };
  const definition = createDropTable(table({ id: "registered_loot" }));
  assert.equal(seen.length, 1);
  assert.equal(seen[0], definition);
  assert.equal(seen[0].id, "registered_loot");
  assert.ok(Object.isFrozen(seen[0]));
});

test("rejects invalid entry counts and bounds", () => {
  assert.throws(() => createDropTable(table({ entries: [] })),
    /1\.\.64/);
  assert.throws(() => createDropTable(table({
    entries: Array.from({ length: 65 }, (_, index) => ({
      itemId: index + 1, minAmount: 1, maxAmount: 1, weight: 1, always: false,
    })),
  })), /1\.\.64/);
  assert.throws(() => createDropTable(table({
    entries: [{ itemId: 0, minAmount: 1, maxAmount: 1, weight: 1, always: false }],
  })), /item id/);
  assert.throws(() => createDropTable(table({
    entries: [{ itemId: 65536, minAmount: 1, maxAmount: 1, weight: 1, always: false }],
  })), /item id/);
  assert.throws(() => createDropTable(table({
    entries: [{ itemId: "bones", minAmount: 0, maxAmount: 1, weight: 1, always: false }],
  })), /minAmount/);
  assert.throws(() => createDropTable(table({
    entries: [{ itemId: "bones", minAmount: 1, maxAmount: 1000001, weight: 1, always: false }],
  })), /maxAmount/);
  assert.throws(() => createDropTable(table({
    entries: [{ itemId: "bones", minAmount: 5, maxAmount: 4, weight: 1, always: false }],
  })), /minAmount must not exceed/);
  assert.throws(() => createDropTable(table({
    entries: [{ itemId: "bones", minAmount: 1, maxAmount: 1, weight: 1.5, always: false }],
  })), /weight/);
  assert.throws(() => createDropTable(table({
    entries: [{ itemId: "bones", minAmount: 1, maxAmount: 1, weight: 1000001, always: false }],
  })), /weight/);
  assert.throws(() => createDropTable(table({
    entries: [{ itemId: "bones", minAmount: 1, maxAmount: 1, weight: 1, always: true }],
  })), /always/);
  assert.throws(() => createDropTable(table({
    entries: [{ itemId: "bones", minAmount: 1, maxAmount: 1, weight: 0, always: false }],
  })), /always/);
});

test("rejects weighted sums outside 1..1000000", () => {
  assert.throws(() => createDropTable(table({
    entries: [
      { itemId: 1, minAmount: 1, maxAmount: 1, weight: 1, always: false },
      { itemId: 2, minAmount: 1, maxAmount: 1, weight: 1000000, always: false },
    ],
  })), /weighted weight sum/);
  const allAlways = createDropTable(table({
    entries: [
      { itemId: 1, minAmount: 1, maxAmount: 1, weight: 0, always: true },
      { itemId: 2, minAmount: 1, maxAmount: 1, weight: 0, always: true },
    ],
  }));
  assert.equal(allAlways.entries.length, 2, "an all-always table is valid");
  const valid = createDropTable(table({
    entries: [
      { itemId: 1, minAmount: 1, maxAmount: 1, weight: 0, always: true },
      { itemId: 2, minAmount: 1, maxAmount: 1, weight: 1000000, always: false },
    ],
  }));
  assert.equal(valid.entries.length, 2);
});

test("rejects invalid ids", () => {
  assert.throws(() => createDropTable(table({ id: "" })), /id/);
  assert.throws(() => createDropTable(table({ id: "has space" })), /id/);
  assert.throws(() => createDropTable(table({ id: "-leading" })), /id/);
});

test("accepts duplicate item entries with different weights", () => {
  const definition = createDropTable(table({
    entries: [
      { itemId: 995, minAmount: 1, maxAmount: 1, weight: 64, always: false },
      { itemId: 995, minAmount: 1, maxAmount: 1, weight: 32, always: false },
    ],
  }));
  assert.equal(definition.entries.length, 2);
});

test("fluent builder emits canonical entries", () => {
  const built = dropTable("guard_loot")
    .always("bones", 1)
    .common("coins", [5, 25])
    .uncommon("bronze_spear", 1)
    .rare("goblin_mail", 1)
    .build();
  assert.equal(built.id, "guard_loot");
  assert.deepEqual(built.entries[0],
    { itemId: "bones", minAmount: 1, maxAmount: 1, weight: 0, always: true });
  assert.deepEqual(built.entries[1],
    { itemId: "coins", minAmount: 5, maxAmount: 25, weight: 128, always: false });
  assert.equal(built.entries[2].weight, 32);
  assert.equal(built.entries[3].weight, 1);
  assert.ok(Object.isFrozen(built));
  assert.ok(Object.isFrozen(built.entries[1]));
  assert.ok(Object.isFrozen(built.entries));

  const ranged = dropTable("ranged_loot")
    .always(995, [100, 200])
    .build();
  assert.deepEqual(ranged.entries[0],
    { itemId: 995, minAmount: 100, maxAmount: 200, weight: 0, always: true });
});

test("fluent builder rejects fractional and out-of-range weights", () => {
  assert.throws(() => new DropTableBuilder("x").entry("a", 1, 0.25).build(),
    /weight must be an integer/);
  assert.throws(() => new DropTableBuilder("x").entry("a", 1, 0).build(),
    /weight must be an integer/);
  assert.throws(() => new DropTableBuilder("x").entry("a", 1, 1000001).build(),
    /weight must be an integer/);
  assert.throws(() => new DropTableBuilder("x").entry("a", 0, 1).build(),
    /amount must be an integer/);
  assert.throws(() => new DropTableBuilder("x").entry("a", [5, 4], 1).build(),
    /max must be >= min/);
  assert.throws(() => new DropTableBuilder("x").entry("a", [1, 1000001], 1).build(),
    /1\.\.1000000/);
  assert.throws(() => new DropTableBuilder("x").build(), /at least one entry/);
});

test("legacy veryRare fails with a migration message", () => {
  assert.throws(
    () => dropTable("demon_loot").veryRare("abyssal_whip", 1).build(),
    /veryRare\(\) is not supported/,
  );
  assert.throws(
    () => dropTable("demon_loot").veryRare("abyssal_whip", 1),
    /veryRare\(\) is not supported/,
  );
});

test("legacy Infinity weights fail instead of being silently converted", () => {
  assert.throws(
    () => new DropTableBuilder("x").entry("ashes", 1, Infinity).build(),
    /weight must be an integer/,
  );
});

test("builder presets match the documented weights", () => {
  assert.equal(DropTableBuilder.COMMON, 128);
  assert.equal(DropTableBuilder.UNCOMMON, 32);
  assert.equal(DropTableBuilder.RARE, 1);
});
