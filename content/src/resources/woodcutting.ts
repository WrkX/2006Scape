/**
 * Gathering resources module.
 *
 * Representative OSRS-style woodcutting resource driven entirely by the
 * public gathering SDK builder. The resource's object route is a Java-owned
 * host route; an equal-id cache or legacy tree at another tile keeps its
 * complete legacy woodcutting behavior.
 *
 * Phase 5 WP10: the resources register under the `woodcutting-resources`
 * content module. The area-projected tree on Dragon Island (object 1276 at
 * (2858, 9660)) is harvested by the same global route, so the vertical pack
 * crosses the area runtime and the resource runtime on one tile.
 *
 * @module resources/woodcutting
 */

import { registerGatheringResource, registerModule } from "../sdk/index.js";

registerModule({ id: "woodcutting-resources", schemaVersion: 1 }, () => {
  /**
   * Regular tree (object 1276, "Tree") harvested with a bronze axe (item
   * 1351). Success chance 3/4 per attempt tick; each success grants one log
   * (item 1511) and 25 woodcutting XP. Depletes to a stump (object 1341) and
   * respawns after 4 game cycles.
   */
  registerGatheringResource({
    id: "tree",
    name: "Tree",
    objectId: 1276,
    action: "first",
    skill: "woodcutting",
    level: 1,
    tools: [{ itemId: 1351 }], // Bronze axe
    animation: 879,
    intervalTicks: 4,
    successChance: { numerator: 3, denominator: 4 },
    rewards: [{ itemId: 1511, amount: 1 }], // Logs
    experience: 25,
    depletedObjectId: 1341, // Stump
    respawnTicks: 4,
  });

  /**
   * Oak tree (object 1281, "Oak") harvested with a bronze axe. Success chance
   * 1/2; each success grants one oak logs (item 1521) and 37.5 -> 37
   * woodcutting XP. Depletes to a stump and respawns after 8 game cycles.
   */
  registerGatheringResource({
    id: "oak-tree",
    name: "Oak",
    objectId: 1281,
    action: "first",
    skill: "woodcutting",
    level: 15,
    tools: [{ itemId: 1351 }], // Bronze axe
    animation: 879,
    intervalTicks: 4,
    successChance: { numerator: 1, denominator: 2 },
    rewards: [{ itemId: 1521, amount: 1 }], // Oak logs
    experience: 37,
    depletedObjectId: 1341, // Stump
    respawnTicks: 8,
  });
});
