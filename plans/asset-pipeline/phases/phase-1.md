---
type: planning
entity: phase
plan: asset-pipeline
phase: 1
status: completed
created: 2026-07-28
updated: 2026-07-28
---

# Phase 1: Inspect 2006Scape Formats

## Objective

Document the 2006Scape client/server cache formats, model encoding, map region structure, object/NPC/item definition formats, and all hard-coded engine limits from the engine source code.

## Status: COMPLETED

All format inspection was performed against `engine/client/` and `engine/server/`.

## Key Findings

### Cache Format
- Flat-file cache: `main_file_cache.dat` + `main_file_cache.idx0..4`
- Block size: 520 bytes (8-byte header + 512-byte payload)
- Index entry: 6 bytes (3-byte file size + 3-byte first sector)
- StreamLoader archives: 10-byte table entries (4-byte hash + 3-byte uncompressed + 3-byte compressed)
- 4 file types: 0=model, 1=animation, 2=midi, 3=map

### Model Format (Model.java, 1898 lines)
- 18-byte header trailer at end of data
- Fields: vertexCount, faceCount, texTriCount, hasTexCoords, alphaFlag, hasVisibility, hasFaceLabels, hasVertexLabels, vertexDataSize, faceDataSize1, faceDataSize2, extraDataSize
- Vertex data: delta-encoded X, Y, Z via variable-length signed int
- Face data: triangle strip/fan with 4 modes
- Render cap: 4096 vertices, 2000 faces per model

### Map Format
- Region size: 64x64 tiles, 4 planes
- Tile grid: 104x104 per plane
- Terrain: `m[regionX]_[regionY].gz` — 4 planes of height/underlay/overlay data
- Landscape: `l[regionX]_[regionY].gz` — object placements
- Max 5 interactable objects per tile

### Definition Formats
- All use opcode-driven stream: read opcode byte, 0 = end
- ObjectDef: `loc.dat`/`loc.idx`
- EntityDef: `npc.dat`/`npc.idx`
- ItemDef: `obj.dat`/`obj.idx`
- Flo: `flo.dat` — RGB color, texture ID

### Hard-Coded Limits
- Textures: 50
- Model render vertices: 4096
- Tile grid: 104x104 per plane
- Planes: 4
- Objects per tile: 5
- Players: 2048
- NPCs: 16384 array / 4096 addressable (12 bits)
- Item limit: 15000
- Model IDs: 16-bit (0-65535)
- Cache archives: 9

## Acceptance Criteria

- [x] Cache format documented.
- [x] Model format documented.
- [x] Map format documented.
- [x] Definition formats documented.
- [x] All hard-coded limits captured.
- [x] Data flow summarized.
