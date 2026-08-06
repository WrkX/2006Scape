---
type: planning
entity: phase
plan: asset-pipeline
phase: 2
status: completed
created: 2026-07-28
updated: 2026-07-28
---

# Phase 2: Neutral Map/Model IR

## Objective

Build a TypeScript package under `tools/` that defines the SingleScape intermediate representation for maps, models, objects, and definitions. Implement decoders for the 2006Scape cache formats that populate this IR. All code must be independently testable with fixture data.

## Status: COMPLETED

All deliverables built and verified. 43 tests pass, zero TypeScript errors.

## Deliverables

- `tools/` as a pnpm workspace package (`@singlescape/tools`).
- IR type definitions matching the design in `tools/docs/ASSET_PIPELINE.md`.
- Cache file reader for `main_file_cache.dat` + idx files.
- Model decoder reading the 2006Scape binary model format.
- Map decoder reading terrain and landscape gzip archives.
- Definition decoders for loc, npc, and obj dat/idx files.
- Hard-coded engine limit constants.
- JSON serialization with round-trip tests.
- Vitest test suite covering all decoders and IR types.

## Implementation Plan

See [Phase 2 Implementation Plan](../implementation/phase-2-impl.md).

## Acceptance Criteria

- [x] `tsc --noEmit` in tools/ succeeds with zero TypeScript errors.
- [x] `vitest run` in tools/ passes all tests (43/43).
- [x] Cache reader extracts entries by type and ID from real cache fixtures.
- [x] Model decoder produces vertex/face/texture data matching engine expectations.
- [x] Map decoder produces Region IR with 4 planes of 64x64 tiles.
- [x] Definition decoders produce structured object/NPC/item data.
- [x] All engine limits captured as named exports (15 constants).
- [x] IR round-trip (encode to JSON, decode, compare) is lossless.
