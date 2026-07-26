/**
 * Dialogue tree builder — constructs {@link Dialogue} objects with a
 * chainable, declarative API.
 *
 * The builder supports the three dialogue types:
 * - **NPC** — an NPC is speaking (shows a head model).
 * - **Player** — the player is speaking (shows the player model).
 * - **Statement** — neutral system text (no model).
 *
 * Dialogue trees are built linearly (`line`, `option`, `continue`) but can
 * also encode branching via option handlers that open new dialogues.
 *
 * @module core/dialogue
 *
 * @example Simple NPC monologue
 * ```ts
 * import { dialogue } from "./dialogue.js";
 *
 * const greeting = dialogue("npc")
 *   .title("Wise Old Man")
 *   .line("Ah, another adventurer.")
 *   .line("I remember when I was young and foolish like you.")
 *   .line("Take this advice: never trust a goblin.")
 *   .continue("I will keep that in mind.", (player) => {
 *     player.message("The old man nods sagely.");
 *   })
 *   .build();
 *
 * player.openDialogue(greeting);
 * ```
 *
 * @example Branching dialogue with options
 * ```ts
 * import { dialogue } from "./dialogue.js";
 *
 * const questStart = dialogue("npc")
 *   .title("Elder Wizard")
 *   .line("I sense great potential in you, adventurer.")
 *   .line("Will you help me with a dangerous task?")
 *   .option("I will help you.", (player) => {
 *     player.message("The elder smiles warmly.");
 *     player.quests.get("dragon_awakens"); // trigger quest start
 *   })
 *   .option("Not right now.", (player) => {
 *     player.message("The elder looks disappointed but nods.");
 *   })
 *   .option("What's in it for me?", (player) => {
 *     player.openDialogue(
 *       dialogue("npc")
 *         .title("Elder Wizard")
 *         .line("A fair question. The reward is a dragon-forged token.")
 *         .line("It will grant you passage to Dragon Island.")
 *         .continue("Very well, I accept.", (p) => {
 *           p.message("The elder begins the ritual...");
 *         })
 *         .build()
 *     );
 *   })
 *   .build();
 * ```
 *
 * @example Statement dialogue (system messages)
 * ```ts
 * import { statement } from "./dialogue.js";
 *
 * const tutorial = statement()
 *   .line("Welcome to SingleScape.")
 *   .line("Use the arrow keys to move around.")
 *   .continue("Got it!", (player) => {
 *     player.message("Tutorial complete!");
 *   })
 *   .build();
 * ```
 *
 * @example Chainable sub-dialogues (NPC → Player → Statement)
 * ```ts
 * import { chain } from "./dialogue.js";
 *
 * const conversation = chain("npc")
 *   .title("Mysterious Stranger")
 *   .line("Do you know who I am?")
 *   .thenPlayer("I have no idea.")
 *   .line("Who are you?")
 *   .thenNpc("Mysterious Stranger")
 *   .line("That is not important. What matters is what I can offer you.")
 *   .thenStatement()
 *   .line("The stranger hands you a sealed letter.")
 *   .continue("Take the letter.", (player) => {
 *     player.inventory.add("sealed_letter", 1);
 *   })
 *   .build();
 * ```
 */

import type { Player } from "./player.js";
import type {
  Dialogue,
  DialogueOption,
  DialogueType,
} from "./types.js";

// ─── Internal helpers ─────────────────────────────────────────────────────────

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[dialogue] ${message}`);
  }
}

const VALID_DIALOGUE_TYPES: ReadonlySet<string> = new Set([
  "npc", "player", "statement",
]);

// ─── Dialogue builder ─────────────────────────────────────────────────────────

/**
 * Options for the `continue` call — a single button that advances past
 * the current dialogue or closes the interface.
 */
export interface ContinueOption {
  readonly text: string;
  readonly handler?: (player: Player) => void;
}

/**
 * Convenience: either a `ContinueOption` full object, or just the button
 * label string (handler defaults to no-op).
 */
export type ContinueSpec = string | ContinueOption;

// ─── Builder implementation ───────────────────────────────────────────────────

/**
 * Builder for constructing a single {@link Dialogue} screen.
 *
 * Each builder instance represents one screen.  Use methods to add lines
 * (displayed in order), options (shown as clickable buttons at the bottom),
 * and a continuation button.
 *
 * @example
 * ```ts
 * dialogue("npc")
 *   .title("Hans")
 *   .line("Hello!")
 *   .option("Buy something.", handler)
 *   .option("Leave.", handler)
 *   .build();
 * ```
 */
export class DialogueBuilder {
  private _type: DialogueType;
  private _title: string | undefined;
  private _lines: string[] = [];
  private _options: DialogueOption[] = [];
  private _continueText: string | null = null;
  private _continueHandler: ((player: Player) => void) | undefined;
  private _frozen = false;

  constructor(type: DialogueType) {
    assert(VALID_DIALOGUE_TYPES.has(type),
      `Unknown dialogue type "${type}". Must be one of: ${[...VALID_DIALOGUE_TYPES].join(", ")}`);
    this._type = type;
  }

  /** Set the optional title shown at the top of the dialogue. */
  title(text: string): this {
    assert(!this._frozen, "Cannot modify a frozen builder");
    assert(typeof text === "string" && text.length > 0,
      "Title must be a non-empty string");
    this._title = text;
    return this;
  }

  /**
   * Add a line of text to the dialogue body.
   *
   * Lines are displayed in the order they are added.
   */
  line(text: string): this {
    assert(!this._frozen, "Cannot modify a frozen builder");
    assert(typeof text === "string" && text.length > 0,
      "Line text must be a non-empty string");
    this._lines.push(text);
    return this;
  }

  /**
   * Add multiple lines at once.
   *
   * @param texts  One or more strings to add as lines.
   */
  lines(...texts: readonly string[]): this {
    for (const text of texts) {
      this.line(text);
    }
    return this;
  }

  /**
   * Add a clickable option button.
   *
   * An option closes the dialogue and fires its handler.  If you want the
   * dialogue to stay open, call `player.openDialogue(nextDialogue)` from
   * inside the handler.
   *
   * @param text     Button label.
   * @param handler  Called when the player clicks this option.
   */
  option(text: string, handler: (player: Player) => void): this {
    assert(!this._frozen, "Cannot modify a frozen builder");
    assert(typeof text === "string" && text.length > 0,
      "Option text must be a non-empty string");
    assert(typeof handler === "function",
      "Option handler must be a function");
    this._options.push({ text, handler });
    return this;
  }

  /**
   * Add a "continue" button that closes the dialogue after firing an
   * optional handler.
   *
   * This is the standard way to end a monologue.  When a dialogue has a
   * `continue` and no `options`, the engine shows a single "Click to
   * continue" interaction.
   *
   * @param spec     Either a button label string, or a `{ text, handler }` object.
   */
  continue(spec: ContinueSpec): this {
    assert(!this._frozen, "Cannot modify a frozen builder");

    if (typeof spec === "string") {
      assert(spec.length > 0, "Continue text must be a non-empty string");
      this._continueText = spec;
      this._continueHandler = undefined;
    } else {
      assert(typeof spec.text === "string" && spec.text.length > 0,
        "Continue text must be a non-empty string");
      if (spec.handler !== undefined) {
        assert(typeof spec.handler === "function",
          "Continue handler must be a function");
      }
      this._continueText = spec.text;
      this._continueHandler = spec.handler;
    }
    return this;
  }

  /**
   * Build the final {@link Dialogue} object.
   *
   * The builder is frozen after calling this method; further modifications
   * will throw.
   *
   * @returns A frozen {@link Dialogue} ready for `player.openDialogue()`.
   */
  build(): Dialogue {
    assert(this._lines.length > 0 || this._options.length > 0,
      "Dialogue must have at least one line or one option");

    // If there is a continue button, wrap it as an option
    let allOptions: readonly DialogueOption[] = this._options;
    if (this._continueText !== null) {
      const continueOption: DialogueOption = {
        text: this._continueText,
        handler: this._continueHandler ?? ((_p: Player) => {}),
      };
      allOptions = [...this._options, continueOption];
    }

    const result: Dialogue = {
      type: this._type,
      title: this._title,
      lines: this._lines,
      options: allOptions.length > 0 ? allOptions : undefined,
    };

    this._frozen = true;
    return Object.freeze(result);
  }
}

/**
 * Create a new dialogue builder.
 *
 * This is the primary entry point for building any dialogue screen.
 *
 * @param type  The type of dialogue interface ("npc", "player", or "statement").
 * @returns A new {@link DialogueBuilder}.
 *
 * @example
 * ```ts
 * dialogue("npc")
 *   .title("Hans")
 *   .line("Hello, adventurer!")
 *   .option("Hello, Hans.", handler)
 *   .build();
 * ```
 */
export function dialogue(type: DialogueType): DialogueBuilder {
  return new DialogueBuilder(type);
}

// ─── Convenience functions ────────────────────────────────────────────────────

/**
 * Create a statement-type dialogue builder (no character model shown).
 *
 * Shorthand for `dialogue("statement")`.
 *
 * @example
 * ```ts
 * statement()
 *   .line("You have completed the tutorial.")
 *   .continue("Continue", handler)
 *   .build();
 * ```
 */
export function statement(): DialogueBuilder {
  return new DialogueBuilder("statement");
}

/**
 * Create an NPC-type dialogue builder.
 *
 * Shorthand for `dialogue("npc")`.
 *
 * @param npcName  Optional title (NPC name shown in the interface).
 *
 * @example
 * ```ts
 * npcDialogue("Wise Old Man")
 *   .line("Take this amulet.")
 *   .continue("Thank you.", handler)
 *   .build();
 * ```
 */
export function npcDialogue(npcName?: string): DialogueBuilder {
  const builder = new DialogueBuilder("npc");
  if (npcName) builder.title(npcName);
  return builder;
}

/**
 * Create a player-type dialogue builder.
 *
 * Shorthand for `dialogue("player")`.
 *
 * @example
 * ```ts
 * playerDialogue()
 *   .line("I think I should head north.")
 *   .continue("Let's go.", handler)
 *   .build();
 * ```
 */
export function playerDialogue(): DialogueBuilder {
  return new DialogueBuilder("player");
}

// ─── Multi-type chained dialogues ─────────────────────────────────────────────

/**
 * Extended builder that supports chaining multiple dialogue types
 * ("npc" / "player" / "statement") together into one conversation.
 *
 * Each `.thenNpc()`, `.thenPlayer()`, or `.thenStatement()` call closes
 * the current dialogue segment and starts a new one.  Line and option
 * calls apply to the segment currently being built.
 *
 * The final `.build()` returns an **array** of dialogues representing
 * the full conversation.  Content authors are responsible for wiring them
 * together with `player.openDialogue()` in the option handlers.
 *
 * @example
 * ```ts
 * chain("npc")
 *   .title("Eluned")
 *   .line("I have sensed a disturbance in the forest.")
 *   .thenPlayer()
 *   .line("What kind of disturbance?")
 *   .thenNpc("Eluned")
 *   .line("Dark creatures roam where they should not.")
 *   .line("I need you to investigate.")
 *   .thenPlayer()
 *   .line("I will help.")
 *   .build(); // → Dialogue[]
 * ```
 */
export class DialogueChain {
  private _segments: DialogueBuilder[] = [];

  /** Get the current (last) segment being built. */
  private current(): DialogueBuilder {
    assert(this._segments.length > 0,
      "Chain has no segments — start with one of the factory functions");
    return this._segments[this._segments.length - 1];
  }

  /** Start a new NPC segment. */
  thenNpc(title?: string): this {
    const builder = new DialogueBuilder("npc");
    if (title) builder.title(title);
    this._segments.push(builder);
    return this;
  }

  /** Start a new Player segment. */
  thenPlayer(): this {
    this._segments.push(new DialogueBuilder("player"));
    return this;
  }

  /** Start a new Statement segment. */
  thenStatement(): this {
    this._segments.push(new DialogueBuilder("statement"));
    return this;
  }

  /** @internal Initialise the first segment without checking current(). */
  _initSegment(type: DialogueType, title?: string): void {
    const builder = new DialogueBuilder(type);
    if (title) builder.title(title);
    this._segments.push(builder);
  }

  /** Set the title on the current segment. */
  title(text: string): this {
    this.current().title(text);
    return this;
  }

  /** Add a line to the current segment. */
  line(text: string): this {
    this.current().line(text);
    return this;
  }

  /** Add multiple lines to the current segment. */
  lines(...texts: readonly string[]): this {
    this.current().lines(...texts);
    return this;
  }

  /** Add an option to the current segment. */
  option(text: string, handler: (player: Player) => void): this {
    this.current().option(text, handler);
    return this;
  }

  /** Add a continue button to the current segment. */
  continue(spec: ContinueSpec): this {
    this.current().continue(spec);
    return this;
  }

  /** Build all segments into an array of {@link Dialogue} objects. */
  build(): readonly Dialogue[] {
    assert(this._segments.length > 0,
      "Chain has no segments");
    return Object.freeze(this._segments.map(s => s.build()));
  }
}

/**
 * Start a chained dialogue sequence at the given type.
 *
 * @param type  The type for the first segment ("npc", "player", "statement").
 * @returns A new {@link DialogueChain} with one segment.
 *
 * @example
 * ```ts
 * chain("npc").title("Guide").line("Hello!").build();
 * ```
 */
export function chain(type: DialogueType): DialogueChain {
  assert(VALID_DIALOGUE_TYPES.has(type),
    `Unknown dialogue type "${type}"`);
  const c = new DialogueChain();
  c._initSegment(type);
  return c;
}

/**
 * Start a chained dialogue with an NPC opening.
 */
export function chainNpc(title?: string): DialogueChain {
  const c = new DialogueChain();
  c._initSegment("npc", title);
  return c;
}

/**
 * Start a chained dialogue with the player opening.
 */
export function chainPlayer(): DialogueChain {
  const c = new DialogueChain();
  c._initSegment("player");
  return c;
}

/**
 * Start a chained dialogue with a statement opening.
 */
export function chainStatement(): DialogueChain {
  const c = new DialogueChain();
  c._initSegment("statement");
  return c;
}

// ─── Simple factory helpers ───────────────────────────────────────────────────

/**
 * Create a simple one-line message as a statement dialogue.
 *
 * @param message      The message to display.
 * @param buttonText   Continue button label (default "Continue").
 * @param handler      Optional handler called on close.
 * @returns A frozen {@link Dialogue}.
 */
export function simpleMessage(
  message: string,
  buttonText: string = "Continue",
  handler?: (player: Player) => void,
): Dialogue {
  return statement()
    .line(message)
    .continue({ text: buttonText, handler })
    .build();
}

/**
 * Create a two-option yes/no dialogue.
 *
 * @param question   The question text displayed to the player.
 * @param onYes      Handler for the "Yes" option.
 * @param onNo       Handler for the "No" option (no-op by default).
 * @param npcName    Optional NPC title.
 * @returns A frozen {@link Dialogue}.
 */
export function yesNoDialogue(
  question: string,
  onYes: (player: Player) => void,
  onNo?: (player: Player) => void,
  npcName?: string,
): Dialogue {
  const builder = dialogue("npc");
  if (npcName) builder.title(npcName);
  return builder
    .line(question)
    .option("Yes.", onYes)
    .option("No.", onNo ?? ((_p: Player) => {}))
    .build();
}
