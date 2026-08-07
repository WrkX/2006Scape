/**
 * Custom-namespace overlay port — asset-pipeline ids 35000+.
 *
 * Demonstrates overlay authoring for one item, NPC, and object in the
 * custom namespace produced by the asset-pipeline (phase 7). Cache pack
 * entries for these ids must exist before the overlays load.
 *
 * The asset-pipeline pack is not yet shipped, so the production cache has no
 * definitions at these ids. Each overlay is gated behind a {@link dev} cache
 * capability probe: when the id is absent the overlay is skipped so a reload
 * candidate cannot be rejected for referencing an id the deployed cache does
 * not contain. Tests that fabricate the custom ids still register every
 * overlay.
 *
 * @module overlays/custom-port
 */

import {
  CUSTOM_ITEM_START,
  CUSTOM_NPC_START,
  CUSTOM_OBJECT_START,
  registerItemOverlay,
  registerModule,
  registerNpcOverlay,
  registerObjectOverlay,
} from "../sdk/index.js";

registerModule({ id: "custom-namespace-overlays", schemaVersion: 1 }, () => {
  if (dev.hasItemId(CUSTOM_ITEM_START)) {
    registerItemOverlay({
      id: "ported-bronze-sword",
      itemId: CUSTOM_ITEM_START,
      name: "Ported bronze sword",
      examine: "A sword from the custom asset namespace.",
      equipSlot: "weapon",
      requirements: { attack: 1 },
      bonuses: {
        attackStab: 4,
        attackSlash: 3,
        strength: 5,
      },
    });
  }

  if (dev.hasNpcId(CUSTOM_NPC_START)) {
    registerNpcOverlay({
      id: "ported-town-guard",
      npcId: CUSTOM_NPC_START,
      name: "Ported town guard",
      combatLevel: 21,
      hitpoints: 22,
    });
  }

  if (dev.hasObjectId(CUSTOM_OBJECT_START)) {
    registerObjectOverlay({
      id: "ported-signpost",
      objectId: CUSTOM_OBJECT_START,
      name: "Ported signpost",
      examine: "A signpost shipped through the asset pipeline.",
      actions: ["Read"],
    });
  }
});
