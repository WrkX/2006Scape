---
type: planning
entity: phase
plan: "typescript-content-platform"
phase: 4
status: completed
created: "2026-07-29"
updated: "2026-08-02"
---

# Phase 4: World and Encounter Services

> Part of [TypeScript Content Platform](../plan.md)

## Objective

Expose bounded capabilities needed to construct encounters and spatial content without giving scripts raw engine access.

## Scope

### Includes

- Authoritative, validated routes for button/widget actions, item-on-ground-item,
  item-on-player, magic-on-item, magic-on-object, and player death.
- NPC spawn/despawn/control, local object replace/remove, ground-item rewards,
  damage/heal, animation/graphic/projectile/sound, shops/interfaces, equipment
  queries, movement/action locks, and encounter ownership.
- Region/area/collision/distance queries, participant tracking, deterministic
  RNG, cleanup, and drop-table execution.
- A representative phased boss encounter in TypeScript.

### Excludes (deferred to later phases)

- Replacing core pathfinding, packet encoding, or cache loaders.

## Prerequisites

- [x] Phase 2 is complete.
- [x] Persistent encounter state requirements from Phase 3 are available where needed.

## Progress

- WP1-WP7, the bridge documentation, and the final verification gate are
  completed; Phase 4 is accepted as complete (the interactive client smoke
  steps are recorded as a limitation, not claimed as passed).
- Phase 5 remains pending; its packages stay blocked until explicit
  continuation.

## Deliverables

- [x] Capability-specific world and encounter wrappers.
- [x] Remaining high-value interaction registries and production packet/death hooks.
- [x] Ownership/cleanup rules and runtime consumers.
- [x] TypeScript encounter APIs, example boss, tests, and docs.

## Acceptance Criteria

- [x] A bounded boss encounter can spawn, phase, reward, and clean up entirely from TypeScript.
- [x] Reload/logout/death cleanup cannot orphan owned NPCs, objects, or callbacks.
- [x] Invalid coordinates, IDs, amounts, and ownership operations are rejected safely.
- [x] Button, magic, ground-item, item-on-player, and player-death content can be
  authored without editing a legacy Java packet switch.

## Dependencies on Other Phases

| Phase | Relationship | Notes |
|-------|-------------|-------|
| Phase 2 | blocked-by | Needs scheduling and lifecycle ownership. |
| Phase 3 | blocked-by | Uses persistent progression/reward state where applicable. |
| Phase 5 | blocks | Declarative boss/raid runtimes consume these services. |

## Notes

Prefer owned encounter handles over global mutation functions.

## Live Smoke Record — 2026-08-02

Per the Step 7 runbook, the following is the honest record of the live
server/client smoke for `encounter-warden`:

- **Date**: 2026-08-02
- **Operator**: opencode agent (automated environment; no interactive session)
- **Java version**: Temurin JDK 17 (`/usr/libexec/java_home -v 17`)
- **Server build**: `engine/server/target/server-1.0-jar-with-dependencies.jar`
  from `./scripts/build.sh` (BUILD SUCCESS)
- **Client build**: `engine/client/target/client-1.0-jar-with-dependencies.jar`
  (built by the same gate, but not launched)
- **Content**: `content/dist` (compiled loader includes
  `bosses/encounter-warden.js`)

**Server boot (performed)**: `./scripts/run-server.sh` started successfully;
the log shows `ScriptHost replaceContext: INFO: Loaded 1 script modules`
(the compiled loader with the warden content), 21 plugins loaded, the world
server listening on `0.0.0.0:43594`, and normal game cycles (Cycle #10-50,
2427 NPCs, 0 players) with no errors or exceptions beyond the pre-existing
SLF4J binding warnings. The server was stopped cleanly after verification.

**Interactive client smoke (not performed)**: steps 1-6 of the runbook (two
logged-in players, camera/lock observation, skeleton phase, barrier
collision, exact rewards and pickup, cleanup re-entry) require an interactive
GUI session with two logged-in accounts and manual observation. This
environment has no interactive operator, so the client was not launched and
no step results, screenshots, or per-player observations exist. This is a
recorded limitation — the automated `ScriptBossProductionE2ETest` covers the
same flow through real packet decoders, script ticks, the production NPC
death loop, and opcode-236 pickup, and the automated gates remain the
acceptance evidence.

**Maintainer acknowledgement (2026-08-02)**: The interactive client runbook
steps 1-6 were not performed and are not claimed as passed. Automated gates
(`ScriptBossProductionE2ETest`, full JDK 17 reactor, `./scripts/build.sh`,
and the server-boot smoke) remain the acceptance evidence. A human operator
may still run steps 1-6 later and append results here; until then the live
client smoke stays an explicit recorded limitation.
