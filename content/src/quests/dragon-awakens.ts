/**
 * Complete public-bridge quest used as the Phase 3 content proof.
 *
 * Phase 5 WP10: the definition and its interaction routes register under
 * the `dragon-awakens` content module, and the dialogue uses the public SDK
 * dialogue helpers. The generic quest journal projects the authoritative
 * objective through `player.quest(id).objective()`.
 */

import {
  createQuest,
  createStage,
  endDialogue,
  registerModule,
  sayNpc,
  sayOptions,
} from "../sdk/index.js";

export const DRAGON_AWAKENS_ID = "dragon-awakens";
export const CHRONOZON_NPC = 667;
export const DRAGON_GUARDIAN_NPC = 941;
export const DRAGON_ALTAR_OBJECT = 409;
export const DRAGON_BONES_ITEM = 536;
export const COINS_ITEM = 995;

registerModule({ id: "dragon-awakens", schemaVersion: 1 }, () => {
  const dragonAwakens = createQuest({
    id: DRAGON_AWAKENS_ID,
    name: "Dragon Awakens",
    summary: "Complete Chronozon's dragon-bone rite in the Wilderness.",
    stages: [
      createStage(0, "Speak to Chronozon again for instructions."),
      createStage(1, "Slay a green dragon and recover its dragon bones."),
      createStage(2, "Use the dragon bones on an altar."),
      createStage(3, "Defeat another green dragon to seal the rite."),
      createStage(4, "Return to Chronozon to claim your reward."),
    ],
    requirements: {
      skills: [{ skill: "magic", level: 1 }],
    },
    rewards: {
      questPoints: 3,
      items: [{ itemId: COINS_ITEM, amount: 1000 }],
      experience: [{ skill: "magic", amount: 1000 }],
    },
  });

  defineQuest(dragonAwakens);

  onNpc(CHRONOZON_NPC, "first", ({ player }) => {
    const quest = player.quest(DRAGON_AWAKENS_ID);
    if (quest === null) return;
    if (quest.state() === "not_started") {
      const eligibility = quest.canStart();
      if (!eligibility.ok()) {
        sayNpc(player, CHRONOZON_NPC, "You are not ready for my dragon rite.");
        endDialogue(player);
        return;
      }
      sayNpc(player, CHRONOZON_NPC, "The green dragons guard a power I require.");
      sayOptions(
        player,
        ["I will perform the rite.", "No, this is too dangerous."],
        (choice) => {
          if (choice !== 0) {
            sayNpc(player, CHRONOZON_NPC, "Then leave before the dragons find you.");
            endDialogue(player);
            return;
          }
          const result = quest.start();
          sayNpc(
            player,
            CHRONOZON_NPC,
            result.changed()
              ? "Return to me and I will explain the first step."
              : "The rite could not be started.",
          );
          endDialogue(player);
        },
      );
      return;
    }
    if (quest.state() === "in_progress" && quest.stage() === 0) {
      if (quest.advance(0).changed()) {
        sayNpc(player, CHRONOZON_NPC, "Slay a green dragon and take its dragon bones.");
        endDialogue(player);
      }
      return;
    }
    if (quest.state() === "in_progress" && quest.stage() === 4) {
      const completion = quest.complete(4);
      if (completion.changed()) {
        player.state(DRAGON_AWAKENS_ID).setString("ending", "dragon-rite-sealed");
        sayNpc(player, CHRONOZON_NPC, "The rite is complete. Take your reward.");
        endDialogue(player);
        return;
      }
      const reason = completion.code();
      const retryMessage = reason === "inventory_full"
        ? "Make room in your inventory, then ask me again."
        : reason === "xp_cap"
          ? "Your Magic experience cannot accept this reward."
          : reason === "quest_points_overflow"
            ? "Your quest points cannot accept this reward."
            : "The reward failed. Ask me again when you are ready.";
      sayNpc(player, CHRONOZON_NPC, retryMessage);
      endDialogue(player);
      return;
    }
    sayNpc(
      player,
      CHRONOZON_NPC,
      quest.state() === "completed"
        ? "The dragon rite remains sealed."
        : "Continue the rite and return when it is done.",
    );
    endDialogue(player);
  });

  onItemPickup(DRAGON_BONES_ITEM, ({ player }) => {
    const quest = player.quest(DRAGON_AWAKENS_ID);
    if (quest !== null && quest.state() === "in_progress" && quest.stage() === 1) {
      if (quest.advance(1).changed()) {
        player.state(DRAGON_AWAKENS_ID).setBoolean("bones-recovered", true);
        player.message("Use the dragon bones on an altar.");
      }
    }
  });

  onItemOnObject(DRAGON_BONES_ITEM, DRAGON_ALTAR_OBJECT, ({ player }) => {
    const quest = player.quest(DRAGON_AWAKENS_ID);
    if (quest === null || quest.state() !== "in_progress" || quest.stage() !== 2) return;
    if (!player.getInventory().remove(DRAGON_BONES_ITEM, 1)) return;
    if (quest.advance(2).changed()) {
      player.state(DRAGON_AWAKENS_ID).setBoolean("altar-sealed", true);
      player.message("The rite awakens. Defeat another green dragon.");
    }
  });

  onNpcDeath(DRAGON_GUARDIAN_NPC, ({ killer }) => {
    if (killer === null) return;
    const quest = killer.quest(DRAGON_AWAKENS_ID);
    if (quest === null || quest.state() !== "in_progress" || quest.stage() !== 3) return;
    if (quest.advance(3).changed()) {
      killer.message("The rite is sealed. Return to Chronozon for your reward.");
    }
  });

  onLogin(({ player }) => {
    const quest = player.quest(DRAGON_AWAKENS_ID);
    if (quest !== null && quest.state() === "in_progress") {
      const objective = quest.objective();
      if (objective !== null) {
        player.message(`Dragon Awakens: ${objective}`);
      }
    }
  });
});
