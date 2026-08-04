/**
 * Shared primitives used across the entire SingleScape TypeScript SDK.
 * These types model the foundational game concepts: position, items, skills,
 * equipment, and quest state.
 *
 * @module core/types
 */

// ─── Coordinates ───────────────────────────────────────────────────────────

/** A position in the game world. Plane 0 is the default ground plane. */
export interface WorldPoint {
  readonly x: number;
  readonly y: number;
  /** Defaults to 0 (ground level). */
  readonly plane: number;
}

/** Axis-aligned rectangular region in world coordinates. */
export interface WorldRegion {
  readonly northWest: WorldPoint;
  readonly southEast: WorldPoint;
}

// ─── Item ──────────────────────────────────────────────────────────────────

/**
 * An item identifier — either a numeric game ID or a named string constant.
 * String constants ("dragon_token") are the preferred authoring form; the
 * bridge resolves them to numeric IDs at load time.
 */
export type ItemId = number | string;

/** A stack of a single item type. */
export interface ItemStack {
  readonly id: ItemId;
  readonly amount: number;
}

// ─── Inventory & Bank ──────────────────────────────────────────────────────

/** The player's carried inventory (28 slots). */
export interface Inventory {
  /** Number of occupied slots. */
  readonly size: number;
  /** Total capacity (typically 28). */
  readonly capacity: number;

  /** True when at least one slot is free. */
  hasSpace(amount?: number): boolean;

  /** Count how many of a given item are held. */
  count(id: ItemId): number;

  /** Add item(s). Returns true on success. */
  add(id: ItemId, amount?: number): boolean;

  /** Remove item(s). Returns true on success. */
  remove(id: ItemId, amount?: number): boolean;

  /** Check whether the player holds at least `amount` of the item. */
  contains(id: ItemId, amount?: number): boolean;

  /** Iterate over occupied slots. */
  items(): Iterable<ItemStack>;
}

/** The player's bank (hundreds of slots). */
export interface Bank {
  /** Number of occupied slots. */
  readonly size: number;
  /** Total capacity. */
  readonly capacity: number;

  /** Count how many of a given item are banked. */
  count(id: ItemId): number;

  /** Add item(s) to the bank. Returns true on success. */
  add(id: ItemId, amount?: number): boolean;

  /** Remove item(s) from the bank. Returns true on success. */
  remove(id: ItemId, amount?: number): boolean;

  /** Check whether the bank holds at least `amount` of the item. */
  contains(id: ItemId, amount?: number): boolean;

  /** Iterate over occupied slots. */
  items(): Iterable<ItemStack>;
}

// ─── Equipment ─────────────────────────────────────────────────────────────

/** Equipment slot identifier. */
export type EquipmentSlot =
  | "head"
  | "cape"
  | "neck"
  | "weapon"
  | "body"
  | "shield"
  | "legs"
  | "hands"
  | "feet"
  | "ring"
  | "ammo";

/** Maps each equipment slot to the item equipped there (or null). */
export type Equipment = {
  readonly [slot in EquipmentSlot]: ItemId | null;
} & {
  /** Equip an item from inventory. Returns true on success. */
  equip(id: ItemId): boolean;

  /** Unequip a slot into inventory (must have space). Returns true on success. */
  unequip(slot: EquipmentSlot): boolean;

  /** Check what is equipped in a given slot. */
  get(slot: EquipmentSlot): ItemId | null;
};

// ─── Skills ────────────────────────────────────────────────────────────────

/**
 * Canonical skill identifiers.
 * Uses the OSRS-era numbering convention for compatibility with 2006-era caches.
 */
export type SkillId =
  | "attack"
  | "defence"
  | "strength"
  | "hitpoints"
  | "ranged"
  | "prayer"
  | "magic"
  | "cooking"
  | "woodcutting"
  | "fletching"
  | "fishing"
  | "firemaking"
  | "crafting"
  | "smithing"
  | "mining"
  | "herblore"
  | "agility"
  | "thieving"
  | "slayer"
  | "farming"
  | "runecraft";

/** The stat block for a single skill. */
export interface SkillStat {
  /** Current level (may be boosted/drained). */
  readonly current: number;
  /** Base level from experience. */
  readonly base: number;
  /** Total experience in this skill. */
  readonly experience: number;
}

/** Read-only view of the player's skills. */
export type Skills = {
  readonly [skill in SkillId]: SkillStat;
} & {
  /** Get the level used for requirement checks (the base level). */
  getBase(skill: SkillId): number;
  /** Get the current (boosted/drained) level. */
  getCurrent(skill: SkillId): number;
  /** Get total experience. */
  getExperience(skill: SkillId): number;
  /** Total level across all skills. */
  readonly totalLevel: number;
};

// ─── Quest ─────────────────────────────────────────────────────────────────

/** Progression state for a single quest. */
export type QuestState = "not_started" | "in_progress" | "completed";

/** A single quest entry indexed by string quest id. */
export interface QuestEntry {
  readonly id: string;
  readonly state: QuestState;
  /** Arbitrary progress data set by quest scripts. */
  readonly stage: number;
}

/** Container that tracks quest completion. */
export interface Quests {
  /** Look up a quest by its string id. */
  get(id: string): QuestEntry | undefined;

  /** True if the quest is fully completed. */
  hasCompleted(id: string): boolean;

  /** True if the quest has been started (or completed). */
  hasStarted(id: string): boolean;

  /** Get the current numeric stage. */
  getStage(id: string): number;

  /** Iterate over all known quest entries. */
  all(): Iterable<QuestEntry>;
}

// ─── Dialogue ──────────────────────────────────────────────────────────────

/** A single dialogue option shown to the player. */
export interface DialogueOption {
  readonly text: string;
  readonly handler: (player: import("./player.js").Player) => void;
}

/** Flavour of dialogue interface. */
export type DialogueType = "npc" | "player" | "statement";

/** Payload sent when opening a dialogue. */
export interface Dialogue {
  readonly type: DialogueType;
  readonly title?: string;
  readonly lines: readonly string[];
  readonly options?: readonly DialogueOption[];
}

// ─── NPC ───────────────────────────────────────────────────────────────────

/** Describes an NPC placed in an area. */
export interface NpcSpawn {
  readonly id: number;
  readonly x: number;
  readonly y: number;
  readonly plane?: number;
  /** Radius in tiles the NPC may wander. */
  readonly walkRadius?: number;
  /** Direction the NPC faces on spawn. */
  readonly direction?: CardinalDirection;
  /** Optional interaction handler. */
  readonly onInteract?: NpcInteractionHandler;
}

export type NpcInteractionHandler = (
  player: import("./player.js").Player,
  npc: NpcSpawn,
) => void;

export type CardinalDirection = "north" | "south" | "east" | "west";

// ─── Shop ──────────────────────────────────────────────────────────────────

/** A single item for sale in a shop. */
export interface ShopEntry {
  readonly id: ItemId;
  readonly amount: number;
  readonly price: number;
  /** Optional restock delay in game ticks. */
  readonly restockTicks?: number;
}

/** A persistent or area-bound shop. */
export interface Shop {
  readonly id: string;
  readonly name: string;
  readonly items: readonly ShopEntry[];
  /** If true the shop is shared across all players (general store). */
  readonly shared: boolean;
}

// ─── Discord-like Status ──────────────────────────────────────────────────

/** Convenience discriminated union for results that may fail. */
export type Result<T, E = string> =
  | { readonly ok: true; readonly value: T }
  | { readonly ok: false; readonly error: E };
