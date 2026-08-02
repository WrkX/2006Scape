/**
 * Encounter Warden — declarative production boss proof.
 *
 * WP3 migration of the Phase 4 imperative fixture: the same King Black
 * Dragon encounter (command entry, exclusive arena reservation, exact
 * entry/phase locks and camera sessions, layered collision barrier, phased
 * skeleton adds, scheduled dragonfire projectiles, and transactional named
 * drops on owned death) authored exclusively through the canonical
 * schema-v1 {@code defineBoss} descriptor and the narrow boss runtime
 * context. The imperative encounter state machine is gone; the Java
 * {@code BossController} drives spawn, phases, special cadence, named
 * drops, death, and cleanup.
 *
 * @module bosses/encounter-warden
 */

import { createDropTable } from "../core/drop-tables.js";
import type { BossRuntimeContext } from "../core/boss.js";

const ARENA_PLANE = 1;

/**
 * Canonical named drop table rolled by the controller on owned death:
 * dragon bones always, coins (500) at weight 100, private TTL 200.
 */
createDropTable({
  id: "encounter_warden_loot",
  entries: [
    { itemId: 536, minAmount: 1, maxAmount: 1, weight: 0, always: true },
    { itemId: 995, minAmount: 500, maxAmount: 500, weight: 100, always: false },
  ],
});

defineBoss({
  id: "encounter-warden",
  npcId: 50,
  name: "King Black Dragon",
  combatLevel: 276,
  maxHitpoints: 240,
  maxHit: 30,
  attack: 350,
  defence: 350,
  arena: { minX: 2264, minY: 4688, maxX: 2287, maxY: 4711, plane: 1 },
  spawn: { x: 2271, y: 4698 },
  command: "encounter-warden",
  closeCommand: "encounter-warden-close",
  entryTeleport: { x: 2271, y: 4696 },
  dropTable: "encounter_warden_loot",
  privateTicks: 200,

  // Entry tick 0: 4-tick movement/action locks, one 6-tick camera, and the
  // layered barrier (an empty tile replaced by solid object 2213).
  onSpawn(ctx: BossRuntimeContext): void {
    ctx.owner.getMovement().lock(4);
    ctx.owner.getActions().lock(4);
    const entryCamera = ctx.owner.getPresentation().beginCamera(6);
    if (entryCamera !== null) {
      entryCamera.position(55, 46, 800, 5, 0);
      entryCamera.lookAt(55, 50, 2400, 5, 0);
      entryCamera.shake(2, 2, 2, 2);
    }
    ctx.encounter.replaceObject(2275, 4698, ARENA_PLANE, -1, -1, -1, 2213, 10, 0);
  },

  // Threshold poll: phase once at HP <= 120, then arm the repeating
  // dragonfire special (first projectile 4 ticks later, then every 4 ticks).
  phases: [
    {
      name: "Warden Rage",
      hpPercentThreshold: 50,
      onEnter(ctx: BossRuntimeContext): void {
        ctx.owner.getPresentation().resetCamera();
        ctx.owner.getMovement().lock(2);
        ctx.owner.getActions().lock(2);
        const phaseCamera = ctx.owner.getPresentation().beginCamera(4);
        if (phaseCamera !== null) {
          phaseCamera.position(55, 48, 900, 8, 0);
          phaseCamera.lookAt(55, 50, 1800, 8, 0);
          phaseCamera.shake(2, 3, 2, 2);
        }
        ctx.boss.animate(1590, 0);
        ctx.boss.graphic(246, "high");
        ctx.encounter.spawnNpc(90, 2269, 4698, ARENA_PLANE, 29, 8, 100, 100);
        ctx.encounter.spawnNpc(90, 2273, 4698, ARENA_PLANE, 29, 8, 100, 100);
        ctx.owner.message("The King Black Dragon's rage erupts!");
        ctx.useSpecial("dragonfire");
      },
    },
  ],

  specials: {
    dragonfire: {
      cooldownTicks: 4,
      handler(ctx: BossRuntimeContext): void {
        const position = ctx.boss.position();
        ctx.owner.getPresentation().projectile(
          393,
          position.x,
          position.y,
          ctx.owner.getX(),
          ctx.owner.getY(),
          ctx.owner.getPlane(),
          0,
          30,
          50,
          50,
          0,
          "self",
        );
        ctx.owner.getCombat().damage(5);
      },
    },
  },
});
