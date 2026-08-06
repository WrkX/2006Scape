---
type: planning
entity: implementation-plan
plan: "typescript-content-platform"
phase: 1
status: completed
created: "2026-07-29"
updated: "2026-07-29"
---

# Implementation Plan: Phase 1 - Interaction and Player Foundation

> Implements [Phase 1](../phases/phase-1.md) of [TypeScript Content Platform](../plan.md)

## Approach

Extend the existing aggregate registry transaction with exact item and pair-handler maps, expose validated registration functions as Graal globals, and dispatch them at the last common validated point before legacy events/behavior. Reuse `ScriptContext` for target actions, add specialized immutable command/item-pair context wrappers for lossless metadata, and expand `ScriptedPlayer` only through explicit, range-checked capabilities whose TypeScript declarations match their real Java return values.

## Affected Modules

| Module | Change Type | Description |
|--------|-------------|-------------|
| `engine/server/com.rs2.script` | modify/create | Item registries, contexts, bindings, wrapper capabilities, and guarded execution. |
| `engine/server` packet/item handlers | modify | Authoritative exact-route dispatch after packet validation. |
| `content/src/core` | modify/create | Exact runtime contracts and item registration globals. |
| `content/src/examples` | modify/create | Compiled examples using command metadata and item interactions. |
| `docs/SCRIPT_BRIDGE.md` | modify | Document the public runtime and dispatch/compatibility rules. |

## Required Context

| File | Why |
|------|-----|
| `engine/server/src/main/java/com/rs2/script/registries/RegistryStore.java` | Atomic candidate/active state all new registries must join. |
| `engine/server/src/main/java/com/rs2/script/ScriptFunctions.java` | Existing registration validation facade. |
| `engine/server/src/main/java/com/rs2/script/ScriptBindings.java` | Explicit Graal global installation. |
| `engine/server/src/main/java/com/rs2/script/ScriptedPlayer.java` | Safe mutable player capability boundary. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/ClickItem.java` | First item-click validation and legacy behavior entry. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/ItemClick2.java` | Second item-click packet path. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/ItemClick3.java` | Third item-click packet path. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/ItemOnItem.java` | Validated symmetric item-pair path. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/ItemOnObject.java` | Validated item/object route and legacy event timing. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/ItemOnNpc.java` | Validated item/NPC route and entity resolution. |
| `engine/server/src/main/java/com/rs2/net/packets/impl/Commands.java` | Command parsing and authoritative script dispatch. |
| `content/src/core/runtime.ts` | Runtime declaration source of truth. |
| `docs/SCRIPT_BRIDGE.md` | Existing bridge contract and constraints. |

## Implementation Steps

### Step 1: Add item registries and validated globals

- **What**: Store exact item actions and canonical pair handlers in candidate/active registry state; bind `onItem`, `onItemOnItem`, `onItemOnObject`, and `onItemOnNpc`.
- **Where**: `RegistryStore`, new registry classes, `ScriptFunctions`, and `ScriptBindings`.
- **Why**: Item routes are the largest immediate blocker to TypeScript quest and skill content.
- **Considerations**: Reject negative IDs/non-executable callbacks; canonicalize symmetric item pairs; avoid wildcard precedence in this phase.

### Step 2: Add accurate invocation context wrappers

- **What**: Add item slot/target metadata, immutable command name/raw input/arguments/rights, and item definition wrappers.
- **Where**: `com.rs2.script` context/wrapper classes and `content/src/core/runtime.ts`.
- **Why**: Content needs lossless invocation data without raw packets or engine objects.
- **Considerations**: Defensive copies for arrays; unknown definitions must not throw; keep ordinal `action` values stable.

### Step 3: Integrate authoritative dispatch

- **What**: Call exact handlers after each packet's ownership/existence/distance validation and return before legacy event posting or behavior.
- **Where**: `ClickItem`, `ItemClick2`, `ItemClick3`, `ItemOnItem`, `ItemOnObject`, `ItemOnNpc`, and `Commands`.
- **Why**: Exact registrations must be a safe replacement seam rather than double-running Java content.
- **Considerations**: Unmatched registrations follow the original path unchanged; item-on-item lookup is symmetric while context preserves used/target order.

### Step 4: Expand safe player primitives

- **What**: Add base-level/XP/add-XP, capacity/free-space, boolean transactional removal, animation, graphic, sound, interface close/show, and rights access.
- **Where**: `ScriptedPlayer` and `content/src/core/runtime.ts`.
- **Why**: These are common content primitives and avoid content-specific Java changes.
- **Considerations**: Validate IDs, amounts, skill indices, interface/animation ranges; do not expose `PacketSender`.

### Step 5: Add examples, tests, and documentation

- **What**: Add TypeScript content exercising APIs, unit/dispatch tests for authority/fallback/contexts/validation, and update the bridge contract.
- **Where**: `content/src/examples`, `engine/server/src/test`, and `docs/SCRIPT_BRIDGE.md`.
- **Why**: Prevent declaration/runtime drift and document the migration seam.
- **Considerations**: Tests must not weaken existing hardening coverage.

## Testing Plan

| Test Type | What to Test | Expected Outcome |
|-----------|-------------|-----------------|
| Registry/unit | Exact and symmetric lookup, staging isolation, invalid registrations | Correct active state and safe rejection. |
| Packet/dispatch | Script match authority and unmatched legacy fallthrough across item routes | Script runs once only for exact registration. |
| Wrapper/unit | Item metadata, command copies, skills/inventory validation and return contracts | Truthful safe values and mutations. |
| Integration | Build representative TypeScript and run full Maven suite | No declaration errors or Java regressions. |

Primary verify command:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)" && export PATH="$JAVA_HOME/bin:$PATH" && pnpm build:content && mvn -B -f engine/pom.xml test
```

### Test Integrity Constraints

- Existing `RegistryStoreTest` must continue proving atomic candidate commit/rollback and will be extended rather than weakened.
- Existing `ScriptHostTest` sandbox/reload coverage must remain unchanged in meaning.
- Existing `ClickDispatchTest`, `CommandsTest`, `ScriptExecutorTest`, and dialogue tests must keep all current assertions.
- No legacy packet behavior may be removed merely to simplify test construction.

## Rollback Strategy

Remove new bindings/maps/wrappers and their exact dispatch calls together; unmatched legacy behavior remains structurally present, so rollback does not require reconstructing deleted content paths.

## Open Decisions

| Decision | Options | Chosen | Rationale |
|----------|---------|--------|-----------|
| Pair matching | ordered / symmetric | symmetric registration with invocation order preserved | Normal item-on-item content should work regardless of selection order. |
| Handler precedence | wildcard + exact / exact only | exact only | Deterministic and safe for the first interaction expansion. |
| API context | overload generic context / specialized immutable wrappers | specialized wrappers extending the stable shape | Preserves `player/target/action` ergonomics while exposing accurate metadata. |

## Reality Check

### Code Anchors Used

| File | Symbol/Area | Why it matters |
|------|-------------|----------------|
| `RegistryStore.java` | `State`, `beginStaging`, `commit` | Every guest `Value` must publish atomically with its owning context. |
| `ScriptBindings.java` | `install` | Host access is explicit; unbound helpers do not exist at runtime. |
| `ClickItem.java` | post-validation `ItemFirstClickEvent` | Authoritative dispatch belongs immediately before the legacy event. |
| `ItemOnObject.java` | distance/inventory checks then event | Existing validation can be retained before script dispatch. |
| `ItemOnNpc.java` | entity/slot checks | NPC must be resolved defensively before wrapping. |
| `ItemOnItem.java` | slot-derived IDs | Invocation order and both slots are available before `UseItem`. |
| `PlayerAssistant.java` | `addSkillXP` | Existing XP semantics can be adapted behind a safe wrapper. |

### Mismatches / Notes

- `ScriptedItem` currently assumes every `ItemDefinition.lookup(id)` is non-null; the phase must make metadata lookup defensive.
- `ItemOnNpc` reads `NpcHandler.npcs[i].npcType` before checking index/entity validity and checks `player == null` after dereferencing it. The dispatch integration must repair that validation order without changing matched legacy semantics.
- Existing inventory removal returns `void`; the wrapper must preflight available quantity and return a truthful boolean rather than mirror the weak legacy signature.
- Implementation review required registration violations to become load-fatal and duplicate insertion to remain first-wins so transactional reload protects the complete previous state.
- Truthful XP success required a focused change in `PlayerAssistant.addSkillXP`: calculate the effective rated gain and enforce the 200M cap before mutation.
- Helper-only dispatch tests were insufficient; the accepted implementation adds encoded `Packet` fixtures that exercise all six production `processPacket` entry points.
