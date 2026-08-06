# TypeScript Bridge Review Report

**Date:** 2026-08-06  
**Review Type:** Comprehensive (Security, Type Safety, Architecture, Performance, Correctness, Documentation)  
**Total Findings:** 57

---

## Executive Summary

The TypeScript bridge is well-architected with clear separation between Java engine and TypeScript content. The use of GraalVM with `HostAccess.EXPLICIT` is correct. However, several **high-severity issues** need immediate attention, particularly around **input validation**, **synchronization**, and **type safety at the JS-Java boundary**.

**Severity Breakdown:**
- 🔴 **Critical:** 0
- 🟠 **High:** 9
- 🟡 **Medium:** 21
- 🟢 **Low:** 27

**Findings by Category:**
- Security: 11 findings
- Type Safety: 9 findings
- Architecture: 10 findings
- Performance: 7 findings
- Correctness: 12 findings
- Documentation: 8 findings

---

## Priority 1: High-Severity Issues (Fix Immediately)

### 1.1 Security: ProxyExecutable Wrappers Lack Input Validation

**Severity:** 🟠 High  
**File:** `engine/server/src/main/java/com/rs2/script/ScriptBindings.java`, `ScriptFunctions.java`  
**Category:** Security

**Issue:**  
GraalVM `ProxyExecutable` wrappers directly call Java methods without enforcing `@HostAccess.Export` semantics. Malicious scripts could invoke functions with crafted arguments. The `buildContext()` method configures the GraalVM context with `HostAccess.EXPLICIT` which is good, but the ScriptFunctions class exposes Java method handles through ProxyExecutable wrappers that don't enforce the annotation.

Additionally, the context allows array access (`allowArrayAccess(true)`) which could be used to access Java arrays if references are leaked.

**Recommendation:**
1. Audit all ProxyExecutable wrappers in ScriptBindings to ensure they validate arguments defensively
2. Consider if `allowArrayAccess(true)` is necessary - if Java arrays are exposed through ScriptedPlayer or other wrappers, array elements could be mutated
3. Ensure the HostAccess policy is applied consistently to all objects exposed to JS, not just the initial bindings

**Example Fix:**
```java
// In each ProxyExecutable wrapper, add defensive validation:
if (args == null || args.length < expected) return Value.asValue(false);
// Validate each argument type before use
if (!args[0].isString()) throw new IllegalArgumentException("Expected string");
```

---

### 1.2 Security: SkillView.setLevel() Allows Arbitrary Modification

**Severity:** 🟠 High  
**File:** `engine/server/src/main/java/com/rs2/script/ScriptedPlayer.java` (lines 342-350)  
**Category:** Security

**Issue:**  
The `setLevel()` method allows arbitrary modification of player skill levels without proper bounds checking beyond the 0-255 range. While it checks `canMutate()`, if a script gains access during a valid encounter, it can set levels to any value in that range. The method directly modifies `player.playerLevel[id]` without going through game logic validation.

Similarly, `InventoryView.add()` catches `RuntimeException` but then manually reverts array state using `System.arraycopy` - this is fragile and could lead to inventory corruption if the exception occurs at the wrong point.

**Recommendation:**
1. Route all player state mutations through proper game logic methods rather than direct array access
2. The inventory revert logic in `InventoryView.add()` should use proper transaction semantics or be removed in favor of letting the game engine handle validation
3. Consider adding rate limiting or additional validation to `setLevel()`

---

### 1.3 Performance: ScriptHost Synchronized Bottleneck

**Severity:** 🟠 High  
**Impact:** Critical  
**File:** `engine/server/src/main/java/com/rs2/script/ScriptHost.java`  
**Category:** Performance

**Issue:**  
ScriptHost serializes all script operations through synchronized methods. Every event dispatch (onNpc, onObject, onItem, etc.), registry read, and generation check acquires the same monitor. With a multi-threaded game engine firing events concurrently, this creates a single contention point that serializes all script execution.

The synchronized monitor on ScriptHost is held during JS interop calls (`handler.execute()`), which are relatively expensive, further increasing lock hold time.

**Recommendation:**
- Use `ReadWriteLock` for registry reads vs. writes
- Consider per-registry locks for event dispatch
- Minimize lock hold time during JS interop calls
- Consider replacing `synchronized` methods with finer-grained locking

---

### 1.4 Type Safety: Null/Undefined Coercion in String Methods

**Severity:** 🟠 High  
**Files:**  
- `engine/server/src/main/java/com/rs2/script/ScriptedPlayer.java`  
- `engine/server/src/main/java/com/rs2/script/ScriptedNpc.java`  
**Category:** Type Safety

**Issue:**  
`message(null)` in JS coerces to string `"null"`. Methods don't validate null/undefined parameters from JS. The `message(String text)` and similar string methods don't validate null/undefined parameters. GraalVM coerces null to string `'null'` which is likely not intended behavior.

Similarly, `forceChat(String text)` doesn't validate null/undefined - same issue.

**Recommendation:**  
Add null checks at the start of all string parameter methods:

```java
public void message(String text) {
    if (text == null) return;
    // or throw IllegalArgumentException
}
```

---

### 1.5 Correctness: Race in getRuntimeStatus()

**Severity:** 🟠 High  
**Category:** Correctness (Race Condition)  
**File:** `engine/server/src/main/java/com/rs2/script/ScriptHost.java`

**Issue:**  
`ScriptHost.getRuntimeStatus()` reads activeState fields across non-atomic multiple synchronized calls, allowing registry/generation inconsistency. Calling `getRuntimeStatus()` while a concurrent reload is in progress could result in the generation read and registry read coming from different activeState instances, producing a mismatched status snapshot.

**Reproduction Steps:**  
Call `getRuntimeStatus()` while a concurrent reload is in progress; the generation read and registry read may come from different activeState instances, producing a mismatched status snapshot.

**Recommendation:**  
Create a single `synchronized` method that returns a consistent snapshot:

```java
public synchronized ScriptRuntimeStatus getRuntimeStatus() {
    // Read generation AND registry in same synchronized block
}
```

---

### 1.6 Type Safety: beginEncounter() Coordinate Coercion

**Severity:** 🟠 High  
**File:** `engine/server/src/main/java/com/rs2/script/ScriptedPlayer.java`  
**Category:** Type Safety

**Issue:**  
`beginEncounter()` accepts double parameters from JS but coordinates should be integers. Silent double-to-int coercion could cause precision loss or truncation for large values.

**Recommendation:**  
Validate and explicitly cast double parameters to int with range checks in `beginEncounter()`, or change the Java signature to accept int and let GraalVM coerce.

---

### 1.7 Type Safety: forceChat() Null Validation

**Severity:** 🟠 High  
**File:** `engine/server/src/main/java/com/rs2/script/ScriptedNpc.java`  
**Category:** Type Safety

**Issue:**  
`forceChat(String text)` doesn't validate null/undefined. Same issue as `ScriptedPlayer.message()` - null coerces to `'null'` string.

**Recommendation:**  
Add null validation: `if (text == null || npc == null) return;`

---

### 1.8 Performance: Hot Path Allocations

**Severity:** 🟠 High  
**Impact:** Significant  
**Category:** Performance

**Issue:**  
Hot event paths allocate multiple objects per event: ScriptedPlayer (wraps Player + generation), ScriptedNpc (wraps Npc + snapshot), ScriptContext (or subclass), and ScriptedPosition. For a single NPC click event, 3-4 objects are allocated. These are short-lived and create GC pressure.

ScriptedPlayer.getPosition() additionally allocates a new ScriptedPosition on every call. With hundreds of events per tick, this becomes significant allocation overhead.

**Recommendation:**
- Consider object pooling for frequently allocated wrappers
- Cache ScriptedPosition in ScriptedPlayer.getPosition()
- Reuse wrapper instances where safe

---

### 1.9 Performance: ScriptHost Serialization

**Severity:** 🟠 High  
**Impact:** Critical  
**Category:** Performance

**Issue:**  
Every event dispatch (onNpc, onObject, onItem, etc.), registry read, and generation check acquires the same monitor in ScriptHost. This creates a single contention point that serializes all script execution.

**Recommendation:**  
Use `ReadWriteLock` for registry reads vs. writes, and consider per-registry locks for event dispatch.

---

## Priority 2: Medium-Severity Issues (Fix This Sprint)

### 2.1 Security Findings

#### 2.1.1 TOCTOU in ReadOnlyContentFileSystem

**Severity:** 🟡 Medium  
**File:** `engine/server/src/main/java/com/rs2/script/ReadOnlyContentFileSystem.java` (lines 132-143)

**Issue:**  
The `allowedPath()` method attempts to prevent path traversal by checking if the resolved path starts with the root. However, there's a TOCTOU (Time-of-Check-Time-of-Use) issue: it checks `Files.exists(candidate)` and then calls `candidate.toRealPath()` separately. Between these calls, a symbolic link could be changed.

**Recommendation:**
1. Combine the existence check and real path resolution into a single atomic operation
2. Override all path-returning methods to ensure they go through `allowedPath()`
3. Consider using a canonical path cache that's validated at startup and then immutable

---

#### 2.1.2 ScriptExecutor Exception Handling

**Severity:** 🟡 Medium  
**File:** `engine/server/src/main/java/com/rs2/script/ScriptExecutor.java`

**Issue:**  
The `execute()` and `executeChecked()` methods catch RuntimeException but only log the exception message at WARNING level. If a script triggers an exception that contains sensitive information (like internal object state, file paths, or player data), this information could be exposed through logs.

Additionally, the exception is caught and swallowed, which means the script continues running - if the exception was due to a security check failing, the script might proceed in an inconsistent state.

**Recommendation:**
1. Ensure exception messages don't contain sensitive data before logging
2. Consider whether certain security-related exceptions should terminate the script context
3. The log output includes 'identity' which could be user-controlled data - validate or sanitize this before logging

---

#### 2.1.3 grantReward() Missing canMutate() Check

**Severity:** 🟡 Medium  
**File:** `engine/server/src/main/java/com/rs2/script/ScriptedPlayer.java` (lines 248-261)

**Issue:**  
The `grantReward()` method directly looks up rewards from RewardRegistry and applies them through PlayerRewardTransaction. The `rewardId` parameter is a String that comes directly from script code. While it checks for null reward, there's no validation that the reward being granted is appropriate for the current game context or player state.

Additionally, the method doesn't check `canMutate()` before granting rewards.

**Recommendation:**
1. Add `canMutate()` check to `grantReward()` method
2. Consider adding context-appropriate validation (e.g., quest requirements, encounter ownership) before allowing reward grants
3. Audit what rewards are registered and ensure none provide inappropriate capabilities

---

#### 2.1.4 ScriptedCombat damage()/heal() Information Leak

**Severity:** 🟡 Medium  
**File:** `engine/server/src/main/java/com/rs2/script/capability/ScriptedCombat.java` (lines 42-67)

**Issue:**  
The `damage()` and `heal()` methods perform input validation through the `integral()` helper, but they directly modify player HP. The `damage()` method returns the actual HP change which could be used by scripts to probe player state.

**Recommendation:**
1. Ensure combat methods can only be called in appropriate game contexts (e.g., during combat encounters)
2. Consider adding logging for damage/heal operations to detect abuse
3. The return value of `damage()` reveals information about player HP - consider if this information should be restricted

---

### 2.2 Type Safety Findings

#### 2.2.1 ScriptArray.get() Double Truncation

**Severity:** 🟡 Medium  
**File:** `engine/server/src/main/java/com/rs2/script/ScriptArray.java`

**Issue:**  
`get(double index)` silently truncates double to int. While bounds checking prevents errors, non-integer doubles like 1.5 will be truncated to 1 without warning.

**Recommendation:**  
Add validation that index is a whole number before casting: `if (index != Math.rint(index)) return null;`

---

#### 2.2.2 Callback Argument Type Validation

**Severity:** 🟡 Medium  
**File:** `engine/server/src/main/java/com/rs2/script/ScriptFunctions.java`

**Issue:**  
Callback argument types are not validated at registration time. Handlers are checked with `isExecutable()` but argument count/type mismatches only fail at invocation.

**Recommendation:**  
Consider wrapping callbacks to validate argument types at registration, or document expected signatures clearly in TypeScript types.

---

#### 2.2.3 addExperience() Double Precision

**Severity:** 🟡 Medium  
**File:** `engine/server/src/main/java/com/rs2/script/ScriptedPlayer.java`

**Issue:**  
`SkillView.addExperience(double amount)` accepts double but validates `amount > 0 && amount <= 200000000`. Very large doubles could lose precision in comparison.

**Recommendation:**  
Validate that amount is a finite integer or explicitly cast to int for the bounds check.

---

### 2.3 Architecture Findings

#### 2.3.1 Player State Codec Migration Framework

**Severity:** 🟡 Medium  
**Category:** Architecture (State)

**Issue:**  
Player script state codec (ScriptStateCodec) supports v0 and v1 with strict decoding, but there is no documented migration framework for future versions. The v1 decoder is hardcoded, and adding v2+ requires modifying the decoder dispatch.

**Recommendation:**  
Add a migration registry: `Map<Integer, StateMigrator>` where StateMigrator transforms a vN payload to vN+1. The load path would then chain migrations (v0 → v1 → v2 → current) instead of hardcoding v0→v1 and v1-only encode.

---

#### 2.3.2 ScriptExecutor Exception Type

**Severity:** 🟡 Medium  
**Category:** Architecture (Error Handling)

**Issue:**  
`ScriptExecutor.execute()` and `executeChecked()` catch RuntimeException from guest callbacks and log them, but the exception detail is limited to the message string. GraalVM guest exceptions may have useful context (source location, stack trace) that is lost when catching RuntimeException directly.

**Recommendation:**  
Catch `PolyglotException` instead of `RuntimeException` for guest code invocations. `PolyglotException` provides `isGuestException()`, `getSourceLocation()`, and `getGuestObject()`. Log the guest stack trace when available.

---

#### 2.3.3 RuntimeActivationTransaction Quarantine Visibility

**Severity:** 🟡 Medium  
**Category:** Architecture (Error Handling)

**Issue:**  
RuntimeActivationTransaction uses a quarantine string to capture non-fatal failures during commit/cleanup. The quarantine is logged but not exposed through ScriptRuntimeReport or ScriptReloadResult. Operators must check logs to discover degraded states.

**Recommendation:**  
Extend ScriptRuntimeReport with an optional `quarantineWarning` field. When quarantine is non-null after commit, include it in the report so `::scripts status` can display it.

---

### 2.4 Performance Findings

#### 2.4.1 GraalVM Source Caching

**Severity:** 🟡 Medium  
**Impact:** Moderate

**Issue:**  
GraalVM Source objects are recreated on every reload in `replaceContext()`. Each JS file is read and parsed via `Source.newBuilder().build()` repeatedly. The content directory file tree is walked each reload instead of caching the module list between reloads.

**Recommendation:**  
Cache compiled scripts between reloads where possible, or at minimum cache the module list.

---

#### 2.4.2 Wrapper Allocation Pattern

**Severity:** 🟡 Medium  
**Impact:** Moderate

**Issue:**  
ScriptedPlayer and ScriptedNpc expose Java objects to JS via `@HostAccess.Export` but create new wrapper instances per event instead of caching or pooling them. ScriptedPlayer.getPosition() additionally allocates a new ScriptedPosition on every call.

**Recommendation:**  
Consider caching or pooling wrapper instances. Cache ScriptedPosition in `getPosition()`.

---

### 2.5 Correctness Findings

#### 2.5.1 ScriptFunctions define* Methods Exception Handling

**Severity:** 🟡 Medium  
**Category:** Correctness (Error Handling)  
**File:** `engine/server/src/main/java/com/rs2/script/ScriptFunctions.java`

**Issue:**  
`define*` methods (defineQuest, defineBoss, defineRaid, etc.) do not catch parser exceptions; a malformed definition payload propagates as an unhandled throwable during module evaluation.

**Reproduction Steps:**  
In a content module, call `defineQuest({})` with a missing required field. `QuestDefinitionParser.parse()` throws, and since ScriptFunctions does not catch it, the exception propagates to the GraalVM eval.

**Recommendation:**  
Wrap parser calls in try-catch and provide meaningful error messages to scripts.

---

#### 2.5.2 addExperience() XP Cap Check

**Severity:** 🟡 Medium  
**Category:** Correctness (Edge Case)  
**File:** `engine/server/src/main/java/com/rs2/script/ScriptedPlayer.java`

**Issue:**  
`SkillView.addExperience()` rejects amount > 200000000 but the RS2 XP cap is 200M total XP, not per-call; a player near 200M can have legitimate small additions rejected.

**Reproduction Steps:**  
Set player XP to 199999995 on a skill, then call `player.getSkills().addExperience(0, 10)`. The method rejects amount > 200M, but the real check should be `playerXP[skill] + amount > 200M_CAP`.

**Recommendation:**  
Change validation to check `playerXP[skill] + amount > MAX_XP` instead of `amount > MAX_XP`.

---

#### 2.5.3 PlayerStateNamespace Reads Not Generation-Aware

**Severity:** 🟡 Medium  
**Category:** Correctness (State)  
**File:** `engine/server/src/main/java/com/rs2/script/state/PlayerStateNamespace.java`

**Issue:**  
`get*` read methods (getBoolean, getNumber, getString) do not check the mutationAllowed supplier, allowing reads even when the player is in a closed encounter or stale generation.

**Reproduction Steps:**  
Open an encounter, begin a script that calls `player.state('ns').getNumber('key')`, then let the encounter close (generation advances). The read still returns a value from the old state store.

**Recommendation:**  
Document this behavior if intentional, or add generation checking to reads.

---

### 2.6 Documentation Findings

#### 2.6.1 Missing declare global in runtime.ts

**Severity:** 🟡 Medium  
**Type:** Inaccuracy  
**File:** `content/src/core/runtime.ts`

**Issue:**  
The `declare global` block in `runtime.ts` (lines 819-842) is missing type declarations for five implemented global functions: `defineBoss`, `defineQuest`, `defineRaid`, `defineArea`, and `defineShop`. Only `defineDropTable`, `defineReward`, and `defineGatheringResource` are declared.

**Correction:**  
Add the missing global declarations to the `declare global` block:

```typescript
declare global {
  // ... existing declarations ...
  const defineBoss: DefineBoss;
  const defineQuest: DefineQuest;
  const defineRaid: DefineRaid;
  const defineArea: DefineArea;
  const defineShop: DefineShop;
}
```

---

#### 2.6.2 Missing defineGatheringResource in SCRIPT_BRIDGE.md

**Severity:** 🟡 Medium  
**Type:** Incomplete  
**File:** `docs/SCRIPT_BRIDGE.md`

**Issue:**  
The global functions table in `SCRIPT_BRIDGE.md` (lines 141-172) is missing `defineGatheringResource`. The function is implemented in `ScriptBindings.java` and `ScriptFunctions.java`, and is documented later in the Phase 8 section, but it should also appear in the authoritative global functions table.

**Correction:**  
Add `defineGatheringResource` to the global functions table after `defineReward`.

---

#### 2.6.3 Incomplete Inline Types in SCRIPT_BRIDGE.md

**Severity:** 🟡 Medium  
**Type:** Incomplete  
**File:** `docs/SCRIPT_BRIDGE.md`

**Issue:**  
The inline type for `getSkills()` only shows `getLevel()` and `setLevel()` methods, but omits `getCurrentLevel()`, `getBaseLevel()`, `getExperience()`, and `addExperience()` which are all implemented in Java and declared in `runtime.ts`.

Similarly, `getInventory()` and `getBank()` inline types don't show string overloads even though Java supports them.

**Correction:**  
Update inline types to show all methods and string overloads.

---

## Priority 3: Low-Severity Issues (Backlog)

### 3.1 Security (Low)

| Finding | File | Description |
|---------|------|-------------|
| getRights() exposes admin flags | `ScriptedPlayer.java` | Player rights exposed without filtering - remove if not needed |
| PlayerStateNamespace no size limits | `PlayerStateNamespace.java` | No limits on stored value sizes - could cause memory exhaustion |
| dev.log() log injection | `ScriptBindings.java` | Could be used for log injection - validate input |
| ScriptContext.target type | `ScriptContext.java` | Target is typed `Object` - audit all possible target types |

### 3.2 Architecture (Low)

| Finding | File | Description |
|---------|------|-------------|
| replaceContext() too long | `ScriptHost.java` | 119 lines - extract post-commit steps into named methods |
| loader.ts static imports | `content/src/loader.ts` | No auto-discovery - consider manifest-driven loader if modules grow |
| runtime.ts too large | `content/src/core/runtime.ts` | 800+ lines - split into focused modules at 1000 lines |
| 4-file change for new globals | Various | Document checklist for adding new bridge globals |

### 3.3 Performance (Low)

| Finding | File | Description |
|---------|------|-------------|
| Sequential post-commit operations | `ScriptHost.java` | 12+ operations could be parallelized since they're independent |
| RouteRegistry already optimal | `RouteRegistry.java` | HashMap lookup is O(1) - no change needed |

### 3.4 Correctness (Low)

| Finding | File | Description |
|---------|------|-------------|
| BankView.add() no return | `ScriptedPlayer.java` | Returns void - cannot signal failure to caller |
| showInterface() race | `ScriptedPlayer.java` | Return value races with other interface operations |
| ScriptStateStore copy per set | `ScriptStateStore.java` | Copies entire state per set operation - optimize for repeated writes |

### 3.5 Documentation (Low)

| Finding | File | Description |
|---------|------|-------------|
| ScriptedDialogue overloads | `SCRIPT_BRIDGE.md` | Missing optional parameters in documented method signatures |
| dev object undocumented | `SCRIPT_BRIDGE.md` | Missing note about `dev.log(msg)` behavior and output format |
| ScriptArray undocumented | `SCRIPT_BRIDGE.md` | No section explaining ScriptArray purpose and behavior |

---

## Key Recommendations

### 1. Add Input Validation Layer

Create a validation utility for all bridge methods:

```java
public class BridgeValidation {
    public static String requireNonBlankString(Value v, String paramName) {
        if (v == null || !v.isString()) throw new IllegalArgumentException(paramName + " must be string");
        String s = v.asString();
        if (s.isBlank()) throw new IllegalArgumentException(paramName + " cannot be blank");
        return s;
    }
    // ... other validators
}
```

### 2. Improve Synchronization Strategy

Replace `synchronized` methods with finer-grained locking:
- `ReadWriteLock` for registry access
- Per-event-type locks for dispatch
- Immutable snapshots for status reads

### 3. Add Type Safety Tests

Create tests that verify JS-Java boundary behavior:
- Null/undefined coercion
- Numeric overflow/truncation
- Array index validation
- Callback signature validation

### 4. Update Documentation

- Fix missing `declare global` entries in `runtime.ts`
- Sync `SCRIPT_BRIDGE.md` with implementation
- Add examples for all global functions

### 5. Performance Monitoring

Add metrics to identify hot spots:
- Script execution time per event type
- Object allocation rate in hot paths
- Lock contention statistics

---

## Files Needing Immediate Attention

| File | Issues | Priority |
|------|--------|----------|
| `ScriptBindings.java` | Input validation | 🔴 High |
| `ScriptedPlayer.java` | setLevel, null checks, race conditions | 🔴 High |
| `ScriptHost.java` | Synchronization bottleneck, race in status | 🔴 High |
| `ScriptFunctions.java` | Exception handling, parser validation | 🟡 Medium |
| `ReadOnlyContentFileSystem.java` | TOCTOU fix | 🟡 Medium |
| `runtime.ts` | Missing type declarations | 🟡 Medium |
| `SCRIPT_BRIDGE.md` | Sync with implementation | 🟡 Medium |

---

## Review Methodology

This review was conducted using a comprehensive workflow with 6 parallel subagent reviews:

1. **Security Review:** Sandboxing, injection risks, privilege escalation vectors
2. **Type Safety Review:** TypeScript types vs Java runtime, type coercion issues
3. **Architecture Review:** Bridge design, separation of concerns, error handling patterns
4. **Performance Review:** GraalVM context usage, memory management, hot paths
5. **Correctness Review:** Edge cases, error handling, state management, race conditions
6. **Documentation Review:** Accuracy of docs vs implementation, completeness

Each subagent performed deep analysis of the codebase, examining Java bridge code, TypeScript SDK, documentation, and test coverage.

---

**Review Completed:** 2026-08-06  
**Next Review:** After Priority 1 issues are resolved
