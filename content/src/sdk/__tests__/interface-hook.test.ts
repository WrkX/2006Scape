import { test } from "node:test";
import assert from "node:assert/strict";
import {
  createInterfaceHook,
  registerInterfaceHook,
  type InterfaceHookDefinition,
} from "../interface-hook.js";

function options(
  overrides: Partial<InterfaceHookDefinition> = {},
): InterfaceHookDefinition {
  return {
    id: "cooking-guide",
    interfaceId: 8134,
    buttons: {
      55096: () => {},
    },
    ...overrides,
  };
}

test("createInterfaceHook validates and freezes a canonical definition", () => {
  const hook = createInterfaceHook(options());
  assert.equal(hook.id, "cooking-guide");
  assert.equal(hook.interfaceId, 8134);
  assert.equal(Object.keys(hook.buttons ?? {}).length, 1);
  assert.throws(() => {
    (hook as { interfaceId: number }).interfaceId = 1;
  });
});

test("createInterfaceHook requires at least one handler or lifecycle callback", () => {
  assert.throws(
    () => createInterfaceHook({
      id: "empty",
      interfaceId: 100,
    }),
    /at least one button handler or lifecycle callback/,
  );
});

test("createInterfaceHook accepts the maximum decodable button id 255255", () => {
  const hook = createInterfaceHook(
    options({ buttons: { 255255: () => {} } }),
  );
  assert.equal(Object.keys(hook.buttons ?? {}).length, 1);
});

test("createInterfaceHook accepts high ids a float division would reject", () => {
  // The original check used float division (255001/1000 = 255.001 > 255) and
  // wrongly rejected 255001..255255; integer division decodes them correctly.
  const hook = createInterfaceHook(
    options({ buttons: { 255001: () => {} } }),
  );
  assert.equal(Object.keys(hook.buttons ?? {}).length, 1);
});

test("createInterfaceHook rejects an out-of-range button id 255256", () => {
  assert.throws(
    () => createInterfaceHook(
      options({ buttons: { 255256: () => {} } }),
    ),
    /integer 0\.\.255255/,
  );
});

test("registerInterfaceHook forwards a frozen definition", () => {
  let captured: InterfaceHookDefinition | undefined;
  (globalThis as { defineInterfaceHook?: unknown }).defineInterfaceHook =
    (definition: InterfaceHookDefinition) => {
      captured = definition;
    };
  try {
    registerInterfaceHook(options({ onOpen: () => {} }));
    assert.equal(captured?.id, "cooking-guide");
    assert.equal(captured?.interfaceId, 8134);
    assert.equal(typeof captured?.onOpen, "function");
  } finally {
    delete (globalThis as { defineInterfaceHook?: unknown }).defineInterfaceHook;
  }
});
