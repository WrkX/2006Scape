```yaml
type: planning
entity: phase
plan: asset-pipeline
phase: 3
status: completed
created: 2026-07-28
updated: 2026-07-28
```

# Phase 3: Source Importer

## Objective

Build a high-level importer that reads one region from the 2006Scape cache by region coordinates and produces a normalized SingleScape IR, including terrain, object placements, object definitions, and required model IDs.

## Scope

### Includes
- RS archive container decoder (bzip2 decompression for archive 0 entries)
- Map index reader (archive 0, file 5, named entry `map_index`)
- Region coordinate resolution (regionId → terrainFileId, landscapeFileId)
- Object definition resolution (loc.dat/loc.idx from archive 0, file 2, named entries)
- Model ID collection from resolved definitions
- `importRegion(cache, input)` high-level API
- `ImportedRegion` IR type extending `Region` with source metadata
- Tests using real cache fixtures

### Excludes (deferred to later phases)
- Model decoding/materialization (collects IDs only)
- Terrain export to legacy format (Phase 4)
- Object mapping/replacement (Phase 5)
- Model backporting (Phase 6)

## Prerequisites
- [x] Phase 2 cache reader, map decoder, definition decoder, model decoder all built
- [x] IR types defined (Region, Plane, Tile, WorldObject, etc.)
- [x] Real cache fixtures in `tools/fixtures/cache/`

## Key Technical Decisions

### Map index format
Archive 0, file 5 is a StreamLoader archive. Named entry `map_index` contains 7-byte records:
- uint16 regionId (where regionId = (regionX << 8) | regionY)
- uint16 terrainFileId (index 4)
- uint16 landscapeFileId (index 4)
- uint8 members (1 = true)

### Archive container format
Archive 0 uses bzip2 compression (Java's stripped-header bzip2). The CacheReader needs an archive decoder that:
1. Reads the raw file from cache index 0
2. Decompresses (bzip2 for the whole archive, or uncompressed)
3. Parses the 10-byte entry table (4-byte hash + 3-byte extracted + 3-byte compressed)
4. Each entry's payload may also be individually bzip2 compressed
5. Named entries are looked up by computing the Java hash of the entry name

### Definition resolution
Object definitions (loc.dat/loc.idx) are stored as named entries in archive 0, file 2. The importer reads the idx to find offsets, then slices only the definitions referenced by the region's objects (not all definitions).

### Model ID collection
For each resolved object definition, the importer extracts model IDs. If `modelTypes` is defined, only model IDs matching the placement `type` on the world object are selected. If `modelTypes` is undefined, all `modelIds` are used. The result is a deduplicated, sorted list of required model IDs.

## Deliverables

- [ ] `src/cache/archive-decoder.ts` — RS archive container parsing with bzip2 support
- [ ] `src/cache/map-index.ts` — Map index reader
- [ ] `src/importer/source-importer.ts` — High-level import orchestration
- [ ] `ImportedRegion` type in `src/ir/types.ts`
- [ ] Tests for archive decoder, map index, and full import
- [ ] `decompress-bzip2` dependency added to package.json

## Acceptance Criteria

- [ ] `importRegion(cache, { regionId: 12850 })` returns a complete `ImportedRegion` with 4 planes of 64x64 tiles.
- [ ] Base coordinates are set correctly (regionId 12850 → baseX=3200, baseY=3200).
- [ ] Object placements match `decodeLandscape` output (4556 objects for region 12850).
- [ ] Each world object has `modelRefs` populated from its resolved definition.
- [ ] `requiredModelIds` is a deduplicated sorted array of all referenced model IDs.
- [ ] Unknown region IDs throw a clear error.
- [ ] `tsc --noEmit` passes with zero errors.
- [ ] `vitest run` passes all existing and new tests.
