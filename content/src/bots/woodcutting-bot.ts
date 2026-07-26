/**
 * WC_Bot_01 — a dedicated woodcutting bot.
 *
 * Spawns near Draynor Village and chops normal, oak, and willow trees
 * until reaching level 99 Woodcutting.  Banks logs when inventory is full.
 *
 * @module bots/woodcutting-bot
 */

import { createSkiller } from "./bot-profile.js";

export const wcBot01 = createSkiller("woodcutting_bot_01", {
  username: "WC_Bot_01",
  // Draynor Village approximate coordinates
  location: { x: 3090, y: 3250, plane: 0 },
  skill: "woodcutting",
  targetLevel: 99,
  // Normal trees (1276, 1278), Oak trees (1281), Willow trees (1308)
  treeIds: [1308, 1281, 1276, 1278],
  logItem: "willow_logs",
  axeTier: "rune",
  bankWhenFull: true,
});
