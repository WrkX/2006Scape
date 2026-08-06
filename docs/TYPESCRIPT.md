# TypeScript Content Layer

## Goal

Keep the existing Java server but make most new game content writable in
TypeScript.

TypeScript compiles to ES2020 JavaScript and executes in the server's embedded
GraalJS context:

```text
TypeScript
   ↓ tsc / bundler
JavaScript
   ↓
embedded JS runtime
   ↓
Java Game API
   ↓
2006Scape engine
```

This pipeline is implemented. The Java host owns engine state, persistence,
transaction boundaries, and validated bridge wrappers; TypeScript owns
definitions and interaction/lifecycle composition. See
[SCRIPT_BRIDGE.md](./SCRIPT_BRIDGE.md) for the exact runtime contract.

## API philosophy

Do not expose arbitrary Java internals.

Expose a stable game SDK.

The richer `Player` domain interface remains useful for declarative models, but
it is not passed to executable callbacks. Runtime registrations receive one
context whose `player` is the narrow `ScriptedPlayer` declared in
`content/src/core/runtime.ts`.

Example:

```ts
export interface Player {
  readonly username: string;

  message(text: string): void;
  teleport(x: number, y: number, plane?: number): void;

  inventory: Inventory;
  bank: Bank;
  skills: Skills;
  equipment: Equipment;
}
```

## Example custom boss

```ts
defineBoss({
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

  onSpawn(ctx) {
    ctx.say("You dare enter my domain? You will burn!");
  },

  phases: [
    {
      name: "Fire Phase",
      hpPercentThreshold: 50,
      onEnter(ctx) {
        ctx.say("Now you will feel true dragon fire!");
        ctx.useSpecial("fire_wave");
      },
    },
  ],

  specials: {
    fire_wave: {
      cooldownTicks: 12,
      handler(ctx) {
        ctx.say("Burn!");
      },
    },
  },
});
```

The canonical boss is parsed into an immutable Java-owned descriptor; its
exact command (or object) route begins one encounter and the encounter-
agnostic controller drives spawn, ordered phase thresholds, armed special
cooldowns, named drops, death, and cleanup. Every callback receives the
narrow `BossRuntimeContext` (`boss`, `encounter`, `owner`,
`participants()`, `hpPercent()`, `say`, `useSpecial`), never a rich
domain player or raw engine object.

## Example object interaction

```ts
onItemOnObject(536, 409, ({ player }) => {
  const quest = player.quest("dragon-awakens");
  if (quest === null || quest.state() !== "in_progress"
      || quest.stage() !== 2) return;

  if (!player.getInventory().remove(536, 1)) return;
  quest.advance(2);
});
```

Executable object and NPC registrations use ordinal interaction keys such as
`"first"` rather than semantic labels such as `"open"`, and their callback
receives a single context object.

## Example raid registration

```ts
defineRaid({
  id: "temple_of_zaros",
  command: "temple-of-zaros",
  bounds: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4711, plane: 1 },
  muster: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4695 },
  entrance: { x: 2268, y: 4690, plane: 1 },
  minPlayers: 2,
  maxPlayers: 5,
  timeLimitTicks: 7200,
  rewards: ["zaros_raid_reward"],
  rooms: [
    {
      id: "guardian",
      name: "Hall of Guardians",
      bounds: { minX: 2264, minY: 4688, maxX: 2275, maxY: 4695, plane: 1 },
      onEnter(ctx) { ctx.announce("Ancient guardians stir..."); },
      onTick(ctx) {
        return ctx.elapsedTicks() >= 3
          ? { status: "completed" }
          : { status: "in_progress" };
      },
      onComplete(ctx) { ctx.announce("The path opens forward."); },
    },
    {
      id: "crypt",
      name: "Crypt of the Fallen",
      bounds: { minX: 2264, minY: 4696, maxX: 2287, maxY: 4711, plane: 1 },
      onEnter(ctx) { ctx.announce("A fallen priest rises!"); },
      onTick() { return { status: "in_progress" }; },
      onComplete(ctx) { ctx.announce("The crypt falls silent."); },
      boss: { bossId: "dragon-king" },
    },
  ],
});
```

Every declarative definition family is now a consumed runtime: quests are
executable through `player.quest(id)`, named drops/rewards through
`defineDropTable`/`defineReward`, bosses through the WP3 boss runtime,
areas/shops through the WP4 area runtime, raids through the WP5 raid
runtime, and gathering resources through the WP8 resource runtime.
The imperative Phase 4 encounter API remains available for
custom content: `player.beginEncounter(...)` returns a handle for spawns,
layered object replacement, scheduled tasks, deterministic `rollDrops`,
and owned-death callbacks. Locks and camera sessions are acquired through
the player facades (`getMovement()` / `getActions()` /
`getPresentation()`), not the encounter handle. The shipped production
proofs are `content/src/bosses/encounter-warden.ts`,
`content/src/bosses/dragon-king.ts`,
`content/src/areas/dragon_island/`,
`content/src/resources/woodcutting.ts`, and
`content/src/raids/temple-of-zaros/raid.ts` (all imported from
`loader.ts`) — see [SCRIPT_BRIDGE.md](./SCRIPT_BRIDGE.md) for the
fixtures and contracts.

## Example gathering resource

```ts
defineGatheringResource({
  id: "tree",
  name: "Tree",
  objectId: 1276,
  action: "first",
  skill: "woodcutting",
  level: 1,
  tools: [{ itemId: 1351 }], // Bronze axe (inventory or equipped)
  animation: 879,
  intervalTicks: 4,
  successChance: { numerator: 3, denominator: 4 },
  rewards: [{ itemId: 1511, amount: 1 }], // Logs
  experience: 25,
  depletedObjectId: 1341, // Stump
  respawnTicks: 4,
});
```

The resource is parsed into an immutable Java-owned descriptor and its exact
object-id/action key becomes a Java-owned host route. A live player clicking
the object validates skill level and an ordered tool, then opens a bounded
per-player session that animates on a tick loop, rolls the deterministic
resource-session RNG, and on success commits the item and XP atomically,
depletes the object to the declared empty id, and restores it after the
respawn interval. Every stop path (movement away, logout, death, reload,
failure) cancels the session with zero residue. Use the public
`createGatheringResource`/`registerGatheringResource` SDK builders to
register one.

## Operator diagnostics

Administrators (rights 2 and above) inspect and reload the runtime through
`::scripts status`, `::scripts list [kind] [page]`, and `::scripts reload`
(`::reload` and the deprecated, sanitized `::scriptdir` alias route the same
way). These commands report only bounded logical state — active generation,
module/definition/route counts, live runtime sessions, and the last rejected
reload reason — never host paths or raw engine objects. See "Operator
diagnostics and admin control (WP9)" in [SCRIPT_BRIDGE.md](./SCRIPT_BRIDGE.md).

## Current scripted quest

`content/src/quests/dragon-awakens.ts` is the shipped end-to-end quest. It uses
the production Wilderness route: Chronozon (`667`) at `(3156, 3704, 0)`,
production green dragons (`941`) and their guaranteed dragon-bones drop
(`536`), and the cache altar (`409`) at `(3243, 3207, 0)`. Dialogue,
successful pickup, item-on-object, NPC death, login, save/load, and content
reload all use public bridge entry points.

Completion is retryable and atomic. It awards 3 quest points, 1,000 coins, and
1,000 Magic XP only when every reward fits. Rewards never fall back to the
bank; full inventory, the 200,000,000 XP cap, quest-point overflow, or a
transaction failure leaves the final stage in progress.

Each quest stage also drives the generic quest journal: `player.quest(id)
.objective()` projects the current stage text (or the stable completion
summary), the scripted quest row appears in the legacy quest tab with the
standard color scheme, and clicking it opens the generic detail interface
with name, summary, requirements, state, and objective. See "Scripted quest
journal" in [SCRIPT_BRIDGE.md](./SCRIPT_BRIDGE.md).

## Public TypeScript content SDK

Content is authored against the public SDK barrel
(`content/src/sdk/index.ts`, re-exported by `content/src/index.ts`).
Builders validate exact bounds, emit canonical schema-v1 definitions, and
deep-freeze all arrays/maps; helpers cover requirements, named rewards,
scripted shops, equipment slots, dialogue frames, cutscene sessions, and
gathering resources.
The Lumbridge man example and all shipped fixtures import only from the
barrel. The generated `docs/API_INVENTORY.md` lists every runtime global
and SDK export; run `pnpm --filter @singlescape/content test` to run the
SDK unit tests (Node's built-in runner, no new dependencies) and verify
the inventory is current. See "Public TypeScript content SDK (WP7)" in
[SCRIPT_BRIDGE.md](./SCRIPT_BRIDGE.md) for the full contract.

## Representative vertical content (Phase 5)

The shipped content pack is the vertical proof that every declarative family
affects real gameplay together. All eight production fixtures register as
source-aware content modules through the SDK barrel:

| Module | Defines |
|--------|---------|
| `dragon-island-drops` | The named drop tables (`dragon_guardian_loot`, `elder_wizard_loot`, `dragon_king_loot`, `ancient_chest_loot`) |
| `dragon-king` | The standalone Dragon King boss (command entry, phases, fire-wave special, named drops) |
| `encounter-warden` | The King Black Dragon boss (entry locks/camera, barrier, phased adds, dragonfire, private drops) |
| `dragon-awakens` | The persisted multi-stage quest with the generic journal |
| `temple-of-zaros` | The two-player raid with the embedded dragon-king boss and roster reward |
| `dragon-island-shops` | The scripted island general store |
| `dragon-island` | The activated Crandor area referencing the shop, quest, boss, and raid by id |
| `woodcutting-resources` | The tree and oak gathering resources |

`VerticalContentE2ETest` loads the compiled `content/dist` loader and crosses
the manifest, area, shop, gathering, boss, quest, rejected reload, and
successful reload in one flow. See
[typescript-content-authoring.md](./typescript-content-authoring.md) to create
new content in this pattern and
[typescript-content-migration.md](./typescript-content-migration.md) to move
legacy content onto it.

## Development objective

The ideal end state:

> Java is the engine. TypeScript is the game.

Deep engine work will still require Java, but normal content development should not.
