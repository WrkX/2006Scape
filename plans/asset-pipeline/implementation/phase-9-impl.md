---
type: planning
entity: implementation
plan: asset-pipeline
phase: 9
status: active
created: 2026-07-29
updated: 2026-07-29
---

# Phase 9 Implementation Plan

## Reference

- Scope: [`phases/phase-9.md`](../phases/phase-9.md)
- Prior phase: [`phases/phase-8.md`](../phases/phase-8.md)
- Plan: [`plan.md`](../plan.md)

## Steps

### Step 1: ObjectSearch fluent filter

**File**: `tools/src/browser/object-search.ts`
**Test**: `tools/src/browser/__tests__/object-search.test.ts`

Create a fluent search API for object definitions. The search takes an array of `ObjectDefinition` (pre-loaded from cache) and filters with chainable methods.

- `byName(pattern)` — string match (case-insensitive substring) or RegExp
- `byIdRange(min, max)` — inclusive range
- `interactive()` — `def.interactive === true`
- `solid()` — `def.solid === true`
- `hasAnimation()` — `def.animationId !== -1`
- `hasModels()` — `def.modelIds` is non-empty
- `bySize(width, length)` — exact match on width/length
- `limit(n)` — cap result count
- `results()` — return filtered `ObjectDefinition[]`
- `count()` — return filtered count

Tests: filter by name against real definitions, combine multiple filters, limit, empty results.

**Verify**: `pnpm typecheck && pnpm test`

### Step 2: ModelSearch fluent filter

**File**: `tools/src/browser/model-search.ts`
**Test**: `tools/src/browser/__tests__/model-search.test.ts`

Create a fluent search API for models. The search loads models from a CacheReader on demand (lazy decode).

```ts
interface ModelSearchResult {
  id: number;
  model: Model;
  format: ModelFormat;
}
```

- `byIdRange(min, max)` — inclusive range
- `byVertexCount(min, max)` — inclusive range
- `byFaceCount(min, max)` — inclusive range
- `byFormat(format)` — "legacy" or "smf"
- `hasAlpha()` — model.alpha is defined and has non-zero values
- `hasTextures()` — model.textureFaces is defined and non-empty
- `limit(n)` — cap result count
- `results()` — return `ModelSearchResult[]`
- `count()` — return filtered count

Constructor takes `CacheReader` and decodes models on demand using `decodeModelAuto`.

Tests: search real cache models, filter by vertex count, filter by format, limit.

**Verify**: `pnpm typecheck && pnpm test`

### Step 3: Wireframe SVG preview

**File**: `tools/src/browser/wireframe-preview.ts`
**Test**: `tools/src/browser/__tests__/wireframe-preview.test.ts`

Generate SVG wireframe projections from Model IR.

Algorithm:
1. Compute bounding box and center model at origin
2. Apply rotation matrix (default: isometric — rotateX(-30deg) then rotateY(45deg))
3. Project to 2D with orthographic projection
4. Compute face centroid Z for depth sorting (painter's algorithm)
5. Sort faces back-to-front
6. Render each face as an SVG `<polygon>` with optional fill
7. Wrap in `<svg>` element with viewBox

Options: width, height, strokeColor, fillColor, backgroundColor, showAxes.

Tests:
- Minimal model (1 triangle) produces valid SVG with 1 polygon
- Multi-face model produces correct polygon count
- Empty model (no faces) produces valid SVG with no polygons
- Custom options are reflected in SVG attributes
- SVG output is well-formed (starts with `<svg`, ends with `</svg>`)

**Verify**: `pnpm typecheck && pnpm test`

### Step 4: Model metadata report

**File**: `tools/src/browser/model-report.ts`
**Test**: `tools/src/browser/__tests__/model-report.test.ts`

Generate a structured metadata report for a decoded model.

```ts
function generateModelReport(id: number, model: Model, format: ModelFormat): ModelReport;
```

Computes: vertex/face/texture counts, bounding box, face color range, hasAlpha, hasPriorities.

Tests: report on real cache model, synthetic model with known values, model with alpha/priorities/textures.

**Verify**: `pnpm typecheck && pnpm test`

### Step 5: Compatibility report

**File**: `tools/src/browser/compatibility-report.ts`
**Test**: `tools/src/browser/__tests__/compatibility-report.test.ts`

Summarize mapping classifications for a set of objects against a MappingDatabase.

```ts
function generateCompatibilityReport(
  objects: WorldObject[],
  mapper: ObjectMapper,
): CompatibilityReport;
```

Counts objects by classification (EXACT/SUBSTITUTE/REMOVE/UNMAPPED), lists unmapped IDs, substituted pairs, removed IDs.

Tests: all-mapped scenario, mixed scenario, empty objects, empty database.

**Verify**: `pnpm typecheck && pnpm test`

### Step 6: Region analyzer

**File**: `tools/src/browser/region-analyzer.ts`
**Test**: `tools/src/browser/__tests__/region-analyzer.test.ts`

Analyze an ImportedRegion to determine what's needed for export.

```ts
function analyzeRegion(
  region: ImportedRegion,
  mapper?: ObjectMapper,
): RegionAnalysis;
```

Logic:
- Count unique object IDs and model IDs from the region
- If mapper provided, classify objects (mapped/unmapped/removed)
- Identify which model IDs are referenced but would need custom allocation (>= CUSTOM_MODEL_START)
- Group unmapped objects by sourceId with occurrence counts
- Look up definition names for unmapped objects from region.definitions

Tests: region with all mapped objects, region with unmapped objects, region requiring custom models.

**Verify**: `pnpm typecheck && pnpm test`

### Step 7: Mapping editor helpers

**File**: `tools/src/browser/mapping-editor.ts`
**Test**: `tools/src/browser/__tests__/mapping-editor.test.ts`

CRUD operations on MappingDatabase with validation.

```ts
class MappingEditor {
  constructor(db: MappingDatabase);
  set(sourceId: number, targetId: number, rule: MappingRule, note?: string): void;
  remove(sourceId: number): void;
  get(sourceId: number): MappingEntry | undefined;
  list(): Array<{ sourceId: number; entry: MappingEntry }>;
  importEntries(entries: Array<{ sourceId: number; targetId: number; rule: MappingRule }>): void;
  validate(): string[];
  build(): MappingDatabase;
}
```

Validation checks: duplicate entries (warn), negative IDs (error), rule consistency (substitute should have targetId).

Tests: set/get/remove cycle, import batch, validate catches errors, build produces valid database, list returns sorted entries.

**Verify**: `pnpm typecheck && pnpm test`

### Step 8: AssetBrowser facade and barrel exports

**File**: `tools/src/browser/asset-browser.ts`
**Test**: `tools/src/browser/__tests__/asset-browser.test.ts`
**File**: `tools/src/browser/index.ts`
**Modified**: `tools/src/index.ts`

Wire all sub-modules into the AssetBrowser facade class.

```ts
class AssetBrowser {
  private readonly cache: CacheReader;
  private objectDefs: ObjectDefinition[] | null = null;
  private readonly locDat: Buffer;
  private readonly locIdx: Buffer;

  constructor(cacheDir: string);

  objects(): ObjectSearch;
  models(): ModelSearch;
  previewModel(modelId: number, options?: PreviewOptions): string;
  modelReport(modelId: number): ModelReport;
  compatibilityReport(db: MappingDatabase): CompatibilityReport;
  analyzeRegion(region: ImportedRegion, db?: MappingDatabase): RegionAnalysis;
  editMappings(db: MappingDatabase): MappingEditor;
}
```

Lazy-loads object definitions on first `objects()` call. Reads models on demand from cache index 1.

Create `tools/src/browser/index.ts` barrel exporting all public types and classes.

Update `tools/src/index.ts` to re-export from `./browser/index.js`.

Tests: facade integration — construct with real cache dir, search objects, search models, generate preview, generate report.

**Verify**: `pnpm typecheck && pnpm test`

### Step 9: Final verification

Run full test suite and typecheck:

```
pnpm typecheck — zero errors
pnpm test — all tests pass (240 + new Phase 9 tests)
```

Update `plans/asset-pipeline/plan.md` and `plans/asset-pipeline/todo.md` with Phase 9 completion.

Write `plans/asset-pipeline/handovers/phase-9-handover.md`.
