/**
 * Cooking skill guide using the generic quest-detail interface 8134.
 *
 * Open with `::cookingguide`. Button handlers are scoped to interface 8134
 * via {@link registerInterfaceHook}; the global close button keeps legacy
 * behavior on other interfaces.
 *
 * @module interfaces/cooking-guide
 */

import {
  registerInterfaceHook,
  registerModule,
} from "../sdk/index.js";

/** Generic legacy quest-detail interface from the shipped cache. */
const COOKING_GUIDE_INTERFACE = 8134;
const TITLE_COMPONENT = 8144;
const SUMMARY_COMPONENT = 8147;
const CLOSE_BUTTON = 55096;

registerModule({ id: "cooking-guide", schemaVersion: 1 }, () => {
  registerInterfaceHook({
    id: "cooking-guide",
    interfaceId: COOKING_GUIDE_INTERFACE,
    onOpen: (context) => {
      const presentation = context.player.getPresentation();
      presentation.setText(TITLE_COMPONENT, "@dre@Cooking Guide");
      presentation.setText(TITLE_COMPONENT + 1, "");
      presentation.setText(
        SUMMARY_COMPONENT,
        "Cook raw food on ranges and fires. Higher levels burn food less "
          + "often. Shrimps are a good first target at level 1.",
      );
      presentation.setText(SUMMARY_COMPONENT + 1, "");
    },
    buttons: {
      [CLOSE_BUTTON]: (context) => {
        context.player.message("Cooking guide closed.");
        context.player.closeInterfaces();
      },
    },
  });

  onCommand("cookingguide", (context) => {
    openCookingGuide(context.player);
  });
});

/**
 * Opens the cooking guide for one player. Intended for `onCommand` wiring.
 */
export function openCookingGuide(
  player: import("../core/runtime.js").ScriptedPlayer,
): void {
  player.getPresentation().showInterface(COOKING_GUIDE_INTERFACE);
}
