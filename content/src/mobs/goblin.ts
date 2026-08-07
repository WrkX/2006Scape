/**
 * World mob module — Lumbridge goblin (npc 100) via {@link registerMob}.
 *
 * Only this cache id is scripted; other goblin variants keep legacy Java
 * `NpcCombat` behavior.
 *
 * @module mobs/goblin
 */

import { registerModule, registerMob } from "../sdk/index.js";

registerModule({ id: "world-mobs", schemaVersion: 1 }, () => {
  /**
   * Goblin (npc 100). Retaliate-only melee, 4-tick attack, max hit 1.
   * The Java mob runtime owns aggression/pathing/attack ticks from these
   * stats; optional hooks are omitted so the port stays fully declarative.
   */
  registerMob({
    id: "goblin",
    npcId: 100,
    name: "Goblin",
    aggression: 0,
    combatStyle: "melee",
    attackSpeed: 4,
    maxHit: 1,
  });
});
