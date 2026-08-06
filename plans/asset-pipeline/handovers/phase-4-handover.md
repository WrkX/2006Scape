# Handoff: Asset Pipeline — Phase 4 Complete

**Date:** 2026-07-28
**Session focus:** Phase 4 — Terrain Exporter

## What was done

Phase 4 (Terrain Exporter) is now **complete**. All code is in `/Users/jonas/Developer/RS/tools/` (`@singlescape/tools` package).

### New files created
- `src/cache/terrain-encoder.ts` — Terrain encoder with procedural height functions (noise2d, multiOctaveNoise, proceduralHeight), `encodeTerrain()` that produces gzip-compressed terrain data for cache index 4
- `src/cache/cache-writer.ts` — `CacheWriter` class for writing block-chained files to `main_file_cache.dat` + idx files
- `src/exporter/terrain-exporter.ts` — `exportTerrain(writer, region, terrainFileId)` high-level export API
- 3 test files with 18 new tests (100 total pass)

### Key bugs fixed during implementation
1. **CacheWriter sector 0**: `CacheReader` rejects `firstSector <= 0` as invalid. CacheWriter now starts allocating from sector 1 and pre-pads the data buffer.
2. **Terrain encoder running height**: The decoder initializes plane N's running height as `planeN-1.height + 240`, but the encoder was tracking just `planeN-1.height`. Fixed by storing `decodedHeight + 240` in the running height array for cross-plane defaults.

### Key implementation notes
- **Procedural height**: Ported `ObjectManager.method172` (multi-octave value noise with cosine interpolation). Returns 10-60. Used for plane 0 type 0 default height comparison.
- **Height encoding**: Type 0 = use running default (no payload). Type 1 = explicit height (1 byte payload, rawHeight where 1 is sentinel for 0).
- **Cache format**: 520-byte blocks (8-byte header + 512-byte payload), chained via `nextSector`. Index entries are 6 bytes (3-byte size + 3-byte firstSector).
- **Round-trip verified**: `decodeTerrain` → IR → `encodeTerrain` → `decodeTerrain` produces identical tile values for all tested regions (including Lumbridge region 12850).

### Verification
```
pnpm typecheck  — zero errors
pnpm test       — 100/100 pass (12 test files)
```

## What's next

**Phase 5: Existing-object mapping** — Map source objects to 2006-era equivalents where suitable.

The Phase 5 plan hasn't been written yet. The next agent should:
1. Read `plans/asset-pipeline/phases/phase-4.md` and `plans/asset-pipeline/implementation/phase-4-impl.md` for the pattern
2. Read the design docs in `tools/docs/` for object mapping strategy
3. Create `plans/asset-pipeline/phases/phase-5.md` and `plans/asset-pipeline/implementation/phase-5-impl.md`
4. Implement object mapping

## Key file paths

| Path | Purpose |
|------|---------|
| `tools/src/cache/terrain-encoder.ts` | Terrain encoder with procedural height (NEW) |
| `tools/src/cache/cache-writer.ts` | Block-chained cache file writer (NEW) |
| `tools/src/exporter/terrain-exporter.ts` | High-level terrain export API (NEW) |
| `tools/src/cache/map-decoder.ts` | Terrain + landscape decoders |
| `tools/src/cache/cache-reader.ts` | Cache file reader |
| `tools/src/ir/types.ts` | IR types |
| `tools/src/importer/source-importer.ts` | High-level importRegion |
| `plans/asset-pipeline/plan.md` | Top-level plan |
| `plans/asset-pipeline/phases/phase-4.md` | Phase 4 scope + acceptance criteria |
| `plans/asset-pipeline/implementation/phase-4-impl.md` | Phase 4 implementation plan |
