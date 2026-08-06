# Handoff: Asset Pipeline — Phase 8 Complete

**Date:** 2026-07-29
**Working directory:** `/Users/jonas/Developer/RS/tools`
**Session type:** Claude Code session

## What was done

Phase 8 (Dual Model Decoder) of the asset-pipeline plan is now **complete**. The `@singlescape/tools` package has a second model binary format — SMF (SingleScape Model Format) — alongside the original 2006Scape delta-encoded format. Both formats produce the same `Model` IR. A dispatch layer detects format from buffer content and routes to the correct decoder/encoder. All 240 tests pass. Zero TypeScript errors.

### New files created

| File | Purpose |
|------|---------|
| `tools/src/cache/new-model-decoder.ts` | `decodeNewModel(data: Buffer): Model` — decodes SMF format. Also exports `SMF_MAGIC` (4-byte header `0x53 0x4d 0x46 0x01`) and `SMF_HEADER_SIZE` (11). |
| `tools/src/cache/new-model-encoder.ts` | `encodeNewModel(model: Model): Buffer` — encodes Model IR into SMF format. |
| `tools/src/cache/model-dispatch.ts` | `detectModelFormat(data): ModelFormat` — checks magic bytes. `decodeModelAuto(data): Model` — dispatches to legacy or SMF decoder. `encodeModelAuto(model, format): Buffer` — encodes to requested format. `ModelFormat` type (`"legacy" | "smf"`). |
| `tools/src/cache/__tests__/new-model-decoder.test.ts` | 12 tests: magic validation, minimal model, triangle, alpha, priorities, texture faces, all optional fields, out-of-range vertex rejection, truncated buffer, negative coordinates. |
| `tools/src/cache/__tests__/new-model-encoder.test.ts` | 11 tests: minimal model, magic presence, round-trips (single vertex, triangle, alpha, priorities, textures, all fields), alpha omission when zeros, cross-format round-trip against real cache fixtures (single + multiple). |
| `tools/src/cache/__tests__/model-dispatch.test.ts` | 11 tests: format detection (SMF, legacy, short buffer, empty), auto dispatch (SMF→new, legacy→old), real cache models via legacy path, encode auto (legacy, SMF), cross-format round-trip, SMF direct decode. |
| `plans/asset-pipeline/phases/phase-8.md` | Phase scope document with engine analysis, SMF format spec, dispatch design. |
| `plans/asset-pipeline/implementation/phase-8-impl.md` | Implementation plan (7 steps). |

### Modified files

| File | Change |
|------|--------|
| `tools/src/cache/index.ts` | Added exports: `decodeNewModel`, `SMF_MAGIC`, `SMF_HEADER_SIZE`, `encodeNewModel`, `detectModelFormat`, `decodeModelAuto`, `encodeModelAuto`, `ModelFormat`. |
| `tools/src/index.ts` | Re-exported all new model format symbols. |
| `plans/asset-pipeline/plan.md` | Phase 8 marked completed, changelog entry. |
| `plans/asset-pipeline/todo.md` | Phase 8 tasks completed, active phase updated to Phase 9. |

### Key implementation notes

- **SMF format**: Direct encoding — all vertex coordinates as int16BE, all face vertex indices as uint16BE, all colors as uint16BE. No delta encoding, no smart encoding, no strip/fan compression. Simpler to generate and reason about, at the cost of slightly larger buffers.
- **Magic header**: `SMF\x01` (bytes 0x53, 0x4D, 0x46, 0x01) as first 4 bytes. The legacy 2006 format never starts with 0x53 (first byte is a vertex flag 0-7), so no collision risk.
- **Same IR**: Both formats produce/consume the identical `Model` interface. The format is purely a binary-level concern — the IR remains format-agnostic.
- **Vertex coordinates**: SMF uses signed int16BE (-32768..32767), matching the coordinate range observed in real 2006Scape models (all vertex coords < 10000 in practice).
- **Face vertex indices**: uint16BE direct values. The decoder validates all indices are < vertexCount, throwing on out-of-range.
- **Optional fields**: Alpha and priorities are present only when at least one value is non-zero (matching the legacy encoder's behavior). Texture faces are present when texTriCount > 0.
- **Cross-format verified**: A real model decoded from the 2006 cache via `decodeModel` → encoded as SMF via `encodeNewModel` → decoded via `decodeNewModel` produces an identical Model IR (vertices, faces, colors, alpha, priorities, textureFaces).

### Engine analysis findings

- The 2006Scape client has no model format version detection — `Model.method460` always assumes the 18-byte trailer format.
- Models live at cache index 2 (decompressors[1]), gzip-compressed.
- `Model.method462(id)` fetches/decodes by ID from `Class21[]` array.
- On the client side, dispatching custom models would require: (a) detecting the SMF magic header in `method460`, or (b) using an ID threshold (50000+) in `method462`. Both approaches are documented in `phase-8.md`.

### Deferred from Phase 8

- **Client-side Java changes**: Model.java dispatch to handle SMF format. Requires a new decode path in `method460`/`Model(int)` constructor.
- **Face vertex compression** (types 2-4 strip/fan): Still deferred from Phase 6. The SMF format doesn't need it since face indices are direct uint16 values.
- **NPC/item definition encoders**: Follow the same opcode-driven pattern as `encodeObjectDefinition`. Deferred to later phases.
- **Morphing objects** (opcode 77 children): Varbit/varp resolution still deferred from Phase 3.

### Verification

```
pnpm typecheck  — zero errors
pnpm test       — 240/240 pass (24 test files, 34 new for Phase 8)
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
| 8 — Dual model decoder | completed | 240 |
| 9 — Visual asset browser | **next** | — |

## What's next

**Phase 9: Visual asset browser** — Build search, preview, compatibility reports, import and mapping controls. Per the ROADMAP.md, this involves:

1. Reading ROADMAP.md for Phase 9 details
2. Creating `plans/asset-pipeline/phases/phase-9.md` and `implementation/phase-9-impl.md`
3. Building a browser/search UI for the model and object definition database
4. Rendering model previews (wireframe or simple 3D)
5. Compatibility reports showing EXACT/CONVERTIBLE/SUBSTITUTE/MANUAL/UNSUPPORTED counts
6. Import and mapping controls

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
| `tools/src/cache/new-model-decoder.ts` | SMF format decoder |
| `tools/src/cache/new-model-encoder.ts` | SMF format encoder |
| `tools/src/cache/model-dispatch.ts` | Format detection and auto dispatch |
| `tools/src/cache/model-decoder.ts` | Legacy 2006 format decoder |
| `tools/src/cache/model-encoder.ts` | Legacy 2006 format encoder |
| `tools/src/ir/types.ts` | Model, Vertex, Face, TextureFace types |
| `tools/src/limits.ts` | Engine limits and custom-range constants |
| `plans/asset-pipeline/plan.md` | Top-level plan |
| `plans/asset-pipeline/todo.md` | Task tracking |
| `plans/asset-pipeline/phases/phase-8.md` | Phase 8 scope with SMF format spec |
| `plans/asset-pipeline/implementation/phase-8-impl.md` | Phase 8 implementation plan |

## Suggested skills

- **`/scout`** — Explore codebase for Phase 9 visual asset browser requirements
- **`/doc-explorer`** — Write Phase 9 plan documents
- **`/implementer`** — Implement visual asset browser
- **`/code-review`** — Review Phase 8 changes before starting Phase 9
- **`/verify`** — Verify SMF-encoded models load correctly if client-side dispatch is implemented
