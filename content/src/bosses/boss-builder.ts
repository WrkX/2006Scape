/**
 * Boss builder — validated factory for {@link BossDefinition} and ergonomic
 * wrapper around the global `defineBoss()` bridge function.
 *
 * The helpers in this module add runtime validation, sensible defaults, and
 * reusable presets so that content authors get clear error messages at load
 * time instead of obscure engine failures.
 *
 * @module bosses/boss-builder
 *
 * @example Basic boss
 * ```ts
 * import { createBoss } from "./boss-builder.js";
 *
 * const dragonKing = createBoss({
 *   npcId: 1234,
 *   combatLevel: 450,
 *   maxHitpoints: 600,
 *
 *   onSpawn(ctx) {
 *     ctx.say("You dare disturb my slumber?");
 *   },
 *
 *   onTick(ctx) {
 *     if (ctx.hpPercent < 0.5) ctx.useSpecial("fire_wave");
 *   },
 *
 *   onDeath(ctx) {
 *     ctx.rollLoot("dragon_king_loot");
 *   },
 *
 *   specials: {
 *     fire_wave: {
 *       cooldownTicks: 12,
 *       handler(ctx) {
 *         ctx.say("Burn!");
 *         ctx.engagedPlayers.forEach(p => p.message("The dragon's fire engulfs you!"));
 *       }
 *     }
 *   },
 *
 *   phases: [
 *     {
 *       name: "Enrage",
 *       hpPercentThreshold: 30,
 *       onEnter(ctx) {
 *         ctx.say("Now you will see my true power!");
 *         ctx.spawnMinions(50, 3);
 *       }
 *     }
 *   ]
 * });
 *
 * // Register with the engine
 * defineBoss(dragonKing);
 * ```
 *
 * @example With the helper that registers immediately
 * ```ts
 * import { registerBoss } from "./boss-builder.js";
 *
 * registerBoss({
 *   npcId: 5678,
 *   combatLevel: 250,
 *   maxHitpoints: 300,
 *   onSpawn: ctx => ctx.say("Fight!"),
 *   onTick: ctx => {},
 *   onDeath: ctx => ctx.rollLoot("minor_boss"),
 * });
 * ```
 */

import type {
  BossContext,
  BossDefinition,
  BossPhase,
  BossSpecial,
  BossSpecials,
} from "../core/boss.js";

// ─── Internal helpers ─────────────────────────────────────────────────────────

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[boss-builder] ${message}`);
  }
}

function validateSpecials(
  specials: BossSpecials | undefined,
  bossName: string,
): void {
  if (!specials) return;
  for (const [name, spec] of Object.entries(specials)) {
    assert(typeof name === "string" && name.length > 0,
      `${bossName}: special attack key must be a non-empty string`);
    assert(typeof spec.cooldownTicks === "number" && spec.cooldownTicks >= 0,
      `${bossName}: special "${name}" cooldownTicks must be >= 0`);
    assert(typeof spec.handler === "function",
      `${bossName}: special "${name}" must have a handler function`);
  }
}

function validatePhases(
  phases: readonly BossPhase[] | undefined,
  bossName: string,
): void {
  if (!phases) return;

  assert(phases.length > 0,
    `${bossName}: phases array must be non-empty if provided`);

  // Phases must be sorted by descending HP threshold
  for (let i = 0; i < phases.length; i++) {
    const phase = phases[i];
    const label = `phase[${i}] "${phase.name}"`;
    assert(typeof phase.name === "string" && phase.name.length > 0,
      `${bossName}: ${label} must have a non-empty name`);
    assert(typeof phase.hpPercentThreshold === "number" &&
      phase.hpPercentThreshold >= 0 && phase.hpPercentThreshold <= 100,
      `${bossName}: ${label} hpPercentThreshold must be between 0 and 100`);
    assert(typeof phase.onEnter === "function",
      `${bossName}: ${label} must have an onEnter handler`);
  }

  // Check descending order
  for (let i = 0; i < phases.length - 1; i++) {
    assert(phases[i].hpPercentThreshold > phases[i + 1].hpPercentThreshold,
      `${bossName}: phases must be in descending hpPercentThreshold order ` +
      `(phase "${phases[i].name}" at ${phases[i].hpPercentThreshold}%, ` +
      `phase "${phases[i + 1].name}" at ${phases[i + 1].hpPercentThreshold}%)`);
  }

  // Check no duplicate names
  const names = new Set<string>();
  for (const phase of phases) {
    assert(!names.has(phase.name),
      `${bossName}: duplicate phase name "${phase.name}"`);
    names.add(phase.name);
  }
}

// ─── Public API ───────────────────────────────────────────────────────────────

/**
 * Options accepted by {@link createBoss}.
 *
 * Identical to {@link BossDefinition} except every field is required at
 * construction time; optional fields in the final definition are filled in
 * with defaults by the builder.
 */
export type BossOptions = {
  readonly npcId: number;
  readonly combatLevel: number;
  readonly maxHitpoints: number;
  readonly displayName?: string;
  readonly respawnTicks?: number;
  readonly onSpawn: (ctx: BossContext) => void;
  readonly onTick: (ctx: BossContext) => void;
  readonly onDeath: (ctx: BossContext) => void;
  readonly specials?: BossSpecials;
  readonly phases?: readonly BossPhase[];
};

/**
 * Create a validated {@link BossDefinition}.
 *
 * Performs the following checks:
 * - `npcId` must be a positive integer.
 * - `combatLevel` must be positive.
 * - `maxHitpoints` must be positive.
 * - All special attacks must have a non-negative cooldown and a handler.
 * - Phases must be in descending HP-threshold order with unique names.
 *
 * @param options  Raw boss configuration.
 * @returns A frozen, validated {@link BossDefinition}.
 */
export function createBoss(options: BossOptions): BossDefinition {
  const name = options.displayName ?? `Boss(npcId=${options.npcId})`;

  assert(Number.isInteger(options.npcId) && options.npcId > 0,
    `${name}: npcId must be a positive integer, got ${options.npcId}`);
  assert(Number.isInteger(options.combatLevel) && options.combatLevel > 0,
    `${name}: combatLevel must be positive, got ${options.combatLevel}`);
  assert(Number.isInteger(options.maxHitpoints) && options.maxHitpoints > 0,
    `${name}: maxHitpoints must be positive, got ${options.maxHitpoints}`);
  assert(typeof options.onSpawn === "function",
    `${name}: onSpawn must be a function`);
  assert(typeof options.onTick === "function",
    `${name}: onTick must be a function`);
  assert(typeof options.onDeath === "function",
    `${name}: onDeath must be a function`);

  if (options.respawnTicks !== undefined) {
    assert(Number.isInteger(options.respawnTicks) && options.respawnTicks >= 0,
      `${name}: respawnTicks must be >= 0, got ${options.respawnTicks}`);
  }

  validateSpecials(options.specials, name);
  validatePhases(options.phases, name);

  const definition: BossDefinition = {
    npcId: options.npcId,
    combatLevel: options.combatLevel,
    maxHitpoints: options.maxHitpoints,
    displayName: options.displayName,
    onSpawn: options.onSpawn,
    onTick: options.onTick,
    onDeath: options.onDeath,
    specials: options.specials,
    phases: options.phases,
    respawnTicks: options.respawnTicks,
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

// ─── Presets ──────────────────────────────────────────────────────────────────

/**
 * Common boss behaviour preset.
 *
 * "dps_check" — the boss has no specials or phases; it is a pure
 * damage-per-second gate.  Useful for tutorial / introductory bosses.
 */
export type BossPreset = "dps_check" | "mechanic_heavy";

/**
 * Fill in missing lifecycle hooks with sensible defaults for common presets.
 *
 * @param options    Partial boss configuration.
 * @param preset     A named behaviour preset.
 * @returns A complete {@link BossOptions} object ready for {@link createBoss}.
 */
export function applyPreset(
  options: Partial<BossOptions> & {
    readonly npcId: number;
    readonly combatLevel: number;
    readonly maxHitpoints: number;
  },
  preset: BossPreset,
): BossOptions {
  const onSpawn = options.onSpawn ?? ((_ctx: BossContext) => {});
  const onTick = options.onTick ?? ((_ctx: BossContext) => {});
  const onDeath = options.onDeath ?? ((ctx: BossContext) => {
    ctx.say("Aaargh!");
  });

  if (preset === "dps_check") {
    return {
      npcId: options.npcId,
      combatLevel: options.combatLevel,
      maxHitpoints: options.maxHitpoints,
      displayName: options.displayName,
      respawnTicks: options.respawnTicks,
      onSpawn,
      onTick,
      onDeath,
      specials: options.specials,
      phases: options.phases,
    };
  }

  // mechanic_heavy — if no phases provided, add a default enrage
  const phases: readonly BossPhase[] = options.phases ?? [
    {
      name: "Enrage",
      hpPercentThreshold: 25,
      onEnter: (ctx: BossContext) => {
        ctx.say("You push me too far!");
        ctx.spawnMinions(options.npcId - 1, 2);
      },
    },
  ];

  return {
    npcId: options.npcId,
    combatLevel: options.combatLevel,
    maxHitpoints: options.maxHitpoints,
    displayName: options.displayName,
    respawnTicks: options.respawnTicks,
    onSpawn,
    onTick,
    onDeath,
    specials: options.specials,
    phases,
  };
}
