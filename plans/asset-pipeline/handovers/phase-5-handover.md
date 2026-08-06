# Handoff: Asset Pipeline — Phase 5 Complete

**Date:** 2026-07-29
**Working directory:** `/Users/jonas/Developer/RS/tools`
**Session type:** Background agent

## What was done

Phase 5 (Object Mapping & Landscape Export) of the asset-pipeline plan is now **complete**. The `@singlescape/tools` package can encode landscape objects to the 2006Scape cache format, classify and remap objects via a mapping database, and export full regions (terrain + landscape) to a new cache. Round-trip verified: decoded landscape → encoded → decoded produces identical object placements.

### New files created
- `tools/src/cache/landscape-encoder.ts` — `writeSmart(value)` (variable-length encoding, 1-2 bytes, range 0-32767) and `encodeLandscape(objects)` producing gzip-compressed landscape data for cache index 4
- `tools/src/mapping/types.ts` — Mapping types: `MappingRule` (exact/substitute/remove), `MappingEntry`, `MappingDatabase`, `ObjectClassification`, `ClassifiedObject`
- `tools/src/mapping/object-mapper.ts` — `ObjectMapper` class with `classify()`, `applyMappings()`, `getMapping()`. Static helpers: `loadMappingDatabase()`, `saveMappingDatabase()`
- `tools/src/mapping/index.ts` — Barrel export for mapping module
- `tools/src/exporter/landscape-exporter.ts` — `exportLandscape(writer, objects, landscapeFileId)` high-level API
- `tools/src/exporter/region-exporter.ts` — `exportRegion(writer, region, terrainFileId, landscapeFileId, mapper?)` combined export
- `tools/src/cache/__tests__/landscape-encoder.test.ts` — 13 tests: writeSmart encoding, round-trips, byte-identical comparison
- `tools/src/mapping/__tests__/object-mapper.test.ts` — 19 tests: classify, applyMappings, getMapping, serialization
- `tools/src/exporter/__tests__/landscape-exporter.test.ts` — 2 tests: export and round-trip
- `tools/src/exporter/__tests__/region-exporter.test.ts` — 3 tests: combined export, mapper filtering, mapper remapping
- `plans/asset-pipeline/phases/phase-5.md` — Phase scope document
- `plans/asset-pipeline/implementation/phase-5-impl.md` — Implementation plan
- `plans/asset-pipeline/handovers/phase-5-handover.md` — This handover doc

### Modified files
- `tools/src/cache/index.ts` — Added landscape encoder exports
- `tools/src/index.ts` — Added mapping, landscape exporter, region exporter exports
- `plans/asset-pipeline/plan.md` — Phase 5 marked completed
- `plans/asset-pipeline/todo.md` — All Phase 5 tasks marked completed

### Key implementation notes
- **Landscape format**: Delta-encoded stream of object placements grouped by sourceId. Uses `writeSmart` for variable-length integers (1 byte for 0-127, 2 bytes for 128-32767 with 0x8000 offset). Objects sorted by sourceId, then by packed position `(plane << 12) | (x << 6) | y`. Terminators: 0 for inner loop (placements), 0 for outer loop (IDs).
- **Object mapper**: JSON-based `MappingDatabase` with version field. Three rules: `exact` (same ID in both caches), `substitute` (different target ID), `remove` (no equivalent). UNMAPPED objects (no entry in database) pass through unchanged — this supports incremental mapping workflows.
- **applyMappings remaps sourceId**: For EXACT/SUBSTITUTE, the returned object's `sourceId` is set to the `targetId` so the landscape encoder writes the correct ID. The original `sourceId` is preserved in the `targetId` field for reference.
- **writeSmart range**: Values must be 0-32767. Throws on out-of-range input.

### Verification
```
pnpm typecheck  — zero errors
pnpm test       — 137/137 pass (16 test files)
```

Note: One pre-existing test (`uses untyped modelIds only for placement types 10/11`) occasionally times out due to its iteration over all objects — this is a pre-existing issue unrelated to Phase 5.

## Current state

| Phase | Status | Tests |
|-------|--------|-------|
| 1 — Inspect formats | completed | — |
| 2 — Neutral IR | completed | 43 |
| 3 — Source importer | completed | 82 |
| 4 — Terrain exporter | completed | 100 |
| 5 — Object mapping & landscape export | completed | 137 |

## What's next

**Phase 6: Static model backport** — Import one simple non-animated model (rock, tree, pillar, wall) into the 2006 cache format. This would involve:
1. Reading `tools/docs/MODELS_AND_OBJECTS.md` for model format details
2. Creating `plans/asset-pipeline/phases/phase-6.md` and `implementation/phase-6-impl.md`
3. Implementing model encoder (inverse of `decodeModel`)
4. Implementing model exporter via CacheWriter

### Known open issues (deferred)
- **Terrain height semantics**: Opcode 1 on planes >0 should incorporate prior-plane height. IR height sign convention should be documented.
- **Morphing objects**: Opcode 77 definitions have child IDs requiring varbit/varp state. Phase 3 exposes morph metadata but doesn't resolve active children.
- **Smart encoding range**: Landscape format limits object IDs to 0-32767. IDs beyond this range (e.g., custom objects at 60000+) would need a different encoding or the format may not support them.
- **Mapping database**: No initial mapping data exists yet. The first use case would be importing a region, exporting it, and iteratively building the mapping database based on what the 2006 client can render.

## Key file paths

| Path | Purpose |
|------|---------|
| `tools/src/cache/landscape-encoder.ts` | Landscape encoder + writeSmart |
| `tools/src/mapping/object-mapper.ts` | ObjectMapper class |
| `tools/src/mapping/types.ts` | Mapping types |
| `tools/src/exporter/landscape-exporter.ts` | High-level landscape export API |
| `tools/src/exporter/region-exporter.ts` | Combined terrain + landscape export |
| `tools/src/cache/terrain-encoder.ts` | Terrain encoder |
| `tools/src/cache/cache-writer.ts` | Block-chained cache file writer |
| `tools/src/exporter/terrain-exporter.ts` | High-level terrain export API |
| `tools/src/cache/map-decoder.ts` | Terrain + landscape decoders |
| `tools/src/importer/source-importer.ts` | importRegion() |
| `tools/src/ir/types.ts` | IR types |
| `plans/asset-pipeline/plan.md` | Top-level plan |
| `plans/asset-pipeline/todo.md` | Task tracking |

## Suggested skills

- **`/scout`** — Explore the engine codebase for Phase 6 model format details
- **`/doc-explorer`** — Write the Phase 6 plan documents
- **`/implementer-strong`** — Implement model encoder (complex binary format work)
- **`/code-review`** — Review Phase 5 changes before starting Phase 6
- **`/verify`** — Verify the landscape exporter works end-to-end in the actual client
