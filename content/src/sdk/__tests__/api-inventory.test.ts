/**
 * SDK inventory contract tests.
 *
 * The generated API inventory must match the declared runtime globals and
 * the public SDK barrel exports, and the checked-in
 * `docs/API_INVENTORY.md` must be current.
 */

import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import {
  CONTENT_ROOT,
  generateApiInventory,
} from "./api-inventory.js";

const DOC_PATH = join(CONTENT_ROOT, "..", "docs", "API_INVENTORY.md");

test("inventory documents every declared runtime global", () => {
  const inventory = generateApiInventory();
  const expectedGlobals = [
    "defineBoss", "defineQuest", "defineRaid", "defineArea", "defineShop",
    "defineDropTable", "defineReward", "defineGatheringResource",
    "defineProcessingSkill", "defineMob", "defineItemOverlay",
    "defineNpcOverlay", "defineObjectOverlay", "defineInterfaceHook", "onObject", "onNpc", "onCommand",
    "onItem", "onItemOnItem", "onItemOnObject", "onItemOnNpc", "onLogin",
    "onLogout", "onNpcDeath", "onItemPickup", "onEnterArea", "onLeaveArea",
    "onButton", "onItemOnGroundItem", "onItemOnPlayer", "onMagicOnItem",
    "onMagicOnObject", "onMagicOnNpc", "onMagicOnPlayer", "onPlayerDeath",
    "registerContentModule", "dev",
    "log",
  ];
  for (const global of expectedGlobals) {
    assert.ok(inventory.includes(`\`${global}\``),
      `inventory must document runtime global '${global}'`);
  }
});

test("inventory documents every SDK barrel module", () => {
  const inventory = generateApiInventory();
  for (const module of [
    "sdk/skills.ts", "sdk/requirements.ts", "sdk/rewards.ts",
    "sdk/shops.ts", "sdk/equipment.ts", "sdk/magic.ts", "sdk/prayer.ts",
    "sdk/dialogue.ts",
    "sdk/drop-tables.ts", "sdk/gathering.ts", "sdk/processing.ts",
    "sdk/mob.ts", "sdk/overlay.ts", "sdk/interface-hook.ts",
    "manifest.ts", "bosses/boss-builder.ts",
    "quests/quest-builder.ts", "areas/area-builder.ts",
    "raids/raid-builder.ts",
  ]) {
    assert.ok(inventory.includes(`\`${module}\``),
      `inventory must document barrel module '${module}'`);
  }
  assert.ok(inventory.includes("createReward"),
    "inventory must list createReward in sdk/rewards.ts exports");
  assert.ok(inventory.includes("runCutscene"),
    "inventory must list runCutscene in sdk/dialogue.ts exports");
});

test("inventory keeps rich Player-bearing types off the SDK surface", () => {
  const inventory = generateApiInventory();
  assert.ok(inventory.includes("`core/types.ts`"),
    "canonical core types remain documented");
  for (const aspirational of ["Inventory", "Equipment", "Skills", "Quests",
    "DialogueOption", "DialogueType", "NpcSpawn", "NpcInteractionHandler",
    "ShopEntry", "BossContext"]) {
    assert.ok(!inventory.includes(` ${aspirational},`),
      `inventory must not export aspirational type '${aspirational}'`);
  }
  assert.ok(inventory.includes("ItemId"),
    "canonical types stay exported");
  assert.ok(inventory.includes("BossRuntimeContext"),
    "canonical boss runtime context stays exported");
});

test("checked-in docs/API_INVENTORY.md is current", () => {
  const current = readFileSync(DOC_PATH, "utf8");
  const expected = generateApiInventory() + "\n";
  assert.equal(current, expected,
    "docs/API_INVENTORY.md is stale — run " +
      "`pnpm --filter @singlescape/content build && " +
      "node content/scripts/api-inventory.mjs`");
});
