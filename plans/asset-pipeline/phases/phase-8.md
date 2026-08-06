---
type: planning
entity: phase
plan: asset-pipeline
phase: 8
status: active
created: 2026-07-29
updated: 2026-07-29
---

# Phase 8: Dual Model Decoder

## Objective

Add a second model binary format alongside the original 2006Scape format. Both formats produce the same `Model` IR, and a dispatch layer selects the correct decoder/encoder based on a format magic marker. This enables custom models (ID 50000+) to use a direct-encoding format that is simpler to generate and reason about, while preserving full compatibility with the original delta-encoded format.

## Scope

### In Scope

- New model binary format with direct (non-delta) encoding and a 4-byte magic header
- Decoder for the new format: `decodeNewModel(data: Buffer): Model`
- Encoder for the new format: `encodeNewModel(model: Model): Buffer`
- Format detection utility: `detectModelFormat(data: Buffer): ModelFormat`
- Unified dispatch: `decodeModelAuto(data: Buffer): Model`
- Unified dispatch: `encodeModelAuto(model: Model, format: ModelFormat): Buffer`
- Tests: synthetic round-trips, real cache fixture verification, format detection, dispatch
- Updated barrel exports

### Out of Scope

- Client-side Java changes (Model.java dispatch — documented but not implemented)
- Changing the existing 2006 model encoder/decoder
- Vertex/face limits (4096/2000 — constrained by client renderer, not format)
- Face vertex compression types 2-4 (deferred from Phase 6)
- Texture coordinate flags, face labels, vertex skin IDs (not in IR)

## Prerequisites

- [x] Phase 6 complete: model decoder and encoder for 2006 format
- [x] Phase 7 complete: custom ID range constants (CUSTOM_MODEL_START=50000)

## Engine Analysis Findings

### Current model loading chain

1. `Game.java`: model bytes arrive via on-demand fetcher, then `Model.n(data, id)` (was `method460`) stores raw bytes + trailer metadata in `Class21`
2. `Model.method462(id)`: creates `new Model(id)` which decodes from the cached `Class21` data
3. No format version detection exists — all models use the same decode path
4. Models live at cache index 1, gzip-compressed
5. The 18-byte trailer at the buffer end describes section layout (vertex/face counts and data sizes)

### Client renderer limits

| Limit | Value | Source |
|-------|-------|--------|
| Max vertices | 4096 | Client render arrays |
| Max faces | 2000 | Client render arrays |
| Max texture faces | 256 | uint8 texTriCount |
| Max model ID | 65535 | uint16 file ID |

### Dispatch mechanism design

On the client side, `Model.method462` would need to detect format and dispatch:
- **ID-based**: models with ID >= 50000 use the new format (tools writes with magic header)
- **Magic-based**: check first 4 bytes of decompressed data for "SMF\x01" magic
- Both approaches work; magic-based is more robust since it works regardless of ID

The tools package uses magic-based detection exclusively (it processes raw buffers without knowing the source ID).

## Technical Design

### New model format ("SMF" — SingleScape Model Format)

Direct encoding — every value written as-is, no delta or smart encoding. Simpler to generate, simpler to debug, slightly larger than delta encoding.

#### Buffer layout

```
Offset  Type       Field
0       char[4]    Magic: "SMF\x01"
4       uint16     vertexCount
6       uint16     faceCount
8       uint8      texTriCount
9       uint8      hasAlpha (1 = present, 0 = absent)
10      uint8      hasPriority (1 = present, 0 = absent)

--- vertex data (immediately after header) ---
+0      int16[vertexCount * 3]   Vertex coordinates: x0,y0,z0, x1,y1,z1, ...

--- face vertex indices ---
+0      uint16[faceCount * 3]    Face vertex indices: a0,b0,c0, a1,b1,c1, ...

--- face colors ---
+0      uint16[faceCount]        Face colors (16-bit)

--- optional: alpha (if hasAlpha == 1) ---
+0      uint8[faceCount]         Per-face alpha values

--- optional: priorities (if hasPriority == 1) ---
+0      uint8[faceCount]         Per-face priority values

--- optional: texture faces (if texTriCount > 0) ---
+0      uint16[texTriCount * 3]  Texture triangle vertex indices
```

Total size: 11 + vertexCount*6 + faceCount*6 + faceCount*2 + (faceCount if alpha) + (faceCount if priority) + (texTriCount*6 if textures)

### Format detection

Check first 4 bytes: if `buf[0..3]` == `SMF\x01` → new format, else → 2006 format.

The 2006 format never starts with `S` (0x53) in practice because the first byte is a vertex flag byte (0-7), so there's no collision risk.

### Dispatch API

```ts
type ModelFormat = "legacy" | "smf";

function detectModelFormat(data: Buffer): ModelFormat;
function decodeModelAuto(data: Buffer): Model;
function encodeModelAuto(model: Model, format: ModelFormat): Buffer;
```

## Deliverables

| File | Purpose |
|------|---------|
| `tools/src/cache/new-model-decoder.ts` | `decodeNewModel(data: Buffer): Model` |
| `tools/src/cache/new-model-encoder.ts` | `encodeNewModel(model: Model): Buffer` |
| `tools/src/cache/model-dispatch.ts` | `detectModelFormat`, `decodeModelAuto`, `encodeModelAuto` |
| `tools/src/cache/__tests__/new-model-decoder.test.ts` | Synthetic + round-trip tests |
| `tools/src/cache/__tests__/new-model-encoder.test.ts` | Synthetic + cross-format round-trip tests |
| `tools/src/cache/__tests__/model-dispatch.test.ts` | Format detection + auto dispatch tests |
| `plans/asset-pipeline/phases/phase-8.md` | This file |
| `plans/asset-pipeline/implementation/phase-8-impl.md` | Implementation plan |

## Acceptance Criteria

- [ ] `decodeNewModel` produces correct Model IR from SMF buffers
- [ ] `encodeNewModel` produces valid SMF buffers from Model IR
- [ ] Round-trip: encode → decode produces identical Model IR
- [ ] Cross-format: decode 2006 model → encode as SMF → decode → compare (identical IR)
- [ ] `detectModelFormat` correctly identifies both formats
- [ ] `decodeModelAuto` dispatches correctly for both formats
- [ ] `encodeModelAuto` encodes to the requested format
- [ ] `decodeNewModel` rejects non-SMF buffers
- [ ] `decodeModel` still rejects SMF buffers (no regression)
- [ ] All tests pass with `pnpm test`
- [ ] Zero TypeScript errors with `pnpm typecheck`

## Risks

| Risk | Mitigation |
|------|------------|
| SMF magic byte 0x53 could theoretically collide with a valid 2006 vertex flag | Vertex flag bytes are 0-7 (3 bits); 0x53 is well outside range |
| SMF format is slightly larger than delta-encoded 2006 format | Acceptable for custom models; can optimize later with a compressed variant |
| Client-side dispatch requires Java changes | Document dispatch design; implement only on tools side for now |
