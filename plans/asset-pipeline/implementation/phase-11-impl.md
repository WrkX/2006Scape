---
type: planning
entity: implementation
plan: asset-pipeline
phase: 11
status: completed
created: 2026-07-29
updated: 2026-07-29
---

# Phase 11 Implementation Plan

References: [Phase 11 scope](../phases/phase-11.md), [Phase 10 implementation](phase-10-impl.md), [asset-pipeline plan](../plan.md).

1. Add `tools/src/composer/types.ts` and `region-composer.ts` with immutable crop, translate, combine, and stitch APIs. Keep fragment terrain arbitrary-sized, preserve four planes and source/object provenance, and validate local/world coordinates with `RangeError`.
2. Assemble output cells into aligned 64x64 regions. Apply explicit fill tiles, terrain overlap policies, object overlap policies, clipping diagnostics, deterministic region ordering, and blocked-result semantics. Add focused synthetic Vitest coverage for crop, translation, composition, policies, clipping, dimensions, provenance, and invalid inputs.
3. Add archive encoding and strict decoded-archive validation to `tools/src/cache/archive-decoder.ts` using the existing six-byte outer header and decoded entry representation. Validate outer sizes, table/payload bounds, compression consistency, decompressed lengths, trailing bytes, and 24-bit limits while preserving entry order, hashes, and data.
4. Extend `tools/src/cache/map-index.ts` with strict encode/decode/validation, fully validated relocation batches with explicit collision policy and snapshot resolution, duplicate MAP_INDEX rejection, and an async archive rewrite that stages only archive 0/file 5 through `CacheWriter` without calling `save()`. Repeated rewrites on one writer use the writer-owned staged file view and are serialized.
5. Allocate new cache writes after a partial final sector and add map-index/archive and sector-allocation regression tests, including read-back after an explicit save.
6. Wire all public functions and types through `src/composer/index.ts`, `src/cache/index.ts`, and `src/index.ts`.
7. Update Phase 11 plans and concise converter/pipeline documentation. Do not package definitions or models; retain the explicit save boundary.
8. Run `pnpm typecheck` and `pnpm test`; resolve all failures before marking the phase complete.
