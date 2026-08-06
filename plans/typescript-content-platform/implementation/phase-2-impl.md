---
type: planning
entity: implementation-plan
plan: "typescript-content-platform"
phase: 2
status: completed
created: "2026-07-29"
updated: "2026-07-29"
---

# Implementation Plan: Phase 2 - Scheduling and Lifecycle

> Implements [Phase 2](../phases/phase-2.md) of [TypeScript Content Platform](../plan.md)

## Approach

Build a script-owned scheduler on the existing game-cycle executor, but keep guest callbacks behind generation-aware Java task objects. Add an event registry for bounded lifecycle names and dispatch from existing authoritative engine transitions. Cleanup will invalidate all tasks for the replaced script generation and all player-owned tasks on logout.

## Affected Modules

| Module | Change Type | Description |
|--------|-------------|-------------|
| `com.rs2.script` | create/modify | Scheduler, generation token, lifecycle registrations, contexts, bindings. |
| Game cycle/player/NPC/item paths | modify | Lifecycle dispatch and cleanup at existing transitions. |
| `content/src/core` | modify/create | Typed scheduling and event API. |
| Tests/docs/examples | modify/create | Ownership, exception, reload, and user-flow coverage. |

## Required Context

| File | Why |
|------|-----|
| `engine/server/src/main/java/com/rs2/event/CycleEventHandler.java` | Existing game-tick execution/cancellation primitive. |
| `engine/server/src/main/java/com/rs2/script/ScriptHost.java` | Context replacement and reload cleanup boundary. |
| `engine/server/src/main/java/com/rs2/game/players/PlayerHandler.java` | Login completion and disconnect removal transitions. |
| `engine/server/src/main/java/com/rs2/game/players/Player.java` | Logout/destruct lifecycle and player process state. |
| `engine/server/src/main/java/com/rs2/game/npcs/NpcHandler.java` | NPC death/drop transition. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/PickupItem.java` | Item-pickup validated behavior. |

## Implementation Steps

### Step 1: Introduce generation-owned scheduling

- **What**: Create cancellable one-shot/repeating task handles and a script generation owner.
- **Where**: New `com.rs2.script.scheduler` classes plus `ScriptHost` commit/cleanup.
- **Why**: Guest `Value` callbacks must never run after their context is closed.
- **Considerations**: Minimum delay one tick; bounded repeat interval; stop on exceptions; idempotent cancel.

### Step 2: Expose player-scoped scheduler capabilities

- **What**: Add `after(ticks, fn)` and `every(ticks, fn)` through a safe scheduler facade/handle.
- **Where**: `ScriptedPlayer`, bindings/types.
- **Why**: Dialogues, skilling loops, encounters, and cutscenes require time.
- **Considerations**: Player disconnect cancels owned work; callbacks receive current safe player context.

### Step 3: Add lifecycle registration and exact dispatch

- **What**: Support login, logout, NPC death, item pickup, and named area enter/leave events.
- **Where**: Aggregate registry, lifecycle service, and authoritative engine transitions.
- **Why**: Content needs reactive entry points outside click packets.
- **Considerations**: Snapshot handlers during dispatch; no unrestricted global `onTick`.

### Step 4: Verify cleanup and containment

- **What**: Add reload/logout/error/order tests and representative content.
- **Where**: Script host/scheduler tests, dispatch tests, content examples, docs.
- **Why**: Asynchronous stale callbacks are the primary risk.
- **Considerations**: Fake or directly process cycle events in tests; do not use wall-clock sleeps.

## Testing Plan

| Test Type | What to Test | Expected Outcome |
|-----------|-------------|-----------------|
| Scheduler unit | due ticks, repeat, cancel, callback throw | Deterministic execution and cleanup. |
| Reload integration | tasks from old/candidate contexts | Only committed current generation can execute. |
| Lifecycle dispatch | login/logout/death/pickup/area transitions | Exact events run once with safe contexts. |
| Full gate | TypeScript build and Maven suite | No regressions. |

Primary verify command:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)" && export PATH="$JAVA_HOME/bin:$PATH" && pnpm build:content && mvn -B -f engine/pom.xml test
```

### Test Integrity Constraints

- `ScriptHostTest` must retain all last-known-good and sandbox assertions and gain stale-task coverage.
- Existing packet and callback exception tests must remain semantically unchanged.
- Tests must drive cycle counts deterministically rather than weakening timing guarantees.

## Rollback Strategy

Remove lifecycle dispatch points and scheduler bindings, then stop script-owned events by their dedicated owner/generation without touching unrelated cycle events.

## Open Decisions

| Decision | Options | Chosen | Rationale |
|----------|---------|--------|-----------|
| Scheduling scope | global / player-owned plus explicit system owner | player-owned first | Natural logout cleanup and smaller blast radius. |
| Repeating callback error | continue / stop | stop and log | Prevents persistent failure spam. |

## Reality Check

### Code Anchors Used

| File | Symbol/Area | Why it matters |
|------|-------------|----------------|
| `CycleEventHandler.java` | `addEvent`, `stopEvents`, `process` | Game-thread scheduler foundation. |
| `ScriptHost.java` | `replaceContext` | Atomic point to invalidate old generation. |
| `PlayerHandler.java` | login/disconnect processing | Lifecycle transitions already centralized. |
| `NpcHandler.java` | `dropItems`/death process | Stable NPC-death dispatch region. |

### Mismatches / Notes

- `CycleEventHandler` uses unsynchronized mutable lists and identity ownership; the script adapter must only mutate it from game-thread paths or add a safe queue rather than claiming thread safety.
- Player login has multiple initialization branches; the exact “fully usable” point must be proven with a dispatch test before wiring.
- Implementation uses a dedicated Java-owned scheduler invoked by the production game-cycle seam instead of storing guest `Value` callbacks in `CycleEventHandler`.
- Scheduler invocation and lifecycle dispatch use the ScriptHost generation lease with host-to-scheduler/lifecycle lock ordering. Rework added per-task claim revalidation so same-tick cancellation and logout/generation invalidation win before callback start.
- Production integration tests use narrow package-private PlayerHandler and GameEngine methods called by the live branches; NPC death drives the actual `NpcHandler.process` state machine and pickup drives `Packet -> CycleEventHandler -> ItemHandler/GlobalDropsHandler`.
- The project-pinned pnpm 11.13.1 could not self-bootstrap without registry signature access. Verification used the installed local TypeScript compiler (the content build script is exactly `tsc`) followed by the full Java 17 Maven suite.
