/**
 * Regenerate `docs/API_INVENTORY.md` from the compiled inventory
 * generator. Run from the repository root:
 *
 * ```bash
 * pnpm --filter @singlescape/content build
 * node content/scripts/api-inventory.mjs
 * ```
 */

import { writeFileSync } from "node:fs";
import { generateApiInventory } from "../dist/sdk/__tests__/api-inventory.js";

const documentPath = new URL("../../docs/API_INVENTORY.md", import.meta.url);

writeFileSync(documentPath, generateApiInventory() + "\n", "utf8");
console.log(`Wrote ${documentPath.pathname}`);
