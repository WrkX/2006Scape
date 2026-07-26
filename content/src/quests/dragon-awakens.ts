/**
 * "Dragon Awakens" quest definition.
 *
 * A master-level quest requiring completion of "Dragon Slayer" and
 * high combat stats.  Players must speak to the Elder Wizard, collect
 * dragon scales, and defeat a Dragon Guardian before completing the quest.
 *
 * @module quests/dragon-awakens
 */

import { createQuest, createStage } from "./quest-builder.js";
import type { Player } from "../core/player.js";

const dragonAwakens = createQuest({
  id: "dragon_awakens",
  name: "Dragon Awakens",
  difficulty: "master",

  requirements: {
    quests: ["dragon_slayer"],
    skills: { defence: 70, magic: 60 },
    combatLevel: 85,
  },

  startPoint: { x: 7000, y: 7020, plane: 0 },

  startNpc: {
    npcId: 667,
    dialogue: {
      type: "npc",
      title: "Elder Wizard",
      lines: [
        "Greetings, adventurer. I sense the ancient dragons stir once more.",
        "Their king slumbers beneath the volcano, but dark forces seek to",
        "awaken him. Will you help me prevent this catastrophe?",
      ],
      options: [
        {
          text: "Yes, I will help.",
          handler: (player: Player): void => {
            player.message("The Elder Wizard nods solemnly.");
          },
        },
        {
          text: "No, this sounds too dangerous.",
          handler: (player: Player): void => {
            player.message("The wizard sighs. 'Perhaps another time, then.'");
          },
        },
      ],
    },
  },

  stages: [
    createStage({
      id: "start",
      description: "Speak to the Elder Wizard on Dragon Island.",
      onEnter(player: Player): void {
        player.message("The Elder Wizard awaits you on Dragon Island.");
        player.message("You can reach the island by boat from Port Sarim.");
      },
      condition(player: Player): boolean {
        return player.quests.getStage("dragon_awakens") > 0;
      },
    }),
    createStage({
      id: "collect_scales",
      description: "Gather 5 dragon scales from the Dragon Guardians.",
      onEnter(player: Player): void {
        player.message("Collect 5 dragon scales from the Dragon Guardians");
        player.message("that patrol the volcanic peaks to the northeast.");
      },
      condition(player: Player): boolean {
        return player.inventory.contains("dragon_scales", 5);
      },
      onComplete(player: Player): void {
        player.message("You have collected enough dragon scales.");
        player.inventory.remove("dragon_scales", 5);
      },
    }),
    createStage({
      id: "defeat_guardian",
      description: "Defeat the alpha Dragon Guardian blocking the volcano entrance.",
      onEnter(player: Player): void {
        player.message("An alpha Dragon Guardian blocks the volcano entrance.");
        player.message("Defeat it to reach the Dragon King's lair.");
      },
      condition(_player: Player): boolean {
        // The actual check is handled by the NPC kill listener via onObject
        // or an external kill-tracker.  Return true when the kill is registered.
        // In practice this would be set by the engine when the NPC dies.
        return false;
      },
    }),
    createStage({
      id: "completed",
      description: "Quest complete! You have prevented the awakening.",
      onEnter(player: Player): void {
        player.message("Congratulations! You have completed Dragon Awakens!");
        player.message("The Dragon Island is now safe — for now.");
      },
      condition(): boolean {
        return true;
      },
    }),
  ],

  rewards: {
    experience: { defence: 50000, magic: 30000 },
    items: { dragon_token: 1 },
    questPoints: 3,
    unlocks: ["dragon_island_access"],
  },

  onGlobalComplete(player: Player): void {
    player.message("Dragon Island is now fully accessible to you.");
  },
});

defineQuest(dragonAwakens);
