```yaml
type: planning
entity: phase
plan: asset-pipeline
phase: 4
status: completed
created: 2026-07-28
updated: 2026-07-28
```

# Phase 4: Terrain Exporter

## Objective

Build a terrain exporter that converts IR terrain data back into the 2006Scape cache format (gzip-compressed terrain files in cache index 4), plus a cache writer to persist exported files. The round-trip must be lossless: decode → IR → encode → identical bytes.

## Scope

### Includes
- Terrain encoder (`encodeTerrain`) — inverse of `decodeTerrain`
- Cache writer (`CacheWriter`) — writes block-chained files into `main_file_cache.dat` + idx files
- Terrain exporter (`exportTerrain`) — high-level API: IR terrain → cache file
- Round-trip test: decode real terrain → encode → compare bytes
- Export integration test: write terrain to a fresh cache, read it back, verify tiles match

### Excludes (deferred to later phases)
- Landscape/object export (Phase 5)
- Map index management (write new entries)
- Model backporting (Phase 6)
- Changes to the Java engine

## Prerequisites
- [x] Phase 3 source importer can produce `ImportedRegion` with full terrain
- [x] `decodeTerrain` reads real cache terrain files
- [x] `CacheReader` reads block-chained cache format
- [x] Real cache fixtures in `tools/fixtures/cache/`

## Key Technical Decisions

### Terrain binary format

Derived from `ObjectManager.java` method `method181` (the client's terrain decoder). The decompressed terrain file is a flat stream:

- Iteration order: **plane 0-3 → x 0-63 → y 0-63** (16,384 tiles total)
- Each tile is a variable-length sequence of opcodes terminated by type 0 or type 1:

| Type byte | Meaning | Payload |
|-----------|---------|---------|
| 0 | End tile, default height | none |
| 1 | End tile, explicit height | 1 byte (rawHeight) |
| 2-49 | Overlay | 1 byte (overlayId) |
| 50-81 | Tile attribute/collision flag | none |
| 82-255 | Underlay colour | none |

### Height encoding

The client encodes height differently based on plane (from `ObjectManager.java:method181`):

- **Plane 0, type 0**: Height is procedurally generated from global world coordinates: `-method172(0xe3b7b + baseX*64 + x, 0x87cce + baseY*64 + y) * 8`. The `method172` function uses multi-octave noise and returns values 10-60, so procedural heights are -480 to -80.
- **Plane 0, type 1**: Height = `-rawHeight * 8`. If rawHeight=1, height=0 (sentinel).
- **Plane >0, type 0**: Height = `tileBelow[x][y] - 240`.
- **Plane >0, type 1**: Height = `tileBelow[x][y] - rawHeight * 8`. If rawHeight=1, height=tileBelow (sentinel for 0 delta).

For the encoder, we reverse this:
- IR stores `tile.height` as the decoded value (already in engine units, multiples of 8).
- Compute the default height for this tile (procedural for plane 0, prevHeight-240 for plane >0).
- If `tile.height === defaultHeight`, write type 0 (no payload).
- Otherwise compute rawHeight: for plane 0, `rawHeight = -tile.height / 8`; for plane >0, `rawHeight = (prevHeight - tile.height) / 8`. If rawHeight === 0, use sentinel 1. Clamp to 0-255. Write type 1 + rawHeight byte.

### Overlay encoding

`type = shape * 4 + rotation + 2`, then write type byte followed by overlayId as a **signed byte** (per `readSignedByte()` in the client). Shape range 0-11, rotation range 0-3.

### Underlay encoding

`type = underlayId + 81`, write single byte. Underlay ID 0 is not stored (would collide with attributes range).

### Attribute encoding

`type = attribute + 49`, write single byte. (Currently not stored in IR Tile type — deferred.)

### Cache writer format

The 2006Scape cache uses a block-chained file format:

- `main_file_cache.dat` — 520-byte blocks
- `main_file_cache.idx0` through `idx4` — 6-byte index entries per file

Each block (520 bytes):
```
Bytes 0-1: fileId (uint16BE)
Bytes 2-3: chunkNumber (uint16BE)
Bytes 4-6: nextSector (uint24BE)
Byte 7:    archiveId (uint8 = archiveIndex + 1)
Bytes 8-519: payload (512 bytes)
```

Each index entry (6 bytes):
```
Bytes 0-2: fileSize (uint24BE)
Bytes 3-5: firstSector (uint24BE)
```

The cache writer must:
1. Allocate sectors sequentially in `main_file_cache.dat`
2. Chain blocks via `nextSector` (0 for last block)
3. Write idx entries with file size and first sector

## Deliverables

- [ ] `src/cache/terrain-encoder.ts` — `encodeTerrain(planes: Plane[]): Buffer`
- [ ] `src/cache/cache-writer.ts` — `CacheWriter` class for writing to cache files
- [ ] `src/exporter/terrain-exporter.ts` — `exportTerrain(cache, region)` high-level API
- [ ] Round-trip test: decode → IR → encode → byte-identical
- [ ] Cache write/read round-trip test
- [ ] All existing tests still pass

## Acceptance Criteria

- [ ] `encodeTerrain(planes)` produces bytes that `decodeTerrain` reads back to identical tiles.
- [ ] Round-trip preserves: all heights, all overlays (id, shape, rotation), all underlays.
- [ ] `CacheWriter.write(idx, fileId, data)` + `CacheReader.readFile(idx, fileId)` round-trips.
- [ ] `exportTerrain` writes terrain to cache and `decodeTerrain` reads it back correctly.
- [ ] `tsc --noEmit` passes with zero errors.
- [ ] `vitest run` passes all existing and new tests.
