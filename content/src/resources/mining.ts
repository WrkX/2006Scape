/**
 * Gathering resources module for mining.
 *
 * Representative OSRS-style copper rock driven entirely by the public
 * gathering SDK builder. The resource's object route is a Java-owned host
 * route; an equal-id cache or legacy rock at another tile keeps its complete
 * legacy mining behavior.
 *
 * @module resources/mining
 */

import { registerGatheringResource, registerModule } from "../sdk/index.js";

registerModule({ id: "mining-resources", schemaVersion: 1 }, () => {
  /**
   * Copper rocks (object 2091) mined with a bronze pickaxe (item 1265).
   * Success is deterministic per attempt tick; each success grants one copper
   * ore (item 436) and 18 mining XP. Depletes to an empty rock (object 452)
   * and respawns after 4 game cycles.
   */
  registerGatheringResource({
    id: "copper-rock",
    name: "Copper rocks",
    objectId: 2091,
    action: "first",
    skill: "mining",
    level: 1,
    tools: [{ itemId: 1265 }], // Bronze pickaxe
    animation: 625,
    intervalTicks: 4,
    successChance: { numerator: 1, denominator: 1 },
    rewards: [{ itemId: 436, amount: 1 }], // Copper ore
    experience: 18,
    depletedObjectId: 452,
    respawnTicks: 4,
  });
});
