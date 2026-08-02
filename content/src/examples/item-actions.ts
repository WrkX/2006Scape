/**
 * Representative exact item routes using reserved demonstration IDs.
 *
 * These handlers exercise the runtime contract without replacing ordinary
 * legacy content.
 */

const DEMO_ITEM = 14990;
const DEMO_TARGET_ITEM = 14991;
const DEMO_OBJECT = 14992;
const DEMO_NPC = 14993;

onItem(DEMO_ITEM, "first", ({ player, item, slot }) => {
  player.message(`First-clicked ${item.getName()} in slot ${slot}.`);
});

onItem(DEMO_ITEM, "second", ({ player }) => {
  player.animate(829);
});

onItem(DEMO_ITEM, "third", ({ player }) => {
  player.message(`You have ${player.getInventory().getFreeSlots()} free slots.`);
});

onItemOnItem(DEMO_ITEM, DEMO_TARGET_ITEM, ({
  player,
  usedItem,
  usedSlot,
  targetItem,
  targetSlot,
}) => {
  player.message(
    `Used ${usedItem.getId()} (${usedSlot}) on ` +
      `${targetItem.getId()} (${targetSlot}).`,
  );
});

onItemOnObject(DEMO_ITEM, DEMO_OBJECT, ({ player, target, slot }) => {
  player.message(`Used slot ${slot} on object ${target.getId()}.`);
});

onItemOnNpc(DEMO_ITEM, DEMO_NPC, ({ player, target, slot }) => {
  player.message(`Used slot ${slot} on NPC ${target.getId()}.`);
});

export {};
