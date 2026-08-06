```yaml
type: planning
entity: implementation-plan
plan: asset-pipeline
phase: 5
status: completed
created: 2026-07-28
updated: 2026-07-28
```

# Phase 5 Implementation Plan: Object Mapping & Landscape Export

## Overview

Build the landscape encoder (inverse of `decodeLandscape`), object mapping system, and export functions. The landscape round-trip test is the primary binary validation; the mapping system validates classification and remapping logic.

## Step 1: Landscape encoder

**Files:**
- `tools/src/cache/landscape-encoder.ts`

Functions:
```ts
export function writeSmart(value: number): number[]
export function encodeLandscape(objects: WorldObject[]): Buffer
```

`writeSmart(value)`:
- If value <= 0x7f: return [value]
- If value <= 0x7fff: return [(value >> 8) | 0x80, value & 0xff]
- Actually: readSmart reads uint16BE with offset, checks high bit. For values > 0x7f:
  - Read uint16BE, subtract 0x8000
  - So writeSmart should: if value <= 0x7f, write 1 byte. Else write (value + 0x8000) as uint16BE.

`encodeLandscape(objects)`:
1. Sort objects by sourceId ascending
2. Group by sourceId
3. For each group (sorted):
   a. Write smart delta: `sourceId - prevSourceId` (first delta from -1)
   b. Sort placements by packed position ascending: `(plane << 12) | (x << 6) | y`
   c. For each placement:
      - Write smart delta: `packed - prevPacked + 1` (0 terminates the inner loop)
      - Write attributes byte: `(type << 2) | rotation`
   d. Write smart 0 (end of placements)
4. Write smart 0 (end of stream)
5. Gzip compress

**Verify:** Unit test: encode known objects, decode, compare.

## Step 2: Landscape encoder round-trip test

**Files:**
- `tools/src/cache/__tests__/landscape-encoder.test.ts`

Tests:
- `writeSmart` encodes 0, 127, 128, 32767 correctly
- Encode a single object → decode → compare
- Encode multiple objects with same sourceId → decode → compare
- **Full round-trip**: decode real landscape (region 12850, file 383) → encode → byte-identical

**Verify:** `pnpm test` — all landscape encoder tests pass.

## Step 3: Mapping types

**Files:**
- `tools/src/mapping/types.ts`

Types:
```ts
export type MappingRule = "exact" | "substitute" | "remove";

export interface MappingEntry {
  targetId: number;    // -1 for remove
  rule: MappingRule;
  note?: string;
}

export interface MappingDatabase {
  version: number;
  mappings: Record<string, MappingEntry>;  // key = sourceId as string
}

export type ObjectClassification = "EXACT" | "SUBSTITUTE" | "REMOVE" | "UNMAPPED";

export interface ClassifiedObject {
  object: WorldObject;
  classification: ObjectClassification;
  targetId: number | null;  // null = removed or unmapped
}
```

**Verify:** `tsc --noEmit` passes.

## Step 4: Object mapper

**Files:**
- `tools/src/mapping/object-mapper.ts`

Class: `ObjectMapper`
```ts
export class ObjectMapper {
  constructor(db: MappingDatabase)
  
  classify(objects: WorldObject[]): ClassifiedObject[]
  // For each object, look up sourceId in db.mappings
  // Return classification + targetId
  
  applyMappings(objects: WorldObject[]): WorldObject[]
  // Classify all objects
  // Return only EXACT + SUBSTITUTE objects with remapped targetId
  // Drop REMOVE + UNMAPPED objects
}
```

Static helpers:
```ts
export function loadMappingDatabase(json: string): MappingDatabase
export function saveMappingDatabase(db: MappingDatabase): string
```

**Verify:** Unit test with synthetic mapping database.

## Step 5: Landscape exporter

**Files:**
- `tools/src/exporter/landscape-exporter.ts`

```ts
export function exportLandscape(
  writer: CacheWriter,
  objects: WorldObject[],
  landscapeFileId: number,
): void
```

Logic:
1. `const compressed = encodeLandscape(objects)`
2. `writer.writeFile(4, landscapeFileId, compressed)`

**Verify:** Integration test: decode landscape → export → decode → compare.

## Step 6: Region exporter

**Files:**
- `tools/src/exporter/region-exporter.ts`

```ts
export function exportRegion(
  writer: CacheWriter,
  region: Region,
  terrainFileId: number,
  landscapeFileId: number,
  mapper?: ObjectMapper,
): void
```

Logic:
1. Export terrain (always)
2. If mapper provided: `objects = mapper.applyMappings(region.objects)`, else use `region.objects`
3. Export landscape

**Verify:** Integration test: import region → export region → decode both terrain and landscape → compare.

## Step 7: Wire up exports

**Files:**
- `tools/src/mapping/index.ts` — barrel export for mapping module
- `tools/src/cache/index.ts` — add landscape encoder export
- `tools/src/index.ts` — add mapping and landscape/region exporter exports

## Step 8: Tests

**Files:**
- `tools/src/cache/__tests__/landscape-encoder.test.ts` (created in Step 2)
- `tools/src/mapping/__tests__/object-mapper.test.ts`
- `tools/src/exporter/__tests__/landscape-exporter.test.ts`
- `tools/src/exporter/__tests__/region-exporter.test.ts`

### object-mapper.test.ts
- classify with empty database → all UNMAPPED
- classify with exact mapping → EXACT
- classify with substitute mapping → SUBSTITUTE, remapped targetId
- classify with remove mapping → REMOVE
- applyMappings drops REMOVE and UNMAPPED, keeps EXACT and SUBSTITUTE
- load/save round-trip

### landscape-exporter.test.ts
- Export decoded landscape objects, read back, compare

### region-exporter.test.ts
- Full pipeline: import → export → decode terrain + landscape → compare all

## Execution Order

1. Step 1 (landscape encoder) + Step 2 (encoder tests) — verify round-trip first
2. Step 3 (mapping types) + Step 4 (object mapper) — can be parallel with step 1
3. Step 5 (landscape exporter) depends on step 1
4. Step 6 (region exporter) depends on steps 4 and 5
5. Step 7 (exports) + Step 8 (tests) last
