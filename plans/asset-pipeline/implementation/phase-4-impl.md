```yaml
type: planning
entity: implementation-plan
plan: asset-pipeline
phase: 4
status: completed
created: 2026-07-28
updated: 2026-07-28
```

# Phase 4 Implementation Plan: Terrain Exporter

## Overview

Build the terrain encoder (inverse of `decodeTerrain`), a cache writer for block-chained cache files, and the `exportTerrain` function. The round-trip test is the primary validation: decode real terrain → encode → identical bytes.

## Step 1: Procedural height function

**Files:**
- `tools/src/cache/terrain-encoder.ts`

The encoder needs the client's procedural height function for plane 0 default height comparison. Port `method172`, `method176`, `method186`, `method170`, `method184` from `ObjectManager.java`:

```
method170(x, y) — 2D value noise
method186(x, y) — interpolated noise
method176(x, y, scale) — multi-scale noise
method172(worldX, worldZ) — final height value (10-60)
method184(a, b, k, l) — cosine interpolation
```

These are pure math functions, straightforward to port.

**Verify:** Unit test: `method172(0xe3b7b + 50*64, 0x87cce + 50*64)` returns a value in range 10-60.

## Step 2: Tile encoder

**Files:**
- `tools/src/cache/terrain-encoder.ts`

Function: `encodeTile(tile, plane, x, y, prevHeight, baseX, baseY): number[]`

Logic:
1. Write underlay if `tile.underlay` is defined and > 0: push `81 + tile.underlay`
2. Write overlay if `tile.overlay` is defined and > 0: push `shape * 4 + rotation + 2`, then push overlayId as signed byte (int8)
3. Compute default height for this plane/position
4. If `tile.height === defaultHeight`, push `0` (type 0 terminal)
5. Otherwise compute rawHeight, push `1` then rawHeight byte

**Verify:** Encode a tile with known values, decode with `decodeTile`, compare.

## Step 3: Full terrain encoder

**Files:**
- `tools/src/cache/terrain-encoder.ts`

Function: `encodeTerrain(planes: Plane[], baseX: number, baseY: number): Buffer`

Logic:
1. Create a buffer accumulator
2. Iterate planes 0-3, x 0-63, y 0-63
3. Track `prevHeight[x][y]` per column across planes
4. For each tile, call `encodeTile`, append bytes
5. Return concatenated buffer

**Verify:** Round-trip test with real cache fixture data.

## Step 4: Cache writer

**Files:**
- `tools/src/cache/cache-writer.ts`

Class: `CacheWriter`

The cache uses 520-byte blocks with 8-byte headers and 512-byte payloads. Files are chained across sectors.

```ts
export class CacheWriter {
  private data: Buffer;       // main_file_cache.dat
  private indices: Map<number, Buffer>;  // idx0-idx4
  private nextSector: number;

  constructor(cacheDir: string)
  // Load or create cache files

  writeFile(archive: number, fileId: number, content: Buffer): void
  // 1. Allocate sectors for the content
  // 2. Write 512-byte chunks, each with 8-byte header
  // 3. Chain sectors via nextSector field
  // 4. Update idx entry with size and firstSector

  save(): void
  // Write all modified buffers to disk
}
```

Block header format:
- Bytes 0-1: fileId (uint16BE)
- Bytes 2-3: chunkNumber (uint16BE)
- Bytes 4-6: nextSector (uint24BE, 0 for last block)
- Byte 7: archiveId (archive + 1)
- Bytes 8-519: payload (512 bytes)

Index entry format (6 bytes):
- Bytes 0-2: fileSize (uint24BE)
- Bytes 3-5: firstSector (uint24BE)

**Verify:** Write a file, read it back with `CacheReader`, compare bytes.

## Step 5: Terrain exporter

**Files:**
- `tools/src/exporter/terrain-exporter.ts`

Function: `exportTerrain(writer: CacheWriter, region: Region, terrainFileId: number): void`

Logic:
1. Extract planes from region
2. `const terrainBytes = encodeTerrain(region.planes, region.baseX, region.baseY)`
3. `const compressed = gzipSync(terrainBytes)`
4. `writer.writeFile(4, terrainFileId, compressed)`

**Verify:** Integration test: export terrain, read back with `CacheReader`, decode, compare tiles.

## Step 6: Wire up exports

**Files:**
- `tools/src/cache/index.ts` — export terrain encoder
- `tools/src/index.ts` — export cache writer and terrain exporter

## Step 7: Tests

**Files:**
- `tools/src/cache/__tests__/terrain-encoder.test.ts` — round-trip and procedural height
- `tools/src/cache/__tests__/cache-writer.test.ts` — write/read round-trip
- `tools/src/exporter/__tests__/terrain-exporter.test.ts` — full export integration

### terrain-encoder.test.ts
- Port `method172` returns values in range 10-60 for known inputs
- Encode a tile with underlay only → decode → compare
- Encode a tile with overlay + height → decode → compare
- **Full round-trip**: decode real terrain (region 12850) → encode → compare bytes exactly

### cache-writer.test.ts
- Write a small buffer, read back, compare
- Write a file larger than one block (>512 bytes), read back, compare
- Overwrite a file, read back, get new content

### terrain-exporter.test.ts
- Import region 12850, export terrain to new cache, read back, compare tiles
- Verify heights, overlays, underlays all match

## Execution Order

1. Step 1 (noise functions) + Step 2 (tile encoder) → Step 3 (full encoder)
2. Step 4 (cache writer) can start in parallel with steps 1-3
3. Step 5 (exporter) depends on steps 3 and 4
4. Step 6 (exports) + Step 7 (tests) last
