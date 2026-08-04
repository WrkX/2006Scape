import type { NpcScriptContext } from "../sdk/index.js";
import {
  sayNpc,
  sayPlayer,
  sayOptions,
  endDialogue,
} from "../sdk/index.js";

// Both Man variants spawn in Lumbridge — handle both
const MAN_IDS = [1, 3] as const;

function handleManDialogue({ player }: NpcScriptContext): void {
  sayNpc(player, 1, "Hello, traveler. Lovely day, isn't it?");
  sayPlayer(player, "Yes, it is.");
  sayNpc(player, 1, "Be careful out there.");
  sayOptions(player, ["Tell me about Lumbridge.", "Goodbye."], (choice) => {
    switch (choice) {
      case 0:
        sayPlayer(player, "Tell me about Lumbridge.");
        sayNpc(
          player,
          1,
          "It's a small town to the south. The castle is to the north.",
        );
        endDialogue(player);
        break;
      case 1:
        sayPlayer(player, "Goodbye.");
        endDialogue(player);
        break;
    }
  });
}

for (const id of MAN_IDS) {
  onNpc(id, "first", handleManDialogue);
}

export {};
