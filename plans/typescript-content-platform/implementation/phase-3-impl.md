---
type: planning
entity: implementation-plan
plan: "typescript-content-platform"
phase: 3
status: completed
created: "2026-07-29"
updated: "2026-07-29"
---

# Implementation Plan: Phase 3 - Persistent State and Quests

> Implements [Phase 3](../phases/phase-3.md) of [TypeScript Content Platform](../plan.md)

## Approach

Add a Java-owned namespaced primitive state store to `Player`, serialize it as a versioned encoded payload in the existing save format, and expose it through a capability facade. Build a quest runtime that validates definitions at load, reads/writes quest state through reserved namespaces, and evaluates requirements/rewards through explicit adapters.

## Affected Modules

| Module | Change Type | Description |
|--------|-------------|-------------|
| Player/save system | modify/create | Versioned namespaced script-state storage and persistence. |
| `com.rs2.script.quest` | create | Definition parser/validator and functional quest service. |
| `ScriptedPlayer` | modify | State and quest capability facades. |
| TypeScript quest SDK | modify | Runtime-aware quest steps, requirements, rewards, migrations. |

## Required Context

| File | Why |
|------|-----|
| `engine/server/src/main/java/com/rs2/game/players/Player.java` | Player-owned persisted fields. |
| `engine/server/src/main/java/com/rs2/game/players/PlayerSave.java` | Current line-based load/save format. |
| `engine/server/src/main/java/com/rs2/script/registries/QuestRegistry.java` | Current data-only guest definitions. |
| `content/src/quests/types.ts` | Existing declarative quest schema. |
| `content/src/quests/quest-builder.ts` | Existing author-facing validation/builder. |
| `content/src/quests/dragon-awakens.ts` | Representative definition to convert into gameplay. |

## Implementation Steps

### Step 1: Add bounded namespaced primitive state

- **What**: Store/get/remove boolean, number, and string values with namespace/key/value/count limits.
- **Where**: New player script-state model and `ScriptedPlayer` facade.
- **Why**: Content needs durable progress without adding Java fields per feature.
- **Considerations**: Reserved namespaces, finite numbers only, defensive snapshots, explicit defaults.

### Step 2: Persist a versioned payload

- **What**: Encode/decode script state as one backwards-compatible save entry with schema versioning.
- **Where**: `PlayerSave` parse/write and a dedicated codec.
- **Why**: Avoid multiplying legacy save keys and allow migrations.
- **Considerations**: Atomic parse, malformed payload quarantine/logging, deterministic output, payload limit.

### Step 3: Consume quest definitions

- **What**: Parse committed definitions into Java-owned descriptors; expose start/stage/complete/status and requirement/reward execution.
- **Where**: Quest runtime/service, `QuestRegistry`, player facade, TypeScript quest types.
- **Why**: Registered quests must affect gameplay.
- **Considerations**: Never retain derived Java state referring to closed guest values; callback-bearing definitions remain generation-bound.

### Step 4: Implement a complete TypeScript quest

- **What**: Connect NPC/object/item handlers, dialogue, state, requirements, journal, and rewards for one multi-stage quest.
- **Where**: `content/src/quests` and loader.
- **Why**: End-to-end proof that quest-specific Java edits are unnecessary.
- **Considerations**: Idempotent rewards and migration from any existing example state.

## Testing Plan

| Test Type | What to Test | Expected Outcome |
|-----------|-------------|-----------------|
| Codec/unit | round-trip, malformed, limits, version migration | Safe deterministic persistence. |
| Quest/unit | transitions, requirements, idempotent completion/reward | Correct durable state. |
| Integration | save/load and full scripted quest flow | Progress survives relog/reload. |
| Full gate | TypeScript build and Maven suite | No regressions. |

Primary verify command:

```bash
content/node_modules/.bin/tsc -p content/tsconfig.json &&
export JAVA_HOME="$(/usr/libexec/java_home -v 17)" &&
export PATH="$JAVA_HOME/bin:$PATH" &&
mvn -B -f engine/pom.xml test
```

### Test Integrity Constraints

- Existing player save/load fixtures must remain readable.
- No legacy quest fields may be removed or repurposed in this phase.
- Reward tests must assert duplicate completion cannot duplicate items/XP/points.

## Rollback Strategy

Readers ignore the new save key when the feature is absent; remove runtime access while leaving stored payloads intact for recoverability.

## Open Decisions

| Decision | Options | Chosen | Rationale |
|----------|---------|--------|-----------|
| State values | arbitrary JSON / primitives | bounded primitives | Easy validation, stable persistence, no host-object ambiguity. |
| Save representation | many keys / one versioned encoded payload | one payload | Namespace/version evolution without parser sprawl. |

## Reality Check

### Code Anchors Used

| File | Symbol/Area | Why it matters |
|------|-------------|----------------|
| `PlayerSave.java` | token switch and character writer | Backward-compatible persistence insertion points. |
| `Player.java` | fixed quest fields | Shows why general namespaced state is required. |
| `QuestRegistry.java` | data-only `Value` storage | No runtime consumer exists yet. |
| `quest-builder.ts` | author validation | Existing SDK can be evolved rather than replaced. |

### Mismatches / Notes

- The save format is line-oriented and hand-parsed; the new payload needs escaping/encoding that cannot introduce newlines or delimiter ambiguity.
- Existing `QuestDefinition` callbacks are guest values owned by a context; persistent state may survive reload but callbacks must not.

## Implementation Outcome

- Added a bounded Java-owned primitive state store, strict deterministic
  `v1.<base64url>` codec, explicit version-decoder seam, atomic character-file
  replacement, and durable malformed-state quarantine.
- Added immutable Java-owned quest descriptors, candidate-only
  dependency/cycle validation, nullable stage and result-shaped bridge
  contracts, atomic fixed-XP rewards, and independent best-effort presentation
  refresh.
- Reworked Dragon Awakens around consumed Chronozon/green-dragon spawn data,
  guaranteed dragon-bone drops, a cache-backed altar, real dialogue/button
  dispatch, retryable reward claiming, and persisted progress.
- Replaced the helper-level quest integration with a production-path E2E
  crossing NPC/dialogue/button packets, ground pickup, item-on-object Region
  validation, the NPC death/drop state machine, rejected/successful reloads,
  and real `PlayerSave` round trips.
- Final gate: TypeScript passed; Maven passed 92 tests with zero failures,
  errors, or skips. Independent review accepted the phase with non-blocking
  follow-up.

## Deviations and Follow-ups

- The working quest uses reachable Wilderness content instead of the
  unconsumed aspirational Dragon Island definition.
- Reward claiming occurs explicitly at final stage so every preflight or
  mutation failure remains retryable.
- Scripted objectives are stored but not yet consumed by a generic client quest
  journal. That author-facing surface is tracked in Phase 5.
- Exhaustive codec/parser byte-boundary cases and a historical migration
  through `PlayerSave` are tracked as Phase 5 hardening.
