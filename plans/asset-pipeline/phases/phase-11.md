---
type: planning
entity: phase
plan: asset-pipeline
phase: 11
status: completed
created: 2026-07-29
updated: 2026-07-29
---

# Phase 11: Region Composer

## Objective

Provide pure, provenance-preserving region composition primitives and map-index-aware relocation for arbitrary destination coordinates. The composer produces client-shaped 4-plane, 64x64 regions while keeping cache persistence explicit.

## In scope

- Crop half-open local tile rectangles from 64x64 regions.
- Translate fragment destination origins without resampling or mutation.
- Combine fragments at explicit world tile coordinates and stitch them deterministically along an axis.
- Detect and configure terrain and object overlaps, fill uncovered terrain, and report or block clipping.
- Preserve source, object, and placement provenance through composition.
- Encode, validate, upsert, and relocate map-index records.
- Rewrite archive 0/file 5 while replacing only the MAP_INDEX entry and staging through `CacheWriter` without saving.
- Encode Java-compatible outer-compressed archive containers with extracted entry payloads.

## Out of scope

- Object-definition, model, animation, texture, or other asset packaging.
- Implicit cache saving or cache sector compaction.
- Visual editor and browser UI.

## Acceptance criteria

- [x] Crop uses strict half-open bounds, rebases included objects, and never mutates input.
- [x] Translation changes only the fragment destination origin.
- [x] Combine and stitch support arbitrary valid world tile coordinates and deterministic output ordering.
- [x] Final regions always contain four 64x64 planes, aligned bases, and valid packed region IDs.
- [x] Terrain overlap defaults to blocking; first/last policies are configurable. Objects default to keep-all with dedupe/error alternatives.
- [x] Discarded clipping is partial with diagnostics; clipping error blocks output and returns no regions.
- [x] Map-index and archive round-trips preserve entry order and all non-target archive data.
- [x] Archive and cache block readers reject inconsistent sizes, trailing bytes, malformed payloads, and truncated sectors.
- [x] Relocation batches validate descriptors, resolve against an original snapshot, reject collisions by default, and compose repeated staged writes on one writer.
- [x] `pnpm typecheck` and `pnpm test` pass.
