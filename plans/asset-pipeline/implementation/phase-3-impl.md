```yaml
type: planning
entity: implementation-plan
plan: asset-pipeline
phase: 3
status: in_progress
created: 2026-07-28
updated: 2026-07-28
```

# Phase 3 Implementation Plan: Source Importer

## Overview

Build the archive decoder, map index reader, and high-level `importRegion` function that reads one region from the 2006Scape cache and produces a complete `ImportedRegion` IR.

## Step 1: Add bzip2 dependency

**Files:**
- `tools/package.json` — add `decompress-bzip2` to dependencies

**Verify:** `pnpm install` succeeds.

## Step 2: Archive decoder

**Files:**
- `tools/src/cache/archive-decoder.ts`

The RS archive format (StreamLoader):

```
If whole-archive compressed (extractedSize != compressedSize):
  8 bytes: extractedSize(uint32BE), compressedSize(uint32BE)
  payload: bzip2 compressed → decompress to get uncompressed archive body
Else:
  payload: raw archive body (no header)

Archive body (uncompressed):
  For each entry:
    10 bytes: hash(uint32BE), extractedSize(uint24BE), compressedSize(uint24BE)
  Entry count = first 2 bytes (uint16BE) of the archive body IF the body is
    the result of whole-archive decompression, OR derived from the sizes.

  Actually: the entry table is embedded in the decompressed data.
  The Java code reads the first N*10 bytes as the table, then the remaining
  bytes are the concatenated entry payloads.

  For each entry:
    if extractedSize == compressedSize: payload is uncompressed
    else: payload is bzip2 compressed (may need "BZh" header prepended)
```

Implementation:
- `decompressBzip2(data: Buffer): Buffer` — prepends `BZh` header if missing, calls decompress-bzip2
- `javaHash(name: string): number` — computes the RS name hash
- `readArchive(cache: CacheReader, archive: number, file: number): ArchiveEntry[]`
  - Reads raw file from cache
  - Decompresses whole archive if needed
  - Parses entry table
  - Decompresses individual entries if needed
- `findEntry(entries: ArchiveEntry[], name: string): Buffer | null`
  - Computes hash of name, finds matching entry

**Verify:** Unit test with synthetic archive data.

## Step 3: Map index reader

**Files:**
- `tools/src/cache/map-index.ts`

- `MAP_INDEX_ARCHIVE = 0`
- `MAP_INDEX_FILE = 5`
- `MAP_INDEX_ENTRY_NAME = "map_index"`
- `readMapIndex(cache: CacheReader): MapIndexEntry[]`
  - Reads archive 0, file 5 via `readArchive`
  - Finds `map_index` entry
  - Parses 7-byte records: (regionId:uint16, terrainFileId:uint16, landscapeFileId:uint16, members:uint8)
- `lookupRegion(entry: MapIndexEntry[], regionX: number, regionY: number): MapIndexEntry | null`
- `regionIdToCoords(regionId: number): { regionX: number, regionY: number }`
- `coordsToRegionId(regionX: number, regionY: number): number`

**Verify:** Fixture test: region 12850 → terrainFileId=382, landscapeFileId=383, members=true.

## Step 4: Extend IR types

**Files:**
- `tools/src/ir/types.ts` — add `ImportedRegion` interface

```ts
export interface ImportedRegion extends Region {
  members: boolean;
  definitions: ObjectDefinition[];
  requiredModelIds: number[];
}
```

Also re-export `ObjectDefinition` from the IR barrel so it's accessible without importing from cache.

**Verify:** `tsc --noEmit` passes.

## Step 5: Source importer

**Files:**
- `tools/src/importer/source-importer.ts`

```ts
export interface ImportRegionInput {
  regionId?: number;
  regionX?: number;
  regionY?: number;
}

export function importRegion(cache: CacheReader, input: ImportRegionInput): ImportedRegion
```

Logic:
1. Resolve coordinates: if `regionId` given, compute regionX/regionY. If coords given, compute regionId.
2. Read map index, find entry for this region. Throw if not found.
3. Decode terrain and landscape using existing `decodeTerrain`/`decodeLandscape`.
4. Collect unique object sourceIds from landscape.
5. Read loc.dat and loc.idx from archive 0, file 2 (named entries "loc.dat" and "loc.idx").
6. For each unique sourceId referenced by objects, decode only that definition.
7. For each world object, resolve modelRefs from its definition:
   - If definition has `modelTypes`, select modelIds where modelTypes match the object's `type`
   - Otherwise, use all `modelIds`
   - Attach to `worldObject.modelRefs`
8. Collect all model IDs, deduplicate, sort → `requiredModelIds`.
9. Return `ImportedRegion`.

**Verify:** Integration test with real cache fixture for region 12850.

## Step 6: Wire up exports

**Files:**
- `tools/src/cache/index.ts` — export archive decoder and map index
- `tools/src/index.ts` — export importer and ImportedRegion

**Verify:** `pnpm --filter @singlescape/tools build` succeeds.

## Step 7: Tests

**Files:**
- `tools/src/cache/__tests__/archive-decoder.test.ts` — synthetic archive, Java hash
- `tools/src/cache/__tests__/map-index.test.ts` — real fixture: region 12850
- `tools/src/importer/__tests__/source-importer.test.ts` — full import integration test

Test assertions for source-importer:
- Region has 4 planes, each with 64x64 tiles
- baseX=3200, baseY=3200 (region 12850 = (50,50), base = 50*64 = 3200)
- Objects count matches landscape decode (~4556)
- Each object with a valid definition has modelRefs populated
- requiredModelIds is sorted, deduplicated, non-empty
- JSON round-trip preserves all fields
- Missing region throws

**Verify:** `pnpm --filter @singlescape/tools test` — all tests pass.

## Execution Order

1. Step 1 (dependency) → Step 2 (archive decoder) → Step 3 (map index)
2. Step 4 (IR types) in parallel with steps 1-3
3. Step 5 (source importer) depends on steps 2, 3, 4
4. Step 6 (exports) and Step 7 (tests) last
