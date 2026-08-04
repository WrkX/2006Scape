/**
 * Canonical drop-table builders.
 *
 * Re-exports the canonical `createDropTable` and the fluent
 * {@link DropTableBuilder} from the core module so the SDK barrel has one
 * author-facing home for named drop tables.
 *
 * @module sdk/drop-tables
 */

export {
  createDropTable,
  dropTable,
  DropTableBuilder,
  COMMON_WEIGHT,
  UNCOMMON_WEIGHT,
  RARE_WEIGHT,
} from "../core/drop-tables.js";

export type { DropTableDefinition, DropTableEntry } from "../core/runtime.js";
