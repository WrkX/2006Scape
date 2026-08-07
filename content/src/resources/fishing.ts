/**
 * Gathering resources module for fishing.
 *
 * Net/trap fishing spot (NPC 316) using the gathering runtime's NPC target
 * mode. The resource's NPC route is a Java-owned host route; an equal-id
 * legacy fishing spot at another tile keeps its complete legacy behavior.
 *
 * @module resources/fishing
 */

import { registerGatheringResource, registerModule } from "../sdk/index.js";

registerModule({ id: "fishing-resources", schemaVersion: 1 }, () => {
  /**
   * Net/trap fishing spot (NPC 316) fished with a small fishing net (item
   * 303). Success chance 3/4 per attempt tick; each success grants one raw
   * shrimps (item 317) and 10 fishing XP. The spot does not deplete — the
   * session continues until the player moves away or inventory is full.
   */
  registerGatheringResource({
    id: "net-fishing-spot",
    name: "Net/Trap",
    npcId: 316,
    action: "first",
    skill: "fishing",
    level: 1,
    tools: [{ itemId: 303 }], // Small fishing net
    animation: 621,
    intervalTicks: 2,
    successChance: { numerator: 3, denominator: 4 },
    rewards: [{ itemId: 317, amount: 1 }], // Raw shrimps
    experience: 10,
    depletes: false,
  });
});
