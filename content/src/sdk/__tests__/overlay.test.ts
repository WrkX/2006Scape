import assert from "node:assert/strict";
import { test } from "node:test";

import {
  createItemOverlay,
  createNpcOverlay,
  createObjectOverlay,
  CUSTOM_ITEM_START,
  CUSTOM_NPC_START,
  CUSTOM_OBJECT_START,
  registerItemOverlay,
} from "../overlay.js";

test("createItemOverlay validates and freezes canonical overlays", () => {
  const overlay = createItemOverlay({
    id: "ported-bronze-sword",
    itemId: CUSTOM_ITEM_START,
    name: "Ported bronze sword",
    examine: "A sword from the custom asset namespace.",
    equipSlot: "weapon",
    requirements: { attack: 1 },
    bonuses: { attackStab: 4, strength: 5 },
  });
  assert.equal(overlay.id, "ported-bronze-sword");
  assert.equal(overlay.itemId, CUSTOM_ITEM_START);
  assert.equal(overlay.equipSlot, "weapon");
  assert.throws(
    () => createItemOverlay({ id: "empty", itemId: CUSTOM_ITEM_START }),
    /at least one field/,
  );
});

test("createNpcOverlay validates combat metadata", () => {
  const overlay = createNpcOverlay({
    id: "ported-town-guard",
    npcId: CUSTOM_NPC_START,
    combatLevel: 21,
    hitpoints: 22,
  });
  assert.equal(overlay.combatLevel, 21);
  assert.throws(
    () => createNpcOverlay({ id: "empty", npcId: CUSTOM_NPC_START }),
    /at least one field/,
  );
});

test("createObjectOverlay validates menu actions", () => {
  const overlay = createObjectOverlay({
    id: "ported-signpost",
    objectId: CUSTOM_OBJECT_START,
    actions: ["Read"],
  });
  assert.deepEqual(overlay.actions, ["Read"]);
  assert.throws(
    () => createObjectOverlay({
      id: "bad",
      objectId: CUSTOM_OBJECT_START,
      actions: ["x".repeat(40)],
    }),
    /actions\[0\]/,
  );
});

test("registerItemOverlay delegates to defineItemOverlay", () => {
  let captured: unknown;
  (globalThis as { defineItemOverlay?: unknown }).defineItemOverlay =
    (definition: unknown) => {
      captured = definition;
    };
  try {
    registerItemOverlay({
      id: "ported-bronze-sword",
      itemId: CUSTOM_ITEM_START,
      name: "Ported bronze sword",
    });
    assert.deepEqual(captured, {
      id: "ported-bronze-sword",
      itemId: CUSTOM_ITEM_START,
      name: "Ported bronze sword",
    });
  } finally {
    delete (globalThis as { defineItemOverlay?: unknown }).defineItemOverlay;
  }
});
