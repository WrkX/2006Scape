/**
 * Boss builder — validated factory for the canonical schema-v1
 * {@link BossDefinition} and ergonomic wrapper around the global
 * `defineBoss()` bridge function.
 *
 * The helpers mirror the strict Java parser so content authors get clear
 * load-time error messages instead of obscure engine failures.
 *
 * @module bosses/boss-builder
 *
 * @example
 * ```ts
 * import { createBoss } from "./boss-builder.js";
 *
 * defineBoss(createBoss({
 *   id: "dragon-king",
 *   npcId: 54,
 *   name: "Dragon King",
 *   combatLevel: 450,
 *   maxHitpoints: 600,
 *   maxHit: 40,
 *   attack: 300,
 *   defence: 300,
 *   arena: { minX: 3200, minY: 3200, maxX: 3210, maxY: 3210, plane: 0 },
 *   spawn: { x: 3205, y: 3205 },
 *   command: "dragon-king",
 *   dropTable: "dragon_king_loot",
 *   privateTicks: 200,
 *   onSpawn(ctx) {
 *     ctx.say("You dare enter my domain? You will burn!");
 *   },
 *   phases: [
 *     {
 *       name: "Enrage",
 *       hpPercentThreshold: 50,
 *       onEnter(ctx) {
 *         ctx.say("Now you will feel true dragon fire!");
 *         ctx.useSpecial("fire_wave");
 *       },
 *     },
 *   ],
 *   specials: {
 *     fire_wave: {
 *       cooldownTicks: 12,
 *       handler(ctx) {
 *         ctx.owner.message("The Dragon King's fire wave engulfs you!");
 *       },
 *     },
 *   },
 * }));
 * ```
 */

import type {
  BossArena,
  BossDefinition,
  BossPhase,
  BossSpecials,
} from "../core/boss.js";

// ─── Internal helpers ─────────────────────────────────────────────────────────

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[boss-builder] ${message}`);
  }
}

const ID_PATTERN = /^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$/;
const COMMAND_PATTERN = /^[a-z0-9][a-z0-9._-]{0,63}$/;
const SPECIAL_NAME_PATTERN = /^[a-z][a-z0-9_-]*$/;

function validateArena(arena: BossArena, bossName: string): void {
  const label = "arena";
  assert(
    typeof arena.minX === "number" && typeof arena.minY === "number" &&
      typeof arena.maxX === "number" && typeof arena.maxY === "number" &&
      typeof arena.plane === "number",
    `${bossName}: ${label} must declare minX, minY, maxX, maxY, and plane`,
  );
  for (const member of ["minX", "minY", "maxX", "maxY"] as const) {
    assert(Number.isInteger(arena[member]) && arena[member] >= 0 &&
      arena[member] <= 16383,
    `${bossName}: ${label}.${member} must be an integer 0..16383`);
  }
  assert(Number.isInteger(arena.plane) && arena.plane >= 0 &&
    arena.plane <= 3,
  `${bossName}: ${label}.plane must be an integer 0..3`);
  assert(arena.minX <= arena.maxX && arena.minY <= arena.maxY,
    `${bossName}: ${label} bounds are inverted`);
  assert(arena.maxX - arena.minX + 1 <= 64 &&
    arena.maxY - arena.minY + 1 <= 64,
  `${bossName}: ${label} sides must be 1..64 tiles`);
}

function validateSpawn(
  spawn: { readonly x: number; readonly y: number },
  arena: BossArena,
  bossName: string,
): void {
  assert(Number.isInteger(spawn.x) && Number.isInteger(spawn.y),
    `${bossName}: spawn must declare integer x and y`);
  assert(spawn.x >= arena.minX && spawn.x <= arena.maxX &&
    spawn.y >= arena.minY && spawn.y <= arena.maxY,
  `${bossName}: spawn (${spawn.x}, ${spawn.y}) must lie inside the ` +
    `declared arena`);
}

function validatePhases(
  phases: readonly BossPhase[] | undefined,
  bossName: string,
): void {
  if (!phases) return;

  assert(phases.length >= 1 && phases.length <= 8,
    `${bossName}: phases must contain 1..8 phases`);

  let previousThreshold = 101;
  const names = new Set<string>();
  for (let i = 0; i < phases.length; i++) {
    const phase = phases[i];
    const label = `phases[${i}] "${phase.name}"`;
    assert(typeof phase.name === "string" && phase.name.length > 0 &&
      phase.name.length <= 64,
    `${bossName}: ${label} must have a name of 1..64 characters`);
    assert(typeof phase.hpPercentThreshold === "number" &&
      Number.isInteger(phase.hpPercentThreshold) &&
      phase.hpPercentThreshold >= 0 && phase.hpPercentThreshold <= 100,
    `${bossName}: ${label} hpPercentThreshold must be an integer 0..100`);
    assert(phase.hpPercentThreshold < previousThreshold,
      `${bossName}: phases must be in strictly descending ` +
        `hpPercentThreshold order (phase "${phase.name}" at ` +
        `${phase.hpPercentThreshold}% follows ${previousThreshold}%)`);
    assert(typeof phase.onEnter === "function",
      `${bossName}: ${label} must have an onEnter handler`);
    assert(!names.has(phase.name),
      `${bossName}: duplicate phase name "${phase.name}"`);
    names.add(phase.name);
    previousThreshold = phase.hpPercentThreshold;
  }
}

function validateSpecials(
  specials: BossSpecials | undefined,
  bossName: string,
): void {
  if (!specials) return;

  const entries = Object.entries(specials);
  assert(entries.length >= 1 && entries.length <= 16,
    `${bossName}: specials must contain 1..16 named specials`);
  for (const [name, spec] of entries) {
    assert(SPECIAL_NAME_PATTERN.test(name),
      `${bossName}: invalid special name "${name}" ` +
        "(lower-case identifier expected)");
    assert(Number.isInteger(spec.cooldownTicks) && spec.cooldownTicks >= 1 &&
      spec.cooldownTicks <= 100000,
    `${bossName}: special "${name}" cooldownTicks must be an integer ` +
      "1..100000");
    assert(typeof spec.handler === "function",
      `${bossName}: special "${name}" must have a handler function`);
  }
}

// ─── Public API ───────────────────────────────────────────────────────────────

/**
 * Options accepted by {@link createBoss}.
 *
 * Identical to the canonical {@link BossDefinition}; `cleanupPolicy`
 * defaults to `"close-on-terminal"`.
 */
export type BossOptions = BossDefinition;

/**
 * Create a validated canonical {@link BossDefinition}.
 *
 * Performs the following checks:
 * - `id` is a bounded identifier and `npcId` a positive integer.
 * - All stats are positive bounded integers.
 * - The arena is a bounded non-inverted rectangle of 1..64-tile sides on
 *   plane 0..3 and the spawn lies inside it.
 * - Exactly one entry source (`command` or `objectEntry`) is declared, and
 *   commands match the canonical lower-case command pattern.
 * - Phases are strictly descending with unique names; specials have bounded
 *   cooldowns and handlers.
 * - `dropTable` and `privateTicks` are declared together.
 *
 * @param options  Raw boss configuration.
 * @returns A frozen, validated {@link BossDefinition}.
 */
export function createBoss(options: BossOptions): BossDefinition {
  const name = options.name ?? `Boss(npcId=${options.npcId})`;

  assert(typeof options.id === "string" && ID_PATTERN.test(options.id),
    `${name}: id must be at most 64 characters of letters, digits, '.', ` +
      "'_', or '-'");
  assert(Number.isInteger(options.npcId) && options.npcId >= 0 &&
    options.npcId <= 14999,
  `${name}: npcId must be an integer 0..14999, got ${options.npcId}`);
  for (const member of ["combatLevel", "maxHitpoints"] as const) {
    assert(Number.isInteger(options[member]) && options[member] >= 1 &&
      options[member] <= 32767,
    `${name}: ${member} must be an integer 1..32767`);
  }
  for (const member of ["maxHit", "attack", "defence"] as const) {
    assert(Number.isInteger(options[member]) && options[member] >= 0 &&
      options[member] <= 32767,
    `${name}: ${member} must be an integer 0..32767`);
  }
  validateArena(options.arena, name);
  validateSpawn(options.spawn, options.arena, name);

  const hasCommand = options.command !== undefined;
  const hasObjectEntry = options.objectEntry !== undefined;
  assert(hasCommand !== hasObjectEntry,
    `${name}: exactly one of command or objectEntry must be declared so ` +
      "the boss has a production entry route");
  if (hasCommand) {
    assert(typeof options.command === "string" &&
      COMMAND_PATTERN.test(options.command),
    `${name}: command must be a lower-case command name of at most 64 ` +
      "characters");
  }
  if (options.closeCommand !== undefined) {
    assert(typeof options.closeCommand === "string" &&
      COMMAND_PATTERN.test(options.closeCommand),
    `${name}: closeCommand must be a lower-case command name of at most 64 ` +
      "characters");
  }
  if (hasObjectEntry) {
    const entry = options.objectEntry!;
    assert(Number.isInteger(entry.objectId) && entry.objectId >= 0 &&
      entry.objectId <= 65535,
    `${name}: objectEntry.objectId must be an integer 0..65535`);
    assert(["first", "second", "third", "fourth"].includes(entry.action),
      `${name}: objectEntry.action must be first, second, third, or fourth`);
  }
  if (options.entryTeleport !== undefined) {
    assert(Number.isInteger(options.entryTeleport.x) &&
      Number.isInteger(options.entryTeleport.y) &&
      options.entryTeleport.x >= 0 && options.entryTeleport.x <= 16383 &&
      options.entryTeleport.y >= 0 && options.entryTeleport.y <= 16383,
    `${name}: entryTeleport must declare integer x and y in 0..16383`);
    assert(options.entryTeleport.x >= options.arena.minX &&
      options.entryTeleport.x <= options.arena.maxX &&
      options.entryTeleport.y >= options.arena.minY &&
      options.entryTeleport.y <= options.arena.maxY,
    `${name}: entryTeleport must lie inside the declared arena: the ` +
      "owner is a participant and cannot be relocated outside the " +
      "reservation");
  }

  assert(typeof options.onSpawn === "function",
    `${name}: onSpawn must be a function`);
  if (options.onTick !== undefined) {
    assert(typeof options.onTick === "function",
      `${name}: onTick must be a function when present`);
  }
  if (options.onDeath !== undefined) {
    assert(typeof options.onDeath === "function",
      `${name}: onDeath must be a function when present`);
  }

  validatePhases(options.phases, name);
  validateSpecials(options.specials, name);

  const hasDropTable = options.dropTable !== undefined;
  const hasPrivateTicks = options.privateTicks !== undefined;
  assert(hasDropTable === hasPrivateTicks,
    `${name}: dropTable and privateTicks must be declared together`);
  if (hasDropTable) {
    assert(typeof options.dropTable === "string" &&
      ID_PATTERN.test(options.dropTable!),
    `${name}: dropTable must be a valid named table id`);
    assert(Number.isInteger(options.privateTicks) &&
      options.privateTicks! >= 1 && options.privateTicks! <= 1000,
    `${name}: privateTicks must be an integer 1..1000`);
  }
  if (options.cleanupPolicy !== undefined) {
    assert(options.cleanupPolicy === "close-on-terminal",
      `${name}: cleanupPolicy must be "close-on-terminal"`);
  }

  const definition: BossDefinition = {
    id: options.id,
    npcId: options.npcId,
    name: options.name,
    combatLevel: options.combatLevel,
    maxHitpoints: options.maxHitpoints,
    maxHit: options.maxHit,
    attack: options.attack,
    defence: options.defence,
    arena: Object.freeze({ ...options.arena }),
    spawn: Object.freeze({ ...options.spawn }),
    command: options.command,
    closeCommand: options.closeCommand,
    objectEntry: options.objectEntry,
    entryTeleport: options.entryTeleport,
    onSpawn: options.onSpawn,
    onTick: options.onTick,
    onDeath: options.onDeath,
    phases: options.phases,
    specials: options.specials,
    dropTable: options.dropTable,
    privateTicks: options.privateTicks,
    cleanupPolicy: options.cleanupPolicy ?? "close-on-terminal",
  };

  return Object.freeze(definition);
}

/**
 * Validate a boss definition and immediately register it via the global
 * `defineBoss()` bridge function.
 *
 * Equivalent to calling `defineBoss(createBoss(options))`.
 *
 * @param options  Raw boss configuration.
 */
export function registerBoss(options: BossOptions): void {
  defineBoss(createBoss(options));
}
