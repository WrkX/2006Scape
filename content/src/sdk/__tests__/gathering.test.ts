/**
 * Gathering resource builder tests.
 *
 * Proves {@link createGatheringResource} emits deeply frozen canonical
 * schema-v1 values matching the Java parser bounds, rejects invalid bounds,
 * duplicate tools, unknown skills, and impossible success chances, and that
 * a frozen resource cannot be mutated after creation.
 */

import { test } from "node:test";
import assert from "node:assert/strict";
import {
  createGatheringResource,
  registerGatheringResource,
  type GatheringResourceDefinition,
} from "../gathering.js";

function resourceOptions(): GatheringResourceDefinition {
  return {
    id: "tree",
    name: "Tree",
    objectId: 1276,
    action: "first",
    skill: "woodcutting",
    level: 1,
    tools: [{ itemId: 1351 }],
    animation: 879,
    intervalTicks: 4,
    successChance: { numerator: 3, denominator: 4 },
    rewards: [{ itemId: 1511, amount: 1 }],
    experience: 25,
    depletedObjectId: 1341,
    respawnTicks: 4,
  };
}

test("gathering builder emits a canonical deep-frozen resource", () => {
  const resource = createGatheringResource(resourceOptions());
  assert.ok(Object.isFrozen(resource));
  assert.ok(Object.isFrozen(resource.tools));
  assert.ok(Object.isFrozen(resource.tools[0]));
  assert.ok(Object.isFrozen(resource.successChance));
  assert.ok(Object.isFrozen(resource.rewards));
  assert.ok(Object.isFrozen(resource.rewards[0]));
  assert.equal(resource.id, "tree");
  assert.equal(resource.objectId, 1276);
  assert.equal(resource.action, "first");
  assert.equal(resource.skill, "woodcutting");
  assert.equal(resource.level, 1);
  assert.equal(resource.experience, 25);
  assert.throws(() => {
    (resource as { level: number }).level = 2;
  }, TypeError);
  assert.throws(() => {
    (resource.tools[0] as { consume: boolean }).consume = true;
  }, TypeError);
});

test("gathering builder rejects invalid ids, bounds, and shapes", () => {
  assert.throws(() => createGatheringResource({
    ...resourceOptions(),
    id: "bad id!",
  }), /invalid resource id/);
  assert.throws(() => createGatheringResource({
    ...resourceOptions(),
    name: "",
  }), /name must be 1\.\.128 UTF-8 bytes/);
  assert.throws(() => createGatheringResource({
    ...resourceOptions(),
    objectId: -1,
  }), /objectId must be an integer/);
  assert.throws(() => createGatheringResource({
    ...resourceOptions(),
    action: "fifth" as "first",
  }), /action must be one of/);
  assert.throws(() => createGatheringResource({
    ...resourceOptions(),
    skill: "runecrafting" as "woodcutting",
  }), /unknown skill/);
  assert.throws(() => createGatheringResource({
    ...resourceOptions(),
    level: 0,
  }), /level must be an integer 1\.\.255/);
  assert.throws(() => createGatheringResource({
    ...resourceOptions(),
    animation: -2,
  }), /animation must be an integer -1\.\.65535/);
  assert.throws(() => createGatheringResource({
    ...resourceOptions(),
    intervalTicks: 0,
  }), /intervalTicks must be an integer 1\.\.100000/);
  assert.throws(() => createGatheringResource({
    ...resourceOptions(),
    experience: 200000001,
  }), /experience must be an integer 1\.\.200000000/);
  assert.throws(() => createGatheringResource({
    ...resourceOptions(),
    respawnTicks: 0,
  }), /respawnTicks must be an integer 1\.\.100000/);
});

test("gathering builder rejects empty or duplicate tools", () => {
  assert.throws(() => createGatheringResource({
    ...resourceOptions(),
    tools: [],
  }), /tools must contain 1\.\.16 entries/);
  assert.throws(() => createGatheringResource({
    ...resourceOptions(),
    tools: [{ itemId: 1351 }, { itemId: 1351 }],
  }), /duplicate tool item id/);
  assert.throws(() => createGatheringResource({
    ...resourceOptions(),
    tools: [{ itemId: 0 }],
  }), /must be an integer 1\.\.65535 or an item name/);
});

test("gathering builder rejects invalid rewards and success chance", () => {
  assert.throws(() => createGatheringResource({
    ...resourceOptions(),
    rewards: [],
  }), /rewards must contain 1\.\.16 entries/);
  assert.throws(() => createGatheringResource({
    ...resourceOptions(),
    rewards: [{ itemId: 1511, amount: 0 }],
  }), /amount must be an integer 1\.\.2147483647/);
  assert.throws(() => createGatheringResource({
    ...resourceOptions(),
    successChance: { numerator: 5, denominator: 4 },
  }), /numerator must not exceed denominator/);
  assert.throws(() => createGatheringResource({
    ...resourceOptions(),
    successChance: { numerator: 3, denominator: 1000001 },
  }), /denominator must be an integer 1\.\.1000000/);
  // A zero numerator is valid: a deterministic always-miss that never
  // advances the resource RNG (matching ScriptEncounterRng.chance).
  const never = createGatheringResource({
    ...resourceOptions(),
    successChance: { numerator: 0, denominator: 4 },
  });
  assert.equal(never.successChance.numerator, 0);
});

test("gathering builder rejects a depleted object equal to the object id", () => {
  assert.throws(() => createGatheringResource({
    ...resourceOptions(),
    depletedObjectId: 1276,
  }), /depletedObjectId must differ from the resource objectId/);
  assert.throws(() => createGatheringResource({
    ...resourceOptions(),
    depletedObjectId: -1,
  }), /depletedObjectId must be an integer 0\.\.65535/);
});

test("gathering builder accepts tool consumption flags and item names", () => {
  const resource = createGatheringResource({
    ...resourceOptions(),
    tools: [{ itemId: "Bronze axe", consume: true }],
  });
  assert.equal(resource.tools.length, 1);
  assert.equal(resource.tools[0].consume, true);
  assert.equal(resource.tools[0].itemId, "Bronze axe");
});

test("registerGatheringResource forwards a frozen canonical definition", () => {
  const original = (globalThis as Record<string, unknown>)
    .defineGatheringResource;
  let registered: GatheringResourceDefinition | null = null;
  (globalThis as Record<string, unknown>).defineGatheringResource =
    (definition: unknown) => {
      registered = definition as GatheringResourceDefinition;
    };
  try {
    registerGatheringResource(resourceOptions());
  } finally {
    if (original === undefined) {
      delete (globalThis as Record<string, unknown>).defineGatheringResource;
    } else {
      (globalThis as Record<string, unknown>).defineGatheringResource = original;
    }
  }
  assert.ok(registered !== null);
  assert.ok(Object.isFrozen(registered));
});
