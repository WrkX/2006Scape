/**
 * Content module manifest helpers.
 *
 * Content modules register through the Java bridge in one synchronous scope
 * so every definition and executable route they create carries the module's
 * logical id and declared schema version. Direct registrations outside a
 * scope are still supported and are recorded as legacy-unscoped
 * compatibility records by the Java host.
 *
 * @module manifest
 */

import type { ContentModuleDescriptor } from "./core/runtime.js";

const MODULE_ID_PATTERN = /^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$/;
const MAX_SCHEMA_VERSION = 255;

/**
 * Opens one content-module registration scope and evaluates the module's
 * registrations synchronously inside it.
 *
 * The id is a bounded logical identifier, never a host path. Optional
 * `onLoad`/`onUnload` hooks on the descriptor run as contained observers
 * around the activation commit.
 */
export function registerModule(
  descriptor: ContentModuleDescriptor,
  scope: () => void,
): void {
  if (!MODULE_ID_PATTERN.test(descriptor.id)) {
    throw new Error(
      `Invalid content module id '${descriptor.id}': expected at most 64 ` +
        "characters of letters, digits, '.', '_', or '-'",
    );
  }
  if (
    !Number.isInteger(descriptor.schemaVersion) ||
    descriptor.schemaVersion < 1 ||
    descriptor.schemaVersion > MAX_SCHEMA_VERSION
  ) {
    throw new Error(
      `Invalid content module schemaVersion '${descriptor.schemaVersion}' ` +
        `for '${descriptor.id}': expected an integer between 1 and ` +
        `${MAX_SCHEMA_VERSION}`,
    );
  }
  registerContentModule(descriptor, scope);
}
