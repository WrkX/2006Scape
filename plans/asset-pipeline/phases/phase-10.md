---
type: planning
entity: phase
plan: asset-pipeline
phase: 10
status: completed
created: 2026-07-29
updated: 2026-07-29
---

# Phase 10: Region Converter UI

## Objective

Provide the first usable region-conversion application surface as a headless TypeScript API: import one region, apply an explicit conversion mode, expose diagnostics and logs, render source/converted SVG previews, and export selected terrain/landscape files.

## In scope

- `RegionConverter` orchestration over the existing importer, mapper, IR, and encoders.
- Strict, approximate, backport-deferred, and 2006-style decision behavior.
- Provenance-preserving converted object records.
- Conversion reports and per-placement logs.
- Terrain and region SVG previews, including side-by-side comparison.
- Validated terrain/landscape export controls without implicit `CacheWriter.save()`.

## Out of scope

- React, Electron, Tauri, WebGL, or a browser runtime.
- Archive-aware object-definition packaging and map-index updates.
- Region relocation/composition (Phase 11).
- Full model/definition backporting, animation, textures, or lighting conversion.

## Acceptance criteria

- Unmapped objects cannot silently pass through strict conversion.
- Approximate substitutions and omissions are visible in logs and reports.
- 2006-style material work is explicit and incomplete when no material map exists.
- Blocked conversions write no export files.
- SVG previews are standalone and contain both source and converted panels.
- Existing tools tests remain passing and the new converter tests cover real encoding round-trips.
