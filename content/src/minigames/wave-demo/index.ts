/**
 * Wave-based minigame demo composing defineArea + defineMinigame.
 *
 * @module minigames/wave-demo
 */

import { registerModule } from "../../manifest.js";
import { createArea } from "../../areas/area-builder.js";
import { registerMinigame } from "../../sdk/minigame.js";
import type { MinigameContext } from "../../core/minigame.js";

const LOBBY_BOUNDS = {
  minX: 3218,
  minY: 3218,
  maxX: 3224,
  maxY: 3224,
  plane: 0,
} as const;

const ARENA_BOUNDS = {
  minX: 3218,
  minY: 3218,
  maxX: 3230,
  maxY: 3230,
  plane: 0,
} as const;

registerModule({ id: "wave-demo", schemaVersion: 1 }, () => {
  defineArea(createArea({
    id: "wave-demo-lobby",
    name: "Wave Demo Lobby",
    bounds: LOBBY_BOUNDS,
    npcs: [],
    objects: [],
  }));

  defineArea(createArea({
    id: "wave-demo-arena",
    name: "Wave Demo Arena",
    bounds: ARENA_BOUNDS,
    npcs: [],
    objects: [],
  }));

  registerMinigame({
    id: "wave-demo",
    name: "Wave Demo",
    command: "wave-demo",
    lobbyAreaId: "wave-demo-lobby",
    arenaAreaId: "wave-demo-arena",
    entrance: { x: 3220, y: 3220, plane: 0 },
    leave: { x: 3218, y: 3218, plane: 0 },
    minPlayers: 1,
    maxPlayers: 5,
    lobbyWaitTicks: 0,
    timeLimitTicks: 600,
    score: { namespace: "minigame", key: "wave_demo_score" },
    waves: [
      {
        id: "wave-one",
        npcs: [{ npcId: 1, x: 3220, y: 3222 }],
      },
      {
        id: "wave-two",
        npcs: [{ npcId: 1, x: 3222, y: 3222 }],
      },
    ],
    onStart: (ctx: MinigameContext) => {
      ctx.announce("The wave demo begins!");
    },
    onWaveStart: (ctx: MinigameContext) => {
      ctx.announce(`Wave ${ctx.waveIndex() + 1} has begun.`);
    },
    onComplete: (ctx: MinigameContext) => {
      ctx.announce("All waves cleared!");
    },
    onWipe: (ctx: MinigameContext, reason: string) => {
      ctx.announce(`Wave demo ended: ${reason}`);
    },
  });
});

export {};
