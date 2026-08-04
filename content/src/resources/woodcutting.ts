/**
 * Gathering resources module.
 *
 * Representative OSRS-style woodcutting resource driven entirely by the
 * public gathering SDK builder. The resource's object route is a Java-owned
 * host route; an equal-id cache or legacy tree at another tile keeps its
 * complete legacy woodcutting behavior.
 *
 * @module resources/woodcutting
 */

import { createGatheringResource } from "../sdk/gathering.js";

/**
 * Regular tree (object 1276, "Tree") harvested with a bronze axe (item
 * 1351). Success chance 3/4 per attempt tick; each success grants one log
 * (item 1511) and 25 woodcutting XP. Depletes to a stump (object 1341) and
 * respawns after 4 game cycles.
 */
defineGatheringResource(createGatheringResource({
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
}));

/**
 * Oak tree (object 1281, "Oak") harvested with a bronze axe. Success chance
 * 1/2; each success grants one oak logs (item 1521) and 37.5 -> 37
 * woodcutting XP. Depletes to a stump and respawns after 8 game cycles.
 */
defineGatheringResource(createGatheringResource({
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
}));
