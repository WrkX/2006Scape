const MAN_NPC_ID = 1;

onNpc(MAN_NPC_ID, "first", ({ player }) => {
  player.dialogue.npc(MAN_NPC_ID, "Hello, traveler. Lovely day, isn't it?");
  player.dialogue.player("Yes, it is.");
  player.dialogue.npc(MAN_NPC_ID, "Be careful out there.");
  player.dialogue.options(
    ["Tell me about Lumbridge.", "Goodbye."],
    (choice) => {
      if (choice === 0) {
        player.dialogue.player("Tell me about Lumbridge.");
        player.dialogue.npc(
          MAN_NPC_ID,
          "It's a small town to the south. The castle is to the north.",
        );
        player.dialogue.end();
      } else if (choice === 1) {
        player.dialogue.player("Goodbye.");
        player.dialogue.end();
      }
    },
  );
});

export {};
