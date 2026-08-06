---
type: planning
entity: todo
plan: "typescript-content-platform"
updated: "2026-08-06"
---

# Todo: TypeScript Content Platform

> Tracking [TypeScript Content Platform](plan.md)

## Phase 5: Declarative Runtimes and OSRS Content Kit — Complete

### Phase Context

- **Scope**: [Phase 5](phases/phase-5.md)
- **Implementation**: [Phase 5 Plan](implementation/phase-5-impl.md)
- **Status**: completed (WP1-WP10 implemented; WP10 accepted 2026-08-06)
- **Latest Handover**: none
- **Relevant Docs**: [TypeScript Bridge](../../docs/SCRIPT_BRIDGE.md)

### Pending

None.

### Completed

- [x] Phase 5 WP10: representative vertical content and migration docs.
  The compiled `content/dist` loader now registers all eight production
  fixtures as source-aware content modules through the public SDK barrel:
  `dragon-island-drops` (the four named tables), `dragon-king` (the standalone
  Dragon King boss), `encounter-warden` (the King Black Dragon boss),
  `dragon-awakens` (the persisted quest, now using the SDK dialogue helpers),
  `temple-of-zaros` (the two-player raid, its roster reward, and its private
  loot), `dragon-island-shops` (the scripted general store), `dragon-island`
  (the activated Crandor area referencing the shop/quest/boss/raid by id),
  and `woodcutting-resources` (the tree and oak gathering resources). All
  fixture imports resolve through the SDK barrel; only the demonstration
  examples remain legacy-unscoped. The new `VerticalContentE2ETest` loads the
  compiled `content/dist` loader and crosses the manifest (8 modules),
  the activated area, the scripted shop through the production assistant,
  the gathering resource on an area-projected tree (harvest/deplete/respawn),
  the standalone boss (entry, named private drops), the scripted quest
  projection, a real save/load roundtrip of the started quest and script
  state, a rejected reload that keeps everything live, and a successful
  reload that re-activates the area and closes stale world projections in one
  flow. New `docs/typescript-content-authoring.md` and
  `docs/typescript-content-migration.md` plus updates to
  `docs/SCRIPT_BRIDGE.md`, `docs/TYPESCRIPT.md`, `docs/ENGINE_BOUNDARY.md`,
  and `docs/README.md` document the full authoring surface and the remaining
  Java-only boundaries. `ScriptHostTest` compiled-loader assertions were
  updated to the module manifest (8 modules, schema v1, source-aware).
  Gate: TypeScript clean, SDK tests 72/72, full server module 495/495 green
  (incl. the new vertical E2E), `./scripts/build.sh` BUILD SUCCESS. Accepted
  by the delegate review at `reviews/impl-review-phase-5-wp10.md` (0
  Critical/0 Major/3 Notes; the barrel-import and doc-phrasing Notes fixed,
  the harmless SCRIPT_BRIDGE phrasing accepted as-is).

### Completed

- [x] Phase 5 WP9: operator diagnostics and admin control. New
  `com.rs2.script.diagnostics` package: immutable `ScriptRuntimeStatus`
  (generation, module/definition/route counts, scheduled tasks, and active
  encounter/boss/area/shop/raid-lobby/raid-session/resource-session/quest-row
  counts) and `ScriptReloadResult` (truthful success/failure with the retained
  generation). `ScriptHost` gained `reloadWithResult()` and `getRuntimeStatus()`
  seams while keeping the programmatic `load()`/`reload()` compatibility
  methods. Permission-gated `::scripts status`, `::scripts list [kind] [page]`,
  `::scripts reload`, and `::reload` delegate to the truthful behavior, and the
  deprecated, sanitized `::scriptdir` alias emits a deprecation line plus the
  same bounded logical status snapshot — never a filesystem string. The legacy
  absolute-path `scriptdir` response was removed from `Commands`. Reserved
  aliases stay content-rejected; denied callers (rights < 2) receive no
  inventory/detail; listing is sorted/paged at 20; inspection is read-only and
  never executes guest code. The production `ScriptAdminCommandsTest` drives
  the real command-packet path covering permission, parsing, status, list,
  successful reload, rejected reload (previous generation proven live), and
  sanitized `scriptdir` (no absolute path for success or failure). Accepted by
  the delegate review at `reviews/impl-review-phase-5-wp9.md` (0 Critical/0
  Major, 2 Minor/3 Notes; both Minors and one Note fixed and re-verified: the
  `drop` list-kind alias now resolves through `parseKind`, the near-
  instantaneous-count semantics are documented, and a failed-reload
  `scriptdir` assertion pins the no-path guarantee). Gate: TypeScript clean,
  SDK tests 72/72, full server module 494/494 green, `./scripts/build.sh`
  BUILD SUCCESS. WP10 (representative vertical content and migration docs)
  is next.

### Completed

- [x] Phase 5 WP8: implement the gathering/resource-loop runtime.
  `defineGatheringResource` schema-v1 (canonical object id/action,
  skill/level, ordered tools, deterministic success chance, bounded item
  rewards plus experience, depleted object id, respawn ticks) parsed into an
  immutable Java-owned descriptor; `ScriptResourceRuntime` owns the exact
  host object route, validates skill/tools/live identity, opens a bounded
  per-player tick session, rolls the Java-owned `ResourceSessionRng`,
  commits item+XP atomically with exact weight/XP restore, depletes through
  the timed-object path, restores on respawn, and cancels every owned
  task/session on movement-away, logout, death, reload, or failure. The
  public `sdk/gathering.ts` builder (GraalJS-safe UTF-8 length, exact parser
  bounds, deep-freeze) and the compiled `resources/woodcutting.ts` fixture
  (tree 1276 + oak 1281) are shipped. The legacy `ClickObject` woodcutting
  pre-dispatch is gated on route absence so a registered tree never
  double-consumes. Accepted by the delegate review at
  `reviews/impl-review-phase-5-wp8.md` after one fix loop (first pass
  Rejected: 1 Major — legacy pre-dispatch double-consumption; 3 Minor —
  mid-session object revalidation, missing inventory-full/death/replacement/
  failure tests, misleading messaging; 2 Notes — deferred death cancel, E2E
  provenance; all fixed and re-verified). Post-fix gate: TypeScript clean,
  SDK tests 72/72, full server module 485/485 green,
  `./scripts/build.sh` BUILD SUCCESS. WP9 (operator diagnostics and admin
  control) is next. <!-- completed: 2026-08-04 -->

### Completed

- [x] Phase 5 WP7: stabilize the reusable public TypeScript content SDK.
  The new `content/src/sdk/` package exposes one public barrel
  (`sdk/index.ts`, re-exported by `content/src/index.ts` and
  `core/index.ts`) with no dependency on engine internals: pure
  requirement predicates and combinators over a narrow read-only view
  (`sdk/requirements.ts`), reward helpers calling the named transactional
  consumer (`sdk/rewards.ts`), explicitly typed scripted-vs-static shop
  references (`sdk/shops.ts`), equipment helpers over the 11 accepted
  runtime slot names with legacy-name migration errors
  (`sdk/equipment.ts`), bounded dialogue helpers plus the cutscene
  session engine that owns every task/lock/camera handle and cancels on
  step failure, the plan's final one-shot task, or explicit cancel
  (`sdk/dialogue.ts`), and a shared canonical skill table
  (`sdk/skills.ts`). Existing builders were aligned and deep-frozen
  (boss phases/specials/entry fields, raid rooms/rewards, area npc/object
  arrays); `createDropTable` validates, deep-freezes, and registers, the
  fluent `DropTableBuilder` emits canonical integral weights only, the
  legacy `Infinity`/`0.25`/`veryRare` forms fail with migration
  messages, the inert `LootTable`/`createLootTable`/`mergeTables`/
  `analyseTable`/`DropWeights` surface was removed, and the boss/raid
  builders reject the reserved `scripts`/`reload`/`scriptdir` aliases and
  duplicate raid reward references like the Java parsers. The Lumbridge
  man example now imports only from the SDK barrel and uses the SDK
  dialogue helpers. A generated API inventory
  (`docs/API_INVENTORY.md`, regenerated by
  `node content/scripts/api-inventory.mjs`) matches the declared runtime
  globals and SDK barrel exports, verified by 62 Node built-in runner
  tests (`pnpm --filter @singlescape/content test`) covering
  deep-freeze/mutation, invalid bounds, duplicates, missing references,
  stale handles, cancellation, and migration errors. Gate:
  TypeScript clean, SDK tests 65/65, full server module 467/467 green,
  `./scripts/build.sh` BUILD SUCCESS. Accepted by the delegate review
  at `reviews/impl-review-phase-5-wp7.md` after one fix loop (first
  pass Rejected: 1 Major — an `every` task fire could auto-complete the
  cutscene session, releasing trailing locks/cameras after one tick; 3
  Minor — rich-`Player` types on the SDK barrel surface, missing `sys.`
  namespace rejection in `createReward`, `createRaid` not deep-freezing
  raw room literals; 1 Note — stale-run guard). All five findings were
  fixed with regression tests and re-verified as Fixed by the delegate;
  final verdict Accepted (0 Critical/0 Major). <!-- completed: 2026-08-03 -->

### Completed

- [x] Phase 5 WP6: complete quest consumption, generic journal, and
  historical state migration. `QuestService.objective()` projects the
  active scripted objective (`null` for not started/missing stage, current
  stage text in progress, stable completion summary when completed) and
  `ScriptedQuest.objective()` exports it through the bridge; the new
  `ScriptQuestJournalService` deterministically maps sorted scripted quest
  ids onto the bounded pool of currently unimplemented legacy quest-tab
  rows (89 usable rows; candidates exceeding them are rejected), renders
  colored rows on login/transitions/successful reload, opens the generic
  detail interface 8134 (name, summary, requirements, state, objective)
  for mapped buttons, and leaves legacy names/buttons/details and exact
  scripted `onButton` authority untouched; Dragon Awakens now uses the
  generic objective projection instead of its login progress workaround;
  the built-in historical `v0` decoder migrates the flat
  u16-entryCount/u16-namespace/u16-key/u8-type/v1-payload body with strict
  grouping, duplicate, limit, truncation, and malformed-input rejection
  (encoding stays v1 only); a real character file with v0 state loads
  through `PlayerSave`, preserves legacy fields, installs the migrated
  state, and atomically re-saves v1 while malformed v0 is quarantined and
  unsavable; table-driven `-1/exact/+1` boundary matrices cover every
  quest parser and state codec bound plus unknown members/types,
  duplicates, truncation, trailing data, malformed UTF-8/Base64URL, and
  overflow. Accepted by the delegate review at
  `reviews/impl-review-phase-5-wp6.md` (0 Critical/0 Major; the docs Minor
  was resolved in the same pass — `docs/SCRIPT_BRIDGE.md` and
  `docs/TYPESCRIPT.md` now document `objective()`, the built-in v0
  decoder, and the "Scripted quest journal" service; the refresh-coupling
  Minor stays tracked as a non-blocking robustness follow-up). Gate:
  TypeScript clean, new suites 35/35 (codec boundary 11, parser boundary
  11, journal 8, quest service +2, persistence +3), quest/state/lifecycle
  focused gate 102/102, full server module 467/467 green,
  `./scripts/build.sh` BUILD SUCCESS. <!-- completed: 2026-08-03 -->

### Completed

- [x] Phase 5 WP5: consume declarative raid definitions. Strict schema-v1
  raid parser/registry (exact command route, bounds/muster/entrance,
  bounded limits, non-overlapping rooms, candidate-scoped boss/reward/
  drop-table references, boss spawn inside the room slice, duplicate
  rejection); the lobby/session runtime with the exact
  create/invite/join/leave/start contract, frozen owner-first/join-FIFO
  roster, exactly one encounter per started raid, embedded
  `BossController` borrowing the sole handle, ordered room advancement,
  the reward barrier with the once-only award id and bounded grace, the
  raid-session RNG owner, and the completion reward-table roll as private
  ground deliveries; the roster-wide `RosterRewardTransaction` (global
  coordinator, ascending player locks, exact snapshots/weight preflight,
  fresh per-attempt plans, reverse rollback, joint once-only commit); the
  compiled `temple-of-zaros` fixture migrated to canonical schema-v1 with
  two distinct live players and the loaded dragon-king boss; lifecycle/
  reload/cleanup seams; narrow `RaidRoomContext`. Accepted by the delegate
  review at `reviews/impl-review-phase-5-wp5.md` after all 10 findings
  were fixed (2 Major: identity-set departed accounting with membership
  removal on passive departure, owner-pinned lobby kept on non-owner
  departure; 4 Minor: owner-aware lobby capacity, roster-order mutation,
  room callback throws wipe, pre-mutation RNG revalidation; 4 Notes: boss
  poll cancellation at terminal, leave message, fixture wipe message,
  bridge-docs refresh tracked for WP10). Post-fix gate: TypeScript clean,
  new suites 32/32 (parser 8, runtime 18, reward 8, E2E 1), focused gate
  125/125, full server module 431/431 green, `./scripts/build.sh` BUILD
  SUCCESS. <!-- completed: 2026-08-03 -->

### Completed

- [x] Phase 5 WP4: consume declarative area definitions (and scripted
  shops). Strict schema-v1 area parser/registry with exact spawn/object
  keys, definition-backed ids, drop-policy coupling, and candidate-scoped
  shop/quest/boss/raid references; the production projection adapter
  (`ScriptAreaRuntime`) activates areas through the real two-phase
  transaction with exact abort restoration at every injected stage,
  same-footprint handoff, and generation cleanup; exact allocation-bound
  NPC death authority through the real NpcHandler critical section with
  killer-private/public WP2 ground identities, handled NO_RECIPIENT,
  equal-id legacy fallback, respawn, and reload cleanup; exact
  tile-position object-drop routes with one-shot claims; scripted shops
  (`defineShop`) with declared stock/prices/restock through the production
  ShopAssistant buy/sell path and the exact allocation-bound opening
  route; the compiled `dragon-island` fixture migrated to canonical
  schema-v1 on the real Crandor map region (11414/11415) with loaded
  definition-backed ids. Post-fix gate: TypeScript clean, new suites
  50/50 (area parser 8, shop parser 7, runtime activation 9, drop
  authority 11, object routes 7, shop runtime 8), focused gate 93/93,
  full server module 386/386 green (incl. `CycleEventHandlerTest` 3/3;
  the cycle-event fix is commit 69457652 — the earlier "user WIP commit
  460230b" record was stale and does not exist), `./scripts/build.sh`
  BUILD SUCCESS. Accepted by the delegate review (0 Critical/0 Major/1
  Minor/3 Notes; Minor fixed here, Notes tracked: bridge-docs refresh in
  WP10, deliberate legacy-shop price/closePlayerShop changes and the new
  boot `loadShops()` call recorded in the plan changelog) at
  `reviews/impl-review-phase-5-wp4.md`. <!-- completed: 2026-08-02 -->

### Completed

- [x] Phase 5 WP3: consume declarative boss definitions (encounter-agnostic
  `BossController` plus standalone owning adapter). Accepted by the delegate
  review (0 Critical/0 Major/4 Minor, all fixed) at
  `reviews/impl-review-phase-5-wp3.md`: the drop-failure -> FAILED semantics
  documented, `BossRegistry` trimmed to typed reads, the loaded-npc
  validation re-gated on the same npc.json list the spawn path validates
  (`NpcHandler.hasNpcDefinitions`), and `enter` now runs the guest `onSpawn`
  outside the service monitor. Post-fix gate: TypeScript, new suites 24/24,
  converted WP7 E2E 6/6, full server module 313/313,
  `./scripts/build.sh` clean reactor. <!-- completed: 2026-08-02 -->

### Completed

- [x] Phase 5 WP2: consume drop and reward definitions transactionally.
  `defineDropTable`/`defineReward` schema-v1 parsers with source-aware
  diagnostics and exact item-name resolution; owner-neutral `DropTransaction`
  with `DropRngTransactionOwner`/`GroundDeliveryPolicy` and the encounter
  adapter preserving the exact WP6 vectors, seams, and cleanup; shared
  `PlayerRewardTransaction` (inventory, recalculated weight, XP, points,
  state) with the per-player reward-state owner (mutex + monotonic version)
  and quest completion as a thin adapter with unchanged result codes; the
  shipped loot fixtures migrated to canonical named records with
  compiled-loader entry-count assertions. Focused suites 52/52, full server
  module 281/281, `./scripts/build.sh` clean reactor. Accepted by the
  delegate review (0 Critical/0 Major/4 Minor, all fixed) at
  `reviews/impl-review-phase-5-wp2.md`. <!-- completed: 2026-08-02 -->
- [x] Phase 5 WP1: establish the source-aware manifest, unified route
  registry, and two-phase runtime activation infrastructure. The compiled
  loader registers every existing category as source-aware
  `legacy-unscoped` compatibility records; one immutable route registry owns
  guest and host routes with candidate-wide uniqueness and the reserved
  `scripts`/`reload`/`scriptdir` aliases; command lookup/invocation is
  generation-leased through the production path; `replaceContext` runs the
  exact two-phase handoff (prepare, reserve, shadow apply/verify, undo-ledger
  retirement, final checkpoint, contained unload observers, no-throw commit,
  contained onLoad, degraded finalize) proven by the synthetic activation
  matrix. TypeScript compiles; focused suites 42/42; full server module
  256/256; `./scripts/build.sh` clean reactor passed. Accepted by the
  delegate review (0 Critical/0 Major/2 Minor, both fixed) at
  `reviews/impl-review-phase-5-wp1.md`. <!-- completed: 2026-08-02 -->

- [x] Audit current bridge and legacy interaction paths. <!-- completed: 2026-07-29 -->
- [x] Define the five-phase TypeScript-first platform roadmap. <!-- completed: 2026-07-29 -->
- [x] Implement and test item click, item-on-item, item-on-object, and item-on-NPC registrations. <!-- completed: 2026-07-29 -->
- [x] Add rich command invocation context. <!-- completed: 2026-07-29 -->
- [x] Expand safe item/player runtime primitives and exact TypeScript contracts. <!-- completed: 2026-07-29 -->
- [x] Add representative content and update bridge documentation. <!-- completed: 2026-07-29 -->
- [x] Run the complete content and engine verification gate (43 tests, independent review accepted). <!-- completed: 2026-07-29 -->
- [x] Implement reload-safe player-owned `after`/`every` scheduling and cancellation. <!-- completed: 2026-07-29 -->
- [x] Add login/logout/NPC-death/item-pickup/area lifecycle APIs and production hooks. <!-- completed: 2026-07-29 -->
- [x] Add generation-atomic reload/logout cleanup, deterministic tests, TypeScript examples, and docs. <!-- completed: 2026-07-29 -->
- [x] Phase 4 WP4: add stable NPC allocation/handles, exact owned-drop
  suppression, participant-only combat continuations, immutable NPC-death
  contexts, and a re-entrancy-safe deferred destructive drain. <!-- completed: 2026-08-01 -->
- [x] Add safe NPC, temporary-object, ground-reward, area, distance, and
  collision capabilities. <!-- completed: 2026-08-01 -->
- [x] Add bounded combat, equipment, movement/action lock, visual, interface,
  camera, projectile, sound, run-energy, and shop capabilities.
  <!-- completed: 2026-08-01 -->
- [x] Phase 4 WP5: implement collision transactions, exact ground identities,
  remaining facades, camera/shops, and the full lock matrix.
  <!-- completed: 2026-08-01 -->
- [x] Run the official WP5 TypeScript/client/server gate and independently
  accept the implementation (204 tests, 0 failures/errors/skips).
  <!-- completed: 2026-08-01 -->
- [x] Prepare and independently review the Phase 5 implementation plan as ten
  bounded, sequential work packages (Ready, 0 findings); keep every package
  blocked until Phase 4 WP6/WP7 are accepted. <!-- completed: 2026-08-01 -->
- [x] Add encounter-scoped deterministic RNG, participant snapshots, and
  drop-table execution. <!-- completed: 2026-08-02 -->
- [x] Phase 4 WP6: implement SplitMix64 with literal vectors, validating
  drop-entry parsing, staged private-detach drop transaction with joint
  RNG/item commit, and the spatial methods that complete the frozen handle
  contract. <!-- completed: 2026-08-02 -->
- [x] Phase 4 WP7: implement the compiled `encounter-warden` production boss
  (arena reservation, entry/phase locks and cameras, layered barrier, phased
  skeleton adds, scheduled projectiles, transactional owned death drops) and
  prove the live flow end to end through real command/pickup/walking/click
  packets, script ticks, the production NPC death loop, private reward
  pickup, and every close path (explicit, owner death, logout, callback
  throw, rejected and successful reload). <!-- completed: 2026-08-02 -->
- [x] Update TypeScript contracts, examples, and bridge documentation:
  `docs/SCRIPT_BRIDGE.md` and `docs/TYPESCRIPT.md` now document the full
  Phase 4 surface and the Encounter Warden fixture. <!-- completed: 2026-08-02 -->
- [x] Run the complete content and engine verification gate: exact final
  offline gate (TypeScript + 229 Maven tests) and the official build passed;
  a real server boot loaded the compiled content cleanly and the interactive
  client smoke steps are recorded as a limitation in the live-smoke record.
  <!-- completed: 2026-08-02 -->
- [x] Run Phase 2 verification (55 tests, independent review accepted). <!-- completed: 2026-07-29 -->
- [x] Add bounded namespaced boolean/number/string player state.
  <!-- completed: 2026-07-29 -->
- [x] Add a deterministic versioned save/load codec, migration seam, durable
  malformed-state quarantine, and atomic file replacement.
  <!-- completed: 2026-07-29 -->
- [x] Turn quest definitions into immutable Java-owned descriptors and a
  validated functional runtime. <!-- completed: 2026-07-29 -->
- [x] Implement Dragon Awakens as a production-playable, persisted,
  retry-safe multi-stage TypeScript quest. <!-- completed: 2026-07-29 -->
- [x] Add state, codec, quest transition/reward, reload, production packet,
  death lifecycle, and real save/load tests. <!-- completed: 2026-07-29 -->
- [x] Correct the nullable-stage, result-shaped eligibility, stable result-code,
  XP-cap, UI-refresh, and reward-rollback contracts.
  <!-- completed: 2026-07-29 -->
- [x] Run Phase 3 verification (92 tests, independent review accepted with
  non-blocking follow-up). <!-- completed: 2026-07-29 -->
- [x] Gate the source-grounded Phase 4 implementation plan through independent
  review (Ready, 0 findings). <!-- completed: 2026-07-29 -->
- [x] Complete Phase 4 WP1: immutable bridge types, staging-only registries,
  one authoritative context/state/generation publication, and generation-
  leased dispatch for every existing executable route. <!-- completed: 2026-07-29 -->
- [x] Rework and independently accept WP1 after the exact TypeScript plus full
  JDK 17 Maven gate passed (103 tests, 0 failures/errors/skips).
  <!-- completed: 2026-07-29 -->
- [x] Complete and independently accept Phase 4 WP2: authoritative packet
  matrices for 185/25/14/237/35, exact-token 25/236/253 ground identity,
  configured global-drop compatibility, and token-safe Firemaking.
  <!-- completed: 2026-07-29 -->
- [x] Run the exact WP2 TypeScript plus full JDK 17 Maven gate (129 tests,
  0 failures/errors/skips). <!-- completed: 2026-07-29 -->
- [x] Complete and independently accept Phase 4 WP3: generation-owned
  encounters, exclusive reservations, participant/task/lock ownership,
  tile-by-tile movement isolation, lifecycle-epoch facade authority,
  exact player-death tickets, and authenticated dialogue continuations.
  <!-- completed: 2026-07-30 -->
- [x] Run the exact WP3 TypeScript, focused JDK 17, and full Maven gates
  (29 focused and 158 full tests; 0 failures/errors/skips; independent review
  accepted with no actionable findings). <!-- completed: 2026-07-30 -->

### Tracked Follow-ups

- [x] Phase 5: expose scripted current objectives and consume them in a
  generic quest journal/UI (resolved in WP6: `objective()` projection,
  generic interface 8134 journal, colored rows, transition/login/reload
  refresh). <!-- completed: 2026-08-03 -->
- [x] Phase 5: finish exhaustive codec/parser boundary matrices and
  exercise a historical state migration through `PlayerSave` (resolved in
  WP6: built-in v0 decoder, table-driven `-1/exact/+1` matrices, real
  v0-character migration and quarantine). <!-- completed: 2026-08-03 -->
- [x] Phase 5: comprehensive authoring/migration docs pass (resolved in
  WP10: `docs/typescript-content-authoring.md` and
  `docs/typescript-content-migration.md` plus `docs/SCRIPT_BRIDGE.md`,
  `docs/TYPESCRIPT.md`, `docs/ENGINE_BOUNDARY.md`, and `docs/README.md`
  updates). <!-- completed: 2026-08-06 -->
- [ ] Phase 4 hardening: add an exhaustive table-driven malformed/duplicate
  negative matrix for every registration global (WP1 review follow-up).
- [ ] Release: perform a connected-client quest smoke test when the launcher
  environment is available.

### Blocked

None.

## Changelog

### 2026-08-06

- Phase 5 WP10 accepted by the delegate review
  (`reviews/impl-review-phase-5-wp10.md`; 0 Critical/0 Major, 3 Notes; the
  barrel-import and doc-phrasing Notes fixed, the harmless SCRIPT_BRIDGE
  phrasing accepted as-is). Phase 5 is complete. Gate: TypeScript clean,
  SDK tests 72/72, full server module 495/495 green, `./scripts/build.sh`
  BUILD SUCCESS.

- Phase 5 WP10 completed: all eight production fixtures now register as
  source-aware content modules through the SDK barrel, the quest uses the SDK
  dialogue helpers, the new `VerticalContentE2ETest` crosses the compiled
  loader across every family, and the authoring/migration guides plus the
  bridge/typescript/boundary/README updates land. Gate: TypeScript clean,
  SDK tests 72/72, full server module 495/495 green, `./scripts/build.sh`
  BUILD SUCCESS. The delegate review is at
  `reviews/impl-review-prompt-phase-5-wp10.md`.

### 2026-08-05

- Phase 5 WP9 accepted by the delegate review
  (`reviews/impl-review-phase-5-wp9.md`; 0 Critical/0 Major, 2 Minor/3
  Notes, all fixed and re-verified; final verdict Accepted). The two Minors
  fixed: `::scripts list drop` now resolves through `parseKind` (the
  `DefinitionKind` enum name is `DROP_TABLE`, so the documented `drop` alias
  previously reported "Unknown definition kind") and `ScriptRuntimeStatus`
  documents that the runtime counts are near-instantaneous across the
  singleton monitors (not a single atomic instant). One Note fixed: the
  `scriptdir` test now forces a failed reload and asserts the subsequent
  output stays bounded logical status + "Last reload failed" with no
  absolute path. Post-fix gate: TypeScript clean, SDK tests 72/72, full
  server module 494/494 green, `./scripts/build.sh` BUILD SUCCESS.
  WP10 (representative vertical content and migration docs) is next.

- Phase 5 WP9 implemented with the primary gate passing (TypeScript clean,
  72/72 SDK tests, full server module 494/494 green — the new
  `ScriptAdminCommandsTest` suite 9/9 — `./scripts/build.sh` BUILD SUCCESS).
  The new `com.rs2.script.diagnostics` package ships the immutable
  `ScriptRuntimeStatus` (active generation, module/definition/route counts,
  scheduled tasks, and active encounter/boss/area/shop/raid-lobby/
  raid-session/resource-session/quest-row counts) and `ScriptReloadResult`
  (truthful success or bounded failure with the retained generation).
  `ScriptHost` exposes `reloadWithResult()` and `getRuntimeStatus()` while
  keeping the programmatic `load()`/`reload()` compatibility methods; the
  runtime singletons gained engine-visible count accessors (encounter, boss,
  area, shop, raid lobby/session, resource session, scheduler task, and
  journal row). Permission-gated `::scripts status`, `::scripts list
  [kind] [page]`, `::scripts reload`, and `::reload` delegate to the truthful
  behavior; the deprecated, sanitized `::scriptdir` alias emits a deprecation
  line plus the same bounded logical status snapshot and never returns a
  filesystem string. The legacy absolute-path `scriptdir` response was removed
  from `Commands` (the aliases moved from rights-3 developer commands to the
  rights-2 admin tier). Reserved aliases stay content-rejected; denied
  callers (rights below 2) receive no inventory/detail; listing is sorted and
  paged at 20; inspection is read-only and never executes guest code. The
  production `ScriptAdminCommandsTest` drives the real command-packet path
  covering permission, parsing, status, list, successful reload, rejected
  reload (previous generation proven live), and sanitized `scriptdir` (no
  absolute path for success or failure). Docs updated:
  `docs/SCRIPT_BRIDGE.md` (new "Operator diagnostics and admin control (WP9)"
  section) and `docs/TYPESCRIPT.md` (new "Operator diagnostics" paragraph).
  WP10 (representative vertical content and migration docs) is next.

### 2026-08-04

- Phase 5 WP8 accepted by the delegate review
  (`reviews/impl-review-phase-5-wp8.md`; first pass Rejected with 1
  Major/3 Minor/2 Notes, all fixed and re-verified; final 0 Critical/0
  Major). The Major fixed the production-path double-consumption: the legacy
  `Woodcutting.startWoodcutting` pre-dispatch in `ClickObject.completeObjectClick`
  is now gated on route absence (`isScriptedClick` reads the unified guest-or-
  host route record), proven by a `completeObjectClick` E2E. The Minors fixed
  live-object revalidation each tick, distinct reward-failure messaging, and
  the missing inventory-full/death/object-replacement/runtime-failure E2E
  coverage (485/485). WP9 (operator diagnostics and admin control) is next.

### 2026-08-04

- Phase 5 WP8 implemented with the primary gate passing (TypeScript clean,
  72/72 SDK tests, full server module 480/480 green, `./scripts/build.sh`
  BUILD SUCCESS). The `defineGatheringResource` schema-v1 runtime is complete
  (see the completed entry above); WP9 (operator diagnostics and admin
  control) is next.

### 2026-08-03

- Phase 5 WP7 accepted by the delegate review
  (`reviews/impl-review-phase-5-wp7.md`; first pass Rejected with 1
  Major/3 Minor/1 Note, all fixed and re-verified; final 0 Critical/0
  Major). The public TypeScript content SDK is stabilized: the
  `content/src/sdk/` barrel (canonical type surface only), aligned
  deep-frozen builders, the cutscene session engine (one-shot-gated
  completion, repeating tasks stay tracked/cancellable), canonical
  drop-table builder with migration errors for the legacy weight forms,
  removed inert `LootTable` machinery, the `sys.`-prefix reward
  rejection, the generated `docs/API_INVENTORY.md`, the 65 Node
  built-in runner tests, and the updated
  `docs/SCRIPT_BRIDGE.md`/`docs/TYPESCRIPT.md` sections. Gate:
  TypeScript clean, SDK tests 65/65, full server module 467/467 green,
  `./scripts/build.sh` BUILD SUCCESS. WP8 (gathering/resource-loop
  runtime) is next.

- Phase 5 WP6 accepted by the delegate review
  (`reviews/impl-review-phase-5-wp6.md`; 0 Critical/0 Major). The docs
  Minor was resolved in the same pass: `docs/SCRIPT_BRIDGE.md` now
  documents `objective()` in the `ScriptedQuest` contract, the built-in
  historical v0 decoder (with the real v0 `PlayerSave` migration and
  quarantine behavior), the updated Dragon Awakens login projection, and
  a new "Scripted quest journal" subsection (mapping/pool, row colors,
  generic interface 8134, refresh seams, and the single-threaded game-
  cycle lock-order note); `docs/TYPESCRIPT.md` gained the author-facing
  journal paragraph. The refresh-coupling Minor (the `start`/`setStage`
  tab refresh runs inside the mutation try-block; not reachable in
  practice, DoD met) stays tracked as a non-blocking robustness
  follow-up, and the encoded-payload boundary Note is recorded with its
  rationale. The delegate could not run the dynamic gates in its
  read-only environment; the primary agent re-ran and confirmed them:
  TypeScript clean, new suites 35/35, focused gate 102/102, full server
  module 467/467 green, `./scripts/build.sh` BUILD SUCCESS. WP7 is next.
- Phase 5 WP6 implementation complete: `QuestService.objective()`
  projection exported through `ScriptedQuest.objective()` (null for not
  started/missing stage, current stage text in progress, stable completion
  summary when completed); the new `ScriptQuestJournalService` with the
  deterministic sorted-id-to-sorted-unused-row mapping (candidate
  rejection beyond the 89 usable rows), colored rows on
  login/transitions/successful reload, the generic detail interface 8134
  (name, summary, bounded requirements, state, objective), and untouched
  legacy buttons/names/details plus exact scripted `onButton` authority;
  Dragon Awakens migrated to the generic objective projection; the built-
  in historical v0 state decoder (flat u16-entry layout, strict grouping/
  duplicate/limit/truncation/malformed rejection, v1-only encoding); real
  v0 `PlayerSave` migration with legacy-field preservation and atomic v1
  re-save plus malformed-v0 quarantine; table-driven `-1/exact/+1`
  boundary matrices for every quest parser and state codec bound. Gate:
  TypeScript clean, new suites 35/35 (codec boundary 11, parser boundary
  11, journal 8, quest service +2, persistence +3), quest/state/lifecycle
  focused gate 102/102, full server module 467/467 green,
  `./scripts/build.sh` BUILD SUCCESS. The independent delegate review is
  pending (review prompt: `reviews/impl-review-prompt-phase-5-wp6.md`);
  WP7 remains blocked until that review accepts the WP6 boundary. Note:
  the `QuestBridgeIntegrationTest` fixture player now discards flushed
  quest-tab packets (quest transitions refresh the tab through the same
  packet path real players use).
- Phase 5 WP5 accepted by the delegate review
  (`reviews/impl-review-phase-5-wp5.md`) after all 10 findings were fixed
  and re-verified. The 2 Major findings: `departed` is now an identity
  `Set` with membership removal on passive departure (a member who dies
  and then logs out is recorded exactly once; the raid continues while
  the owner remains active), and the lobby is pinned to its owner (a
  non-owner invitee/opt-in departure removes only its invite/opt-in and
  membership). The 4 Minor findings: the lobby capacity check counts the
  owner's roster slot (`maxPlayers - 1` opt-ins) with a join-proof test;
  planning and mutation now follow the frozen owner-first/join-FIFO
  roster order while locks stay slot-ascending; throwing room
  onEnter/onTick/onComplete callbacks now wipe the raid exactly once;
  the RNG-owner version is revalidated immediately before the first
  player mutation and returns RETRYABLE with zero mutation. The 4 Notes:
  the boss poll task is cancelled at terminal, `leave` during an
  inactive session reports a message, the fixture wipe message no longer
  claims a teleport, and the bridge-docs refresh stays tracked for WP10.
  Post-fix gate: TypeScript clean, new suites 32/32 (parser 8, runtime
  18, reward 8, E2E 1), focused gate 125/125, full server module 431/431
  green, `./scripts/build.sh` BUILD SUCCESS. WP6 is next.
- Phase 5 WP5 implementation complete: strict schema-v1 raid parser and
  registry, the lobby/session runtime with the exact create/invite/join/
  leave/start contract and frozen owner-first/join-FIFO roster, exactly
  one encounter per started raid with the embedded boss controller
  borrowing the sole handle, the reward barrier with the once-only award
  id, the raid-session RNG owner and completion reward-table roll, the
  roster-wide `RosterRewardTransaction`, the compiled `temple-of-zaros`
  fixture migrated to canonical schema-v1 with two distinct live players
  and the loaded dragon-king boss, and the lifecycle/reload/cleanup seams.
  Gate: TypeScript clean, new suites 32/32 (parser 8, runtime 15, reward
  8, E2E 1), full server module 428/428 green, `./scripts/build.sh`
  BUILD SUCCESS. The independent delegate review is pending (review
  prompt: `reviews/impl-review-prompt-phase-5-wp5.md`); WP6 remains
  blocked until that review accepts the WP5 boundary. Note: the uncommitted
  user WIP `KeldagrimStairsTest` was adjusted only for JUnit 4
  compatibility and a coordinate that contradicted its own implementation;
  both changes are required for the gate and are unrelated to WP5.

### 2026-08-02

- Phase 5 WP4 accepted by the delegate review (0 Critical, 0 Major,
  1 Minor, 3 Notes) at `reviews/impl-review-phase-5-wp4.md`. The Minor
  (stale gate record citing a non-existent "user WIP commit 460230b" and
  33/33 new-suite numbers) is fixed: observed gates are new suites 50/50,
  focused 93/93, full server module 386/386 green (the cycle-event fix is
  commit 69457652), TypeScript clean, `./scripts/build.sh` BUILD SUCCESS.
  The three Notes are tracked: the `docs/SCRIPT_BRIDGE.md` globals/docs
  refresh is owned by WP10; the deliberate legacy-shop price changes
  (`ShopAssistant` buy/sell multipliers for shops 190/220/226, covered by
  `ShopAssistantPriceTest`), the `ShopHandler.closePlayerShop` rewrite
  (prior scan-from-empty-shop bug), and the first-ever boot
  `shopHandler.loadShops()` call (`GameEngine.java:208`) are deliberate,
  tested boot/price changes beyond pure area-runtime scope and recorded
  here. WP5 is next.
- Phase 5 WP4 implementation complete: canonical area/shop schemas and
  registries, the real two-phase area activation adapter, exact
  allocation-bound NPC death and object-drop authority over the WP2 drop
  transaction, the scripted-shop runtime through the production
  buy/sell path, and the compiled `dragon-island` fixture migrated to the
  real Crandor map region with loaded ids. The independent delegate review
  is pending (review prompt:
  `reviews/impl-review-prompt-phase-5-wp4.md`); WP5 remains blocked until
  that review accepts the WP4 boundary. Gate: TypeScript, new suites
  33/33, full server module 372 tests with only the pre-existing
  unrelated `CycleEventHandlerTest` failures (user WIP commit 460230b),
  `./scripts/build.sh` otherwise clean reactor.

- Phase 5 WP3 accepted by the delegate review (0 Critical, 0 Major,
  4 Minor; all fixed and re-verified: documented drop-failure -> FAILED
  semantics, `BossRegistry` trimmed to typed reads, loaded-npc validation
  re-gated on the same npc.json list the spawn path validates, and `enter`
  running the guest `onSpawn` outside the service monitor). Post-fix gate:
  TypeScript, new suites 24/24, converted WP7 E2E 6/6, full server module
  313/313, `./scripts/build.sh` clean reactor. Review record:
  `reviews/impl-review-phase-5-wp3.md`. WP4 is next.
- Phase 5 WP3 implementation complete: strict schema-v1 boss parser and
  registry, encounter-agnostic `BossController`, standalone owning adapter
  with exact WP1 host routes, finalized `HostRoute` invocation shape,
  Java-owned scheduler/death/drop seams, narrow `BossRuntimeContext`, and
  the atomic `dragon-king`/`encounter-warden` fixture migration. Primary
  gate passed (TypeScript, new suites 23/23, converted WP7 E2E 6/6, full
  server module 309/309, `./scripts/build.sh` clean reactor). The
  independent delegate review could not run in this session because the
  review provider hit its usage limit; the review prompt is
  `reviews/impl-review-prompt-phase-5-wp3.md` and WP4 remains blocked until
  that review accepts the WP3 boundary.
- Phase 5 WP2 accepted by the delegate review (0 Critical, 0 Major, 4 Minor;
  all fixed and re-verified: exact `ItemDefinition.exists` drop preflight,
  parser label diagnostic, named-table-through-real-encounter composition
  test, and load-time `sys.*` namespace rejection). The review record is
  `reviews/impl-review-phase-5-wp2.md`. WP3 is next.
- Phase 5 WP1 accepted by the delegate review (0 Critical, 0 Major, 2 Minor;
  both minors fixed and re-verified: `content/src/manifest.ts` recreated and
  the checkpoint test now asserts the unload hook produced no output). The
  review record is `reviews/impl-review-phase-5-wp1.md`. WP2 is now in
  progress.
- Completed Phase 5 WP1 implementation: source-aware content-module manifest
  (`registerContentModule`), common `DefinitionRecord` envelope, unified
  guest/host route registry with reserved admin aliases, generation-leased
  command dispatch, and the two-phase `RuntimeActivationTransaction` with a
  synthetic projection matrix. The primary gate passed (TypeScript compile,
  focused suites 42/42, full server module 256/256, `./scripts/build.sh`
  clean reactor). The independent delegate review could not run in this
  session because the review provider hit its usage limit; the review prompt
  is `reviews/impl-review-prompt-phase-5-wp1.md` and WP2 remains blocked
  until that review accepts the WP1 boundary.
- Completed Phase 4: bridge docs (`docs/SCRIPT_BRIDGE.md`,
  `docs/TYPESCRIPT.md`) updated for the full Phase 4 surface; the exact final
  offline gate (TypeScript + 229 Maven tests) and the official build passed;
  a real server boot loaded the compiled content cleanly. The interactive
  client smoke steps are recorded as a limitation (no interactive operator)
  with maintainer acknowledgement in the Phase 4 live-smoke record, and the
  external review prompt is
  `reviews/impl-review-prompt-phase-4-docs-gate.md`. Phase 5 remains pending
  and blocked until explicit continuation.
- Completed and accepted Phase 4 WP7: the compiled `encounter-warden`
  production boss and its live-flow E2E across real command/pickup/walking/
  click packet decoding, script scheduler ticks, the production NPC death
  loop, exact private owned rewards with pickup, and every close path. The
  delegate reviewer accepted after the forged-magic-packet and NPC-update-
  exclusion gaps were closed with production-predicate assertions.
  TypeScript compile, the focused gate (18 tests), the full JDK 17 reactor
  (229 tests), and the official build all passed with zero failures/errors/
  skips. Execution stopped at the accepted WP7 boundary; the remaining Phase 4
  work packages are bridge docs and the final verification gate.
- Completed and accepted Phase 4 WP6: deterministic SplitMix64 with
  independently verified literal vectors, validating drop-entry parser,
  staged private-detach drop transaction with joint RNG/item commit and full
  rollback, and the spatial methods that complete the frozen encounter-handle
  contract. The delegate reviewer subagent then closed its one coverage
  finding with the exhaustive real-path parser negative matrix. Focused gate
  (19 tests), TypeScript compile, full JDK 17 reactor (223 tests), and the
  official build all passed with zero failures/errors/skips. Execution
  stopped at the accepted WP6 boundary; WP7 (production boss E2E) requires
  explicit continuation.

### 2026-07-29

- Plan created and Phase 1 started.
- Phase 1 completed after independent review/rework; Phase 2 scheduling and lifecycle work started.
- Phase 2 completed after independent review/rework; Phase 3 persistence and quest work started.
- Expanded future endpoint coverage after auditing remaining packet, player,
  presentation, encounter, and skilling boundaries.
- Phase 3 completed after rework and independent acceptance; Phase 4 world and
  encounter capability work started.
- Tracked the accepted Phase 3 journal, exhaustive-boundary, historical
  migration, and live-smoke follow-ups explicitly.
- Gated the revised Phase 4 implementation plan as Ready after three
  source-grounded review/revision loops.
- Completed and independently accepted Phase 4 WP1 after atomic-publication,
  immutable-registry, strict-registration, and full-suite rework; WP2 started.
- Completed and independently accepted Phase 4 WP2 after configured-global-
  drop, exact-player-privacy, exhaustive packet-matrix, and TypeScript-contract
  rework; WP3 started.

### 2026-07-30

- Completed and independently accepted Phase 4 WP3 after action-lock,
  intermediate-path isolation, retained-facade epoch, exact death-ticket,
  non-null task-handle, and delayed-dialogue authority rework.
- Paused execution at the user-requested WP3 boundary. WP4-WP7 and Phase 5
  remain pending and were not started.
- Resumed the full TypeScript-first objective from the accepted WP3 boundary
  and started Phase 4 WP4.

### 2026-08-01

- Completed and independently accepted Phase 4 WP4 after rework covering
  clip-aware owned-NPC routes, fail-closed live participant authorization,
  null-killer-safe death ordering, exact callback FIFO cleanup, and real owned
  death integration fixtures. TypeScript compilation and the full Maven reactor
  gate passed (169 tests).
- Completed and independently accepted Phase 4 WP5 after layered object and
  collision authority, exact reward projection, truthful facades, production
  writer/packet coverage, and lossless deferred mutation chains. The official
  build passed with 204 tests; stopped before WP6 for explicit approval.
- Prepared the source-grounded Phase 5 implementation plan as ten bounded WPs
  and independently reviewed it to Ready with no findings. Phase 5 remains
  pending/draft and blocked behind accepted Phase 4 WP6/WP7.
