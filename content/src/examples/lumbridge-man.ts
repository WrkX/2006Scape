import type { NpcScriptContext } from "../core/runtime.js";

// Both Man variants spawn in Lumbridge — handle both
const MAN_IDS = [1, 3] as const;

function handleManDialogue({ player }: NpcScriptContext): void {
  const dialogue = player.getDialogue();

  dialogue.npc(1, "Hello, traveler. Lovely day, isn't it?");
  dialogue.player("Yes, it is.");
  dialogue.npc(1, "Be careful out there.");
  dialogue.options(
    ["Tell me about Lumbridge.", "Goodbye."],
    (choice: number) => {
      switch (choice) {
        case 0:
          dialogue.player("Tell me about Lumbridge.");
          dialogue.npc(
            1,
            "It's a small town to the south. The castle is to the north.",
          );
          dialogue.end();
          break;
        case 1:
          dialogue.player("Goodbye.");
          dialogue.end();
          break;
      }
    },
  );
}

for (const id of MAN_IDS) {
  onNpc(id, "first", handleManDialogue);
}

export {};
