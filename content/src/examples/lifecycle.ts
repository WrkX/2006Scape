/**
 * Small Phase 2 examples. IDs are deliberately outside normal content ranges
 * so these demonstrate the API without taking over existing gameplay.
 */

const bridgeExampleArea = {
  id: "bridge-example-lumbridge-courtyard",
  minX: 3218,
  minY: 3218,
  maxX: 3225,
  maxY: 3225,
  plane: 0,
} as const;

onNpcDeath(14_994, (ctx) => {
  ctx.killer?.message(`Observed death of ${ctx.npc.getName()}.`);
});

onItemPickup(14_995, (ctx) => {
  ctx.player.message(`Picked up ${ctx.amount} ${ctx.item.getName()}.`);
});

onEnterArea(bridgeExampleArea, (ctx) => {
  ctx.player.message(`Entered ${ctx.area.getId()}.`);
  ctx.player.after(2, () => {
    ctx.player.message("This message ran two game ticks later.");
  });
});

onLeaveArea(bridgeExampleArea, (ctx) => {
  ctx.player.message(`Left ${ctx.area.getId()}.`);
});

export {};
