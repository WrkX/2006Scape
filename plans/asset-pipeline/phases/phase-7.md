# Phase 7 — Custom Asset Namespace

## Objective

Safely add new model/object definition ranges so that custom imported assets can coexist with original 2006Scape assets in the cache. Build the tooling to allocate IDs, write definitions, and export them.

## Scope

### In Scope

1. **Object definition encoder** — Write ObjectDefinition to the opcode-driven loc.dat/loc.idx binary format (inverse of decodeObjectDefinition)
2. **ID allocation registry** — Allocate, track, persist, and validate custom ID ranges for models, objects, NPCs, and items
3. **Definition exporter** — High-level API to export custom object definitions to the cache
4. **Limits update** — Define safe custom-range constants based on engine analysis
5. **Tests** — Round-trip definition encoding, registry operations, export verification

### Out of Scope

- Client-side Java changes (Phase 8 dual decoder)
- Custom model format (Phase 8)
- Visual editor / asset browser (Phase 9)
- NPC/item definition encoders (follows same pattern, defer to later)

## Engine Analysis Findings

### Model Limits

- No hard-coded `MAX_MODEL_ID` constant in the engine
- Model data array (`aClass21Array1661`) is a fixed-size `Class21[]`, allocated once at startup
- Size comes from `onDemandFetcher.getVersionCount(0)` = `model_version file size / 2`
- Model lookup: `aClass21Array1661[j]` — direct array index, returns null if out of bounds
- Network protocol uses 2-byte model IDs (`readUnsignedWord`)
- **Practical limit: 65535** (uint16 file IDs in cache index 1)

### Object Definition Limits

- `totalObjects = stream.readUnsignedWord()` → max 65535
- `streamIndices = new int[totalObjects]` — direct array indexed by object ID
- `ObjectDef.forID(i)` — looks up `streamIndices[i]`, decodes from `stream.currentOffset = streamIndices[i]`
- 20-slot LRU cache for decoded definitions
- Gaps in ID space are safe: undefined IDs produce default-looking objects
- **Practical limit: 65535** (uint16 in loc.idx header)

### NPC / Item Limits

- `EntityDef.totalNPCs = stream.readUnsignedWord()` → max 65535
- `ItemDef.totalItems = stream.readUnsignedWord()` → max 65535
- Same uint16 pattern as objects

### Landscape Encoding Constraint

- `method422()` = unsigned smart encoding: 0-127 in 1 byte, 128-32767 in 2 bytes
- Used for **deltas** between consecutive sorted object IDs in landscape files
- The delta encoding in `encodeLandscape` produces `idDelta = sourceId - prevId`
- Original object IDs max out around ~25000 in the 2006Scape cache
- **Hard constraint: deltas must be ≤ 32767**
- If custom objects start at 60000, delta from last original (~25000) = ~35000 → **EXCEEDS LIMIT**

### Safe Custom Range Strategy

Given the landscape delta encoding constraint:

| Asset Type | Original Max (est.) | Safe Custom Start | Max Custom ID |
|-----------|-------------------|-------------------|---------------|
| Models | ~20000 | 50000 | 65535 |
| Objects | ~25000 | 35000 | 65535 |
| NPCs | ~4000 | 35000 | 65535 |
| Items | ~12000 | 35000 | 65535 |

Objects at 35000+ keep deltas within 32767 of original max (~25000). Models don't go through landscape encoding, so 50000+ is safe.

**Deferred**: Starting objects at 60000 (as originally proposed in MODELS_AND_OBJECTS.md) requires either a client-side smart encoding extension or a different landscape encoding strategy. This is noted for Phase 8.

## Technical Design

### 1. Object Definition Encoder

`encodeObjectDefinition(def: ObjectDefinition): Buffer` — inverse of `decodeObjectDefinition`.

Must support all opcodes the decoder reads:
- Opcode 1: model IDs + model types (uint16 + uint8 pairs)
- Opcode 2: name (string)
- Opcode 3: description (string)
- Opcode 5: model IDs only (uint16)
- Opcode 14/15: width/length
- Opcode 17/18: solid/impenetrable flags
- Opcode 19: hasActions
- Opcode 21: contouredGround
- Opcode 24: animationId
- Opcode 28: decorDisplacement
- Opcode 29/39: ambientLighting/contrast
- Opcode 30-38: actions
- Opcode 40: recolor pairs
- Opcode 60: secondary model ID
- Opcode 62: inverted
- Opcode 64: clipBlocked
- Opcode 65-67: scale X/Y/Z
- Opcode 68: mapSceneId
- Opcode 69: minimap marker
- Opcode 70-72: offset X/Y/Z
- Opcode 73: obstructive
- Opcode 75: support item
- Opcode 77: varbit/varp + children

Only emit opcodes that differ from defaults (matching how the original encoder works).

### 2. Definition File Writer

`writeDefinitionFiles(definitions: ObjectDefinition[]): { dat: Buffer, idx: Buffer }`

- Build idx file: uint16 count, then uint16 size-per-entry
- Build dat file: opcode-driven records
- Entries for IDs with no definition get size 0 in idx (gap-safe)

### 3. ID Allocation Registry

```ts
interface IdRange {
  type: 'model' | 'object' | 'npc' | 'item';
  start: number;
  end: number;      // exclusive
  label: string;    // human-readable purpose
}

interface Allocation {
  id: number;
  type: string;
  label: string;
  allocatedAt: string;  // ISO timestamp
}

interface RegistryState {
  version: number;
  ranges: IdRange[];
  allocations: Allocation[];
}
```

API:
- `createRegistry(ranges: IdRange[]): IdRegistry`
- `loadRegistry(json: string): IdRegistry`
- `registry.allocate(type, label): number` — returns next free ID
- `registry.free(id): void`
- `registry.lookup(id): Allocation | undefined`
- `registry.save(): string` — JSON serialization
- `registry.validate(): ValidationError[]` — check for overlaps, range violations

### 4. Limits Constants

Add to `limits.ts`:
```ts
export const CUSTOM_MODEL_START = 50000;
export const CUSTOM_OBJECT_START = 35000;
export const CUSTOM_NPC_START = 35000;
export const CUSTOM_ITEM_START = 35000;
export const MAX_CUSTOM_ID = 65535;
```

## Deliverables

| File | Purpose |
|------|---------|
| `tools/src/cache/definition-encoder.ts` | `encodeObjectDefinition()` + `writeDefinitionFiles()` |
| `tools/src/registry/id-registry.ts` | `IdRegistry` class with allocate/free/save/load |
| `tools/src/registry/types.ts` | Registry type definitions |
| `tools/src/exporter/definition-exporter.ts` | `exportObjectDefinitions()` to cache |
| `tools/src/cache/__tests__/definition-encoder.test.ts` | Round-trip encoding tests |
| `tools/src/registry/__tests__/id-registry.test.ts` | Registry operation tests |
| `tools/src/exporter/__tests__/definition-exporter.test.ts` | Export verification tests |
| `plans/asset-pipeline/phases/phase-7.md` | This document |

## Acceptance Criteria

- [ ] `encodeObjectDefinition()` round-trips against `decodeObjectDefinition()` for all real definitions in cache fixture
- [ ] `writeDefinitionFiles()` produces valid loc.dat/loc.idx that the client can load
- [ ] `IdRegistry` allocates sequential IDs within ranges, prevents double-allocation, persists to JSON
- [ ] Custom definitions can be exported to cache and read back correctly
- [ ] `pnpm typecheck` — zero errors
- [ ] `pnpm test` — all existing + new tests pass

## Risks

| Risk | Mitigation |
|------|-----------|
| Definition encoder misses an opcode | Round-trip test against ALL real definitions in fixture cache |
| Custom IDs conflict with future original updates | Registry validates against configurable ranges |
| Landscape delta overflow for high object IDs | Range starts at 35000 (within 32767 of original max ~25000) |
| loc.idx uint16 overflow | Hard cap at 65535, validated by registry |
