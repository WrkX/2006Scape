/**
 * Family builder alignment tests.
 *
 * Proves the aligned boss/quest/area/raid builders emit deeply frozen
 * canonical values, reject invalid bounds and duplicates, and keep their
 * compatibility adapters for shipped input shapes.
 */

import { test } from "node:test";
import assert from "node:assert/strict";
import { createBoss } from "../../bosses/boss-builder.js";
import { createQuest, createStage } from "../../quests/quest-builder.js";
import { createArea } from "../../areas/area-builder.js";
import {
  createRaid,
  createRaidRoom,
  createBossRoom,
} from "../../raids/raid-builder.js";

function bossOptions() {
  return {
    id: "dragon-king",
    npcId: 54,
    name: "Dragon King",
    combatLevel: 450,
    maxHitpoints: 600,
    maxHit: 40,
    attack: 350,
    defence: 350,
    arena: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4711, plane: 1 },
    spawn: { x: 2271, y: 4698 },
    command: "dragon-king",
    dropTable: "dragon_king_loot",
    privateTicks: 200,
    onSpawn: () => {},
    phases: [
      { name: "Fire Phase", hpPercentThreshold: 50, onEnter: () => {} },
    ],
    specials: {
      fire_wave: { cooldownTicks: 12, handler: () => {} },
    },
  };
}

test("boss builder deep-freezes phases, specials, and entry fields", () => {
  const boss = createBoss(bossOptions() as Parameters<typeof createBoss>[0]);
  assert.ok(Object.isFrozen(boss));
  assert.ok(Object.isFrozen(boss.arena));
  assert.ok(Object.isFrozen(boss.spawn));
  assert.ok(Object.isFrozen(boss.phases));
  assert.ok(Object.isFrozen(boss.phases![0]));
  assert.ok(Object.isFrozen(boss.specials));
  assert.ok(Object.isFrozen(boss.specials!.fire_wave));
  assert.throws(() => {
    (boss.phases![0] as { hpPercentThreshold: number }).hpPercentThreshold = 10;
  }, TypeError);
  assert.throws(() => {
    (boss.specials!.fire_wave as { cooldownTicks: number }).cooldownTicks = 1;
  }, TypeError);

  const objectEntry = createBoss({
    ...bossOptions(),
    command: undefined,
    objectEntry: { objectId: 2213, action: "first" },
    entryTeleport: { x: 2270, y: 4690 },
  } as never);
  assert.deepEqual(objectEntry.objectEntry, { objectId: 2213, action: "first" });
  assert.deepEqual(objectEntry.entryTeleport, { x: 2270, y: 4690 });
  assert.ok(Object.isFrozen(objectEntry.objectEntry));
  assert.ok(Object.isFrozen(objectEntry.entryTeleport));
});

test("boss builder rejects invalid bounds and duplicate phases", () => {
  const base = bossOptions();
  assert.throws(() => createBoss({
    ...base, arena: { ...base.arena, maxX: 2263 },
  } as never), /inverted/);
  assert.throws(() => createBoss({
    ...base, arena: { ...base.arena, minX: 2200, maxX: 2350 },
  } as never), /1\.\.64/);
  assert.throws(() => createBoss({
    ...base, spawn: { x: 100, y: 100 },
  } as never), /inside the declared arena/);
  assert.throws(() => createBoss({
    ...base, phases: [
      { name: "Same", hpPercentThreshold: 80, onEnter: () => {} },
      { name: "Same", hpPercentThreshold: 50, onEnter: () => {} },
    ],
  } as never), /duplicate phase name/);
  assert.throws(() => createBoss({
    ...base, phases: [
      { name: "A", hpPercentThreshold: 50, onEnter: () => {} },
      { name: "B", hpPercentThreshold: 80, onEnter: () => {} },
    ],
  } as never), /strictly descending/);
  assert.throws(() => createBoss({
    ...base, command: undefined, closeCommand: "x",
  } as never), /exactly one of command or objectEntry/);
  assert.throws(() => createBoss({
    ...base, dropTable: "missing", privateTicks: undefined,
  } as never), /declared together/);
  assert.throws(() => createBoss({
    ...base, command: "scripts",
  } as never), /command pattern|reserved/);
});

test("quest builder deep-freezes stages, requirements, and rewards", () => {
  const quest = createQuest({
    id: "dragon-awakens",
    name: "Dragon Awakens",
    summary: "Complete the rite.",
    stages: [createStage(0, "Speak to Chronozon.")],
    requirements: { skills: [{ skill: "magic", level: 1 }] },
    rewards: { questPoints: 3, items: [{ itemId: 995, amount: 1000 }] },
  });
  assert.ok(Object.isFrozen(quest));
  assert.ok(Object.isFrozen(quest.stages));
  assert.ok(Object.isFrozen(quest.stages[0]));
  assert.ok(Object.isFrozen(quest.requirements));
  assert.ok(Object.isFrozen(quest.requirements!.skills![0]));
  assert.ok(Object.isFrozen(quest.rewards));
  assert.ok(Object.isFrozen(quest.rewards!.items![0]));
  assert.throws(() => {
    (quest.stages[0] as { objective: string }).objective = "Changed.";
  }, TypeError);
});

test("quest builder rejects duplicate and out-of-bound members", () => {
  assert.throws(() => createQuest({
    id: "dragon-awakens",
    name: "Dragon Awakens",
    summary: "Summary",
    stages: [createStage(0, "A."), createStage(1, "B.")],
    requirements: {
      skills: [{ skill: "magic", level: 1 }, { skill: "magic", level: 2 }],
    },
  }), /duplicate skill requirement/);
  assert.throws(() => createQuest({
    id: "dragon-awakens",
    name: "Dragon Awakens",
    summary: "Summary",
    stages: [createStage(1, "Wrong numbering.")],
  }), /exactly numbered/);
  assert.throws(() => createQuest({
    id: "dragon-awakens",
    name: "Dragon Awakens",
    summary: "Summary",
    stages: [createStage(0, "A.")],
    requirements: { skills: [{ skill: "magic", level: 100 }] },
  }), /1\.\.99/);
  assert.throws(() => createQuest({
    id: "dragon-awakens",
    name: "Dragon Awakens",
    summary: "Summary",
    stages: [createStage(0, "A.")],
    rewards: { items: [{ itemId: 995, amount: 1 }, { itemId: 995, amount: 2 }] },
  }), /duplicate .* item/);
});

test("area builder deep-freezes spawns, objects, drops, and references", () => {
  const area = createArea({
    id: "dragon_island",
    name: "Dragon Island",
    bounds: { minX: 2830, minY: 9630, maxX: 2870, maxY: 9670, plane: 0 },
    npcs: [
      {
        key: "guardian",
        npcId: 55,
        x: 2840,
        y: 9660,
        dropTable: "dragon_guardian_loot",
        dropPolicy: "private-to-killer",
        privateTicks: 200,
      },
    ],
    objects: [
      {
        key: "chest",
        objectId: 2213,
        x: 2850,
        y: 9640,
        drops: [{
          action: "first",
          dropTable: "ancient_chest_loot",
          dropPolicy: "public",
        }],
      },
    ],
    shops: ["dragon_island_general"],
    quests: ["dragon-awakens"],
    bosses: ["dragon-king"],
    raids: ["temple_of_zaros"],
  });
  assert.ok(Object.isFrozen(area));
  assert.ok(Object.isFrozen(area.bounds));
  assert.ok(Object.isFrozen(area.npcs));
  assert.ok(Object.isFrozen(area.npcs[0]));
  assert.ok(Object.isFrozen(area.objects));
  assert.ok(Object.isFrozen(area.objects[0]));
  assert.ok(Object.isFrozen(area.objects[0].drops![0]));
  assert.ok(Object.isFrozen(area.shops));
  assert.ok(Object.isFrozen(area.quests));
  assert.ok(Object.isFrozen(area.bosses));
  assert.ok(Object.isFrozen(area.raids));
  assert.throws(() => {
    (area.npcs[0] as { npcId: number }).npcId = 1;
  }, TypeError);
});

test("area builder rejects duplicate keys, tiles, and invalid drops", () => {
  const base = {
    id: "dragon_island",
    name: "Dragon Island",
    bounds: { minX: 2830, minY: 9630, maxX: 2870, maxY: 9670, plane: 0 },
  };
  assert.throws(() => createArea({
    ...base,
    npcs: [
      { key: "dup", npcId: 1, x: 2840, y: 9640 },
      { key: "dup", npcId: 2, x: 2841, y: 9640 },
    ],
    objects: [],
  }), /duplicate NPC spawn key/);
  assert.throws(() => createArea({
    ...base,
    npcs: [],
    objects: [
      { key: "a", objectId: 1, x: 2840, y: 9640 },
      { key: "b", objectId: 2, x: 2840, y: 9640 },
    ],
  }), /duplicate object tile/);
  assert.throws(() => createArea({
    ...base,
    npcs: [{
      key: "g", npcId: 55, x: 2840, y: 9640,
      dropPolicy: "private-to-killer", privateTicks: 200,
    }],
    objects: [],
  }), /'dropPolicy' requires a named 'dropTable'/);
  assert.throws(() => createArea({
    ...base,
    npcs: [{
      key: "g", npcId: 55, x: 2840, y: 9640,
      dropTable: "loot", dropPolicy: "public", privateTicks: 200,
    }],
    objects: [],
  }), /'privateTicks' is not allowed/);
  assert.throws(() => createArea({
    ...base,
    npcs: [],
    objects: [{
      key: "c", objectId: 2213, x: 2850, y: 9640,
      drops: [{ action: "first", dropTable: "loot", dropPolicy: "public" },
        { action: "first", dropTable: "loot", dropPolicy: "public" }],
    }],
  }), /duplicate drop action/);
  assert.throws(() => createArea({
    ...base,
    npcs: [], objects: [],
    shops: ["dragon_island_general", "dragon_island_general"],
  }), /duplicate shops reference/);
});

test("area builder normalizes the legacy northWest/southEast bounds", () => {
  const area = createArea({
    id: "legacy_bounds_area",
    name: "Legacy",
    bounds: {
      northWest: { x: 100, y: 200, plane: 0 },
      southEast: { x: 90, y: 180 },
    } as never,
    npcs: [],
    objects: [],
  });
  assert.deepEqual(area.bounds, { minX: 90, minY: 180, maxX: 100, maxY: 200, plane: 0 });
  assert.throws(() => createArea({
    id: "legacy_bounds_area",
    name: "Legacy",
    bounds: { northWest: { x: 1, y: 2 } } as never,
    npcs: [],
    objects: [],
  }), /bounds must be canonical/);
});

test("raid builder deep-freezes rooms and rewards", () => {
  const room = createRaidRoom({
    id: "guardian",
    name: "Hall of Guardians",
    bounds: { minX: 2264, minY: 4688, maxX: 2275, maxY: 4695, plane: 1 },
    onEnter: () => {},
    onTick: () => ({ status: "in_progress" }),
    onComplete: () => {},
    boss: createBossRoom({ bossId: "dragon-king" }),
  });
  assert.ok(Object.isFrozen(room));
  assert.ok(Object.isFrozen(room.bounds));
  assert.ok(Object.isFrozen(room.boss));
  assert.throws(() => {
    (room.bounds as { minX: number }).minX = 1;
  }, TypeError);

  const raid = createRaid("temple_of_zaros", {
    command: "temple-of-zaros",
    bounds: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4711, plane: 1 },
    muster: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4695 },
    entrance: { x: 2268, y: 4690, plane: 1 },
    minPlayers: 2,
    maxPlayers: 5,
    timeLimitTicks: 7200,
    rooms: [room],
    rewards: ["zaros_raid_reward"],
  });
  assert.ok(Object.isFrozen(raid));
  assert.ok(Object.isFrozen(raid.rooms));
  assert.ok(Object.isFrozen(raid.rewards));
  assert.ok(Object.isFrozen(raid.bounds));
  assert.ok(Object.isFrozen(raid.muster));
  assert.ok(Object.isFrozen(raid.entrance));
  assert.throws(() => {
    (raid.rooms as unknown as unknown[]).push(room);
  }, TypeError);
  assert.throws(() => {
    (raid.rewards as unknown as unknown[]).push("extra");
  }, TypeError);
});

test("raid builder deep-freezes raw room literals", () => {
  const rawRoom: import("../../core/raid.js").RaidRoomDefinition = {
    id: "raw-room",
    name: "Raw Room",
    bounds: { minX: 2264, minY: 4688, maxX: 2275, maxY: 4695, plane: 1 },
    onEnter: () => {},
    onTick: () => ({ status: "in_progress" }),
    onComplete: () => {},
    boss: { bossId: "dragon-king" },
  };
  const raid = createRaid("temple_of_zaros", {
    command: "temple-of-zaros",
    bounds: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4711, plane: 1 },
    muster: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4695 },
    entrance: { x: 2268, y: 4690, plane: 1 },
    minPlayers: 2,
    maxPlayers: 5,
    timeLimitTicks: 7200,
    rooms: [rawRoom],
    rewards: ["zaros_raid_reward"],
  });
  const room = raid.rooms[0];
  assert.ok(Object.isFrozen(room));
  assert.ok(Object.isFrozen(room.bounds));
  assert.ok(Object.isFrozen(room.boss));
  assert.throws(() => {
    (room as { name: string }).name = "Changed";
  }, TypeError);
  assert.throws(() => {
    (room.bounds as { minX: number }).minX = 1;
  }, TypeError);
  assert.throws(() => {
    (room.boss as { bossId: string }).bossId = "other";
  }, TypeError);
});

test("raid builder rejects invalid limits, rooms, and references", () => {
  const base = {
    command: "temple-of-zaros",
    bounds: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4711, plane: 1 },
    muster: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4695 },
    entrance: { x: 2268, y: 4690, plane: 1 },
    timeLimitTicks: 7200,
    rooms: [createRaidRoom({
      id: "guardian",
      name: "Hall of Guardians",
      bounds: { minX: 2264, minY: 4688, maxX: 2275, maxY: 4695, plane: 1 },
      onEnter: () => {},
      onTick: () => ({ status: "in_progress" }),
      onComplete: () => {},
    })],
  };
  assert.throws(() => createRaid("r", {
    ...base, minPlayers: 2, maxPlayers: 1, rewards: ["reward"],
  }), /minPlayers.*<=.*maxPlayers/);
  assert.throws(() => createRaid("r", {
    ...base, minPlayers: 0, maxPlayers: 1, rewards: ["reward"],
  }), /minPlayers must be 1\.\.8/);
  assert.throws(() => createRaid("r", {
    ...base, minPlayers: 1, maxPlayers: 9, rewards: ["reward"],
  }), /maxPlayers must be 1\.\.8/);
  assert.throws(() => createRaid("r", {
    ...base, minPlayers: 1, maxPlayers: 2, rewards: [],
  }), /non-empty array of named reward ids/);
  assert.throws(() => createRaid("r", {
    ...base, minPlayers: 1, maxPlayers: 2,
    rewards: ["reward", "reward"],
  }), /duplicate reward/);
  assert.throws(() => createRaid("r", {
    ...base, minPlayers: 1, maxPlayers: 2, rewards: ["reward"],
    muster: { minX: 2200, minY: 4688, maxX: 2287, maxY: 4695 },
  }), /muster must lie inside/);
  assert.throws(() => createRaid("r", {
    ...base, minPlayers: 1, maxPlayers: 2, rewards: ["reward"],
    entrance: { x: 2268, y: 4690, plane: 0 },
  }), /entrance must lie inside/);
  assert.throws(() => createRaid("r", {
    ...base, minPlayers: 1, maxPlayers: 2, rewards: ["reward"],
    rooms: [
      createRaidRoom({
        id: "guardian",
        name: "Hall of Guardians",
        bounds: { minX: 2264, minY: 4688, maxX: 2275, maxY: 4695, plane: 1 },
        onEnter: () => {},
        onTick: () => ({ status: "in_progress" }),
        onComplete: () => {},
      }),
      createRaidRoom({
        id: "guardian",
        name: "Duplicate Hall",
        bounds: { minX: 2276, minY: 4688, maxX: 2287, maxY: 4695, plane: 1 },
        onEnter: () => {},
        onTick: () => ({ status: "in_progress" }),
        onComplete: () => {},
      }),
    ],
  }), /duplicate room id/);
  assert.throws(() => createRaid("r", {
    ...base, minPlayers: 1, maxPlayers: 2, rewards: ["reward"],
    command: "scripts",
  }), /command/);
});
