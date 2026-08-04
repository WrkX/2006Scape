/**
 * Temple of Zaros raid definition.
 *
 * A 2-room raid beneath Crandor on plane 1: a guardian hall that clears
 * after a few ticks and a crypt whose boss fight embeds the canonical
 * {@code dragon-king} declarative boss (loaded Black dragon 54) by stable id
 * reference, borrowing the raid's sole encounter handle. Minimum 2 players,
 * maximum 5. Time limit: 7200 ticks (~2 hours).
 *
 * Completion commits the {@code zaros_raid_reward} roster-wide and
 * atomically, then rolls the {@code zaros_raid_loot} table once as private
 * ground deliveries through the raid-session RNG owner.
 *
 * The legacy fixture's custom NPC ids 7001/7002/7003 have no npc.json
 * definitions; WP5 migrated the raid to the loaded dragon-king boss and the
 * canonical schema-v1 command/lobby/muster/room contract.
 *
 * @module raids/temple-of-zaros/raid
 */

import { createRaid, createRaidRoom, createBossRoom } from "../../raids/raid-builder.js";
import { createDropTable } from "../../core/drop-tables.js";
import type { RaidRoomContext, RoomResult } from "../../raids/types.js";

// ─── Room 1: Guardian Chamber ────────────────────────────────────────────────

const guardianRoom = createRaidRoom({
  id: "guardian",
  name: "Hall of Guardians",
  bounds: { minX: 2264, minY: 4688, maxX: 2275, maxY: 4695, plane: 1 },
  onEnter(ctx: RaidRoomContext): void {
    ctx.announce("Ancient guardians stir from their slumber...");
  },
  onTick(ctx: RaidRoomContext): RoomResult {
    return ctx.elapsedTicks() >= 3
      ? { status: "completed" }
      : { status: "in_progress" };
  },
  onComplete(ctx: RaidRoomContext): void {
    ctx.announce("The guardians crumble to dust. The path opens forward.");
  },
});

// ─── Room 2: Crypt (Boss) ─────────────────────────────────────────────────────

const cryptRoom = createRaidRoom({
  id: "crypt",
  name: "Crypt of the Fallen",
  bounds: { minX: 2264, minY: 4696, maxX: 2287, maxY: 4711, plane: 1 },
  onEnter(ctx: RaidRoomContext): void {
    ctx.announce("A fallen Zarosian priest rises from its sarcophagus!");
  },
  onTick(_ctx: RaidRoomContext): RoomResult {
    // Boss rooms complete only through the embedded boss controller.
    return { status: "in_progress" };
  },
  onComplete(ctx: RaidRoomContext): void {
    ctx.announce("The crypt falls silent. Only the reward barrier remains.");
  },
  boss: createBossRoom({ bossId: "dragon-king" }),
});

// ─── Raid Definition ─────────────────────────────────────────────────────────

/**
 * Canonical named reward committed roster-wide and atomically at completion
 * by the WP5 raid runtime. Every legacy loot-table item id is preserved so
 * the migration loses no silent reward.
 */
defineReward({
  id: "zaros_raid_reward",
  items: [
    { id: 995, amount: 50000 },
    { id: 1149, amount: 1 },
    { id: 1127, amount: 1 },
    { id: 1305, amount: 1 },
    { id: 1215, amount: 1 },
    { id: 1079, amount: 1 },
  ],
  experience: [],
  questPoints: 0,
  state: [],
});

/**
 * Canonical named reward table rolled once after the roster commit through
 * the raid-session RNG owner as private ground deliveries.
 */
createDropTable({
  id: "zaros_raid_loot",
  entries: [
    { itemId: 995, minAmount: 50000, maxAmount: 200000, weight: 0, always: true },
    { itemId: 1149, minAmount: 1, maxAmount: 1, weight: 128, always: false },
    { itemId: 1127, minAmount: 1, maxAmount: 1, weight: 64, always: false },
    { itemId: 1305, minAmount: 1, maxAmount: 1, weight: 32, always: false },
    { itemId: 1215, minAmount: 1, maxAmount: 1, weight: 32, always: false },
    { itemId: 1079, minAmount: 1, maxAmount: 1, weight: 16, always: false },
  ],
});

const templeOfZarosRaid = createRaid("temple_of_zaros", {
  command: "temple-of-zaros",
  bounds: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4711, plane: 1 },
  muster: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4695 },
  entrance: { x: 2268, y: 4690, plane: 1 },
  minPlayers: 2,
  maxPlayers: 5,
  timeLimitTicks: 7200,
  rooms: [guardianRoom, cryptRoom],
  rewards: ["zaros_raid_reward"],
  rewardTable: "zaros_raid_loot",
  privateTicks: 200,

  onStart(ctx: RaidRoomContext): void {
    ctx.announce("The temple doors seal behind you. There is no turning back.");
  },

  onComplete(ctx: RaidRoomContext): void {
    ctx.announce("The Temple of Zaros has been cleared!");
    ctx.announce("Ancient Zarosian treasures await each of you.");
  },

  onWipe(ctx: RaidRoomContext, reason: string): void {
    ctx.announce(`The raid has failed: ${reason}`);
    ctx.announce("The temple seals close and the raid instance is disbanded.");
  },
});

defineRaid(templeOfZarosRaid);
