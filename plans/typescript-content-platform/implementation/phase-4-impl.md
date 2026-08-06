---
type: planning
entity: implementation-plan
plan: "typescript-content-platform"
phase: 4
status: in_progress
created: "2026-07-29"
updated: "2026-08-01"
---

# Implementation Plan: Phase 4 - World and Encounter Services

> Implements [Phase 4](../phases/phase-4.md) of [TypeScript Content Platform](../plan.md)

## Approach

Add the remaining exact interaction hooks and an imperative, player-owned
encounter runtime without widening the Graal sandbox. Every executable packet
route, including the Phase 1 routes, will use one `ScriptHost` API that captures
the active `RegistryStore.State`, context, and generation and performs exact
lookup plus invocation while holding the same generation lease. A successful
candidate publishes those three values together; a failed candidate publishes
nothing and leaves the old generation executable.

Packet dispatch follows one normative state machine:

1. Require the exact payload length and decode into local primitives without
   mutating player or world state.
2. Apply the route's universal safety validation whether or not an exact script
   key is registered.
3. Reject invalid traffic without guest, legacy, plugin-event, task-cancellation,
   facing, or other side effects.
4. Apply the action-lock gate.
5. Under one `ScriptHost` lease, look up the exact key in the captured registry
   and invoke it once if present.
6. Treat a matched success, guest exception, or rejected callback result as
   consumed; only a valid unmatched key continues into the existing legacy
   path.

World mutation is reachable only through a generation-bound
`ScriptEncounterHandle`. Its owner is automatically its first participant. A
live player may belong to at most one encounter. The service owns identity
indexes for encounters, participants, NPCs, object footprints, ground items,
tasks, locks, camera controls, and callbacks. Every close or lifecycle removal
uses compare-and-remove with both the encounter token and backing object
identity. Cleanup is idempotent and invokes no guest code.

Player death is split into an authoritative transition and a later read-only
observation. Encounter cleanup and death snapshot capture happen at the tick
that calls `applyDead`; all mandatory `applyDead` work completes; only then is
the immutable snapshot delivered. NPC death uses a captured NPC identity,
explicit critical-section guard, and FIFO deferred destructive-action queue so
a callback can request `despawn()` or `close()` without invalidating the
remainder of the death loop.

Object replacement is a collision transaction, not a tile-list edit. One
resolver defines cache-versus-dynamic visibility, reserves every touched
footprint cell, snapshots movement and projectile masks, applies and verifies
the replacement, and restores the exact snapshot on cleanup. Ground rewards
use stable item tokens and player object identity. Pickup and packet 25 share
one visibility resolver; exact claim plus inventory transfer is atomic.
Rewards may be detached with a bounded private TTL so encounter close does not
delete a just-awarded drop.

Preserve Java 8 source/target compatibility, GraalJS `23.1.0`,
`HostAccess.EXPLICIT`, and the existing bans on host class lookup, host
filesystem access, process creation, sockets, native access, and guest-created
threads. Phase 4 exposes low-level numeric drop and encounter services only.
Declarative boss/raid/area consumption, string item resolution, loot builders,
and shop definitions remain Phase 5.

### Normative Public Bridge Contract

The following declarations are normative for every Phase 4 addition. Existing
Phase 1-3 inventory, bank, skill, dialogue, state, quest, command, NPC, object,
item, area, login/logout, pickup, and scheduler declarations remain source
compatible. Replace the existing `ScriptedPlayer` declaration with the complete
merged declaration below and add every other declaration verbatim to
`content/src/core/runtime.ts`.

```ts
export interface ScriptArray<T> {
  length(): number;
  get(index: number): T | null;
}

export interface ScriptedPosition {
  readonly x: number;
  readonly y: number;
  readonly plane: number;
}

export type EquipmentSlot =
  | "hat"
  | "cape"
  | "amulet"
  | "weapon"
  | "chest"
  | "shield"
  | "legs"
  | "hands"
  | "feet"
  | "ring"
  | "arrows";

export type ScriptAudience = "self" | "nearby";
export type GraphicHeight = "low" | "high";

export interface ScriptLockHandle {
  token(): string;
  isActive(): boolean;
  release(): boolean;
}

export interface ScriptCameraSession {
  token(): string;
  isActive(): boolean;
  position(
    localX: number,
    localY: number,
    height: number,
    speed: number,
    angle: number,
  ): boolean;
  lookAt(
    localX: number,
    localY: number,
    height: number,
    speed: number,
    angle: number,
  ): boolean;
  shake(
    axis: number,
    intensity: number,
    speed: number,
    frequency: number,
  ): boolean;
  release(): boolean;
}

export interface ScriptedEquipment {
  get(slot: EquipmentSlot): number | null;
  amount(slot: EquipmentSlot): number;
}

export interface ScriptedCombat {
  hp(): number;
  maxHp(): number;
  inCombat(): boolean;
  damage(amount: number): number;
  heal(amount: number): number;
}

export interface ScriptedMovement {
  face(x: number, y: number): boolean;
  walkTo(x: number, y: number): boolean;
  teleport(x: number, y: number, plane: number): boolean;
  runEnergy(): number;
  setRunEnergy(amount: number): boolean;
  lock(ticks: number): ScriptLockHandle | null;
}

export interface ScriptedActions {
  lock(ticks: number): ScriptLockHandle | null;
}

export interface ScriptedPresentation {
  animate(animationId: number, delay: number): boolean;
  graphic(graphicId: number, height: GraphicHeight): boolean;
  forcedChat(text: string): boolean;
  sound(soundId: number, volume: number, delay: number): boolean;
  showInterface(interfaceId: number): boolean;
  closeInterfaces(): boolean;
  setText(componentId: number, text: string): boolean;
  setItemModel(componentId: number, itemId: number, zoom: number): boolean;
  openStaticShop(shopId: number): boolean;
  stillGraphic(
    graphicId: number,
    x: number,
    y: number,
    plane: number,
    height: number,
    delay: number,
    audience: ScriptAudience,
  ): boolean;
  projectile(
    graphicId: number,
    fromX: number,
    fromY: number,
    toX: number,
    toY: number,
    plane: number,
    angle: number,
    speed: number,
    startHeight: number,
    endHeight: number,
    delay: number,
    audience: ScriptAudience,
  ): boolean;
  beginCamera(ticks: number): ScriptCameraSession | null;
  resetCamera(): boolean;
}

export interface ScriptedPlayer {
  getUsername(): string;
  getX(): number;
  getY(): number;
  getPlane(): number;
  getCombatLevel(): number;
  getRights(): number;
  getPosition(): ScriptedPosition;
  message(text: string): void;
  teleport(x: number, y: number, plane?: number): void;
  getSkills(): ScriptedSkills;
  getInventory(): ScriptedInventory;
  getBank(): ScriptedBank;
  getDialogue(): ScriptedDialogue;
  animate(animationId: number): boolean;
  graphic(graphicId: number): boolean;
  sound(soundId: number): boolean;
  closeInterfaces(): void;
  showInterface(interfaceId: number): boolean;
  after(ticks: number, handler: ScheduledHandler): ScriptTaskHandle;
  every(ticks: number, handler: ScheduledHandler): ScriptTaskHandle;
  state(namespace: string): PlayerStateNamespace;
  quest(id: string): ScriptedQuest | null;
  questPoints(): number;
  getEquipment(): ScriptedEquipment;
  getCombat(): ScriptedCombat;
  getMovement(): ScriptedMovement;
  getActions(): ScriptedActions;
  getPresentation(): ScriptedPresentation;
  beginEncounter(
    id: string,
    minX: number,
    minY: number,
    maxX: number,
    maxY: number,
    plane: number,
  ): ScriptEncounterHandle | null;
}

export interface ScriptPlayerSnapshot {
  username(): string;
  position(): ScriptedPosition;
  combatLevel(): number;
  rights(): number;
}

export interface ScriptNpcSnapshot {
  id(): number;
  name(): string;
  position(): ScriptedPosition;
  maxHp(): number;
}

export interface ScriptedGroundItemView {
  token(): string;
  id(): number;
  amount(): number;
  position(): ScriptedPosition;
  isPrivateToPlayer(): boolean;
}

export interface ButtonScriptContext {
  readonly player: ScriptedPlayer;
  readonly target: null;
  readonly action: "button";
  readonly buttonId: number;
}

export interface ItemOnGroundItemScriptContext {
  readonly player: ScriptedPlayer;
  readonly target: ScriptedGroundItemView;
  readonly action: "item-on-ground-item";
  readonly item: ScriptedItem;
  readonly slot: number;
}

export interface ItemOnPlayerScriptContext {
  readonly player: ScriptedPlayer;
  readonly target: ScriptedPlayer;
  readonly action: "item-on-player";
  readonly item: ScriptedItem;
  readonly slot: number;
}

export interface MagicOnItemScriptContext {
  readonly player: ScriptedPlayer;
  readonly target: ScriptedItem;
  readonly action: "magic-on-item";
  readonly spellId: number;
  readonly slot: number;
}

export interface MagicOnObjectScriptContext {
  readonly player: ScriptedPlayer;
  readonly target: ScriptedObject;
  readonly action: "magic-on-object";
  readonly spellId: number;
}

export interface PlayerDeathScriptContext {
  readonly player: ScriptPlayerSnapshot;
  readonly killer: ScriptPlayerSnapshot | null;
  readonly position: ScriptedPosition;
  readonly action: "death";
}

export interface EncounterNpcDeathScriptContext {
  readonly encounter: ScriptEncounterHandle;
  readonly npc: ScriptNpcSnapshot;
  readonly killer: ScriptedPlayer | null;
  readonly position: ScriptedPosition;
  readonly action: "encounter-npc-death";
}

export interface ScriptNpcHandle {
  token(): string;
  id(): number;
  position(): ScriptedPosition;
  hp(): number;
  maxHp(): number;
  isAlive(): boolean;
  setTarget(player: ScriptedPlayer): boolean;
  clearTarget(): boolean;
  face(x: number, y: number): boolean;
  walkTo(x: number, y: number): boolean;
  damage(amount: number, source: ScriptedPlayer | null): number;
  heal(amount: number): number;
  animate(animationId: number, delay: number): boolean;
  graphic(graphicId: number, height: GraphicHeight): boolean;
  forcedChat(text: string): boolean;
  despawn(): boolean;
}

export interface ScriptObjectHandle {
  token(): string;
  id(): number;
  position(): ScriptedPosition;
  type(): number;
  rotation(): number;
  isActive(): boolean;
  remove(): boolean;
}

export interface ScriptGroundItemHandle {
  token(): string;
  id(): number;
  amount(): number;
  position(): ScriptedPosition;
  identityCount(): number;
  isAttached(): boolean;
  isClaimed(): boolean;
  detach(privateTicks: number): boolean;
  remove(): boolean;
}

export interface ScriptDropEntry {
  readonly itemId: number;
  readonly minAmount: number;
  readonly maxAmount: number;
  readonly weight: number;
  readonly always: boolean;
}

export interface ScriptDropResult {
  itemId(): number;
  amount(): number;
  groundItems(): ScriptArray<ScriptGroundItemHandle>;
}

export interface ScriptEncounterHandle {
  id(): string;
  owner(): ScriptedPlayer;
  isOpen(): boolean;
  addParticipant(player: ScriptedPlayer): boolean;
  removeParticipant(player: ScriptedPlayer): boolean;
  participants(): ScriptArray<ScriptedPlayer>;
  spawnNpc(
    npcId: number,
    x: number,
    y: number,
    plane: number,
    hp: number,
    maxHit: number,
    attack: number,
    defence: number,
  ): ScriptNpcHandle | null;
  replaceObject(
    x: number,
    y: number,
    plane: number,
    expectedId: number,
    expectedType: number,
    expectedRotation: number,
    replacementId: number,
    replacementType: number,
    replacementRotation: number,
  ): ScriptObjectHandle | null;
  removeObject(
    x: number,
    y: number,
    plane: number,
    expectedId: number,
    expectedType: number,
    expectedRotation: number,
  ): ScriptObjectHandle | null;
  dropFor(
    player: ScriptedPlayer,
    itemId: number,
    amount: number,
    x: number,
    y: number,
    plane: number,
  ): ScriptGroundItemHandle | null;
  after(ticks: number, callback: ScheduledHandler): ScriptTaskHandle | null;
  every(ticks: number, callback: ScheduledHandler): ScriptTaskHandle | null;
  onNpcDeath(
    npc: ScriptNpcHandle,
    callback: (context: EncounterNpcDeathScriptContext) => void,
  ): boolean;
  nextInt(bound: number): number;
  chance(numerator: number, denominator: number): boolean;
  rollDrops(
    player: ScriptedPlayer,
    x: number,
    y: number,
    plane: number,
    privateTicks: number,
    entries: readonly ScriptDropEntry[],
  ): ScriptArray<ScriptDropResult>;
  contains(x: number, y: number, plane: number): boolean;
  distance(first: ScriptedPosition, second: ScriptedPosition): number;
  isWalkable(x: number, y: number, plane: number): boolean;
  hasProjectilePath(
    fromX: number,
    fromY: number,
    toX: number,
    toY: number,
    plane: number,
  ): boolean;
  close(): boolean;
}

export type OnButton = (
  buttonId: number,
  handler: (context: ButtonScriptContext) => void,
) => void;

export type OnItemOnGroundItem = (
  itemId: number,
  groundItemId: number,
  handler: (context: ItemOnGroundItemScriptContext) => void,
) => void;

export type OnItemOnPlayer = (
  itemId: number,
  handler: (context: ItemOnPlayerScriptContext) => void,
) => void;

export type OnMagicOnItem = (
  spellId: number,
  itemId: number,
  handler: (context: MagicOnItemScriptContext) => void,
) => void;

export type OnMagicOnObject = (
  spellId: number,
  objectId: number,
  handler: (context: MagicOnObjectScriptContext) => void,
) => void;

export type OnPlayerDeath = (
  handler: (context: PlayerDeathScriptContext) => void,
) => void;

declare global {
  const onButton: OnButton;
  const onItemOnGroundItem: OnItemOnGroundItem;
  const onItemOnPlayer: OnItemOnPlayer;
  const onMagicOnItem: OnMagicOnItem;
  const onMagicOnObject: OnMagicOnObject;
  const onPlayerDeath: OnPlayerDeath;
}
```

`ScriptArray<T>` is implemented by a final Java `ScriptArray` that copies its
input into a private `Object[]` and exports only `int length()` and
`Object get(double)`. `get` rejects non-finite/fractional input before narrowing
and returns `null` for it or an index outside `0..length-1`. It exports no
setter, iterator, spliterator, raw
array, collection, or component type. `allowArrayAccess(true)` may remain for
legacy bridge values, but Phase 4 returns no Java array. Tests cover length,
ordered `get`, invalid indexes, absence of iteration, and rejection/no effect
of property assignment.

All Java implementation classes are `final` where inheritance is unnecessary.
Constructors, backing fields, engine objects, and service accessors are not
exported. The exact Java-to-TypeScript method mapping is:

| TypeScript type | Java class | Export contract |
|-----------------|------------|-----------------|
| `ScriptArray<T>` | `com.rs2.script.ScriptArray` | `length(): int`, `get(double): Object`; finite/integral validation and copied immutable contents. |
| Six new contexts | `com.rs2.script.context.*` | `@HostAccess.Export public final` fields named exactly as each readonly TS property; no mutable engine member. |
| `ScriptPlayerSnapshot`, `ScriptNpcSnapshot` | `com.rs2.script.snapshot.*` | Value-copy strings, ids, coordinates, levels/HP only. |
| `ScriptedEquipment`, `ScriptedCombat`, `ScriptedMovement`, `ScriptedActions`, `ScriptedPresentation`, `ScriptCameraSession` | `com.rs2.script.capability.*` | Every method and return type exactly matches the TS declaration; wrappers carry player identity plus generation. |
| Encounter and resource handles | `com.rs2.script.world.*Handle` | Every method above is `@HostAccess.Export`; token strings are opaque unsigned-long decimal values. |
| Drop entry parsing | `ScriptDropEntryParser` | `rollDrops` receives Graal `Value`, requires an array with only the five declared readable members, and copies to unexported Java values before RNG use. |
| Registration handlers | `ScriptFunctions`, `ScriptBindings` | Java signatures receive raw `Value` ids/callbacks, validate finite integral ids before narrowing, and reject singleton/exact-key duplicates during candidate evaluation. |

Registration ids must be integral. A button id is accepted only when
`0 <= buttonId <= 255255`, `buttonId / 1000 <= 255`, and
`buttonId % 1000 <= 255`, using integer quotient/remainder after integrality
validation. Thus `255255` is valid while unreachable keys such as `256` and
`999` reject the whole candidate. Item and NPC ids are `0..14999` with a
loaded definition; spell, object, interface, component, animation, graphic,
and sound ids are `0..65535`. `-1` is accepted only as the
`replaceObject.expectedId` empty-tile sentinel, paired with expected type and
rotation `-1`, and as the existing animation clear value. Null callbacks,
non-executable callbacks, unknown equipment slot strings, non-finite numbers,
and fractional numbers are rejected before narrowing.

Every new exported Java method that receives a TypeScript `number` uses
`double` (or raw Graal `Value` for registration/object parsing) at the host
boundary, checks finite, integral, and operation-specific range, and only then
narrows to `int`/`long` for an internal engine method. No new exported method
accepts an `int` directly from guest input. Numeric return values remain the
Java `int`/`long` type implied by the TypeScript declaration.

Registration mistakes throw and reject the whole candidate. Runtime
validation never throws across the bridge: boolean mutators return `false`,
numeric live queries/mutators return `0` except `nextInt`/`distance`, which
return `-1`, factories declared with `| null` return `null`, and
array-producing operations return an empty `ScriptArray`. A stale generation,
closed handle, wrong backing identity, output stream absence,
dead/disconnected player, or failed postcondition uses that operation's
declared failure result. Non-null identity accessors have immutable snapshot
semantics: `ScriptEncounterHandle.owner()` returns the wrapper captured at
creation; NPC `id/maxHp/position`, object `id/type/rotation/position`, and
ground-item `id/position/initial amount` are copied at handle creation and
remain readable after close. NPC position is replaced with a new immutable
snapshot after each successful owned move; a failed move leaves the prior
snapshot. Live `hp/isAlive/isActive/isAttached/isClaimed` queries use their
numeric/boolean failure values. Callback exceptions are reported through
`ScriptExecutor`, consume exact packet routes, and close only the owning
encounter when the callback is encounter-owned.
The pre-Phase-4 `void` compatibility methods (`message`, `teleport`,
`closeInterfaces`, dialogue/bank/skill setters) remain `void` and no-op after
failed validation; their new facade equivalents provide truthful booleans.

### Normative Bounds and Postconditions

| API | Accepted input and truthful success condition |
|-----|-----------------------------------------------|
| Equipment | Only the 11 literals above map to `ItemConstants` indexes `0,1,2,3,4,5,7,9,10,12,13`. Empty equipment returns `null`/`0`; raw array indexes `6,8,11` are unsupported. |
| Player/NPC HP | Amount is integral `1..32767`. `damage` clamps to current HP and enters the engine hit/death path; it cannot damage an already-dead entity. `heal` clamps to base max HP and cannot revive a dead entity. Return value is the observed HP delta. |
| Movement | Coordinates `0..16383`, plane `0..3`, loaded region, live player/NPC, and encounter area. `face` succeeds only when the face fields equal the request. `walkTo` uses a new `PathFinder.tryFindRoute` result and succeeds only if a route was queued. `teleport` succeeds only after authoritative position/region fields reflect the destination. |
| Run energy/locks | Energy is integral `0..100`. Lock duration is `1..100000` game ticks. Action and movement locks stack independently and release only their exact token. |
| Text/visuals | Animation accepts `-1..65535`, animation delay `0..255`; graphic/sound ids `0..65535`; graphic height is exact literal; force-chat length `1..80`; interface text length `0..512`; general `message` compatibility delegate remains capped at 512. |
| Sound/interface/model | Volume `0..100`, sound delay `0..255`, interface/component ids `0..65535`, item id `0..14999` with definition, zoom `1..2000`. Interface success requires live output state, no trade/duel refusal, and expected `lastMainFrameInterface`. Close succeeds only after interface state is reset. |
| Static shop | Shop id `0..ShopHandler.MAX_SHOPS-1`, loaded by `shops.json`, marked in a new immutable `staticShopConfigured[]` provenance table, non-empty name, valid modifiers, and not a player-store slot. Success requires `myShopId` and shop UI state to match. |
| World graphics | Coordinates `0..16383`, plane `0..3`, loaded and same plane; both endpoints in encounter area when bound; Chebyshev distance from player and between projectile endpoints at most 25. Height/delay/angle/start/end values `0..255`, projectile speed `1..255`, audience exact literal. Success means the packet was queued to every selected live output stream. |
| Camera | `beginCamera` ticks `1..100000`; at most one active session per player. It fails rather than replacing an active session. On that same session, position/look-at local x/y are `0..103`, height `0..65535`, speed/angle `0..255`; shake axis `0..3`, intensity/speed/frequency `0..4`. Updates queue their packet without reset. Exact release, expiry, `resetCamera`, close, death, logout, or reload atomically marks the session inactive and queues exactly one reset; repeated cleanup queues none. |
| Encounter | Id matches `[a-z0-9][a-z0-9._-]{0,63}`. Rectangle is inclusive and loaded, width/height `1..64`, plane `0..3`; the live owner need not start inside it. `beginEncounter` atomically reserves the rectangle before returning, then content may teleport the owner into it. Max per generation: 64 encounters; per encounter: 8 participants, 16 NPCs, 32 object mutations, 128 ground identities, 32 tasks, 32 NPC callbacks, and 16 action/movement lock tokens per participant plus one camera session per participant. |
| NPC spawn | Definition-backed id; coordinates in area; HP `1..32767`; max hit/attack/defence `0..32767`; free NPC slot. Returned handle must resolve the exact installed object and slot token. |
| Objects | Expected id is `-1` with expected type/rotation `-1`, or a definition-backed id with expected type `0..22` and rotation `0..3`. Replacement id is definition-backed with its independent replacement type `0..22` and rotation `0..3`. Coordinate is in area; resolver must exactly match all three expected fields before footprint reservation. `removeObject` accepts the expected triple and installs the engine's empty state. |
| Rewards | Item definition exists; amount `1..1000000`; player is current participant; position is in area and same plane. A stackable logical result is one identity with the amount; a non-stackable result is `amount` distinct identities of amount 1, subject to the 128-identity limit. |
| Detach | Private TTL `1..1000` item ticks. Exact attached token transfers from encounter cleanup index to player-private expiry index; it never becomes globally visible and close no longer removes it. |
| Spatial | `distance` requires two valid same-plane positions and returns Chebyshev distance or `-1`; walkability/projectile checks require loaded in-area cells and return the authoritative clipping result. |

### Normative Packet Matrices

The common player predicate is: handler's source object is still the exact
`PlayerHandler.players[playerId]` identity; initialized; active; not
disconnected; not dead; not in the death/respawn transition; not teleporting;
and has a live packet stream. Targeted routes add the predicates shown below.

| Opcode | Exact payload/decode | Exact key | Universal validation and precedence | Action-lock result | Valid unmatched continuation |
|--------|----------------------|-----------|-------------------------------------|--------------------|------------------------------|
| 185 `ClickingButtons` | Length `2`; `first = u8`, `second = u8`, `buttonId = first * 1000 + second`. | `buttonId` | Common player; the decoded quotient/remainder are inherently `<=255`, and registration applies the same sparse-domain rule. Before this table only an authenticated pending scripted-dialogue option may resolve: same player identity, active generation, pending callback token, and button mapping to one offered option. | Locked traffic is dropped before glass/glider/emote/quest/event/switch helpers. The authenticated pending dialogue option is the sole exception and releases only its dialogue token. | Existing helper/event/switch sequence exactly as before. |
| 25 `ItemOnGroundItem` | Length `12`; consume ignored signed word; `usedItemId = signedWordA`, `groundItemId = unsignedWord`, `y = signedWordA`, `slot = signedWordBigEndianA`, `x = unsignedWord`. | Ordered `(usedItemId, groundItemId)` | Common player; ids definition-backed; slot `0..27`; exact inventory identity `playerItems[slot] - 1 == usedItemId`; coordinates `0..16383`; shared resolver returns one visible exact creation token on player's plane; Chebyshev distance `<=1`. Player-private identity wins over public identity; lowest creation token breaks ties. | Drop with no guest or legacy effect. | The validated continuation receives that exact token. Firemaking carries it through its scheduled action and may remove only that identity after revalidation. |
| 14 `ItemOnPlayer` | Length `4`; `targetIndex = unsignedWord`, `slot = signedWordBigEndian`. Derive item id only after slot validation. | `usedItemId` | Common player; slot `0..27`; exact inventory identity and definition; target index `1..PlayerHandler.players.length-1`; captured target remains same array identity, initialized/active/not disconnected/not dead; target is not source; same plane; Chebyshev distance `<=1`. Self use is invalid. | Drop with no guest or legacy effect. | Existing cracker/item-on-player behavior receives captured target and validated slot. |
| 237 `MagicOnItems` | Length `8`; `slot = signedWord`, `itemId = signedWordA`, consume ignored signed word, `spellId = signedWordA`. | Ordered `(spellId, itemId)` | Common player; slot `0..27`; exact inventory identity; definition-backed item; spell `0..65535`. This non-world target has no distance or plane predicate. | Drop before `endCurrentTask`, `usingMagic`, legacy magic, or plugin event. | Existing order: task cancellation, magic state, legacy handler, plugin event. |
| 35 `MagicOnObject` | Length `8`; `x = signedWordBigEndian`, `spellId = unsignedWord`, `y = unsignedWordA`, `objectId = signedWordBigEndian`. | Ordered `(spellId, objectId)` | Common player; spell/object `0..65535`, definition-backed object; coordinates `0..16383`; same plane; Chebyshev distance `<=5`. Resolver precedence: any dynamic object occupying the coordinate masks cache state; its id/type/rotation must match. Only when no dynamic state occupies the coordinate may cache state match. | Drop before facing, orb charging, or any event. | Existing turn/orb switch path receives the resolved object. |

For every opcode, the outcome table is mandatory:

| Validation | Exact registration | Callback | Outcome |
|------------|--------------------|----------|---------|
| Invalid | Present | Not called | Consume/drop; zero guest, legacy, plugin, or pre-dispatch side effects. |
| Invalid | Absent | Not called | Consume/drop with the same zero-side-effect result. |
| Valid and unlocked | Present | Returns | Consume once. |
| Valid and unlocked | Present | Throws | Report once and consume; do not fall back. |
| Valid and unlocked | Absent | Not called | Run the validated legacy continuation once. |
| Valid and action-locked | Present or absent | Not called | Consume/drop, except the authenticated opcode-185 dialogue escape. |

Opcode 253 `ItemClick2OnGroundItem` is an existing compatibility route, not a
new script registration, but it obeys the same universal safety and lock
ordering. Require payload length `6`, then decode locals in current order:
`itemX = signedWordBigEndian`, `itemY = signedWordBigEndianA`, and
`itemId = unsignedWordA`. Before debug output, task cancellation, messages,
Telekinetic statue reset, or Firemaking, require the common live-player
predicate, coordinates `0..16383`, a definition-backed item `0..14999`, exact
player plane, `player.absX == itemX && player.absY == itemY`, and one exact
visible ground creation token from the same private-first/lowest-token resolver
used by opcodes 25 and 236. Invalid or action-locked traffic is dropped with no
side effect. Valid unlocked traffic preserves the existing statue/log/other
continuation; a log continuation passes the already-resolved token into
`Firemaking.attemptFire` and never re-resolves by tuple.

### Generation-Leased Dispatch API

Add this single public-to-engine dispatch seam to `ScriptHost`; packet classes
must not read `RegistryStore.active()` directly:

```java
public synchronized DispatchResult dispatchActive(
        RegistryLookup lookup,
        RegisteredInvocation invocation)
```

`RegistryLookup.find(RegistryStore.State)` returns the exact `Value` from the
captured state. `RegisteredInvocation.invoke(long generation, Value handler)`
constructs the context with that generation and calls `ScriptExecutor`.
`DispatchResult` is `NO_ACTIVE_CONTEXT`, `UNMATCHED`, or `CONSUMED`. Lookup,
generation capture, context construction, callback execution, and result
selection occur while the same `ScriptHost` monitor/lease is held. A matched
guest failure still returns `CONSUMED`.

`ScriptHost` gains `activeRegistryState`; successful replacement assigns the
candidate state, candidate context, and incremented generation while the
monitor is held, then performs old-generation cancellation/lifecycle cleanup
and closes the old context in the existing safe order. Failed replacement
never changes any of the three. Migrate command, NPC clicks, object clicks,
item clicks, item-on-item, item-on-object, item-on-NPC, and all five new packet
routes to this API. Scheduler and lifecycle callbacks retain their equivalent
leased APIs. A latch-based test pauses after state lookup, races reload, and
proves no old-handler/new-context or new-handler/old-context combination can
execute.

### Encounter Ownership and Lifecycle

The encounter id scope is `(generation, owner Player identity, normalized id)`;
the same id may be used by different owners, never twice for one owner. Owner
is automatically a participant. `beginEncounter` fails when the owner or any
requested participant is already indexed by another open encounter. It also
fails when its inclusive rectangle intersects an active rectangle on the same
plane; edge/corner contact counts only when the rectangles share a cell. Every
mutating `ScriptedPlayer` facade asks the encounter service whether its backing
player is indexed; if so, destinations, targets, broadcasts, and task/lock
creation must satisfy that exact encounter token and area even when content
retained a wrapper created before `beginEncounter`.

Service indexes are:

- composite encounter key and opaque encounter token;
- `(plane, inclusive rectangle)` reservation plus generation/encounter token;
- owner identity and participant identity;
- NPC slot plus allocation token plus `Npc` identity;
- every reserved object footprint cell plus object transaction token;
- ground creation token plus exact `GroundItem` identity;
- scheduler task id, action lock token, movement lock token, and camera-session
  token;
- NPC death callback token.

`beginEncounter` performs every id, owner, generation, capacity, loaded-region,
rectangle, and overlap check under the synchronized service before changing
player or world state. It then installs the area reservation and encounter
identity atomically; if handle construction cannot finish, it compare-removes
both before returning `null`. Teleport, locks, NPC/object/item creation, and
callbacks occur only after a handle is returned. This lets command entry
reserve a remote arena first and then teleport its owner safely.

The reservation is a shared-world isolation boundary:

- `Walking.processPacket` rejects a nonparticipant step entering a reserved
  cell and a participant step leaving its own rectangle before path, duel,
  skilling, or movement mutation. Central gameplay teleport/movement helpers
  apply the same rule; encounter-owned teleport into the just-reserved area,
  death/lifecycle relocation, administrator recovery, and cleanup are explicit
  privileged reasons rather than boolean bypasses.
- `PlayerHandler.updateNPC` excludes an encounter-owned NPC from every
  nonparticipant's existing/add-new NPC list and removes it on the next rebuild.
  Encounter object broadcasts and encounter-scoped `"nearby"` presentation
  target participants only. Private ground rewards already use exact player
  identity. The shared collision masks remain authoritative, but
  nonparticipants cannot enter the reserved rectangle.
- Refactor every `ClickNPC` opcode to decode/capture a bounds-checked NPC slot
  before its current common resets. If that exact NPC token is encounter-owned,
  `canTargetOwnedNpc(player,npc,slotToken)` requires exact current participant
  identity, same encounter, inside rectangle, and same plane. Failure drops the
  click before task cancellation, facing, follow, dialogue/event, or combat
  state.
- `CombatAssistant.attackNpc`, `attackingNpcTick`, projectile setup, and
  `delayedHit` repeat the same token/identity authorization before follow,
  ammunition, animation, projectile, hit, damage, or killer attribution.
  Failure clears `npcIndex`, `oldNpcIndex`, `followNpcId`, pending hit/projectile
  state, and causes no HP change. `DwarfCannon` target/damage selection applies
  the same predicate. NPC target/follow/attack continuation in `NpcHandler` and
  `NpcCombat` likewise permits an owned NPC to target only a current
  participant. Unowned NPC/player combat is unchanged.

Compare-and-remove requires both token and identity. Owner death, logout,
player removal, callback failure, explicit close, and successful reload close
the encounter. Non-owner death/logout removes that participant, releases only
their locks/camera/targets/private attached rewards, and leaves the encounter
open. Failed reload changes nothing. Close order is: mark closed and remove
callback registrations; cancel tasks; release camera/action/movement tokens;
clear NPC targets; request/despawn NPCs; remove attached rewards; restore
objects in reverse transaction order; clear participants/resource indexes;
finally compare-remove the exact area reservation using plane, rectangle,
generation, and encounter token. The reservation remains held throughout
cleanup, so a second begin cannot observe a partially restored arena. During an
NPC death critical section, destructive NPC/object/reward cleanup is accepted
but queued, then drained FIFO after the critical section.

### Player and NPC Death Ordering

At the `Player.process` transition where `isDead && respawnTimer == -6`, call
`ScriptLifecycleService.beginPlayerDeath(player)` before `applyDead()`. It:

1. verifies the exact live player identity and ensures the transition token is
   new;
2. selects the killer from positive `damageTaken[]` only, with an in-range
   player index, exact `PlayerHandler.players[index]` identity, active/live
   target, and target not equal to the dying player;
3. uses greatest positive damage, preserving the lowest player index on a tie;
   no positive damage, invalid identity, or self attribution produces `null`;
4. copies player, killer, and position into immutable snapshots and closes or
   removes encounter ownership before core death bookkeeping.

The tick then calls `applyDead()` to completion. Only after it returns does
`completePlayerDeath(token)` invoke `onPlayerDeath` with the stored snapshots.
The context has no `ScriptedPlayer`, inventory, state, teleport, task, or
presentation capability. Exceptions are contained after death is already
complete. `giveLife()` remains the later mandatory item/respawn seam and is not
replaced by script.

Refactor the NPC death branch in `NpcHandler.process()` as one explicit
critical section:

1. Capture `Npc npc = npcs[i]`, allocation token, slot, death position, and
   killer identity; verify the slot still contains that object.
2. Enter `ScriptNpcDeathGuard` for that token.
3. If and only if the exact identity is encounter-owned, suppress
   `dropItems(i)` and clue-drop creation. Unowned identities keep both.
4. Complete mandatory legacy bookkeeping on the captured object: Fight Caves
   and Jad handling, Slayer XP, tutorial/minigame/quest/kill-count updates,
   `resetEvent`, combat-target resets, and respawn/despawn decisions.
5. Complete every required slot/object dereference before guest execution and
   create an immutable NPC death snapshot.
6. Invoke the existing NPC-id `onNpcDeath` observer and then the exact
   encounter callback. Both are deliberately moved after mandatory bookkeeping
   and all slot dereferences. `npc.despawn()`, `encounter.close()`, object
   cleanup, and ground cleanup called from either return accepted/deferred; an
   encounter-callback exception queues encounter close while the existing
   observer retains its current failure containment.
7. Exit the guard and drain its FIFO queue. Each action rechecks slot,
   allocation token, backing identity, and resource token. No guest callback
   runs during drain and `NpcHandler.process()` performs no later `npcs[i]`
   dereference.

### Object Transaction and Ground Reward Protocols

`WorldObjectService` becomes the sole authoritative mutable object index and
the resolver used by clicks, magic-on-object, login/rebuild, collision, and
encounter mutation. The existing stores are inventoried and assigned these
layers, highest visible precedence first:

1. an active encounter transaction;
2. an active timed `ObjectManager.objects` entry (`objectId` until expiry,
   followed by its `newId` transition);
3. an `ObjectHandler.globalObjects` dynamic entry;
4. immutable startup/cache state loaded through `RegionFactory` and currently
   represented by `Region.realObjects`.

At most one visible object per `(x,y,plane)` is returned from those layers;
type and rotation are part of identity. `Region.realObjects` becomes the
base/cache projection only and never receives a non-startup dynamic append.
`ObjectManager.objects` and `ObjectHandler.globalObjects` remain private
compatibility/tick projections owned by `WorldObjectService`; no resolver scans
them independently. Resolution returns id, type, rotation, plane, definition,
layer, backing identity, and layer version. Client-only `PacketSender.object`
and `checkObjectSpawn` calls are output operations and cannot establish
authoritative world state.

Route all central world writers through
`WorldObjectService.apply(ObjectMutation)`: `RegionFactory`/`Region.addObject`,
every `ObjectHandler.createAnObject/addObject/removeObject/placeObject/process`
entry, `ObjectManager.addObject/removeObject/placeObject/updateObject/process`
and the `com.rs2.game.objects.Object` constructor, plus current callers in
`GateHandler`, `Doors`, `DoubleDoors`, `ClimbOther`, `DwarfCannon`, `Mining`,
`Firemaking`, `Stalls`, `DesertCactus`, `Flowers`, `NpcHandler`,
`ObjectsActions`, `FlourMill`, `OpenObject`, `OtherObjects`, `Pickable`, `Webs`,
`Balloons`, `PartyRoom`, `Trawler`, `Commands`, and `ItemOnObject`. Direct
`Region.addObject` remains a compatibility delegate to the service, so missed
call sites cannot bypass reservation/collision accounting. A writer receives
`APPLIED`, `DEFERRED_BY_RESERVATION`, or `INVALID`; a timed expiry deferred by
an encounter retains its projection and queues its exact mutation FIFO. The
queue drains after encounter restoration and revalidates its original backing
identity/version before applying.

`Region` gains a collision-contributor adapter: immutable base movement and
projectile masks plus per-object bit contributors/refcounts. Cache loading and
all dynamic layers use it. Its dry-run calculator enumerates every touched
cell, including adjacent wall cells and each rotated solid footprint cell. The
public `replaceObject` receives independent
`(expectedId,expectedType,expectedRotation)` and
`(replacementId,replacementType,replacementRotation)` triples. An encounter
mutation:

1. resolves and compares expected object state;
2. computes the union of old and replacement footprints and atomically
   reserves all cells, rejecting any overlap;
3. snapshots visible state, contributor versions, and exact movement and
   projectile masks for every union cell;
4. under the world/game-cycle owner installs the encounter layer, removes the
   previously visible contributor, adds the replacement contributor, updates
   the authoritative index and projections, compares every expected mask, and
   broadcasts the replacement to encounter participants;
5. on any apply failure reverses all writes before returning `null`;
6. on cleanup compares reservation token, visible object identity,
   contributor versions, and expected post-masks, reverses contributors,
   restores every exact mask and visible snapshot, broadcasts restoration,
   and releases the reservation.

All engine object writers enter this adapter, so a reserved footprint defers a
conflicting later write rather than silently overwriting it. Cleanup restores
the exact prior layer state, broadcasts it to participants, releases the
footprint, and then drains valid deferred mutations. A comparison mismatch is
an invariant failure: log/quarantine the transaction, do not erase the
conflicting object, and retain the footprint reservation for administrative
reconciliation. Tests inject the mismatch and prove unrelated state is not
clobbered.

`GroundItem` gains an unexported monotonic creation token, exact private
`Player` identity, encounter token, `scriptPrivate`, claimed/detached flags,
and private expiry ticks. `ItemHandler.resolveVisibleGroundItem` selects the
lowest-token eligible exact-private identity first, then the lowest-token
public identity. It matches id/x/y/plane and never trusts reusable player slot
or username alone. Packet 25 and opcode 236 both use this resolver.

`PickupItem` captures the exact token on packet receipt and schedules that
token, not just numeric values. At completion,
`ItemHandler.claimGroundItem(player, token, addToInventory)` revalidates the
same identity, owner visibility, plane, distance, and inventory capacity;
performs the complete transfer; then atomically marks claimed and removes that
identity. The pickup lifecycle callback fires only after successful claim. A
script-private token never falls into `GlobalDrops`; legacy public items keep
their existing fallback behavior.

A stackable reward is one identity carrying its amount. A non-stackable reward
is one amount-1 identity per item and `ScriptGroundItemHandle.identityCount()`
reports that count. One logical handle owns the complete identity set;
`isClaimed()` becomes true only after all identities were claimed and
`remove()`/`detach()` operate atomically on every still-unclaimed identity.
Attached identities are removed on close. `detach(1..1000)`
atomically transfers every logical-result identity from encounter cleanup to
the same player's private expiry index; it never becomes public. Claim or
expiry removes the exact identity. `rollDrops` detaches all staged identities
only as its final successful commit, allowing a boss to close immediately
without deleting rewards.

Packet 25's valid unmatched path and opcode 253's validated log path have the
same Firemaking identity guarantee. Both resolve once at packet receipt and
call `Firemaking.attemptFire` with the creation token (or an internal
`GroundItemRef` containing token plus copied id/x/y/plane), never only
`logId/x/y`. The `CycleEvent` captures that token. Immediately before success
sound, XP, fire-object creation, dialogue, or ash scheduling, it calls
`ItemHandler.consumeGroundItemExact(player, token)`, which revalidates backing
identity, item id, coordinates, plane, current visibility, unclaimed state,
and player distance and compare-removes only that identity. If the token was
claimed, expired, explicitly removed, replaced, made invisible, or otherwise
fails revalidation, Firemaking stops cleanly and creates no fire, XP, sound,
ash task, or removal of an equal private/public item. Inventory-origin
Firemaking uses the token returned by its own staged ground-item creation. No
scheduled Firemaking path may call tuple-based `removeGroundItem` or resolve a
replacement token after scheduling.

### Lock Enforcement Matrix

Lock checks occur before the listed packet's current duel, skilling,
teleporting, facing, event, or helper side effects.

| Entry point | Movement lock | Action lock |
|-------------|---------------|-------------|
| `Walking.processPacket` opcodes 98/164/248 | Drop at method entry | No additional effect. |
| `ScriptedMovement.walkTo` / `teleport` | Return `false` | Allowed unless destination/encounter rules fail. |
| Button 185 | No effect | Drop, except exact authenticated pending scripted-dialogue option. |
| NPC/object clicks, item clicks, item-on-item/object/NPC | No effect | Drop before route-specific side effects. |
| New packet routes 25/14/237/35 | No effect | Drop according to packet matrix. |
| Ground-item second click 253 | No effect | Drop before debug output, message, task cancellation, statue reset, or Firemaking token capture/scheduling. |
| Pickup 236 | No effect | Drop before pickup scheduling/animation. |
| Command/chat/logout/keepalive/admin transport | No effect | Never blocked, so a player cannot be stranded. |
| Death, cleanup, lock expiry, camera reset | Never blocked | Never blocked. |

Locks are not implemented through broad `PlayerAction` booleans. They use
token counts owned by `(generation, encounter, Player identity)`. Expiry runs
on the game cycle. The dialogue exception requires the same player identity,
active generation, pending callback token, and offered option; no arbitrary
button bypasses the lock.

Camera ownership is one composable `ScriptCameraSession`, not one token per
packet. `beginCamera(ticks)` reserves the player's sole session and schedules
expiry. `position`, `lookAt`, and `shake` on that handle verify the same active
token and queue the corresponding packet in call order without reset, matching
the existing `Telekinetic.observeStatue` position-then-look-at sequence. Each
operation may update its component repeatedly. The session tracks an atomic
`resetSent` flag; exact release, expiry, `resetCamera`, encounter close, death,
logout, and reload all converge on `releaseCamera(token)`, whose first caller
queues `sendCameraReset` and whose later callers return `false` without another
packet.

### Deterministic RNG and Drop Transaction

`ScriptEncounterRng` is game-cycle-owned SplitMix64. `nextLong` is exactly:

```java
state += 0x9E3779B97F4A7C15L;
long z = state;
z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
return z ^ (z >>> 31);
```

Every add/multiply uses Java `long` two's-complement overflow and every shift
is unsigned `>>>`.

At process startup the service obtains one `processSeed` from
`new SecureRandom().nextLong()` and assigns monotonic positive `ownerToken` and
`encounterOrdinal` values. Production encounter state is derived exactly:

```java
long material = processSeed;
material ^= generation * 0xD1342543DE82EF95L;
material ^= ownerToken * 0x9E3779B97F4A7C15L;
material ^= encounterOrdinal * 0x94D049BB133111EBL;
long initialState = mix64(material);
```

`mix64` is the three xor/multiply steps above without the initial gamma add.
The server logs generation, owner token, ordinal, encounter id, and initial
state for replay, never exposes them to guest code, and rejects ordinal/token
overflow rather than reuse. Tests inject only `processSeed` and counters through
a package-private constructor.

Independent literal vectors, not values generated by the class under test,
are normative:

| Input | Expected |
|-------|----------|
| `state = 0`, first five `nextLong()` calls | `0xe220a8397b1dcdaf`, `0x6e789e6aa1b965f4`, `0x06c45d188009454f`, `0xf88bb8a8724c81ec`, `0x1b39896a51a8749b` |
| `processSeed=0x0123456789abcdef`, `generation=7`, `ownerToken=11`, `encounterOrdinal=13` | material `0xfbbfc53b1d71fcf4`, initial state `0x84451a083e76124d`, first output `0xbcc864c2a4d2c848` |
| `state=0`, sequential `nextInt(10)`, `nextInt(1000000)`, `nextInt(3)` | `6`, `699317`, `2` |

`nextInt(bound)` accepts `1..1000000`. It uses the upper 31 output bits and
Java-style rejection:

```java
int bits = (int) (nextLong() >>> 33);
int value = bits % bound;
while (bits - value + (bound - 1) < 0) {
    bits = (int) (nextLong() >>> 33);
    value = bits % bound;
}
return value;
```

Invalid bounds return `-1` without state advance. `chance` accepts denominator
`1..1000000` and numerator `0..denominator`; invalid values return `false`
without state advance, zero returns `false` without advance, equality returns
`true` without advance, and other values call
`nextInt(denominator) < numerator`.

Drop tables contain `1..64` entries. Item ids must be definition-backed;
amounts are integral `1..1000000` with `minAmount <= maxAmount`; weight is an
integer `0..1000000`; `always: true` requires weight `0`; non-always entries
require positive weight. Sum weights uses `long`, must be `1..1000000` when
weighted entries exist, and input order defines cumulative selection. Every
always entry and exactly one weighted entry are selected; an all-always table
is valid. Total logical amount must fit `Integer.MAX_VALUE`; total physical
ground identities must fit the encounter's remaining 128-identity budget.

`rollDrops` copies the input, preflights definitions, ownership, location,
capacity, amounts, and weights, then clones RNG state into a local transaction.
Selection and amount rolls use only that local state. It stages all exact
ground identities, verifies each, and detaches them for `privateTicks`. Only
then does it commit RNG state and return immutable results. Any parse,
allocation, creation, verification, or detach failure removes every staged
identity and leaves encounter RNG unchanged.

## Affected Modules

| Module | Change Type | Description |
|--------|-------------|-------------|
| `com.rs2.script`, `com.rs2.script.context`, `com.rs2.script.snapshot` | modify/create | Complete contexts, immutable arrays/snapshots, registrations, and generation-leased dispatch. |
| `com.rs2.script.registries` | modify | Candidate-owned maps for five packet endpoints plus player-death singleton. |
| `com.rs2.script.world`, `com.rs2.script.capability` | create | Encounter ownership, exclusive area reservations, handles, locks/camera sessions, RNG, drops, facades, resource transactions, and lifecycle cleanup. |
| Packet/combat/visibility seams | modify | Universal validation for opcodes 185/25/14/237/35 and legacy ground route 253, migration of existing routes, exact pickup/Firemaking identity, early lock/area gates, and participant-only owned-NPC targeting/visibility. |
| Player/NPC death seams | modify | Pre/post player-death transition and guarded/deferred NPC death branch. |
| NPC/object/ground engine adapters | modify | Stable NPC allocation; authoritative layered object service spanning `ObjectManager`, `ObjectHandler`, and `Region`; collision transactions; exact ground resolver/claim/Firemaking/detach/expiry. |
| `ScriptedPlayer`, `ScriptedNpc`, `PathFinder`, shops/presentation | modify | Truthful bounded facades and static-shop provenance. |
| TypeScript runtime and boss content | modify/create | Exact declarations and one imperative production boss fixture. |
| Java tests and packet fixtures | modify/create | Focused gates, race tests, real decoders/death loops, cleanup, boss E2E, and contract/sandbox coverage. |

## Required Context

| File | Why |
|------|-----|
| `plans/typescript-content-platform/plan.md` | Phase invariants, exact authority, reload, sandbox, and offline gate. |
| `plans/typescript-content-platform/phases/phase-4.md` | Gated scope and acceptance criteria. |
| `plans/typescript-content-platform/implementation/phase-3-impl.md` | Existing state/quest/production-E2E conventions to preserve. |
| `plans/typescript-content-platform/implementation/phase-5-impl.md` | Declarative consumers/builders that remain out of scope. |
| `engine/server/src/main/java/com/rs2/script/ScriptHost.java` | Context/state/generation publication and lease APIs. |
| `engine/server/src/main/java/com/rs2/script/registries/RegistryStore.java` | Candidate and active registry snapshots. |
| `engine/server/src/main/java/com/rs2/script/ScriptFunctions.java` | Registration validation. |
| `engine/server/src/main/java/com/rs2/script/ScriptBindings.java` | Explicit global binding and sandbox boundary. |
| `engine/server/src/main/java/com/rs2/script/ScriptExecutor.java` | Callback failure containment. |
| `engine/server/src/main/java/com/rs2/script/ScriptLifecycleService.java` | Lifecycle, scheduler, and death dispatch. |
| `engine/server/src/main/java/com/rs2/script/scheduler/ScriptScheduler.java` | Generation/player task ownership to extend. |
| `engine/server/src/main/java/com/rs2/script/ScriptedPlayer.java` | Existing complete runtime player surface and compatibility delegates. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/ClickingButtons.java` | Opcode 185 decode and pre-event helpers. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/ItemOnGroundItem.java` | Opcode 25 decode and weak ground lookup. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/ItemClick2OnGroundItem.java` | Opcode 253's second-click ground route currently performs effects and enters delayed Firemaking without a stable identity. |
| `engine/server/src/main/java/com/rs2/net/packets/PacketHandler.java` | Production registration maps opcode 253 to `ItemClick2OnGroundItem`. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/ItemOnPlayer.java` | Opcode 14 unsafe array indexing. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/MagicOnItems.java` | Opcode 237 mutation/event order. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/MagicOnObject.java` | Opcode 35 decode and unresolved object switch. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/PickupItem.java` | Production opcode 236 exact claim and delayed pickup. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/ClickNPC.java` | All NPC click/attack opcodes currently target the global NPC array before encounter authorization. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/Walking.java` | Early movement lock enforcement. |
| `engine/server/src/main/java/com/rs2/game/content/skills/firemaking/Firemaking.java` | Packet 25 continuation schedules tuple-based ground removal that must carry the exact token. |
| `engine/server/src/main/java/com/rs2/game/players/Player.java` | Authoritative death tick, live/output state, equipment arrays, and HP. |
| `engine/server/src/main/java/com/rs2/game/players/PlayerHandler.java` | NPC update list must hide encounter-owned NPCs from nonparticipants. |
| `engine/server/src/main/java/com/rs2/game/players/PlayerAction.java` | Existing action flags that token locks must not conflate. |
| `engine/server/src/main/java/com/rs2/game/players/PlayerAssistant.java` | `applyDead`, `giveLife`, movement, combat, interface, projectile, and camera helpers. |
| `engine/server/src/main/java/com/rs2/GameEngine.java` | Item → player → NPC → script tick order controlling re-entrancy. |
| `engine/server/src/main/java/com/rs2/game/npcs/NpcHandler.java` | Spawn/death/drop loop and post-callback slot dereferences. |
| `engine/server/src/main/java/com/rs2/game/npcs/Npc.java` | Backing combat/visual identity. |
| `engine/server/src/main/java/com/rs2/game/npcs/NpcData.java` | NPC killer attribution and damage reset. |
| `engine/server/src/main/java/com/rs2/game/content/combat/CombatAssistant.java` | Repeating attacks and delayed hits can bypass the initial click and must recheck owned-NPC identity. |
| `engine/server/src/main/java/com/rs2/game/content/combat/npcs/NpcCombat.java` | Owned NPC continuation must target participants only. |
| `engine/server/src/main/java/com/rs2/game/content/combat/range/DwarfCannon.java` | Independent NPC target/damage path needs the same ownership guard. |
| `engine/server/src/main/java/com/rs2/world/ObjectHandler.java` | `globalObjects` dynamic store and broad tile mutation. |
| `engine/server/src/main/java/com/rs2/world/ObjectManager.java` | Separate timed `objects` store, broadcasts, expiry, and replacements. |
| `engine/server/src/main/java/com/rs2/game/objects/Object.java` | Constructor currently self-registers timed objects in `ObjectManager`. |
| `engine/server/src/main/java/com/rs2/game/objects/Objects.java` | Dynamic/cache object shape representation. |
| `engine/server/src/main/java/com/rs2/world/clip/Region.java` | `realObjects` cache store plus movement/projectile masks and footprints. |
| `engine/server/src/main/java/com/rs2/world/clip/RegionFactory.java` | Startup cache-object writer and base-layer provenance. |
| `engine/server/src/main/java/com/rs2/world/clip/PathFinder.java` | Void route API requiring truthful adapter. |
| `engine/server/src/main/java/com/rs2/world/ItemHandler.java` | Ground creation/visibility/removal and global fallback. |
| `engine/server/src/main/java/com/rs2/game/items/GroundItem.java` | Mutable ground item lacking stable identity/private lifetime. |
| `engine/server/src/main/java/com/rs2/game/shops/ShopHandler.java` | Static versus player-owned shop slots and configuration arrays. |
| `engine/server/src/main/java/com/rs2/game/shops/ShopAssistant.java` | Shop UI postconditions. |
| `engine/server/src/main/java/com/rs2/net/PacketSender.java` | Interface, visuals, projectile, sound, and camera output primitives. |
| `engine/server/src/main/java/com/rs2/game/content/minigames/magetrainingarena/Telekinetic.java` | Existing working position-plus-look-at camera packet sequence. |
| Central object writer call sites listed under Object Protocol | Audit set | Gates, doors, skills, minigames, cannon, object actions, commands, packets, NPC cleanup, and timed objects must route through the authoritative writer. |
| `engine/server/data/cfg/npc.json` | Definition-backed boss/add ids and stats. |
| `engine/server/data/cfg/spawns.json` | Real KBD-lair coordinates. |
| `engine/server/data/cfg/shops.json` | Static shop provenance fixture. |
| `content/src/core/runtime.ts` | Authoritative executable bridge declarations. |
| `content/src/core/drop-tables.ts`, `content/src/core/boss.ts` | Phase 5 abstractions explicitly not consumed now. |
| `content/src/bosses/dragon-king.ts` | Existing data-only, non-production proof to leave intact. |
| `engine/server/src/test/java/com/rs2/net/packets/impl/PacketFixtures.java` | Real packet encoders. |
| `engine/server/src/test/java/com/rs2/game/npcs/NpcDeathLifecycleIntegrationTest.java` | Real NPC death-loop fixture. |
| `engine/server/src/test/java/com/rs2/script/SchedulingLifecycleTest.java` | Generation lease/task lifecycle test patterns. |

## Work Package Status

| Work package | Status | Verification |
|--------------|--------|--------------|
| WP1: Freeze bridge types and atomic dispatch | completed | Exact TypeScript + full JDK 17 Maven gate passed; 103 tests; independent review accepted with one non-blocking exhaustive-negative-matrix follow-up. |
| WP2: Apply universal packet validation and authority | completed | Exact TypeScript + full JDK 17 Maven gate passed; 129 tests; independent review accepted with no actionable findings. |
| WP3: Establish ownership, player death, and lifecycle cleanup | completed | Exact TypeScript + focused/full JDK 17 gates passed; 29 focused and 158 full tests; independent review accepted with no actionable findings. |
| WP4: Make NPC death and owned NPCs re-entrancy-safe | completed | Independently accepted after rework; focused 12-test gate, TypeScript compile, and full Maven reactor (169 tests) passed. |
| WP5: Implement collision transactions, exact rewards, and facades | completed | Independently accepted after layered object/collision authority, exact reward projection, facade, writer, packet, and deferred-chain rework; official build passed with 204 tests and no failures/errors/skips. |
| WP6: Commit RNG and drops as one transaction | completed | Exact TypeScript + focused JDK 17 gate passed (19 tests); full reactor passed with 223 tests; official build passed; delegate subagent review found no implementation defect and its parser negative-coverage finding was closed with the exhaustive real-path matrix. |
| WP7: Prove the concrete production boss and live flow | completed | Compiled boss content, real command/pickup/walking/click packets, script ticks, production NPC death, exact private rewards and pickup, and every close path covered by 6 E2E tests; delegate review accepted after closing the fixture-route gaps with production-predicate assertions; TypeScript + focused gate (18 tests) + full reactor (229 tests) + official build all passed. |

## Implementation Steps

### Step 1 (WP1): Freeze bridge types and atomic dispatch

- **What**: Implement the normative contexts, `ScriptArray`, registrations,
  `ScriptHost.dispatchActive`, atomic state/context/generation publication, and
  migrate every existing executable packet route to the leased API.
- **Where**: `ScriptHost`, `RegistryStore`, `ScriptFunctions`,
  `ScriptBindings`, `ScriptExecutor`, registries/contexts, existing packet
  handlers, and `content/src/core/runtime.ts`.
- **Why**: No world feature may build on a handler value that can race into a
  different Graal context.
- **Considerations**: Ordered pair keys are directional; candidate duplicate or
  type failure rolls back every category; enforce sparse decodable button keys
  and immutable non-null handle snapshot accessors. Keep sandbox construction
  unchanged. Add the lookup-versus-reload latch test and no-array/raw-host
  export tests.
- **Focused gate**:

  ```bash
  mvn -B -f engine/pom.xml -pl server \
    -Dtest=ScriptHostDispatchLeaseTest,ScriptHostTest,RegistryStoreTest test
  ```

### Step 2 (WP2): Apply universal packet validation and authority

- **What**: Implement the exact five-opcode matrices, early action-lock seam,
  three-layer read-only object resolution, stable ground creation tokens,
  exact-token Firemaking continuation for opcodes 25 and 253, and production
  context construction.
- **Where**: `ClickingButtons`, `ItemOnGroundItem`, `ItemOnPlayer`,
  `MagicOnItems`, `MagicOnObject`, `ItemClick2OnGroundItem`, `PacketHandler`,
  `Firemaking`, `GroundItem`, `ItemHandler`, `PacketFixtures`, and narrow
  resolver classes.
- **Why**: Invalid unmatched packets must not reach legacy array indexing, and
  exact routes must preserve Phase 1 authority semantics.
- **Considerations**: Decode before mutation, validate registered and
  unregistered identically, preserve only valid unmatched legacy behavior,
  carry opcodes 25/253's resolved token through the delayed callback, and test
  all exact-route outcomes plus opcode-253 bounds/live/action-lock/success and
  equal private/public identity replacement/claim/expiry races.
- **Focused gate**:

  ```bash
  mvn -B -f engine/pom.xml -pl server \
    -Dtest=RemainingInteractionDispatchTest,PacketValidationMatrixTest,ItemClick2OnGroundItemTest,FiremakingGroundIdentityTest test
  ```

### Step 3 (WP3): Establish ownership, player death, and lifecycle cleanup

- **What**: Implement the service indexes/limits/exclusivity, encounter and
  lock handles, exclusive plane/rectangle reservation, participant-aware
  facade/movement/visibility guards, scheduler ownership, cleanup order, and
  pre/post `applyDead` snapshot protocol.
- **Where**: New `com.rs2.script.world`, `ScriptedPlayer`,
  `ScriptLifecycleService`, `ScriptScheduler`, `Player`, `PlayerHandler`,
  `PlayerAssistant`, `Walking`, and logout/removal/reload paths.
- **Why**: Ownership and transition-safe cleanup must exist before any resource
  can be created.
- **Considerations**: Owner is participant zero; retained player wrappers
  become constrained via service lookup; overlapping same-plane begin fails
  before player/world mutation; nonparticipants cannot enter or see owned NPCs;
  the reservation compare-removes only after cleanup; death callback receives
  snapshots after `applyDead`.
- **Focused gate**:

  ```bash
  mvn -B -f engine/pom.xml -pl server \
    -Dtest=ScriptEncounterOwnershipTest,ScriptEncounterIsolationTest,PlayerDeathLifecycleIntegrationTest,ScriptLockLifecycleTest test
  ```

### Step 4 (WP4): Make NPC death and owned NPCs re-entrancy-safe

- **What**: Add stable NPC allocation/handles, exact owned-drop suppression,
  participant-only click/attack/continuation, immutable death contexts, guarded
  death critical section, and deferred destructive FIFO drain.
- **Where**: `ClickNPC`, `CombatAssistant`, `DwarfCannon`, `NpcCombat`,
  `NpcHandler`, `Npc`, `NpcData`, `ScriptLifecycleService`, and
  `com.rs2.script.world` NPC classes.
- **Why**: Close/despawn from a callback must neither crash the NPC loop nor
  skip mandatory legacy bookkeeping.
- **Considerations**: Use captured identities for all remaining bookkeeping;
  suppress legacy drops only for the exact owned token; keep the existing
  unowned observer order intentional; fix the current NPC animation adapter so
  it sets the requested animation/update flags before returning success.
  Recheck authorization at initial click, attack tick, projectile, delayed hit,
  cannon hit, and NPC retaliation so a forged/stale continuation cannot deal
  damage.
- **Focused gate**:

  ```bash
  mvn -B -f engine/pom.xml -pl server \
    -Dtest=ScriptNpcEncounterTest,ScriptOwnedNpcCombatIsolationTest,NpcDeathLifecycleIntegrationTest,ScriptNpcDeathReentrancyTest test
  ```

### Step 5 (WP5): Implement collision transactions, exact rewards, and facades

- **What**: Implement the object contributor/reservation transaction, exact
  layered world-object source of truth and writer migration, exact ground
  identity claim/detach/expiry, truthful movement/combat/presentation/shop
  facades, composable camera sessions, and full lock matrix.
- **Where**: `WorldObjectService`, `ObjectManager`, `ObjectHandler`,
  `RegionFactory`, `Region`, `Object`, `Objects`, every inventoried central
  writer, `PathFinder`, `ItemHandler`, `GroundItem`, `PickupItem`, `Walking`,
  capability classes, `PacketSender`, `ShopHandler`, and `ShopAssistant`.
- **Why**: These engine seams currently return void, select by numeric values,
  or mutate masks irreversibly.
- **Considerations**: Enforce encounter > timed > global dynamic > cache
  precedence; compare every touched movement/projectile cell; defer reserved
  writer mutations; packet 25 and 236 use one resolver; pickup transfers exact
  identity; static shop provenance is loader-owned; one camera session composes
  position/look-at/shake and emits one final reset.
- **Focused gate**:

  ```bash
  mvn -B -f engine/pom.xml -pl server \
    -Dtest=ScriptObjectTransactionTest,WorldObjectPrecedenceTest,ScriptGroundRewardIntegrationTest,ScriptedPlayerCapabilityTest,ScriptLockIntegrationTest,ScriptCameraSessionTest test
  ```

### Step 6 (WP6): Commit RNG and drops as one transaction

- **What**: Implement exact SplitMix64, bounded rejection, rational chance,
  entry parser, identity preflight/staging, private detach, and joint RNG/item
  commit.
- **Where**: `ScriptEncounterRng`, `ScriptDropEntryParser`,
  `ScriptDropResult`, encounter/ground services, and runtime declarations.
- **Why**: A failed reward attempt must not consume randomness or leave a
  partial logical reward.
- **Considerations**: Use integer weights and `long` overflow checks; preserve
  entry order; implement the exact production seed derivation; assert the
  literal independent vectors; injected creation/detach failures restore RNG
  and every staged identity.
- **Focused gate**:

  ```bash
  mvn -B -f engine/pom.xml -pl server \
    -Dtest=ScriptEncounterRngTest,ScriptDropTransactionTest test
  ```

### Step 7 (WP7): Prove the concrete production boss and live flow

- **What**: Add `content/src/bosses/encounter-warden.ts`, import it from
  `loader.ts`, and exercise it only through public Phase 4 APIs.
- **Where**: TypeScript content plus
  `ScriptBossProductionE2ETest` and the live-client runbook below.
- **Why**: The final proof must cross compiled TypeScript, a production packet,
  scheduler cycles, combat/NPC death, exact rewards, pickup, and cleanup.
- **Concrete fixture**:
  - entry is ordinary `onCommand("encounter-warden", handler)` through the real
    command packet; a second `onCommand("encounter-warden-close", handler)`
    requests explicit cleanup;
  - encounter id `encounter-warden`, arena
    `(2264,4688)..(2287,4711)`, plane `1`; the command first reserves that
    rectangle and only then teleports the owner to `(2271,4696,1)`;
  - on entry tick 0, acquire movement and action locks for 4 ticks, call
    `beginCamera(6)`, then on that same session call
    `position(55,46,800,5,0)`, `lookAt(55,50,2400,5,0)`, and
    `shake(2,2,2,2)` in that order. Locks expire on tick 4 and the session sends
    its sole reset on tick 6;
  - King Black Dragon NPC `50` at `(2271,4698,1)`, HP `240`; skeleton NPC `90`
    adds at `(2269,4698,1)` and `(2273,4698,1)`;
  - an empty tile at `(2275,4698,1)` is transactionally replaced with object
    `2213` by
    `replaceObject(2275,4698,1,-1,-1,-1,2213,10,0)`, then restored on close;
  - at HP `<=120`, once only: animation `1590`, graphic `246`, both skeletons,
    and an owner message. The threshold check first releases a still-active
    entry camera session, acquires movement/action locks for 2 ticks, starts a
    4-tick camera session, and calls
    `position(55,48,900,8,0)`, `lookAt(55,50,1800,8,0)`, and
    `shake(2,3,2,2)` in order. The locks expire after 2 ticks and that session
    resets once after 4. Every 4 ticks thereafter, projectile `393` travels
    from boss to owner followed by bounded damage `5`;
  - owned death callback rolls the exact table
    `{ itemId: 536, minAmount: 1, maxAmount: 1, weight: 0, always: true }` and
    `{ itemId: 995, minAmount: 500, maxAmount: 500, weight: 100, always: false }`
    with private TTL `200`, verifies both results, then closes;
  - detached dragon bones and coins remain private after close. The E2E picks
    both up through real opcode 236 and asserts exact inventory deltas and zero
    remaining ground identities.
- **Considerations**: Test-only Java seed injection is package-private. Drive
  entry through command packet decoding, ticks through
  `ScriptLifecycleService.processGameTick`, boss death through
  `NpcHandler.process`, and rewards through `PickupItem.processPacket`. Cover
  normal completion, explicit close, callback throw, owner death, logout, and
  successful reload; rejected reload leaves the running encounter live. The
  automated fixture creates owner and observer as distinct live
  `PlayerHandler.players` identities. The observer starts at
  `(2263,4696,1)`, outside the reserved rectangle: its competing entry command
  returns arena-busy with no teleport/resource mutation, its walking packet
  cannot enter `(2264,4696,1)`, its NPC update contains neither boss nor adds,
  forged attack/magic/click packets and direct combat continuation cause no
  owner-NPC HP change, and it cannot resolve/pick up the private rewards. After
  owner cleanup, the same observer command succeeds, proving exact reservation
  release rather than a permanent coordinate ban.
- **Focused gate**:

  ```bash
  content/node_modules/.bin/tsc -p content/tsconfig.json
  mvn -B -f engine/pom.xml -pl server \
    -Dtest=ScriptBossProductionE2ETest,ScriptHostTest test
  ```

## Testing Plan

| Test Type | Required evidence |
|-----------|-------------------|
| Contract/sandbox | TypeScript compile; reflection/export inventory matches declarations; sparse button-key acceptance/rejection; immutable `ScriptArray`; immutable non-null handle snapshots after close; unsupported equipment indexes and raw host access unavailable. |
| Dispatch lease | State lookup paused across successful reload cannot mix state/context/generation; failed candidate preserves all old handlers and encounter execution. |
| Packet matrices | For each of 185/25/14/237/35: invalid registered, invalid unregistered, valid registered, valid registered throw, valid unregistered, and action locked through real packet bytes. Real opcode-253 bytes prove malformed/bounds/live/action-lock rejection occurs before effects and valid legacy success remains enabled. |
| Player death | Real death tick, `applyDead` mandatory effects, read-only context, killer bounds/identity/tie/self/no-damage rules, callback exception, and duplicate transition. |
| NPC death | Exact owned drop suppression; unowned drop/Slayer/minigame/quest/kill/reset behavior; close and despawn inside callback; no later null slot dereference; FIFO drain. |
| Object transaction | Encounter > timed `ObjectManager` > global `ObjectHandler` > cache `Region.realObjects` precedence; separate expected/replacement shapes; every central writer; deferred expiry; rotated/wall footprints; every movement/projectile mask before/apply/restore; conflict does not clobber. |
| Ground rewards/Firemaking | Equal private/public id/x/y identities and deterministic token selection through opcodes 25 and 253; exact claim/detach/expiry; token claim, expiry, removal, or replacement before the delayed callback causes no equal-item removal, XP, success sound, fire object, or ash task; both packet routes retain successful legacy Firemaking. |
| Facades/locks/camera | Truthful route/HP/interface/shop results, early stacked locks, dialogue escape, one camera session queues position then look-at then shake without reset and queues exactly one reset across release/expiry/cleanup races. |
| Ownership/isolation | Automatic owner, membership/id limits, overlapping same-plane rectangle rejection before mutation, non-overlap/cross-plane acceptance, participant boundary movement, nonparticipant NPC visibility/click/attack/delayed-hit/cannon denial, and exact final compare-remove. |
| RNG/drop | Literal `nextLong`, derived-seed, and mixed-bound `nextInt` vectors; invalid/no-advance chance cases; integer weights/overflow; all-always table; injected create/detach failure restoring RNG and every identity. |
| Boss E2E | Compiled loader, real command, exact lock/camera tick order, competing observer denial, phase/add/special order, layered collision object, exact death/drop/private pickup, and zero attached resources/tasks/locks/session/reservation after every close path. |
| Compatibility | Existing unowned packets/events, NPC drops, pickup/global drops, objects, shops, quests, persistence, no-script boot, candidate reload, and Phase 1-3 content remain unchanged. |

The one final offline acceptance gate remains exact:

```bash
content/node_modules/.bin/tsc -p content/tsconfig.json &&
export JAVA_HOME="$(/usr/libexec/java_home -v 17)" &&
export PATH="$JAVA_HOME/bin:$PATH" &&
mvn -B -f engine/pom.xml test
```

### Live Client Smoke Runbook

The automated offline gate is always the acceptance gate. Before marking Phase
4 complete, also run the following with the Java 17 server and real client:

1. Run `scripts/build.sh`, `scripts/run-server.sh`, and
   `scripts/run-client.sh`; log in as the owner and a second player. Using the
   existing administrator/development coordinate move, place only the second
   player at `(2263,4696,1)`, immediately west of but outside the arena; do not
   create or join an encounter for that player.
2. As owner, enter `::encounter-warden`. During ticks 0-4 verify both walking
   and action attempts are blocked; observe camera position then look-at plus
   shake without an intervening reset, and one reset on tick 6. From the second
   client enter the same command and verify arena-busy/no teleport, attempt to
   walk east into `(2264,4696,1)`, and verify the step is refused and NPC 50 is
   neither visible nor attackable/clickable.
3. Reduce NPC 50 below 120 HP after tick 6. Verify the 2-tick locks, the exact
   second camera sequence and one reset, graphic/projectile, skeleton phase,
   and object `2213` blocking movement/projectiles as its masks specify.
4. Kill NPC 50. Verify no legacy KBD drop, both exact rewards appear to the
   owner, the second player cannot see or pick them up, and the barrier is
   visibly restored.
5. Pick up bones and coins using normal client packets; verify inventory and
   ground disappearance.
6. Repeat entry and `::encounter-warden-close`; after cleanup have the second
   player enter the same command successfully, proving area release. Repeat
   with owner death,
   logout, and successful script reload. Verify camera reset, movement/action
   restoration, no adds/barrier/rewards/tasks/area reservation, and successful
   re-entry.

Record date, operator, Java version, server/client build identifiers, both
player names, each step's result, screenshots/log references, and cleanup
counts in the Phase 4 completion note. If a server/client launcher is absent
or cannot run in the environment, record the missing path or exact failure,
command output, environment, and maintainer acknowledgement; do not silently
claim the live smoke passed. The full automated gate still must pass.

### Test Integrity Constraints

- Do not delete, disable, ignore, weaken, or convert an existing production
  integration assertion into a helper-only assertion.
- Every packet route must cross its real `processPacket` decoder. Direct
  registry/helper calls are supplementary only.
- Boss completion must cross `content/dist/loader.js`, production command
  decode, script tick, `NpcHandler.process`, and `PickupItem.processPacket`.
- Cleanup assertions inspect authoritative scheduler/lock indexes, NPC slot
  identity, area reservation, every object layer/movement/projectile mask,
  camera packet/reset count, and exact ground-item identities.
- Keep `ScriptHostTest.currentCompiledLoaderRegistersEverySupportedCategory`
  complete and keep all sandbox negative tests.
- Extend existing `RegistryStoreTest`, `SchedulingLifecycleTest`,
  `NpcDeathLifecycleIntegrationTest`, `PlayerLifecycleIntegrationTest`,
  item/click dispatch tests, Firemaking tests, object manager/handler/region
  tests, combat tests, and `PacketFixtures`; do not replace them.
- Existing unowned `ClickNPC` and combat assertions remain unchanged; add
  participant/nonparticipant cases through production click packets and the
  real repeating/delayed/cannon paths rather than mocking
  `canTargetOwnedNpc`.
- Firemaking race tests retain two equal id/x/y items, invalidate the captured
  token before the cycle event, and assert the other exact identity, XP, fire
  object, sound, and ash schedule are unchanged. Run the matrix through both
  opcode 25 and real opcode-253 `processPacket` bytes; opcode 253 additionally
  covers absent visible identity, private-over-public selection, action lock,
  token claim/expiry/removal, and a successful completion.
- No engine singleton/global array may be replaced by a test-only
  implementation. Deterministic seed and injected allocation failure seams are
  package-private and unavailable to guest code.
- Unowned NPCs/items/objects, plugin events, persistence, quests, no-script
  boot, and Phase 1-3 content retain their existing tests and behavior.

## Rollback Strategy

Before reverting, invoke `ScriptEncounterService.closeAll()` on the game cycle
and verify zero encounter/area indexes, participants, tasks, locks, camera
sessions, NPC tokens, object reservations/deferred writers, attached rewards,
and detached test rewards.
Resolve any quarantined object transaction from its retained snapshot before
removing the adapter.

Revert the registrations/contexts, leased packet dispatch, universal packet
validation, death seams, encounter/capability packages, NPC/object/ground
adapters, runtime declarations, boss import/content, and tests as one unit.
Restore legacy packet continuation but retain any independent bounds checks
that prevent unsafe array access. Existing direct Phase 1-3 presentation
methods remain compatibility delegates throughout implementation and rollback.

There is no save-format migration. Candidate evaluation creates registrations
only, not live encounters, so a rejected candidate requires no cleanup.
Successful reload closes only the old generation before its context closes;
rollback must preserve that ordering.

## Open Decisions

All implementation-shaping choices are resolved for this phase.

| Decision | Chosen |
|----------|--------|
| Button key | Decoded `actionButtonId`; packet 185 exposes no parent widget id. |
| Button domain | Sparse `readHex` quotient/remainder domain; unreachable continuous-range keys reject candidate load. |
| Packet authority | Universal validation first; exact valid registration consumes; valid absence alone falls back. |
| Registry lease | Lookup and callback under one synchronized `ScriptHost` snapshot lease. |
| Player death | Immutable post-`applyDead` observation; encounter cleanup at transition start. |
| NPC death | Captured identity plus guarded critical section and FIFO destructive drain. |
| Equipment | Only 11 named `ItemConstants` slots; unnamed array indexes are unavailable. |
| Arena isolation | Exclusive same-plane rectangle reservation; participant-only entry, visibility, clicks, and combat for owned NPCs. |
| Object visibility | Encounter > timed ObjectManager > global ObjectHandler > Region cache, through one authoritative service. |
| Object mutation | Separate expected/replacement shapes plus reserved full-footprint movement/projectile transaction. |
| Reward lifetime | Exact player-identity private items; explicit detach TTL survives encounter close. |
| Delayed ground use | Opcodes 25 and 253 capture one exact visible creation token at receipt and carry it through Firemaking; no tuple fallback or delayed re-resolution. |
| Locking | Separate tokenized action/movement locks with the published packet matrix. |
| Camera | One composable session owns position/look-at/shake updates and exactly one final reset. |
| Handle identity reads | Immutable creation/last-success snapshots remain non-null after close; live operations use declared failure values. |
| Shop eligibility | Loader-authored immutable static provenance; dynamic player shops rejected. |
| RNG | Fully pinned SplitMix64, SecureRandom process seed derivation, logged replay state, literal vectors, local snapshot/commit, and integer weights. |
| Encounter identity | Id scoped by active generation and owner identity; one encounter membership per player. |
| Boss proof | Imperative KBD-based production fixture; declarative `defineBoss` remains Phase 5. |

## Reality Check

### Code Anchors

| File | Symbol/Area | Plan consequence |
|------|-------------|------------------|
| `ScriptHost.java` | `replaceContext`, generation execute methods | Registry state is currently published separately; add one state/context/generation lease. |
| `RegistryStore.java` | `State`, `commit` | Every handler map must remain one candidate snapshot. |
| Existing item/NPC/object packet handlers | direct `RegistryStore.active()` lookup | Migrate all executable packet routes, not only the five new ones. |
| `Packet.java`, `ClickingButtons.java` | two-byte `readHex` and early helpers | Keys are sparse `u8*1000+u8`, not every integer through 255255; action lock must precede helpers. |
| `ItemOnPlayer.java` | inline array indexes | Universal validation must protect unmatched traffic. |
| `ItemOnGroundItem.java`, `ItemClick2OnGroundItem.java`, `PacketHandler.java`, `Firemaking.java` | opcodes 25/253 feed tuple-only `attemptFire`; opcode 253 is registered in production; callback later calls tuple `removeGroundItem` | Both ground packet routes must capture the exact token before effects and preserve it through the cycle-event delay. |
| `Player.java`, `PlayerAssistant.java` | death tick, `applyDead`, `giveLife` | Cleanup begins at transition; immutable observation occurs only after `applyDead`. |
| `PlayerHandler.java`, `ClickNPC.java`, `CombatAssistant.java` | global NPC visibility, initial targeting, repeating attack/delayed hit | Spatial reservation alone does not isolate owned NPCs; every path needs participant authorization. |
| `NpcHandler.java` | `dropItems`, callback, later `npcs[i]` reads | Exact drop suppression and deferred death critical section are mandatory. |
| `ObjectManager.java`, `ObjectHandler.java`, `Region.java` | timed `objects`, dynamic `globalObjects`, cache/dynamic `realObjects`, and OR-ed masks | One layered source of truth and full footprint contributor transaction are required. |
| `Telekinetic.java` | consecutive `sendCameraCutscene` and `sendCameraCutscene2` | Position and look-at must compose under one lease without reset. |
| `PathFinder.java` | void `findRoute` | Add a truthful route-result adapter for `walkTo`. |
| `GroundItem.java`, `ItemHandler.java`, `PickupItem.java` | slot/name ownership and first equal numeric removal | Add stable token/player identity and exact claim path shared with packet 25. |
| `ShopHandler.java` | player shops also have names | Non-empty name is insufficient; preserve static configuration provenance. |
| `ItemConstants.java`, `Player.java` | 11 constants in 14-length equipment array | Expose only supported named slots. |
| `npc.json`, `spawns.json` | NPC 50/90 and KBD lair | Pin the production fixture to real ids and coordinates. |

### Mismatches / Notes

- `allowArrayAccess(true)` does not make Java collections immutable TypeScript
  arrays. Phase 4 therefore uses exported copied `ScriptArray` values with no
  iteration surface.
- `PlayerAssistant.applyDead()` is not a safe point for a mutable guest
  callback; mandatory resets continue after its beginning and item loss occurs
  later in `giveLife()`.
- Existing NPC death callbacks can invalidate later slot dereferences. Guest
  execution must move after captured mandatory bookkeeping, with destruction
  deferred until the explicit drain.
- Owner/player identity indexes do not isolate a fixed shared-world rectangle.
  Phase 4 therefore reserves it and gates movement, visibility, click, attack,
  delayed hit, cannon, and NPC retaliation.
- Visible objects currently span timed `ObjectManager.objects`, dynamic
  `ObjectHandler.globalObjects`, and `Region.realObjects`. Saving only object id
  or scanning one list cannot resolve or restore the world; layer identity,
  expected/replacement shape, and every movement/projectile contributor cell
  are part of the transaction.
- Existing ground pickup cannot distinguish equal items or survive reusable
  player slots. Exact token claim and controller object identity are required.
- Firemaking repeats the same tuple-removal problem after a randomized cycle
  delay. Both packet 25 and production opcode 253 enter it from an existing
  ground log, so each must capture the visible token before side effects and
  consume only that token before any success effect.
- The existing Telekinetic cutscene proves camera position and look-at are a
  pair of packets, so per-call replacement tokens would reset valid state.
- `defineBoss`, `LootTable`, and string item builders remain unconsumed in
  Phase 4. The imperative fixture proves the low-level API without pulling
  Phase 5 forward.
