/**
 * Encounter Warden — Phase 4 production boss proof.
 *
 * An imperative King Black Dragon encounter authored exclusively through the
 * public Phase 4 bridge surface: command entry, exclusive arena reservation,
 * exact entry/phase locks and camera sessions, a layered collision barrier,
 * phased skeleton adds, scheduled projectiles, and a transactional drop roll
 * on owned death.
 *
 * @module bosses/encounter-warden
 */

import type { CommandScriptContext, ScriptEncounterHandle } from "../core/runtime.js";

const ARENA = { minX: 2264, minY: 4688, maxX: 2287, maxY: 4711, plane: 1 };
const OWNER_X = 2271;
const OWNER_Y = 4696;

/** King Black Dragon, pinned to the production npc.json definition. */
const BOSS = { id: 50, x: 2271, y: 4698, hp: 240, maxHit: 30, attack: 350, defence: 350 };
const SKELETON = { id: 90, hp: 29, maxHit: 8, attack: 100, defence: 100 };

const DROP_TABLE = [
  { itemId: 536, minAmount: 1, maxAmount: 1, weight: 0, always: true },
  { itemId: 995, minAmount: 500, maxAmount: 500, weight: 100, always: false },
] as const;

/** Handles per owner so the explicit-close command can reach the encounter. */
const active = new Map<string, ScriptEncounterHandle>();

onCommand("encounter-warden", (ctx: CommandScriptContext) => {
  const player = ctx.player;
  // Reserve the arena rectangle first; only then move the owner into it.
  const encounter = player.beginEncounter(
    "encounter-warden",
    ARENA.minX,
    ARENA.minY,
    ARENA.maxX,
    ARENA.maxY,
    ARENA.plane,
  );
  if (encounter === null) {
    player.message("The warden's arena is busy.");
    return;
  }
  player.teleport(OWNER_X, OWNER_Y, ARENA.plane);
  active.set(player.getUsername(), encounter);

  // Entry tick 0: 4-tick movement/action locks and one 6-tick camera.
  player.getMovement().lock(4);
  player.getActions().lock(4);
  const entryCamera = player.getPresentation().beginCamera(6);
  if (entryCamera !== null) {
    entryCamera.position(55, 46, 800, 5, 0);
    entryCamera.lookAt(55, 50, 2400, 5, 0);
    entryCamera.shake(2, 2, 2, 2);
  }

  // The layered barrier: an empty tile replaced by solid object 2213.
  encounter.replaceObject(2275, 4698, ARENA.plane, -1, -1, -1, 2213, 10, 0);

  const boss = encounter.spawnNpc(
    BOSS.id,
    BOSS.x,
    BOSS.y,
    ARENA.plane,
    BOSS.hp,
    BOSS.maxHit,
    BOSS.attack,
    BOSS.defence,
  );
  if (boss === null) {
    encounter.close();
    active.delete(player.getUsername());
    player.message("The warden failed to summon his guardian.");
    return;
  }

  let phased = false;

  // Threshold poll: phase once, at HP <= 120.
  encounter.every(1, () => {
    if (phased || !boss.isAlive() || boss.hp() > 120) {
      return;
    }
    phased = true;
    // Release the still-active entry camera before starting the phase one.
    if (entryCamera !== null && entryCamera.isActive()) {
      entryCamera.release();
    }
    player.getMovement().lock(2);
    player.getActions().lock(2);
    const phaseCamera = player.getPresentation().beginCamera(4);
    if (phaseCamera !== null) {
      phaseCamera.position(55, 48, 900, 8, 0);
      phaseCamera.lookAt(55, 50, 1800, 8, 0);
      phaseCamera.shake(2, 3, 2, 2);
    }
    boss.animate(1590, 0);
    boss.graphic(246, "high");
    encounter.spawnNpc(SKELETON.id, 2269, 4698, ARENA.plane,
      SKELETON.hp, SKELETON.maxHit, SKELETON.attack, SKELETON.defence);
    encounter.spawnNpc(SKELETON.id, 2273, 4698, ARENA.plane,
      SKELETON.hp, SKELETON.maxHit, SKELETON.attack, SKELETON.defence);
    player.message("The King Black Dragon's rage erupts!");

    // Every 4 ticks after the phase: a dragonfire projectile then 5 damage.
    encounter.every(4, () => {
      if (boss.isAlive() && player.getPlane() === ARENA.plane) {
        player.getPresentation().projectile(
          393,
          boss.position().x,
          boss.position().y,
          player.getX(),
          player.getY(),
          player.getPlane(),
          0,
          30,
          50,
          50,
          0,
          "self",
        );
        player.getCombat().damage(5);
      }
    });
  });

  // Owned death: roll the exact table, verify both results, then close.
  encounter.onNpcDeath(boss, (death) => {
    const results = death.encounter.rollDrops(
      death.encounter.owner(),
      death.position.x,
      death.position.y,
      death.position.plane,
      200,
      DROP_TABLE,
    );
    if (results.length() === 2) {
      const bones = results.get(0);
      const coins = results.get(1);
      if (
        bones !== null &&
        coins !== null &&
        bones.itemId() === 536 &&
        bones.amount() === 1 &&
        coins.itemId() === 995 &&
        coins.amount() === 500
      ) {
        death.encounter.close();
        return;
      }
    }
    death.encounter.close();
  });
});

onCommand("encounter-warden-close", (ctx: CommandScriptContext) => {
  const encounter = active.get(ctx.player.getUsername());
  if (encounter !== undefined && encounter.isOpen() && encounter.close()) {
    active.delete(ctx.player.getUsername());
    ctx.player.message("The warden's arena has been sealed.");
  } else {
    ctx.player.message("No warden encounter is active.");
  }
});
