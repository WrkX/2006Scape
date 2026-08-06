---
type: planning
entity: todo
plan: asset-pipeline
updated: 2026-07-29
---

# Todo: asset-pipeline

> Tracking [asset-pipeline](plan.md)

## Completed Phase: 11

### Phase Context

- **Scope**: [Phase 11](phases/phase-11.md) — Region composer and map-index-aware relocation (completed)
- **Implementation**: [Phase 11 implementation](implementation/phase-11-impl.md) (completed)
- **Relevant Docs**: [Map Converter](../../tools/docs/MAP_CONVERTER_AND_EDITOR.md), [Region Converter](../../tools/docs/REGION_CONVERTER.md), [Asset Pipeline](../../tools/docs/ASSET_PIPELINE.md)
- **Prior Phase**: [Phase 10](phases/phase-10.md) — Region converter UI (completed)

### Pending

- [ ] None.

### In Progress

- [ ] None.

### Completed

- [x] Explore engine codebase for cache/model/map/definition format details. <!-- completed: 2026-07-28 -->
- [x] Document all hard-coded engine limits and format constants. <!-- completed: 2026-07-28 -->
- [x] Create tools/ pnpm workspace package with TypeScript + Vitest. <!-- completed: 2026-07-28 -->
- [x] Define IR types (Region, Plane, Tile, WorldObject, Model, Vertex, Face, TextureFace). <!-- completed: 2026-07-28 -->
- [x] Implement cache decoder (main_file_cache.dat + idx files). <!-- completed: 2026-07-28 -->
- [x] Implement model format decoder (header + vertex/face/texture data). <!-- completed: 2026-07-28 -->
- [x] Implement map format decoder (terrain m*.gz + landscape l*.gz). <!-- completed: 2026-07-28 -->
- [x] Implement definition decoders (loc/npc/obj dat+idx opcode format). <!-- completed: 2026-07-28 -->
- [x] Capture hard-coded engine limits as named constants. <!-- completed: 2026-07-28 -->
- [x] IR JSON serialization with round-trip tests. <!-- completed: 2026-07-28 -->
- [x] Run Phase 2 verification: `tsc --noEmit` zero errors, `vitest run` 43/43 pass. <!-- completed: 2026-07-28 -->
- [x] Implement RS archive container decoder with bzip2 support. <!-- completed: 2026-07-28 -->
- [x] Implement map index reader (archive 0, file 5, map_index entry). <!-- completed: 2026-07-28 -->
- [x] Fix opcode 21 bug in definition decoder (boolean flag, no payload). <!-- completed: 2026-07-28 -->
- [x] Implement source importer with importRegion() orchestration. <!-- completed: 2026-07-28 -->
- [x] Add ImportedRegion IR type with members/definitions/requiredModelIds. <!-- completed: 2026-07-28 -->
- [x] Implement model type normalization (types 5-8→4, types 10/11→10). <!-- completed: 2026-07-28 -->
- [x] Wire up all exports (archive-decoder, map-index, importer, ImportedRegion). <!-- completed: 2026-07-28 -->
- [x] Run Phase 3 verification: `tsc --noEmit` zero errors, `vitest run` 82/82 pass. <!-- completed: 2026-07-28 -->
- [x] Implement terrain encoder with procedural height functions. <!-- completed: 2026-07-28 -->
- [x] Implement CacheWriter for block-chained cache format. <!-- completed: 2026-07-28 -->
- [x] Implement terrain exporter (exportTerrain). <!-- completed: 2026-07-28 -->
- [x] Run Phase 4 verification: `tsc --noEmit` zero errors, `vitest run` 100/100 pass. <!-- completed: 2026-07-28 -->
- [x] Implement landscape encoder (encodeLandscape with writeSmart + delta encoding). <!-- completed: 2026-07-29 -->
- [x] Implement ObjectMapper with MappingDatabase (classify, applyMappings, load/save). <!-- completed: 2026-07-29 -->
- [x] Implement landscape exporter (exportLandscape). <!-- completed: 2026-07-29 -->
- [x] Implement region exporter (exportRegion with optional mapper). <!-- completed: 2026-07-29 -->
- [x] Run Phase 5 verification: `tsc --noEmit` zero errors, `vitest run` 137/137 pass. <!-- completed: 2026-07-29 -->
- [x] Implement model encoder (encodeModel with writeSmartSigned, inverse of decodeModel). <!-- completed: 2026-07-29 -->
- [x] Implement model exporter (exportModel: gzip + write to cache index 1). <!-- completed: 2026-07-29 -->
- [x] Write model encoder tests (synthetic models + real cache round-trip). <!-- completed: 2026-07-29 -->
- [x] Write model exporter tests (export → read back → decode → compare). <!-- completed: 2026-07-29 -->
- [x] Wire up encoder/exporter barrel exports. <!-- completed: 2026-07-29 -->
- [x] Run Phase 6 verification: `tsc --noEmit` zero errors, `vitest run` 159/159 pass. <!-- completed: 2026-07-29 -->
- [x] Implement object definition encoder (encodeObjectDefinition, inverse of decodeObjectDefinition). <!-- completed: 2026-07-29 -->
- [x] Implement ID allocation registry (IdRegistry with allocate/free/save/load/validate). <!-- completed: 2026-07-29 -->
- [x] Add custom range constants to limits.ts (CUSTOM_MODEL_START=50000, CUSTOM_OBJECT_START=35000, etc.). <!-- completed: 2026-07-29 -->
- [x] Write definition encoder tests (synthetic + real cache round-trip). <!-- completed: 2026-07-29 -->
- [x] Write ID registry tests (allocation, validation, persistence). <!-- completed: 2026-07-29 -->
- [x] Wire up all new exports. <!-- completed: 2026-07-29 -->
- [x] Run Phase 7 verification: tsc --noEmit zero errors, vitest all passing. <!-- completed: 2026-07-29 -->
- [x] Implement new model decoder (decodeNewModel) for SMF format. <!-- completed: 2026-07-29 -->
- [x] Implement new model encoder (encodeNewModel) for SMF format. <!-- completed: 2026-07-29 -->
- [x] Implement format detection (detectModelFormat) and auto dispatch (decodeModelAuto, encodeModelAuto). <!-- completed: 2026-07-29 -->
- [x] Write new model decoder/encoder/dispatch tests. <!-- completed: 2026-07-29 -->
- [x] Wire up all new exports. <!-- completed: 2026-07-29 -->
- [x] Run Phase 8 verification: tsc --noEmit zero errors, vitest 240/240 pass. <!-- completed: 2026-07-29 -->
- [x] Implement ObjectSearch fluent filter. <!-- completed: 2026-07-29 -->
- [x] Implement ModelSearch fluent filter with lazy cache decoding. <!-- completed: 2026-07-29 -->
- [x] Implement SVG wireframe renderer (orthographic projection, painter's algorithm). <!-- completed: 2026-07-29 -->
- [x] Implement model metadata report generator. <!-- completed: 2026-07-29 -->
- [x] Implement compatibility report generator. <!-- completed: 2026-07-29 -->
- [x] Implement region analyzer. <!-- completed: 2026-07-29 -->
- [x] Implement MappingEditor CRUD helpers. <!-- completed: 2026-07-29 -->
- [x] Implement AssetBrowser facade and barrel exports. <!-- completed: 2026-07-29 -->
- [x] Run Phase 9 verification: tsc --noEmit zero errors, vitest 331/331 pass. <!-- completed: 2026-07-29 -->
- [x] Implement RegionConverter mode-aware import/analyze/convert/export orchestration. <!-- completed: 2026-07-29 -->
- [x] Add terrain and side-by-side region SVG previews. <!-- completed: 2026-07-29 -->
- [x] Add Phase 10 converter and preview tests. <!-- completed: 2026-07-29 -->
- [x] Run Phase 10 verification: tsc --noEmit zero errors, vitest 340/340 pass. <!-- completed: 2026-07-29 -->
- [x] Implement immutable composer crop, translation, combination, and deterministic stitching. <!-- completed: 2026-07-29 -->
- [x] Add terrain/object overlap policies, clipping diagnostics, provenance, fixed-region output, and synthetic tests. <!-- completed: 2026-07-29 -->
- [x] Add map-index validation, encode/decode, upsert/update, relocation, and archive rewrite staging. <!-- completed: 2026-07-29 -->
- [x] Add archive round-trip and partial-final-sector cache-writer regression tests. <!-- completed: 2026-07-29 -->
- [x] Wire composer, archive, and map-index APIs through public barrels. <!-- completed: 2026-07-29 -->
- [x] Run Phase 11 verification: pnpm typecheck zero errors, pnpm test 360/360 pass. <!-- completed: 2026-07-29 -->

### Blocked

- [ ] None.

## Changelog

### 2026-07-29

- Phase 9 completed: visual asset browser — AssetBrowser facade, ObjectSearch/ModelSearch fluent filters, SVG wireframe renderer (orthographic projection, painter's algorithm), model metadata reports, compatibility reports (EXACT/SUBSTITUTE/REMOVE/UNMAPPED), region analyzer, MappingEditor CRUD, 91 new tests, 331 total tests passing.
- Phase 11 completed: immutable region composer with crop/translate/combine/stitch, provenance, overlap and clipping policies, fixed-region output, Java-compatible archive encoding with strict archive/cache validation, map-index encode/upsert/relocation, writer-owned serialized archive rewrite staging, and partial-sector allocation regression coverage.
- Phase 10 completed: headless RegionConverter with mode-aware conversion reports/logs, terrain and side-by-side region SVG previews, validated terrain/landscape export controls, 9 new tests, Vitest timeout raised to 15 seconds for real full-definition fixture tests, 340 total tests passing.
- Phase 8 completed: dual model decoder — SMF format with direct int16/uint16 encoding and 4-byte magic header, decodeNewModel/encodeNewModel, detectModelFormat/decodeModelAuto/encodeModelAuto dispatch, 34 new tests (12 decoder + 11 encoder + 11 dispatch), cross-format round-trip verified, 240 total tests passing.
- Phase 5 completed: landscape encoder (writeSmart, delta-encoded landscape binary format), ObjectMapper with MappingDatabase (JSON-based mapping file, classify/applyMappings with EXACT/SUBSTITUTE/REMOVE/UNMAPPED rules), landscape exporter, region exporter (combined terrain+landscape with optional mapper), 37 new tests. UNMAPPED objects pass through unchanged. writeSmart validates range 0-32767.
- Phase 6 completed: model encoder (encodeModel with writeSmartSigned, inverse of decodeModel), model exporter (exportModel: gzip + write to cache index 1), 22 new tests (19 encoder + 3 exporter), 159 total passing tests. All faces use type 1 encoding (strip/fan compression deferred). Round-trip verified against real cache fixtures. writeSmartSigned range -49152..16383.
- Phase 7 completed: custom asset namespace — IdRegistry (allocate/free/save/load/validate), object definition encoder (encodeObjectDefinition, inverse of decodeObjectDefinition), custom range constants in limits.ts (CUSTOM_MODEL_START=50000, CUSTOM_OBJECT_START=35000, etc.), definition encoder tests and ID registry tests, all new exports wired up. Phase 7 verification: tsc --noEmit zero errors, vitest all passing.

### 2026-07-28

- Plan created.
- Phase 1 format exploration completed via Scout agent.
- Phase 2 completed: IR types, cache reader, model/map/definition decoders, 43 tests.
- Phase 3 completed: archive decoder (bzip2), map index reader, source importer with model-type normalization, ImportedRegion type, opcode 21 fix, 82 tests total.
- Phase 4 planning completed: terrain encoder, cache writer, terrain exporter plan.
- Phase 4 completed: terrain encoder with procedural height functions (noise2d, multiOctaveNoise, proceduralHeight), CacheWriter for block-chained cache format (sector allocation starting at 1), terrain exporter (exportTerrain), 100 tests passing (18 new). Key bug fix: running height must include +240 offset for cross-plane defaults to match decoder state.
