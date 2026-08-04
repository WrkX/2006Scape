/**
 * Generated API inventory.
 *
 * Extracts the runtime globals the Java bridge installs (from every
 * `declare global` block in `content/src`) and the export surface of the
 * public SDK barrel modules, and renders the canonical
 * `docs/API_INVENTORY.md`. The inventory test regenerates the document
 * and fails when it is stale.
 *
 * @module sdk/__tests__/api-inventory
 */

import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

/** Repository `content/` root (this module compiles to `dist/sdk/__tests__/`). */
export const CONTENT_ROOT = new URL("../../../", import.meta.url).pathname;

const SRC_ROOT = join(CONTENT_ROOT, "src");
const SKIP_DIRS = new Set(["__tests__"]);

interface GlobalRecord {
  name: string;
  file: string;
}

interface ModuleExports {
  module: string;
  exports: string[];
}

function walkSources(dir: string): string[] {
  const files: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) {
      if (!SKIP_DIRS.has(entry.name)) {
        files.push(...walkSources(path));
      }
    } else if (entry.name.endsWith(".ts")) {
      files.push(path);
    }
  }
  return files;
}

const GLOBAL_BLOCK = /declare\s+global\s*\{([\s\S]*?)\}/g;
const GLOBAL_MEMBER = /\b(?:const|function)\s+([A-Za-z_$][\w$]*)/g;

function extractGlobals(files: string[]): GlobalRecord[] {
  const globals: GlobalRecord[] = [];
  for (const file of files) {
    const source = readFileSync(file, "utf8");
    const relative = file.replace(SRC_ROOT + "/", "").replace(/\.ts$/, ".ts");
    for (const block of source.matchAll(GLOBAL_BLOCK)) {
      for (const member of block[1].matchAll(GLOBAL_MEMBER)) {
        globals.push({ name: member[1], file: relative });
      }
    }
  }
  globals.sort((a, b) => a.name.localeCompare(b.name));
  return globals;
}

const EXPORT_STAR = /export\s+(?:type\s+)?\*\s+from\s+"([^"]+)"/g;
const EXPORT_NAMED_FROM =
  /export\s+(?:type\s+)?\{([^}]*)\}\s+from\s+"([^"]+)"/g;
const EXPORT_DECL =
  /export\s+(?:declare\s+)?(?:async\s+)?(?:function|const|class|interface|type|enum)\s+([A-Za-z_$][\w$]*)/g;
const EXPORT_NAMED = /export\s+(?:type\s+)?\{([^}]*)\}/g;

function resolveModule(barrelDir: string, specifier: string): string | null {
  const file = join(barrelDir, specifier.replace(/\.js$/, ".ts"));
  if (!file.startsWith(SRC_ROOT + "/")) {
    return null;
  }
  return file;
}

function extractModuleExports(barrelPath: string): ModuleExports[] {
  const barrel = readFileSync(barrelPath, "utf8");
  const barrelDir = join(barrelPath, "..");
  const modules = new Map<string, Set<string>>();
  const addModule = (file: string, names: Iterable<string>): void => {
    let set = modules.get(file);
    if (set === undefined) {
      set = new Set();
      modules.set(file, set);
    }
    for (const name of names) {
      set.add(name);
    }
  };
  for (const match of barrel.matchAll(EXPORT_STAR)) {
    const file = resolveModule(barrelDir, match[1]);
    if (file === null) {
      continue;
    }
    const source = readFileSync(file, "utf8");
    const names = new Set<string>();
    for (const declaration of source.matchAll(EXPORT_DECL)) {
      names.add(declaration[1]);
    }
    for (const named of source.matchAll(EXPORT_NAMED)) {
      for (const entry of named[1].split(",")) {
        const name = entry.trim().split(/\s+as\s+/).pop()?.trim();
        if (name !== undefined && name !== "") {
          names.add(name);
        }
      }
    }
    addModule(file, names);
  }
  for (const match of barrel.matchAll(EXPORT_NAMED_FROM)) {
    const file = resolveModule(barrelDir, match[2]);
    if (file === null) {
      continue;
    }
    const names = new Set<string>();
    for (const entry of match[1].split(",")) {
      const name = entry.trim().split(/\s+as\s+/).pop()?.trim();
      if (name !== undefined && name !== "") {
        names.add(name);
      }
    }
    addModule(file, names);
  }
  return [...modules.entries()]
    .map(([file, names]) => ({
      module: file.replace(SRC_ROOT + "/", ""),
      exports: [...names].sort((a, b) => a.localeCompare(b)),
    }))
    .sort((a, b) => a.module.localeCompare(b.module));
}

/**
 * Render the canonical API inventory markdown.
 */
export function generateApiInventory(): string {
  const files = walkSources(SRC_ROOT).sort();
  const globals = extractGlobals(files);
  const modules = extractModuleExports(join(SRC_ROOT, "sdk", "index.ts"));

  const lines: string[] = [];
  lines.push("# SingleScape TypeScript SDK — API Inventory");
  lines.push("");
  lines.push("> Generated from the TypeScript sources by");
  lines.push("> `content/scripts/api-inventory.mjs` — do not edit by hand.");
  lines.push("> `pnpm --filter @singlescape/content test` fails when this");
  lines.push("> document is stale.");
  lines.push("");
  lines.push("## Runtime globals installed by the Java bridge");
  lines.push("");
  lines.push("Every global below is provided by the host at runtime and is");
  lines.push("declared for the compiler by the listed module. Content never");
  lines.push("imports these names; it calls them directly.");
  lines.push("");
  lines.push("| Global | Declared in |");
  lines.push("|--------|-------------|");
  for (const global of globals) {
    lines.push(`| \`${global.name}\` | \`${global.file}\` |`);
  }
  lines.push("");
  lines.push("## Public SDK barrel (`content/src/sdk/index.ts`)");
  lines.push("");
  lines.push("| Module | Exports |");
  lines.push("|--------|---------|");
  for (const module of modules) {
    lines.push(`| \`${module.module}\` | ${module.exports.join(", ")} |`);
  }
  lines.push("");
  return lines.join("\n");
}
