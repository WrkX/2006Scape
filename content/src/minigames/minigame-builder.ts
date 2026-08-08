/**
 * Minigame builder helpers.
 *
 * @module minigames/minigame-builder
 */

import type {
  DefineMinigame,
  MinigameDefinition,
  MinigamePoint,
  MinigameWaveDefinition,
} from "../core/minigame.js";

const ID_PATTERN = /^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$/;
const COMMAND_PATTERN = /^[a-z0-9][a-z0-9-]*$/;
const RESERVED_COMMANDS = new Set(["scripts", "reload", "scriptdir"]);

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[minigame-builder] ${message}`);
  }
}

function integral(value: number, min: number, max: number, label: string): void {
  assert(Number.isInteger(value) && value >= min && value <= max,
    `${label} must be an integer ${min}..${max}, got ${value}`);
}

function validatePoint(point: MinigamePoint, label: string): MinigamePoint {
  integral(point.x, 0, 16383, `${label}.x`);
  integral(point.y, 0, 16383, `${label}.y`);
  integral(point.plane, 0, 3, `${label}.plane`);
  return Object.freeze({ ...point });
}

function validateWaves(
  waves: readonly MinigameWaveDefinition[],
): readonly MinigameWaveDefinition[] {
  assert(waves.length >= 1, "waves must contain at least one entry");
  const ids = new Set<string>();
  return Object.freeze(waves.map((wave) => {
    assert(ID_PATTERN.test(wave.id),
      `invalid wave id '${wave.id}'`);
    assert(!ids.has(wave.id), `duplicate wave id '${wave.id}'`);
    ids.add(wave.id);
    assert(wave.npcs.length >= 1,
      `wave '${wave.id}' must contain at least one npc spawn`);
    const npcs = Object.freeze(wave.npcs.map((spawn) => {
      integral(spawn.npcId, 0, 65535, `wave '${wave.id}' npcId`);
      integral(spawn.x, 0, 16383, `wave '${wave.id}' x`);
      integral(spawn.y, 0, 16383, `wave '${wave.id}' y`);
      if (spawn.plane !== undefined) {
        integral(spawn.plane, 0, 3, `wave '${wave.id}' plane`);
      }
      return Object.freeze({ ...spawn });
    }));
    return Object.freeze({ id: wave.id, npcs });
  }));
}

/**
 * Create a validated, deeply frozen {@link MinigameDefinition}.
 */
export function createMinigame(
  definition: MinigameDefinition,
): MinigameDefinition {
  assert(ID_PATTERN.test(definition.id),
    `invalid minigame id '${definition.id}'`);
  assert(COMMAND_PATTERN.test(definition.command),
    `invalid command '${definition.command}'`);
  assert(!RESERVED_COMMANDS.has(definition.command),
    `command '${definition.command}' is reserved`);
  assert(typeof definition.lobbyAreaId === "string"
      && definition.lobbyAreaId.length > 0,
    "lobbyAreaId is required");
  assert(typeof definition.arenaAreaId === "string"
      && definition.arenaAreaId.length > 0,
    "arenaAreaId is required");
  integral(definition.minPlayers, 1, 25, "minPlayers");
  integral(definition.maxPlayers, 1, 25, "maxPlayers");
  assert(definition.minPlayers <= definition.maxPlayers,
    "minPlayers must not exceed maxPlayers");
  integral(definition.lobbyWaitTicks, 0, 10000, "lobbyWaitTicks");
  integral(definition.timeLimitTicks, 1, 100000, "timeLimitTicks");
  const waves = validateWaves(definition.waves);
  const entrance = validatePoint(definition.entrance, "entrance");
  const leave = validatePoint(definition.leave, "leave");
  if (definition.score !== undefined) {
    assert(typeof definition.score.namespace === "string"
        && definition.score.namespace.length > 0,
      "score.namespace is required when score is present");
    assert(typeof definition.score.key === "string"
        && definition.score.key.length > 0,
      "score.key is required when score is present");
  }
  return Object.freeze({
    ...definition,
    entrance,
    leave,
    waves,
    score: definition.score === undefined
      ? undefined
      : Object.freeze({ ...definition.score }),
  });
}

/** Register one minigame through the Java bridge. */
export function registerMinigame(definition: MinigameDefinition): void {
  defineMinigame(createMinigame(definition));
}
