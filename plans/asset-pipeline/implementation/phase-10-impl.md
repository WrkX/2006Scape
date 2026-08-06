---
type: planning
entity: implementation
plan: asset-pipeline
phase: 10
status: completed
created: 2026-07-29
updated: 2026-07-29
---

# Phase 10 Implementation Plan

1. Add converter types and deterministic object decision policy.
2. Implement `RegionConverter.import`, `analyze`, `convert`, and validated terrain/landscape export.
3. Add terrain, region, and side-by-side SVG preview renderers.
4. Add synthetic converter tests and real cache-encoding round-trip coverage.
5. Update public exports and Phase 10 documentation.
6. Run `pnpm typecheck` and `pnpm test`; resolve all failures before marking the phase complete.

The implementation deliberately leaves archive-aware definitions, map-index updates, relocation, and complete model backporting for later work instead of claiming unsupported output is runnable by the legacy client.
