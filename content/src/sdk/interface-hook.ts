/**
 * Interface hook builders.
 *
 * {@link createInterfaceHook} validates and deep-freezes a canonical schema-v1
 * {@link InterfaceHookDefinition}; {@link registerInterfaceHook} registers it
 * through `defineInterfaceHook`. Button handlers are scoped to the hook's
 * interface id while it is the player's main frame.
 *
 * @module sdk/interface-hook
 */

import type {
  ButtonScriptContext,
  InterfaceHookDefinition,
  InterfaceHookScriptContext,
} from "../core/runtime.js";

const ID_PATTERN = /^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$/;
const MAX_INTERFACE_ID = 65535;
const MAX_BUTTON_ID = 255255;

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[sdk/interface-hook] ${message}`);
  }
}

function integral(value: number, min: number, max: number): boolean {
  return Number.isSafeInteger(value) && value >= min && value <= max;
}

function decodableButtonId(buttonId: number): boolean {
  // The Java runtime decodes a button as two unsigned bytes (first * 1000 +
  // second), so ids up to 255255 are valid. Use integer division: 255000/1000
  // is 255, but a float division of e.g. 255255/1000 is 255.255 > 255.
  return Math.floor(buttonId / 1000) <= 255 && buttonId % 1000 <= 255;
}

function validateButtons(
  buttons: InterfaceHookDefinition["buttons"],
): InterfaceHookDefinition["buttons"] | undefined {
  if (buttons === undefined) {
    return undefined;
  }
  assert(typeof buttons === "object" && buttons !== null
      && !Array.isArray(buttons),
    "interfaceHook.buttons must be an object when present");
  const normalized: Record<number, (context: ButtonScriptContext) => void> = {};
  for (const [key, handler] of Object.entries(buttons)) {
    const buttonId = Number(key);
    assert(integral(buttonId, 0, MAX_BUTTON_ID),
      `buttons key '${key}' must be an integer 0..${MAX_BUTTON_ID}`);
    assert(decodableButtonId(buttonId),
      `button id ${buttonId} is not decodable from two unsigned bytes`);
    assert(typeof handler === "function",
      `buttons[${buttonId}] must be a function`);
    assert(normalized[buttonId] === undefined,
      `duplicate button id ${buttonId} in buttons map`);
    normalized[buttonId] = handler;
  }
  return Object.freeze(normalized);
}

/**
 * Create a validated, deeply frozen {@link InterfaceHookDefinition}.
 */
export function createInterfaceHook(
  definition: InterfaceHookDefinition,
): InterfaceHookDefinition {
  assert(typeof definition.id === "string" && ID_PATTERN.test(definition.id),
    `invalid interface hook id '${String(definition.id)}': expected at most `
      + "64 characters of letters, digits, '.', '_', or '-'");
  assert(integral(definition.interfaceId, 0, MAX_INTERFACE_ID),
    `interfaceHook.interfaceId must be an integer 0..${MAX_INTERFACE_ID}, `
      + `got ${definition.interfaceId}`);
  const buttons = validateButtons(definition.buttons);
  if (definition.onOpen !== undefined) {
    assert(typeof definition.onOpen === "function",
      "interfaceHook.onOpen must be a function when present");
  }
  if (definition.onClose !== undefined) {
    assert(typeof definition.onClose === "function",
      "interfaceHook.onClose must be a function when present");
  }
  assert((buttons !== undefined && Object.keys(buttons).length > 0)
      || definition.onOpen !== undefined
      || definition.onClose !== undefined,
    "interface hook must define at least one button handler or lifecycle "
      + "callback");
  return Object.freeze({
    id: definition.id,
    interfaceId: definition.interfaceId,
    ...(buttons !== undefined ? { buttons } : {}),
    ...(definition.onOpen !== undefined ? { onOpen: definition.onOpen } : {}),
    ...(definition.onClose !== undefined
      ? { onClose: definition.onClose }
      : {}),
  });
}

/**
 * Validate an interface hook and register it via `defineInterfaceHook()`.
 */
export function registerInterfaceHook(
  definition: InterfaceHookDefinition,
): void {
  defineInterfaceHook(createInterfaceHook(definition));
}

export type {
  ButtonScriptContext,
  InterfaceHookDefinition,
  InterfaceHookScriptContext,
} from "../core/runtime.js";
