```yaml
type: planning
entity: phase
plan: asset-pipeline
phase: 5
status: completed
created: 2026-07-28
updated: 2026-07-28
```

# Phase 5: Object Mapping & Landscape Export

## Objective

Build the object mapping system and landscape exporter so that imported regions can have their objects classified, remapped, and written back to the 2006Scape cache format. The round-trip must work: import a region → map objects → export landscape → decode landscape → identical placements.

## Scope

### Includes
- **Landscape encoder** (`encodeLandscape`) — inverse of `decodeLandscape`, produces gzip-compressed landscape data for cache index 4
- **Object mapper** (`ObjectMapper`) — classifies objects by compatibility, maps source IDs to target IDs using a mapping database
- **Mapping database** — JSON-based mapping file format with load/save, supporting rules: `exact`, `substitute`, `remove`
- **Landscape exporter** (`exportLandscape`) — high-level API: mapped region → cache landscape file
- **Region exporter** (`exportRegion`) — combined terrain + landscape export
- Round-trip test: decode landscape → encode → identical bytes
- Integration test: import → map → export → decode → verify

### Excludes (deferred to later phases)
- Model backporting (Phase 6)
- Custom asset namespace (Phase 7)
- Dual model decoder (Phase 8)
- Visual editor/UI (Phase 9)
- Morphing object resolution (opcode 77 children)

## Prerequisites
- [x] Phase 3 source importer produces `ImportedRegion` with objects and definitions
- [x] Phase 4 terrain exporter writes terrain to cache
- [x] `CacheWriter` writes block-chained cache files
- [x] `decodeLandscape` reads landscape files from cache

## Key Technical Decisions

### Landscape binary format

The landscape format is delta-encoded, matching `decodeLandscape` in `map-decoder.ts`:

**Smart encoding** (`writeSmart`):
- Values 0-127: 1 byte
- Values 128-32767: 2 bytes with high bit set (`value - 0x8000` as uint16BE)

**Object stream structure**:
```
Objects are sorted by sourceId ascending.
For each sourceId group:
  writeSmart(sourceId - prevSourceId)   // delta from previous ID
  prevPacked = 0
  For each placement (sorted by packed position ascending):
    packed = (plane << 12) | (x << 6) | y
    writeSmart(packed - prevPacked + 1) // delta + 1 (0 is terminator)
    writeByte((type << 2) | rotation)   // attributes byte
    prevPacked = packed
  writeSmart(0)                          // end of placements for this ID
writeSmart(0)                            // end of stream
```

The first sourceId delta is from -1 (the decoder initializes `id = -1`).

### Object mapping model

Each mapping entry specifies:
- `sourceId`: The object ID from the imported (source) cache
- `targetId`: The object ID to use in the 2006 cache (-1 = remove)
- `rule`: `exact` (identical object exists), `substitute` (use different 2006 object), `remove` (no equivalent)

A mapping database is a JSON file:
```json
{
  "version": 1,
  "mappings": {
    "45123": { "targetId": 1512, "rule": "substitute", "note": "Use 2006 stone wall" },
    "12345": { "targetId": 12345, "rule": "exact" },
    "99999": { "targetId": -1, "rule": "remove", "note": "No 2006 equivalent" }
  }
}
```

Objects without a mapping entry are classified as `UNMAPPED` and excluded from export by default.

### Export pipeline

```text
ImportedRegion
  → ObjectMapper.applyMappings(region, mappingDb)
    → MappedRegion (objects classified, IDs remapped)
  → exportLandscape(writer, mappedRegion, landscapeFileId)
  → exportRegion(writer, region, terrainFileId, landscapeFileId, mapper?, mappingDb?)
```

## Deliverables

- [ ] `src/cache/landscape-encoder.ts` — `encodeLandscape(objects: WorldObject[]): Buffer`
- [ ] `src/mapping/types.ts` — Mapping types (MappingRule, MappingEntry, MappingDatabase)
- [ ] `src/mapping/object-mapper.ts` — `ObjectMapper` class with `classify()`, `applyMappings()`
- [ ] `src/exporter/landscape-exporter.ts` — `exportLandscape(writer, objects, fileId)`
- [ ] `src/exporter/region-exporter.ts` — `exportRegion(writer, region, ...)` combined export
- [ ] Round-trip test: decode real landscape → encode → compare bytes
- [ ] Mapper test: classify objects, apply mappings, verify results
- [ ] Integration test: full import → map → export → decode pipeline
- [ ] All existing tests still pass (100+ tests)

## Acceptance Criteria

- [ ] `encodeLandscape(objects)` produces bytes that `decodeLandscape` reads back to identical objects.
- [ ] Round-trip preserves: all sourceIds, positions, planes, types, rotations.
- [ ] `ObjectMapper.classify()` correctly categorizes objects as EXACT/SUBSTITUTE/REMOVE/UNMAPPED.
- [ ] `ObjectMapper.applyMappings()` remaps source IDs to target IDs and removes mapped-for-removal objects.
- [ ] `exportLandscape` writes landscape to cache and `decodeLandscape` reads it back correctly.
- [ ] `exportRegion` writes both terrain and landscape that survive round-trip.
- [ ] `tsc --noEmit` passes with zero errors.
- [ ] `vitest run` passes all existing and new tests.
