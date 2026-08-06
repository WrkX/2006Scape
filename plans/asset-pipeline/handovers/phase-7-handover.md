# Handoff: Asset Pipeline — Phase 7 Complete

**Date:** 2026-07-29
**Working directory:** `/Users/jonas/Developer/RS/tools`
**Session type:** Claude Code session

## What was done

Phase 7 (Custom Asset Namespace) of the asset-pipeline plan is now **complete**. The `@singlescape/tools` package has an ID allocation registry for managing custom ID ranges, an object definition encoder (inverse of `decodeObjectDefinition`), a definition exporter to write custom definitions to the cache, and safe custom-range constants extracted from engine analysis. All 206 tests pass. Zero TypeScript errors.

### New files created

| File | Purpose |
|------|---------|
| `tools/src/cache/definition-encoder.ts` | `encodeObjectDefinition()` — opcode-driven binary format encoder (inverse of `decodeObjectDefinition`). `writeDefinitionFiles()` — produce dense loc.dat/loc.idx buffers with 2-byte count prefix. |
| `tools/src/registry/id-registry.ts` | `IdRegistry` class — allocate/free/save/load/validate for custom ID ranges with per-type separate allocation cursors |
| `tools/src/registry/types.ts` | `AssetType`, `IdRange`, `Allocation`, `RegistryState` interfaces |
| `tools/src/exporter/definition-exporter.ts` | `exportObjectDefinitions()` — high-level API to write custom object definitions to cache archive 0, files 2/3 (dat/idx) |
| `tools/src/cache/__tests__/definition-encoder.test.ts` | 22 tests: synthetic round-trips (minimal, name, model IDs, model types, all opcodes), real cache fixture round-trip (every definition in loc.dat encoded and decoded), writeDefinitionFiles structure, gap handling, round-trip through writeDefinitionFiles |
| `tools/src/registry/__tests__/id-registry.test.ts` | 19 tests: sequential allocation, range exhaustion, free/reallocate, separate per-type cursors, validate (no errors, overlapping ranges, out-of-range), save/load round-trip, createDefault ranges, double-allocation prevention |
| `tools/src/exporter/__tests__/definition-exporter.test.ts` | 6 tests: synthetic definition export, gaps in definition array, empty array, high ID allocation, real fixture definition round-trip |
| `plans/asset-pipeline/phases/phase-7.md` | Phase scope document with engine analysis findings, risk assessment, safe range strategy |
| `plans/asset-pipeline/implementation/phase-7-impl.md` | Implementation plan (7 steps) |

### Modified files

| File | Change |
|------|--------|
| `tools/src/limits.ts` | Added `CUSTOM_MODEL_START=50000`, `CUSTOM_OBJECT_START=35000`, `CUSTOM_NPC_START=35000`, `CUSTOM_ITEM_START=35000`, `MAX_CUSTOM_ID=65535` |
| `tools/src/cache/index.ts` | Added `encodeObjectDefinition`, `writeDefinitionFiles` exports |
| `tools/src/index.ts` | Added definition encoder, definition exporter, IdRegistry, types exports |
| `plans/asset-pipeline/plan.md` | Phase 7 marked completed, changelog entry |
| `plans/asset-pipeline/todo.md` | Phase 7 tasks completed, active phase updated to Phase 8 |

### Key implementation notes

- **Definition encoder**: Emits opcodes only for values differing from defaults (matching original format behavior — decoder applies defaults first, then overwrites from opcodes). All 24 opcodes from the decoder are supported.
- **writeDefinitionFiles**: Produces dense dat layout with 2-byte count prefix (matching the real loc.dat structure that `readDefinitionIndex` expects with `pos=2`). Gaps (IDs with no definition) get size 0 in idx.
- **IdRegistry**: Separate allocation cursors per asset type, sequential allocation within configured ranges, JSON serialization for persistence. `createDefault()` pre-configures standard ranges from `limits.ts`.
- **Definition exporter**: Writes to cache archive 0, files 2/3 (dat/idx), matching where `ObjectDef.forID()` reads from via the `streamLoader` archive.
- **Engine analysis**: Object definitions, NPCs, and items all use `readUnsignedWord()` for their max count — hard 16-bit ceiling at 65535. Landscape encoding uses unsigned smart (max 32767 per delta), which constrains custom object start to 35000+ (within 32767 of original max ~25000).
- **Optional field detection matches decoder**: opcode 17 (solid=false), 18 (impenetrable=false), 21 (contouredGround) are boolean-only flags with no payload. opcode 39 (contrast) stores `contrast * 5` as int8, matching the decoder's `readSignedByte() / 5` round-trip. Opcode 19 (interactive) writes `uint8(1)`.

### Deferred from Phase 7

- **Custom objects at 60000**: Starting at 60000 (as originally proposed in MODELS_AND_OBJECTS.md) requires client-side landscape encoding extension — the unsigned smart delta encoding caps at 32767, but delta from original max (~25000) to 60000 is ~35000. The safe start at 35000 avoids this issue. Extension noted for Phase 8.
- **NPC/item definition encoders**: Follow the same opcode-driven pattern as the object encoder. Deferred to later phases.
- **Face vertex compression** (types 2-4 strip/fan): Still deferred from Phase 6.
- **Morphing objects** (opcode 77 children): Varbit/varb resolution still deferred from Phase 3.

### Verification

```
pnpm typecheck  — zero errors
pnpm test       — 206/206 pass (21 test files, 47 new for Phase 7)
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
| 7 — Custom asset namespace | completed | 206 |
| 8 — Dual model decoder | **next** | — |

## What's next

**Phase 8: Dual model decoder** — Support original and newer/custom model formats side-by-side. Per MODELS_AND_OBJECTS.md Strategy B: a model request dispatches to OldModelDecoder or NewModelDecoder based on the model ID or format flag. This would involve:

1. Reading `tools/docs/MODELS_AND_OBJECTS.md` for dual decoder design details
2. Creating `plans/asset-pipeline/phases/phase-8.md` and `implementation/phase-8-impl.md`
3. Implementing the client-side dispatch mechanism (ID threshold or format flag)
4. Implementing the new model format decoder (if needed, e.g. for OSRS-style models)
5. Implementing the new model format encoder
6. Mapping between old and new vertex/face representations
7. Testing that custom-range models (50000+) dispatch correctly

### Known open issues (deferred)

- **Face vertex compression**: Type 1 encoding works but produces larger files than the original. Types 2/3/4 (strip/fan) could be added as an optimization.
- **Terrain height semantics**: Opcode 1 on planes >0 should incorporate prior-plane height. IR height sign convention should be documented.
- **Morphing objects**: Opcode 77 definitions have child IDs requiring varbit/varp state. Phase 3 exposes morph metadata but doesn't resolve active children.
- **Smart encoding range**: Landscape format limits object ID deltas to 0-32767. Custom objects at 35000+ are within range, but 60000+ would exceed the limit without client-side changes.
- **Custom objects at 60000**: Starting custom object IDs at 60000 (as originally planned) requires extending the landscape encoding or client-side dispatching. Currently capped at 35000+ to avoid delta overflow.
- **NPC/item definition encoders**: Follow the same pattern as `encodeObjectDefinition`. Deferred to a later phase.
- **Mapping database**: No initial mapping data exists yet. The first use case would be importing a region, exporting it, and iteratively building the mapping database based on what the 2006 client can render.

## Key file paths

| Path | Purpose |
|------|---------|
| `tools/src/cache/definition-encoder.ts` | Object definition encoder + writeDefinitionFiles |
| `tools/src/registry/id-registry.ts` | ID allocation registry |
| `tools/src/registry/types.ts` | Registry type definitions |
| `tools/src/exporter/definition-exporter.ts` | High-level definition export API |
| `tools/src/limits.ts` | Engine limits and custom-range constants |
| `tools/src/cache/cache-writer.ts` | Block-chained cache file writer |
| `tools/src/ir/types.ts` | IR types (ObjectDefinition, Model, etc.) |
| `plans/asset-pipeline/plan.md` | Top-level plan |
| `plans/asset-pipeline/todo.md` | Task tracking |
| `plans/asset-pipeline/phases/phase-7.md` | Phase 7 scope with engine analysis |
| `plans/asset-pipeline/implementation/phase-7-impl.md` | Phase 7 implementation plan |
| `tools/docs/MODELS_AND_OBJECTS.md` | Dual decoder design background |

## Suggested skills

- **`/scout`** — Explore engine source for Phase 8 dual model decoder limits and format differences
- **`/doc-explorer`** — Write Phase 8 plan documents
- **`/implementer`** — Implement dual model decoder
- **`/code-review`** — Review Phase 7 changes before starting Phase 8
- **`/verify`** — Verify custom-range definition export works end-to-end in the actual client
