/**
 * Temple of Zaros raid definition.
 *
 * A 4-room instanced raid deep beneath Dragon Island.  Players fight
 * through guardian chambers, solve an ancient puzzle, defeat a crypt
 * boss, and finally face Zaros himself.
 *
 * Minimum 2 players, maximum 5.  Time limit: 7200 ticks (~2 hours).
 * Rewards are drawn from the "zaros_raid_loot" table.
 *
 * @module raids/temple-of-zaros/raid
 */

import { createRaid, createRaidRoom, createBossRoom } from "../../raids/raid-builder.js";
import { createDropTable } from "../../core/drop-tables.js";
import type { RoomContext, RoomResult } from "../../raids/types.js";
import type { BossContext } from "../../core/boss.js";

// ─── Room 1: Guardian Chamber ────────────────────────────────────────────────

const guardianRoom = createRaidRoom({
  id: "guardian",
  name: "Hall of Guardians",
  onEnter(ctx: RoomContext): void {
    ctx.announce("Ancient guardians stir from their slumber...");
  },
  onTick(ctx: RoomContext): RoomResult {
    // Room completes when no players are in combat.
    const allClear = ctx.players.every((p) => !p.inCombat);
    return allClear ? { status: "completed" } : { status: "in_progress" };
  },
  onComplete(ctx: RoomContext): void {
    ctx.announce("The guardians crumble to dust. The path opens forward.");
  },
});

// ─── Room 2: Puzzle ──────────────────────────────────────────────────────────

const puzzleRoom = createRaidRoom({
  id: "puzzle",
  name: "Chamber of Riddles",
  onEnter(ctx: RoomContext): void {
    ctx.announce("Ancient Zarosian runes glow on the walls...");
    ctx.announce("Solve the puzzle to unlock the gate ahead.");
  },
  onTick(_ctx: RoomContext): RoomResult {
    // Puzzle completion is tracked by the engine when players interact
    // with the rune objects.  For now, the room remains in progress
    // until the puzzle state is resolved externally.
    return { status: "in_progress" };
  },
  onComplete(ctx: RoomContext): void {
    ctx.announce("The runes align and the gate groans open.");
  },
});

// ─── Room 3: Crypt (Boss) ────────────────────────────────────────────────────

const cryptBossRoom = createBossRoom({
  npcId: 7001,
  combatLevel: 400,
  maxHitpoints: 800,
  onTick(ctx: BossContext): void {
    if (ctx.hpPercent < 0.3) {
      ctx.useSpecial("shadow_blast");
      ctx.spawnMinions(7002, 2);
    }
  },
  onDeath(ctx: BossContext): void {
    ctx.say("Zaros... will... return...");
  },
});

const cryptRoom = createRaidRoom({
  id: "crypt",
  name: "Crypt of the Fallen",
  onEnter(ctx: RoomContext): void {
    ctx.announce("A fallen Zarosian priest rises from its sarcophagus!");
  },
  onTick(_ctx: RoomContext): RoomResult {
    return { status: "in_progress" };
  },
  onComplete(ctx: RoomContext): void {
    ctx.announce("The crypt falls silent. Only the final chamber remains.");
  },
  boss: cryptBossRoom,
});

// ─── Room 4: Zaros (Final Boss) ──────────────────────────────────────────────

const zarosBossRoom = createBossRoom({
  npcId: 7003,
  combatLevel: 600,
  maxHitpoints: 1500,
  onTick(ctx: BossContext): void {
    if (ctx.hpPercent < 0.5) {
      ctx.useSpecial("doom_curse");
    }
    if (ctx.hpPercent < 0.25) {
      ctx.useSpecial("void_strike");
    }
  },
  onDeath(ctx: BossContext): void {
    ctx.say("No! The cycle... cannot end...");
  },
});

const zarosRoom = createRaidRoom({
  id: "zaros",
  name: "Throne of Zaros",
  onEnter(ctx: RoomContext): void {
    ctx.announce("Zaros turns his gaze upon you. 'You dare trespass in my sanctum?'");
  },
  onTick(_ctx: RoomContext): RoomResult {
    return { status: "in_progress" };
  },
  onComplete(ctx: RoomContext): void {
    ctx.announce("The temple begins to collapse! Grab your rewards and escape!");
  },
  boss: zarosBossRoom,
});

// ─── Raid Definition ─────────────────────────────────────────────────────────

const templeOfZarosRaid = createRaid("temple_of_zaros", {
  entrance: { x: 7100, y: 7100, plane: 0 },
  minPlayers: 2,
  maxPlayers: 5,
  rooms: [guardianRoom, puzzleRoom, cryptRoom, zarosRoom],
  rewardTable: "zaros_raid_loot",
  timeLimitTicks: 7200,

  onStart(ctx: RoomContext): void {
    ctx.announce("The temple doors seal behind you. There is no turning back.");
  },

  onComplete(ctx: RoomContext): void {
    ctx.announce("The Temple of Zaros has been cleared!");
    ctx.announce("Ancient Zarosian treasures await each of you.");
  },

  onWipe(ctx: RoomContext, reason: string): void {
    ctx.announce(`The raid has failed: ${reason}`);
    ctx.announce("All players are teleported outside the temple.");
  },
});

defineRaid(templeOfZarosRaid);

/**
 * Canonical named reward table referenced by the raid definition's
 * {@code rewardTable}. Phase 5 WP5 consumes this record with the raid
 * runtime; the registration proves the reference is not silent data loss.
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
