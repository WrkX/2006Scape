# Authoring TypeScript Content

This guide shows how to create new game content for SingleScape using only the
public TypeScript SDK. It is the entry point for content authors; the exact
runtime contract for every definition family lives in
[SCRIPT_BRIDGE.md](./SCRIPT_BRIDGE.md), and the Java/TypeScript ownership
boundary is [ENGINE_BOUNDARY.md](./ENGINE_BOUNDARY.md).

## 1. The one rule

> **Java is the engine. TypeScript is the game.**

A content module imports only the public SDK barrel
(`content/src/sdk/index.ts`), registers its definitions and routes inside one
`registerModule` scope, and never touches engine internals, host paths, the
filesystem, wall-clock time, or uncontrolled randomness. Every shipped
production fixture follows this rule; the whole compiled `content/dist`
loader registers exactly eight source-aware modules.

```text
content/src/
├── loader.ts                 ← the single entry point, imported by the bridge
├── manifest.ts               ← registerModule (id/schemaVersion) helper
├── sdk/index.ts              ← the public barrel (content-kit + canonical types)
├── quests/  bosses/  areas/  raids/  resources/  drops/
│   └── ...your module...       ← each file registers under one module scope
└── examples/                 ← legacy-unscoped demonstration routes
```

## 2. Create a module

Open one bounded registration scope. Every definition, route, and observer
inside it carries the module id and schema version; operator diagnostics
(`::scripts list modules`) can then attribute the content to a logical source.

```ts
// content/src/quests/my-quest.ts
import { createQuest, createStage, registerModule } from "../sdk/index.js";

registerModule({ id: "my-quest", schemaVersion: 1 }, () => {
  const quest = createQuest({
    id: "my-quest",
    name: "My Quest",
    summary: "A short adventure.",
    stages: [
      createStage(0, "Speak to the quest giver."),
      createStage(1, "Return to the quest giver."),
    ],
    rewards: {
      questPoints: 1,
      items: [{ itemId: 995, amount: 500 }],
      experience: [{ skill: "magic", amount: 500 }],
    },
  });
  defineQuest(quest);
});
```

Rules for the module id: at most 64 characters of letters, digits, `.`, `_`,
or `-`. Schema versions are `1..255`. Nested scopes and duplicate module ids
reject the whole reload candidate. Registrations made outside any scope are
recorded as `legacy-unscoped` compatibility records (schema version 0) and
still load, but they are invisible to module attribution.

Then add the module to the loader import graph:

```ts
// content/src/loader.ts
import "./quests/my-quest.js";
```

> Importing a file is what evaluates its registrations. Merely exporting a
> definition from the SDK does not register it.

## 3. Register the definitions you need

The public barrel exports the canonical family builders and the global
registration functions. The two-step pattern is always:

1. Build + validate a frozen canonical definition with the builder
   (`createQuest`, `createBoss`, `createArea`, `createRaid`,
   `createGatheringResource`, `createShop`, `createDropTable`, `createReward`).
2. Register it with the matching global (`defineQuest`, `defineBoss`,
   `defineArea`, `defineRaid`, `defineGatheringResource`, `defineShop`,
   `defineDropTable`, `defineReward`).

There are also `register*` convenience helpers that do both steps
(`registerQuest`, `registerBoss`, `registerArea`, `registerRaid`,
`registerGatheringResource`, `registerShop`). Every builder validates the exact
bounds the Java parser enforces, deep-freezes its output, and throws a bounded
load-time diagnostic on invalid input instead of letting the engine fail late.

### Definition families at a glance

| Family | Builder | Global | Consumed by |
|--------|---------|--------|-------------|
| Quest | `createQuest`/`createStage` | `defineQuest` | The Java-owned quest runtime: staged progress, generic journal, retryable reward |
| Boss | `createBoss` | `defineBoss` | WP3 standalone boss: command/object entry, phases, specials, named drops |
| Area | `createArea` | `defineArea` | WP4 area runtime: NPC spawns, layered objects, drops, scripted shops |
| Raid | `createRaid`/`createRaidRoom`/`createBossRoom` | `defineRaid` | WP5 raid runtime: lobby, rooms, embedded boss, roster rewards |
| Gathering | `createGatheringResource` | `defineGatheringResource` | WP8 resource runtime: tool/level/tick/reward/deplete/respawn |
| Drop table | `createDropTable`/`dropTable` builder | `defineDropTable` | Rolled by bosses, areas, raids, encounters |
| Reward | `createReward` | `defineReward` | `player.grantReward(id)`, quest/raid completion |
| Shop | `createShop` | `defineShop` | WP4 scripted shop runtime |

### Cross-references

Named references — a boss `dropTable`, a raid `rewards: [...]`, an area
`shops: [...]`, a raid room `boss: { bossId }` — resolve **within the same
reload candidate**, not within one module. A module may reference a definition
registered by another module as long as the referenced id is registered
earlier in the loader import order. The shipped Dragon Island content is split
across eight modules exactly this way: `dragon-island-drops` registers the
tables first, then `dragon-king`, `dragon-awakens`, `temple-of-zaros`,
`dragon-island-shops`, and finally `dragon-island` references them all by id.

A missing, duplicate, ambiguous, or cyclic reference rejects the candidate
with the source module and field path.

## 4. Write executable routes

Runtime interaction handlers are registered with the globals (`onObject`,
`onNpc`, `onCommand`, `onItem`, `onItemOnItem`, `onItemOnObject`,
`onItemOnNpc`, `onButton`, `onItemOnGroundItem`, `onItemOnPlayer`,
`onMagicOnItem`, `onMagicOnObject`) and the lifecycle observers (`onLogin`,
`onLogout`, `onNpcDeath`, `onItemPickup`, `onEnterArea`, `onLeaveArea`,
`onPlayerDeath`). Each receives one narrow context whose `player` is the
`ScriptedPlayer` runtime wrapper — never the rich domain `Player`.

```ts
import { sayNpc, sayOptions, endDialogue } from "../sdk/index.js";

onNpc(667, "first", ({ player }) => {
  sayNpc(player, 667, "The rite awaits.");
  sayOptions(player, ["Begin.", "Later."], (choice) => {
    if (choice === 0) {
      // Start the quest.
    }
    endDialogue(player);
  });
});
```

Object and NPC actions are ordinal (`"first"`/`"second"`/`"third"`/`"fourth"`),
not semantic labels. Executable handlers are authoritative: a matched handler
consumes the packet even when it throws; a valid unmatched key alone falls
through to the legacy Java path. Invalid input reaches neither.

## 5. Use the SDK helpers

The barrel exports composable helpers that cover the recurring authoring
patterns without reaching into the engine:

- **Requirements** — `sdk/requirements`: pure predicates
  (`hasSkillLevel`, `hasItem`, `hasCompletedQuest`, `hasQuestPoints`, …)
  composed with `all`/`any`/`not` over the narrow `ScriptedPlayer` view.
- **Rewards** — `sdk/rewards`: `grantReward(player, id)` forwards the named
  reward through the shared transactional consumer; `isRewarded` checks the
  result code.
- **Shops** — `sdk/shops`: typed `ShopReference` (`scriptedShop(id)` vs
  `staticShop(number)`) and `openShop(player, ref)` routes to the exact
  capability.
- **Equipment** — `sdk/equipment`: the 11 canonical runtime slot names,
  `equipped`/`hasEquipped`/`equipmentSummary`. Legacy names (`head`, `neck`,
  `body`, `ammo`) fail with a migration message.
- **Dialogue** — `sdk/dialogue`: bounded `sayNpc`/`sayPlayer`/`sayStatement`/
  `sayOptions` over the dialogue chain, plus the cutscene session engine
  (`runCutscene`, `cancelCutscene`) that owns every task, lock, and camera
  it creates.
- **Drop tables** — `createDropTable` and the fluent `dropTable(id)`
  builder with canonical integral weights; `always` entries have weight `0`.
- **Skills** — `sdk/skills`: the canonical 21-name table and `skillIndex`.

## 6. Run and verify

```bash
pnpm build:content                     # tsc: typechecks and compiles content/dist
pnpm --filter @singlescape/content test # 72 SDK unit tests + API-inventory freshness
./scripts/build.sh                      # the single acceptance gate: content + full Maven reactor
```

In a running server, inspect and reload the runtime through the
administrator commands (rights >= 2):

```text
::scripts status            # active generation, module/definition/route counts
::scripts list modules      # the source-aware module ids
::scripts list boss         # registered bosses and their source module
::scripts reload            # transactional reload; truthful success/failure
::scriptdir                 # deprecated, sanitized alias of ::scripts status
```

A rejected reload reports the bounded candidate error and proves the previous
generation stays live; fix the offending module and reload again.

## 7. Authoring checklist

- [ ] The module imports only from `content/src/sdk/index.js`.
- [ ] The module id is a bounded logical identifier and the schema version is
      `1..255`.
- [ ] Definitions are registered (not merely exported) inside the module scope.
- [ ] The loader import graph is updated in `content/src/loader.ts`.
- [ ] Named references resolve to definitions registered earlier in the loader.
- [ ] Executable callbacks use the narrow runtime contexts, never the rich
      domain `Player`.
- [ ] `pnpm build:content` is clean and the SDK tests pass.
- [ ] `./scripts/build.sh` passes.
