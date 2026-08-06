/**
 * Magic helper tests.
 */

import { test } from "node:test";
import assert from "node:assert/strict";
import {
  WIND_STRIKE,
  spellIndex,
  hasSpellRunes,
  consumeSpellRunes,
  spellRequiredLevel,
  hasSpellLevel,
} from "../magic.js";

function magicPlayer(overrides: Partial<{
  findIndex: (id: number) => number;
  hasRunes: (id: number) => boolean;
  consumeRunes: (id: number) => boolean;
  requiredLevel: (id: number) => number;
  hasLevel: (id: number) => boolean;
}> = {}) {
  return {
    getMagic() {
      return {
        findIndex: overrides.findIndex ?? (() => 0),
        hasRunes: overrides.hasRunes ?? (() => true),
        consumeRunes: overrides.consumeRunes ?? (() => true),
        requiredLevel: overrides.requiredLevel ?? (() => 1),
        hasLevel: overrides.hasLevel ?? (() => true),
      };
    },
  };
}

test("WIND_STRIKE matches the modern wind strike button id", () => {
  assert.equal(WIND_STRIKE, 1152);
});

test("spell helpers validate button ids and delegate to the facade", () => {
  const player = magicPlayer({
    findIndex: (id) => (id === 1152 ? 0 : -1),
    hasRunes: (id) => id === 1152,
    consumeRunes: (id) => id === 1152,
    requiredLevel: (id) => (id === 1152 ? 1 : -1),
    hasLevel: (id) => id === 1152,
  });
  assert.equal(spellIndex(player as never, 1152), 0);
  assert.ok(hasSpellRunes(player as never, 1152));
  assert.ok(consumeSpellRunes(player as never, 1152));
  assert.equal(spellRequiredLevel(player as never, 1152), 1);
  assert.ok(hasSpellLevel(player as never, 1152));
});

test("spell helpers reject invalid button ids", () => {
  const player = magicPlayer();
  for (const invalid of [0, -1, 1.5, 70000]) {
    assert.throws(() => spellIndex(player as never, invalid), /1\.\.65535/);
    assert.throws(() => hasSpellRunes(player as never, invalid), /1\.\.65535/);
    assert.throws(() => consumeSpellRunes(player as never, invalid), /1\.\.65535/);
    assert.throws(() => spellRequiredLevel(player as never, invalid), /1\.\.65535/);
    assert.throws(() => hasSpellLevel(player as never, invalid), /1\.\.65535/);
  }
});
