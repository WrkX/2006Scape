---
type: planning
entity: phase
plan: "typescript-content-platform"
phase: 2
status: completed
created: "2026-07-29"
updated: "2026-07-29"
---

# Phase 2: Scheduling and Lifecycle

> Part of [TypeScript Content Platform](../plan.md)

## Objective

Add game-tick scheduling and high-value player/world lifecycle hooks with strict ownership, cancellation, and reload safety.

## Scope

### Includes

- Player-scoped `after`, `every`, and cancellation handles.
- Login, logout, NPC death, pickup, and bounded area enter/leave events.
- Context-generation ownership, logout cleanup, callback error isolation, and diagnostics.

### Excludes (deferred to later phases)

- Persistent script state, encounter spawning, and a global unrestricted per-tick callback.

## Prerequisites

- [x] Phase 1 is complete.

## Deliverables

- [x] Scheduler and lifecycle registries/facades.
- [x] Engine dispatch integration and cleanup.
- [x] Typed TypeScript APIs, examples, tests, and docs.

## Acceptance Criteria

- [x] Tasks run on game ticks, can cancel themselves, and cannot outlive reload/logout ownership.
- [x] Exact lifecycle registrations run once and do not expose raw engine values.
- [x] Throwing callbacks do not stop the game loop or other callbacks.

## Dependencies on Other Phases

| Phase | Relationship | Notes |
|-------|-------------|-------|
| Phase 1 | blocked-by | Reuses safe wrapper and registry conventions. |
| Phase 3 | blocks | Quest steps need delayed and lifecycle behavior. |

## Notes

Do not expose general Java timers or threads.

Independent review accepted the phase with the strict TypeScript build and 55 Java tests passing. A reload-first concurrency branch remains a worthwhile additional regression test, and a live client/server lifecycle smoke test remains recommended before release.
