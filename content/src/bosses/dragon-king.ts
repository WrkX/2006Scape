/**
 * Dragon King boss definition.
 *
 * A two-phase fire-breathing dragon boss with a melee phase above 50% HP
 * and a fire_wave special attack phase below 50%.  Respawns after 100 ticks.
 *
 * @module bosses/dragon-king
 */

import type { BossContext } from "../core/boss.js";

defineBoss({
  npcId: 12001,
  combatLevel: 450,
  maxHitpoints: 600,
  displayName: "Dragon King",

  onSpawn(ctx: BossContext): void {
    ctx.say("You dare enter my domain? You will burn!");
  },

  onTick(ctx: BossContext): void {
    // Phase check — below 50% HP the boss uses fire_wave.
    if (ctx.hpPercent < 0.5) {
      ctx.useSpecial("fire_wave");
    }
  },

  onDeath(ctx: BossContext): void {
    ctx.rollLoot("dragon_king_loot");
  },

  specials: {
    fire_wave: {
      cooldownTicks: 12,
      handler(ctx: BossContext): void {
        ctx.say("Burn!");
        for (const player of ctx.engagedPlayers) {
          player.message("The Dragon King's fire wave engulfs you!");
        }
      },
    },
  },

  phases: [
    {
      name: "Melee Phase",
      hpPercentThreshold: 100,
      onEnter(ctx: BossContext): void {
        ctx.say("Come, face me with steel!");
      },
    },
    {
      name: "Fire Phase",
      hpPercentThreshold: 50,
      onEnter(ctx: BossContext): void {
        ctx.say("Now you will feel true dragon fire!");
      },
    },
  ],

  respawnTicks: 100,
});
