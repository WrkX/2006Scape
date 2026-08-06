# Migrating Existing Content to the TypeScript Platform

This guide covers moving existing Java content and legacy TypeScript content
onto the current public SDK. It complements
[typescript-content-authoring.md](./typescript-content-authoring.md), which
describes how to create new content.

## 1. The three legacy surfaces

| Surface | What it is | Migration target |
|---------|-----------|------------------|
| Legacy Java content | `engine/server` switches/events for objects, NPCs, quests, shops, skills, drops | Leave in place if it works; **do not** bulk-migrate unrelated legacy content. Unregistered routes are compatibility assertions, not conversion work. |
| Legacy-unscoped TypeScript | Registrations made outside a `registerModule` scope | Wrap in a module scope (schema version 1) so the content is attributable in `::scripts list`. |
| Legacy author-side builders/weights | The aspirational `BossContext`, `RoomContext.players`, `Infinity`/fractional drop weights, rich domain `Player` in callbacks | Replace with canonical schema-v1 builders and the narrow runtime contexts. |

## 2. Wrap registrations in a module scope

Before:

```ts
// content/src/bosses/my-boss.ts
defineBoss({ /* ... */ });
```

After:

```ts
import { registerModule } from "../sdk/index.js";
registerModule({ id: "my-boss", schemaVersion: 1 }, () => {
  defineBoss({ /* ... */ });
});
```

- The module id is a bounded logical identifier (max 64 chars of letters,
  digits, `.`, `_`, `-`); it is never a host path.
- Nested scopes and duplicate ids reject the candidate.
- Definitions may still reference ids registered by other modules as long as
  those modules load earlier in `content/src/loader.ts`.

## 3. Migrate a legacy definition family

### Quests

Legacy quests (or old scripted quests using raw state) move to the canonical
`createQuest`/`createStage` builder:

```ts
import { createQuest, createStage, registerModule } from "../sdk/index.js";
registerModule({ id: "my-quest", schemaVersion: 1 }, () => {
  defineQuest(createQuest({
    id: "my-quest",
    name: "My Quest",
    summary: "...",
    stages: [createStage(0, "Objective text.")],
    rewards: { questPoints: 1, items: [], experience: [] },
  }));
});
```

- Stages are contiguous and numbered exactly `0..n-1`; each carries the
  objective text that drives the generic quest journal.
- Progress is accessed through `player.quest(id)` (`state()`, `stage()`,
  `objective()`, `start()`, `advance()`, `complete()`), never through raw
  script state in the reserved `__quest` namespace.
- Completion is retryable and atomic; a full inventory, the XP cap, or
  quest-point overflow returns a refusal code and leaves the stage intact.

### Bosses

Legacy imperative encounter bosses and old `BossContext` callbacks move to the
canonical `createBoss`/`defineBoss`:

```ts
defineBoss(createBoss({
  id: "my-boss",
  npcId: 54,                 // must be definition-backed (npc.json)
  name: "My Boss",
  combatLevel: 200,
  maxHitpoints: 500,
  maxHit: 30,
  attack: 300,
  defence: 300,
  arena: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4711, plane: 1 },
  spawn: { x: 2271, y: 4698 },
  command: "my-boss",        // command XOR objectEntry, exactly one
  dropTable: "my_boss_loot", // registered earlier; requires privateTicks
  privateTicks: 200,
  onSpawn(ctx) { ctx.say("You dare?!"); },
  phases: [{ name: "Enrage", hpPercentThreshold: 50, onEnter(ctx) { ctx.useSpecial("fire"); } }],
  specials: { fire: { cooldownTicks: 12, handler(ctx) { /* ... */ } } },
}));
```

- Callbacks receive the narrow `BossRuntimeContext` (`boss`, `encounter`,
  `owner`, `participants()`, `hpPercent()`, `say`, `useSpecial`), never the
  rich domain `Player` or a raw engine object.
- The numeric `npcId` must be a loaded definition; custom ids absent from
  `npc.json` (e.g. the former `12001`) must be replaced with a loaded id.

### Areas

Legacy nested area data (inline NPCs/objects/shops/quests) moves to the
canonical `createArea`:

```ts
defineArea(createArea({
  id: "my_area",
  name: "My Area",
  bounds: { minX: 2830, minY: 9630, maxX: 2870, maxY: 9670, plane: 0 },
  npcs: [{
    key: "my-shopkeeper", npcId: 1, x: 2845, y: 9640,
    openShop: "my_shop",   // requires the spawn to be stationary
  }],
  objects: [{ key: "my-chest", objectId: 2213, x: 2850, y: 9640, drops: [{ action: "first", dropTable: "chest_loot", dropPolicy: "public" }] }],
  shops: ["my_shop"], quests: [], bosses: [], raids: [],
}));
```

- Nested definitions are **not** canonical: register shops, drop tables,
  quests, bosses, and raids separately and reference them by id.
- NPC ids and object ids must be definition-backed.
- Area NPC/object drop bindings are exact allocation/projection routes; an
  equal-id legacy NPC or cache object keeps its own fallback.

### Raids

Legacy raid definitions (rich `RoomContext.players`, inline loot) move to
`createRaid`/`createRaidRoom`/`createBossRoom`:

```ts
defineRaid(createRaid("my_raid", {
  command: "my-raid",
  bounds: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4711, plane: 1 },
  muster: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4695 },
  entrance: { x: 2268, y: 4690, plane: 1 },
  minPlayers: 2, maxPlayers: 5,
  timeLimitTicks: 7200,
  rewards: ["my_reward"],
  rooms: [createRaidRoom({ id: "guardian", name: "Guardians",
    bounds: { ... }, onEnter(ctx) {}, onTick(ctx) { return { status: "completed" }; },
    onComplete(ctx) {} })],
}));
```

- The lobby contract is `create` / `invite <player>` / `join <owner>` /
  `start` through the definition's command route; the roster is immutable
  after start (owner first, then join FIFO).
- A boss room embeds a `defineBoss` by stable id, borrowing the raid's sole
  encounter handle.
- Callbacks receive the narrow `RaidRoomContext`; room results are
  `{ status: "in_progress" | "completed" | "wiped" }`.

### Drop tables

Legacy loot weights (`Infinity`, `0.25`, `veryRare()`) have no canonical
representation:

```ts
// Legacy (rejected):
// dropTable("x").veryRare("dragon_med_helm", 1)

// Canonical:
createDropTable({
  id: "x",
  entries: [
    { itemId: 536, minAmount: 1, maxAmount: 1, weight: 0, always: true },
    { itemId: 1149, minAmount: 1, maxAmount: 1, weight: 1, always: false }, // RARE_WEIGHT
  ],
});
```

- `always: true` requires weight `0`; non-always entries require a positive
  integral weight; the weighted sum must stay in `1..1000000`.
- The fluent `dropTable(id).always(...).common(...).uncommon(...).rare(...)`
  builder emits canonical output; `veryRare()` fails with a migration message.

### Rewards

Legacy ad-hoc inventory/XP grants move to named rewards:

```ts
registerReward({
  id: "my_reward",
  items: [{ id: 995, amount: 5000 }],
  experience: [{ skill: "magic", amount: 1000 }],
  questPoints: 0,
  state: [],
});
```

`player.grantReward("my_reward")` applies the reward through the shared
player-local transaction (preflight, atomic commit, full rollback). Raid
completion applies named rewards roster-wide and atomically.

### Gathering resources

Legacy woodcutting/mining loops move to the resource runtime:

```ts
registerGatheringResource({
  id: "my-tree",
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
});
```

The object route is a Java-owned host route; an equal-id cache or legacy
object at another tile keeps its complete legacy behavior.

## 4. Import hygiene

Import only from the SDK barrel:

```ts
import { createQuest, registerModule, sayNpc } from "../sdk/index.js";
```

Do **not** import engine internals, test globals, or the rich domain `Player`
into executable callbacks. The canonical definitions of the compiled loader
contain no `legacy` raw callback shape; every one is a typed record with a
source module (the demonstration `content/src/examples/*` routes remain
legacy-unscoped by design).

## 5. Migration checklist

- [ ] Every definition is registered inside a `registerModule` scope with a
      bounded id and schema version `1..255`.
- [ ] Every item/npc/object id is definition-backed.
- [ ] Executable callbacks use the narrow runtime contexts.
- [ ] Drop tables use canonical integral weights only.
- [ ] Shop references are typed (`scriptedShop(id)` vs `staticShop(number)`).
- [ ] Equipment slots use the 11 canonical names.
- [ ] `pnpm build:content` is clean and `./scripts/build.sh` passes.
- [ ] `::scripts status` shows the expected module/definition counts and
      `::scripts list modules` attributes every module to its source id.
