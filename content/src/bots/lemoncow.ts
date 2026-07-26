/**
 * LemonCow bot profile.
 *
 * A laid-back skilling bot that trains fishing, cooking, and strength
 * with low efficiency and a social personality.
 *
 * Personality profile:
 * - efficiency: 0.35 (takes frequent breaks, not min-maxing)
 * - social: 0.60 (often chats with nearby players)
 * - risk: 0.20 (avoids dangerous content)
 *
 * @module bots/lemoncow
 */

import { botProfile } from "./bot-profile.js";
import type { Activity } from "../core/bot.js";
import type { SimulatedPlayer } from "../core/bot.js";

export const lemoncowProfile = botProfile("lemoncow")
  .username("LemonCow")
  .location({ x: 6985, y: 7005, plane: 0 })
  .description(
    "Laid-back skilling bot. efficiency=0.35 social=0.60 risk=0.20. " +
    "Trains fishing, cooking, and strength on Dragon Island.",
  )
  .skillLevel("fishing", 40)
  .skillLevel("cooking", 40)
  .skillLevel("strength", 30)
  .equipment({ weapon: "rune_scimitar", body: "rune_platebody", legs: "rune_platelegs" })
  .inventory("lobster", 15)
  .inventory("small_fishing_net", 1)
  .inventory("harpoon", 1)
  .goal({
    id: "train_fishing",
    label: "Train Fishing to 70",
    priority: 0,
    condition: (p: SimulatedPlayer): boolean =>
      p.skills.getBase("fishing") < 70,
    action: (_p: SimulatedPlayer): Activity => ({
      kind: "fishing",
      label: "Fishing at Dragon Island",
      spotIds: [1520],
      fish: "lobster",
      method: "cage",
      tool: "lobster_pot",
      onStart: () => {},
      onTick: () => ({ signal: "continue" }),
      onStop: () => {},
    }),
  })
  .goal({
    id: "train_cooking",
    label: "Train Cooking to 70",
    priority: 1,
    condition: (p: SimulatedPlayer): boolean =>
      p.skills.getBase("fishing") >= 70 &&
      p.skills.getBase("cooking") < 70,
    action: (_p: SimulatedPlayer): Activity => ({
      kind: "fishing",
      label: "Cooking caught fish",
      spotIds: [],
      fish: "lobster",
      onStart: () => {},
      onTick: () => ({ signal: "continue" }),
      onStop: () => {},
    } as Activity),
  })
  .goal({
    id: "train_strength",
    label: "Train Strength to 60",
    priority: 2,
    condition: (p: SimulatedPlayer): boolean =>
      p.skills.getBase("fishing") >= 70 &&
      p.skills.getBase("cooking") >= 70 &&
      p.skills.getBase("strength") < 60,
    action: (_p: SimulatedPlayer): Activity => ({
      kind: "combat",
      label: "Training strength on guards",
      npcIds: [5001],
      style: "melee",
      foodId: "lobster",
      eatThreshold: 0.4,
      lootDrops: true,
      bankLoot: false,
      onStart: () => {},
      onTick: () => ({ signal: "continue" }),
      onStop: () => {},
    }),
  })
  .goal({
    id: "idle",
    label: "All goals complete — idle",
    priority: 100,
    condition: (_p: SimulatedPlayer): boolean =>
      true,
    action: (_p: SimulatedPlayer): Activity => ({
      kind: "fishing",
      label: "Relaxing and fishing",
      spotIds: [1520],
      fish: "lobster",
      method: "cage",
      onStart: () => {},
      onTick: () => ({ signal: "continue" }),
      onStop: () => {},
    }),
  })
  .build();
