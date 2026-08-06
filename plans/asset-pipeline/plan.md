---
type: planning
entity: plan
plan: asset-pipeline
status: active
created: 2026-07-28
updated: 2026-07-29
---

# Plan: asset-pipeline

## Objective

Build a TypeScript-first asset conversion pipeline under `tools/` that can read 2006Scape cache formats, normalize map/model/object data into an intermediate representation, and export SingleScape-compatible assets. The pipeline must run on macOS with Node.js and pnpm, and integrate with the existing root workspace.

## Motivation

The `tools/docs/` design documents describe a 13-phase asset pipeline but no code exists yet. The 2006Scape engine has tightly-coupled cache format assumptions (opcode-driven definitions, block-based cache, 16-bit model IDs, 104x104 tile grids, 50 texture limit, 4096 vertex render cap). A standalone TypeScript toolchain is needed to inspect these formats, build a neutral IR, and eventually convert modern/custom assets into the legacy client format.

## Requirements

### Functional

- [ ] The tools workspace builds and tests independently from the game engine.
- [ ] Cache format decoders can read the 2006Scape `main_file_cache` and all index files.
- [ ] Model format decoder reads the 18-byte header trailer and all vertex/face/texture data.
- [ ] Map format decoder reads terrain (m*.gz) and landscape object (l*.gz) files.
- [ ] Object/NPC/Item definition decoders read the opcode-driven dat/idx format.
- [ ] All decoded data normalizes into a TypeScript IR (Region, Plane, Tile, WorldObject, Model, etc.).
- [ ] IR serializes to/from JSON with deterministic output.
- [ ] Hard-coded engine limits are captured as named constants.

### Non-Functional

- [ ] TypeScript strict mode, zero errors.
- [ ] Vitest for all decoders and IR round-trip tests.
- [ ] Each decoder is independently testable against fixture data.
- [ ] No runtime dependency on the Java engine.

## Scope

### In Scope

- pnpm workspace package under `tools/`.
- Cache, model, map, and definition format decoders.
- Neutral IR types and JSON serialization.
- Hard-coded limit registry.
- Fixture-based tests using real cache data.

### Out of Scope

- Visual editor or UI.
- Source importer for modern OSRS caches (Phase 3+).
- Terrain exporter to legacy format (Phase 4+).
- Model backporting or dual decoder (Phase 6+).
- Changes to the Java engine.

## Definition of Done

- [ ] `tools/` is a pnpm workspace package that builds with `pnpm build`.
- [ ] All IR types are defined and tested with round-trip JSON serialization.
- [ ] Cache decoder reads `main_file_cache.dat` + idx files and extracts entries by ID.
- [ ] Model decoder reads the 2006Scape model header and vertex/face data.
- [ ] Map decoder reads terrain and landscape files.
- [ ] Definition decoders read loc/npc/obj dat+idx files.
- [ ] All limits from the engine are captured as constants.
- [ ] Tests pass with `pnpm test`.

## Phases

| Phase | Title | Scope | Status |
|-------|-------|-------|--------|
| 1 | Inspect 2006Scape formats | Document cache format, model format, map format, definition formats, and hard-coded limits from engine source. | completed |
| 2 | Neutral map/model IR | TypeScript package with IR types, JSON serialization, cache decoder, model decoder, map decoder, definition decoders, limit constants, and tests. | completed |
| 3 | Source importer | Read one OSRS region and extract terrain, object placements, definitions, required model IDs. | completed |
| 4 | Terrain exporter | Convert terrain to legacy format, render one imported region in the old client. | completed |
| 5 | Object mapping & landscape export | Object mapper with mapping database, landscape encoder/exporter, region exporter. | completed |
| 6 | Static model backport | Model encoder (inverse of decodeModel), model exporter to cache index 1, round-trip verified. | completed |
| 7 | Custom asset namespace | ID allocation registry, object definition encoder, custom range constants. | completed |
| 8 | Dual model decoder | SMF format with direct encoding, format detection, auto dispatch, cross-format round-trip verified. | completed |
| 9 | Visual asset browser | AssetBrowser facade, ObjectSearch/ModelSearch fluent filters, SVG wireframe preview, model reports, compatibility reports, region analysis, mapping editor. | completed |
| 10 | Region converter UI | Headless RegionConverter orchestration, conversion modes, diagnostics/logs, region SVG previews, and validated terrain/landscape export controls. | completed |
| 11 | Region composer | Crop, translate, stitch, combine, custom coordinates, and map-index-aware relocation. | completed |

## Risks & Open Questions

| Risk/Question | Impact | Mitigation |
|---------------|--------|------------|
| Cache fixture data needed for tests | Tests cannot run without real cache files | Copy minimal fixtures from engine/server/data/cache/ |
| Model format has obfuscated field names in engine source | Decoder may have subtle bugs | Cross-reference decoded models with known in-game objects |
| Opcode-driven definition format has version-sensitive opcodes | New opcodes could break decoder | Start with 2006-era opcode set, flag unknowns |
| Texture limit is only 50 | Modern assets use far more textures | Phase 7+ will address custom asset namespace |

## Changelog

### 2026-07-28

- Plan created from tools/docs/ design documents and engine codebase exploration.
- Phase 1 completed: comprehensive format documentation produced from Scout agent analysis.
- Phase 2 completed: `@singlescape/tools` package built with IR types, cache reader, model decoder, map decoder, definition decoders, engine limits, and 43 passing tests.
- Phase 3 completed: source importer with archive decoder (bzip2), map index reader, object definition resolution with model-type normalization, ImportedRegion IR type, and 82 passing tests (39 new). Also fixed opcode 21 bug in definition decoder.
- Phase 4 completed: terrain encoder (inverse of decodeTerrain), CacheWriter for block-chained cache format, terrain exporter (exportTerrain), and 100 passing tests (18 new). Key fix: running height must include +240 offset for cross-plane defaults.
- Phase 5 completed: landscape encoder (encodeLandscape with writeSmart/delta encoding), ObjectMapper with MappingDatabase (classify/applyMappings), landscape exporter (exportLandscape), region exporter (exportRegion with optional mapper), and 137 passing tests (37 new). writeSmart has range validation (0-32767). UNMAPPED objects pass through applyMappings unchanged.

### 2026-07-29

- Phase 6 completed: model encoder (encodeModel with writeSmartSigned, inverse of decodeModel), model exporter (exportModel: gzip + write to cache index 1), 22 new tests (19 encoder + 3 exporter), 159 total passing tests. All faces use type 1 encoding (strip/fan compression deferred). Round-trip verified against real cache fixtures. writeSmartSigned range -49152..16383.
- Phase 7 completed: custom asset namespace — ID allocation registry (IdRegistry with allocate/free/save/load/validate), object definition encoder (encodeObjectDefinition, inverse of decodeObjectDefinition), custom range constants in limits.ts (CUSTOM_MODEL_START=50000, CUSTOM_OBJECT_START=35000, etc.), encoder and registry tests, all new exports wired up.
- Phase 8 completed: dual model decoder — SMF (SingleScape Model Format) with direct int16/uint16 encoding and 4-byte magic header ("SMF\x01"), new model decoder (decodeNewModel) and encoder (encodeNewModel), format detection (detectModelFormat) and auto dispatch (decodeModelAuto, encodeModelAuto), 34 new tests (12 decoder + 11 encoder + 11 dispatch), cross-format round-trip verified against real cache fixtures, 240 total passing tests.
- Phase 9 completed: visual asset browser — AssetBrowser facade (cache loading, lazy definition decoding), ObjectSearch fluent filter (name/id/interactive/solid/animation/models/size), ModelSearch fluent filter (id range/vertex/face count/format/alpha/textures with lazy decode), SVG wireframe renderer (orthographic projection, painter's algorithm depth sorting, configurable options), model metadata reports (vertex/face/texture counts, bounding box, color range), compatibility reports (EXACT/SUBSTITUTE/REMOVE/UNMAPPED counts with deduped ID lists), region analyzer (mapped/unmapped/custom model counts with unmapped detail breakdown), MappingEditor CRUD (set/remove/get/list/importEntries/validate/build), 91 new tests (16 object-search + 12 model-search + 9 wireframe + 10 model-report + 7 compat-report + 8 region-analyzer + 17 mapping-editor + 12 asset-browser), 331 total passing tests.
- Phase 10 completed: headless RegionConverter with strict/approximate/backport/2006-style decision policies, provenance-preserving reports/logs, terrain and side-by-side region SVG previews, validated terrain/landscape export controls, 9 new tests, Vitest timeout raised to 15 seconds for real full-definition fixture tests, 340 total passing tests.
- Phase 11 completed: immutable region composer with crop/translate/combine/stitch, provenance, overlap and clipping policies, fixed-region output, Java-compatible archive encoding and strict archive/cache validation, map-index encode/upsert/relocation, writer-owned serialized archive rewrite staging, and partial-sector allocation regression coverage (360 tests passing).
