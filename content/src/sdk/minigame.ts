/**
 * Minigame SDK helpers.
 *
 * @module sdk/minigame
 */

export {
  createMinigame,
  registerMinigame,
} from "../minigames/minigame-builder.js";

export type {
  MinigameContext,
  MinigameDefinition,
  MinigamePoint,
  MinigameResult,
  MinigameScoreDefinition,
  MinigameWaveDefinition,
  MinigameWaveSpawn,
} from "../core/minigame.js";
