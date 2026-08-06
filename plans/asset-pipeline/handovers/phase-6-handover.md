# Handoff: Asset Pipeline — Phase 6 Complete

**Date:** 2026-07-29
**Working directory:** `/Users/jonas/Developer/RS/tools`
**Session type:** Claude Code session

## What was done

Phase 6 (Static Model Backport) of the asset-pipeline plan is now **complete**. The `@singlescape/tools` package can encode IR `Model` objects to the 2006Scape model binary format and export them to the cache. Round-trip verified: decoded model → encoded → decoded produces identical vertices, faces, colors, alpha, priorities, and texture faces.

### New files created
- `tools/src/cache/model-encoder.ts` — `writeSmartSigned(value)` (variable-length signed encoding, 1-2 bytes, range -49152..16383) and `encodeModel(model)` producing uncompressed model binary data
- `tools/src/exporter/model-exporter.ts` — `exportModel(writer, model, modelFileId)` high-level API (gzip + write to cache index 1)
- `tools/src/cache/__tests__/model-encoder.test.ts` — 19 tests: writeSmartSigned boundary values, round-trip with readSmartSigned, synthetic models (minimal, triangle, alpha, priorities, textures, all-optional), real cache round-trips
- `tools/src/exporter/__tests__/model-exporter.test.ts` — 3 tests: export real model, export synthetic with all fields, export multiple to different file IDs
- `plans/asset-pipeline/phases/phase-6.md` — Phase scope document
- `plans/asset-pipeline/implementation/phase-6-impl.md` — Implementation plan
- `plans/asset-pipeline/handovers/phase-6-handover.md` — This handover doc

### Modified files
- `tools/src/cache/index.ts` — Added model encoder exports
- `tools/src/index.ts` — Added model encoder and model exporter exports
- `plans/asset-pipeline/plan.md` — Phase 6 marked completed, changelog entry
- `plans/asset-pipeline/todo.md` — All Phase 6 tasks marked completed

### Key implementation notes
- **Model binary format**: The encoder inverts `decodeModel` exactly. Data layout matches the decoder's sequential offset computation: vertex X flags → face delta type → alpha (conditional) → tex coord flags (conditional) → priority (conditional) → face vertex index data → face colors → texture triangles → vertex X deltas → vertex Y deltas → vertex Z deltas → 18-byte trailer.
- **Face vertex encoding**: All faces use **type 1** encoding (three absolute deltas from a running base). This is the simplest correct encoding — the decoder handles it identically to compressed types. Strip/fan compression (types 2-4) is deferred and noted as a future optimization for reducing file size.
- **writeSmartSigned**: Variable-length signed integer. Values -64..63 encode as 1 byte (`value + 64`). All other values encode as 2 bytes (`value + 49152` as uint16BE). Valid range: -49152..16383. Throws `RangeError` on out-of-range input.
- **Optional field detection**: Alpha section is present only when at least one face has non-zero alpha. Priority section is present only when at least one face has non-zero priority. Texture triangles section is present only when `textureFaces` array is non-empty. Face labels and vertex skin IDs are not in the IR, so those trailer flags are always 0.
- **Cache placement**: Models are stored at **cache index 1** (confirmed by `model-decoder.test.ts` using `cache.readFile(1, fileId)`). The exporter writes gzip-compressed model data to index 1.

### Verification
```
pnpm typecheck  — zero errors
pnpm test       — 159/159 pass (18 test files)
```

## Current state

| Phase | Status | Tests |
|-------|--------|-------|
| 1 — Inspect formats | completed | — |
| 2 — Neutral IR | completed | 43 |
| 3 — Source importer | completed | 82 |
| 4 — Terrain exporter | completed | 100 |
| 5 — Object mapping & landscape export | completed | 137 |
| 6 — Static model backport | completed | 159 |

## What's next

**Phase 7: Custom asset namespace** — Safely add new model/object definition ranges (50000+ for custom models, 60000+ for custom objects). This would involve:
1. Reading `tools/docs/MODELS_AND_OBJECTS.md` for reserved range details
2. Verifying actual client limits from engine source (hard-coded ID caps)
3. Creating `plans/asset-pipeline/phases/phase-7.md` and `implementation/phase-7-impl.md`
4. Implementing ID allocation/registry system
5. Testing that custom-range models and objects load correctly in the client

### Known open issues (deferred)
- **Face vertex compression**: Type 1 encoding works but produces larger files than the original. Types 2/3/4 (strip/fan) could be added as an optimization.
- **Terrain height semantics**: Opcode 1 on planes >0 should incorporate prior-plane height. IR height sign convention should be documented.
- **Morphing objects**: Opcode 77 definitions have child IDs requiring varbit/varp state. Phase 3 exposes morph metadata but doesn't resolve active children.
- **Smart encoding range**: Landscape format limits object IDs to 0-32767. IDs beyond this range (e.g., custom objects at 60000+) would need a different encoding or the format may not support them.
- **Mapping database**: No initial mapping data exists yet. The first use case would be importing a region, exporting it, and iteratively building the mapping database based on what the 2006 client can render.

## Key file paths

| Path | Purpose |
|------|---------|
| `tools/src/cache/model-encoder.ts` | Model encoder + writeSmartSigned |
| `tools/src/exporter/model-exporter.ts` | High-level model export API |
| `tools/src/cache/model-decoder.ts` | Model decoder (inverse) |
| `tools/src/cache/cache-writer.ts` | Block-chained cache file writer |
| `tools/src/ir/types.ts` | IR types (Model, Vertex, Face, TextureFace) |
| `tools/src/limits.ts` | Engine limits and constants |
| `plans/asset-pipeline/plan.md` | Top-level plan |
| `plans/asset-pipeline/todo.md` | Task tracking |

## Suggested skills

- **`/scout`** — Explore the engine codebase for Phase 7 ID range limits
- **`/doc-explorer`** — Write the Phase 7 plan documents
- **`/implementer`** — Implement ID allocation registry
- **`/code-review`** — Review Phase 6 changes before starting Phase 7
- **`/verify`** — Verify model export works end-to-end in the actual client
