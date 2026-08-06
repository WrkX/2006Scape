---
type: planning
entity: phase
plan: asset-pipeline
phase: 9
status: completed
created: 2026-07-29
updated: 2026-07-29
---

# Phase 9: Visual Asset Browser

## Objective

Build a programmatic asset browsing layer for `@singlescape/tools` that provides search, preview, compatibility reporting, and mapping controls over the cache's model and object definition database. This is a headless TypeScript API — "visual" means structured preview data (SVG wireframes) and machine-readable reports, not a GUI.

## Scope

### In Scope

- **AssetBrowser class**: High-level facade that loads a cache directory and provides search, preview, and report methods
- **Object search**: Filter object definitions by name, ID range, properties (interactive, solid, has animation, has models)
- **Model search**: Filter models by ID range, vertex count, face count, format (legacy/SMF)
- **Model wireframe preview**: Generate 2D SVG wireframe projections from 3D Model IR (configurable projection, dimensions, colors)
- **Model metadata report**: Vertex/face/texture counts, bounding box, format detection, alpha/presence flags
- **Compatibility report**: Summarize mapping classifications (EXACT/SUBSTITUTE/REMOVE/UNMAPPED counts and details)
- **Bulk region analysis**: Given a source region's objects, report which are mapped, unmapped, need custom models, and which models exist in the target cache
- **Mapping editor helpers**: CRUD operations on MappingDatabase entries with validation

### Out of Scope

- GUI/TUI/browser UI — this is a programmatic API only
- 3D rendering (OpenGL, WebGL) — wireframe SVG only
- Animation preview — requires skeleton/sequence data not in IR
- Texture preview — requires texture atlas decoding
- Changes to the Java engine

## Prerequisites

- [x] Phase 6 complete: model decoder/encoder
- [x] Phase 7 complete: definition decoder/encoder, IdRegistry, ObjectMapper
- [x] Phase 8 complete: dual model format dispatch

## Technical Design

### Module Structure

```
src/browser/
├── index.ts                  # Barrel exports
├── asset-browser.ts          # AssetBrowser facade class
├── object-search.ts          # ObjectSearch fluent filter
├── model-search.ts           # ModelSearch fluent filter
├── wireframe-preview.ts      # SVG wireframe generation
├── model-report.ts           # Model metadata report
├── compatibility-report.ts   # Mapping classification summary
├── region-analyzer.ts        # Bulk region analysis
└── mapping-editor.ts         # MappingDatabase CRUD helpers
```

### AssetBrowser Facade

```ts
class AssetBrowser {
  constructor(cacheDir: string);

  // Search
  objects(): ObjectSearch;
  models(): ModelSearch;

  // Preview
  previewModel(modelId: number, options?: PreviewOptions): string; // SVG string
  modelReport(modelId: number): ModelReport;

  // Reports
  compatibilityReport(db: MappingDatabase): CompatibilityReport;
  analyzeRegion(region: ImportedRegion, db?: MappingDatabase): RegionAnalysis;

  // Mapping
  editMappings(db: MappingDatabase): MappingEditor;
}
```

### ObjectSearch Fluent API

```ts
class ObjectSearch {
  byName(pattern: string | RegExp): this;
  byIdRange(min: number, max: number): this;
  interactive(): this;
  solid(): this;
  hasAnimation(): this;
  hasModels(): this;
  bySize(width: number, length: number): this;
  limit(n: number): this;
  results(): ObjectDefinition[];
  count(): number;
}
```

### ModelSearch Fluent API

```ts
class ModelSearch {
  byIdRange(min: number, max: number): this;
  byVertexCount(min: number, max: number): this;
  byFaceCount(min: number, max: number): this;
  byFormat(format: ModelFormat): this;
  hasAlpha(): this;
  hasTextures(): this;
  limit(n: number): this;
  results(): ModelSearchResult[];  // { id, model, format }
  count(): number;
}
```

### Wireframe Preview

```ts
interface PreviewOptions {
  width?: number;       // SVG width (default 400)
  height?: number;      // SVG height (default 400)
  strokeColor?: string; // Edge color (default "#333")
  fillColor?: string;   // Face fill (default "#888")
  backgroundColor?: string; // Background (default "#fff")
  showAxes?: boolean;   // Show XYZ axes (default false)
}

function renderWireframe(model: Model, options?: PreviewOptions): string; // SVG
```

Projection: Simple orthographic projection with configurable camera angle. Default: isometric-like view (rotate X by ~30deg, Y by ~45deg). Sort faces back-to-front for basic painter's algorithm.

### Model Metadata Report

```ts
interface ModelReport {
  id: number;
  format: ModelFormat;
  vertexCount: number;
  faceCount: number;
  textureFaceCount: number;
  hasAlpha: boolean;
  hasPriorities: boolean;
  boundingBox: { minX: number; maxX: number; minY: number; maxY: number; minZ: number; maxZ: number };
  faceColorRange: { min: number; max: number };
}
```

### Compatibility Report

```ts
interface CompatibilityReport {
  total: number;
  byCategory: Record<ObjectClassification, number>;
  unmappedIds: number[];
  substitutedIds: { sourceId: number; targetId: number }[];
  removedIds: number[];
}
```

### Region Analysis

```ts
interface RegionAnalysis {
  regionId: number;
  totalObjects: number;
  uniqueObjectIds: number;
  uniqueModelIds: number;
  mapped: number;
  unmapped: number;
  removed: number;
  customModelsNeeded: number;   // models not in target cache
  customObjectsNeeded: number;  // objects needing custom definitions
  unmappedDetails: { objectId: number; count: number; sampleName?: string }[];
}
```

### MappingEditor

```ts
class MappingEditor {
  constructor(db: MappingDatabase);

  set(sourceId: number, targetId: number, rule: MappingRule, note?: string): void;
  remove(sourceId: number): void;
  get(sourceId: number): MappingEntry | undefined;
  list(): { sourceId: number; entry: MappingEntry }[];
  importEntries(entries: { sourceId: number; targetId: number; rule: MappingRule }[]): void;
  validate(): string[];       // Return list of issues
  build(): MappingDatabase;   // Return the updated database
}
```

## Deliverables

| File | Purpose |
|------|---------|
| `tools/src/browser/asset-browser.ts` | AssetBrowser facade |
| `tools/src/browser/object-search.ts` | ObjectSearch fluent filter |
| `tools/src/browser/model-search.ts` | ModelSearch fluent filter |
| `tools/src/browser/wireframe-preview.ts` | SVG wireframe renderer |
| `tools/src/browser/model-report.ts` | Model metadata report generator |
| `tools/src/browser/compatibility-report.ts` | Compatibility report generator |
| `tools/src/browser/region-analyzer.ts` | Bulk region analyzer |
| `tools/src/browser/mapping-editor.ts` | MappingDatabase CRUD helpers |
| `tools/src/browser/index.ts` | Barrel exports |
| `tools/src/browser/__tests__/object-search.test.ts` | ObjectSearch tests |
| `tools/src/browser/__tests__/model-search.test.ts` | ModelSearch tests |
| `tools/src/browser/__tests__/wireframe-preview.test.ts` | Wireframe SVG tests |
| `tools/src/browser/__tests__/model-report.test.ts` | Model report tests |
| `tools/src/browser/__tests__/compatibility-report.test.ts` | Compatibility report tests |
| `tools/src/browser/__tests__/region-analyzer.test.ts` | Region analysis tests |
| `tools/src/browser/__tests__/mapping-editor.test.ts` | Mapping editor tests |
| `tools/src/browser/__tests__/asset-browser.test.ts` | AssetBrowser facade integration tests |
| `plans/asset-pipeline/phases/phase-9.md` | This file |
| `plans/asset-pipeline/implementation/phase-9-impl.md` | Implementation plan |

## Acceptance Criteria

- [ ] `ObjectSearch` filters by name, ID range, interactive, solid, animation, models, size
- [ ] `ModelSearch` filters by ID range, vertex/face count, format, alpha, textures
- [ ] `renderWireframe` produces valid SVG from a Model IR
- [ ] Wireframe SVG contains expected polygon elements for a model with faces
- [ ] `ModelReport` returns accurate vertex/face/texture/bounding box data
- [ ] `CompatibilityReport` correctly counts EXACT/SUBSTITUTE/REMOVE/UNMAPPED
- [ ] `RegionAnalysis` identifies unmapped objects and needed custom models
- [ ] `MappingEditor` supports set/remove/get/list/import/validate/build
- [ ] `AssetBrowser` facade delegates correctly to all sub-modules
- [ ] All tests pass against real cache fixtures where applicable
- [ ] `pnpm typecheck` — zero errors
- [ ] `pnpm test` — all tests pass

## Risks

| Risk | Mitigation |
|------|------------|
| SVG wireframe may be hard to read for complex models | Support configurable dimensions and stroke width; sort faces by depth |
| Searching all models requires decoding every cache entry | Cache decoded results within the search session; support limit() to bound work |
| Model IDs in the cache may have gaps | Search iterates cache file count, skips null entries |
