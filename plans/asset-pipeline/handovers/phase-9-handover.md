# Handoff: Asset Pipeline — Phase 9 Complete

**Date:** 2026-07-29
**Working directory:** `/Users/jonas/Developer/RS/tools`
**Session type:** Claude Code session

## What was done

Phase 9 (Visual asset browser) of the asset-pipeline plan is now **complete**. The `@singlescape/tools` package has a new `browser/` module providing programmatic search, preview, reporting, and mapping controls over the cache's model and object definition database. All 331 tests pass. Zero TypeScript errors.

### New files created

| File | Purpose |
|------|---------|
| `tools/src/browser/asset-browser.ts` | `AssetBrowser` facade class — loads a cache directory, provides search/preview/report/mapping methods. Accepts `string` (cache dir path) or `CacheReader` instance. Lazily loads object definitions on first `objects()` call. |
| `tools/src/browser/object-search.ts` | `ObjectSearch` fluent filter — `byName`, `byIdRange`, `interactive`, `solid`, `hasAnimation`, `hasModels`, `bySize`, `limit`. Returns `ObjectDefinition[]`. |
| `tools/src/browser/model-search.ts` | `ModelSearch` fluent filter — `byIdRange`, `byVertexCount`, `byFaceCount`, `byFormat`, `hasAlpha`, `hasTextures`, `limit`. Lazily decodes models from cache with gunzip + `decodeModelAuto`. Returns `ModelSearchResult[]`. |
| `tools/src/browser/wireframe-preview.ts` | `renderWireframe(model, options?)` — generates SVG wireframe from Model IR. Orthographic projection (rotateX -20°, rotateY 30°), painter's algorithm depth sort, configurable width/height/stroke/fill/axes. |
| `tools/src/browser/model-report.ts` | `generateModelReport(id, model, format)` — returns `ModelReport` with vertex/face/texture counts, bounding box, face color range, alpha/priority presence flags. |
| `tools/src/browser/compatibility-report.ts` | `generateCompatibilityReport(objects, mapper)` — counts EXACT/SUBSTITUTE/REMOVE/UNMAPPED with deduplicated ID lists. |
| `tools/src/browser/region-analyzer.ts` | `analyzeRegion(region, mapper?)` — counts mapped/unmapped/removed objects, unique model IDs, custom models needed (>= 50000), unmapped detail breakdown with definition names. |
| `tools/src/browser/mapping-editor.ts` | `MappingEditor` class — CRUD on `MappingDatabase`: `set`, `remove`, `get`, `list`, `importEntries`, `validate`, `build`. Deep-clones input database. |
| `tools/src/browser/index.ts` | Barrel exports for all browser module types and classes. |
| `tools/src/browser/__tests__/object-search.test.ts` | 16 tests: name substring/regex, ID range, interactive, solid, animation, models, size, combined filters, limit, count. |
| `tools/src/browser/__tests__/model-search.test.ts` | 12 tests: ID range, vertex/face count, format, alpha, textures, limit against real cache fixtures. |
| `tools/src/browser/__tests__/wireframe-preview.test.ts` | 9 tests: single triangle, multi-face, empty model, custom options, axes, SVG validity, coordinate ranges. |
| `tools/src/browser/__tests__/model-report.test.ts` | 10 tests: synthetic models with known bounding box, alpha, priorities, textures, empty model, color range. |
| `tools/src/browser/__tests__/compatibility-report.test.ts` | 7 tests: all-mapped, mixed, empty, unmapped/substituted/removed ID lists, deduplication. |
| `tools/src/browser/__tests__/region-analyzer.test.ts` | 8 tests: mapped objects, unmapped objects, modelRefs counting, no mapper, definition names, group counts. |
| `tools/src/browser/__tests__/mapping-editor.test.ts` | 17 tests: set/get/remove cycle, overwrite, list sorted, import batch, validate catches errors, build, no mutation. |
| `tools/src/browser/__tests__/asset-browser.test.ts` | 12 tests: facade integration — objects(), models(), previewModel(), modelReport(), compatibilityReport(), analyzeRegion(), editMappings() against real cache fixtures. |
| `plans/asset-pipeline/phases/phase-9.md` | Phase scope document. |
| `plans/asset-pipeline/implementation/phase-9-impl.md` | Implementation plan (9 steps). |

### Modified files

| File | Change |
|------|--------|
| `tools/src/index.ts` | Added re-exports: `AssetBrowser`, `ObjectSearch`, `ModelSearch`, `ModelSearchResult`, `renderWireframe`, `PreviewOptions`, `generateModelReport`, `ModelReport`, `generateCompatibilityReport`, `CompatibilityReport`, `analyzeRegion`, `RegionAnalysis`, `UnmappedDetail`, `MappingEditor`. |
| `plans/asset-pipeline/plan.md` | Phase 9 marked completed, changelog entry. |
| `plans/asset-pipeline/todo.md` | Phase 9 tasks completed, active phase updated to Phase 10. |

### Key implementation notes

- **AssetBrowser facade**: Accepts either a cache directory path (string) or a pre-constructed `CacheReader` instance. The string constructor creates a `CacheReader` directly (no dynamic require — clean ESM import).
- **Lazy definition loading**: `objects()` is async because it reads LOC.DAT/LOC.IDX from the cache archive on first call, then caches the decoded definitions.
- **ModelSearch**: Uses a generator pattern internally (`*iterate()`) for efficiency — models are decoded and filtered on demand, not all at once.
- **Wireframe projection**: Uses a fixed isometric-like camera (rotateX -20°, rotateY 30°). Faces are sorted by centroid Z depth (painter's algorithm). The SVG uses viewBox for resolution-independent rendering.
- **MappingEditor**: Deep-clones the input MappingDatabase in the constructor so edits don't mutate the original. `validate()` returns string issues (not throws) for non-destructive checking.
- **All tests use synthetic data** except for AssetBrowser facade tests and ModelSearch tests, which use real cache fixtures at `tools/fixtures/cache/`.

### Architecture

```
src/browser/
├── index.ts                  # Barrel exports
├── asset-browser.ts          # AssetBrowser facade (cache → search/preview/report/mapping)
├── object-search.ts          # ObjectSearch fluent filter
├── model-search.ts           # ModelSearch fluent filter (lazy cache decode)
├── wireframe-preview.ts      # SVG wireframe generation
├── model-report.ts           # Model metadata report
├── compatibility-report.ts   # Mapping classification summary
├── region-analyzer.ts        # Bulk region analysis
├── mapping-editor.ts         # MappingDatabase CRUD helpers
└── __tests__/                # 8 test files, 91 tests total
```

### Verification

```
pnpm typecheck  — zero errors
pnpm test       — 331/331 pass (32 test files, 91 new for Phase 9)
```

## Current state

| Phase | Status | Tests |
|-------|--------|-------|
| 1 — Inspect formats | completed | — |
| 2 — Neutral IR | completed | 43 |
| 3 — Source importer | completed | 82 |
| 4 — Terrain exporter | completed | 100 |
| 5 — Object mapping & landscape export | completed | 137 |
| 6 — Static model backport | completed | 159 |
| 7 — Custom asset namespace | completed | 206 |
| 8 — Dual model decoder | completed | 240 |
| 9 — Visual asset browser | completed | 331 |
| 10 — Region converter UI | **next** | — |

## What's next

**Phase 10: Region converter UI** — Per the ROADMAP.md: "Side-by-side source and converted preview." This would build on the Phase 9 browser to add:
1. Region import → convert → preview pipeline
2. Side-by-side SVG previews (source terrain/objects vs converted)
3. Conversion mode selection (strict, approximate, backport, 2006-style)
4. Export controls (trigger terrain/landscape/model/definition export)
5. Conversion log showing what was mapped, substituted, or skipped

### Known open issues (deferred)

- **Face vertex compression**: Type 1 encoding works but produces larger files than the original. Types 2/3/4 (strip/fan) could be added as an optimization.
- **Terrain height semantics**: Opcode 1 on planes >0 should incorporate prior-plane height. IR height sign convention should be documented.
- **Morphing objects**: Opcode 77 definitions have child IDs requiring varbit/varp state. Phase 3 exposes morph metadata but doesn't resolve active children.
- **Smart encoding range**: Landscape format limits object ID deltas to 0-32767. Custom objects at 35000+ are within range, but 60000+ would exceed the limit without client-side changes.
- **Custom objects at 60000**: Starting custom object IDs at 60000 (as originally planned) requires extending the landscape encoding or client-side dispatching. Currently capped at 35000+ to avoid delta overflow.
- **NPC/item definition encoders**: Follow the same pattern as `encodeObjectDefinition`. Deferred to a later phase.
- **Mapping database**: No initial mapping data exists yet. The first use case would be importing a region, exporting it, and iteratively building the mapping database based on what the 2006 client can render.
- **Wireframe quality**: The fixed isometric projection works for most models but may not be ideal for all viewing angles. A future enhancement could accept custom rotation parameters.

## Key file paths

| Path | Purpose |
|------|---------|
| `tools/src/browser/asset-browser.ts` | AssetBrowser facade |
| `tools/src/browser/object-search.ts` | ObjectSearch fluent filter |
| `tools/src/browser/model-search.ts` | ModelSearch fluent filter |
| `tools/src/browser/wireframe-preview.ts` | SVG wireframe renderer |
| `tools/src/browser/model-report.ts` | Model metadata report |
| `tools/src/browser/compatibility-report.ts` | Compatibility report |
| `tools/src/browser/region-analyzer.ts` | Region analyzer |
| `tools/src/browser/mapping-editor.ts` | Mapping editor CRUD |
| `tools/src/browser/index.ts` | Browser barrel exports |
| `tools/src/index.ts` | Main barrel exports |
| `plans/asset-pipeline/plan.md` | Top-level plan |
| `plans/asset-pipeline/todo.md` | Task tracking |
| `plans/asset-pipeline/phases/phase-9.md` | Phase 9 scope |
| `plans/asset-pipeline/implementation/phase-9-impl.md` | Phase 9 implementation plan |

## Suggested skills

- **`/verify`** — Verify the browser module works end-to-end with a real cache
- **`/code-review`** — Review Phase 9 changes before starting Phase 10
- **`/implementer-strong`** — Implement Phase 10 region converter UI
