---
type: planning
entity: phase
plan: "typescript-content-platform"
phase: 1
status: completed
created: "2026-07-29"
updated: "2026-07-29"
---

# Phase 1: Interaction and Player Foundation

> Part of [TypeScript Content Platform](../plan.md)

## Objective

Make ordinary item-driven content and useful commands implementable in TypeScript, while establishing accurate safe player/item contracts that later systems can reuse.

## Scope

### Includes

- First/second/third inventory item clicks.
- Item-on-item, item-on-object, and item-on-NPC handlers.
- Rich command context containing command name, raw input, arguments, and player privilege level.
- Accurate `ScriptedItem`, inventory, bank, skill, animation, graphic, sound, and basic interface primitives.
- Registration validation, duplicate diagnostics, authoritative dispatch, tests, examples, and documentation.

### Excludes (deferred to later phases)

- Delayed/repeating tasks and login/logout/death/area lifecycle events.
- Namespaced persistent variables and functional quest progression.
- NPC spawning, world-object mutation, combat orchestration, and drop execution.
- Full declarative boss/raid/area runtime consumption.

## Prerequisites

- [x] Hardened bridge reload, sandbox, handler authority, and exception containment are present.
- [x] Java 17 and the content TypeScript build are available.

## Deliverables

- [x] Item interaction registries and Java bridge globals.
- [x] Authoritative packet/use-item integration for supported item routes.
- [x] Typed item and command contexts.
- [x] Expanded, truthful safe player utilities.
- [x] Representative TypeScript item/command content.
- [x] Java tests, TypeScript build verification, and updated bridge docs.

## Acceptance Criteria

- [x] An exact scripted item registration runs once and suppresses the corresponding legacy event/behavior.
- [x] An unmatched item route retains existing legacy behavior.
- [x] Pair registrations are order-insensitive where the game interaction is symmetric.
- [x] Invalid handler actions/IDs/callbacks are rejected without corrupting active state.
- [x] Command callbacks receive lossless raw input and immutable argument access.
- [x] Player wrapper mutations validate ranges and report meaningful success values.
- [x] All pre-existing and new tests pass with the content build.

## Dependencies on Other Phases

| Phase | Relationship | Notes |
|-------|-------------|-------|
| Bridge Hardening | blocked-by | Completed predecessor plan supplies transactional registries and safe dispatch. |
| Phase 2 | blocks | Scheduling callbacks reuse the context and player capabilities established here. |
| Phase 3 | blocks | Quest content needs complete interaction coverage. |

## Notes

Item routes are intentionally exact-ID registrations in this phase. Predicate/wildcard handlers remain deferred until precedence and performance rules can be designed safely.

Independent review accepted the phase with 43 Java tests plus the strict TypeScript build passing. A live client/server smoke test remains recommended before release but is not an automated acceptance blocker.
