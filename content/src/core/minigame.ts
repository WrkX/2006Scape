/**
 * Declarative minigame type definitions.
 *
 * {@link defineMinigame} registers a schema-v1 minigame that composes
 * {@link defineArea} lobby/arena references with one encounter session,
 * ordered waves, and optional per-player score state.
 *
 * @module core/minigame
 */

import type {
  ScriptArray,
  ScriptEncounterHandle,
  ScriptedPlayer,
  ScriptedPosition,
} from "./runtime.js";

export interface MinigameBounds {
  readonly minX: number;
  readonly minY: number;
  readonly maxX: number;
  readonly maxY: number;
  readonly plane: number;
}

export interface MinigamePoint {
  readonly x: number;
  readonly y: number;
  readonly plane: number;
}

export interface MinigameWaveSpawn {
  readonly npcId: number;
  readonly x: number;
  readonly y: number;
  readonly plane?: number;
}

export interface MinigameWaveDefinition {
  readonly id: string;
  readonly npcs: readonly MinigameWaveSpawn[];
}

export interface MinigameScoreDefinition {
  readonly namespace: string;
  readonly key: string;
}

export type MinigameResult =
  | { readonly status: "in_progress" }
  | { readonly status: "wiped"; readonly reason: string };

export interface MinigameContext {
  readonly encounter: ScriptEncounterHandle;
  id(): string;
  waveId(): string | null;
  waveIndex(): number;
  elapsedTicks(): number;
  position(): ScriptedPosition;
  participants(): ScriptArray<ScriptedPlayer>;
  announce(text: string): boolean;
  score(player: ScriptedPlayer): number;
  addScore(player: ScriptedPlayer, delta: number): boolean;
}

export interface MinigameDefinition {
  readonly id: string;
  readonly name?: string;
  readonly command: string;
  readonly lobbyAreaId: string;
  readonly arenaAreaId: string;
  readonly entrance: MinigamePoint;
  readonly leave: MinigamePoint;
  readonly minPlayers: number;
  readonly maxPlayers: number;
  readonly lobbyWaitTicks: number;
  readonly timeLimitTicks: number;
  readonly waves: readonly MinigameWaveDefinition[];
  readonly score?: MinigameScoreDefinition;
  readonly onStart?: (context: MinigameContext) => void;
  readonly onWaveStart?: (context: MinigameContext) => void;
  readonly onWaveComplete?: (context: MinigameContext) => void;
  readonly onTick?: (context: MinigameContext) => MinigameResult;
  readonly onComplete?: (context: MinigameContext) => void;
  readonly onWipe?: (context: MinigameContext, reason: string) => void;
}

export type DefineMinigame = (definition: MinigameDefinition) => void;
