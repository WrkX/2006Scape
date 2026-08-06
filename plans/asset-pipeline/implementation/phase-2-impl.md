---
type: planning
entity: implementation-plan
plan: asset-pipeline
phase: 2
status: in_progress
created: 2026-07-28
updated: 2026-07-28
---

# Phase 2 Implementation Plan: Neutral Map/Model IR

## Overview

Build `@singlescape/tools` as a pnpm workspace package containing:
1. IR type definitions for Region, Plane, Tile, WorldObject, Model, etc.
2. Binary cache reader for `main_file_cache.dat` + idx files
3. Model decoder matching Model.java's 18-byte header trailer format
4. Map decoder for gzip terrain + landscape archives
5. Definition decoders for loc/npc/obj opcode-driven dat/idx
6. Engine limit constants
7. JSON serialization with round-trip tests

## Workspace Integration

- Add `tools` to root `pnpm-workspace.yaml`
- `tools/package.json` as `@singlescape/tools`
- TypeScript strict mode, Vitest, Node.js built-in `zlib` for gzip

## Step 1: Scaffold tools/ package

**Files:**
- `tools/package.json` — name `@singlescape/tools`, scripts: build, test, typecheck
- `tools/tsconfig.json` — strict mode, ESNext target, NodeNext module resolution
- `tools/vitest.config.ts` — minimal config
- Update `pnpm-workspace.yaml` — add `tools`

**Verify:** `pnpm install && pnpm --filter @singlescape/tools typecheck`

## Step 2: IR types

**Files:**
- `tools/src/ir/types.ts` — all IR interfaces

Types from ASSET_PIPELINE.md:
- `Region` (id, baseX, baseY, planes, objects)
- `Plane` (tiles)
- `Tile` (height, underlay, overlay, shape, rotation)
- `WorldObject` (sourceId, targetId, x, y, plane, type, rotation, modelRefs)
- `Model` (vertices, faces, colors, textureFaces, alpha, priorities)
- `Vertex` (x, y, z)
- `Face` (a, b, c — vertex indices)
- `TextureFace` (a, b, c — vertex indices for texture mapping)

Also add:
- `CompatibilityCategory` enum (EXACT, CONVERTIBLE, SUBSTITUTE, MANUAL, UNSUPPORTED)
- `ConversionMode` enum (strict, approximate, backport, 2006-style)

**Verify:** `pnpm --filter @singlescape/tools typecheck`

## Step 3: IR JSON serialization

**Files:**
- `tools/src/ir/serialize.ts` — `toJson(ir)` and `fromJson(json)` functions
- `tools/src/ir/index.ts` — re-export types + serialize

**Verify:** Round-trip test: create Region in-memory, toJson, fromJson, deep-equal comparison.

## Step 4: Engine limit constants

**Files:**
- `tools/src/limits.ts` — named constants from engine source

Constants (from Scout findings):
- `MAX_TEXTURES = 50`
- `MAX_MODEL_VERTICES = 4096`
- `MAX_MODEL_FACES = 2000`
- `TILE_GRID_SIZE = 104`
- `REGION_SIZE = 64`
- `MAX_PLANES = 4`
- `MAX_OBJECTS_PER_TILE = 5`
- `MAX_MODEL_ID = 65535` (16-bit)
- `MAX_ITEM_ID = 15000`
- `MAX_NPC_ID = 4096` (12-bit addressable)
- `MAX_PLAYERS = 2048`
- `CACHE_BLOCK_SIZE = 520`
- `CACHE_INDEX_ENTRY_SIZE = 6`
- `STREAMLOADER_ENTRY_SIZE = 10`
- `WORLD_UNIT = 128` (tile to world coordinate)

**Verify:** Typecheck. Manual spot-check against engine source.

## Step 5: Cache file reader

**Files:**
- `tools/src/cache/cache-reader.ts` — reads `main_file_cache.dat` + idx files

The cache format:
- idx file: array of 6-byte entries (3-byte size + 3-byte firstSector)
- dat file: linked-list of 520-byte blocks (8-byte header + 512-byte payload)
- Header: nextFileID (uint16), chunkNumber (uint16), nextSector (uint24), archiveIndex (uint8)
- To read entry N from idx file: seek to N*6, read size and firstSector, then follow the block chain in the dat file

**Verify:** Read a known entry from engine/server/data/cache/, verify byte count matches idx entry.

## Step 6: Model decoder

**Files:**
- `tools/src/cache/model-decoder.ts`

Model format (from Model.java lines 35-106):
- Read the last 18 bytes of the raw data as the header
- Header fields at these offsets (unsigned 16-bit LE where noted):
  - +0: vertexCount (uint16)
  - +2: faceCount (uint16)
  - +4: texTriCount (uint8)
  - +5: hasTexCoords (uint8, 0 or 1)
  - +6: alphaFlag (uint8)
  - +7: hasVisibility (uint8)
  - +8: hasFaceLabels (uint8)
  - +9: hasVertexLabels (uint8)
  - +10: vertexDataSize (uint16)
  - +12: faceDataSize1 (uint16)
  - +14: faceDataSize2 (uint16)
  - +16: extraDataSize (uint16)
- After header: read vertex X deltas, vertex Y deltas, vertex Z deltas (variable-length signed ints)
- Then face index data (triangle strip/fan decoding)
- Then face color/type data
- Then texture triangle data (if texTriCount > 0)

**Verify:** Decode a model from the cache, verify vertex/face counts match the header.

## Step 7: Map decoder

**Files:**
- `tools/src/cache/map-decoder.ts`

Map file format:
- Terrain: `m{x}_{y}.gz` in archive 4 — gzip compressed, contains 4 planes of 64x64 tiles
  - Each tile: 1 byte flags, optional height offset, 1 byte underlay, 1 byte overlay
- Landscape: `l{x}_{y}.gz` in archive 4 — gzip compressed, contains object placements
  - Each object: objectId (varuint16), location data (packed byte: x, y, type, rotation)

Server-side reference: `MapFile`, `MapPlane`, `Tile`, `MapObjectsDecoder` in `engine/server/src/main/java/org/apollo/cache/map/`

**Verify:** Decode region 12850, verify 4 planes x 64x64 tiles, verify object count is reasonable.

## Step 8: Definition decoders

**Files:**
- `tools/src/cache/definition-decoder.ts`

Format: opcode-driven binary stream. Read uint8 opcode, if 0 then end. Different definitions per opcode.

ObjectDef opcodes (from ObjectDef.java):
- 1: model types + model IDs
- 2: name
- 5: model IDs (single type)
- 14: width
- 15: height
- 17: blocks projectiles
- 18: blocks land
- 19: actions[0]
- 21: wall width
- 22: ambient lighting
- 23: actions[1]
- 24: animation ID
- 27: actions[2]
- 28: decor displacement
- 29: contrast
- 39: contrast (scaled)
- 62: recolor src
- 64: recolor dst
- 65: scale X
- 66: scale Y
- 67: scale Z
- 68: map scene ID
- 69: minimap marker
- 70: offset X
- 71: offset Y
- 72: offset Z
- 73: obstructs ground
- 75: support item
- 77: varbit
- 78: varp
- 79: ambient sound
- 81: texture brightness
- 82: map function ID
- 88: inverted
- 89: casts shadow
- 90: model size X
- 91: model size Y
- 92: model size Z
- 93: obstructs sky
- 94: actions[3]
- 95: actions[4]
- 97: map scene active
- 98: retexture src
- 99: retexture dst
- 249: metadata (JS5-style extended params)

Similar for EntityDef (NPC) and ItemDef.

**Verify:** Decode a known object/NPC/item from cache, verify name and model IDs.

## Step 9: Tests

**Files:**
- `tools/src/ir/__tests__/serialize.test.ts` — round-trip tests
- `tools/src/cache/__tests__/cache-reader.test.ts` — read cache entries
- `tools/src/cache/__tests__/model-decoder.test.ts` — decode model, check counts
- `tools/src/cache/__tests__/map-decoder.test.ts` — decode region, check dimensions
- `tools/src/cache/__tests__/definition-decoder.test.ts` — decode definitions
- `tools/src/__tests__/limits.test.ts` — verify constants

Fixture data: copy minimal cache files from `engine/server/data/cache/` to `tools/fixtures/`.

**Verify:** `pnpm --filter @singlescape/tools test` — all pass.

## Step 10: Wire up exports

**Files:**
- `tools/src/index.ts` — re-export everything
- `tools/package.json` — add exports field

**Verify:** `pnpm --filter @singlescape/tools build && pnpm --filter @singlescape/tools test`

## Execution Order

Steps 1-4 first (scaffold, types, serialization, limits) — these have no dependencies.
Then steps 5-8 (cache reader, model decoder, map decoder, definition decoders) — these depend on types and limits.
Step 9 (tests) can be written alongside each decoder.
Step 10 (exports) last.
