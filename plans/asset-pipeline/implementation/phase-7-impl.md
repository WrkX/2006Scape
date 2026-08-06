# Phase 7 Implementation Plan

## Step 1 — Object definition encoder core

**File:** `tools/src/cache/definition-encoder.ts`

Implement `encodeObjectDefinition(def: ObjectDefinition): Buffer` that produces the opcode-driven binary format (inverse of `decodeObjectDefinition`).

Strategy: emit opcodes only for values that differ from the defaults defined in `decodeObjectDefinition`. This matches how the original format works — the decoder applies defaults first, then overwrites from opcodes.

Opcodes to emit (in order, matching decoder):
| Opcode | Condition | Data |
|--------|-----------|------|
| 1 | modelIds + modelTypes both non-null | uint8 count, then (uint16 modelId + uint8 type) × count |
| 2 | name defined | null-terminated string |
| 3 | description defined | null-terminated string |
| 5 | modelIds non-null, no modelTypes | uint8 count, then uint16 × count |
| 14 | width ≠ 1 | uint8 |
| 15 | length ≠ 1 | uint8 |
| 17 | solid === false | (no data) |
| 18 | impenetrable === false | (no data) |
| 19 | interactive | uint8(1) |
| 21 | contouredGround | (no data) |
| 24 | animationId ≠ -1 | uint16 |
| 28 | decorDisplacement ≠ 16 | uint8 |
| 29 | ambientLighting ≠ 0 | int8 |
| 30-38 | actions[i] defined | string |
| 39 | contrast ≠ 0 | int8 (contrast × 5) |
| 40 | recolorSrc.length > 0 | uint8 count, then (uint16 + uint16) × count |
| 60 | anInt746 (secondary model) | uint16 |
| 62 | inverted | (no data) |
| 64 | clipBlocked === false | (no data) |
| 65 | scaleX ≠ 128 | uint16 |
| 66 | scaleY ≠ 128 | uint16 |
| 67 | scaleZ ≠ 128 | uint16 |
| 68 | mapSceneId ≠ -1 | uint16 |
| 69 | minimap marker | uint8 |
| 70 | offsetX ≠ 0 | int16 |
| 71 | offsetY ≠ 0 | int16 |
| 72 | offsetZ ≠ 0 | int16 |
| 73 | obstructive | (no data) |
| 75 | support item | uint8 |
| 77 | varbit/varp + children | uint16 + uint16 + uint8 + (uint16 × (count+1)) |

Terminate with opcode 0.

Helper functions: `writeU8`, `writeU16`, `writeI8`, `writeI16`, `writeString`.

Also implement `writeDefinitionFiles(definitions: ObjectDefinition[], maxId?: number): { dat: Buffer, idx: Buffer }`:
- Build idx: uint16 count (maxId + 1), then uint16 size-per-entry for each ID
- Build dat: concatenated encoded definitions
- Gaps (IDs with no definition) get size 0 in idx

## Step 2 — Definition encoder tests

**File:** `tools/src/cache/__tests__/definition-encoder.test.ts`

Tests:
1. Encode minimal definition (all defaults) → verify decoder produces identical def
2. Encode definition with name → round-trip
3. Encode definition with model IDs (opcode 5) → round-trip
4. Encode definition with model IDs + types (opcode 1) → round-trip
5. Encode definition with all opcodes set → round-trip
6. **Real cache round-trip**: For every definition in the fixture loc.dat, encode then decode, compare all fields
7. `writeDefinitionFiles` with synthetic definitions → verify idx/dat structure
8. `writeDefinitionFiles` with gaps → verify gap entries have size 0
9. Round-trip through writeDefinitionFiles → decodeObjectDefinitions → compare

## Step 3 — ID allocation registry

**Files:**
- `tools/src/registry/types.ts` — type definitions
- `tools/src/registry/id-registry.ts` — `IdRegistry` class

Types:
```ts
interface IdRange { type, start, end (exclusive), label }
interface Allocation { id, type, label, allocatedAt }
interface RegistryState { version, ranges, allocations }
```

IdRegistry API:
- `constructor(ranges: IdRange[], allocations?: Allocation[])`
- `allocate(type: string, label: string): number` — finds next free ID in range, throws if exhausted
- `free(id: number): void` — removes allocation
- `lookup(id: number): Allocation | undefined`
- `getAllocations(type?: string): Allocation[]`
- `validate(): string[]` — returns list of errors (overlaps, out-of-range, double allocation)
- `save(): string` — JSON serialization
- `static load(json: string): IdRegistry`
- `static createDefault(): IdRegistry` — with standard ranges from limits.ts

## Step 4 — Registry tests

**File:** `tools/src/registry/__tests__/id-registry.test.ts`

Tests:
1. Allocate sequential IDs within a range
2. Allocate until range exhausted → throws
3. Free and re-allocate → freed ID reused
4. Multiple types → separate allocation cursors
5. Validate: no errors for valid state
6. Validate: detects overlapping ranges
7. Validate: detects out-of-range allocations
8. Save/load round-trip
9. createDefault() → verify standard ranges
10. Double-allocation prevention

## Step 5 — Definition exporter

**File:** `tools/src/exporter/definition-exporter.ts`

`exportObjectDefinitions(writer: CacheWriter, definitions: ObjectDefinition[]): void`

- Uses `writeDefinitionFiles()` to produce loc.dat/loc.idx
- Writes to cache archive 0, file 2 (matching where the source importer reads from)
- Actually: definitions go into the `streamLoader` archive. The client reads `loc.dat` and `loc.idx` from `streamLoader.getDataForName()`. Need to verify how these are stored in the cache.

**Note:** The definition dat/idx files are inside an archive (archive 0, file 2 in the cache, accessed via `findArchiveEntry`). The exporter may need to produce standalone files rather than direct cache writes. Investigate the actual storage format during implementation.

## Step 6 — Update limits and exports

**Modify:** `tools/src/limits.ts` — add:
```ts
export const CUSTOM_MODEL_START = 50000;
export const CUSTOM_OBJECT_START = 35000;
export const CUSTOM_NPC_START = 35000;
export const CUSTOM_ITEM_START = 35000;
export const MAX_CUSTOM_ID = 65535;
```

**Modify:** `tools/src/cache/index.ts` — add definition encoder exports
**Modify:** `tools/src/index.ts` — add all new exports

## Step 7 — Verification

```
pnpm typecheck  — zero errors
pnpm test       — all passing (159+ new tests)
```

Update `plan.md` and `todo.md` with Phase 7 completion status.
