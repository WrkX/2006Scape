/**
 * Runtime dialogue helpers and cutscene sessions.
 *
 * The dialogue helpers are thin validated wrappers over the Java
 * {@link ScriptedDialogue} chain (`npc`/`player`/`statement` frames and a
 * terminal options frame). Cutscenes compose the accepted capability
 * handles — tasks, action/movement locks, camera sessions, animation,
 * graphics, sounds, and dialogue — into one owned session: every handle a
 * cutscene creates is tracked, and a step failure, the plan's final timed
 * step, or an explicit cancel releases them all. Player-owned handles are
 * additionally invalidated by the engine on logout and reload, and the
 * session treats such stale handles as contained no-ops.
 *
 * @module sdk/dialogue
 */

import type {
  ScriptedDialogue,
  ScriptedPlayer,
  ScriptTaskHandle,
} from "../core/runtime.js";

const MAX_LINES_PER_FRAME = 4;
const MAX_LINE_CHARS = 255;
const MIN_OPTIONS = 2;
const MAX_OPTIONS = 5;

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[sdk/dialogue] ${message}`);
  }
}

/**
 * The narrow player surface the dialogue helpers need. The Java
 * `ScriptedPlayer` wrapper satisfies it directly.
 */
export interface DialoguePlayer {
  getDialogue(): ScriptedDialogue;
}

function validateLines(
  lines: readonly string[],
  label: string,
): void {
  assert(lines.length >= 1 && lines.length <= MAX_LINES_PER_FRAME,
    `${label} must contain 1..${MAX_LINES_PER_FRAME} lines`);
  for (const line of lines) {
    assert(typeof line === "string" && line.length >= 1
        && line.length <= MAX_LINE_CHARS,
      `${label} lines must be 1..${MAX_LINE_CHARS} characters`);
  }
}

function applyNpc(
  dialogue: ScriptedDialogue,
  npcId: number,
  lines: readonly string[],
): void {
  switch (lines.length) {
    case 1: dialogue.npc(npcId, lines[0]); break;
    case 2: dialogue.npc(npcId, lines[0], lines[1]); break;
    case 3: dialogue.npc(npcId, lines[0], lines[1], lines[2]); break;
    case 4: dialogue.npc(npcId, lines[0], lines[1], lines[2], lines[3]); break;
  }
}

function applyPlayer(dialogue: ScriptedDialogue, lines: readonly string[]): void {
  switch (lines.length) {
    case 1: dialogue.player(lines[0]); break;
    case 2: dialogue.player(lines[0], lines[1]); break;
    case 3: dialogue.player(lines[0], lines[1], lines[2]); break;
    case 4: dialogue.player(lines[0], lines[1], lines[2], lines[3]); break;
  }
}

function applyStatement(
  dialogue: ScriptedDialogue,
  lines: readonly string[],
): void {
  switch (lines.length) {
    case 1: dialogue.statement(lines[0]); break;
    case 2: dialogue.statement(lines[0], lines[1]); break;
    case 3: dialogue.statement(lines[0], lines[1], lines[2]); break;
    case 4: dialogue.statement(lines[0], lines[1], lines[2], lines[3]); break;
  }
}

/** Show one bounded NPC dialogue frame (`1..4` lines, `<=255` chars each). */
export function sayNpc(
  player: DialoguePlayer,
  npcId: number,
  ...lines: readonly string[]
): void {
  assert(Number.isInteger(npcId) && npcId >= 0,
    `npcId must be a non-negative integer, got ${npcId}`);
  validateLines(lines, "sayNpc lines");
  applyNpc(player.getDialogue(), npcId, lines);
}

/** Show one bounded player dialogue frame (`1..4` lines, `<=255` chars). */
export function sayPlayer(
  player: DialoguePlayer,
  ...lines: readonly string[]
): void {
  validateLines(lines, "sayPlayer lines");
  applyPlayer(player.getDialogue(), lines);
}

/** Show one bounded statement frame (`1..4` lines, `<=255` chars). */
export function sayStatement(
  player: DialoguePlayer,
  ...lines: readonly string[]
): void {
  validateLines(lines, "sayStatement lines");
  applyStatement(player.getDialogue(), lines);
}

/**
 * Show a terminal options frame of `2..5` choices.
 *
 * The choice callback receives the zero-based selected index. The engine
 * clears the pending callback before invocation, so a throwing callback
 * cannot remain armed.
 */
export function sayOptions(
  player: DialoguePlayer,
  lines: readonly string[],
  onChoice: (choice: number) => void,
): void {
  assert(lines.length >= MIN_OPTIONS && lines.length <= MAX_OPTIONS,
    `options must contain ${MIN_OPTIONS}..${MAX_OPTIONS} choices`);
  for (const line of lines) {
    assert(typeof line === "string" && line.length >= 1
        && line.length <= MAX_LINE_CHARS,
      `option lines must be 1..${MAX_LINE_CHARS} characters`);
  }
  assert(typeof onChoice === "function", "onChoice must be a function");
  player.getDialogue().options([...lines], onChoice);
}

/** End the current dialogue chain. */
export function endDialogue(player: DialoguePlayer): void {
  player.getDialogue().end();
}

// ─── Cutscene sessions ───────────────────────────────────────────────────────

/** Camera component update of one cutscene camera session. */
export type CameraMove =
  | {
      readonly op: "position";
      readonly localX: number;
      readonly localY: number;
      readonly height: number;
      readonly speed: number;
      readonly angle: number;
    }
  | {
      readonly op: "lookAt";
      readonly localX: number;
      readonly localY: number;
      readonly height: number;
      readonly speed: number;
      readonly angle: number;
    }
  | {
      readonly op: "shake";
      readonly axis: number;
      readonly intensity: number;
      readonly speed: number;
      readonly frequency: number;
    };

/**
 * One cutscene step. Steps run in order; timed steps (locks, cameras,
 * waits, scheduled tasks) own their engine handles for the session
 * duration.
 */
export type CutsceneStep =
  | { readonly kind: "message"; readonly text: string }
  | { readonly kind: "animate"; readonly animationId: number }
  | { readonly kind: "graphic"; readonly graphicId: number }
  | { readonly kind: "sound"; readonly soundId: number }
  | { readonly kind: "dialogue"; readonly npcId: number; readonly lines: readonly string[] }
  | { readonly kind: "player-dialogue"; readonly lines: readonly string[] }
  | { readonly kind: "statement-dialogue"; readonly lines: readonly string[] }
  | {
      readonly kind: "options";
      readonly lines: readonly string[];
      readonly onChoice: (choice: number) => void;
    }
  | { readonly kind: "end-dialogue" }
  | {
      readonly kind: "lock";
      readonly ticks: number;
      readonly targets: "actions" | "movement" | "both";
    }
  | { readonly kind: "camera"; readonly ticks: number; readonly moves: readonly CameraMove[] }
  | { readonly kind: "wait"; readonly ticks: number }
  | {
      readonly kind: "after";
      readonly ticks: number;
      readonly run: (session: CutsceneSession) => void;
    }
  | {
      readonly kind: "every";
      readonly ticks: number;
      readonly run: (session: CutsceneSession) => void;
    }
  | { readonly kind: "run"; readonly run: (session: CutsceneSession) => void };

/**
 * The narrow player surface a cutscene may use. The Java `ScriptedPlayer`
 * wrapper satisfies it directly.
 */
export interface CutscenePlayer {
  getUsername(): string;
  message(text: string): void;
  animate(animationId: number): boolean;
  graphic(graphicId: number): boolean;
  sound(soundId: number): boolean;
  getDialogue(): ScriptedDialogue;
  after(ticks: number, handler: (handle: ScriptTaskHandle) => void): ScriptTaskHandle;
  every(ticks: number, handler: (handle: ScriptTaskHandle) => void): ScriptTaskHandle;
  getActions(): { lock(ticks: number): { release(): boolean } | null };
  getMovement(): { lock(ticks: number): { release(): boolean } | null };
  getPresentation(): {
    beginCamera(ticks: number): {
      release(): boolean;
      position(localX: number, localY: number, height: number, speed: number, angle: number): boolean;
      lookAt(localX: number, localY: number, height: number, speed: number, angle: number): boolean;
      shake(axis: number, intensity: number, speed: number, frequency: number): boolean;
    } | null;
  };
}

/** A fully validated cutscene plan. */
export interface CutscenePlan {
  readonly steps: readonly CutsceneStep[];
}

/**
 * One running cutscene session. Owns every task, lock, and camera session
 * the plan created; `cancel()` releases them all and is idempotent.
 */
export interface CutsceneSession {
  /** The player the cutscene runs for. */
  readonly player: CutscenePlayer;
  /** True while the session still owns live handles. */
  active(): boolean;
  /** True after a step failure or an explicit cancel. */
  cancelled(): boolean;
  /** True after the plan's final timed step fired. */
  completed(): boolean;
  /** Cancel all owned handles; idempotent, stale handles are no-ops. */
  cancel(): boolean;
}

interface TrackedSession {
  player: CutscenePlayer;
  tasks: Set<ScriptTaskHandle>;
  locks: Set<{ release(): boolean }>;
  cameras: Set<{ release(): boolean }>;
  cancelled: boolean;
  completed: boolean;
  pendingAfter: Set<ScriptTaskHandle>;
}

/** Module-level session registry keyed by the stable player username. */
const sessionsByPlayer = new Map<string, Set<TrackedSession>>();

function registerSession(player: CutscenePlayer): TrackedSession {
  const key = player.getUsername();
  let set = sessionsByPlayer.get(key);
  if (set === undefined) {
    set = new Set();
    sessionsByPlayer.set(key, set);
  }
  const tracked: TrackedSession = {
    player,
    tasks: new Set(),
    locks: new Set(),
    cameras: new Set(),
    cancelled: false,
    completed: false,
    pendingAfter: new Set(),
  };
  set.add(tracked);
  return tracked;
}

function unregisterSession(player: CutscenePlayer, tracked: TrackedSession): void {
  const set = sessionsByPlayer.get(player.getUsername());
  if (set !== undefined) {
    set.delete(tracked);
    if (set.size === 0) {
      sessionsByPlayer.delete(player.getUsername());
    }
  }
}

/** Release every handle a session still owns. */
function releaseAll(tracked: TrackedSession): void {
  for (const task of tracked.tasks) {
    task.cancel();
  }
  tracked.tasks.clear();
  tracked.pendingAfter.clear();
  for (const lock of tracked.locks) {
    lock.release();
  }
  tracked.locks.clear();
  for (const camera of tracked.cameras) {
    camera.release();
  }
  tracked.cameras.clear();
}

function cancelTracked(tracked: TrackedSession): void {
  if (tracked.cancelled || tracked.completed) {
    return;
  }
  tracked.cancelled = true;
  releaseAll(tracked);
  unregisterSession(tracked.player, tracked);
}

function completeTracked(tracked: TrackedSession): void {
  if (tracked.cancelled || tracked.completed) {
    return;
  }
  tracked.completed = true;
  releaseAll(tracked);
  unregisterSession(tracked.player, tracked);
}

function trackTask(
  tracked: TrackedSession,
  handle: ScriptTaskHandle | null,
  after: boolean,
): void {
  if (handle === null || handle === undefined) {
    cancelTracked(tracked);
    return;
  }
  tracked.tasks.add(handle);
  if (after) {
    tracked.pendingAfter.add(handle);
  }
}

function trackLock(
  tracked: TrackedSession,
  lock: { release(): boolean } | null,
): void {
  if (lock === null || lock === undefined) {
    cancelTracked(tracked);
    return;
  }
  tracked.locks.add(lock);
}

function trackCamera(
  tracked: TrackedSession,
  camera: {
    release(): boolean;
    position(localX: number, localY: number, height: number, speed: number, angle: number): boolean;
    lookAt(localX: number, localY: number, height: number, speed: number, angle: number): boolean;
    shake(axis: number, intensity: number, speed: number, frequency: number): boolean;
  } | null,
): void {
  if (camera === null || camera === undefined) {
    cancelTracked(tracked);
    return;
  }
  tracked.cameras.add(camera);
}

function stepExecutor(
  tracked: TrackedSession,
  run: (session: CutsceneSession) => void,
): (handle: ScriptTaskHandle) => void {
  const view = toSessionView(tracked);
  return (handle) => {
    if (tracked.cancelled || tracked.completed) {
      return;
    }
    const wasOneShot = tracked.pendingAfter.has(handle);
    if (wasOneShot) {
      tracked.tasks.delete(handle);
      tracked.pendingAfter.delete(handle);
    }
    try {
      run(view);
    } catch (caught) {
      cancelTracked(tracked);
      return;
    }
    if (wasOneShot && tracked.pendingAfter.size === 0) {
      completeTracked(tracked);
    }
  };
}

function toSessionView(tracked: TrackedSession): CutsceneSession {
  return {
    player: tracked.player,
    active: () => tracked.tasks.size > 0 || tracked.locks.size > 0
      || tracked.cameras.size > 0,
    cancelled: () => tracked.cancelled,
    completed: () => tracked.completed,
    cancel: () => {
      if (tracked.cancelled || tracked.completed) {
        return false;
      }
      cancelTracked(tracked);
      return true;
    },
  };
}

function validateStep(step: CutsceneStep, index: number): void {
  const label = `step[${index}]`;
  switch (step.kind) {
    case "message":
      assert(typeof step.text === "string" && step.text.length >= 1
          && step.text.length <= MAX_LINE_CHARS,
        `${label} message text must be 1..${MAX_LINE_CHARS} characters`);
      break;
    case "animate":
      assert(Number.isInteger(step.animationId) && step.animationId >= 0,
        `${label} animationId must be a non-negative integer`);
      break;
    case "graphic":
      assert(Number.isInteger(step.graphicId) && step.graphicId >= 0,
        `${label} graphicId must be a non-negative integer`);
      break;
    case "sound":
      assert(Number.isInteger(step.soundId) && step.soundId >= 0,
        `${label} soundId must be a non-negative integer`);
      break;
    case "dialogue":
      assert(Number.isInteger(step.npcId) && step.npcId >= 0,
        `${label} npcId must be a non-negative integer`);
      validateLines(step.lines, `${label} lines`);
      break;
    case "player-dialogue":
    case "statement-dialogue":
      validateLines(step.lines, `${label} lines`);
      break;
    case "options":
      assert(step.lines.length >= MIN_OPTIONS && step.lines.length <= MAX_OPTIONS,
        `${label} options must contain ${MIN_OPTIONS}..${MAX_OPTIONS} choices`);
      assert(typeof step.onChoice === "function",
        `${label} onChoice must be a function`);
      break;
    case "lock":
      assert(Number.isInteger(step.ticks) && step.ticks >= 1
          && step.ticks <= 100000,
        `${label} lock ticks must be an integer 1..100000`);
      break;
    case "camera":
      assert(Number.isInteger(step.ticks) && step.ticks >= 1
          && step.ticks <= 100000,
        `${label} camera ticks must be an integer 1..100000`);
      assert(step.moves.length >= 1 && step.moves.length <= 16,
        `${label} camera moves must contain 1..16 updates`);
      for (const move of step.moves) {
        if (move.op === "shake") {
          assert(Number.isFinite(move.axis) && Number.isFinite(move.intensity)
              && Number.isFinite(move.speed) && Number.isFinite(move.frequency),
            `${label} camera shake components must be finite`);
        } else {
          assert(Number.isFinite(move.localX) && Number.isFinite(move.localY)
              && Number.isFinite(move.height) && Number.isFinite(move.speed)
              && Number.isFinite(move.angle),
            `${label} camera ${move.op} components must be finite`);
        }
      }
      break;
    case "wait":
    case "after":
      assert(Number.isInteger(step.ticks) && step.ticks >= 1
          && step.ticks <= 100000,
        `${label} ticks must be an integer 1..100000`);
      break;
    case "every":
      assert(Number.isInteger(step.ticks) && step.ticks >= 1
          && step.ticks <= 100000,
        `${label} ticks must be an integer 1..100000`);
      assert(typeof step.run === "function", `${label} run must be a function`);
      break;
    case "run":
      assert(typeof step.run === "function", `${label} run must be a function`);
      break;
  }
}

/**
 * Run one cutscene plan synchronously, in order.
 *
 * Timed steps create engine handles owned by the returned session. A step
 * failure cancels every owned handle and rethrows; the plan's final
 * one-shot task releases trailing locks/cameras when it fires; a plan that
 * ends on locks/cameras or repeating tasks stays active until
 * {@link cancelCutscene} (or {@link cancelCutscenesFor} on logout) — locks
 * and cameras expire by their declared tick counts regardless. Handles
 * invalidated by logout or reload are contained no-ops.
 *
 * @param player  The live runtime player wrapper (or any
 *                {@link CutscenePlayer}).
 * @param plan    The validated cutscene plan.
 * @returns The active session (or a cancelled session when a step failed).
 */
export function runCutscene(
  player: CutscenePlayer,
  plan: CutscenePlan,
): CutsceneSession {
  assert(plan.steps.length >= 1 && plan.steps.length <= 64,
    "cutscene plan must contain 1..64 steps");
  plan.steps.forEach(validateStep);

  const tracked = registerSession(player);
  const view = toSessionView(tracked);
  try {
    for (const step of plan.steps) {
      if (tracked.cancelled) {
        return view;
      }
      switch (step.kind) {
        case "message":
          player.message(step.text);
          break;
        case "animate":
          player.animate(step.animationId);
          break;
        case "graphic":
          player.graphic(step.graphicId);
          break;
        case "sound":
          player.sound(step.soundId);
          break;
        case "dialogue":
          sayNpc(player, step.npcId, ...step.lines);
          break;
        case "player-dialogue":
          sayPlayer(player, ...step.lines);
          break;
        case "statement-dialogue":
          sayStatement(player, ...step.lines);
          break;
        case "options":
          sayOptions(player, step.lines, step.onChoice);
          break;
        case "end-dialogue":
          endDialogue(player);
          break;
        case "lock": {
          const locks: ({ release(): boolean } | null)[] = [];
          if (step.targets === "actions" || step.targets === "both") {
            locks.push(player.getActions().lock(step.ticks));
          }
          if (step.targets === "movement" || step.targets === "both") {
            locks.push(player.getMovement().lock(step.ticks));
          }
          for (const lock of locks) {
            trackLock(tracked, lock);
          }
          break;
        }
        case "camera": {
          const camera = player.getPresentation().beginCamera(step.ticks);
          if (camera === null || camera === undefined) {
            cancelTracked(tracked);
            return view;
          }
          tracked.cameras.add(camera);
          for (const move of step.moves) {
            switch (move.op) {
              case "position":
                camera.position(move.localX, move.localY, move.height,
                  move.speed, move.angle);
                break;
              case "lookAt":
                camera.lookAt(move.localX, move.localY, move.height,
                  move.speed, move.angle);
                break;
              case "shake":
                camera.shake(move.axis, move.intensity, move.speed,
                  move.frequency);
                break;
            }
          }
          break;
        }
        case "wait":
          trackTask(tracked, player.after(step.ticks, stepExecutor(
            tracked, () => {})), true);
          break;
        case "after":
          trackTask(tracked, player.after(step.ticks, stepExecutor(
            tracked, step.run)), true);
          break;
        case "every":
          trackTask(tracked, player.every(step.ticks, stepExecutor(
            tracked, step.run)), false);
          break;
        case "run":
          step.run(view);
          break;
      }
    }
    if (!tracked.cancelled && tracked.tasks.size === 0
        && tracked.locks.size === 0 && tracked.cameras.size === 0) {
      completeTracked(tracked);
    }
  } catch (caught) {
    cancelTracked(tracked);
    throw caught;
  }
  return view;
}

/**
 * Cancel one cutscene session: every owned task, lock, and camera session
 * is released. Idempotent; handles already invalidated by logout or reload
 * are contained no-ops.
 *
 * @param session  The session to cancel.
 * @returns True when the session was still active and owned handles.
 */
export function cancelCutscene(session: CutsceneSession): boolean {
  return session.cancel();
}

/**
 * Cancel every active cutscene session of one player.
 *
 * Wire this into the author's own `onLogout` observer when a cutscene may
 * span a logout; the engine itself already invalidates player-owned
 * tasks, locks, and camera sessions on logout and reload.
 *
 * @param player  The live runtime player wrapper.
 * @returns The number of sessions cancelled.
 */
export function cancelCutscenesFor(player: CutscenePlayer): number {
  const set = sessionsByPlayer.get(player.getUsername());
  if (set === undefined) {
    return 0;
  }
  let count = 0;
  for (const tracked of [...set]) {
    cancelTracked(tracked);
    count++;
  }
  return count;
}
