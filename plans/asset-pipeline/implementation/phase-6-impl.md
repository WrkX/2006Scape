```yaml
type: planning
entity: implementation-plan
plan: asset-pipeline
phase: 6
status: active
created: 2026-07-29
updated: 2026-07-29
```

# Phase 6 Implementation Plan: Static Model Backport

## Overview

Build `encodeModel` (inverse of `decodeModel`) and `exportModel` (gzip + write to cache index 1). The encoder writes the exact binary layout the decoder reads: 5 vertex streams, face vertex index stream, color/alpha/priority arrays, texture triangles, and the 18-byte trailer header. All faces use type 1 encoding (three absolute deltas from running base) — strip/fan compression (types 2-4) is deferred.

## Step 1: Model encoder

**Files:**
- `tools/src/cache/model-encoder.ts`

Functions:
```ts
export function writeSmartSigned(value: number): number[]
export function encodeModel(model: Model): Buffer
```

`writeSmartSigned(value)`:
- Assert: value in range -49152..16383
- If value in -64..63: return `[value + 64]`
- Else: return two bytes of `(value + 49152)` as big-endian uint16

`encodeModel(model)`:
1. Compute counts: vertexCount, faceCount, texTriCount
2. Determine optional field flags:
   - `alphaFlag = 255` if model.alpha has any non-zero value, else 0
   - `hasVisibility = 1` if model.priorities has any non-zero value, else 0
   - `hasFaceLabels = 0` (not in IR)
   - `hasVertexLabels = 0` (not in IR)
   - `hasTexCoords = 1` if texTriCount > 0, else 0
3. Compute section offsets (same order as decoder):
   - VERTEX_X = 0, size = vertexCount
   - FACE_DELTA_TYPE = vertexCount, size = faceCount
   - ALPHA (conditional), size = faceCount
   - FACE_LABELS (conditional), size = faceCount
   - TEX_COORDS (conditional), size = faceCount
   - VERTEX_SKIN (conditional), size = vertexCount
   - PRIORITY (conditional), size = faceCount
   - FACE_IDX_DELTA = end of per-vertex/face metadata, size = extraDataSize (computed later)
   - FACE_COLOR = FACE_IDX_DELTA + extraDataSize, size = faceCount * 2
   - TEX_TRI = FACE_COLOR + faceCount * 2, size = texTriCount * 6
   - VERT_DELTA = TEX_TRI + texTriCount * 6, size = vertexDataSize (computed)
   - FACE_DATA1 = VERT_DELTA + vertexDataSize, size = faceDataSize1 (computed)
   - FACE_DATA2 = FACE_DATA1 + faceDataSize1, size = faceDataSize2 (computed)
4. Compute vertex streams:
   - Flags + X deltas: track prevX, write flag byte per vertex (bit 0 if dx != 0), append smartSigned(dx) to X stream
   - Y deltas: same with bit 1, append to Y stream
   - Z deltas: same with bit 2, append to Z stream
5. Compute face vertex index stream (all type 1):
   - For each face: writeSmartSigned(face.a - faceBase), faceBase = face.a; same for b, c
6. Write sections to buffer in order
7. Write 18-byte trailer at end

**Verify:** `tsc --noEmit` passes.

## Step 2: Model encoder tests

**Files:**
- `tools/src/cache/__tests__/model-encoder.test.ts`

Tests:
- `writeSmartSigned` encodes 0, 63, -64, 64, -65, 16383, -49152 correctly (match readSmartSigned)
- `writeSmartSigned` throws on out-of-range values
- Encode minimal model (1 vertex, 0 faces) → decode → compare
- Encode model with 3 vertices, 1 face → decode → verify vertices and face indices
- Encode model with alpha, priorities, textureFaces → decode → compare all fields
- **Round-trip**: decode real model from fixture cache → encode → decode → identical vertices, faces, colors, alpha, priorities, textureFaces

**Verify:** `pnpm test` — all model encoder tests pass.

## Step 3: Model exporter

**Files:**
- `tools/src/exporter/model-exporter.ts`

```ts
export function exportModel(
  writer: CacheWriter,
  model: Model,
  modelFileId: number,
): void
```

Logic:
1. `const encoded = encodeModel(model)`
2. `const compressed = gzipSync(encoded)`
3. `writer.writeFile(1, modelFileId, compressed)`

Cache index 1 confirmed by `model-decoder.test.ts` (`cache.readFile(1, fileId)`).

**Verify:** Integration test: decode model from fixture → export to new cache → read back → gunzip → decode → compare.

## Step 4: Model exporter tests

**Files:**
- `tools/src/exporter/__tests__/model-exporter.test.ts`

Tests:
- Export a decoded model to cache, read back, gunzip, decode → identical model
- Export model with all optional fields → round-trip correct
- Export multiple models to different file IDs → all round-trip correctly

**Verify:** `pnpm test` — all model exporter tests pass.

## Step 5: Wire up exports

**Files:**
- `tools/src/cache/index.ts` — add `export { encodeModel } from "./model-encoder.js"`
- `tools/src/index.ts` — add `export { encodeModel }` and `export { exportModel }`

## Step 6: Update plan docs

**Files:**
- `plans/asset-pipeline/plan.md` — Phase 6 row marked completed
- `plans/asset-pipeline/todo.md` — Phase 6 tasks marked completed, changelog entry
- `plans/asset-pipeline/handovers/phase-6-handover.md` — Handover document

## Step 7: Final verification

```bash
pnpm typecheck    # zero errors
pnpm test         # all tests pass (137 existing + new)
```

## Execution Order

1. Step 1 (model encoder) → Step 2 (encoder tests) — verify round-trip first
2. Step 3 (model exporter) → Step 4 (exporter tests) — depends on step 1
3. Step 5 (exports) — wire up barrel exports
4. Step 6 (plan docs) + Step 7 (verification) — last
