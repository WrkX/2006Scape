# TypeScript Content Bridge

> **Canonical contract:** See
> [`../../docs/SCRIPT_BRIDGE.md`](../../docs/SCRIPT_BRIDGE.md) for the current
> runtime surface, author contract (`ScriptedPlayer` only), entity ID ceilings,
> and reload semantics. See [`../../docs/API_INVENTORY.md`](../../docs/API_INVENTORY.md)
> for the generated global/SDK inventory. This file is a legacy server-local
> quickstart and may omit newer content-platform features.

The TypeScript bridge lets you write game content (NPCs, objects, quests, commands) in TypeScript/JavaScript instead of Java. It runs on **GraalJS** — a high-performance JavaScript runtime embedded in the JVM — and supports full ES module syntax.

## How it works

```
TypeScript source (.ts)     → tsc compiles to → content/dist/*.js
                                                  ↓
                                   GraalJS context (ScriptHost)
                                                  ↓
                                   Registers handlers in Java registries
                                                  ↓
                                   Java game engine dispatches to handlers
```

The server loads scripts from the root workspace's `content/dist/` directory. The
path can be set with the `singlescape.contentDir` JVM property or the
`SINGLESCAPE_CONTENT_DIR` environment variable. If a `loader.js` exists it's
used as the entry point (imports handle the rest). Otherwise every `.js` file is
evaluated in alphabetical order.

**Boot:** `GameEngine.java` calls `ScriptHost.getInstance().load()` at startup.

**Hot-reload:** The `::scripts` command builds and evaluates a candidate
context and registry set, then swaps it in atomically. If module loading fails, the
candidate is discarded and the last-known-good scripts continue running. Use
`::scriptdir` to show the content path.

Successful reloads also advance the script generation. Player-owned scheduled
callbacks from the previous generation are invalidated before its Graal
context closes; a failed candidate leaves the active generation and its tasks
untouched.

## Content iteration workflow

Use this loop while authoring or porting content:

1. **Edit** TypeScript under `content/src/`.
2. **Compile** with `pnpm build:content` from the repo root (or run
   `pnpm watch` in another terminal to rebuild on save).
3. **Reload** in-game as an administrator with `::scripts reload` (or
   `::reload`).
4. **Inspect** with `::scripts status` — generation, module/definition/route
   counts, runtime session totals, the last rejected-reload reason, and any
   reload quarantine or diagnostic warnings.

A failed reload keeps the last-known-good generation live. Fix the compile or
registration error, rebuild, and reload again. Duplicate route keys fail the
candidate with the exact route identity (for example `command:dup` or
`object:409/first`) in the error message.

`pnpm watch` only recompiles TypeScript; you still need `::scripts reload` to
load the new `content/dist/` output into the running server.

## Globals available in scripts

These are injected by `ScriptBindings.java` and available in any script module.

### defineBoss(def: BossDef): void
Register a boss definition by ID.

```ts
defineBoss({
  npcId: 50,               // canonical NPC/registry ID
  combatLevel: 276,
  maxHitpoints: 240,
  onSpawn: ctx => {},
  onTick: ctx => {},
  onDeath: ctx => {}
});
```

`npcId` is the canonical boss identity field on both sides of the bridge.

### defineQuest(def: QuestDef): void
Register a quest definition by its stable string `id`.

```ts
defineQuest({
  id: "cooks_assistant",
  name: "Cook's Assistant",
  // quest-specific fields
});
```

### defineRaid(def: RaidDef): void
Register one complete raid definition. The bridge accepts a single definition
object; the former `defineRaid(id, def)` form is not supported.

```ts
defineRaid({
  id: "barbarian_assault",
  // raid-specific fields
});
```

### defineArea(def: AreaDef): void
Register an area definition by its stable string `id`.

```ts
defineArea({
  id: "falador",
  name: "Falador",
  // area boundary, spawns, etc.
});
```

Boss, quest, raid, and area globals currently populate data-only registries.
Their callback-shaped fields are retained as definition data; the bridge does
not invoke them as gameplay lifecycle hooks.

### onNpc(npcId: number, action: NpcAction, handler: NpcHandler): void
Register a handler for an NPC interaction. `action` is one of `"first"`, `"second"`, `"third"` (corresponding to right-click options). The handler receives a `ScriptContext` with `player` and `target` fields.

```ts
onNpc(1, "first", ctx => {
  const { player, target } = ctx;
  player.message("Hello, " + (target?.getName() ?? "traveler") + "!");
});
```

```ts
onNpc(2, "third", ctx => {
  const p = ctx.player;
  p.teleport(3222, 3218);
});
```

### onObject(objectId: number, action: ObjectAction, handler: ObjectHandler): void
Register a handler for an object interaction. `action` is exactly `"first"`,
`"second"`, `"third"`, or `"fourth"`, corresponding to the object's
interaction slots. Semantic option text such as `"open"`, `"chop"`, or
`"search"` is not an action key.

```ts
onObject(2213, "first", ctx => {
  ctx.player.message("You open the bank booth.");
});
```

### onCommand(name: string, handler: CommandHandler): void
Register a player command handler.

```ts
onCommand("heal", ctx => {
  const p = ctx.player;
  p.getSkills().setLevel(3, p.getSkills().getLevel(3) + 50); // heal HP
});
```

### dev.log(message: string): void
Print a message to the server console, prefixed with `[script]`.

```ts
dev.log("Script loaded: tutorial island npcs");
```

### log(message: string): void
Same as `dev.log`. Shortcut without the `dev.` namespace.

### Scheduling and lifecycle globals

`ScriptedPlayer.after(ticks, handler)` schedules one callback, while
`ScriptedPlayer.every(ticks, handler)` repeats it. Ticks are bounded to
`1..100000`, callbacks receive a cancellable `ScriptTaskHandle`, and repeating
work stops on callback failure. Work is cancelled at successful explicit
logout/terminal removal and on successful script reload.

The bounded lifecycle globals are `onLogin`, `onLogout`,
`onNpcDeath(npcId, handler)`, `onItemPickup(itemId, handler)`,
`onEnterArea(area, handler)`, and `onLeaveArea(area, handler)`. Areas are
inclusive rectangles with a stable string `id`, four bounds, and an optional
plane. Lifecycle callbacks observe established engine transitions; they do not
replace legacy behavior. Reloads baseline online area membership without
emitting synthetic enter/leave events.

## ScriptContext

Every runtime handler receives exactly one `ScriptContext` object:

| Field | Type | Description |
|---|---|---|
| `player` | `ScriptedPlayer` | The player who triggered the interaction |
| `target` | `ScriptedNpc \| ScriptedObject \| null` | The target, or `null` for a command |
| `action` | `string` | The action string (e.g. `"first"`) |

This runtime contract is intentionally separate from the broader `Player`
domain interface in `content/src/core/player.ts`. That interface models
aspirational gameplay APIs and is not the host object received from GraalJS.
Use the bridge-specific `ScriptContext`, `ScriptedPlayer`, `ScriptedNpc`, and
`ScriptedObject` declarations in `content/src/core/runtime.ts` for executable
handlers.

## ScriptedPlayer

The `ctx.player` object exposes methods on the interacting player.

### Properties

| Method | Returns | Description |
|---|---|---|
| `getUsername()` | `string` | Player's username |
| `getX()` | `number` | Absolute X coordinate |
| `getY()` | `number` | Absolute Y coordinate |
| `getPlane()` | `number` | Height level (0-3) |
| `getCombatLevel()` | `number` | Player's combat level |
| `getPosition()` | `ScriptedPosition` | `{x, y, plane}` position object |

### Methods

| Method | Description |
|---|---|
| `message(text)` | Send a chat box message to the player |
| `teleport(x, y)` | Move player to surface-level coords |
| `teleport(x, y, plane)` | Move player with height level |
| `getSkills()` | Returns a `SkillView` (see below) |
| `getInventory()` | Returns an `InventoryView` (see below) |
| `getBank()` | Returns a `BankView` (see below) |
| `getDialogue()` | Returns a `ScriptedDialogue` builder (see below) |

### SkillView (player.getSkills())

| Method | Description |
|---|---|
| `getLevel(id)` | Get current level (0 = attack, 1 = defence, ..., 3 = hitpoints) |
| `setLevel(id, lvl)` | Set current level and refresh the client |

Skill IDs match the RuneScape convention: 0=Attack, 1=Defence, 2=Strength, 3=Hitpoints, 4=Ranged, 5=Prayer, 6=Magic, 7=Cooking, 8=Woodcutting, 9=Fletching, 10=Fishing, 11=Firemaking, 12=Crafting, 13=Smithing, 14=Mining, 15=Herblore, 16=Agility, 17=Thieving, 18=Slayer, 19=Farming, 20=Runecrafting.

### InventoryView (player.getInventory())

Item methods accept either an integer `id` (item ID) or a `string` `name` (resolved via `DeprecatedItems`):

| Method | Description |
|---|---|
| `add(idOrName, amount)` | Add item(s) to inventory. Returns boolean. |
| `remove(idOrName, amount)` | Remove item(s) from inventory |
| `has(idOrName, amount)` | Check if player has at least N of item |
| `count(idOrName)` | Count how many of item player has |

### BankView (player.getBank())

Same API pattern as InventoryView:

| Method | Description |
|---|---|
| `add(idOrName, amount)` | Add item(s) to bank |
| `remove(idOrName, amount)` | Remove item(s) from bank |
| `has(idOrName, amount)` | Check if bank has at least N of item |
| `count(idOrName)` | Count how many of item in bank |

## ScriptedDialogue (player.getDialogue())

A fluent dialogue builder. Every method returns `this` so calls chain naturally. Call `.end()` to close.

### NPC chat

```ts
player.getDialogue()
  .npc(1, "Hello " + player.getUsername() + "!")
  .npc(1, "Welcome to the server.", "How can I help you?")
  .end();
```

Variants: `.npc(npcId, line)`, `.npc(npcId, line1, line2)`, `.npc(npcId, line1, line2, line3)`, `.npc(npcId, line1, line2, line3, line4)`.

### Player chat

```ts
player.getDialogue()
  .player("I'm looking for a quest.")
  .end();
```

Variants: `.player(line)`, `.player(line1, line2)`, `.player(line1, line2, line3)`, `.player(line1, line2, line3, line4)`.

### Statement (information box)

```ts
player.getDialogue()
  .statement("You found a hidden treasure!")
  .end();
```

Variants: `.statement(line)`, `.statement(line1, line2)`, `.statement(line1, line2, line3)`, `.statement(line1, line2, line3, line4)`.

### Options (player choice)

Requires 2–5 option strings and a callback that receives the zero-based choice
index (`0` for the first option and `lines.length - 1` for the last):

```ts
player.getDialogue()
  .statement("What would you like to do?")
  .options(["Buy supplies", "Sell items", "Nothing"], choice => {
    if (choice === 0) player.message("You buy supplies.");
    else if (choice === 1) player.message("You sell items.");
    else player.message("Maybe next time.");
  });
```

### Item dialogue

```ts
player.getDialogue()
  .itemDialogue(995, "Here's your reward!", ["100 gold coins"])  // itemId, header, lines
  .end();
```

## ScriptedNpc (the target in onNpc handlers)

| Method | Returns | Description |
|---|---|---|
| `getId()` | `number` | NPC type ID |
| `getName()` | `string` | NPC name from definitions |
| `getX()` | `number` | Absolute X |
| `getY()` | `number` | Absolute Y |
| `getPlane()` | `number` | Height level |
| `getHp()` | `number` | Current hitpoints |
| `getMaxHp()` | `number` | Maximum hitpoints |
| `isDead()` | `boolean` | Whether the NPC is dead |
| `getCombatLevel()` | `number` | Combat level |
| `forceChat(text)` | `void` | Make the NPC say something |
| `getPosition()` | `ScriptedPosition` | `{x, y, plane}` |

## ScriptedObject (the target in onObject handlers)

| Method | Returns | Description |
|---|---|---|
| `getId()` | `number` | Object ID |
| `getX()` | `number` | Tile X |
| `getY()` | `number` | Tile Y |
| `getPlane()` | `number` | Height level from the resolved clicked object |
| `getName()` | `string` | Object name from definitions |
| `getPosition()` | `ScriptedPosition` | `{x, y, plane}` |

Object type and rotation are intentionally not exported: the legacy click
pipeline cannot determine them reliably for every dynamic object, so exposing
guessed values would make runtime predicates unsafe.

## Scripting primer: complete example

```ts
// content/src/quests/cooksAssistant.ts (compiles to content/dist/quests/cooksAssistant.js)

defineQuest({
  id: "cooks_assistant",
  name: "Cook's Assistant",
  started: false,
  stages: ["start", "find_eggs", "find_milk", "find_flour", "reward"],
});

onNpc(462, "first", ctx => {
  const p = ctx.player;

  if (!p.getInventory().has(1944, 1)) {
    p.getDialogue()
      .npc(462, "Oh dear, oh dear!", "I need eggs for my cake!")
      .npc(462, "Could you fetch some from the farm?")
      .options(["Sure!", "Not now"], choice => {
        if (choice === 0) {
          p.message("You accept the quest.");
        }
      });
  } else {
    p.getDialogue()
      .npc(462, "Thank you!")
      .end();
  }
});
```

## Dev commands

| Command | Description |
|---|---|
| `::scripts` | Transactionally load and activate a fresh script context |
| `::scriptdir` | Print the content directory path |

## Registries (Java-side reference)

These are the Java backing stores that content scripts populate:

| Registry | Backed by | Register with |
|---|---|---|
| `NpcHandlerRegistry` | `Map<npcId, Map<action, handler>>` | `onNpc(id, action, fn)` |
| `ObjectHandlerRegistry` | `Map<objectId, Map<action, handler>>` | `onObject(id, action, fn)` |
| `BossRegistry` | `Map<npcId, definition>` | `defineBoss(def)` |
| `QuestRegistry` | `Map<id, definition>` | `defineQuest(def)` |
| `RaidRegistry` | `Map<id, definition>` | `defineRaid(def)` |
| `AreaRegistry` | `Map<id, definition>` | `defineArea(def)` |
| `commandHandlers` | `Map<commandName, handler>` | `onCommand(name, fn)` |

NPC, object, and command registries contain executable runtime handlers.
Boss, quest, raid, and area registries are data-only: registration validates
and stores definitions, but the bridge does not execute their lifecycle hooks
or provide gameplay consumers for them.

Reload uses isolated candidate registries rather than clearing the live maps
in place. A successful swap fully replaces the old state; a failed reload
leaves all last-known-good handlers and definitions operational.

## Quick-start example

Runnable examples are imported through `content/src/examples/index.ts`. After
logging in, try these:

**1. Player command — type `::hello` in chat:**

```ts
onCommand("hello", ctx => {
  const p = ctx.player;
  p.message("Hello, " + p.getUsername() + "! The script bridge works!");
});
```

Expected: a chat message greets you by name.

**2. NPC interaction — right-click a Man (id=1) in Lumbridge → "Talk-to":**

```ts
onNpc(1, "first", ctx => {
  const { player, target } = ctx;
  player.getDialogue()
    .npc(1, "Hello there, " + player.getUsername() + "!")
    .player("Greetings!")
    .npc(1, "The TypeScript bridge is working perfectly.")
    .end();
});
```

Expected: a 3-panel NPC dialogue. The action `"first"` maps to the first right-click option ("Talk-to" for most NPCs). `"second"` and `"third"` map to the second and third options.

**3. Object interaction — click a Bank booth (id=2213) in Lumbridge castle:**

```ts
onObject(2213, "first", ctx => {
  const { player } = ctx;
  player.message("You examine the bank booth carefully.");
  player.getDialogue()
    .statement("Bank Booth — intercepted by the script bridge!")
    .end();
});
```

Expected: a chat message and dialogue. The script handler takes priority over the Java default.

**4. Reload scripts at runtime** — type `::scripts` after editing. No server restart needed.

### Project setup

```
content/             # Root workspace directory
├── dist/            # Compiled JS output (loaded by the server)
│   ├── loader.js   # Preferred deterministic entry point
│   └── examples/
├── src/             # TypeScript source files
│   ├── core/runtime.ts # Runtime bridge declarations
│   ├── index.ts    # SDK barrel
│   ├── examples/
│   └── ...
├── tsconfig.json
└── package.json
```

The root launcher sets `SINGLESCAPE_CONTENT_DIR` to the absolute
`content/dist/` path. When launched directly from `engine/server/`,
the default `../../content/dist` resolves to the same root output.

The source entry point, `content/src/loader.ts`, imports the shipped boss,
quest, raid, area, NPC, and command modules. New content must be reachable
from this import graph to register when `loader.js` is evaluated.

A minimal `tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "ES2020",
    "moduleResolution": "bundler",
    "outDir": "dist",
    "rootDir": "src",
    "strict": true
  }
}
```

### Type declarations

The repository's bridge-specific runtime declarations live in
`content/src/core/runtime.ts` and are re-exported by the SDK barrels. Keep
these declarations aligned with the exported Java wrapper members; do not
type runtime handlers with the broader domain `Player`.

```ts
declare function onCommand(
  name: string,
  handler: (ctx: ScriptContext) => void
): void;

declare function onNpc(
  npcId: number,
  action: "first" | "second" | "third",
  handler: (ctx: ScriptContext) => void
): void;

declare function onObject(
  objectId: number,
  action: "first" | "second" | "third" | "fourth",
  handler: (ctx: ScriptContext) => void
): void;

// ... see content/src/core/runtime.ts for the full declarations
```

### Building

```bash
cd content
npm install
npx tsc
```

Then launch the server. Scripts load automatically at startup.

### Testing checklist

| Test | How | Expected |
|---|---|---|
| `::scripts` | Type in chat | Server logs "Loaded N script modules" |
| `::scriptdir` | Type in chat | Shows absolute content dir path |
| `::hello` | Type in chat | Chat message greets you |
| Talk-to Man | Right-click any Man in Lumbridge | 3-panel NPC dialogue |
| Hot-reload | Edit a module imported by `content/src/loader.ts`, rebuild, `::scripts` | Changes apply without restart |

## Notes

- Script handlers receive the raw GraalJS `Value` for the function, wrapped in Java's `ProxyExecutable`. TypeScript compiles to JS that GraalJS can evaluate directly.
- Runtime callbacks execute through a shared guard. Failures are logged with
  handler category, registration identity, and action and are contained at the
  packet/dialogue/game-loop boundary. Use `dev.log()` or `log()` for content
  diagnostics.
- GraalJS uses an explicit export-only host policy. Host class lookup, process
  execution, sockets/network access, native access, and guest thread creation
  are unavailable.
- ES-module resolution has read-only access beneath the configured content
  directory. Scripts cannot read outside that root or write files.
- Reload evaluates into isolated candidate state and swaps only after the
  complete loader succeeds, preventing stale or closed-context handlers.
