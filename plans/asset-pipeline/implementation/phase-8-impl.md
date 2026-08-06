---
type: planning
entity: implementation-plan
plan: asset-pipeline
phase: 8
status: active
created: 2026-07-29
updated: 2026-07-29
---

# Phase 8 Implementation Plan: Dual Model Decoder

## Overview

Implement a second model binary format (SMF) with direct encoding alongside the existing 2006 delta format. Both formats produce/consume the same `Model` IR. A dispatch layer detects format from buffer content and routes to the correct decoder/encoder.

## Step 1: New model decoder (`decodeNewModel`)

**Files:** `tools/src/cache/new-model-decoder.ts`

Decode SMF format buffers into Model IR.

```ts
export const SMF_MAGIC = Buffer.from([0x53, 0x4D, 0x46, 0x01]); // "SMF\x01"
export const SMF_HEADER_SIZE = 11;

export function decodeNewModel(data: Buffer): Model
```

Implementation:
1. Validate minimum size (11 bytes) and magic bytes `data[0..3]`
2. Read header: vertexCount (uint16BE @ 4), faceCount (uint16BE @ 6), texTriCount (uint8 @ 8), hasAlpha (uint8 @ 9), hasPriority (uint8 @ 10)
3. Read vertices: `vertexCount * 6` bytes of int16BE, stride 6, produce `vertices[]`
4. Read face indices: `faceCount * 6` bytes of uint16BE, stride 6, produce `faces[]`
5. Read colors: `faceCount * 2` bytes of uint16BE, produce `colors[]`
6. If hasAlpha: read `faceCount` bytes, produce `alpha[]`
7. If hasPriority: read `faceCount` bytes, produce `priorities[]`
8. If texTriCount > 0: read `texTriCount * 6` bytes of uint16BE, produce `textureFaces[]`
9. Return Model IR

## Step 2: New model decoder tests

**Files:** `tools/src/cache/__tests__/new-model-decoder.test.ts`

Tests:
1. Rejects buffer too short for header
2. Rejects buffer with wrong magic
3. Decodes minimal model (1 vertex, 0 faces)
4. Decodes triangle (3 vertices, 1 face) with color
5. Decodes model with alpha
6. Decodes model with priorities
7. Decodes model with texture faces
8. Decodes model with all optional fields
9. Validates vertex indices are within bounds
10. Throws on out-of-range vertex index in faces

## Step 3: New model encoder (`encodeNewModel`)

**Files:** `tools/src/cache/new-model-encoder.ts`

Encode Model IR into SMF format buffer.

```ts
export function encodeNewModel(model: Model): Buffer
```

Implementation:
1. Compute header fields from model data
2. Allocate buffer: 11 + v*6 + f*6 + f*2 + (f if alpha) + (f if priority) + (tex*6)
3. Write magic "SMF\x01"
4. Write header fields
5. Write vertex coordinates as int16BE
6. Write face vertex indices as uint16BE
7. Write face colors as uint16BE
8. Write alpha if any non-zero
9. Write priorities if any non-zero
10. Write texture faces if present

## Step 4: New model encoder tests

**Files:** `tools/src/cache/__tests__/new-model-encoder.test.ts`

Tests:
1. Encodes minimal model → decode produces identical IR
2. Encodes triangle with color → round-trip
3. Preserves alpha when non-zero
4. Preserves priorities when non-zero
5. Preserves texture faces
6. Preserves all optional fields together
7. Cross-format round-trip: decode 2006 model → encode SMF → decode SMF → compare

## Step 5: Format detection and dispatch

**Files:** `tools/src/cache/model-dispatch.ts`

```ts
export type ModelFormat = "legacy" | "smf";

export function detectModelFormat(data: Buffer): ModelFormat;
export function decodeModelAuto(data: Buffer): Model;
export function encodeModelAuto(model: Model, format: ModelFormat): Buffer;
```

- `detectModelFormat`: check first 4 bytes against SMF_MAGIC
- `decodeModelAuto`: detect format, dispatch to `decodeModel` or `decodeNewModel`
- `encodeModelAuto`: dispatch to `encodeModel` or `encodeNewModel` based on format param

## Step 6: Dispatch tests

**Files:** `tools/src/cache/__tests__/model-dispatch.test.ts`

Tests:
1. `detectModelFormat` identifies SMF buffers
2. `detectModelFormat` identifies legacy buffers
3. `decodeModelAuto` decodes SMF buffers via new decoder
4. `decodeModelAuto` decodes legacy buffers via old decoder
5. `encodeModelAuto("legacy")` → `detectModelFormat` returns "legacy"
6. `encodeModelAuto("smf")` → `detectModelFormat` returns "smf"
7. Cross-format round-trip: decode real 2006 model → encode as SMF → `decodeModelAuto` → compare
8. `decodeModel` rejects SMF buffers (no regression)

## Step 7: Wire up exports and update docs

**Files:** `tools/src/cache/index.ts`, `tools/src/index.ts`, `plans/asset-pipeline/plan.md`, `plans/asset-pipeline/todo.md`

- Export `decodeNewModel`, `SMF_MAGIC` from `cache/index.ts`
- Export `encodeNewModel` from `cache/index.ts`
- Export `detectModelFormat`, `decodeModelAuto`, `encodeModelAuto`, `ModelFormat` from `cache/index.ts`
- Re-export from `tools/src/index.ts`
- Update plan.md: Phase 8 completed
- Update todo.md: Phase 8 tasks

## Execution Order

```
Step 1 (decoder)       → Step 2 (decoder tests)
Step 3 (encoder)       → Step 4 (encoder tests)
Step 5 (dispatch)      → Step 6 (dispatch tests)
Step 7 (exports + docs)
```

Steps 1+3 can be done in sequence (encoder depends on decoder format knowledge).
Steps 2+4+6 can be verified together at the end.
Step 7 last.

## Final Verification

```bash
pnpm typecheck  # zero errors
pnpm test       # all passing (206 existing + ~25 new)
```
