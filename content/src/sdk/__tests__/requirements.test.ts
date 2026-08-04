/**
 * Pure requirement predicate tests.
 *
 * Predicates are pure functions over a narrow view; tests prove bounds
 * validation, quest-state semantics, and combinator composition with fake
 * views.
 */

import { test } from "node:test";
import assert from "node:assert/strict";
import {
  always,
  not,
  all,
  any,
  hasSkillLevel,
  hasItem,
  hasCompletedQuest,
  hasQuestInProgress,
  hasNotStartedQuest,
  hasQuestPoints,
  unmetReason,
} from "../requirements.js";
import type { RequirementView } from "../requirements.js";
import type { ScriptQuestState } from "../../core/runtime.js";

function questState(state: ScriptQuestState, stage: number | null = 0) {
  return {
    id: () => "test-quest",
    name: () => "Test Quest",
    summary: () => "Summary",
    state: () => state,
    stage: () => stage,
    objective: () => state === "in_progress" ? "Objective" : null,
    canStart: () => ({ ok: () => true, changed: () => false, code: () => "can_start" as const }),
    start: () => ({ ok: () => false, changed: () => false, code: () => "started" as const }),
    setStage: () => ({ ok: () => false, changed: () => false, code: () => "advanced" as const }),
    advance: () => ({ ok: () => false, changed: () => false, code: () => "advanced" as const }),
    complete: () => ({ ok: () => false, changed: () => false, code: () => "completed" as const }),
  };
}

function view(overrides: Partial<RequirementView> = {}): RequirementView {
  return {
    getSkills: () => ({ getBaseLevel: (id: number) => id === 6 ? 40 : 1 }),
    getInventory: () => ({
      has: (id: number | string, amount: number) =>
        id === 536 && amount <= 3,
      count: (id: number | string) => id === 536 ? 3 : 0,
    }),
    quest: (id: string) => id === "test-quest" ? questState("in_progress") : null,
    questPoints: () => 12,
    ...overrides,
  };
}

test("skill level predicates validate bounds", () => {
  assert.throws(() => hasSkillLevel("magic", 0), /1\.\.99/);
  assert.throws(() => hasSkillLevel("magic", 100), /1\.\.99/);
  assert.throws(() => hasSkillLevel("magic", 1.5), /1\.\.99/);
  assert.throws(() => hasSkillLevel("not-a-skill", 1), /unknown skill/);
  assert.ok(hasSkillLevel("magic", 40)(view()));
  assert.ok(!hasSkillLevel("magic", 41)(view()));
  assert.ok(hasSkillLevel("attack", 1)(view()));
});

test("item predicates validate bounds", () => {
  assert.throws(() => hasItem(536, 0), /positive/);
  assert.throws(() => hasItem(536, 1.5), /positive/);
  assert.ok(hasItem(536)(view()));
  assert.ok(hasItem(536, 3)(view()));
  assert.ok(!hasItem(536, 4)(view()));
  assert.ok(!hasItem(995)(view()));
});

test("quest-state predicates use exact state semantics", () => {
  assert.ok(hasQuestInProgress("test-quest")(view()));
  assert.ok(!hasQuestInProgress("other-quest")(view()));
  assert.ok(!hasQuestInProgress("test-quest")(view({
    quest: () => questState("completed"),
  })));
  assert.ok(hasCompletedQuest("test-quest")(view({
    quest: () => questState("completed"),
  })));
  assert.ok(!hasCompletedQuest("test-quest")(view()));
  assert.ok(hasNotStartedQuest("other-quest")(view()));
  assert.ok(hasNotStartedQuest("test-quest")(view({
    quest: () => questState("not_started", null),
  })));
  assert.ok(!hasNotStartedQuest("test-quest")(view()));
});

test("quest-point predicates validate bounds", () => {
  assert.throws(() => hasQuestPoints(-1), /0\.\.10000/);
  assert.throws(() => hasQuestPoints(10001), /0\.\.10000/);
  assert.throws(() => hasQuestPoints(1.5), /0\.\.10000/);
  assert.ok(hasQuestPoints(12)(view()));
  assert.ok(!hasQuestPoints(13)(view()));
});

test("combinators compose without side effects", () => {
  const combined = all(hasSkillLevel("magic", 40), hasItem(536),
    hasQuestPoints(12));
  assert.ok(combined(view()));
  assert.ok(!all(hasSkillLevel("magic", 40), hasItem(995))(view()));
  assert.ok(any(hasSkillLevel("magic", 41), hasItem(536))(view()));
  assert.ok(!any(hasSkillLevel("magic", 41), hasItem(995))(view()));
  assert.ok(not(hasItem(995))(view()));
  assert.ok(!not(hasItem(536))(view()));
  assert.ok(always(view()));
});

test("unmetReason reports only a bounded diagnostic", () => {
  assert.equal(unmetReason(view(), hasItem(536)), null);
  assert.equal(unmetReason(view(), hasItem(995)),
    "A requirement is not met.");
});
