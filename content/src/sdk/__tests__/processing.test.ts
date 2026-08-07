/**
 * @module sdk/__tests__/processing
 */

import assert from "node:assert/strict";
import test from "node:test";
import {
  createProcessingSkill,
  registerProcessingSkill,
} from "../processing.js";
import type { ProcessingSkillDefinition } from "../../core/runtime.js";

function options(
  overrides: Partial<ProcessingSkillDefinition> = {},
): ProcessingSkillDefinition {
  return {
    id: "cook-shrimp-range",
    name: "shrimp",
    skill: "cooking",
    level: 1,
    inputItemId: 317,
    objectId: 114,
    productItemId: 315,
    failProductItemId: 7954,
    experience: 30,
    animation: 896,
    sound: 357,
    intervalTicks: 4,
    stopBurnLevel: 34,
    stopBurnLevelWithGloves: 30,
    glovesItemId: 775,
    ...overrides,
  };
}

test("createProcessingSkill deep-freezes the canonical shape", () => {
  const skill = createProcessingSkill(options());
  assert.equal(skill.id, "cook-shrimp-range");
  assert.equal(skill.inputItemId, 317);
  assert.equal(skill.objectId, 114);
  assert.equal(skill.productItemId, 315);
  assert.equal(skill.failProductItemId, 7954);
  assert.equal(skill.stopBurnLevel, 34);
  assert.throws(() => {
    (skill as { level: number }).level = 99;
  });
});

test("rejects invalid ids, skills, and item/product collisions", () => {
  assert.throws(() => createProcessingSkill(options({ id: "" })),
    /invalid processing id/);
  assert.throws(() => createProcessingSkill(options({ skill: "alchemy" })),
    /unknown skill/);
  assert.throws(() => createProcessingSkill(options({
    productItemId: 317,
  })), /must differ from inputItemId/);
  assert.throws(() => createProcessingSkill(options({
    failProductItemId: 315,
  })), /must differ from input and product/);
});

test("gloves fields must be declared together", () => {
  assert.throws(() => createProcessingSkill(options({
    glovesItemId: undefined,
    stopBurnLevelWithGloves: 30,
  })), /must be set together/);
  assert.throws(() => createProcessingSkill(options({
    glovesItemId: 775,
    stopBurnLevelWithGloves: undefined,
  })), /must be set together/);
});

test("stopBurnLevel must be at least the required level", () => {
  assert.throws(() => createProcessingSkill(options({
    level: 40,
    stopBurnLevel: 34,
    stopBurnLevelWithGloves: 40,
  })), /stopBurnLevel must be >= level/);
});

test("registerProcessingSkill forwards a frozen definition", () => {
  const seen: ProcessingSkillDefinition[] = [];
  (globalThis as { defineProcessingSkill?: unknown }).defineProcessingSkill =
    (definition: ProcessingSkillDefinition) => {
      seen.push(definition);
    };
  try {
    registerProcessingSkill(options({ burnBonus: 3 }));
    assert.equal(seen.length, 1);
    assert.equal(seen[0].id, "cook-shrimp-range");
    assert.equal(seen[0].burnBonus, 3);
    assert.throws(() => {
      (seen[0] as { experience: number }).experience = 1;
    });
  } finally {
    delete (globalThis as { defineProcessingSkill?: unknown })
      .defineProcessingSkill;
  }
});
