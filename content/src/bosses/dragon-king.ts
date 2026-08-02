/**
 * Dragon King boss definition.
 *
 * A two-phase fire-breathing dragon boss with a melee phase above 50% HP
 * and a fire_wave special attack phase below 50%.  The custom npc id 12001
 * had no npc.json definition; WP3 migrated the fixture to the loaded
 * Black dragon (54) with the canonical schema-v1 descriptor and the
 * definition-backed host command route.
 *
 * @module bosses/dragon-king
 */

import type { BossRuntimeContext } from "../core/boss.js";

defineBoss({
  id: "dragon-king",
  npcId: 54,
  name: "Dragon King",
  combatLevel: 450,
  maxHitpoints: 600,
  maxHit: 40,
  attack: 350,
  defence: 350,
  arena: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4711, plane: 1 },
  spawn: { x: 2271, y: 4698 },
  command: "dragon-king",
  closeCommand: "dragon-king-close",
  dropTable: "dragon_king_loot",
  privateTicks: 200,

  onSpawn(ctx: BossRuntimeContext): void {
    ctx.say("You dare enter my domain? You will burn!");
  },

  phases: [
    {
      name: "Melee Phase",
      hpPercentThreshold: 100,
      onEnter(ctx: BossRuntimeContext): void {
        ctx.say("Come, face me with steel!");
      },
    },
    {
      name: "Fire Phase",
      hpPercentThreshold: 50,
      onEnter(ctx: BossRuntimeContext): void {
        ctx.say("Now you will feel true dragon fire!");
        ctx.useSpecial("fire_wave");
      },
    },
  ],

  specials: {
    fire_wave: {
      cooldownTicks: 12,
      handler(ctx: BossRuntimeContext): void {
        ctx.say("Burn!");
        const players = ctx.participants();
        for (let index = 0; index < players.length(); index++) {
          const player = players.get(index);
          if (player !== null) {
            player.message("The Dragon King's fire wave engulfs you!");
          }
        }
      },
    },
  },
});
