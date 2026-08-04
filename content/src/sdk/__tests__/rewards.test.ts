/**
 * Named reward builder tests.
 *
 * Proves the exact `RewardDefinitionParser` bounds (items, experience,
 * quest points, state mutations), deep freezing, duplicate rejection, and
 * the grant wrapper's result forwarding.
 */

import { test } from "node:test";
import assert from "node:assert/strict";
import {
  createReward,
  registerReward,
  grantReward,
  isRewarded,
} from "../rewards.js";
import type { RewardDefinition } from "../../core/runtime.js";

function reward(overrides: Partial<RewardDefinition> = {}): RewardDefinition {
  return {
    id: "test_reward",
    items: [{ id: 995, amount: 1000 }],
    experience: [{ skill: "magic", amount: 1000 }],
    questPoints: 3,
    state: [{ namespace: "test", key: "done", value: true }],
    ...overrides,
  };
}

test("canonical reward validates and deep-freezes", () => {
  const definition = createReward(reward());
  assert.equal(definition.id, "test_reward");
  assert.ok(Object.isFrozen(definition));
  assert.ok(Object.isFrozen(definition.items));
  assert.ok(Object.isFrozen(definition.items[0]));
  assert.ok(Object.isFrozen(definition.experience));
  assert.ok(Object.isFrozen(definition.experience[0]));
  assert.ok(Object.isFrozen(definition.state));
  assert.ok(Object.isFrozen(definition.state[0]));
  assert.throws(() => {
    (definition.items[0] as { amount: number }).amount = 1;
  }, TypeError);
});

test("rejects invalid ids and member bounds", () => {
  assert.throws(() => createReward(reward({ id: "" })), /reward id/);
  assert.throws(() => createReward(reward({ id: "has space" })), /reward id/);
  assert.throws(() => createReward(reward({ id: "a".repeat(65) })), /reward id/);
  assert.throws(() => createReward(reward({
    items: Array.from({ length: 29 }, (_, index) =>
      ({ id: index + 1, amount: 1 })),
  })), /at most 28/);
  assert.throws(() => createReward(reward({
    items: [{ id: 995, amount: 0 }],
  })), /1\.\.2147483647/);
  assert.throws(() => createReward(reward({
    items: [{ id: 995, amount: 2147483648 }],
  })), /1\.\.2147483647/);
  assert.throws(() => createReward(reward({
    items: [{ id: -1, amount: 1 }],
  })), /item id/);
  assert.throws(() => createReward(reward({
    items: [{ id: 15000, amount: 1 }],
  })), /item id/);
});

test("rejects duplicate item grants and experience skills", () => {
  assert.throws(() => createReward(reward({
    items: [{ id: 995, amount: 1 }, { id: 995, amount: 2 }],
  })), /duplicate item grant/);
  assert.throws(() => createReward(reward({
    items: [{ id: "coins", amount: 1 }, { id: "coins", amount: 2 }],
  })), /duplicate item grant/);
  assert.throws(() => createReward(reward({
    experience: [
      { skill: "magic", amount: 10 },
      { skill: "magic", amount: 20 },
    ],
  })), /duplicate experience skill/);
  assert.throws(() => createReward(reward({
    experience: [{ skill: "unknown", amount: 10 }],
  })), /unknown skill/);
});

test("rejects out-of-range experience and quest points", () => {
  assert.throws(() => createReward(reward({
    experience: [{ skill: "magic", amount: 200000001 }],
  })), /1\.\.200000000/);
  assert.throws(() => createReward(reward({
    experience: [{ skill: "magic", amount: 0 }],
  })), /1\.\.200000000/);
  assert.throws(() => createReward(reward({
    experience: Array.from({ length: 22 }, (_, index) =>
      ({ skill: "attack", amount: 1 })).map((entry, index) =>
      ({ ...entry, skill: entry.skill + (index === 0 ? "" : index) })),
  })), /at most 21/);
  assert.throws(() => createReward(reward({ questPoints: 10001 })), /questPoints/);
  assert.throws(() => createReward(reward({ questPoints: -10001 })), /questPoints/);
  assert.throws(() => createReward(reward({ questPoints: 1.5 })), /questPoints/);
  const negative = createReward(reward({ questPoints: -5 }));
  assert.equal(negative.questPoints, -5);
});

test("rejects invalid state mutations", () => {
  assert.throws(() => createReward(reward({
    state: [{ namespace: "Bad.Name", key: "k", value: true }],
  })), /state namespace/);
  assert.throws(() => createReward(reward({
    state: [{ namespace: "n", key: "k".repeat(97), value: true }],
  })), /state key/);
  assert.throws(() => createReward(reward({
    state: [{ namespace: "n", key: "k", value: "x".repeat(4097) }],
  })), /4096/);
  assert.throws(() => createReward(reward({
    state: [{ namespace: "n", key: "k", value: Number.NaN }],
  })), /finite/);
  assert.throws(() => createReward(reward({
    state: [{ namespace: "n", key: "k", value: {} as boolean }],
  })), /state value/);
  assert.throws(() => createReward(reward({
    state: [{ namespace: "sys.internal", key: "k", value: true }],
  })), /reserved for engine state/);
  assert.throws(() => createReward(reward({
    state: [
      { namespace: "n", key: "k", value: true },
      { namespace: "n", key: "k", value: false },
    ],
  })), /duplicate state mutation/);
  assert.throws(() => createReward(reward({
    state: Array.from({ length: 33 }, (_, index) =>
      ({ namespace: "n", key: `k${index}`, value: true })),
  })), /at most 32/);
});

test("registerReward forwards through defineReward", () => {
  const seen: RewardDefinition[] = [];
  (globalThis as Record<string, unknown>).defineReward = (definition: RewardDefinition) => {
    seen.push(definition);
  };
  registerReward(reward({ id: "registered_reward" }));
  assert.equal(seen.length, 1);
  assert.equal(seen[0].id, "registered_reward");
  assert.ok(Object.isFrozen(seen[0]));
  delete (globalThis as Record<string, unknown>).defineReward;
});

test("grantReward forwards the narrow result code", () => {
  const player = {
    grantReward(rewardId: string) {
      return {
        rewardId: () => rewardId,
        code: () => rewardId === "missing" ? "not_found" : "rewarded",
      };
    },
  };
  assert.equal(grantReward(player as never, "missing"), "not_found");
  assert.equal(grantReward(player as never, "present"), "rewarded");
  assert.ok(isRewarded("rewarded"));
  assert.ok(!isRewarded("inventory_full"));
});
