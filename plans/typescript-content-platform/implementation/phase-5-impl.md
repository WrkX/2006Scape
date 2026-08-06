---
type: planning
entity: implementation-plan
plan: "typescript-content-platform"
phase: 5
status: completed
created: "2026-07-29"
updated: "2026-08-06"
---

# Implementation Plan: Phase 5 - Declarative Runtimes and OSRS Content Kit

> Implements [Phase 5](../phases/phase-5.md) of [TypeScript Content Platform](../plan.md)

## Approach

Phase 5 is complete. It was gated on Phase 4 completion (WP6 deterministic
RNG/drop transaction and WP7 production boss accepted) and executed one
bounded work package at a time, each independently reviewed and accepted.
Phase 4 WP6 supplies the deterministic RNG/drop transaction used by loot,
bosses, raids, and gathering; Phase 4 WP7 supplies the production boss
fixture that the declarative boss runtime generalized without regressing.

Implementation proceeded one bounded work package at a time. WP1 establishes
only the common manifest/source envelope, unified executable-route authority,
and two-phase activation machinery. It deliberately does **not** freeze the
boss, area, or raid schema early: current direct-import definitions are carried
as explicit `legacy-unscoped` compatibility records so the compiled loader
continues to work. Each consumer WP then owns its final strict schema/parser,
callback contract, validation, and atomic migration of its shipped fixtures.
After drops/rewards, bosses, areas, raids, and the already-functional quest
runtime are consumed, the public TypeScript builders are stabilized, the
gathering/resource-loop runtime is added, operator diagnostics are exposed,
and the representative content pack becomes the final vertical proof.

The Java engine continues to own world state, validation, deterministic random
state, transactions, persistence, packet routing, and cleanup. TypeScript owns
descriptors and content composition. Existing Phase 1-4 globals and facades
remain source compatible, the Graal sandbox is not widened, and unregistered
packets/world content continue through the current legacy fallback.

## Affected Modules

| Module | Change Type | Description |
|--------|-------------|-------------|
| `com.rs2.script.definition`, `com.rs2.script.route`, registries, and `ScriptHost` | create/modify | Common descriptor envelope, source/module metadata, unified guest/host routes, activation transaction, and atomic publication. |
| `com.rs2.script.drop`, `com.rs2.script.reward` | create | Java-owned definitions, an owner-neutral RNG-plus-ground drop transaction, player-local rewards, and the roster-wide atomic reward coordinator built on Phase 4 WP6. |
| `com.rs2.script.boss`, `com.rs2.script.area`, `com.rs2.script.raid` | create | Generation-owned declarative runtimes built on Phase 4 encounter/world services. |
| Quest runtime, persistence, and legacy quest UI | modify/create | Current-objective access, generic journal projection, exhaustive parser/codec boundaries, and one real historical migration through `PlayerSave`. |
| `content/src/core`, `content/src/*-builder.ts`, and new `content/src/sdk` | modify/create | Versioned public declarations and reusable requirement, reward, shop, equipment, dialogue/cutscene, drop, encounter, area, raid, quest, and gathering builders. |
| World/NPC/shop adapters | modify | Reversible generation-owned area spawns, objects, drop bindings, and scripted-shop projections while retaining legacy sources. |
| Admin command and diagnostics surfaces | modify/create | Permission-gated status/list/reload with bounded, source-aware output. |
| Representative content and docs | modify/create | Quest, resource, area, boss, and raid-facing vertical proofs plus migration/authoring documentation. |
| Java integration tests and compiled-content fixtures | modify/create | Candidate rollback, every definition consumer, production packet/tick/death paths, cleanup, journal, migration, diagnostics, and content-pack E2E coverage. |

## Required Context

The implementing agent must read the following before starting any Phase 5
work package. For a later package, re-read the accepted implementation/review
artifacts of every dependency rather than relying on this draft.

| File | Why |
|------|-----|
| `plans/typescript-content-platform/plan.md` | Global objective, compatibility, sandbox, reload, and final DoD. |
| `plans/typescript-content-platform/phases/phase-5.md` | Gated Phase 5 scope and acceptance criteria; this plan must not expand it. |
| `plans/typescript-content-platform/implementation/phase-4-impl.md` | Normative low-level APIs, WP6 RNG/drop contract, WP7 boss proof, and explicit Phase 5 deferrals. |
| `plans/typescript-content-platform/todo.md` | Authoritative execution boundary: Phase 4 WP6/WP7 are still pending. |
| `docs/SCRIPT_BRIDGE.md` | Current bridge, sandbox, reload, persistence, quest, registration, and legacy-fallback contract. |
| `docs/TYPESCRIPT.md` | Java/TypeScript ownership boundary and current author-facing examples. |
| `docs/ENGINE_BOUNDARY.md` | Rules for keeping engine mechanics in Java and content in TypeScript. |
| `engine/server/src/main/java/com/rs2/script/ScriptHost.java` | Candidate evaluation, active state/generation publication, and current absence of source/result diagnostics. |
| `engine/server/src/main/java/com/rs2/script/ScriptBindings.java` | Explicit global installation and sandbox-visible surface. |
| `engine/server/src/main/java/com/rs2/script/ScriptFunctions.java` | Existing definition/handler registrations and shallow boss/raid/area validation. |
| `engine/server/src/main/java/com/rs2/script/registries/RegistryStore.java` | Single staging snapshot that every definition and callback must continue to publish atomically. |
| `engine/server/src/main/java/com/rs2/script/registries/{Boss,Raid,Area,Quest}Registry.java` | Three data-only guest-value stores and the already Java-owned quest exception. |
| `engine/server/src/main/java/com/rs2/script/registries/CommandHandlerRegistry.java` | Current raw guest command map and the lookup half of the command lease gap. |
| `engine/server/src/main/java/com/rs2/script/registries/ObjectHandlerRegistry.java` | Exact object/action keys that host area, raid, boss, and resource routes must share rather than bypass. |
| `engine/server/src/main/java/com/rs2/script/registries/InteractionHandlerRegistry.java` | Existing exact button/item/magic keys and compatibility facades for the unified route registry. |
| `engine/server/src/main/java/com/rs2/script/quest/QuestDefinitionParser.java` | Strict parser pattern to reuse for all Java-owned descriptors. |
| `engine/server/src/main/java/com/rs2/script/quest/QuestService.java` | Existing authoritative quest transitions and requirement checks. |
| `engine/server/src/main/java/com/rs2/script/quest/QuestRewardTransaction.java` | Existing atomic quest reward implementation to extract, including inventory-derived `player.weight` recalculation, exact old-weight restore, verification, and best-effort presentation ordering. |
| `engine/server/src/main/java/com/rs2/script/quest/ScriptedQuest.java` | Public quest capability that lacks current-objective access. |
| `engine/server/src/main/java/com/rs2/script/state/ScriptStateCodec.java` | Current built-in v1 decoder and package-private migration injection seam. |
| `engine/server/src/main/java/com/rs2/game/players/PlayerSave.java` | Quarantine, atomic character-file replacement, and the real migration boundary. |
| `engine/server/src/main/java/com/rs2/game/content/quests/QuestAssistant.java` | Legacy quest-tab rows/buttons and the journal compatibility seam. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/ClickingButtons.java` | Production quest-button order and exact scripted button authority. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/ScriptInteractionGate.java` | Universal validate/lock/dispatch/fallback order that host-consumer routes must preserve. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/Commands.java` | Existing `::reload`/`::scripts` aliases, unconditional reload success, and `::scriptdir` absolute-path disclosure to inventory and replace. |
| `engine/server/src/main/java/com/rs2/net/PacketSender.java` | Quest-tab refresh and generic interface/string primitives. |
| `engine/server/src/main/java/com/rs2/script/world/ScriptEncounterHandle.java` | Accepted WP5 encounter surface; WP6 will add deterministic drop methods. |
| `engine/server/src/main/java/com/rs2/script/world/ScriptEncounterService.java` | Ownership, reservations, tasks, locks, cleanup, and generation lifecycle. |
| `engine/server/src/main/java/com/rs2/script/world/ScriptEncounterRng.java`, `ScriptDropTransaction.java` after Phase 4 WP6 | Normative local-RNG snapshot, ground staging, verification, rollback, and commit algorithm that WP2 must extract without changing its vectors. |
| `engine/server/src/main/java/com/rs2/script/world/ScriptNpcService.java` | Current exact encounter-NPC identity and death ownership seam; area spawns require a separate exact owner without id-wide suppression. |
| `engine/server/src/main/java/com/rs2/world/WorldObjectService.java` | Authoritative layered objects and reversible generation-owned area/resource writes. |
| `engine/server/src/main/java/com/rs2/world/ItemHandler.java` | Exact ground identity and private reward projection. |
| `engine/server/src/main/java/com/rs2/game/npcs/NpcHandler.java` | Stable NPC allocation/death loop; today only exact `ScriptNpcService` ownership suppresses `dropItems`, while ordinary equal-id NPCs retain legacy drops. |
| `engine/server/src/main/java/com/rs2/game/npcs/drops/NPCDropsHandler.java` | Legacy id/name drop lookup that must remain active for unmatched NPC identities. |
| `engine/server/src/main/java/com/rs2/game/shops/ShopHandler.java` | Static/player shop provenance and compatibility storage to adapt. |
| `content/src/core/runtime.ts` | Exact executable bridge declarations; currently ends at accepted WP5. |
| `content/src/core/{types,boss,raid,drop-tables}.ts` | Aspirational domain types and current loot semantics requiring runtime alignment. |
| `content/src/{bosses,raids,areas,quests}/*-builder.ts` | Existing builders to audit, migrate, and deep-freeze rather than rewrite blindly. |
| `content/src/areas/types.ts` | Current nested area schema for NPCs, objects, shops, quests, bosses, and raids. |
| `content/src/quests/{types,quest-builder,dragon-awakens}.ts` | Exact quest schema, author validation, and production quest proof. |
| `content/src/loader.ts` | Deterministic direct-import entry point to replace with an explicit source-aware manifest. |
| `engine/server/src/test/java/com/rs2/script/ScriptHostTest.java` | `currentCompiledLoaderRegistersEverySupportedCategory`, candidate rollback, and sandbox conventions that WP1 compatibility records must preserve. |
| `engine/server/src/test/java/com/rs2/script/ScriptHostDispatchLeaseTest.java` | Lookup/invocation/reload synchronization pattern; command dispatch must join this lease. |
| `engine/server/src/test/java/com/rs2/net/packets/impl/CommandsTest.java` | Current command authority/throw/metadata tests to extend for host routes and reserved aliases. |
| `engine/server/src/test/java/com/rs2/script/world/ScriptDropTransactionTest.java` after Phase 4 WP6 | Literal RNG, staged-ground rollback, and exact-identity baseline that owner-neutral and area adapters must preserve. |
| `engine/server/src/test/java/com/rs2/script/quest/*Test.java` | Existing parser, transition, reward, and concurrency contracts. |
| `engine/server/src/test/java/com/rs2/script/state/ScriptStateCodecTest.java` | Current codec malformed-input and injected migration coverage. |
| `engine/server/src/test/java/com/rs2/game/players/PlayerScriptStatePersistenceTest.java` | Real save/load/quarantine/atomic-file test seam. |
| `engine/server/data/cfg/npc.json` | Proves shipped custom boss/area/raid ids are not definition-backed and cannot pass future strict consumers unchanged. |
| `engine/server/src/main/java/com/rs2/game/items/DeprecatedItems.java` | Current first-match item-name lookup is ambiguous and cannot be the canonical definition resolver. |
| `content/src/bosses/dragon-king.ts`, `content/src/areas/dragon_island/**`, `content/src/raids/temple-of-zaros/raid.ts` | Legacy fixtures that WP1 must preserve and WP3/WP4/WP5 must migrate with their final schemas. |
| Phase 4 WP6/WP7 accepted source and tests | Direct prerequisites | Re-ground names and contracts after those packages land; do not plan against draft-only symbols. |

## Work Package Status

Phase 5 contains ten work packages. They are deliberately partitioned by
runtime ownership and dependency boundaries, not by equal size.

| Work package | Status | Direct prerequisite | Boundary/result |
|--------------|--------|---------------------|-----------------|
| WP1: Establish source-aware manifest, route, and activation infrastructure | completed (accepted 2026-08-02) | Phase 4 complete (WP6/WP7 accepted; global gate) | Common envelope, authoritative guest/host routes, and synthetic activation proof; final consumer schemas remain deferred. |
| WP2: Consume drop and reward definitions transactionally | completed (accepted 2026-08-02) | WP1; Phase 4 WP6 | Owner-neutral RNG/ground drops and player-local named rewards provide exact adapters. |
| WP3: Consume declarative boss definitions | completed (accepted 2026-08-02) | WP2; Phase 4 WP6 and WP7 | Encounter-agnostic controller plus standalone encounter-owning adapter. |
| WP4: Consume declarative area definitions | completed (accepted 2026-08-02) | WP1-WP3 | Reversible area activation plus exact non-encounter NPC/object drop authority. |
| WP5: Consume declarative raid definitions | completed (accepted 2026-08-03) | WP2-WP4; Phase 4 WP6 | Explicit party, one raid encounter, borrowed boss, and roster-wide all-or-none rewards. |
| WP6: Complete quest consumption, journal, and state migration | completed (accepted 2026-08-03) | WP1 | Current objective drives generic UI; historical state migrates through `PlayerSave`. |
| WP7: Stabilize the reusable public content SDK | completed (accepted 2026-08-03) | WP2-WP6 | Builders are aligned with proven consumers and exported as the stable authoring kit. |
| WP8: Implement the gathering/resource-loop runtime | completed (accepted 2026-08-04) | WP4, WP7; Phase 4 WP6 | Tool/level/tick/reward/depletion/respawn/cancellation loop. |
| WP9: Add operator diagnostics and admin control | completed (accepted 2026-08-05) | WP1-WP8 | Truthful bounded status/list/reload and sanitized legacy `scriptdir`. |
| WP10: Ship representative vertical content and migration docs | completed (accepted 2026-08-06) | WP1-WP9 | Public-SDK-only quest/resource/area/boss proof, raid consumer proof, and authoring guide. |

### Dependency and Execution Order

1. Do not start Phase 5 until Phase 4 WP6 and WP7 are both accepted and Phase
   4 is marked complete.
2. Execute WP1 through WP10 strictly in numeric order, one work package at a
   time, with independent implementation review and the primary gate passing
   before advancing.
3. WP2, WP3, WP5, and WP8 directly consume the Phase 4 WP6 RNG/drop contract;
   if that accepted contract differs from the current draft, revise this file
   before execution.
4. WP3 directly consumes the Phase 4 WP7 production boss fixture and tests;
   preserve that fixture's entry, tick, death, private pickup, and cleanup
   evidence while replacing imperative orchestration with a declarative
   consumer.
5. WP7 may remove or rename aspirational builder fields only through a
   documented compatibility adapter and migration entry; it must not invent
   capabilities that WP2-WP6 did not prove.
6. WP2, WP3, WP4, and WP5 each finalize only their own definition schema and
   atomically migrate the corresponding shipped fixtures in that same package.
   WP1's common envelope is not permission to enforce their future loaded-id,
   field, callback, or cross-reference rules early.

## Implementation Steps

### Step 1 (WP1): Establish source-aware manifest, route, and activation infrastructure

- **What**: Introduce an explicit content-module manifest, a common immutable
  `DefinitionRecord` envelope (`kind`, stable key, declared schema version,
  bounded logical source, generation-owned legacy payload or later typed
  descriptor), one unified executable-route registry for guest callbacks and
  Java host consumers, and a reusable two-phase runtime activation
  transaction. WP1 does not define final boss/area/raid fields or callbacks.
- **Where**: `ScriptHost`, `ScriptBindings`, `ScriptFunctions`,
  `RegistryStore`, `CommandHandlerRegistry`, `ObjectHandlerRegistry`,
  `InteractionHandlerRegistry` and the other exact handler registries,
  `ScriptInteractionGate`, `Commands`, new `com.rs2.script.definition`, new
  `com.rs2.script.route`, new `com.rs2.script.activation`,
  `content/src/core/definitions.ts`, `content/src/manifest.ts`,
  `content/src/loader.ts`, `ScriptHostDispatchLeaseTest`, `CommandsTest`, and
  compiled-loader assertions in `ScriptHostTest`.
- **Why**: Later declarative consumers need one exact authority/lease model and
  a real old/new activation protocol. They cannot safely build separate host
  routes beside raw guest maps or infer final schemas from the current
  aspirational fixtures.
- **Considerations**:
  - Use one synchronous `registerContentModule({ id, schemaVersion }, fn)`
    candidate-loading scope. It records a bounded logical module id rather
    than a host path, clears in `finally`, and rejects nested/duplicate module
    scopes. Direct existing globals remain supported as `legacy-unscoped`
    records with generated compatibility schema version `0`. WP1 wraps current
    boss/area/raid guest payloads without applying
    future loaded-id, strict-member, callback, or cross-reference rules; the
    compiled loader and every existing category must still load unchanged.
  - Each WP2-WP6 consumer replaces only its own legacy payloads with final
    typed Java descriptors and migrates its shipped fixture in that same
    atomic candidate. Until then the compatibility record stays data-only and
    generation-owned. Common validation in WP1 is limited to envelope/module
    bounds, declared version shape, route keys, source identity, and duplicate
    common keys.
  - Define `ExecutableRouteKey` as route kind plus its canonical exact key
    (for example command name, static object id/action, or a resolver-captured
    generation-owned object projection identity/action) and
    `ExecutableRouteRecord` as metadata plus exactly one invoker: a
    generation-owned guest `Value`, or a Java `HostRoute` owned by candidate
    runtime state. Compatibility registry classes become typed facades over
    this immutable candidate-wide registry rather than independent maps.
    Observational login/logout/death/pickup/area callbacks receive the common
    source/callback envelope but remain lifecycle observers, not authoritative
    routes, because they do not own a consumed-versus-legacy decision.
  - Route uniqueness has no precedence escape hatch: guest-vs-guest,
    guest-vs-host, and host-vs-host registrations for the same exact key all
    reject the candidate and identify both kind/key/source records. Command
    aliases `scripts`, `reload`, and the existing path-bearing `scriptdir` are
    reserved before module evaluation and reject both guest and host content
    registrations. They remain owned by Java admin transport; WP9 replaces
    `scriptdir`'s absolute-path response with its specified sanitized alias.
  - Authority is identical for guest and host invokers. A packet/command is
    universally decoded and validated, then action-locked where its existing
    matrix requires, then performs exact lookup and invocation under one
    `ScriptHost.dispatchActive` generation lease. An exact route returning
    success or a handled rejection is consumed; an exact route throwing is
    contained and still consumed; only a valid unmatched key continues to the
    existing legacy path; invalid input reaches neither route nor legacy.
    Migrate `Commands.executeScriptCommand` to this lease and construct its
    `ScriptedPlayer` with the leased generation.
  - Refactor `ScriptHost.replaceContext`, which currently publishes before
    fallible post-commit work, around `RuntimeActivationTransaction`:
    1. evaluate the candidate and prepare immutable descriptors, routes, and
       projection intents without touching live state;
    2. acquire a handoff reservation over every predecessor/replacement
       runtime key, NPC slot, object footprint, shop/drop binding, and report
       identity, blocking third-party writers while allowing the exact old/new
       pair to share a logical footprint;
    3. reversibly apply the candidate under an inactive owner token (shadow
       route/runtime state, reserved-but-not-visible NPCs, staged object/shop/
       drop projections) and verify it without guest visibility;
    4. on the game-cycle/world owner while holding the `ScriptHost` dispatch/
       reload lease (so no packet, callback, or world tick can observe the
       handoff), retire predecessor
       projections into an idempotent undo ledger without releasing the
       handoff reservation, verify both retirement and candidate state, and
       pass the final injectable pre-publication checkpoint. This is the last
       operation allowed to fail or abort;
    5. with registration scopes already closed, execute the old generation's
       `onUnload` as a contained, non-vetoing observer. The hook runner catches
       return or throw into a preallocated bounded result, and attempted
       registration cannot alter the prepared bundle. Other guest-visible
       effects are not generally reversible, so once hook invocation begins
       no verification, checkpoint, allocation, lock release, or other
       fallible/injectable step may intervene before publication;
    6. immediately perform one no-throw commit assignment that makes context, generation,
       frozen registries/routes, runtime owner, projection selector, manifest,
       report, and the captured unload result visible together. Every attempted
       `onUnload`, including a mutating hook that throws, is necessarily
       followed by candidate publication; neither hook effects nor the commit
       are claimed rollbackable;
    7. execute new `onLoad` as a contained, non-vetoing active-generation
       observer with registration closed, then discard predecessor undo/shadow state, release handoff
       reservations, close the old context, and retry/quarantine any
       non-authoritative final cleanup failure without reverting the published
       candidate.
  - Any failure before the commit assignment aborts in reverse order: restore
    predecessor from the undo ledger if retirement began, remove candidate
    shadow projections, release reservations, close candidate context, and
    leave the complete previous `ActiveState` and report selected. Undo
    operations are idempotent/non-guest and retry while the handoff reservation
    is held; a permanent invariant failure is quarantined and reported as
    fatal rather than falsely claiming last-known-good restoration. Failure
    injection covers prepare, reservation, candidate apply, verification,
    predecessor retirement, first rollback attempt, and the final checkpoint,
    all before `onUnload`. An abort invokes no unload hook. WP1 proves the
    protocol with synthetic projections; WP4 must
    prove it with real same-footprint NPC/object/shop/drop area state.
  - Preserve `HostAccess.EXPLICIT`, Java 8 source compatibility, the read-only
    module filesystem, Phase 1-4 globals, and exact legacy fallback.
- **Definition of Done**:
  - The current compiled loader registers every existing category as explicit
    source-aware canonical or `legacy-unscoped` records without requiring the
    future boss/area/raid schema or definition-backed custom fixture ids.
  - One immutable route registry owns guest and host route records, rejects
    every cross-source/owner conflict and reserved alias, and preserves exact
    consumed/unmatched/throw/invalid behavior.
  - Command lookup/invocation is generation-leased; a paused lookup across
    reload completes entirely on the old generation before replacement.
  - The synthetic activation matrix proves old/new same-key handoff, exact
    abort restoration before publication, atomic visibility at the no-throw
    commit line, no unload invocation on any abort, mandatory commit after any
    attempted unload (including a mutating/throwing hook), contained observer
    failures, and explicit degraded handling after final cleanup failure.
  - No final boss/area/raid schema, loaded-id validation, or cross-reference
    validation lands in WP1; each remains owned by its consumer package.
  - The primary gate passes before WP2 begins.

### Step 2 (WP2): Consume drop and reward definitions transactionally

- **What**: Add first-class `defineDropTable` and `defineReward` schema-v1
  definitions, strict Java parsers/registries, load-time item resolution, and
  gameplay consumers. Extract the accepted Phase 4 WP6 selection/staging
  algorithm into an owner-neutral Java `DropTransaction` that receives an
  explicit `DropRngTransactionOwner` and `GroundDeliveryPolicy`; the existing
  encounter API becomes one adapter rather than the only possible owner.
  Named rewards preflight and atomically commit inventory, recalculated
  inventory/equipment-derived `player.weight`, exact skill XP/current levels,
  quest points where allowed, and optional state mutations
  through a shared player-local transaction extracted from
  `QuestRewardTransaction`; WP5 composes it under a roster-wide coordinator.
  WP2 owns the final allowed members/bounds/reference contract and atomically
  migrates all shipped inline/legacy loot and reward fixtures to it.
- **Where**: New `com.rs2.script.drop` and `com.rs2.script.reward`,
  `RegistryStore`, `ScriptFunctions`/`ScriptBindings`,
  `ScriptEncounterHandle`, `ScriptEncounterRng`, encounter/ground services,
  `ItemHandler`, `ScriptedPlayer`, quest reward code,
  `content/src/core/drop-tables.ts`, and new reward definition/types.
- **Why**: Boss, raid, area, quest, and gathering definitions need one named,
  exact, rollback-safe reward vocabulary rather than independent callback
  mutations.
- **Considerations**:
  - Canonical drop entries match WP6 exactly: integral amounts/weights,
    `always: true` with weight `0`, bounded entry/identity totals, preserved
    order, and no `Infinity` or fractional weights at the Java boundary. The
    existing author-side `Infinity`/`0.25` forms receive a versioned migration
    adapter or a clear load-time migration error; canonical output never emits
    them.
  - Resolve numeric ids only when a loaded definition exists. String item ids
    resolve at candidate load through one exact, deterministic item-name
    resolver; missing or ambiguous names fail with definition source and
    field path. Do not use `DeprecatedItems.getItemId`, which silently chooses
    the first duplicate name. Runtime transactions use copied numeric ids only.
  - `DropRngTransactionOwner` is a Java-only game-cycle-owned contract: acquire
    its owner lock, snapshot exact state plus version into a local WP6 RNG,
    validate that version immediately before commit, publish the resulting
    state once, and release. Invalid input or abort never advances it. The
    encounter adapter supplies the encounter RNG/owner lock and participant;
    WP4 supplies an area-session owner without pretending it is an encounter.
  - `GroundDeliveryPolicy` is Java-only and explicitly owns eligibility,
    source location/plane, private recipient or public visibility, private TTL,
    identity budget, staging, verification, publication, and exact removal.
    `DropTransaction` holds the RNG-owner and ground-owner locks in the fixed
    order RNG owner then ground projection; selects from the local RNG, stages
    every ground identity invisibly, performs all capacity/version/identity
    checks, and reaches one no-throw commit that publishes those identities
    and the final RNG state together. Any earlier parse, selection, allocation,
    creation, verification, owner-version, or delivery failure removes every
    staged identity and leaves RNG/visible ground state exact.
  - The Phase 4 encounter adapter preserves `rollDrops` behavior exactly:
    reserved-arena location, an exact attached participant as recipient,
    encounter identity budget, private delivery/detach policy, literal RNG
    vectors, result order, and cleanup. No owner-neutral API or RNG state is
    guest-exposed.
  - Provide narrow result-shaped facades (named table/reward id plus result
    code), not access to registry maps or engine inventory arrays.
  - The shared player-local reward transaction snapshots exact old
    `player.weight`, computes expected weight from candidate inventory plus
    locked equipment, recalculates after item mutation, verifies it with every
    other postcondition, and restores the old exact value after restoring item/
    XP arrays on failure. A Java-only per-player reward-state owner provides
    the exact live player/session token, mutex, and monotonic version; reward
    commits increment it once while abort/rollback leaves it unchanged. Presentation refresh
    remains post-commit best effort in inventory, weight, skill, then quest/
    state order.
  - A parse, lookup, capacity, XP-cap, amount, ground creation, detach, or
    postcondition failure changes neither items, derived weight, XP, points,
    state, world identities, nor encounter RNG.
- **Definition of Done**:
  - At least one named drop table creates and detaches the exact private
    identities through a real encounter, and one named reward commits through
    a real player transaction.
  - Every failure path proves zero partial items, `player.weight`, XP, quest
    points, state, ground identities, and RNG advance.
  - Owner-neutral transaction tests run the same literal table through
    encounter and synthetic non-encounter owners, inject every stage/verify/
    final-revalidation failure, and prove identical selection plus atomic
    RNG/ground commit or exact rollback; WP4 must add its real area adapters.
  - Quest completion uses the shared reward transaction without changing its
    existing result codes or retry behavior.
  - Duplicate/missing/ambiguous named references reject the candidate with
    both consumer and source definition identified.
  - Shipped area/boss/raid loot inputs needed by later WPs have canonical
    named records (or explicit legacy records awaiting their consumer), and
    compiled-loader assertions prove no silent loss during migration.
  - Existing WP6 literal RNG/drop vectors and all quest reward tests remain
    unchanged and pass.

### Step 3 (WP3): Consume declarative boss definitions

- **What**: Finalize the strict schema-v1 boss parser/callback contract and add
  an encounter-agnostic `BossController`. The controller borrows a supplied
  encounter, arena slice, and participant view while driving spawn, ordered
  phase thresholds, special cooldowns, target policy, visuals/projectiles,
  named drops, death, and boss-local cleanup. A separate
  `StandaloneBossSession` adapter owns/begins/closes an encounter for ordinary
  boss entry. Replace the unsafe aspirational `BossContext` with a narrow
  runtime context composed only of accepted wrappers/handles.
- **Where**: New `com.rs2.script.boss`, boss registry/descriptor callbacks,
  `ScriptLifecycleService`, encounter/death seams, `content/src/core/boss.ts`,
  `content/src/bosses/boss-builder.ts`, and the accepted Phase 4 WP7 fixture
  and E2E test.
- **Why**: `defineBoss` currently stores data only; the accepted imperative
  boss proves the engine mechanics but not reusable declarative consumption.
- **Considerations**:
  - Canonical boss descriptors include a stable string id, definition-backed
    NPC id/stats, bounded arena/spawn, explicit command or object entry,
    ordered phases, named specials/cooldowns, named drop table/private TTL,
    and cleanup policy. Numeric `npcId` remains a duplicate key for combat
    ownership. WP3 owns the exact allowed members, bounds, callback result
    codes, compatibility transform, and cross-references; WP1 does not.
  - `BossController.start(handle, arena, participants)` never calls
    `beginEncounter`, adds/removes raid membership, or closes its borrowed
    handle. It owns only its NPCs, boss callbacks/tasks, phase/special state,
    and boss-local objects. It reports `RUNNING`, `DEFEATED`, or `FAILED` to
    its caller. The standalone adapter registers the definition's exact WP1
    host route, creates one handle, supplies the controller, awards standalone
    drops, and closes that handle on terminal result.
  - Each callback runs under its active generation lease. A callback exception
    makes the controller `FAILED`; the standalone adapter closes its encounter,
    while WP5's raid adapter applies raid wipe policy without a nested close.
    Reload, owner death/logout, explicit close, and normal death converge on
    the accepted idempotent owner cleanup path.
  - The runtime must not emulate combat/pathfinding in TypeScript. It composes
    `ScriptNpcHandle`, `ScriptEncounterHandle`, locks, scheduler, presentation,
    collision-aware objects, and WP6 drops.
  - Preserve Phase 4 WP7's real command packet, game ticks, NPC death loop,
    private pickup, observer isolation, and zero-resource cleanup evidence.
    Atomically migrate `dragon-king.ts` and the accepted WP7 fixture to the
    final schema and WP1 host routes in WP3. Custom npc id `12001` is not in
    `npc.json`; migrate the fixture to a loaded definition-backed id rather
    than weakening validation or silently retaining an inert canonical boss.
- **Definition of Done**:
  - A registered boss starts from its declared production entry and executes
    spawn, phase, special, death, named drops, pickup, and cleanup without an
    imperative content-side encounter state machine.
  - The same `BossController` passes a borrowed-handle test that proves it
    creates no second encounter/reservation, never closes the supplied handle,
    and removes only its boss-local resources; standalone behavior still owns
    exactly one handle and remains unchanged.
  - Phase and special callbacks run exactly once/at their declared cadence in
    deterministic FIFO order; stale-generation callbacks never run.
  - Normal death, explicit close, callback throw, owner death/logout, and
    successful reload leave zero encounter resources; rejected reload leaves
    the running boss unchanged.
  - Nonparticipants retain the Phase 4 isolation guarantees.
  - The migrated compiled loader contains no canonical boss with an unloaded
    NPC id, missing route, or legacy raw callback shape.
  - The converted WP7 production E2E and the primary gate pass before WP4.

### Step 4 (WP4): Consume declarative area definitions

- **What**: Add `ScriptAreaRuntime` and a reversible generation-owned
  consumer for area bounds/lifecycle, NPC spawns, object projections, drop
  bindings, scripted shops, and references to quest/boss/raid definition
  records. WP4 owns the final strict area/shop schema, parser, callback
  contract, bounds, and compatibility transform, and atomically migrates
  `dragon_island` with the runtime. Normalize nested current area data into
  canonical ids so every referenced definition has one owner and one source.
- **Where**: New `com.rs2.script.area` and `com.rs2.script.shop`, area
  registry/descriptor, area-session RNG and `AreaDropAdapter`,
  `ScriptLifecycleService`, `NpcHandler`, `WorldObjectService`, `ItemHandler`,
  `ShopHandler`/`ShopAssistant`,
  `ScriptedPresentation`, `content/src/areas/types.ts`, and
  `content/src/areas/area-builder.ts`.
- **Why**: `defineArea` currently neither populates the world nor invokes its
  hooks, and the current inline nested schema cannot provide authoritative
  cross-definition ownership.
- **Considerations**:
  - Express the complete area as WP1 `ProjectionIntent`s and use its exact
    activation protocol: prepare/validate without mutation; acquire one
    predecessor/replacement handoff reservation; stage inactive NPC/object/
    shop/drop state; verify; retire the predecessor into the undo ledger;
    commit the projection selector with `ActiveState`; then finalize. Do not
    add a second best-effort area swap beside `ScriptHost`.
  - Preflight loaded NPC/object/item definitions, world layers, bounds, WP1
    route conflicts, shop limits, and references before reservation. A raid
    reference in WP4 resolves only to its common definition record; WP5 owns
    the later strict raid-schema validation.
  - Area-owned NPC/object/shop records carry generation/source tokens and use
    compare-and-remove. They do not rewrite `spawns.json`, cache objects,
    legacy static shops, or unregistered drop behavior.
  - Each activated area owns one Java-only `AreaSessionRng`, derived with the
    accepted WP6 mix/seed algorithm from generation plus monotonic area owner
    token and activation ordinal, logged for replay, and serialized by
    game-cycle FIFO event order. It implements WP2's RNG-owner contract; a
    failed death/object drop leaves its version/state unchanged. Reload never
    reuses an owner token/ordinal.
  - An NPC drop binding is indexed by the exact generation-owned spawn
    allocation identity plus source/area/spawn key, never by `npcType` alone.
    The `NpcHandler` death critical section captures the exact NPC and killer
    identities, asks one singular scripted-death authority to claim that
    allocation, and invokes the WP2 transaction once. `UNMATCHED` runs current
    `dropItems` exactly once; `MATCHED` suppresses legacy drops even when the
    canonical transaction returns a handled rejection or contained failure.
    An equal-id legacy NPC or a stale/reused allocation remains unmatched and
    retains its complete legacy drop/special-case path.
  - Both private and public area-NPC policies require the captured killer slot
    still to contain the exact live player object at commit. Null/stale killer,
    wrong plane, or an ineligible player produces handled `NO_RECIPIENT` with
    no RNG/ground mutation and no legacy fallback for the claimed spawn.
    `PRIVATE_TO_KILLER(privateTicks)` creates exact killer-private identities;
    `PUBLIC` creates exact public identities at the captured death tile. The
    selected policy, location, source identity, and amount are immutable
    inputs to WP2's transaction.
  - An area object drop uses a WP1 host route keyed by the resolver's exact
    generation-owned object projection identity plus action, not merely its
    object id. The acting live player is its required recipient; private/public
    delivery follows the same policy. A valid claimed route is consumed on
    success, handled rejection, or throw. A cache/legacy object with the same
    id/action has no owner-route key and continues through guest/legacy exact
    lookup once; invalid or locked packets reach neither path.
  - Successful reload compare-removes the old death bindings/object routes,
    closes the old area RNG owner, and removes only its unclaimed exact staged
    or published ground identities before the old context closes. Rejected
    reload preserves bindings, RNG position, and ground identities. Pickup,
    expiry, close, and reload race through one idempotent exact-token cleanup;
    no equal legacy/public identity is removed.
  - Same-source reload of the same NPC slot/object footprint/shop/drop route is
    an explicit handoff, not a duplicate: the transaction reservation permits
    only the exact predecessor and replacement owner tokens while blocking
    third-party writes. Old masks/projections remain selected until the
    no-throw commit line; new projections are invisible before it.
  - Scripted shops are immutable Java-owned definitions with bounded stock,
    prices, and restock policy. Add a narrow `openScriptShop(id)` capability;
    keep `openStaticShop(number)` and legacy player shops unchanged.
  - `onUnload` executes once as a contained non-vetoing old-generation
    observer only after successful retirement verification and the final
    injectable checkpoint, immediately before the mandatory no-throw commit;
    `onLoad` executes once as a contained non-vetoing new-generation observer
    after commit. Their exceptions are reported but cannot cause a false
    rollback. Enter/leave callbacks use ordinary active-generation routes.
  - `dragon_island` currently uses custom NPC id `5001`, which is absent from
    `npc.json`; migrate it to a loaded definition-backed fixture id in WP4.
    Do not weaken canonical validation or require WP1 to reject the legacy
    compiled loader before this migration.
- **Definition of Done**:
  - One area definition visibly activates its bounds, NPC, object, shop, drop
    association, and lifecycle behavior through real engine paths.
  - Successful reload replaces exact generation-owned projections once;
    failed reload and activation failure preserve the prior world exactly.
  - Real area tests inject prepare, reservation, inactive apply, verification,
    predecessor retirement, first undo attempt, and pre-publication failures;
    every pre-commit case restores exact NPC allocation, object visibility and
    movement/projectile masks, shop/drop bindings, manifest/report, routes,
    generation, and context. The retrying undo ledger proves exact restoration
    before the handoff lease is released, and no aborted case invokes
    `onUnload`.
  - A same-footprint old/new area replacement is invisible until one atomic
    selector swap, has no missing/double-visible tick, and blocks a competing
    writer. A mutating/throwing `onUnload` after the passed final checkpoint is
    followed immediately by commit; `onLoad`/cleanup failures are post-commit.
    Tests report these effects honestly rather than pretending to roll them
    back.
  - Conflicting world footprints/routes/ids fail or defer according to the
    accepted Phase 4 object protocol without clobbering legacy content.
  - Area cleanup removes only matching generation/source identities and
    leaves legacy NPCs, objects, shops, and drops unchanged. Exact claimed
    area drops cannot double-run legacy drops; unmatched equal-id NPCs and
    objects retain fallback once.
  - Real death tests cover exact bound spawn, equal-id unbound legacy spawn,
    stale allocation, private/public visibility, null/stale killer, transaction
    throw, staged-ground failure, pickup/expiry, accepted/rejected reload, and
    exact cleanup, asserting ground tokens and area RNG before/after. Real
    object-packet tests cover exact owned projection, equal-id cache/legacy
    object, invalid/locked input, handled rejection/throw, and no double drop.
  - Nested compatibility input either normalizes to canonical ids or fails
    with an actionable source/path diagnostic.
  - The migrated compiled loader contains no canonical area with unloaded NPC/
    object/item ids or inline duplicate definitions, and the primary gate
    passes before WP5.

### Step 5 (WP5): Consume declarative raid definitions

- **What**: Add `ScriptRaidRuntime` that consumes an ordered room graph,
  entrance/party limits, room bounds, optional boss references, completion/
  wipe callbacks, time limit, and named rewards. WP5 owns the final strict raid
  schema/parser/callback/result contract and atomically migrates the temple
  fixture. A started raid owns exactly one encounter/reservation and embeds
  WP3 `BossController` instances by borrowing that handle.
- **Where**: New `com.rs2.script.raid`, raid registry/descriptor callbacks,
  encounter/lifecycle services, new `RosterRewardTransaction` and its
  per-player reward mutex/coordinator seam, `content/src/core/raid.ts`,
  `content/src/raids/raid-builder.ts`, and the temple raid content fixture.
- **Why**: Raid definitions and fluent builders are currently inert data with
  rich `Player` callbacks that cannot execute safely at the Graal boundary.
- **Considerations**:
  - Replace `RoomContext.players: Player[]` with immutable/narrow participant
    wrappers and explicit result codes. Room entry/tick/complete callbacks are
    generation-owned, bounded, and exception-contained.
  - Canonical raid entry is one exact WP1 host command route. Its bounded
    subcommands are `create`, `invite <player>`, `join <owner>`, `leave`, and
    `start`. A valid exact route is authoritative and consumed even when the
    requested lobby operation is rejected; an absent raid command alone falls
    through to legacy. Conflicts with guest commands, another host route, or
    reserved aliases reject the candidate.
  - `create` makes a pre-encounter lobby owned by the exact live inviter
    identity and a generation/session token. `invite` records an exact live
    invitee identity while both players are outside any lobby/encounter and
    capacity remains. `join` is the invitee's explicit opt-in and compare-
    consumes that invitation; resolving the same username to a replacement
    player object fails. Duplicate invites/joins and membership in another
    lobby/session are no-ops with a result message.
  - Only the owner may `start`. Start requires `minPlayers..maxPlayers`
    explicitly opted-in live identities on the declared entrance plane and
    within its bounded muster area. It freezes an immutable roster ordered
    owner-first then join FIFO, begins one encounter, and atomically adds every
    roster identity before any room callback. Any begin/add failure closes the
    partial handle and leaves the lobby retryable. After start there are no
    invites, joins, replacements, or roster reordering.
  - Resolve boss, area, drop, and reward ids at candidate validation. Reject
    empty/duplicate rooms, impossible player limits, overlapping room bounds,
    unreachable ordering, and missing references before publication.
  - The raid owns the sole `ScriptEncounterHandle`. A boss room constructs a
    WP3 controller with that handle, the room arena slice, and the raid's
    current active subset. Controller `DEFEATED` advances the room; `FAILED`
    wipes. The controller never begins or closes an encounter and no player
    may acquire a second encounter membership.
  - Before start, owner leave/logout/death closes the lobby and invitations;
    a non-owner leave only removes its opt-in. After start, non-owner leave/
    logout/death marks that frozen roster member departed, removes its live
    participation, and permits no replacement; the raid continues while the
    owner remains active and the active roster is nonempty. Owner leave/logout/
    death, zero active members, room callback/controller failure, or timeout
    invokes `onWipe` once, awards nothing, and closes. Completion enters an
    explicit reward barrier and freezes the surviving active roster; no later
    departure or replacement changes reward eligibility. All callbacks are
    contained; cleanup is non-guest and idempotent.
  - The raid has exactly one Java-owned raid-session RNG owner with immutable
    owner token, monotonic state version, state, and lock; room, boss, drop,
    and reward randomness all transact through that owner. Entering the reward
    barrier cancels/joins prior raid tasks and forbids new room/boss/drop RNG
    consumers, but the reward path must still compare owner token/version. A
    stable award transaction id of raid owner token plus completion ordinal is
    checked on every tick; an immutable reward plan is never retained without
    its locks across ticks.
  - One bounded synchronous reward attempt acquires locks in this global order:
    roster-reward coordinator; raid-session RNG-owner lock; exact live player
    identity locks by ascending `PlayerHandler` slot/session token; then, for
    each player in that same slot order, inventory/equipment/derived-weight,
    skill/XP, quest-point, and script-state locks. With the full set held it
    captures the RNG owner token/version/state and each player's reward-state
    version and complete snapshot, clones the local WP6 RNG, and resolves named
    rewards into immutable plans in frozen owner-first/join-FIFO order.
  - Each player snapshot contains full inventory id/count arrays, the full
    equipment array used by `Weight.calculateWeight`, exact `player.weight`,
    full skill XP/current-level arrays, quest points, script-state values/
    versions, and the composite reward-state version. Preflight covers exact connected
    identity/eligibility, definitions, aggregate stack/slot capacity, item/
    amount overflow, resulting XP cap/current level, quest points, state
    conditions, finite/exact current weight against captured inventory/
    equipment, and the expected post-reward weight computed from candidate
    inventory plus the captured equipment.
  - Capacity/XP/state/eligibility preflight failure applies no mutation,
    discards the local plan, releases every lock, and leaves live RNG state/
    version unchanged. A later grace-period tick acquires the complete lock set
    and clones/plans again from the then-current live RNG without advancing it;
    no prior plan is reused. Immediately before the first player mutation, the
    coordinator revalidates the award id is absent, the exact RNG owner token/
    version, and every player identity, reward-state version, inventory,
    equipment, weight, XP/levels, points, and script-state snapshot. Any
    mismatch discards the plan without mutation and consumes one bounded retry.
  - Mutation follows frozen roster order and the shared player-local protocol:
    copy candidate inventory/amount arrays, XP/current levels, quest points,
    and script state, then recalculate `player.weight` with
    `Weight.calculateWeight(player.playerItems, player.playerEquipment)`.
    Verify every field including exact derived weight before moving to commit.
    Any exception or injected
    second-player postcondition failure restores members in reverse roster
    order using the `QuestRewardTransaction` ordering—inventory arrays, XP/
    levels, exact old weight, quest points, and script state—then verifies
    every full snapshot and that the not-yet-committed reward-state version
    remains captured. Live raid RNG owner state/
    version and award id remain unchanged; no completion callback or
    presentation runs. Rollback failure is fatal/quarantined and is never
    reported as a clean retry.
  - With all locks still held and all player postconditions verified, one
    no-fail owner commit advances raid RNG state/version, publishes the new
    player reward-state versions, and records the once-only award id together.
    Locks then release; inventory, weight, skill, and quest/state presentation
    refreshes run best-effort in that order, `onComplete` is invoked once as a
    contained post-commit observer, and the raid closes. Re-entry with the
    awarded id is a no-op. Capacity/preflight or version-mismatch retries are
    bounded by the configured reward grace period; exact participant departure
    or grace expiry invokes `onWipe` once, awards nobody, and closes.
  - Room changes, timeouts, wipes, death/logout, rewards, and cleanup use the
    accepted encounter identity and scheduler semantics. Rejected reload keeps
    the lobby/session and route generation; accepted reload closes old lobbies
    and sessions before their context closes.
  - `temple-of-zaros` currently requires two players and uses custom NPC ids
    `7001`, `7002`, and `7003`, which are absent from `npc.json`. WP5 migrates
    it to a two-distinct-player command-entry fixture and loaded definition-
    backed NPC ids; it does not relax loaded-id validation.
  - Preserve ordinary movement/combat and all unregistered raid entrances.
- **Definition of Done**:
  - A registered raid starts through a real declared entrance, admits only the
    bounded party, advances at least two rooms including one declarative boss,
    commits named rewards, and closes with zero resources.
  - The production fixture uses two distinct live player identities: owner
    creates/invites, invitee explicitly joins, owner starts at `minPlayers=2`,
    and the immutable roster/order is asserted.
  - Entry tests cover unauthorized/duplicate invite, join without invite,
    replaced identity, max capacity, below-minimum start, non-owner start,
    wrong muster area/plane, atomic participant-add failure, late join, and
    simultaneous membership rejection.
  - The embedded boss shares the raid's sole encounter token/reservation,
    returns its terminal result without closing it, and standalone WP3 boss
    behavior remains unchanged.
  - Wipe, timeout, callback failure, participant logout/death, explicit close,
    and reload have deterministic, independently asserted outcomes.
  - Owner and non-owner leave/logout/death cases prove the pinned lobby/session
    policy, no replacement after start, exactly-once wipe/complete, reward
    eligibility, and zero lobby/invite/roster/encounter residue.
  - Two-player reward tests prove full-inventory, XP-cap, player-state-version,
    RNG-owner-version mismatch, and injected second-player mutation/
    postcondition failures restore both players' inventory, XP/levels, quest
    points, script state, and exact pre-attempt `player.weight`; each captured
    reward-state version, live raid RNG state/version, and award id remain unchanged,
    and `onComplete` is not called. Every retry discards the failed local plan,
    releases locks, and re-clones under the next bounded attempt. A repaired
    retry commits both players, recalculated weights, RNG state/version, and
    award id once, calls `onComplete` once, and closes; grace expiry/departure
    calls `onWipe` once with zero awards. Duplicate completion ticks and a
    throwing post-commit callback remain once-only.
  - Missing/cyclic/duplicate room and cross-definition references reject the
    candidate with source/path information.
  - A nonparticipant cannot enter, see, target, or claim raid-owned content.
  - The temple fixture and compiled loader are migrated atomically to the
    canonical schema/host route with loaded NPC ids; no inert legacy raid
    remains after WP5, and the primary gate passes before WP6.

### Step 6 (WP6): Complete quest consumption, generic journal, and historical migration

- **What**: Expose the active scripted quest objective through
  `QuestService`/`ScriptedQuest`, project scripted quest state into a generic
  legacy-client journal, refresh it on lifecycle and quest transitions, and
  finish the accepted Phase 3 codec/parser follow-ups including one built-in
  historical state migration through real `PlayerSave` load and v1 re-save.
- **Where**: Quest descriptor/parser/service/capability, new
  `ScriptQuestJournalService`, `QuestAssistant`, `ClickingButtons`,
  `PacketSender`, `ScriptStateCodec`, `ScriptStateVersionDecoder`,
  `PlayerSave`, quest TypeScript types/builders, and their tests.
- **Why**: Quest descriptors already affect gameplay, but objectives are only
  stored strings and Dragon Awakens still reports progress via ad-hoc messages;
  persistence has a migration seam but no production historical decoder.
- **Considerations**:
  - Add `objective(): string | null`: `null` for not started/missing stage,
    current stage text while in progress, and a stable completion summary for
    completed quests. It is a read-only projection of Java-owned descriptors
    and state.
  - Deterministically map sorted scripted quest ids to a validated bounded pool
    of currently unimplemented legacy quest-tab rows/buttons. Reject a
    candidate exceeding unique usable rows. Exact scripted `onButton`
    authority remains first; otherwise a mapped quest button opens generic
    interface `8134`, clears bounded text components, and shows name, summary,
    requirements, state, and current objective. Unmapped buttons retain the
    existing `QuestAssistant.questButtons` path.
  - Define the built-in historical `v0` body as canonical unpadded Base64URL
    over a flat binary sequence: `u16 entryCount`, then repeated bounded
    `u16 namespace UTF-8`, `u16 key UTF-8`, `u8 type`, and the existing v1
    type payload. The decoder groups entries, rejects duplicate namespace/key
    pairs, enforces every current aggregate/UTF-8/value limit, and returns a
    current snapshot. Encoding remains v1 only.
  - Add table-driven `-1/exact/+1` byte/count/value tests for every quest
    parser and state codec bound, unknown members/types, duplicates,
    truncation, trailing data, malformed UTF-8/Base64URL, and overflow.
- **Definition of Done**:
  - Starting, advancing, completing, logging in, and successful reload update
    the scripted quest row and generic detail interface from authoritative
    state; rejected reload changes neither mapping nor UI state.
  - Dragon Awakens uses the generic objective projection rather than its login
    progress workaround while preserving production quest behavior.
  - A real character file containing valid v0 state loads through
    `PlayerSave`, preserves legacy fields, installs the migrated state, and
    atomically re-saves v1. Malformed v0 remains quarantined and unsavable.
  - Legacy quest names/buttons/details and quest points remain unchanged.
  - Exhaustive parser/codec matrices and existing persistence/quest tests pass.

### Step 7 (WP7): Stabilize the reusable public TypeScript content SDK

- **What**: Align and deep-freeze reusable builders for requirements,
  transactional rewards, shops, equipment checks, dialogue/cutscenes, drop
  tables, encounters/bosses, areas, raids, and quests against the proven
  WP2-WP6 schemas and runtime results. Export them through one documented
  public SDK barrel with no dependency on engine internals.
- **Where**: Existing `content/src/*-builder.ts`, `content/src/core`, new
  `content/src/sdk/{requirements,rewards,shops,equipment,dialogue,cutscene}.ts`,
  and `content/src/index.ts`/`core/index.ts`.
- **Why**: Current builders contain useful validation but also aspirational
  unsafe contexts, incompatible loot weights, inline duplicates, and fields
  that no runtime consumes.
- **Considerations**:
  - Builders emit canonical schema-v1 values, attach manifest/source metadata
    through module registration, validate exact bounds, copy/deep-freeze all
    arrays/maps, and never expose mutable guest objects as descriptor data.
  - Requirement combinators are pure predicates over narrow runtime views.
    Reward helpers call the named transactional consumer. Dialogue/cutscene
    helpers own every continuation/task/lock/camera handle and cancel cleanly
    on completion, failure, logout, or reload.
  - Shop builders distinguish scripted definitions from numeric legacy static
    shop references. Equipment helpers use only the 11 accepted runtime slot
    names. Escape hatches stop at existing capability handles.
  - Keep compatibility adapters for currently shipped builder input where it
    can be represented exactly; otherwise fail at build/load with a migration
    message rather than silently ignore a field.
- **Definition of Done**:
  - Every public builder maps to an independently tested consumer or accepted
    Phase 1-4 capability; no exported field is data-only or aspirational.
  - Public examples import only from the SDK barrel and compile without
    ambient rich `Player` objects in executable callbacks.
  - Deep-freeze/mutation, invalid-bound, duplicate, missing-reference, stale
    callback, and cancellation tests pass.
  - Existing source-compatible Phase 1-4 runtime globals and facades remain
    declared exactly as implemented.
  - A generated API inventory matches runtime globals/exports before WP8.

### Step 8 (WP8): Implement the gathering/resource-loop runtime

- **What**: Add a versioned `GatheringResourceDefinition`, public builder,
  Java-owned parser/runtime, and per-player resource session. The runtime owns
  the exact WP1 host object route, validates skill/tools, animates on a bounded tick
  loop, performs deterministic success checks, awards exact item/XP
  transactions, depletes/restores the authoritative object, and cancels every
  owned task/lock/session cleanly.
- **Where**: New `content/src/sdk/gathering.ts` and representative resource
  module; new `com.rs2.script.resource`; definition registry/manifest;
  `ScriptLifecycleService`; scheduler/lock services; `WorldObjectService`;
  inventory/skill presentation seams.
- **Why**: The gated Phase 5 acceptance criteria require a complete gathering
  resource, which cannot be truthfully implemented by the current inert area
  builder or uncontrolled guest `Math.random()` callbacks.
- **Considerations**:
  - Canonical fields include stable id/source, exact object ids/action,
    bounded area, skill/required level, ordered tool alternatives with
    inventory/equipment policy, animation/tick interval, rational success
    chance, item/amount and exact XP reward, depletion rule, empty object
    replacement, and respawn ticks.
  - Reuse the accepted WP6 deterministic RNG algorithm with a resource-session
    seed owned by Java; invalid checks do not advance it. Do not create a fake
    exclusive encounter merely to obtain RNG.
  - Revalidate live player identity, position, action lock, exact world-object
    identity/version, skill, tool, and inventory capacity before every attempt
    and again before commit. Item plus XP is one rollback-safe transaction.
  - Register one `HostRoute` at each canonical object-id/action key. WP1
    candidate-wide uniqueness rejects conflicts with ordinary guest
    `onObject`, another host consumer, or another resource definition and
    reports both sources. Universal validation/action lock precedes it; exact
    handled rejection or throw is consumed, and only absence reaches legacy.
    Depletion/restoration uses the Phase 4 object transaction and respects
    reservations/deferred writers.
- **Definition of Done**:
  - A production-path gathering resource proves insufficient level/tool,
    inventory-full, successful item/XP, animation cadence, deterministic miss/
    success, depletion, exact respawn, movement-away cancellation, logout,
    death, object replacement, reload, and callback/runtime failure.
  - Every stop path leaves zero tasks, locks, pending rewards, or orphaned
    object reservations; the prior world mask/object identity is exact.
  - Concurrent players cannot double-consume a depleted resource or remove one
    another's rewards/sessions.
  - Legacy skilling on unregistered objects is unchanged.
  - The representative resource uses only the public builder and runtime
    definitions, with no content-specific Java branch.

### Step 9 (WP9): Add operator diagnostics and admin control

- **What**: Add immutable `ScriptRuntimeReport`/`ScriptReloadResult` snapshots
  and permission-gated `::scripts status`, `::scripts list [kind] [page]`, and
  `::scripts reload`. Inventory the existing `::scriptdir` developer command
  and replace its absolute-path response with a deprecated, rights-gated
  sanitized alias of `::scripts status`. Report active generation, last
  successful/failed load, module/definition counts and logical sources, active
  runtime/resource counts, quarantined activation failures, and bounded
  sanitized diagnostics.
- **Where**: `ScriptHost`, registry/runtime services, new
  `com.rs2.script.diagnostics`, and `Commands`.
- **Why**: The current `reload()` catches failures internally while
  `Commands` always prints “Scripts reloaded”; operators cannot inspect what
  is active or which module caused a rejected candidate.
- **Considerations**:
  - Keep the existing programmatic `load()`/`reload()` compatibility methods;
    add a result/report seam and make `::reload`/bare `::scripts` delegate to
    the truthful new behavior. WP1 already rejects content routes for
    `scripts`, `reload`, and `scriptdir` and has migrated command lookup/
    invocation to the active lease, so none can be shadowed.
  - Remove the call to `ScriptHost.resolveContentDir().getAbsolutePath()` from
    `Commands.developerCommands`. Authorized `::scriptdir` emits only a
    deprecation line plus the same bounded logical status snapshot as
    `::scripts status`; unauthorized callers receive the generic denial and no
    existence/source/path detail. It accepts no arguments and never returns a
    filesystem string, URI, stack trace, or process working directory.
  - Require administrator rights (`playerRights >= 2`) before revealing module
    sources or triggering reload. Denied commands expose no inventory/detail.
  - Sort output deterministically, page at at most 20 entries, cap every line
    and failure summary, and show logical module ids only—never host paths,
    stack dumps, registry objects, or guest values.
  - Inspection is read-only and must not take a lease that can execute guest
    code. A failed reload reports failure and leaves the previous report/state
    active.
- **Definition of Done**:
  - Status/list report exact active manifest and runtime counts with stable
    ordering and bounded pagination.
  - Reload success reports the new generation; reload failure reports the
    candidate source/error while proving the previous generation and gameplay
    remain live.
  - Unauthorized users cannot list sources or reload; legacy aliases are
    truthful and retain their transport behavior.
  - Production command tests prove `scriptdir` is reserved from guest/host
    content, rejects arguments, applies the same rights gate, contains no
    absolute content/workspace/home path for success or failure, and exposes
    only the bounded logical status/deprecation response.
  - Diagnostics contain no absolute path, raw `Value`, engine object,
    credential, or unrestricted exception text.
  - Production command-packet tests cover permission, parsing, status/list,
    successful reload, and rejected reload.

### Step 10 (WP10): Ship representative vertical content and migration documentation

- **What**: Migrate/refine the shipped content into one vertically integrated
  OSRS-style pack: a persisted multi-stage quest with generic journal, a
  gathering resource, an activated area/shop, and a phased boss with named
  drops. Retain a declarative raid consumer fixture. Update authoring,
  capability, migration, operations, and remaining-engine-boundary docs.
- **Where**: `content/src/loader.ts`, manifest and representative modules
  under `content/src/{quests,areas,bosses,raids,resources}`, compiled-loader
  E2E fixtures, `docs/SCRIPT_BRIDGE.md`, `docs/TYPESCRIPT.md`,
  `docs/ENGINE_BOUNDARY.md`, and new `docs/typescript-content-authoring.md` and
  `docs/typescript-content-migration.md`.
- **Why**: The phase is complete only when public definitions/builders affect
  real gameplay together and a content author can reproduce the pattern
  without reading Java internals.
- **Considerations**:
  - Reuse the accepted Dragon Awakens and Phase 4 encounter-warden production
    routes where practical, converting orchestration to canonical public
    builders while preserving their packet/save/death/pickup evidence.
  - The vertical E2E must cross compiled `content/dist/loader.js`, the manifest,
    real entry packets, game ticks, object/NPC death/pickup, save/load, journal
    button, reward transactions, successful/rejected reload, and cleanup.
  - The connected-client runbook pins observable checks for `::scripts status`
    and source listing plus sanitized/deprecated `::scriptdir`, scripted quest row/detail objective, gathering tool/
    level/depletion/respawn, activated area shop/object/NPC, standalone boss
    phase/private drop/pickup, and a two-client raid create/invite/join/start
    with embedded boss, rewards, reload, departure, and final cleanup.
  - Docs list every supported public capability, schema/version/source rule,
    result code, bounds, lifecycle/cleanup rule, admin command, migration from
    existing builders/direct imports/loot weights, and the few remaining
    Java-only engine boundaries.
  - Do not bulk-migrate unrelated legacy Java content; unregistered paths are
    compatibility assertions, not Phase 5 conversion work.
- **Definition of Done**:
  - Representative quest, gathering, area/shop, and boss content use only
    public TypeScript SDK APIs; no content-specific Java `if`/switch exists.
  - Every registered definition family has production gameplay-consumer
    evidence, including the raid runtime.
  - The full vertical flow survives save/load, accepted/rejected reload, death/
    logout, full inventory, and cleanup with exact state/resources.
  - Migration and authoring guides are sufficient to create a new module,
    register it in the manifest, inspect it, and diagnose a rejected reload.
  - The primary gate and the documented connected-client smoke runbook pass
    before Phase 5 is marked complete; if a live client is unavailable, record
    the exact environment limitation and maintainer acknowledgement rather
    than claiming the smoke passed.

## Testing Plan

| Test Type | What to Test | Expected Outcome |
|-----------|--------------|------------------|
| Manifest/schema | Versions, module source scope, strict members/bounds, loaded ids, duplicates, cross-references, deep copies, candidate rollback | Invalid content names its kind/key/source/path and leaves last-known-good state active. |
| Drop/reward transactions | Named resolution, item-name ambiguity, WP6 deterministic vectors, owner-neutral encounter/area RNG and delivery adapters, amount/weight/identity bounds, player-local and roster-wide reward preflight/rollback | Exact ground/RNG and roster items/derived player weight/XP/points/state/RNG commit together or not at all. |
| Boss runtime E2E | Real entry, spawn, phases, specials, death, named private drops, pickup, isolation, every close path | Definition drives production gameplay with zero leaked resources. |
| Area runtime integration | Activation/reload of NPCs, objects, shops, drops, lifecycle, conflicts, legacy coexistence | Exact generation projections replace/restore atomically. |
| Raid runtime integration | Party limits, ordered rooms, boss reference, completion/wipe/timeout, rewards, cleanup | Definition drives an isolated multi-room session. |
| Quest/journal/persistence | Objective state, row mapping, detail UI, legacy buttons, exhaustive parser/codec boundaries, real v0 `PlayerSave` migration/quarantine | Scripted objective drives UI and historical state safely migrates to v1. |
| SDK contract | Public barrel, builder validation/deep freeze, callback ownership/cancellation, runtime export inventory | Types match implemented consumers and expose no aspirational/raw host surface. |
| Gathering production E2E | Level/tool checks, tick animation, deterministic success, item/XP atomicity, depletion/respawn, concurrency, cancellation/reload | Public resource definition implements the complete gated loop. |
| Diagnostics commands | Rights, exact parsing, bounded status/list, truthful reload success/failure, reserved/sanitized `scriptdir` | Operators see accurate logical sources without leaking host/runtime objects or absolute paths. |
| Vertical content E2E | Compiled loader/manifest through packets, ticks, journal, save/load, death/drop/pickup, reload, cleanup | Representative OSRS-style content uses only the public SDK. |
| Compatibility/sandbox | All Phase 1-4 tests, unregistered legacy paths, no-script boot, Java 8 source, Java 17 runtime, host/filesystem/process/socket/thread bans | Low-level contracts and legacy fallback remain unchanged. |

### Required Executable Route Matrix

| Candidate/runtime case | Required assertion |
|------------------------|--------------------|
| Same guest route twice in one source | Candidate rejected; original and conflicting source/key reported. |
| Same guest route from two module sources | Candidate rejected with both logical sources. |
| Guest route versus Java host consumer, in either registration order | Candidate rejected; neither owner receives precedence. |
| Two host consumers claim one key | Candidate rejected before activation. |
| Guest or host claims command `scripts`, `reload`, or `scriptdir` | Candidate rejected as a reserved admin alias. |
| Invalid packet/command input with a present or absent route | Dropped before route, legacy behavior, or side effects according to the existing gate. |
| Valid exact guest/host route succeeds | Invoked once and consumed; legacy path is not called. |
| Valid exact guest/host route returns handled rejection | Consumed with its bounded result; legacy path is not called. |
| Valid exact guest callback or host consumer throws | Exception contained/reported once and route consumed; legacy path is not called. |
| Valid exact route absent | `UNMATCHED`; existing legacy behavior runs exactly once. |
| Command lookup paused while reload begins | Lookup and invocation finish under the old context/registry/generation; reload then publishes the replacement. |

### Required Activation Transaction Matrix

| Injection/state case | Required assertion |
|----------------------|--------------------|
| Candidate evaluation/prepare fails | No reservation or live mutation; old context/runtime/report remain selected. |
| Handoff reservation fails | No candidate projection is staged; old state and third-party owner remain exact. |
| Inactive candidate apply fails after one or more intents | Candidate intents reverse in LIFO order; predecessor was never retired. |
| Candidate verification fails | Same exact reverse result, including masks, NPC slots, shops, drops, routes, and report. |
| Predecessor retirement fails midway | Undo ledger restores every retired predecessor identity before candidate cleanup. |
| First undo attempt is injected to fail | Idempotent retry succeeds while handoff remains held; exact old state is selected before reload returns. Persistent failure is fatal/quarantined and is never reported as successful rollback. |
| Final pre-publication checkpoint fails with a mutating/throwing old `onUnload` configured | Checkpoint runs before the hook; predecessor is restored, candidate removed, old `ActiveState` remains, hook invocation/effect count is zero, and commit was not reached. |
| Same-footprint old/new area succeeds | Old remains visible through preparation; one no-throw commit selects new context/generation/runtime/projections/report with no absent or double-visible tick. |
| Third-party writer races same-footprint handoff | Writer is deferred/rejected by the handoff reservation and revalidated after commit/abort. |
| Final checkpoint passes, then old `onUnload` mutates guest-visible state and throws | Hook mutation occurs once, throw becomes the bounded unload result, and the no-throw candidate commit follows immediately/necessarily. No abort or rollback is attempted for the hook effect or publication. |
| New `onLoad` throws | Candidate remains published; failure appears in diagnostics and cleanup/ownership remains valid. |
| Final predecessor disposal/context close fails after commit | New state remains authoritative; exact retained cleanup identity is quarantined/retried and no rollback is claimed. |

### Required Area Drop Authority Matrix

| Death/object case | Required assertion |
|-------------------|--------------------|
| Exact generation-owned area NPC dies with exact live killer, private policy | Binding claims the allocation once; WP2 transaction advances area RNG and publishes exact killer-private identities together; legacy `dropItems` is suppressed. |
| Same `npcType` legacy NPC or stale/reused allocation dies | Area binding is `UNMATCHED`; the complete legacy drop/special-case path runs once and area RNG is unchanged. |
| Exact bound NPC dies with null, stale, wrong-plane, or otherwise ineligible killer | Claim is consumed as `NO_RECIPIENT`; no RNG/ground mutation and no legacy fallback/double drop. |
| Exact bound NPC uses public policy | Exact public tokens appear at the captured death tile, use no player-private alias, and remain tied to area source/owner cleanup. |
| Selection, allocation, staging, verification, final owner-version check, or contained callback fails | Every staged token is removed, visible ground and area RNG/version are exact, and claimed authority still suppresses legacy fallback. |
| Exact generation-owned area object/action is used | Resolver-derived owner route is authoritative; exact actor eligibility and private/public delivery commit once; success/rejection/throw is consumed. |
| Equal-id/action cache or legacy object is used | No owner-route key matches; validated guest/legacy behavior runs exactly once and area RNG is unchanged. |
| Rejected versus successful area reload with unclaimed drops | Rejection preserves binding/routes/RNG/tokens; success compare-removes old bindings/routes and only old exact unclaimed tokens, never equal legacy/public identities. |

### Required Raid Admission and Ownership Matrix

| Party/session case | Required assertion |
|--------------------|--------------------|
| Owner creates, invites, invitee explicitly joins | Exact identities recorded owner-first/join-FIFO; no encounter exists before start. |
| Join without invite, duplicate invite/join, replacement player identity, second lobby/session | Rejected without membership/resource mutation. |
| Below minimum, above maximum, wrong plane/muster area, non-owner start | Start rejected and lobby remains retryable. |
| Participant-add failure during start | Partial encounter closes; lobby/invites/roster remain exact and retryable. |
| Successful two-player start | One encounter token/reservation and one immutable two-player roster; no late joins/replacements. |
| Embedded boss room | WP3 controller borrows the raid handle and creates/closes no second encounter; standalone boss test remains unchanged. |
| Non-owner leave/logout/death | Member marked departed/removed, no replacement, pinned continuation policy applied. |
| Owner leave/logout/death, zero active, timeout, room/controller callback failure | `onWipe` once, no rewards, one idempotent cleanup. |
| Completion | Roster-wide reward transaction commits every frozen survivor or none; `onComplete` once only after award-id/RNG commit; zero lobby/invite/roster/encounter residue. |
| Rejected/successful reload | Rejected retains exact lobby/session/routes; successful reload closes old lobby/session before context close. |

### Required Raid Reward Atomicity Matrix

| Two-player reward case | Required assertion |
|------------------------|--------------------|
| Player two has full inventory or would exceed XP cap; either player has stale state version | Roster-wide preflight mutates nobody; local plan is discarded, all locks release, live raid RNG state/version and both exact weights remain unchanged, `onComplete` count is zero, and only the stable award transaction id remains for a fresh bounded retry. |
| Injected second-player mutation or postcondition failure after player one changed | Reverse restore returns both complete inventories, XP/current levels, quest points, script state/version, and exact old `player.weight`; captured reward-state versions remain unchanged, recalculation/rollback postconditions are verified, RNG owner state/version and award id remain uncommitted, and no presentation/callback runs. |
| Roster slot order differs from owner-first/join-FIFO order | Locks acquire coordinator, raid RNG owner, ascending exact live player identities, then subsystems; planning/mutation remain owner-first/join-FIFO with no deadlock or reward reordering. |
| Test seam advances the raid RNG owner/version after a retryable attempt releases locks and before the next tick | The discarded plan is never reused; the next attempt locks and clones the advanced live owner/version, producing a fresh plan without overwriting or double-advancing it. |
| Test seam changes RNG owner token/version or a player reward-state version after local planning but before final revalidation | Attempt discards its plan before the first player mutation, releases locks, consumes one bounded retry, and preserves the intervening owner/player state exactly. |
| Retry after capacity/state condition is repaired | New attempt re-clones/replans under the complete lock set and commits both players, exact recalculated weights, RNG state/version, player reward-state versions, and award id once; `onComplete` runs once and the raid closes. |
| Duplicate completion tick/command/cleanup retry or throwing `onComplete` | Award id makes the path a no-op: no duplicate item/XP/state/weight, RNG advance, presentation, or callback invocation. Hook effects/throw are post-commit and do not roll rewards back. |
| Exact participant departs or reward grace expires before commit | `onWipe` runs once, nobody receives any reward, uncommitted plan/RNG state is discarded, and cleanup leaves zero session residue. |

Primary verify command (the single acceptance command for every completed work
package and for the final phase):

```bash
./scripts/build.sh
```

This is the repository build contract: it compiles `content/` first and then
runs the complete client/server Maven reactor and test suite.

### Test Integrity Constraints

- Do not delete, disable, ignore, weaken, rename away, or replace an existing
  Phase 1-4 assertion with a helper-only test. New direct parser/service tests
  supplement production-path tests; they do not replace them.
- Keep `ScriptHostTest` candidate rollback/compiled-loader/sandbox coverage,
  `RegistryStoreTest` atomic state coverage, and all Phase 4 generation lease,
  encounter, collision, reward, lock, camera, NPC death, and pickup tests.
- Extend rather than replace `ScriptHostDispatchLeaseTest` and `CommandsTest`:
  the production `Commands.executeScriptCommand` path, not a direct registry
  helper, must prove the unified route lease, reserved aliases, host/guest
  authority, callback throw consumption, invocation metadata, and reload race.
- Keep `QuestDefinitionParserTest`, `QuestRegistryValidationTest`,
  `QuestServiceTest`, `QuestRewardTransactionTest`,
  `QuestCompletionConcurrencyTest`, and `DragonAwakensProductionE2ETest` as
  compatibility contracts. Journal assertions are added through the real
  button/interface path.
- Keep `ScriptStateCodecTest` and `PlayerScriptStatePersistenceTest` malformed,
  quarantine, save refusal, atomic replacement, and legacy quest-point checks.
  Historical migration must use a real character file and the production
  `PlayerSave` parser, not only a directly injected decoder.
- Definition-consumer E2Es must load compiled `content/dist/loader.js` through
  the manifest. Constructing a Java descriptor directly may test edge cases
  but cannot be the sole proof that a registry is consumed.
- Boss/raid/resource completion must cross real game-cycle scheduling and real
  NPC/object/ground/pickup paths. Do not mock `ScriptEncounterService`,
  `WorldObjectService`, `NpcHandler`, `ItemHandler`, or engine singleton arrays.
- Area drop tests must enter the real `NpcHandler` death critical section and
  real object packet/resolver path with bound and equal-id legacy identities.
  Direct `AreaDropAdapter` calls may supplement but cannot prove exact claim,
  legacy suppression/fallback, RNG-ground atomicity, or reload cleanup.
- Candidate failure tests must assert the complete previous state: context,
  generation, manifest, definition/callback registries, active runtimes,
  world projections, scheduled work, UI mapping, and diagnostics report.
- Activation tests use both a synthetic WP1 projection adapter and WP4's real
  NPC/object/shop/drop adapters. Do not mock away handoff reservations,
  predecessor undo, same-footprint masks, projection selection, or the atomic
  `ActiveState` commit line.
- Raid tests use two distinct live `PlayerHandler.players` identities and the
  production command route. Direct lobby/controller calls are supplementary;
  they cannot replace invite/join/start packets, sole-encounter assertions,
  departure policy, roster-wide lock/preflight/snapshot/rollback, injected
  second-player failure, exact weight restore/recalculation, stale raid-RNG
  owner/version rejection, fresh-plan retry, once-only commit, wipe/complete,
  and cleanup evidence.
- Every new guest-exposed Java method is reflected against
  `content/src/core/runtime.ts`, uses `@HostAccess.Export` only where intended,
  validates finite/integral/bounded numbers before narrowing, and has stale-
  generation and raw-host negative coverage.
- Representative content imports only public SDK modules. Tests may inspect
  package-private state but content may not depend on test globals, engine
  classes, filesystem paths, wall-clock time, or uncontrolled randomness.
- Run Maven tests serially. Preserve Java 8 source/target compatibility and
  execute with Java 17 through the repository build contract.

## Rollback Strategy

Rollback is package-boundary based. Do not partially revert a schema/parser
while leaving its active consumer or compiled TypeScript definitions in place.

1. Before removing a runtime package, use its Java-owned shutdown path and
   assert zero matching generation identities: encounters, NPCs, objects,
   shops, ground rewards, tasks, locks, cameras, area/raid/resource sessions,
   journal mappings, and deferred writers.
2. WP10 content/docs can roll back to the last accepted representative modules
   without changing the SDK. WP9 diagnostics can be removed while retaining
   internal immutable reports. WP8 resource registrations can be disabled as
   one kind while leaving area/object legacy fallback active.
3. WP7 compatibility adapters remain until all shipped content has migrated;
   removing an adapter is a separately reviewed breaking change, not rollback.
4. WP2-WP6 consumers can be disabled by definition kind only if registration
   then fails clearly rather than silently storing inert data. During a reload,
   restore the predecessor only for failure before the final checkpoint and
   before old `onUnload` starts. Once that hook is attempted, its guest-visible
   effects are not generally reversible and the no-throw candidate commit must
   follow immediately. After that assignment the candidate is authoritative:
   quarantine/retry retained predecessor cleanup and report degraded status,
   but do not claim that hook effects or context/world publication roll back.
5. WP1 rollback restores the pre-Phase-5 direct loader/registry behavior as one
   unit. Preserve all Phase 1-4 globals, accepted WP6/WP7 APIs, sandbox policy,
   no-script boot, and unregistered legacy behavior.
6. State migration is forward-safe: once a valid v0 character is loaded it is
   saved only as v1. Rollback must keep v0 read support or perform an explicit
   offline migration; it must never make already migrated character files
   unreadable or discard quarantined payloads.
7. A failed WP2 drop transaction removes only its invisible staged exact
   identities and leaves its explicit encounter/area RNG owner unchanged. A
   successful area reload removes only old generation-owned unclaimed drop
   identities; it does not rewind already committed pickups or legacy drops.
8. A failed WP5 roster reward attempt restores every player's locked inventory,
   XP/levels, exact old `player.weight`, points, and state, then verifies the
   captured reward-state version remains unchanged before releasing locks. It leaves live raid RNG state/version and award id
   uncommitted, discards the local plan, and replans under fresh locks on a
   bounded retry. After the once-only award id/RNG commit, items, recalculated
   weight, XP, state, RNG, and `onComplete` are forward-only; cleanup/callback
   failure cannot roll them back or make the award retryable.

## Open Decisions

All implementation-shaping decisions needed to execute this draft are resolved
subject to re-grounding after Phase 4 WP6/WP7 acceptance.

| Decision | Options | Chosen | Rationale |
|----------|---------|--------|-----------|
| Phase start gate | prepare early / wait for Phase 4 | Wait for accepted WP7 and Phase 4 completion | Matches the gated prerequisite and avoids building consumers on draft contracts. |
| Definition ownership | retain every guest object / common envelope then per-consumer copy | WP1 legacy compatibility records; each consumer atomically replaces its kind with Java-owned descriptors plus generation-owned callbacks | Keeps WP1 executable while still eliminating inert/raw definitions at the owning consumer boundary. |
| Source identity | infer stack/file path / explicit logical module scope | Explicit bounded manifest module id | Deterministic diagnostics without exposing the host filesystem. |
| Schema timing | freeze all schemas in WP1 / finalize with each consumer | WP1 common envelope; WP2-WP6 own strict schemas and shipped-fixture migration | Avoids provisional boss/area/raid contracts and preserves the current loader until its owner migrates it. |
| Candidate activation | publish then best-effort / two-phase handoff | Prepare, reserve, inactive apply, predecessor undo-retire, complete every checkpoint, then attempt old `onUnload` and immediately no-throw commit | No abort can unload the retained generation; any attempted hook necessarily publishes the candidate and neither guest hook effects nor publication are falsely called rollbackable. |
| Route conflicts | host precedence / guest precedence / reject duplicates | One exact guest-or-host record; every collision rejects | Keeps exact authority deterministic and source-diagnosable. |
| Admin aliases | content-shadowable / reserved | `scripts`, `reload`, and `scriptdir` reserved in WP1; WP9 sanitizes `scriptdir` into a rights-gated deprecated status alias | Prevents content shadowing and removes the existing absolute-path disclosure without silently retaining it. |
| Drop weights | floating/Infinity / WP6 integer model | WP6 integer weights plus explicit `always` | One deterministic runtime contract with bounded overflow. |
| Drop execution owner | encounter-only / owner-neutral transaction | Owner-neutral WP2 `DropTransaction` with explicit RNG owner and ground-delivery policy; encounter and area adapters retain their own identity/eligibility semantics | Lets area drops reuse the exact WP6 atomic algorithm without creating fake encounters or sharing RNG implicitly. |
| Area drop authority | NPC/object id-wide / exact owner identity | Exact spawn allocation or resolver-owned object projection/action; matched consumes and suppresses legacy, unmatched equal-id content falls back | Prevents inert bindings, double drops, and clobbering unrelated legacy NPCs/objects. |
| Definition references | nested copies / stable ids | Stable ids in canonical descriptors | One owner/source, deterministic duplicate/cycle validation, and reusable definitions. |
| Boss ownership | controller always owns encounter / controller borrows | Encounter-agnostic controller plus standalone owning adapter | Lets a raid embed a boss while retaining one player encounter membership/reservation. |
| Boss/raid callbacks | rich domain `Player` / narrow runtime context | Narrow generation-owned wrappers/handles | Matches the sandbox and accepted runtime boundary. |
| Raid party | implicit nearby players / explicit lobby consent | Owner create/invite, invitee join, owner start; immutable roster and no late joins | Reconciles `minPlayers=2` with exact player identity and deterministic session ownership. |
| Raid reward failure | sequential player commits / durable claims / roster-wide atomic | One bounded attempt locks coordinator then versioned raid RNG then players/subsystems, plans locally, verifies/restores weight and full snapshots, discards on retry, and commits RNG plus award id once | A later player failure cannot partially mutate weight/state, and no stale local plan can overwrite an intervening RNG advance. |
| Shops | name-only/static hack / typed scripted definition | Separate scripted shop ids plus retained numeric static shops | Consumes area shop definitions without weakening static-shop provenance. |
| Quest journal | client/cache rewrite / reuse bounded legacy slots | Deterministic unused-row mapping plus generic interface 8134 | Produces a real UI with server changes only and preserves legacy quests. |
| Historical state | test-only injected decoder / built-in v0 migration | Strict flat-entry v0 decoder, v1 write-only | Exercises the real `PlayerSave` migration boundary and remains deterministic. |
| Gathering randomness | guest `Math.random` / Java-owned deterministic RNG | WP6 algorithm in a resource-session owner | Replayable, bounded, and does not misuse exclusive encounters. |
| Builder timing | design before consumers / stabilize after consumers | Derive from WP2-WP6 then freeze in WP7 | Prevents exporting fields with no gameplay meaning. |
| Admin detail | raw exceptions/paths / sanitized logical reports | Rights-gated bounded logical source reports | Useful operations without sandbox or filesystem disclosure. |

## Reality Check

### Code Anchors Used

| File | Symbol/Area | Why it matters |
|------|-------------|----------------|
| `ScriptHost.java` | `replaceContext`, `ActiveState`, `runPostCommit` | It assigns the new context/registry/generation before fallible cleanup and swallows post-commit failures; WP1 must move fallible runtime work before a no-throw publication line. |
| `ScriptBindings.java` | `install` | All globals are explicit; new definition/module functions must preserve this boundary. |
| `ScriptFunctions.java` | `getDefineBoss`, `getDefineRaid`, `getDefineArea`, `getDefineQuest` | Boss/raid/area validate only identity and store raw `Value`; quest already uses a strict Java parser. |
| `RegistryStore.java` | `State`, `freeze` | One staging snapshot exists and is the correct atomic home for descriptors, callbacks, manifest, and diagnostics metadata. |
| `CommandHandlerRegistry.java`, `Commands.java` | `get`, `executeScriptCommand` | Command lookup uses `readActiveRegistry` and invokes later, outside `dispatchActive`; content can currently shadow admin switch names. |
| `ObjectHandlerRegistry.java`, `InteractionHandlerRegistry.java` | independent raw `Value` maps | Later host consumers need the same exact keys and cannot safely rely on unspecified precedence beside these guest maps. |
| `ScriptInteractionGate.java` | validation and action-lock helpers | Host routes must preserve universal validate/lock/exact-authority/fallback order rather than add a parallel dispatch shortcut. |
| `ScriptHostDispatchLeaseTest.java`, `CommandsTest.java` | lease and command tests | Existing tests prove the lease primitive and current authority but do not run command lookup/invocation together across reload. |
| `BossRegistry.java`, `RaidRegistry.java`, `AreaRegistry.java` | `put/get/all` | These registries are data-only and expose active guest values; no gameplay consumer exists. |
| `QuestDefinitionParser.java` | `parse`, `only`, bounded readers | Proven pattern for strict one-way guest-to-Java descriptor conversion. |
| `QuestService.java`, `ScriptedQuest.java` | transitions and exported reads | Quest definitions are consumed for gameplay, but there is no current-objective method or journal projection. |
| `ScriptStateCodec.java` | constructor decoder map, `decodeV1` | Migration dispatch exists, but only v1 is built in and `PlayerSave` always constructs the default codec. |
| `PlayerSave.java` | `installScriptState`, `saveGame` | Real quarantine and atomic replacement must remain the historical-migration acceptance boundary. |
| `QuestAssistant.java` | `sendStages`, `Quests`, `questButtons` | The legacy quest tab has many disabled rows and a generic interface path but knows nothing about scripted definitions. |
| `ClickingButtons.java` | script dispatch before `QuestAssistant.questButtons` | Exact scripted button authority must remain ahead of generic journal and legacy behavior. |
| `Commands.java` | `reload`/`scripts`/`scriptdir` cases | Reload can report false success; `scriptdir` directly emits `resolveContentDir().getAbsolutePath()`, violating the no-host-path diagnostic boundary. |
| `content/src/loader.ts` | direct side-effect imports | Load order is deterministic but there is no explicit module inventory/source scope. |
| `content/src/core/drop-tables.ts` | `DropWeights`, `always`, `veryRare` | Current `Infinity` and fractional weights conflict with Phase 4 WP6's exact integer/always parser. |
| `content/src/core/boss.ts` | `BossContext`, `BossDefinition` | Context uses the rich domain `Player` and hooks are explicitly documented as inert. |
| `content/src/core/raid.ts`, `raids/raid-builder.ts` | `RoomContext` and callbacks | Rich contexts and room results are author models, not safe executable bridge contracts. |
| `content/src/areas/types.ts` | nested NPC/object/shop/quest/boss/raid arrays | Broad area data exists, but none is activated and nested definitions obscure ownership/source. |
| `content/src/quests/dragon-awakens.ts` | login objective message | Production quest behavior exists; ad-hoc progress messaging demonstrates the missing generic objective UI. |
| `content/src/core/runtime.ts` | `ScriptEncounterHandle` | Accepted WP5 currently lacks `nextInt`, `chance`, and `rollDrops`; those arrive only with Phase 4 WP6. |
| Phase 4 WP6 `ScriptDropTransaction`/`ScriptEncounterRng` contract | `rollDrops`, local RNG snapshot, ground staging | The accepted algorithm is encounter-owned; WP2 must extract it behind explicit RNG/delivery owners before a non-encounter area can use it. |
| `NpcHandler.java`, `ScriptNpcService.java` | death critical section, `isExactOwned`, `dropItems` | Only exact encounter-owned NPCs currently suppress legacy drops; ordinary/equal-id area NPCs otherwise use legacy id/name RNG and ground creation. |
| `QuestRewardTransaction.java`, `Weight.java` | player-local reward and derived weight | The current transaction snapshots exact `player.weight`, recalculates it from inventory/equipment after item mutation, verifies it, restores the old value on failure, then refreshes presentation best-effort; WP5 must preserve that state/order across a roster. |
| `npc.json` plus shipped boss/area/raid fixtures | ids `12001`, `5001`, `7001`, `7002`, `7003` | These aspirational ids are not loaded definitions, so WP1 strict validation would break the compiled loader; WP3-WP5 must migrate them with final schemas. |
| `DeprecatedItems.java` | `getItemId` | It returns the first case-insensitive name match, so WP2 needs ambiguity-detecting candidate-time item resolution. |
| `phase-4-impl.md` | WP6/WP7 status and normative contracts | WP6/WP7 are pending; all Phase 5 execution remains blocked even though this plan is ready to review. |

### Mismatches / Notes

- Phase 4 is not complete: WP6 deterministic RNG/drop transaction and WP7
  production boss are still pending. Therefore this implementation plan must
  remain `draft`, every Phase 5 WP remains blocked, and none of the proposed
  source files/classes should be created yet.
- WP1 cannot strictly parse final boss/area/raid schemas because those
  contracts and their safe callback contexts belong to WP3-WP5. The current
  compiled loader also contains unloaded custom NPC ids and inline references.
  WP1 therefore records legacy payloads explicitly and each consumer performs
  its own atomic schema plus fixture migration.
- The gated Phase 5 scope says boss, raid, area, quest, drop, and reward
  definitions are consumed. Current code has no drop/reward definition globals
  and only quest definitions are Java-owned/gameplay-active. WP1/WP2 make that
  missing boundary explicit rather than pretending current loot builders are a
  registry.
- Existing `ItemId = number | string` author types over-promise Phase 4's
  numeric drop API. Phase 5 resolves strings once at candidate load and stores
  numeric ids; ambiguous names are errors, not runtime guesses.
- Current boss/raid callbacks use the rich declarative `Player` interface,
  which is not the Graal runtime wrapper and cannot be consumed safely. WP3/
  WP5 replace executable contexts while preserving data compatibility through
  versioned adapters.
- Current area shops are full string-id/item-list definitions, while accepted
  WP5 exposes only numeric static shops. Phase 5 must add a separately typed,
  Java-owned scripted-shop consumer; treating any named shop as static would
  violate the accepted provenance contract.
- Area world activation is not part of `ScriptHost`'s current atomic swap.
  `replaceContext` publishes before post-commit cleanup and logs/swallow failures.
  Publishing descriptors and then best-effort spawning would violate
  last-known-good semantics, so WP1 refactors publication into the specified
  two-phase handoff and WP4 proves it over real same-footprint projections.
  Every abort point precedes old `onUnload`; after that guest hook is attempted,
  commit follows immediately because arbitrary guest-visible hook effects
  cannot honestly be rolled back. Final cleanup remains degraded/forward-only.
- Phase 4 WP6's drop transaction is attached to encounter RNG, participant,
  arena, and ground budget. Ordinary area NPC death instead calls legacy
  `NpcHandler.dropItems`. WP2 therefore extracts an explicit owner-neutral
  transaction, and WP4 claims only exact spawn allocations/resolved object
  projections; equal-id legacy identities remain unmatched.
- The existing reward transaction is player-local and treats derived
  `player.weight` as atomic state: inventory mutation is followed by weight
  recalculation/verification, while failure restores the exact old weight.
  It cannot make a raid-wide sequential loop atomic. WP5 therefore adds a
  lock-ordered coordinator that includes the versioned raid-session RNG owner,
  complete player/weight snapshots, fresh per-attempt local planning, reverse
  rollback, and a joint once-only RNG/award-id boundary before completion.
- `Commands.executeScriptCommand` currently has a real lookup/invocation lease
  gap and runs before Java admin switches. WP1 must migrate it to the unified
  route registry and reserve `scripts`/`reload`/`scriptdir`; WP9 cannot safely
  defer that authority fix. WP9 must also remove `scriptdir`'s current absolute
  path emission, not merely sanitize the new status/list commands.
- A standalone boss that always creates its own encounter cannot be embedded
  in a raid because each player may own/join only one encounter and overlapping
  reservations reject. WP3 therefore separates borrowed `BossController` from
  its standalone owning adapter, and WP5 supplies the sole raid handle.
- There is no existing raid lobby/party service. WP5 explicitly introduces
  owner invitation plus invitee opt-in, immutable owner-first/join-FIFO roster,
  no late join/replacement, and pinned departure/wipe/reward behavior; the
  two-player temple fixture is not treated as a solo encounter.
- The legacy quest tab is fixed-component UI. A bounded deterministic mapping
  over validated unused rows is required; an unbounded dynamic list would need
  client/cache work outside the chosen server-side approach.
- No TypeScript unit-test runner is currently part of the build contract.
  Builder behavior must be exercised through compiled-loader Java integration
  fixtures unless a runner is added without replacing the single primary
  acceptance gate defined above.
- Phase 5 does not bulk-migrate legacy Java content. Exact unregistered packet,
  object, NPC, shop, quest, skill, and drop behavior remains a required
  compatibility fallback throughout all ten work packages.
