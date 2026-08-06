---
type: planning
entity: plan
plan: "typescript-content-platform"
status: completed
created: "2026-07-29"
updated: "2026-08-06"
---

# Plan: TypeScript Content Platform

## Objective

Turn the hardened GraalJS bridge into the primary content-authoring platform so quests, NPCs, items, objects, skills, encounters, areas, and OSRS-style content can be implemented in TypeScript with Java changes limited to new low-level engine capabilities.

## Motivation

The bridge currently handles commands, NPC clicks, object clicks, and dialogue, while most gameplay paths and mutable player/world services remain Java-only. A stable, typed, reload-safe content API is needed before substantial game content can move out of the engine.

## Requirements

### Functional

- [x] Cover the main item interaction routes with authoritative TypeScript handlers.
- [x] Provide rich command invocation data and safe player primitives for normal content.
- [x] Provide reload-safe scheduling and lifecycle events.
- [x] Provide namespaced persistent player state and a functional quest runtime.
- [x] Provide bounded world, NPC, object, combat, interface, shop, and reward services.
- [x] Turn declarative boss, raid, area, and quest definitions into consumed runtime systems.
- [x] Supply reusable TypeScript builders and representative OSRS-style content.

### Non-Functional

- [x] Keep guest code sandboxed behind explicit wrappers; never expose raw engine objects.
- [x] Preserve last-known-good transactional reload semantics across all registrations and callbacks.
- [x] Make scripted handlers authoritative only when an exact registration exists.
- [x] Validate registration arguments and fail content loading clearly on invalid definitions or duplicates.
- [x] Keep Java 8 source compatibility and run the server/tests on Java 17.
- [x] Maintain strict TypeScript declarations matching the real runtime surface.
- [x] Keep no-script and unregistered-content behavior compatible with the legacy engine.

## Scope

### In Scope

- Runtime registrations, bridge wrappers, dispatch hooks, persistence adapters, schedulers, lifecycle events, content builders, examples, tests, diagnostics, and bridge documentation.
- Incremental migration seams that let Java content coexist with TypeScript content.

### Out of Scope

- Exposing arbitrary Java classes or raw `Player`, `Npc`, packet, filesystem, network, process, or thread access.
- Rewriting networking, pathfinding, cache loading, or the core game loop in TypeScript.
- Migrating every existing Java content feature before the platform APIs are stable.

## Definition of Done

- [x] A content author can build a multi-stage quest with dialogue, items, NPC/object interactions, requirements, rewards, persisted progress, and scheduled actions without editing Java.
- [x] A content author can build a bounded combat encounter/boss with spawns, phases, drops, animations, graphics, sounds, and lifecycle hooks without editing Java.
- [x] Main client interaction paths have typed, validated, authoritative TypeScript endpoints.
- [x] Every callback and scheduled task is contained, observable, and invalidated safely on reload/logout.
- [x] Runtime and TypeScript contracts are documented and verified by Java tests plus a content build.
- [x] Existing legacy content continues to work for unregistered routes.

## Testing Strategy

- [ ] Unit-test every registry, validation rule, wrapper mutation, and callback exception boundary.
- [ ] Packet/dispatch tests prove exact scripted routes consume legacy behavior while unmatched routes fall through.
- [ ] Reload tests prove candidate isolation, last-known-good behavior, and stale callback/task cancellation.
- [ ] Build/typecheck representative TypeScript content using every public API.
- [ ] Run `pnpm build:content` and the complete engine Maven test suite after each phase.
- [ ] Perform a live client/server smoke test for each user-visible phase when the launcher environment is available.

## Phases

| Phase | Title | Scope | Status |
|-------|-------|-------|--------|
| 1 | Interaction and Player Foundation | [Detail](phases/phase-1.md) | completed |
| 2 | Scheduling and Lifecycle | [Detail](phases/phase-2.md) | completed |
| 3 | Persistent State and Quests | [Detail](phases/phase-3.md) | completed |
| 4 | World and Encounter Services | [Detail](phases/phase-4.md) | completed |
| 5 | Declarative Runtimes and OSRS Content Kit | [Detail](phases/phase-5.md) | completed |

## Risks & Open Questions

| Risk/Question | Impact | Mitigation/Answer |
|---------------|--------|-------------------|
| Legacy packet handlers mix validation, event posting, and behavior. | Scripts could double-run or bypass safety checks. | Hook only after packet validation and return before legacy event/behavior for exact registrations. |
| Graal `Value` callbacks become invalid after context close. | Delayed or player-held callbacks could execute stale guest code. | Associate asynchronous work with a context generation and cancel/ignore it during reload/logout. |
| Existing save format has fixed quest fields. | Arbitrary content state cannot persist safely. | Add a versioned, namespaced script-state payload with size/type limits and migration support. |
| Broad wrappers can become a sandbox escape or unstable API. | Security and content compatibility regressions. | Export capability-specific facades, validate values, and keep raw engine types private. |
| The legacy engine has inconsistent success/return contracts. | TypeScript APIs may promise more than Java can guarantee. | Add adapter-level result contracts and tests instead of directly mirroring weak legacy methods. |

## Changelog

### 2026-08-06

- Phase 5 WP10 accepted by the delegate review
  (`reviews/impl-review-phase-5-wp10.md`; 0 Critical/0 Major, 3 Notes; the
  barrel-import and doc-phrasing Notes fixed, the harmless SCRIPT_BRIDGE
  phrasing accepted as-is). The compiled `content/dist` loader registers all
  eight production fixtures as source-aware content modules through the
  public SDK barrel, the quest dialogue uses the SDK helpers, and the vertical
  `VerticalContentE2ETest` loads the compiled loader and crosses the
  manifest, area, shop, gathering, boss, quest save/load, rejected reload,
  and successful reload in one flow. New authoring and migration guides
  (`docs/typescript-content-authoring.md`,
  `docs/typescript-content-migration.md`) plus updates to
  `docs/SCRIPT_BRIDGE.md`, `docs/TYPESCRIPT.md`, `docs/ENGINE_BOUNDARY.md`,
  and `docs/README.md` complete the documentation surface. Gate: TypeScript
  clean, SDK tests 72/72, full server module 495/495 green,
  `./scripts/build.sh` BUILD SUCCESS. **Phase 5 is complete.**

### 2026-08-05 (post-review)

- Phase 5 WP9 accepted by the independent delegate review
  (`reviews/impl-review-phase-5-wp9.md`; 0 Critical, 0 Major, 2 Minor, 3
  Notes; both Minors and one Note fixed and re-verified). The Minors fixed:
  `::scripts list drop` now resolves through `parseKind` (the `DefinitionKind`
  enum name is `DROP_TABLE`, so the documented `drop` alias previously
  reported "Unknown definition kind"), and `ScriptRuntimeStatus` documents
  that the runtime counts are near-instantaneous across the singleton
  monitors rather than a single atomic instant. The Note fixed: the
  `scriptdir` test now forces a failed reload and asserts the subsequent
  output stays bounded logical status + "Last reload failed" with no absolute
  path. Post-fix gate: TypeScript clean, SDK 72/72, full server module
  494/494, `./scripts/build.sh` BUILD SUCCESS. WP10 (representative vertical
  content and migration docs) is next.

### 2026-08-05

- Phase 5 WP9 implemented with the primary gate passing (TypeScript clean,
  72/72 SDK tests, full server module 494/494 green, `./scripts/build.sh`
  BUILD SUCCESS): the new `com.rs2.script.diagnostics` package adds immutable
  `ScriptRuntimeStatus`/`ScriptReloadResult` snapshots and the
  permission-gated `::scripts status`, `::scripts list [kind] [page]`,
  `::scripts reload`, `::reload`, and deprecated sanitized `::scriptdir`
  admin commands. The legacy absolute-path `scriptdir` response is removed;
  inspection is read-only, output is bounded/logical-only, and a failed
  reload reports the candidate error while proving the previous generation
  stays live. Docs updated (`docs/SCRIPT_BRIDGE.md`,
  `docs/TYPESCRIPT.md`). WP10 (representative vertical content and migration
  docs) is next.

### 2026-08-04 (post-review)

- Phase 5 WP8 accepted by the independent delegate review
  (`reviews/impl-review-phase-5-wp8.md`). First pass **Rejected** with 1
  Major, 3 Minor, and 2 Note findings; all fixed and re-verified as Fixed;
  final verdict Accepted (0 Critical, 0 Major). **Major**: the legacy
  `Woodcutting.startWoodcutting` pre-dispatch in
  `ClickObject.completeObjectClick` ran before the WP8 host route dispatch
  and was not gated on route absence, so a registered tree (1276/1281, both
  legacy) double-consumed in the real packet path. Fixed by making
  `ClickObject.isScriptedClick` cover Java host routes too (unified route
  record lookup, not guest-only) and gating the legacy pre-dispatch on
  `!isScriptedClick(objectId, "first")`; a `completeObjectClick` E2E now
  proves a registered tree consumes the route without starting legacy
  woodcutting, and legacy woodcutting still runs for an unregistered tree.
  **Minor**: (a) `tickSession` now revalidates the live world-object identity
  each tick and closes the session when the tile no longer carries the
  resource; (b) `commitReward` returns a distinct result (tool-missing /
  inventory-full / XP-cap / failed) with accurate per-reason messages; (c)
  new E2E tests cover inventory-full rollback (exact items/amounts/weight/
  XP/level restore), immediate death cancel (wired into `beginPlayerDeath`),
  object-replacement cancel, and scheduler failure cancel. **Notes**: death
  now cancels immediately via `beginPlayerDeath`; the E2E uses a synthetic
  loader (compiled-fixture consumption proven separately by
  `ScriptHostTest`). Post-fix gate: TypeScript clean, SDK 72/72, full server
  module **485/485** green (gathering E2E 12/12, parser 6/6),
  `./scripts/build.sh` BUILD SUCCESS. WP9 (operator diagnostics) is next.

### 2026-08-04

- Phase 5 WP8 implemented with the primary gate passing (TypeScript clean,
  72/72 SDK tests — new `sdk/gathering.ts` suite 7/7, full server module
  480/480 green, `./scripts/build.sh` BUILD SUCCESS). The new
  `defineGatheringResource` schema-v1 global registers an immutable
  Java-owned `GatheringResourceDefinition` (canonical object id/action,
  skill/level, ordered tool alternatives with inventory-or-equipped policy,
  animation/tick interval, exact deterministic success chance, bounded item
  rewards plus one experience grant, depleted object id, respawn ticks). The
  `ScriptResourceRuntime` owns the exact host object route, validates
  skill/tools/live identity, opens a bounded per-player session driven by a
  repeating game-cycle task, rolls success on the Java-owned
  `ResourceSessionRng` (WP6 SplitMix64 with a per-session owner token — no
  fake encounter), commits item+XP atomically with exact weight/XP restore,
  depletes the object through the timed-object path, and restores it after
  respawn; every stop path (harvest, movement-away, logout, death, object
  replacement, reload, failure) cancels the session with zero residue. The
  public `sdk/gathering.ts` builder deep-freezes and validates the exact
  parser bounds (a GraalJS-safe UTF-8 byte counter replaces `TextEncoder`,
  which the embedded runtime does not expose). The compiled
  `content/src/resources/woodcutting.ts` fixture (regular tree 1276 and oak
  tree 1281) is the production proof. Test coverage: 6 parser-negative
  bounds/duplicate/chance tests and 7 production-path E2E tests
  (harvest/deplete/respawn, tool/level rejection, movement-away, logout,
  depleted re-click, rejected-vs-successful reload, always-miss RNG).
  WP9 (operator diagnostics) is next.

### 2026-08-03

- Phase 5 WP7 completed and accepted by the delegate review
  (`reviews/impl-review-phase-5-wp7.md`; first pass Rejected with 1
  Major/3 Minor/1 Note, all fixed and re-verified; final 0 Critical/0
  Major). The public TypeScript content SDK is stabilized. New `content/src/sdk/` package with one public
  barrel (`sdk/index.ts`, re-exported by `content/src/index.ts` and
  `core/index.ts`): pure requirement predicates/combinators, reward
  helpers over the named transactional consumer, explicitly typed
  scripted/static shop references, equipment helpers over the 11 runtime
  slots with legacy-name migration errors, bounded dialogue helpers plus
  the cutscene session engine owning every task/lock/camera handle
  (cancels on step failure, final one-shot task, or explicit cancel;
  logout/reload handles are engine-invalidated contained no-ops), and a
  shared canonical skill table. Existing builders aligned and
  deep-frozen (boss phases/specials/entry, raid rooms/rewards, area
  arrays); `createDropTable` validates, deep-freezes, and registers; the
  fluent `DropTableBuilder` emits canonical integral weights only; the
  legacy `Infinity`/`0.25`/`veryRare` forms fail with migration
  messages; the inert `LootTable`/`createLootTable`/`mergeTables`/
  `analyseTable`/`DropWeights` surface removed; boss/raid builders now
  reject the reserved `scripts`/`reload`/`scriptdir` aliases and
  duplicate raid reward references like the Java parsers. Examples
  import only from the SDK barrel; the Lumbridge man dialogue uses the
  SDK helpers. Generated `docs/API_INVENTORY.md` (regenerated by
  `node content/scripts/api-inventory.mjs`) matches runtime globals and
  barrel exports, verified by 62 Node built-in runner tests
  (`pnpm --filter @singlescape/content test`: deep-freeze/mutation,
  invalid bounds, duplicates, missing references, stale handles,
  cancellation, migration errors). The fix loop added the
  one-shot-gated cutscene completion with a regression test, the
  canonical-only SDK barrel type surface (rich `Player` types stay off
  the SDK surface), the `sys.`-prefix reward rejection, and the raw
  raid-room deep freeze. Gate: TypeScript clean, SDK tests 65/65, full
  server module 467/467 green, `./scripts/build.sh` BUILD SUCCESS. WP8
  (gathering/resource-loop runtime) is next.

- Phase 5 WP6 implemented with the primary gate passing (TypeScript clean,
  new suites 35/35 — codec boundary 11, parser boundary 11, journal 8,
  quest service +2, persistence +3 — quest/state/lifecycle focused gate
  102/102, full server module 467/467 green, `./scripts/build.sh` BUILD
  SUCCESS): `QuestService.objective()`/`ScriptedQuest.objective()` expose
  the current scripted objective (null not-started/missing stage, stage
  text in progress, stable completion summary); the new
  `ScriptQuestJournalService` maps sorted scripted quest ids onto the
  bounded pool of unimplemented legacy quest-tab rows (candidates beyond
  89 usable rows are rejected), renders colored rows on login, quest
  transitions, and successful reload, and opens generic interface 8134
  (name, summary, requirements, state, objective) for mapped buttons
  while exact scripted `onButton` authority and every legacy quest
  name/button/detail stay unchanged; Dragon Awakens now uses the generic
  objective projection instead of its login progress workaround; the
  built-in historical `v0` state decoder migrates the flat
  u16-entry-count body through real `PlayerSave` load with legacy-field
  preservation and atomic v1 re-save, while malformed v0 is quarantined
  and unsavable; table-driven `-1/exact/+1` boundary matrices cover every
  quest parser and state codec bound plus unknown members/types,
  duplicates, truncation, trailing data, malformed UTF-8/Base64URL, and
  overflow. Accepted by the delegate review
  (`reviews/impl-review-phase-5-wp6.md`, 0 Critical/0 Major); the docs
  Minor was resolved in the same pass (bridge docs now cover
  `objective()`, the v0 decoder, and the quest journal), and the
  refresh-coupling Minor stays tracked as a non-blocking robustness
  follow-up. WP7 is next.

- Phase 5 WP5 completed and accepted by the delegate review (first pass:
  2 Major, 4 Minor, 4 Notes; all fixed and re-verified; review record
  `reviews/impl-review-phase-5-wp5.md`). Fixed: identity-set `departed`
  accounting with membership removal on passive departure (double-death/
  death-then-logout keeps the raid running), owner-pinned lobby on
  non-owner departure, owner-aware lobby capacity (`maxPlayers - 1`
  opt-ins), roster-order mutation with slot-ascending locks, room
  callback throws wiping the raid, pre-mutation RNG-owner revalidation
  returning RETRYABLE, boss poll cancellation at terminal, the leave
  message, and the fixture wipe message. The bridge-docs refresh note
  stays tracked for WP10. Post-fix gate: TypeScript clean, new suites
  32/32 (parser 8, runtime 18, reward 8, E2E 1), focused gate 125/125,
  full server module 431/431 green, `./scripts/build.sh` BUILD SUCCESS.
  WP6 (quest journal, generic objective UI, and historical state
  migration) is next.

### 2026-08-02

- Phase 5 WP5 implemented with the primary gate passing (TypeScript clean,
  new suites 32/32 — raid parser 8, raid runtime 15, roster reward 8,
  production E2E 1 — full server module 428/428 green, `./scripts/build.sh`
  BUILD SUCCESS): the strict schema-v1 raid parser/registry with exact
  command/bounds/muster/entrance/limits, non-overlapping rooms, and
  candidate-scoped boss/reward/drop-table references; the lobby/session
  runtime proving the create/invite/join/leave/start contract, the frozen
  owner-first/join-FIFO roster, exactly one encounter per started raid,
  embedded `BossController`s borrowing the sole handle, ordered room
  advancement, the reward barrier with the once-only award id and bounded
  grace, the raid-session RNG owner, and the completion reward-table roll
  as private ground deliveries; the roster-wide `RosterRewardTransaction`
  with the global coordinator, ascending player locks, exact snapshots and
  weight preflight, fresh per-attempt plans, reverse rollback, and the
  joint once-only commit; the compiled `temple-of-zaros` fixture migrated
  to canonical schema-v1 with two distinct live players and the loaded
  dragon-king boss; and the lifecycle/reload/cleanup seams. The independent
  delegate review is pending
  (`reviews/impl-review-prompt-phase-5-wp5.md`); WP6 remains blocked until
  that review accepts the WP5 boundary.

### 2026-08-02

- Phase 5 WP4 completed and accepted by the delegate review (0 Critical/0
  Major/1 Minor/3 Notes; review record `reviews/impl-review-phase-5-wp4.md`).
  The Minor corrected the stale gate record: the actual observed gates are
  TypeScript clean, new suites 50/50, focused 93/93, full server module
  386/386 green (the earlier "372 tests with user WIP commit 460230b
  failures" was stale — 460230b does not exist; the cycle-event fix is
  commit 69457652), and `./scripts/build.sh` BUILD SUCCESS. Notes tracked:
  the bridge-docs refresh is owned by WP10; the deliberate legacy-shop
  price changes (`ShopAssistant` multipliers for shops 190/220/226,
  covered by `ShopAssistantPriceTest`), the `ShopHandler.closePlayerShop`
  rewrite, and the first-ever boot `shopHandler.loadShops()` call
  (`GameEngine.java:208`) are deliberate tested boot/price changes
  recorded in the WP4 boundary. WP5 (declarative raid runtime) is next.

- Phase 5 WP4 implemented with the primary gate passing: strict schema-v1
  area/shop parsers and typed registries, the production
  `ScriptAreaRuntime` projection adapter proving prepare/reserve/shadow/
  verify/retire/checkpoint/commit through real reloads with exact abort
  restoration at every injected stage, same-footprint handoff, and
  generation cleanup; exact allocation-bound NPC death claims through the
  real NpcHandler critical section (killer-private/public WP2 ground
  identities, handled NO_RECIPIENT, equal-id legacy fallback, respawn,
  private-TTL expiry, reload cleanup); exact tile-position object-drop
  routes with one-shot claims; scripted shops with declared stock/prices/
  restock through the production ShopAssistant path and exact
  allocation-bound opening routes; and the compiled `dragon-island`
  fixture migrated to canonical schema-v1 on the real Crandor map region
  (11414/11415) with loaded definition-backed ids (the former custom
  6950-7100 island has no cache map data and cannot carry layered object
  projections). Gate: TypeScript, new suites 33/33, full server module
  372 tests (only pre-existing unrelated `CycleEventHandlerTest` failures
  from user WIP commit 460230b), `./scripts/build.sh` clean reactor.
  The independent delegate review is pending
  (`reviews/impl-review-prompt-phase-5-wp4.md`); WP5 remains blocked until
  that review accepts the WP4 boundary.

- Phase 5 WP3 completed and accepted by the delegate review (0 Critical/0
  Major/4 Minor, all fixed): strict schema-v1 boss parser/registry,
  encounter-agnostic `BossController` (borrowed handle, ordered phases,
  armed special cadence, named WP2 drops on death, contained callbacks),
  standalone owning adapter with exact WP1 host routes, finalized
  `HostRoute` invocation shape, Java-owned scheduler/death/drop seams, the
  narrow `BossRuntimeContext` matching the TypeScript surface under
  `HostAccess.EXPLICIT`, and the atomic `dragon-king` (loaded npc 54) /
  `encounter-warden` (named `encounter_warden_loot`) fixture migration with
  the converted WP7 E2E. Post-fix gate: TypeScript, new suites 24/24, E2E
  6/6, full server module 313/313, `./scripts/build.sh` clean reactor.
  Review record: `reviews/impl-review-phase-5-wp3.md`.

### 2026-08-02

- Phase 5 WP2 completed and accepted by the delegate review (0 Critical/0
  Major/4 Minor, all fixed): `defineDropTable`/`defineReward` schema-v1
  parsers with exact item-name resolution, owner-neutral `DropTransaction`
  over explicit RNG/ground owners (encounter adapter preserves the WP6
  vectors and seams), shared `PlayerRewardTransaction` with the per-player
  reward-state owner and quest completion as a thin adapter, canonical
  migration of the shipped loot fixtures with compiled-loader assertions.
  Gate: focused 52/52, server module 281/281, `./scripts/build.sh` clean
  reactor. Review record: `reviews/impl-review-phase-5-wp2.md`.

### 2026-08-02

- Phase 5 WP1 implemented with the primary gate passing (TypeScript compile,
  focused suites 42/42, full server module 256/256, `./scripts/build.sh`
  clean reactor): source-aware manifest and `legacy-unscoped` compatibility
  records, unified guest/host route registry with reserved admin aliases,
  generation-leased command dispatch, and the two-phase
  `RuntimeActivationTransaction` proven by the synthetic activation matrix.
  The independent delegate review is pending (review provider usage limit);
  WP2 remains blocked until that review accepts the WP1 boundary. The review
  prompt is `reviews/impl-review-prompt-phase-5-wp1.md`.

### 2026-07-29

- Plan created and execution approved by the user's request to build the best possible TypeScript-first content platform.
- Phase 1 completed after independent review and rework; Phase 2 started.
- Phase 2 completed after independent review and rework; Phase 3 started.
- Expanded Phases 4 and 5 after a production-route audit to include button/widget,
  magic, ground-item, item-on-player, and player-death endpoints plus equipment,
  action locks, deterministic encounter utilities, and complete skilling/resource
  loops.
- Phase 3 completed after production-route rework and independent review
  (accepted with two non-blocking follow-ups); Phase 4 started.
- Carried scripted quest objective/journal consumption and exhaustive persisted
  state/parser migration-boundary tests into Phase 5.

### 2026-07-30

- Completed and independently accepted Phase 4 WP3 with generation-owned
  encounters, arena isolation, exact lifecycle/death authority, and
  reload-safe dialogue continuations.
- Paused implementation after WP3 at the user's request; Phase 4 remains
  in progress with WP4-WP7 pending, and Phase 5 remains pending.
- Resumed the full TypeScript-first platform objective from the accepted WP3
  boundary; Phase 4 WP4 is now in progress.

### 2026-08-02

- Completed Phase 4: the bridge documentation (`docs/SCRIPT_BRIDGE.md`,
  `docs/TYPESCRIPT.md`) now covers the full Phase 4 surface, the exact final
  offline acceptance gate passed (TypeScript, 229 Maven tests, official
  build), and the live-smoke record documents a clean real-server boot with
  the compiled content loaded while honestly recording that the interactive
  client runbook steps were not performed (no interactive operator). Phase 4
  is marked completed; the external review prompt for the docs/gate work is
  `reviews/impl-review-prompt-phase-4-docs-gate.md`. Phase 5 remains pending.

### 2026-08-02

- Completed and accepted Phase 4 WP7: `content/src/bosses/encounter-warden.ts`
  entered the compiled loader and its live flow was proven end to end by
  `ScriptBossProductionE2ETest` — real command/pickup/walking/click packet
  decoding, script scheduler ticks, the production NPC death loop with exact
  owned-drop suppression, transactional private rewards verified and picked
  up through opcode 236, and every close path (explicit, owner death, logout,
  callback throw, rejected reload keeping the encounter live, successful
  reload closing the old generation). The delegate reviewer found the E2E
  faithful and exact; its compile-failure claim rested on a false tsconfig
  assumption, and its two fixture-route gaps (forged magic packet, NPC-update
  exclusion) were closed with production-predicate assertions. Focused gate
  18 tests, TypeScript compile, full JDK 17 reactor 229 tests, and the
  official build all passed with no failures/errors/skips. Execution stopped
  at the accepted WP7 boundary; the docs and final verification-gate work
  packages remain.

### 2026-08-02

- Completed and accepted Phase 4 WP6: exact SplitMix64 with literal vectors
  (independently reproduced outside the class under test), validating
  drop-entry parser, staged private-detach drop transaction with joint
  RNG/item commit and complete rollback on parse/capacity/staging/detach
  failure, and the user-approved spatial methods (`distance`, `isWalkable`,
  `hasProjectilePath`) that complete the frozen handle contract. Focused gate
  19 tests, TypeScript compile, full JDK 17 reactor 223 tests, and the
  official build all passed with no failures/errors/skips. The delegate
  reviewer subagent reviewed the parser/tests, found no implementation
  defect, and its parser negative-coverage finding was closed with the
  exhaustive real-path matrix (22 invalid-table cases). Execution stopped
  before WP7 at the standing work-package boundary.

### 2026-08-01

- Completed and independently accepted Phase 4 WP4 after clip-aware NPC route,
  live participant authorization, null-killer death safety, callback FIFO
  cleanup, and production owned-death fixture rework. TypeScript and the full
  Maven reactor gate passed (169 tests).
- Completed and independently accepted Phase 4 WP5 after exact layered object
  transactions, contributor-safe collision, authoritative writer/rebuild
  routing, exact private reward projection, truthful player facades, and
  deferred mutation-chain rework. The official build passed with 204 tests and
  no failures, errors, or skips. Execution stopped before WP6 as requested.
- Prepared and independently reviewed the source-grounded Phase 5
  implementation plan. Ten bounded work packages are defined for sequential
  execution; the final plan review is Ready with no findings. Phase 5 remains
  pending and every package stays blocked until Phase 4 WP6/WP7 are accepted.
