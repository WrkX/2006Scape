# TypeScript Bridge Design

Phases 2 and 3 of the TypeScript content-platform work wired the existing
TypeScript SDK into the Java engine via a GraalVM JS context and added
Java-owned persistent player state plus an executable quest service. Phase 4
adds bounded world and encounter services: exact interaction routes for
buttons, magic, ground items, item-on-player, and player death; encounter
ownership with arena reservation; truthful player/NPC facades; composable
camera sessions and locks; a deterministic encounter RNG with transactional
drop tables; and a production boss authored entirely in TypeScript. Phase 5
converts the declarative definitions into consumed runtimes: named drop
tables and rewards, declarative bosses, activated areas with scripted
shops, and a full raid runtime with a bounded lobby, an embedded boss, and
a roster-wide atomic reward barrier.

## Goals

1. Load `content/dist/**/*.js` (ES2020 modules built by `tsc`) at server start.
2. Expose a small, stable Java Game API to the script context.
3. Let scripts register object / NPC / command handlers and boss / quest /
   raid / area / shop / drop / reward definitions, grouped into logical
   content modules.
4. Keep all existing engine behaviour intact when no scripts are loaded.
5. Make hot-reload transactional: a failed candidate load must leave the
   previous context and registrations usable.
6. Expose only the bridge wrappers required by content; scripts do not get
   general JVM, process, socket, thread, native, or filesystem access.

## Runtime: GraalVM JS (community edition)

Server runs Java 17. Nashorn is unavailable. We use:

```xml
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>polyglot</artifactId>
    <version>23.1.0</version>
</dependency>
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>js-community</artifactId>
    <version>23.1.0</version>
    <type>pom</type>
</dependency>
```

`Source` is built with `mimeType("application/javascript+module")` so `import`
statements in `content/dist/**/*.js` resolve as ES modules.

Source compatibility: stay on `source=8 target=8` for the existing code.
GraalVM API calls from bridge code are written in Java 8 syntax.

### Offline install

`js-community` is a POM dependency that brings in the GraalJS runtime. For an
offline build, pre-populate the local Maven repository with its dependency set
or place the matching artifacts in `engine/server/libs/`:

```
engine/server/libs/polyglot-23.1.0.jar
engine/server/libs/js-community-23.1.0.jar
engine/server/libs/collections-23.1.0.jar
engine/server/libs/word-23.1.0.jar
engine/server/libs/nativeimage-23.1.0.jar
```

The pom's `libs-local` repository can then resolve the local artifacts.

## Layout

```text
engine/server/src/main/java/com/rs2/script/
├── ScriptHost.java           (singleton, owns Context, load() / reload())
├── ScriptBindings.java       (install(Context) — exposes globals to JS)
├── ScriptFunctions.java      (Java functional facades for the globals)
├── ScriptedPlayer.java       (per-call wrapper around Player)
├── ScriptedNpc.java
├── ScriptedObject.java
├── ScriptedPosition.java
├── ScriptedDialogue.java     (dialogue builder + pending option callback)
├── ScriptArray.java          (immutable copied array surface)
├── ScriptContext.java        (immutable per-handler-invocation context)
├── CommandScriptContext.java (+ other root-package route contexts)
├── ScriptExecutor.java       (guarded guest-callback execution)
├── ScriptLifecycleService.java
├── ReadOnlyContentFileSystem.java
├── context/                  (Phase 4 route contexts: button, item-on-ground,
│                              item-on-player, magic-on-item/object, player-death)
├── snapshot/                 (immutable ScriptPlayerSnapshot/ScriptNpcSnapshot)
├── capability/               (equipment, combat, movement, presentation, camera)
├── quest/
│   ├── QuestDefinition.java  (immutable Java-owned descriptor)
│   ├── QuestDefinitionParser.java
│   ├── QuestService.java
│   ├── QuestRewardTransaction.java
│   └── ScriptedQuest.java
├── boss/                     (WP3 declarative boss runtime: descriptor,
│                              parser, registry, controller, standalone service)
├── area/                     (WP4 declarative area runtime: descriptor,
│                              parser, registry, session RNG, area runtime)
├── shop/                     (WP4 scripted-shop definitions and runtime)
├── drop/                     (WP2 named drop tables and the owner-neutral
│                              transactional drop roll)
├── reward/                   (WP2 named rewards, the player-local transaction,
│                              and the WP5 roster-wide transaction)
├── raid/                     (WP5 declarative raid runtime: descriptor,
│                              parser, registry, lobby/session runtime,
│                              raid-session RNG)
├── scheduler/
│   └── ScriptScheduler.java
├── state/
│   ├── ScriptStateStore.java
│   ├── ScriptStateCodec.java
│   └── PlayerStateNamespace.java
├── registries/
│   ├── InteractionHandlerRegistry.java  (Phase 4 button/ground/magic/death routes)
│   ├── ObjectHandlerRegistry.java
│   ├── NpcHandlerRegistry.java
│   ├── ItemHandlerRegistry.java
│   ├── CommandHandlerRegistry.java
│   ├── LifecycleRegistry.java
│   ├── BossRegistry.java
│   ├── QuestRegistry.java
│   ├── RaidRegistry.java
│   ├── AreaRegistry.java
│   ├── RegistryStore.java
│   └── ScriptArea.java
└── world/                    (encounter ownership, NPC/object/ground handles,
    ├── ScriptEncounterService.java    reservation, locks, camera, RNG, drops)
    ├── ScriptEncounterHandle.java
    ├── ScriptNpcService.java / ScriptNpcHandle.java
    ├── ScriptObjectHandle.java
    ├── ScriptGroundItemHandle.java
    ├── ScriptEncounterRng.java
    ├── ScriptDropEntryParser.java / ScriptDropResult.java
    └── ScriptPlayerDeathTicket.java
```

## Java → JS global functions

| Global | Signature | Purpose |
|---|---|---|
| `defineBoss(def)` | `(BossDefinition) => void` | parse and register a canonical boss consumed by the WP3 boss runtime |
| `defineQuest(def)` | `(QuestDefinition) => void` | parse and register a Java-owned quest descriptor |
| `defineRaid(def)` | `(RaidDefinition) => void` | parse and register a canonical raid consumed by the WP5 raid runtime |
| `defineArea(def)` | `(AreaDefinition) => void` | parse and register a canonical area activated by the WP4 area runtime |
| `defineShop(def)` | `(ShopDefinition) => void` | parse and register a Java-owned scripted shop with declared stock/prices/restock |
| `defineDropTable(def)` | `(DropTableDefinition) => void` | parse and register a Java-owned named drop table |
| `defineReward(def)` | `(RewardDefinition) => void` | parse and register a Java-owned named reward |
| `defineGatheringResource(def)` | `(GatheringResourceDefinition) => void` | parse and register a canonical gathering resource consumed by the WP8 resource runtime |
| `defineProcessingSkill(def)` | `(ProcessingSkillDefinition) => void` | parse and register a canonical processing skill (item-on-object cook/smith loop) |
| `defineMob(def)` | `(MobDefinition) => void` | parse and register a canonical world mob; registered `npcId` suppresses `NpcCombat` |
| `defineItemOverlay(def)` | `(ItemOverlayDefinition) => void` | merge item metadata/equipment stats over a loaded cache item id at activation |
| `defineNpcOverlay(def)` | `(NpcOverlayDefinition) => void` | merge NPC name/combat stats over a loaded cache NPC id at activation |
| `defineObjectOverlay(def)` | `(ObjectOverlayDefinition) => void` | merge object name/examine/actions over a loaded cache object id at activation |
| `onObject(id, action, handler)` | `(number, "first" \| "second" \| "third" \| "fourth", fn) => void` | object click by interaction slot |
| `onNpc(id, action, handler)` | `(number, "first" \| "second" \| "third", fn) => void` | NPC click by interaction slot |
| `onCommand(name, fn)` | `(string, fn) => void` | register a player command |
| `onItem(id, action, handler)` | `(number, "first" \| "second" \| "third", fn) => void` | inventory item click by interaction slot |
| `onItemOnItem(firstId, secondId, handler)` | `(number, number, fn) => void` | symmetric exact item-pair handler |
| `onItemOnObject(itemId, objectId, handler)` | `(number, number, fn) => void` | exact item-on-object handler |
| `onItemOnNpc(itemId, npcId, handler)` | `(number, number, fn) => void` | exact item-on-NPC handler |
| `onButton(buttonId, handler)` | `(number, fn) => void` | sparse decodable `u8*1000+u8` button key |
| `onItemOnGroundItem(itemId, groundItemId, handler)` | `(number, number, fn) => void` | exact item-on-ground-item handler |
| `onItemOnPlayer(itemId, handler)` | `(number, fn) => void` | exact item-on-player handler |
| `onMagicOnItem(spellId, itemId, handler)` | `(number, number, fn) => void` | exact magic-on-item handler |
| `onMagicOnObject(spellId, objectId, handler)` | `(number, number, fn) => void` | exact magic-on-object handler |
| `onMagicOnNpc(spellId, npcId, handler)` | `(number, number, fn) => void` | exact magic-on-NPC-type handler |
| `onMagicOnPlayer(spellId, handler)` | `(number, fn) => void` | exact magic-on-player spell handler |
| `onPlayerDeath(handler)` | `(fn) => void` | observe one completed player death per transition |
| `onLogin(handler)` | `(fn) => void` | observe completed player initialization |
| `onLogout(handler)` | `(fn) => void` | observe terminal player removal |
| `onNpcDeath(npcId, handler)` | `(number, fn) => void` | observe one exact NPC type after legacy death bookkeeping |
| `onItemPickup(itemId, handler)` | `(number, fn) => void` | observe one exact successfully transferred ground item |
| `onEnterArea(area, handler)` | `(ScriptAreaDescriptor, fn) => void` | observe entry into an inclusive rectangle |
| `onLeaveArea(area, handler)` | `(ScriptAreaDescriptor, fn) => void` | observe exit from the same rectangle |
| `registerContentModule(descriptor, scope)` | `({ id, schemaVersion, onLoad?, onUnload? }, () => void) => void` | open a bounded logical module scope; every registration inside it carries the module id and declared schema version |
| `dev` | object | dev console: `dev.log(msg)` |
| `log(msg)` | `(string) => void` | write to server stdout |

Runtime handlers receive exactly one bridge context object. Most interaction
and lifecycle contexts extend this common shape:

```text
readonly player: ScriptedPlayer
readonly target: ScriptedNpc | ScriptedObject | null
readonly action: string
```

NPC-death observation is the deliberate exception: its one argument contains
`npc`, nullable `killer`, `position`, and `action: "death"` because a death may
have no player killer. `ScriptContext`-shaped contexts and the `Scripted*`
wrappers are the Graal boundary contract.
They are deliberately distinct from the richer `Player` domain interface in
`content/src/core/player.ts`. That domain interface and `content/src/core/bot.ts`
are aspirational design sketches and are **not** injected by the host. Runtime
content must use only the members declared by the bridge-specific types in
`content/src/core/runtime.ts` and implemented by the Java wrappers.

### Entity ID ceilings

| Kind | Inclusive max id | Source |
|------|------------------|--------|
| Item | `65535` | `ItemConstants.ITEM_LIMIT - 1` / `ScriptEntityLimits.MAX_ITEM_ID` |
| NPC type | `65535` | `ScriptEntityLimits.MAX_NPC_ID` |
| Object type | `65535` | `ScriptEntityLimits.MAX_OBJECT_ID` |

TypeScript mirrors these in `content/src/core/limits.ts`. Raising the ceiling
does not invent cache definitions — ids must still exist in the loaded cache.

## ScriptedPlayer surface (`ctx.player`)

```text
getUsername(): string
getX(): number
getY(): number
getPlane(): number
getCombatLevel(): number
getSkills(): {
  getLevel(id: number): number
  getCurrentLevel(id: number): number
  getBaseLevel(id: number): number
  getExperience(id: number): number
  addExperience(id: number, amount: number): boolean
  setLevel(id: number, lvl: number): void
}
getInventory(): {
  add(item: number | string, amount: number): boolean
  canRemove(item: number | string, amount: number): boolean
  remove(item: number | string, amount: number): boolean
  has(item: number | string, amount: number): boolean
  count(item: number | string): number
  getCapacity(): number
  getFreeSlots(): number
}
getBank(): {
  add(item: number | string, amount: number): void
  remove(item: number | string, amount: number): boolean
  has(item: number | string, amount: number): boolean
  count(item: number | string): number
  getCapacity(): number
}
getDialogue(): ScriptedDialogue
getPosition(): ScriptedPosition
message(text: string): void
teleport(x: number, y: number, plane?: number): void
getRights(): number
animate(animationId: number): boolean
graphic(graphicId: number): boolean
sound(soundId: number): boolean
closeInterfaces(): void
showInterface(interfaceId: number): boolean
after(ticks: number, callback: (handle: ScriptTaskHandle) => void): ScriptTaskHandle
every(ticks: number, callback: (handle: ScriptTaskHandle) => void): ScriptTaskHandle
state(namespace: string): PlayerStateNamespace
quest(id: string): ScriptedQuest | null
questPoints(): number
getEquipment(): ScriptedEquipment
getCombat(): ScriptedCombat
getMagic(): ScriptedMagic
getPrayer(): ScriptedPrayer
getMovement(): ScriptedMovement
getActions(): ScriptedActions
getPresentation(): ScriptedPresentation
openBank(): boolean
beginEncounter(id, minX, minY, maxX, maxY, plane): ScriptEncounterHandle | null
grantReward(rewardId: string): RewardGrantResult
```

`getEquipment()` supports read (`get`/`amount`) and mutation (`equip(itemId)`,
`unequip(slot)`), plus recalculated combat bonuses (`bonus(index 0..11)`,
`bonusName(index)`). `getMagic()` checks and consumes spell runes by client
button id (`MagicData.MAGIC_SPELLS[i][0]`). `getPrayer()` covers prayer indexes `0..25`
(`isActive`/`activate`/`deactivate`/`deactivateAll`/`name`/`requiredLevel`).
`getCombat()` also exposes `underAttack()` and `poisoned()` for port guards.
`openBank()` opens the host bank UI (bank-area and pin rules still apply).

`getSkills()` also exposes current level, base level, XP, and validated
`addExperience(id, amount)`. Inventory exposes `getCapacity()`,
`getFreeSlots()`, `canRemove(...)`, and boolean `remove(...)`. Bank exposes
`getCapacity()` and boolean `remove(...)`. Invalid IDs, amounts, skill indexes,
animation/graphic/sound IDs, and interface IDs are rejected without mutating
engine state. Removal returns `true` only when the complete requested amount
was available and removed. Inventory addition also preflights item definitions,
stack limits, and complete slot capacity; `false` never represents a partial
addition.

`state(namespace)` returns a fixed-namespace capability and rejects an invalid
or reserved namespace rather than returning `null`. `quest(id)` returns `null`
when the active generation does not register that quest ID. `questPoints()`
returns the player's current legacy quest-point total as a read-only number;
quest completion is the mutation path exposed to scripts.

## Persistent player state

Script-owned state is held in a synchronized, Java-owned store on `Player`.
Content must choose a public namespace before accessing a key:

```ts
const state = player.state("dragon-awakens");

state.has("bones-recovered");
state.getBoolean("bones-recovered");             // boolean | null
state.getBooleanOr("bones-recovered", false);    // boolean
state.setBoolean("bones-recovered", true);       // true only when changed
state.getNumber("attempts");                     // number | null
state.getNumberOr("attempts", 0);
state.setNumber("attempts", 1);
state.getString("ending");                       // string | null
state.getStringOr("ending", "unknown");
state.setString("ending", "dragon-rite-sealed");
state.remove("ending");                          // true only when removed
```

The value types are exactly finite JavaScript numbers, booleans, and strings.
There are no guest objects, arrays, callbacks, Java handles, or `undefined`
values in persisted state. A typed getter returns `null` only when the key is
absent; reading a present value through the wrong typed getter is an error.
The `*Or` methods validate their fallback and use it only for an absent key.

Public namespaces and keys use lower-case ASCII identifiers beginning with a
letter, with optional lower-case alphanumeric segments separated by `.` or
`-`. Namespaces are 1–48 UTF-8 bytes, keys are 1–96 bytes, strings are at most
4096 bytes, and numbers must be finite. The store allows at most 32 namespaces,
256 entries per namespace, 1024 entries total, and a 65536-byte encoded
payload. The internal `__quest` namespace and `sys.*` names are reserved; quest
progress must be accessed through `player.quest(...)`, not raw state.

Snapshots defensively copy and sort namespaces and keys. `PlayerSave` writes
one deterministic `character-script-state = v1.<base64url>` line. The `v1`
decoder is strict about the version, canonical unpadded Base64URL, UTF-8,
duplicates, type tags, finite numbers, trailing bytes, and all limits. The
codec has a version-decoder dispatch seam for future migrations, and ships a
built-in historical `v0` decoder for the flat legacy body: `u16 entryCount`,
then repeated bounded `u16 namespace UTF-8`, `u16 key UTF-8`, `u8 type`, and
the same per-type payload v1 uses. `v0` entries are grouped by namespace,
duplicate namespace/key pairs and every current aggregate/UTF-8/value limit
are rejected, and truncation, trailing bytes, malformed Base64URL, padding,
invalid types, non-finite numbers, and invalid UTF-8 are isolated like any
malformed payload. Encoding remains `v1` only: a valid `v0` character loads
through `PlayerSave`, keeps its legacy fields, installs the migrated state,
and is atomically re-saved as `v1`; malformed `v0` stays quarantined and
unsavable.

Load decodes and validates a complete candidate before replacing the live
store. A missing field installs an empty store. A malformed, unsupported, or
duplicate field is isolated: the player receives an empty live store and the
original payload is retained as a quarantine marker. While that marker is
unresolved, `PlayerSave.saveGame` refuses to save, so an ordinary later save
cannot overwrite the recoverable character file with empty script state.
Valid saves encode before opening the destination, write a temporary file, and
replace the character file atomically when the filesystem supports it.

## Quest definitions and runtime contract

`defineQuest` accepts a data descriptor with `id`, `name`, `summary`, and
contiguous stages numbered exactly `0..n-1`. Optional requirements cover quest
points, completed quest IDs, base skill levels, and inventory items. Optional
rewards cover quest points, inventory items, and experience. TypeScript's
`createQuest` provides author-side validation; Java parses the descriptor
again, rejects unknown members and invalid bounds, and copies it into immutable
Java collections. The active quest registry therefore retains no Graal
`Value` from a guest context.

All staged quest definitions are validated as one reload candidate, including
duplicate IDs, missing dependencies, self-dependencies, and dependency cycles.
Only after module evaluation and candidate validation succeed does
`ScriptHost` publish the new Java-owned descriptors with the other registries.
A rejected candidate leaves the last-known-good context, generation,
definitions, and persisted player progress intact.

The exact runtime surfaces are:

```ts
interface ScriptedPlayer {
  state(namespace: string): PlayerStateNamespace;
  quest(id: string): ScriptedQuest | null;
  questPoints(): number;
}

type ScriptQuestState = "not_started" | "in_progress" | "completed";

interface ScriptedQuest {
  id(): string;
  name(): string;
  summary(): string;
  state(): ScriptQuestState;
  stage(): number | null;
  objective(): string | null;
  canStart(): QuestResult;
  start(): QuestResult;
  setStage(expectedCurrent: number, nextStage: number): QuestResult;
  advance(expectedCurrent: number): QuestResult;
  complete(expectedFinalStage: number): QuestResult;
}

interface QuestResult {
  ok(): boolean;
  changed(): boolean;
  code(): QuestResultCode;
}
```

`stage()` returns `null` when no stage is stored, including an unstarted quest;
it is not represented as `undefined`, `-1`, or `0`. A completed quest retains
its final stage. `objective()` is a read-only projection of Java-owned
descriptors and state: `null` while the quest is not started or no stage is
stored, the current stage text while in progress, and the stable completion
summary (`"Quest complete."`) once completed. `canStart()` is result-shaped,
not a boolean. `ok()` says the operation reached a successful or idempotently
satisfied outcome, while `changed()` is `true` only when the call committed a
mutation.

`QuestResultCode` is the following closed 16-code wire union:

```ts
type QuestResultCode =
  | "can_start"
  | "started"
  | "already_completed"
  | "already_started"
  | "requirements_not_met"
  | "state_failed"
  | "not_in_progress"
  | "stage_mismatch"
  | "invalid_stage"
  | "advanced"
  | "not_final_stage"
  | "quest_points_overflow"
  | "inventory_full"
  | "xp_cap"
  | "reward_failed"
  | "completed";
```

`canStart()` reports `can_start`, `already_completed`,
`already_started`, or `requirements_not_met` without changing state.
`start()` additionally reports `started` or `state_failed`.
`setStage()` and `advance()` report `advanced`, `not_in_progress`,
`stage_mismatch`, or `invalid_stage`. `complete()` can report
`completed`, `already_completed`, `not_in_progress`, `stage_mismatch`,
`not_final_stage`, or one of the four reward refusal/failure codes.

Quest completion simulates the complete reward first and then commits
inventory, XP/current levels, quest points, final stage, completion state, and
weight as one player-locked transaction. Quest XP is the descriptor's exact
amount (not the configurable ordinary skill-XP rate); aggregate awards and the
existing skill XP must remain at or below 200,000,000 or the result is
`xp_cap`. Quest points must remain in `0..10000`. Item rewards must all fit in
the inventory with valid definitions and stack limits. There is deliberately
no bank fallback: insufficient inventory returns `inventory_full` and leaves
the quest retryable. Any mutation/postcondition exception restores the full
snapshot and returns `reward_failed`. After a successful commit, inventory,
weight, affected skill, and quest-stage presentation refreshes are
best-effort; a refresh failure is logged but does not roll back a valid reward.

## Scheduling and lifecycle

Scheduling is player-owned and advances only from the main game cycle. Delays
and repeat intervals are integer game ticks in the inclusive range
`1..100000`. Equal-due tasks run in registration order. `cancel()` is
idempotent, and a repeating callback stops if it throws. Player removal and a
successful script reload cancel all affected work before an old context is
closed. Each callback runs under its generation lease, so reload waits for an
already-running old-generation callback and no old callback can begin after
that generation is invalidated. A task also stops when its player is no longer
the live player in its slot.

Lifecycle handlers are observers: legacy login, logout, NPC death, pickup, and
area behavior continues even when a handler throws. Singleton `onLogin` and
`onLogout` registrations and all exact-ID registrations reject duplicates as
load errors. Area descriptors have stable IDs and inclusive bounds:

`onLogin` and `onLogout` receive the standard player context.
`onItemPickup` additionally supplies the transferred `item`, `amount`, and
ground `position`. `onNpcDeath` supplies the dead `npc`, nullable `killer`, and
death `position`. Area transitions supply `area`, `from`, and `to`.

```ts
const courtyard = {
  id: "lumbridge-courtyard",
  minX: 3218,
  minY: 3218,
  maxX: 3225,
  maxY: 3225,
  plane: 0, // optional; omit to match all planes
};

onEnterArea(courtyard, (ctx) => {
  const reminder = ctx.player.after(2, (handle) => {
    ctx.player.message(`Still near ${ctx.area.getId()}.`);
    handle.cancel(); // useful for repeating work; harmlessly false after completion
  });
});
```

Login fires after engine initialization, logout once at terminal removal, NPC
death after legacy drop/kill bookkeeping, and pickup only after an inventory
transfer succeeds. Reload re-baselines area membership and player removal
clears it, so neither operation fabricates area transitions.

## Phase 4 — World and encounter services

Phase 4 adds bounded world/encounter capabilities without widening the Graal
host-access boundary. All declarations below are the executable contract in
`content/src/core/runtime.ts`; Java wrappers live in `com.rs2.script.world`,
`com.rs2.script.capability`, `com.rs2.script.context`, and
`com.rs2.script.snapshot`. Runtime validation never throws across the bridge:
boolean mutators return `false`, numeric queries return `0` (except
`nextInt`/`distance`, which return `-1`), nullable factories return `null`,
and array-producing operations return an empty `ScriptArray`.

### Interaction routes

`onButton`, `onItemOnGroundItem`, `onItemOnPlayer`, `onMagicOnItem`,
`onMagicOnObject`, `onMagicOnNpc`, `onMagicOnPlayer`, and `onPlayerDeath`
join the Phase 1-3 registrations. All packet routes share one normative
dispatch state machine: exact payload decoding into local primitives,
universal safety validation (live player, identity, coordinates, definitions,
plane/distance), the action-lock gate, then one generation-leased exact-key
lookup and invocation. A matched handler consumes the packet even when it
throws; a valid unmatched key alone falls through to the legacy continuation.
Invalid traffic — registered or not — is dropped with zero side effects.

`onMagicOnNpc` and `onMagicOnPlayer` consume the packet and bypass the legacy
combat rune check when a handler matches. The host does not auto-consume runes;
handlers must call `player.getMagic().consumeRunes(spellButtonId)` (or
`sdk/magic.consumeSpellRunes`) or casts become free.

Button keys are sparse `u8*1000+u8` values (`0..255255` with both digits
`<=255`); unreachable keys such as `256` reject the whole reload candidate.
`onPlayerDeath` is a singleton observer: it receives immutable
`player`/`killer` snapshots and the death position only after the engine's
mandatory `applyDead` work completes; it cannot mutate the dead player.

Every route context extends the common `player`/`target`/`action` shape
unless noted. The executable context members are:

| Context | Extra members |
|---|---|
| `CommandScriptContext` | `getName()`, `getRawInput()`, `getArguments()` (immutable), `getRights()` |
| `ItemClickScriptContext` | `item` (`ScriptedItem`), `slot` |
| `ItemOnItemScriptContext` | `usedItem`, `usedSlot`, `targetItem`, `targetSlot` (actual invocation direction) |
| `ItemOnObjectScriptContext` | `item`, `slot`, `target` (`ScriptedObject`) |
| `ItemOnNpcScriptContext` | `item`, `slot`, `target` (`ScriptedNpc`) |
| `ButtonScriptContext` | `buttonId` |
| `ItemOnGroundItemScriptContext` | `item`, `slot`, `target` (`ScriptedGroundItemView`: `token()`, `id()`, `amount()`, `position()`, `isPrivateToPlayer()`) |
| `ItemOnPlayerScriptContext` | `item`, `slot`, `target` (`ScriptedPlayer`) |
| `MagicOnItemScriptContext` | `spellId`, `slot`, `target` (`ScriptedItem`) |
| `MagicOnObjectScriptContext` | `spellId`, `target` (`ScriptedObject`) |
| `MagicOnNpcScriptContext` | `spellId`, `target` (`ScriptedNpc`) |
| `MagicOnPlayerScriptContext` | `spellId`, `target` (`ScriptedPlayer`) |
| `NpcDeathScriptContext` | `npc`, nullable `killer`, `position`, `action: "death"` (no `player`) |
| `EncounterNpcDeathScriptContext` | `encounter`, `npc` (immutable `ScriptNpcSnapshot`: `id()`, `name()`, `position()`, `maxHp()`), nullable `killer`, `position`, `action: "encounter-npc-death"` |
| `ItemPickupScriptContext` | `item`, `amount`, `position` |
| `AreaTransitionScriptContext` | `area` (`ScriptArea`), `from`, `to` |
| `PlayerDeathScriptContext` | `player`/`killer` (immutable `ScriptPlayerSnapshot`: `username()`, `position()`, `combatLevel()`, `rights()`), `position`, `action: "death"` |

`ScriptedNpc` snapshots and `ScriptedItem`/`ScriptedObject` wrappers are
defensive: unknown definitions never throw and report conservative
metadata.

### Encounters and arena reservation

`player.beginEncounter(id, minX, minY, maxX, maxY, plane)` returns an
encounter handle or `null`. The id is scoped by generation and owner; the
inclusive rectangle must be loaded, `1..64` tiles per side, and must not
overlap an active same-plane reservation. At most 64 encounters may be live
per content generation. The owner is automatically participant zero; a live
player belongs to at most one encounter. Per encounter: 8 participants,
16 NPCs, 32 object mutations, 128 ground identities, 32 tasks, 32 NPC death
callbacks, and 16 locks per type per participant. Each participant may hold
at most one composable camera session at a time.

The reservation is a shared-world isolation boundary: nonparticipants cannot
enter it, encounter-owned NPCs are excluded from their NPC updates, clicks,
attacks, delayed hits, cannon hits, and retaliation, and encounter-scoped
presentation targets participants only. Owner death, logout, player removal,
callback failure, explicit `close()`, and successful reload close the
encounter; a non-owner leaving releases only their resources. Close order is
callbacks, tasks, camera, locks, targeting, NPCs, rewards, object restoration
in reverse transaction order, then the exact reservation compare-remove —
the reservation stays held until the end, so a concurrent `begin` cannot
observe a partially restored arena.

```ts
onCommand("example", (ctx) => {
  const encounter = ctx.player.beginEncounter("example", 2264, 4688, 2287, 4711, 1);
  if (encounter === null) { ctx.player.message("Busy."); return; }
  ctx.player.teleport(2271, 4696, 1);
  const boss = encounter.spawnNpc(50, 2271, 4698, 1, 240, 30, 350, 350);
  if (boss === null) { encounter.close(); return; }
  encounter.onNpcDeath(boss, (death) => death.encounter.close());
});
```

The complete `ScriptEncounterHandle` surface is:

```text
id(): string                     owner(): ScriptedPlayer        isOpen(): boolean
addParticipant(player): boolean  removeParticipant(player): boolean
participants(): ScriptArray<ScriptedPlayer>
spawnNpc(npcId, x, y, plane, hp, maxHit, attack, defence): ScriptNpcHandle | null
onNpcDeath(npc, callback): boolean
replaceObject(x, y, plane, expectedId, expectedType, expectedRotation,
             replacementId, replacementType, replacementRotation): ScriptObjectHandle | null
removeObject(x, y, plane, expectedId, expectedType, expectedRotation): ScriptObjectHandle | null
dropFor(player, itemId, amount, x, y, plane): ScriptGroundItemHandle | null
after(ticks, handler): ScriptTaskHandle | null    every(ticks, handler): ScriptTaskHandle | null
contains(x, y, plane): boolean
nextInt(bound): number          chance(numerator, denominator): boolean
rollDrops(player, x, y, plane, privateTicks, entries): ScriptArray<ScriptDropResult>
distance(first, second): number
isWalkable(x, y, plane): boolean
hasProjectilePath(fromX, fromY, toX, toY, plane): boolean
close(): boolean
```

The spatial methods are authoritative engine results: `distance` is the
Chebyshev distance between two valid same-plane positions (else `-1`),
`isWalkable` the clipping result for a loaded in-area cell, and
`hasProjectilePath` the straight-line projectile clipping between two
in-area cells.

### Capability handles

Every owned resource is exposed through a narrow handle with a stable
opaque token; a stale or foreign token makes every operation fail closed:

- `ScriptNpcHandle` — `token()`, `id()`, `position()`, `hp()`, `maxHp()`,
  `isAlive()`, `setTarget(player)`, `clearTarget()`, `face(x, y)`,
  `walkTo(x, y)`, `damage(amount, source)`, `heal(amount)`,
  `animate(animationId, delay)`, `graphic(graphicId, height)`,
  `forcedChat(text)`, `despawn()`.
- `ScriptObjectHandle` — `token()`, `id()`, `position()`, `type()`,
  `rotation()`, `isActive()`, `remove()`.
- `ScriptGroundItemHandle` — `token()`, `id()`, `amount()`, `position()`,
  `identityCount()`, `isAttached()`, `isClaimed()`, `detach(privateTicks)`,
  `remove()`. `detach` arms the private expiry (`1..1000` ticks) so an
  encounter or area close cannot delete a just-awarded drop; pickup
  resolves one exact visible creation token and claims it atomically.
- `ScriptLockHandle` — `token()`, `isActive()`, `release()`.
- `ScriptCameraSession` — `token()`, `isActive()`, `position(...)`,
  `lookAt(...)`, `shake(...)`, `release()`.
- `ScriptTaskHandle` — `cancel()` (idempotent), `isCancelled()`.

Handles are never guest-constructible; they exist only as returned values
and are invalidated by close, death, logout, or reload.

### Facades, locks, and camera

- `getEquipment()` exposes the 11 named `ItemConstants` slots
  (`hat/cape/amulet/weapon/chest/shield/legs/hands/feet/ring/arrows`); empty
  slots return `null`/`0`. `equip(itemId)` and `unequip(slot)` mutate through
  `ItemAssistant`. `bonus(index)` recalculates and returns one combat bonus
  (`0..11`: stab through prayer).
- `getCombat()` reports live HP and enters the engine hit/death path for
  `damage` (clamped to current HP, cannot damage a dead entity) and `heal`
  (clamped to base max HP, cannot revive). Returns are the observed deltas.
  `underAttack()` and `poisoned()` mirror the legacy player flags.
- `getMagic()` resolves spell button ids to `MagicData` rows and exposes
  silent `hasRunes` / `consumeRunes` plus `requiredLevel` / `hasLevel`.
  Script magic-on-NPC/player routes do not invoke these automatically.
- `getPrayer()` activates/deactivates prayer indexes `0..25` through
  `ActivatePrayers` / `PrayerDrain`.
- `openBank()` opens the bank UI through `PacketSender.openUpBank()`.
- `getMovement()` provides `face`, `walkTo` (truthful route result),
  `teleport` (succeeds only after authoritative position/region fields
  reflect the destination), `runEnergy`, `setRunEnergy` (integral `0..100`),
  and `lock`.
- `getPresentation()` covers animation, graphic, forced chat, sound,
  interface/text/model, static shops, scripted shops
  (`openScriptShop(id)` for a `defineShop` definition), still graphics and
  projectiles with validated endpoints, and camera sessions.
  Interface/shop success requires the live output and interface state to
  match; projectiles validate loaded same-plane in-area endpoints within
  Chebyshev distance 25.
- `getActions().lock(ticks)` and `getMovement().lock(ticks)` acquire
  independent tokenized locks (`1..100000` ticks) that stack and release
  only their exact token. Locked players are dropped before packet side
  effects (the authenticated pending scripted-dialogue option is the sole
  exception); commands, logout, keepalive, death, cleanup, and expiry are
  never blocked.
- `getPresentation().beginCamera(ticks)` reserves the player's sole
  composable camera session. `position`, `lookAt`, and `shake` update
  components in call order without reset; exact release, expiry,
  `resetCamera`, close, death, logout, and reload converge on one reset
  packet — repeated cleanup queues none.

### Objects and ground rewards

Object replacement is a collision transaction, not a tile-list edit: one
authoritative resolver enforces encounter > timed > global > cache
visibility, reserves every footprint cell of the old/replacement union,
snapshots and restores movement/projectile masks, and defers conflicting
legacy writers until the encounter restores the arena. `replaceObject` takes
independent expected/replacement shape triples; the empty-tile sentinel is
the exact all-`-1` triple. `removeObject(x, y, plane, expectedId,
expectedType, expectedRotation)` is the dedicated empty-state transaction —
it installs that same all-`-1` replacement under the expected shape.

`dropFor` stages exact player-private ground identities; `detach(privateTicks)`
arms a `1..1000`-tick private expiry so encounter close cannot delete a
just-awarded drop. Pickup (opcode 236) and the ground-item routes (25/253)
resolve one exact visible creation token and claim that identity atomically;
a script-private identity never falls into global drops.

### Deterministic RNG and drop tables

Every encounter owns a game-cycle SplitMix64 RNG derived from a process seed,
generation, owner token, and encounter ordinal — never exposed to guest code,
logged for replay, and pinned by literal test vectors. `nextInt(bound)`
(`1..1000000`) and `chance(numerator, denominator)` validate before advancing
state.

`rollDrops(player, x, y, plane, privateTicks, entries)` runs one transaction:
the guest table (`1..64` entries with exactly `itemId`, `minAmount`,
`maxAmount`, `weight`, `always`) is validated — definition-backed items,
`minAmount <= maxAmount`, `always` requires weight `0` and non-always entries
require a positive weight, and when any weighted entry is present the
weighted weight sum must be `1..1000000` in `long` (an all-always table with
no weighted entry is valid) — then selection (every always entry plus, when
present, exactly one weighted entry by cumulative input order) and amount
rolls advance only a cloned RNG. All exact ground identities are staged,
verified, and detached for `privateTicks` before the RNG state commits; any
parse, capacity, staging, or detach failure removes every staged identity
and leaves the encounter RNG unchanged. Results are
immutable `ScriptDropResult` values exposing `itemId()`, `amount()`, and the
owning ground-item handle.

## ScriptedDialogue surface

`player.getDialogue()` returns the invocation's dialogue builder. The engine resets the
pending-option callback on every new dialogue.

```text
npc(npcId: number, line: string, line2?: string, line3?: string, line4?: string): this
player(line: string, line2?: string, line3?: string, line4?: string): this
statement(line: string, line2?: string, line3?: string, line4?: string): this
options(lines: string[], callback: (choice: number) => void): this
itemDialogue(itemId: number, header: string, lines: string[]): this
end(): void
```

Internal flow:

1. `npc(...)`, `player(...)`, and `statement(...)` buffer the matching
   `DialogueHandler` calls for click-through.
2. `options([...], cb)` appends the option frame, stores `cb` as the
   **pending option callback** when that frame is shown, and starts the chain.
   `itemDialogue(...)` is also a terminal frame and starts the chain.
3. When the client sends the option-button packet,
   `DialogueOptions.handleScriptDialogueOption` checks for a pending callback
   before legacy button handlers run. If
   present, it invokes the callback with the chosen zero-based index
   (`0..lines.length - 1`) and clears the callback slot. The existing switch is
   skipped.
4. `end()` starts a buffered chain, or clears dialogue state and closes the
   interface when no frames remain.

## Registries

The runtime handler registries use an id plus an ordinal interaction action.
Object actions are exactly `"first" | "second" | "third" | "fourth"`; semantic
labels such as `"open"` or `"chop"` are not bridge keys. NPC actions are
`"first" | "second" | "third"`. Each handler receives one `ScriptContext`;
the clicked wrapper is available as `ctx.target`.

`ObjectHandlerRegistry` and `NpcHandlerRegistry` are queried by the patched
`NpcActions` and `ObjectsActions` at the top of their first/second/third
click methods. If a scripted handler exists for the `(id, action)` pair, it
runs and the original switch is bypassed.

Every declarative definition family is consumed by a Java-owned runtime
instead of being stored as data:

- **Bosses** (WP3): `defineBoss` is parsed into an immutable descriptor and
  the standalone adapter registers its exact command/object entry route.
  Entry begins one encounter; an encounter-agnostic controller drives
  spawn, ordered phases, armed special cooldowns, named drops, death, and
  cleanup. Every callback receives the narrow `BossRuntimeContext` composed
  only of accepted wrappers and handles.
- **Areas and shops** (WP4): `defineArea` is parsed into a canonical
  descriptor and activated through the two-phase runtime activation
  transaction (prepared, reserved, shadow-applied, verified, retired into
  an undo ledger, and atomically committed); area NPC spawns, layered
  objects, exact allocation-bound death claims, object-drop routes, and
  scripted shops (`defineShop`, opened with `player.getPresentation()
  .openScriptShop(id)`) are all generation-owned and reversible.
- **Raids** (WP5): `defineRaid` is parsed into a canonical descriptor and
  consumed by the raid runtime (see below).
- **Drops and rewards** (WP2): `defineDropTable` and `defineReward` are
  parsed into Java-owned descriptors with exact item-name resolution at
  candidate load; bosses, raids, and areas reference them by stable id.
- **Quests** remain the executable exception described above.

Boss definitions use numeric `npcId` as their registry identity. Quest,
raid, and area definitions use their stable string `id`. Raid registration
accepts one complete canonical definition object; do not use the obsolete
`defineRaid(id, definition)` shape.

## Phase 5 — Declarative runtimes

Phase 5 converts the declarative definitions from stored data into
Java-owned runtime systems. Definitions, callbacks, and routes are
registered and replaced as one atomic candidate; each family owns its final
schema and validation. All callbacks receive narrow runtime contexts
composed only of accepted wrappers and handles — never a rich domain
`Player`, registry access, or a raw engine object.

### Content modules and source-aware registration

`registerContentModule({ id, schemaVersion, onLoad?, onUnload? }, scope)`
opens a bounded logical module scope: every definition, route, and
observer registered inside `scope` carries the module id and its declared
schema version. Nested scopes, duplicate module ids, invalid ids, and
versions outside `1..255` reject the whole candidate. Registrations made
outside any scope are recorded as `legacy-unscoped` compatibility records
with schema version `0`, so direct-import content keeps loading unchanged.

The shipped production content all registers inside module scopes: the
compiled `content/dist` loader exposes exactly eight modules
(`dragon-island-drops`, `dragon-king`, `encounter-warden`, `dragon-awakens`,
`temple-of-zaros`, `dragon-island-shops`, `dragon-island`,
`woodcutting-resources`), each carrying schema version 1. `::scripts list
modules` reports these ids, and `::scripts status` reports the module count.
Only the demonstration `content/src/examples/*` routes remain legacy-unscoped.

All definitions share one immutable envelope (`kind`, stable key, declared
schema version, bounded logical source, and exactly one payload: the
generation-owned guest value or a Java-owned typed descriptor). One
candidate-wide route registry owns every executable guest callback and
Java host route with no precedence escape hatch: a duplicate exact key
between any two sources or owners rejects the candidate and identifies
both records. The command aliases `scripts`, `reload`, and `scriptdir` are
reserved for the engine admin transport; content may never register them.
`onLoad`/`onUnload` run as contained, non-vetoing observers around the
activation commit: registration is closed before `onUnload` starts, and a
mutating or throwing hook is followed immediately by the mandatory
candidate commit (hook effects and publication are never claimed
rollbackable).

### Named drops and rewards

`defineDropTable` registers a Java-owned named drop table: `1..64` entries
with exact integral amounts and weights, `always: true` with weight `0`,
and non-always entries with positive weights whose weighted sum stays in
`long`. String item ids resolve once at candidate load through an exact,
deterministic item-name resolver; missing or ambiguous names reject the
candidate with the source and field path. Runtime transactions use copied
numeric ids only. The same table is rolled by the encounter handle
(`rollDrops`), by a boss death, by an area NPC death or object drop, and by
a raid completion — each through an explicit Java-owned RNG owner and
ground-delivery policy, never through a shared implicit RNG.

`defineReward` registers a Java-owned named reward: copied item grants,
skill XP, quest points, and script-state mutations. `player.grantReward
(id)` applies it through the shared player-local transaction — exact
snapshots of inventory, derived `player.weight`, XP/current levels, quest
points, and script state; preflight (definitions, capacity, stack limits,
XP cap, points range, weight consistency); mutation with weight
recalculation and postcondition verification; and full rollback on any
failure. The result is a narrow `RewardGrantResult` facade with a closed
code union: `rewarded`, `not_found`, `inventory_full`, `xp_cap`,
`quest_points_overflow`, `reward_failed`. Raid completion applies named
rewards roster-wide and atomically through the roster transaction (see the
raid runtime below).

### Declarative boss runtime

`defineBoss` registers a canonical schema-v1 boss: stable id, a
definition-backed `npcId` (validated against the same npc.json list the
spawn path uses), copied stats, a bounded arena and spawn point inside it,
exactly one entry source (a command XOR an object entry, so no canonical
boss can be inert), an optional close command and entry teleport, ordered
phases with strictly descending `hpPercentThreshold`, named specials with
cooldowns, an optional named drop table requiring `privateTicks`, and the
`onSpawn`/`onTick`/`onDeath` callbacks.

The standalone adapter registers the exact host entry route and begins one
encounter on entry; an encounter-agnostic `BossController` then borrows
that handle and never begins or closes a second encounter. The controller
spawns the boss, fires phases exactly once in descending order, fires
armed specials first after their cooldown and then every cooldown game
cycles, rolls the named drop table on death at the death position for the
exact killer (or the owner), and reports `RUNNING`/`DEFEATED`/`FAILED` to
its owning adapter, which closes the encounter on any terminal result. A
throwing callback or a failed drop roll fails the controller. Callbacks
receive the narrow `BossRuntimeContext` (`boss`, `encounter`, `owner`,
`participants()`, `position()`, `hpPercent()`, `alive()`, `say`,
`useSpecial`). The compiled `content/src/bosses/dragon-king.ts` is the
production fixture (loaded Black dragon 54, command entry, phase and
special cadence, named table with private TTL).

### Declarative areas and scripted shops

`defineArea` registers a canonical schema-v1 area: stable id, bounds
(`1..512` per side, one plane), exact spawn and object keys with
definition-backed ids, a drop policy (`PRIVATE_TO_KILLER` with private TTL
or `PUBLIC`) coupled to a named drop table, and candidate-scoped shop,
quest, boss, and raid references. `defineShop` registers an immutable
scripted shop with bounded stock, prices, and restock ticks.

Areas are activated through the production two-phase activation adapter:
prepare/validate without mutation, acquire a handoff reservation over
every predecessor/replacement NPC slot, object footprint, shop, and drop
binding, apply the candidate invisibly, verify it, retire the predecessor
into an idempotent undo ledger, pass the final pre-publication checkpoint,
run the old `onUnload` observer, and reach one no-throw commit that
selects the new context, generation, and world projections together.
Every injected pre-commit failure restores the complete previous world
exactly and never invokes `onUnload`; a same-area replacement stays
invisible until the selector swap and blocks competing writers while the
reservation is held. Rejected reloads preserve the active area; successful
reloads compare-remove old generation identities before the old context
closes.

Area-owned NPC death claims flow through the exact `NpcHandler` critical
section: the binding claims the exact spawn allocation once, a matched
claim suppresses legacy `dropItems` (even when the canonical transaction
returns a handled rejection or contained failure), and an equal-id legacy
NPC or a stale/reused allocation remains unmatched and keeps its complete
legacy drop path. Killer eligibility (live slot, plane) is enforced at the
delivery commit; `NO_RECIPIENT`, wrong-plane killers, missing tables, and
transaction failures consume the claim without RNG or ground mutation.
Object-drop routes are keyed by the resolver's exact generation-owned
projection identity plus action — a cache object with the same id/action
at another tile has no such key and falls through to the plain lookup.
Scripted shops open through `player.getPresentation().openScriptShop(id)`
and buy/sell/restock through the production `ShopAssistant` path; shadow
shopkeepers cannot open before the commit line. The compiled
`content/src/areas/dragon_island` fixture moved from the map-less custom
island to the real Crandor region (11414/11415) with loaded definition-
backed ids and an exact chest object-drop route.

### Declarative raid runtime

`defineRaid` registers a canonical schema-v1 raid with an exact command
route, the raid bounds on one plane, a bounded muster rectangle, the
entrance point, player limits (1..8, `minPlayers <= maxPlayers`), a time
limit, ordered non-overlapping rooms, at least one named reward, an
optional reward table with its private TTL, and the
`onStart`/`onComplete`/`onWipe` callbacks. Every callback receives the
narrow `RaidRoomContext` (`encounter`, `owner`, `participants()`,
`elapsedTicks()`, `position()`, `announce(text)`).

The bounded lobby subcommands run through the definition's exact command
route, e.g. `::temple-of-zaros`:

- `create` — the inviter owns one pre-encounter lobby per raid id.
- `invite <player>` — records an exact live invitee while both players are
  outside any lobby/session and capacity remains.
- `join <owner>` — the invitee's explicit opt-in; the invitation is
  compare-consumed against the exact live player object.
- `leave` — the owner closes the lobby; a non-owner removes only its
  invite/opt-in. After start, a non-owner leave marks the member departed.
- `start` — owner only; requires every opted-in identity live on the
  entrance plane inside the muster area. It freezes the immutable roster
  (owner first, then join FIFO), begins exactly one encounter with the raid
  bounds as its reservation, atomically adds every roster identity, and
  teleports the members to the entrance before the first room callback.

Rooms advance in declared order. A room with `boss: { bossId }` embeds the
WP3 `BossController` for the referenced boss, borrowing the raid's sole
encounter handle — it never begins or closes a second encounter.
Controller `DEFEATED` completes the room; `FAILED` wipes. The final room's
completion enters the reward barrier: the surviving active roster is
frozen, and the named rewards commit roster-wide and atomically through
the shared roster transaction (global coordinator, raid-session RNG owner,
per-player reward-state mutexes in ascending player-slot order, exact
snapshots including `player.weight`, fresh per-attempt plans, reverse
rollback, and one joint commit advancing the RNG and recording the
once-only award id). The optional reward table rolls once after that
commit as private ground deliveries through the raid RNG owner.
`onComplete` runs once after the commit; owner departure, zero active
members, timeout, boss or room-callback failure, barrier departure, or
grace expiry invoke `onWipe` once and award nobody. A rejected reload
keeps the lobby/session; a successful reload closes old-generation
lobbies/sessions before the old context closes.

The shipped `content/src/raids/temple-of-zaros/raid.ts` fixture is the
production proof: two distinct live players create/invite/join/start
through real command packets on the Crandor plane-1 region, the guardian
room completes by elapsed ticks, the crypt embeds the canonical
`dragon-king` boss, and completion commits `zaros_raid_reward` to both
members plus the `zaros_raid_loot` private ground roll.

### Declarative gathering resource runtime (WP8)

`defineGatheringResource` registers a canonical schema-v1 gathering
resource: stable id, name, one canonical object id and ordinal action, the
required skill and level, ordered tool alternatives (each an inventory or
equipped item id with an optional per-success consumption), the harvest
animation, the tick interval, an exact deterministic success chance, bounded
item rewards and one experience grant, the depleted object id, and respawn
ticks.

The runtime owns the exact host object route at the canonical
object-id/action key. A live player clicking the object validates the skill
level and an ordered tool (inventory first, then equipment), verifies the
exact world-object identity, and opens a bounded per-player resource
session. The session runs a repeating game-cycle task that revalidates live
identity, skill, and tool, animates, and performs a deterministic success
check on the Java-owned `ResourceSessionRng` (the accepted WP6 SplitMix64
derived from the process seed, generation, and a per-session owner token —
never a fake exclusive encounter). On success the item and XP rewards commit
as one rollback-safe transaction (exact snapshots of inventory, derived
`player.weight`, XP, and current levels; full restore on any failure), the
object depletes to the declared empty id through the timed-object path, and
the original object restores after the respawn interval. Every stop path —
harvest/depletion, movement away, logout, death, object replacement, reload,
or runtime failure — cancels the session's task with zero residue.

Only the canonical object-id/action key is a host route: an equal-id cache
or legacy object at another tile has no route key and retains its complete
legacy behavior, and route conflicts with guest `onObject`, another host
consumer, or another resource definition reject the whole candidate through
the unified route registry. Legacy skilling pre-dispatch is gated on route
absence: `ClickObject` skips the legacy woodcutting `startWoodcutting` call
when an exact guest or host route owns the object-id/action key, so a
registered resource never double-consumes (the WP8 host route and the legacy
loop would otherwise both grant rewards). The shipped
`content/src/resources/woodcutting.ts` fixture registers a regular tree
(object 1276, bronze axe, one log + 25 woodcutting XP, stump 1341, 4-tick
respawn) and an oak tree (object 1281, level 15) as the production proof.

### Declarative processing skill runtime

`defineProcessingSkill` registers a canonical schema-v1 processing skill
proven by the shrimp-on-range cooking port: stable id, name, skill/level,
input item on a target object, success product, optional fail/burn product,
experience, animation, optional sound, tick interval, and a burn-style
success curve (`stopBurnLevel`, optional cooking-gauntlet override).

The runtime owns the exact host item-on-object route for
`inputItemId`/`objectId`. Using the item on the object opens a bounded
per-player session that processes one input every `intervalTicks` until the
inventory runs out, the player walks away, logs out, dies, or a generation
reload closes the session. Unregistered item/object pairs keep legacy Java
behavior. The shipped `content/src/skills/cooking.ts` module registers raw
shrimps (317) on cooking range 114 as the production proof.

### Declarative world mob runtime

`defineMob` registers a canonical schema-v1 world mob for a cache NPC id:
stable id, optional name, aggression radius (tiles; `0` = retaliate-only),
`combatStyle` (`melee` / `ranged` / `magic`), `attackSpeed` (ticks),
`maxHit`, optional attack `animation`, and optional `onSpawn` / `onTick` /
`onDeath` callbacks.

The Java `ScriptMobRuntime` owns aggression targeting, walk-to-target via
the existing NPC follow path, and basic attack ticks from those stats.
Registered `npcId` values suppress the legacy `NpcCombat` switch;
unregistered ids keep legacy behavior. Callbacks receive a narrow
`MobRuntimeContext` (identity, vitals, position, optional killer, plus
`say` / `face` / `animate`) and are invalidated on `::scripts` reload and
NPC despawn. Arena bosses remain on `defineBoss`. The shipped
`content/src/mobs/goblin.ts` module registers goblin (npc 100) as the
production proof.

### Definition overlays (cache metadata merge)

`defineItemOverlay`, `defineNpcOverlay`, and `defineObjectOverlay` merge
optional metadata over **existing** cache definitions at `::scripts`
activation. The target id must already exist in the loaded cache pack;
unknown ids reject the candidate at load.

Item overlays may set `name`, `examine`, `stackable`, `equipSlot`, skill
`requirements`, and combat `bonuses`. NPC overlays may set `name`,
`combatLevel`, and `hitpoints`. Object overlays may set `name`, `examine`,
and up to five `actions`.

`ScriptOverlayRuntime` applies overlays in ascending cache-id order and logs
each merge at activation. On generation close the previous overlays revert
to their captured baselines before the next generation publishes. Use
asset-pipeline custom-namespace ids (`35000+` for items/objects/NPCs;
`50000+` for models) rather than remapping OSRS ports onto 2006 ids. The
shipped `content/src/overlays/custom-port.ts` module is the production
proof.

### Scripted quest journal

Every scripted quest definition is projected into the legacy client quest
tab and a generic detail interface by `ScriptQuestJournalService`, so
authors do not need client work to show objectives:

- **Mapping.** Sorted scripted quest ids are paired deterministically with
  the bounded pool of currently unimplemented legacy quest-tab rows — enum
  rows without a legacy quest status whose buttons are not handled by
  `QuestAssistant.questButtons` (the 15 implemented legacy buttons,
  including Romeo Juliet, are never reused) — sorted by button id. A
  candidate with more scripted quests than usable rows (89 today) is
  rejected at load with both counts. The mapping is generation-owned:
  only an accepted reload recomputes it, and a rejected candidate leaves
  the previous mapping and UI state untouched.
- **Rows.** `QuestAssistant.sendStages` renders each mapped row with the
  legacy color scheme — plain name, `@yel@` in progress, `@gre@`
  completed — on login, quest transitions (`start`/`setStage`/`complete`),
  and successful reload (`ScriptLifecycleService.onGenerationCommitted`
  re-renders rows for live players). These refreshes run on the game
  cycle; the quest-mutation path holds the player monitor while the
  reload path holds the `ScriptHost` monitor, so both are single-threaded
  in production.
- **Generic detail interface.** A mapped row button opens interface `8134`:
  bounded text components are cleared (8144–8195, 12174–12223,
  14945–15044) and the quest name, summary, requirements (a bounded
  240-byte join, truncated deterministically with `...`), state, and the
  `objective()` projection are rendered from authoritative Java-owned
  state. Exact scripted `onButton` registrations still run first and
  consume their buttons; unmapped buttons keep the exact legacy
  `QuestAssistant.questButtons` path, including the disabled-quest
  message.

The compiled `dragon-awakens` quest is the production proof: its row
transitions with the quest state, its button opens the generic journal,
and its login reminder sources from `objective()` instead of a stage
counter.

### Public TypeScript content SDK (WP7)

The author-facing surface is stabilized and exported through one public
barrel, `content/src/sdk/index.ts` (`content/src/index.ts` re-exports it).
Every builder emits canonical schema-v1 values that the Java parsers
accept, validates exact bounds, and deep-freezes every array/map; no
exported surface depends on engine internals, and executable callbacks
receive the narrow runtime wrappers — never a rich domain `Player`. The
barrel's type surface is canonical only: the rich domain models
(`Inventory`, `Equipment`, `Skills`, `Quests`, `Dialogue`,
`NpcInteractionHandler`, the deprecated `BossContext`, and friends) are
not exported from the SDK barrel — they remain importable by path (and
from the source-compatible root barrel) for the future bot phase. The
barrel exports:

- **Family builders**: `createBoss`/`registerBoss`,
  `createQuest`/`registerQuest`/`createStage`,
  `createArea`/`registerArea`, `createRaid`/`createRaidRoom`/
  `createBossRoom`/`registerRaid`/`raidBuilder`,
  `createDropTable`/`dropTable`/`DropTableBuilder`,
  `createReward`/`registerReward`, `createShop`/`registerShop`,
  `createGatheringResource`/`registerGatheringResource`, and
  `registerModule` (content-module scopes).
- **`sdk/requirements`**: pure predicates (`hasSkillLevel`, `hasItem`,
  `hasCompletedQuest`, `hasQuestInProgress`, `hasNotStartedQuest`,
  `hasQuestPoints`) composed with `all`/`any`/`not` over a narrow
  read-only `RequirementView` (the Java `ScriptedPlayer` satisfies it
  structurally). They never mutate and never use the rich domain model.
- **`sdk/rewards`**: `grantReward(player, id)` forwards the narrow
  `RewardGrantCode` from the shared player-local transactional consumer.
- **`sdk/shops`**: `ShopReference` is explicitly typed — scripted shops
  open by stable id (`openScriptShop`), legacy numeric static shops by
  number (`openStaticShop`) — and `openShop(player, ref)` routes to the
  exact capability.
- **`sdk/equipment`**: the 11 canonical runtime slot names only; the
  legacy domain names `head`/`neck`/`body`/`ammo` fail with a migration
  message naming their canonical replacement instead of being silently
  accepted. Helpers include `equipItem` / `unequipSlot` and
  `equipmentBonus` / `equipmentBonusName`.
- **`sdk/magic`**: `hasSpellRunes` / `consumeSpellRunes` /
  `spellRequiredLevel` / `hasSpellLevel` over `ScriptedMagic`, keyed by
  client spell button ids (for example `WIND_STRIKE = 1152`).
- **`sdk/prayer`**: `activatePrayer` / `deactivatePrayer` /
  `deactivateAllPrayers` / `isPrayerActive` over `ScriptedPrayer`.
- **`sdk/dialogue`**: bounded `sayNpc`/`sayPlayer`/`sayStatement`/
  `sayOptions` helpers over the `ScriptedDialogue` chain, and the
  cutscene session engine: `runCutscene(player, plan)` executes steps in
  order and owns every task, action/movement lock, and camera session it
  creates. A step failure, the plan's final one-shot task, or an explicit
  `cancelCutscene` releases all owned handles (locks and cameras expire
  by their declared tick counts regardless); repeating (`every`) task
  fires never complete the session, and repeating tasks stay tracked and
  cancellable across fires; `cancelCutscenesFor(player)` cancels every
  session of one player (wire it into the author's own `onLogout`
  observer). Handles invalidated by logout or reload are contained
  no-ops, and the engine itself already cancels player-owned tasks and
  releases locks/cameras on those events.
- **Drop tables**: `createDropTable` validates, deep-freezes, and
  registers; the fluent `DropTableBuilder` emits canonical integral
  weights only — the legacy `Infinity`/`0.25` forms fail with a migration
  message (`veryRare()` is not supported) rather than silently changing
  odds. The inert `LootTable`/`createLootTable`/`mergeTables`/
  `analyseTable`/`DropWeights` surface was removed.

The generated API inventory in `docs/API_INVENTORY.md` lists every
runtime global the bridge installs and the export surface of every SDK
barrel module; `pnpm --filter @singlescape/content test` regenerates it
in memory and fails when the checked-in document is stale. The SDK tests
run with the Node built-in test runner against the compiled
`content/dist` output (no new runner dependency) and cover deep-freeze/
mutation, invalid bounds, duplicates, missing references, stale
handles, cancellation, and migration errors; the single primary
acceptance gate remains `./scripts/build.sh`.

The shipped examples (`content/src/examples/*`) import only from the SDK
barrel and compile without ambient rich `Player` objects in executable
callbacks; the Lumbridge man dialogue now uses the SDK dialogue helpers.

## Item interactions and invocation metadata

Item routes are exact-ID registrations. A matching scripted handler is
authoritative: it runs once after packet ownership/existence/distance
validation and the legacy event or behavior is skipped. An unmatched route
continues through the original Java path unchanged. Guest exceptions are
contained and still consume an exact registered route.

Invalid Phase 1 item IDs, actions, callbacks, and duplicate exact keys are
load errors. The candidate context is rolled back as a unit, leaving every
registry from the last known-good context active. Reversing an item-on-item
pair does not avoid duplicate detection.

`onItem` receives an `ItemClickScriptContext` containing `player`, `item`,
`slot`, and the ordinal `action`.

`onItemOnItem` lookup is order-insensitive, but the callback preserves the
actual invocation direction:

```ts
onItemOnItem(590, 1511, ctx => {
  // These always describe what the player actually selected and targeted.
  ctx.usedItem.getId();
  ctx.usedSlot;
  ctx.targetItem.getId();
  ctx.targetSlot;
});
```

`onItemOnObject` receives the used item and slot plus a `ScriptedObject`
target. `onItemOnNpc` receives the used item and slot plus a `ScriptedNpc`
target. Item definition lookup is defensive: unknown definitions do not throw,
and report `"Unknown item"` with conservative metadata flags.

Third item clicks are rejected while duel or trade interaction is blocked,
before either script or legacy events run. Item-on-NPC packets require a live,
owned inventory slot and a live NPC on the player's plane within ordinary NPC
interaction distance before either route may execute.

Command registration remains `onCommand(name, handler)`. Each invocation now
receives a `CommandScriptContext` with `getName()` (canonical lower-case name),
`getRawInput()` (unmodified packet text), `getArguments()` (a defensive,
immutable snapshot), and `getRights()` (the invoking player's current rights
level). Rights are invocation metadata, not bridge-level permission policy.

## Engine boot wiring

`GameEngine.main` loads TypeScript content after
`setMinutesCounter(minutesCounter)` and before
`Player.getPluginService().load()`:

```java
/**
 * Load TypeScript content
 */
ScriptHost.getInstance().load();
```

The plugin loader still runs — Java plugins are independent of scripts.

## File resolution

The authoritative source is `content/src/` and the compiled output is
`content/dist/`. `ScriptHost` resolves the runtime directory in this order:

1. JVM property `-Dsinglescape.contentDir=/absolute/path/to/content/dist`
2. Environment variable `SINGLESCAPE_CONTENT_DIR`
3. Default `../../content/dist` relative to the server working directory
   (`engine/server/`)

The root launcher exports the environment variable and checks for
`content/dist/loader.js` before starting the server.

## Entry point

The bridge loads `content/dist/loader.js` (a single ES module) if present.
All other modules are pulled in via `import` statements from there. This guarantees
deterministic load order.

If `loader.js` does not exist, every `.js` file under the content root is loaded
individually as a fallback.

`content/src/loader.ts` is the source for the entry. It imports the shipped
examples, drops, boss, quest, raid, and area modules, including Dragon
Awakens, so their definition, interaction, and lifecycle registrations are
evaluated in a fresh context. Add new runtime content to this import graph;
merely exporting a definition from the SDK does not register it. Content
modules may wrap their registrations in
`registerContentModule({ id, schemaVersion }, scope)` to attach source and
version metadata (see Phase 5 — Declarative runtimes).

## Pending-option hook (the only invasive change to existing code)

`ClickingButtons` calls `DialogueOptions.handleScriptDialogueOption` before
legacy button handlers. The bridge decodes a button only against the active
two- through five-option interface. A valid choice clears the pending callback
and dialogue UI before invoking the callback through `ScriptExecutor`; this
lets the callback safely build a follow-up dialogue and prevents a throwing
callback from remaining armed. Buttons from another interface leave the
callback intact and continue to the legacy `handleDialogueOptions` switch.

`Player` gains two public fields:
- `public Consumer<Integer> pendingScriptOption;`
- `public int pendingOptionCount;`

`ScriptedDialogue.options` sets these, then calls `DialogueHandler.sendOption`.

## Object / NPC dispatch (also invasive)

`NpcActions.firstClickNpc` and its second/third variants look up the matching
ordinal action and invoke it through the guarded callback boundary with a
`ScriptContext`. `ObjectsActions` follows the same pattern and additionally
dispatches the `"fourth"` action. When a script owns the click, the outer
packet handler also skips the corresponding legacy plugin event, including
when the script throws; without a registration, the legacy action and event
flow are unchanged.

For NPC handlers, pass the resolved `Npc` (the entity the player clicked), not
the npcType, so the script can use the approved `ScriptedNpc` methods such as
`getHp()`, `getX()`, `getY()`, and `getName()`.

## Reload

`ScriptHost.reload()` is transactional and two-phase. Phase one evaluates a
fresh candidate Graal context in isolation: module scopes open and close
synchronously, every definition and route registers into a candidate-only
staging snapshot, strict parsers validate schemas and bounds, and
candidate-scoped cross-references (boss ids, reward ids, drop tables,
quest dependencies) must resolve inside that same candidate. Phase two runs
the runtime activation transaction over the complete previous and candidate
state:

1. prepare immutable descriptors, routes, and projection intents without
   touching live state;
2. acquire a handoff reservation over every predecessor/replacement runtime
   key, NPC slot, object footprint, shop/drop binding, and report identity;
3. reversibly apply the candidate under an inactive owner token (shadow
   routes, reserved-but-not-visible NPCs, staged projections) and verify it
   without guest visibility;
4. retire predecessor projections into an idempotent undo ledger, verify
   both retirement and candidate state, and pass the final injectable
   pre-publication checkpoint — the last operation allowed to fail or
   abort;
5. run the old generation's `onUnload` as a contained, non-vetoing
   observer with registration closed — once hook invocation begins, no
   fallible step may intervene;
6. immediately perform one no-throw commit assignment that makes context,
   generation, frozen registries/routes, runtimes, projection selector,
   manifest, and report visible together;
7. run the new `onLoad` observer, then discard undo/shadow state, release
   reservations, close the old context, and quarantine/retry any final
   cleanup failure without reverting the published candidate.

Any failure before the commit assignment aborts in reverse order and
restores the complete last-known-good world; an abort never invokes
`onUnload`. A successful reload replaces rather than merges registry
state, so handlers removed from the TypeScript source disappear and no
`Value` from a closed context remains reachable. The `scripts`, `reload`,
and `scriptdir` command aliases stay owned by the Java admin transport and
cannot be shadowed by content.

## Execution and sandbox boundaries

Every NPC, object, command, and dialogue callback is invoked through the shared
guarded execution boundary. A script exception is logged with the handler
category, registration identity, and action; it does not escape into the packet
handler or game loop. Dialogue option state is cleared before its callback is
invoked, so a throwing callback cannot remain armed.

The Graal context uses an explicit export-only host policy. Content can call
only approved `@HostAccess.Export` members on bridge wrappers. Host class
lookup, process execution, socket/network access, native access, and guest
thread creation are prohibited.

ES-module imports remain available through a read-only filesystem rooted at
the configured content directory. Scripts can read modules beneath that root
for import resolution, cannot read paths outside it, and cannot write files.

## First scripted feature (Phase 2 milestone)

After the bridge loads, the engine needs one demonstrable end-to-end scripted
feature. We pick **NPC dialogue** because it exercises the most bridge
machinery in the smallest surface:

1. `onNpc(MAN_NPC_ID, "first", handler)` is registered from a TS module.
2. Player right-clicks "Talk-to" on the NPC.
3. The patched `NpcActions.firstClickNpc` calls the script handler.
4. The handler calls `player.getDialogue().npc(...)`,
   `player.getDialogue().player(...)`, then
   `player.getDialogue().options([...], cb)`.
5. Player picks an option; `DialogueOptions` invokes the callback with its
   zero-based index.
6. Callback calls `player.getDialogue().end()`.

A working example module lives in `content/src/examples/lumbridge-man.ts`
and is imported from `content/src/loader.ts`.

## Current production quest: Dragon Awakens

`content/src/quests/dragon-awakens.ts` is the content-platform Phase 3
production-path proof. It does not depend on the `dragon_island` area
fixture (which Phase 4 migrated to the real Crandor map region
11414/11415). Its live route uses consumed Wilderness world/cache data and
only public bridge registrations:

1. First-click Chronozon (`667`) at `(3156, 3704, 0)`. Accepting the dialogue
   starts stage `0`; speaking to him again advances to stage `1`.
2. Kill a production green dragon (`941`), such as the spawn at
   `(3150, 3704, 0)`. Legacy death/drop bookkeeping guarantees dragon bones
   (`536`). Successfully picking up those bones triggers `onItemPickup` and
   advances stage `1 → 2`.
3. Use one dragon-bones item on the cache altar object (`409`) at
   `(3243, 3207, 0)`. The exact `onItemOnObject` route removes one bone and
   advances stage `2 → 3`.
4. Kill another green dragon. `onNpcDeath(941)` runs after legacy death
   bookkeeping and advances stage `3 → 4`.
5. Return to Chronozon. `complete(4)` atomically awards 3 quest points, 1,000
   coins, and 1,000 Magic XP. A full inventory, XP cap, quest-point overflow,
   or transaction failure leaves stage `4` intact so the player can retry.

The quest also emits an objective reminder on login — sourced from the generic
`objective()` projection rather than a stage counter — and writes ordinary
script-owned flags under the public `dragon-awakens` namespace. Quest
state/stage themselves remain in the reserved Java-owned quest namespace and
survive valid save/load and successful or rejected content reloads. The
scripted quest row and generic detail interface render the same authoritative
objective through the Phase 5 journal service (see "Scripted quest journal"
below).

## Current production boss: Encounter Warden

`content/src/bosses/encounter-warden.ts` is the content-platform Phase 4
production-path proof and is imported from `content/src/loader.ts`. It uses
only public Phase 4 bridge registrations:

1. `::encounter-warden` reserves arena `(2264,4688)..(2287,4711)` on plane 1,
   then teleports the owner to `(2271,4696,1)`. Entry tick 0 acquires
   movement/action locks for 4 ticks and one 6-tick camera session with
   `position`, then `lookAt`, then `shake`; the locks expire on tick 4 and
   the session emits its sole reset on tick 6.
2. King Black Dragon (`50`, HP 240) spawns at `(2271,4698,1)`; an empty tile
   is transactionally replaced by solid object `2213` at `(2275,4698,1)`,
   restored on close.
3. At HP `<= 120`, once: the still-active entry camera is released, 2-tick
   locks and a 4-tick camera are started, the boss animates (`1590`) and
   graphics (`246`), two skeleton `90` adds spawn at `(2269,4698,1)` and
   `(2273,4698,1)`, and the owner is messaged. Every 4 ticks thereafter a
   projectile `393` travels from boss to owner followed by 5 damage.
4. On owned death the boss rolls the exact table (bones `536` always,
   coins `995` weight 100 for 500) with private TTL 200, verifies both
   results, and closes. The detached rewards stay private after close and are
   picked up through the normal opcode-236 path. `::encounter-warden-close`
   requests explicit cleanup, and owner death, logout, callback failure, and
   successful reload close the encounter with no orphaned resources.

The `ScriptBossProductionE2ETest` suite drives the full flow through real
command/pickup/walking/click packet decoding, script scheduler ticks, the
production NPC death loop, and every close path; a live client/server smoke
runbook for the same fixture is part of the Phase 4 completion note.

## Operator diagnostics and admin control (WP9)

Operators inspect and reload the TypeScript content runtime through
permission-gated commands that report only bounded logical state — never host
paths, raw Graal values, engine objects, stack traces, or credentials.

The following commands are available to players with administrator rights
(`playerRights >= 2`); the reserved aliases `scripts`, `reload`, and
`scriptdir` cannot be registered by content (see "Content modules and
source-aware registration"):

| Command | Behavior |
|---|---|
| `::scripts status` (or bare `::scripts`) | Reports the active generation, registered module/definition/route counts, scheduled tasks, and the active encounter/boss/area/shop/raid-lobby/raid-session/resource-session/quest-row counts, plus the last rejected-reload reason when one exists. |
| `::scripts list [kind] [page]` | Sorted, paged listing (at most 20 entries per page). `kind` is `modules` (default) or a definition kind: `boss`, `raid`, `area`, `quest`, `drop`, `reward`, `shop`, `resource`. Module listings show logical module ids; definition listings show the stable key and its logical source module. |
| `::scripts reload` | Triggers one transactional reload and reports the truthful outcome. On success it reports the new generation and module count; on failure it reports the bounded candidate error and proves the previous generation remains live. |
| `::reload` | Same truthful reload as `::scripts reload`. |
| `::scriptdir` | Deprecated, sanitized alias of `::scripts status`. It accepts no arguments, emits a deprecation line plus the same logical status snapshot, and never returns a filesystem string. |

Denied callers (rights below 2) receive the generic denial with no inventory
or detail. Inspection is read-only and never executes guest code; it reads
the immutable active registry snapshot and the Java-owned runtime singletons
under their own monitors. Line output is bounded, listing is deterministic,
and the failure reason is capped. The implementation lives in the new
`com.rs2.script.diagnostics` package (`ScriptRuntimeStatus`,
`ScriptReloadResult`, `ScriptAdminCommands`) with `ScriptHost` providing
`getRuntimeStatus()` and `reloadWithResult()` seams.

## Current production content (Phase 5)

The compiled loader ships the Phase 5 declarative fixtures as eight
source-aware content modules, so `::scripts list modules` attributes every
definition to its logical source:

| Module | Defines |
|--------|---------|
| `dragon-island-drops` | `dragon_guardian_loot`, `elder_wizard_loot`, `dragon_king_loot`, `ancient_chest_loot` |
| `dragon-king` | The standalone Dragon King boss (loaded Black dragon 54, command entry, phases, armed specials, named table) |
| `encounter-warden` | The King Black Dragon boss (entry locks/camera, layered barrier, phased adds, dragonfire, private drops) |
| `dragon-awakens` | The persisted multi-stage quest and its interaction/lifecycle routes |
| `temple-of-zaros` | The two-player raid, its roster reward, and its private ground loot |
| `dragon-island-shops` | The scripted island general store |
| `dragon-island` | The activated Crandor area referencing the shop, quest, boss, and raid by id |
| `woodcutting-resources` | The tree and oak gathering resources |
| `cooking-skills` | Shrimp on cooking range 114 via `defineProcessingSkill` |
| `world-mobs` | Goblin (npc 100) via `defineMob` |
| `custom-namespace-overlays` | Item/NPC/object overlays at asset-pipeline ids 35000+ |

The area, boss, quest, and raid fixtures are the compiled production proofs:
`content/src/areas/dragon_island/` activates on the real Crandor map region
(11414/11415) with loaded NPCs, layered objects, the exact chest object-drop
route, and the scripted general store; `content/src/bosses/dragon-king.ts`
and `content/src/bosses/encounter-warden.ts` are the standalone bosses;
`content/src/raids/temple-of-zaros/raid.ts` is the two-player raid with the
embedded dragon-king boss, the roster-wide `zaros_raid_reward` commit, and
the `zaros_raid_loot` private ground roll; `content/src/quests/
dragon-awakens.ts` is the persisted quest with the generic journal; and
`content/src/resources/woodcutting.ts` is the gathering proof.

Their production-path evidence lives in the `ScriptAreaRuntimeTest`,
`ScriptAreaDropAuthorityTest`, `ScriptAreaObjectRouteTest`,
`ScriptShopRuntimeTest`, `ScriptRaidRuntimeTest`, `ScriptRaidRewardTest`,
`ScriptRaidProductionE2ETest`, `DragonAwakensProductionE2ETest`,
`ScriptBossProductionE2ETest`, `ScriptGatheringResourceE2ETest`, and
`ScriptAdminCommandsTest` suites, plus the vertical
`VerticalContentE2ETest` that loads the compiled `content/dist` loader and
crosses the manifest, area, shop, gathering, boss, quest, rejected reload,
and successful reload in one flow.
