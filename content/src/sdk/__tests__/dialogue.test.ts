/**
 * Dialogue helper and cutscene session tests.
 *
 * Fakes replace the engine handles so the SDK contract — bounds, handle
 * ownership, cancellation on failure/completion/cancel, and stale-handle
 * no-ops — is proven without a live server.
 */

import { test } from "node:test";
import assert from "node:assert/strict";
import {
  sayNpc,
  sayPlayer,
  sayStatement,
  sayOptions,
  endDialogue,
  runCutscene,
  cancelCutscene,
  cancelCutscenesFor,
} from "../dialogue.js";
import type { CutscenePlayer, CutsceneSession } from "../dialogue.js";
import type { ScriptedDialogue, ScriptTaskHandle } from "../../core/runtime.js";

class FakeTask implements ScriptTaskHandle {
  cancelled = false;
  cancel(): boolean {
    this.cancelled = true;
    return true;
  }
  isCancelled(): boolean {
    return this.cancelled;
  }
}

class FakeLock {
  released = false;
  release(): boolean {
    this.released = true;
    return true;
  }
}

class FakeCamera {
  released = false;
  moves: string[] = [];
  release(): boolean {
    this.released = true;
    return true;
  }
  position(): boolean { this.moves.push("position"); return true; }
  lookAt(): boolean { this.moves.push("lookAt"); return true; }
  shake(): boolean { this.moves.push("shake"); return true; }
}

class FakeDialogue implements ScriptedDialogue {
  frames: string[] = [];
  endCalls = 0;
  lastOptions: readonly string[] = [];
  lastChoiceCallback: ((choice: number) => void) | null = null;

  npc(npcId: number, line: string, line2?: string, line3?: string, line4?: string): this {
    this.frames.push(`npc:${npcId}:${[line, line2, line3, line4]
      .filter((value) => value !== undefined).join("|")}`);
    return this;
  }
  player(line: string, line2?: string, line3?: string, line4?: string): this {
    this.frames.push(`player:${[line, line2, line3, line4]
      .filter((value) => value !== undefined).join("|")}`);
    return this;
  }
  statement(line: string, line2?: string, line3?: string, line4?: string): this {
    this.frames.push(`statement:${[line, line2, line3, line4]
      .filter((value) => value !== undefined).join("|")}`);
    return this;
  }
  options(lines: string[], callback: (choice: number) => void): this {
    this.lastOptions = lines;
    this.lastChoiceCallback = callback;
    this.frames.push(`options:${lines.join("|")}`);
    return this;
  }
  itemDialogue(_itemId: number, _header: string, _lines: string[]): this {
    this.frames.push("itemDialogue");
    return this;
  }
  end(): void {
    this.endCalls++;
  }
}

interface Scheduled {
  ticks: number;
  handler: (handle: ScriptTaskHandle) => void;
  task: FakeTask;
  every: boolean;
}

class FakePlayer implements CutscenePlayer {
  username: string;
  dialogue = new FakeDialogue();
  messages: string[] = [];
  animations: number[] = [];
  graphics: number[] = [];
  sounds: number[] = [];
  scheduled: Scheduled[] = [];
  locks: FakeLock[] = [];
  cameras: FakeCamera[] = [];
  lockFailure = false;
  cameraFailure = false;

  constructor(username = "tester") {
    this.username = username;
  }

  getUsername(): string { return this.username; }
  message(text: string): void { this.messages.push(text); }
  animate(animationId: number): boolean { this.animations.push(animationId); return true; }
  graphic(graphicId: number): boolean { this.graphics.push(graphicId); return true; }
  sound(soundId: number): boolean { this.sounds.push(soundId); return true; }
  getDialogue(): ScriptedDialogue { return this.dialogue; }

  after(ticks: number, handler: (handle: ScriptTaskHandle) => void): ScriptTaskHandle {
    const task = new FakeTask();
    this.scheduled.push({ ticks, handler, task, every: false });
    return task;
  }

  every(ticks: number, handler: (handle: ScriptTaskHandle) => void): ScriptTaskHandle {
    const task = new FakeTask();
    this.scheduled.push({ ticks, handler, task, every: true });
    return task;
  }

  getActions(): { lock(ticks: number): { release(): boolean } | null } {
    return { lock: () => this.lockFailure ? null : new FakeLock() };
  }

  getMovement(): { lock(ticks: number): { release(): boolean } | null } {
    return { lock: () => this.lockFailure ? null : new FakeLock() };
  }

  getPresentation(): {
    beginCamera(ticks: number): {
      release(): boolean;
      position(): boolean;
      lookAt(): boolean;
      shake(): boolean;
    } | null;
  } {
    return { beginCamera: () => this.cameraFailure ? null : new FakeCamera() };
  }

  /** Fire the engine's scheduled tasks in registration order. */
  fireAll(): void {
    for (const scheduled of [...this.scheduled]) {
      scheduled.handler(scheduled.task);
    }
  }
}

test("dialogue helpers bound frames and forward exact arity", () => {
  const player = new FakePlayer();
  sayNpc(player, 1, "Hello.");
  sayNpc(player, 1, "A", "B");
  sayNpc(player, 1, "A", "B", "C", "D");
  sayPlayer(player, "I agree.");
  sayPlayer(player, "A", "B", "C", "D");
  sayStatement(player, "The door opens.");
  assert.deepEqual(player.dialogue.frames, [
    "npc:1:Hello.",
    "npc:1:A|B",
    "npc:1:A|B|C|D",
    "player:I agree.",
    "player:A|B|C|D",
    "statement:The door opens.",
  ]);
});

test("dialogue helpers reject out-of-bound input", () => {
  const player = new FakePlayer();
  assert.throws(() => sayNpc(player, 1), /1\.\.4/);
  assert.throws(() => sayNpc(player, 1, "A", "B", "C", "D", "E"), /1\.\.4/);
  assert.throws(() => sayNpc(player, 1, ""), /1\.\.255/);
  assert.throws(() => sayNpc(player, 1, "x".repeat(256)), /1\.\.255/);
  assert.throws(() => sayNpc(player, -1, "x"), /npcId/);
  assert.throws(() => sayPlayer(player), /1\.\.4/);
  assert.throws(() => sayStatement(player, "x".repeat(300)), /1\.\.255/);
});

test("sayOptions bounds choices and forwards the callback", () => {
  const player = new FakePlayer();
  const chosen: number[] = [];
  sayOptions(player, ["Yes.", "No."], (choice) => chosen.push(choice));
  assert.deepEqual(player.dialogue.lastOptions, ["Yes.", "No."]);
  assert.ok(player.dialogue.lastChoiceCallback !== null);
  player.dialogue.lastChoiceCallback!(1);
  assert.deepEqual(chosen, [1]);

  assert.throws(() => sayOptions(player, ["Only one."], () => {}), /2\.\.5/);
  assert.throws(() => sayOptions(player,
    ["A", "B", "C", "D", "E", "F"], () => {}), /2\.\.5/);
  assert.throws(() => sayOptions(player, ["A", ""], () => {}), /1\.\.255/);
});

test("endDialogue forwards through the chain", () => {
  const player = new FakePlayer();
  endDialogue(player);
  assert.equal(player.dialogue.endCalls, 1);
});

test("cutscene executes steps in order", () => {
  const player = new FakePlayer();
  const session = runCutscene(player, {
    steps: [
      { kind: "message", text: "First" },
      { kind: "animate", animationId: 829 },
      { kind: "graphic", graphicId: 246 },
      { kind: "sound", soundId: 1 },
      { kind: "dialogue", npcId: 54, lines: ["Beware."] },
      { kind: "run", run: (s) => s.player.message("Inside") },
    ],
  });
  assert.ok(session.active() === false || session.completed());
  assert.deepEqual(player.messages, ["First", "Inside"]);
  assert.deepEqual(player.animations, [829]);
  assert.deepEqual(player.graphics, [246]);
  assert.deepEqual(player.sounds, [1]);
  assert.deepEqual(player.dialogue.frames, ["npc:54:Beware."]);
});

test("cutscene failure cancels every owned handle and rethrows", () => {
  const player = new FakePlayer();
  let thrown: unknown = null;
  try {
    runCutscene(player, {
      steps: [
        { kind: "lock", ticks: 50, targets: "both" },
        { kind: "camera", ticks: 30, moves: [
          { op: "position", localX: 0, localY: 0, height: 0, speed: 1, angle: 0 },
        ] },
        { kind: "after", ticks: 5, run: () => {} },
        { kind: "run", run: () => {
          throw new Error("boom");
        } },
      ],
    });
  } catch (caught) {
    thrown = caught;
  }
  assert.ok(thrown instanceof Error);
  assert.match(String(thrown), /boom/);
  assert.ok(player.locks.every((lock) => lock.released));
  assert.ok(player.cameras.every((camera) => camera.released));
  const tasks = player.scheduled.map((scheduled) => scheduled.task);
  assert.ok(tasks.every((task) => task.cancelled));
});

test("a throwing scheduled callback cancels the session", () => {
  const player = new FakePlayer();
  const session = runCutscene(player, {
    steps: [
      { kind: "lock", ticks: 50, targets: "actions" },
      { kind: "after", ticks: 3, run: () => {
        throw new Error("scheduled boom");
      } },
    ],
  });
  assert.ok(session.active());
  player.fireAll();
  assert.ok(session.cancelled());
  assert.ok(player.locks.every((lock) => lock.released));
});

test("completion fires after the final one-shot task and releases trailing handles", () => {
  const player = new FakePlayer();
  const session = runCutscene(player, {
    steps: [
      { kind: "lock", ticks: 50, targets: "both" },
      { kind: "camera", ticks: 30, moves: [
        { op: "lookAt", localX: 1, localY: 2, height: 3, speed: 4, angle: 5 },
      ] },
      { kind: "every", ticks: 4, run: () => {} },
      { kind: "wait", ticks: 3 },
    ],
  });
  assert.ok(session.active());
  const wait = player.scheduled.find((scheduled) => !scheduled.every);
  assert.ok(wait !== undefined);
  wait.handler(wait.task);
  assert.ok(session.completed());
  assert.ok(!session.active());
  assert.ok(player.locks.every((lock) => lock.released));
  assert.ok(player.cameras.every((camera) => camera.released));
  assert.ok(player.scheduled.filter((scheduled) => scheduled.every)
    .every((scheduled) => scheduled.task.cancelled));
});

test("an every-task fire never completes the session", () => {
  const player = new FakePlayer();
  const session = runCutscene(player, {
    steps: [
      { kind: "every", ticks: 4, run: () => {} },
      { kind: "lock", ticks: 50, targets: "actions" },
    ],
  });
  assert.ok(session.active());
  const every = player.scheduled.find((scheduled) => scheduled.every);
  assert.ok(every !== undefined);
  every.handler(every.task);
  assert.ok(!session.completed(), "an every fire must not complete the session");
  assert.ok(session.active(), "locks stay owned after an every fire");
  assert.ok(player.locks.every((lock) => !lock.released));
  assert.ok(every.task.isCancelled() === false,
    "the repeating task keeps running");
  assert.ok(cancelCutscene(session));
  assert.ok(session.cancelled());
  assert.ok(player.locks.every((lock) => lock.released));
  assert.ok(every.task.cancelled);
});

test("plans without one-shot tasks stay active until explicit cancel", () => {
  const player = new FakePlayer();
  const session = runCutscene(player, {
    steps: [
      { kind: "lock", ticks: 50, targets: "actions" },
      { kind: "camera", ticks: 30, moves: [
        { op: "shake", axis: 1, intensity: 2, speed: 3, frequency: 4 },
      ] },
    ],
  });
  assert.ok(session.active());
  assert.ok(!session.cancelled());
  assert.ok(!session.completed());
  assert.ok(cancelCutscene(session));
  assert.ok(session.cancelled());
  assert.ok(player.locks.every((lock) => lock.released));
  assert.ok(player.cameras.every((camera) => camera.released));
  assert.ok(!cancelCutscene(session), "second cancel is a no-op");
  assert.ok(!cancelCutscene(session), "cancelled sessions stay cancelled");
});

test("cancelCutscenesFor cancels every session of one player", () => {
  const player = new FakePlayer();
  const first = runCutscene(player, {
    steps: [{ kind: "lock", ticks: 10, targets: "actions" }],
  });
  const second = runCutscene(player, {
    steps: [{ kind: "camera", ticks: 10, moves: [
      { op: "position", localX: 0, localY: 0, height: 0, speed: 1, angle: 0 },
    ] }],
  });
  assert.ok(first.active());
  assert.ok(second.active());
  assert.equal(cancelCutscenesFor(player), 2);
  assert.ok(first.cancelled());
  assert.ok(second.cancelled());
  assert.ok(player.locks.every((lock) => lock.released));
  assert.ok(player.cameras.every((camera) => camera.released));
  assert.equal(cancelCutscenesFor(player), 0, "registry is empty after cancel");
});

test("failed handle acquisition cancels the session", () => {
  const lockPlayer = new FakePlayer();
  lockPlayer.lockFailure = true;
  const session = runCutscene(lockPlayer, {
    steps: [{ kind: "lock", ticks: 10, targets: "actions" }],
  });
  assert.ok(session.cancelled());

  const cameraPlayer = new FakePlayer();
  cameraPlayer.cameraFailure = true;
  const cameraSession = runCutscene(cameraPlayer, {
    steps: [{ kind: "camera", ticks: 10, moves: [
      { op: "position", localX: 0, localY: 0, height: 0, speed: 1, angle: 0 },
    ] }],
  });
  assert.ok(cameraSession.cancelled());
});

test("stale fired handles after cancel are contained no-ops", () => {
  const player = new FakePlayer();
  const session = runCutscene(player, {
    steps: [{ kind: "wait", ticks: 3 }],
  });
  const wait = player.scheduled[0];
  assert.ok(cancelCutscene(session));
  assert.doesNotThrow(() => wait.handler(wait.task));
  assert.ok(session.cancelled());
});

test("invalid cutscene plans are rejected before execution", () => {
  const player = new FakePlayer();
  assert.throws(() => runCutscene(player, { steps: [] }), /1\.\.64/);
  assert.throws(() => runCutscene(player, {
    steps: Array.from({ length: 65 }, () => ({ kind: "message" as const, text: "x" })),
  }), /1\.\.64/);
  assert.throws(() => runCutscene(player, {
    steps: [{ kind: "lock", ticks: 0, targets: "actions" }],
  }), /1\.\.100000/);
  assert.throws(() => runCutscene(player, {
    steps: [{ kind: "lock", ticks: 100001, targets: "actions" }],
  }), /1\.\.100000/);
  assert.throws(() => runCutscene(player, {
    steps: [{ kind: "camera", ticks: 10, moves: [] }],
  }), /1\.\.16/);
  assert.throws(() => runCutscene(player, {
    steps: [{ kind: "camera", ticks: 10, moves: [
      { op: "position", localX: Number.NaN, localY: 0, height: 0, speed: 1, angle: 0 },
    ] }],
  }), /finite/);
  assert.throws(() => runCutscene(player, {
    steps: [{ kind: "dialogue", npcId: 1, lines: [] }],
  }), /1\.\.4/);
  assert.throws(() => runCutscene(player, {
    steps: [{ kind: "every", ticks: 3, run: "not-a-function" as never }],
  }), /function/);
  assert.equal(player.messages.length, 0, "no step executed");
});

test("session view reports state truthfully", () => {
  const player = new FakePlayer();
  const session = runCutscene(player, {
    steps: [
      { kind: "lock", ticks: 10, targets: "actions" },
      { kind: "wait", ticks: 2 },
    ],
  });
  assert.ok(session.active());
  assert.ok(!session.cancelled());
  assert.ok(!session.completed());
  player.fireAll();
  assert.ok(session.completed());
  assert.ok(!session.cancelled());
  assert.ok(!session.active());
});

test("cutscene cancels by username across wrapper instances", () => {
  const firstWrapper = new FakePlayer("shared-player");
  const session = runCutscene(firstWrapper, {
    steps: [{ kind: "lock", ticks: 10, targets: "actions" }],
  });
  const secondWrapper = new FakePlayer("shared-player");
  assert.equal(cancelCutscenesFor(secondWrapper), 1);
  assert.ok(session.cancelled());
});

test("cutscene session type is structurally the runtime player", () => {
  const player: CutscenePlayer = new FakePlayer();
  const session: CutsceneSession = runCutscene(player, {
    steps: [{ kind: "message", text: "Structural." }],
  });
  assert.ok(!session.cancelled());
});
