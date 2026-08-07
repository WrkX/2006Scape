/**
 * World mob builder tests.
 */

import { test } from "node:test";
import assert from "node:assert/strict";
import { createMob, registerMob, type MobDefinition } from "../mob.js";

function options(overrides: Partial<MobDefinition> = {}): MobDefinition {
  return {
    id: "goblin",
    npcId: 100,
    name: "Goblin",
    aggression: 0,
    combatStyle: "melee",
    attackSpeed: 4,
    maxHit: 1,
    ...overrides,
  };
}

test("createMob deep-freezes the canonical shape", () => {
  const mob = createMob(options({ animation: 165 }));
  assert.ok(Object.isFrozen(mob));
  assert.equal(mob.id, "goblin");
  assert.equal(mob.npcId, 100);
  assert.equal(mob.aggression, 0);
  assert.equal(mob.combatStyle, "melee");
  assert.equal(mob.attackSpeed, 4);
  assert.equal(mob.maxHit, 1);
  assert.equal(mob.animation, 165);
  assert.throws(() => {
    (mob as { maxHit: number }).maxHit = 9;
  }, TypeError);
});

test("createMob rejects invalid ids, styles, and bounds", () => {
  assert.throws(() => createMob(options({ id: "" })), /invalid mob id/);
  assert.throws(() => createMob(options({ npcId: -1 })), /npcId/);
  assert.throws(() => createMob(options({ aggression: 65 })), /aggression/);
  assert.throws(() => createMob(options({
    combatStyle: "fire" as "melee",
  })), /combatStyle/);
  assert.throws(() => createMob(options({ attackSpeed: 0 })), /attackSpeed/);
  assert.throws(() => createMob(options({ maxHit: -1 })), /maxHit/);
  assert.throws(() => createMob(options({
    onTick: "nope" as unknown as () => void,
  })), /onTick must be a function/);
});

test("registerMob forwards a frozen definition", () => {
  const seen: MobDefinition[] = [];
  (globalThis as { defineMob?: unknown }).defineMob =
    (definition: MobDefinition) => {
      seen.push(definition);
    };
  try {
    registerMob(options({ aggression: 5 }));
    assert.equal(seen.length, 1);
    assert.equal(seen[0].aggression, 5);
    assert.ok(Object.isFrozen(seen[0]));
  } finally {
    delete (globalThis as { defineMob?: unknown }).defineMob;
  }
});
