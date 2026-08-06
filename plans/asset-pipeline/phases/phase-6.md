```yaml
type: planning
entity: phase
plan: asset-pipeline
phase: 6
status: active
created: 2026-07-29
updated: 2026-07-29
```

# Phase 6: Static Model Backport

## Objective

Build a model encoder (inverse of `decodeModel`) and model exporter so that IR `Model` objects can be written to the 2006Scape cache format. A simple static model (rock, tree, wall) imported from a source cache must survive the round-trip: decode → encode → decode produces identical geometry.

## Scope

### Includes
- **Model encoder** (`encodeModel`) — inverse of `decodeModel` in `model-decoder.ts`, produces uncompressed binary data matching the 2006Scape model format
- **Model exporter** (`exportModel`) — high-level API: gzip-compress encoded model, write to cache index 1
- Round-trip test: decode real model from cache → encode → decode → identical vertices, faces, colors, alpha, priorities
- Unit tests for encoder with synthetic models

### Excludes (deferred to later phases)
- Face vertex strip/fan compression (types 2/3/4) — all faces use type 1 encoding. Works correctly, produces larger files. Optimization deferred.
- Custom asset namespace (Phase 7)
- Dual model decoder (Phase 8)
- Model/object definition export (separate from raw geometry)
- Textured face encoding (texture triangles are preserved but not yet a focus)

## Prerequisites
- [x] Phase 2 model decoder (`decodeModel`) reads the 18-byte trailer and all vertex/face/texture data
- [x] Phase 4 `CacheWriter` writes block-chained cache files
- [x] IR types: `Model`, `Vertex`, `Face`, `TextureFace`

## Key Technical Decisions

### Binary format

The model format is the inverse of `decodeModel`. The data layout (matching `model-decoder.ts` exactly):

```
Offset 0:                        Vertex X flags (1 byte per vertex)
  + vertexCount:                 Face delta type (1 byte per face, always 1)
  + [faceCount if alphaFlag=255]: Per-face alpha (1 byte each)
  + [faceCount if hasFaceLabels]: Per-face labels (1 byte each)
  + [faceCount if hasTexCoords]: Texture coord flags (1 byte each)
  + [vertexCount if hasVertexLabels]: Per-vertex skin IDs (1 byte each)
  + [faceCount if hasVisibility]: Per-face priority (1 byte each)
  + extraDataSize:               Face color (uint16BE per face = faceCount * 2)
  + faceCount * 2:               Texture triangles (6 bytes each)
  + texTriCount * 6:             Vertex X deltas (smartSigned per vertex with deltas)
  + vertexDataSize:              Vertex Y deltas (smartSigned per vertex with deltas)
  + faceDataSize1:               Vertex Z deltas (smartSigned per vertex with deltas)
  + faceDataSize2:               18-byte trailer header
  = totalLength:                 END
```

### SmartSigned encoding

Variable-length signed integer encoding (inverse of `readSmartSigned`):
- Values -64 to 63: 1 byte, encoded as `(value + 64)`
- Values outside that range: 2 bytes, encoded as `(value + 49152)` as uint16BE
- Valid range: -49152 to 16383

### Vertex encoding

5 streams, matching decoder exactly:
1. **Flags stream**: 1 byte per vertex. Bit 0 = X changed, bit 1 = Y changed, bit 2 = Z changed
2. **X delta stream**: smartSigned delta for X (only when bit 0 set)
3. **Y delta stream**: smartSigned delta for Y (only when bit 1 set)
4. **Z delta stream**: smartSigned delta for Z (only when bit 2 set)
5. **Skin stream**: 1 byte per vertex (only when vertex labels present)

### Face vertex encoding

All faces use type 1 (three absolute deltas from running base). The decoder handles all four types (1-4) for strip/fan compression, but the encoder starts with the simplest correct approach. Type 1 always works:

```
For each face:
  write type byte = 1
  writeSmartSigned(face.a - faceBase); faceBase = face.a
  writeSmartSigned(face.b - faceBase); faceBase = face.b
  writeSmartSigned(face.c - faceBase); faceBase = face.c
```

### Trailer

18-byte header at `totalLength - 18`:
```
Offset 0:  vertexCount (uint16BE)
Offset 2:  faceCount (uint16BE)
Offset 4:  texTriCount (uint8)
Offset 5:  hasTexCoords (uint8, 0 or 1)
Offset 6:  alphaFlag (uint8, 255 if per-face alpha, else 0)
Offset 7:  hasVisibility (uint8, 0 or 1)
Offset 8:  hasFaceLabels (uint8, 0 or 1)
Offset 9:  hasVertexLabels (uint8, 0 or 1)
Offset 10: vertexDataSize (uint16BE)
Offset 12: faceDataSize1 (uint16BE)
Offset 14: faceDataSize2 (uint16BE)
Offset 16: extraDataSize (uint16BE)
```

### Cache placement

Models live at **cache index 1** (confirmed by `model-decoder.test.ts` using `cache.readFile(1, fileId)`). The exporter writes gzip-compressed model data to index 1.

### Optional field handling

| IR field | When present | Trailer flag |
|----------|-------------|--------------|
| `colors` | Always (decoder always produces it) | N/A (always written) |
| `alpha` | Array with any non-zero value | `alphaFlag = 255` |
| `priorities` | Array with any non-zero value | `hasVisibility = 1` |
| `textureFaces` | Non-empty array | `texTriCount > 0` |

Face labels and vertex skin IDs are not part of the IR (not decoded by `decodeModel` into IR fields), so `hasFaceLabels = 0` and `hasVertexLabels = 0` always.

## Deliverables

- [ ] `src/cache/model-encoder.ts` — `encodeModel(model: Model): Buffer`
- [ ] `src/exporter/model-exporter.ts` — `exportModel(writer, model, fileId)`
- [ ] `src/cache/__tests__/model-encoder.test.ts` — Encoder tests (synthetic + round-trip)
- [ ] `src/exporter/__tests__/model-exporter.test.ts` — Exporter integration tests
- [ ] Updated barrel exports in `src/cache/index.ts` and `src/index.ts`
- [ ] Phase plan docs: `phases/phase-6.md`, `implementation/phase-6-impl.md`
- [ ] Updated `plan.md` (Phase 6 row), `todo.md` (Phase 6 tasks)
- [ ] `tsc --noEmit` zero errors
- [ ] `vitest run` all tests pass (137 existing + new)

## Acceptance Criteria

- [ ] `encodeModel(model)` produces binary data that `decodeModel` reads back to identical vertices, faces, colors, alpha, priorities, and textureFaces.
- [ ] Round-trip preserves: all vertex coordinates, face indices, face colors, alpha values, priorities, texture triangles.
- [ ] `exportModel(writer, model, fileId)` writes gzipped model to cache index 1.
- [ ] Reading back the exported file from cache → gunzip → decodeModel produces the original model.
- [ ] Synthetic model with no alpha/priorities/textures encodes and decodes correctly.
- [ ] Synthetic model with all optional fields encodes and decodes correctly.
- [ ] `tsc --noEmit` passes with zero errors.
- [ ] `vitest run` passes all existing and new tests.
