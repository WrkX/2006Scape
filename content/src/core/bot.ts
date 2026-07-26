/**
 * Bot system type definitions.
 *
 * Simulated players (bots) share the same {@link Player} interface as human
 * players — only their action source differs.  A {@link BotBrain} drives a
 * {@link SimulatedPlayer} through goal selection, pathfinding, and activity
 * execution.
 *
 * @module core/bot
 */

import type { Player } from "./player.js";
import type {
  WorldPoint,
  ItemId,
  SkillId,
  Equipment,
  EquipmentSlot,
} from "./types.js";

// ─── SimulatedPlayer ──────────────────────────────────────────────────────

/**
 * A non-human player driven by a {@link BotBrain}.
 *
 * Extends {@link Player} with bot-specific introspection and control hooks
 * that content scripts and bot brains use without any network/client
 * dependency.
 */
export interface SimulatedPlayer extends Player {
  /** The brain currently controlling this bot. */
  readonly brain: BotBrain;

  /** Current activity the bot is performing (or null if idle). */
  readonly currentActivity: Activity | null;

  /** Whether the bot is currently actively executing a task. */
  readonly isActive: boolean;

  /** Pause the bot (stops ticking). */
  pause(): void;

  /** Resume a paused bot. */
  resume(): void;

  /** Force the bot to stop its current activity immediately. */
  stopActivity(): void;
}

// ─── BotBrain ──────────────────────────────────────────────────────────────

/** Top-level coordinator for a simulated player. */
export interface BotBrain {
  /** The player this brain controls. */
  readonly player: SimulatedPlayer;

  /** The goal selector (decides what to do next). */
  readonly goalSelector: GoalSelector;

  /** Navigation / pathfinding. */
  readonly navigation: Navigation;

  /** Bank planning and routing. */
  readonly bankPlanner: BankPlanner;

  /** Economy decisions (buy/sell, price evaluation). */
  readonly economyPlanner: EconomyPlanner;

  /** All activities available to this bot. */
  readonly activities: ActivityRegistry;

  /** Called every tick by the engine. */
  tick(): void;

  /** Set the bot to pursue a specific goal. */
  setGoal(goal: Goal): void;

  /** Clear the current goal (bot becomes idle). */
  clearGoal(): void;

  /** Log a message visible in bot debug output. */
  log(message: string): void;
}

// ─── GoalSelector ─────────────────────────────────────────────────────────

/**
 * Priority-ordered set of goals.  The selector evaluates conditions top to
 * bottom and picks the first satisfied goal.
 */
export interface GoalSelector {
  /** The ordered list of candidate goals. */
  readonly goals: readonly Goal[];

  /** Evaluate all goals and return the highest-priority satisfied one. */
  evaluate(): Goal | null;

  /** Add a goal to the end of the priority list. */
  add(goal: Goal): void;

  /** Remove a goal by id. */
  remove(goalId: string): void;
}

/**
 * A single goal the bot may pursue.
 *
 * Goals are evaluated in priority order; the first one whose
 * {@link condition} returns true wins.
 */
export interface Goal {
  /** Unique identifier for this goal. */
  readonly id: string;

  /** Human-readable description (for debug). */
  readonly label: string;

  /**
   * Priority: lower numbers are evaluated first.
   * Typical range 0 (highest) to 100 (lowest).
   */
  readonly priority: number;

  /**
   * Called each tick to check whether this goal should activate.
   * @returns true if the bot should pursue this goal right now.
   */
  readonly condition: (player: SimulatedPlayer) => boolean;

  /**
   * Called when this goal becomes active.
   * @returns The {@link Activity} to execute.
   */
  readonly action: (player: SimulatedPlayer) => Activity;
}

// ─── Navigation ────────────────────────────────────────────────────────────

/** Pathfinding and movement for bots. */
export interface Navigation {
  /** Current target the bot is walking towards, or null. */
  readonly target: WorldPoint | null;

  /** Whether the bot is currently moving. */
  readonly isMoving: boolean;

  /**
   * Walk to a destination.
   * @param destination The target location.
   * @returns true if a path was found and movement started.
   */
  walkTo(destination: WorldPoint): boolean;

  /**
   * Walk to and interact with a specific object.
   * @param objectId The object id.
   * @returns true if a path was found and movement started.
   */
  walkToObject(objectId: number): boolean;

  /**
   * Walk to and interact with a specific NPC.
   * @param npcId The NPC id.
   * @returns true if a path was found and movement started.
   */
  walkToNpc(npcId: number): boolean;

  /** Cancel current movement. */
  cancel(): void;

  /** Distance in tiles to the current target (or Infinity). */
  distanceTo(target: WorldPoint): number;

  /** Check whether the bot can reach the given point. */
  canReach(target: WorldPoint): boolean;
}

// ─── BankPlanner ───────────────────────────────────────────────────────────

/** High-level bank routing logic. */
export interface BankPlanner {
  /** Nearest bank location the bot knows about. */
  readonly nearestBank: WorldPoint | null;

  /**
   * Walk to the nearest bank and open it.
   * @returns true if the bank was successfully opened.
   */
  goToBank(): boolean;

  /**
   * Deposit specific items into the bank.
   * @param items Items to deposit (id and optional amount).
   * @returns true when all requested items are deposited.
   */
  deposit(items: readonly { id: ItemId; amount?: number }[]): boolean;

  /**
   * Withdraw specific items from the bank.
   * @param items Items to withdraw.
   * @returns true when all requested items are in inventory.
   */
  withdraw(items: readonly { id: ItemId; amount?: number }[]): boolean;

  /** Whether the bot is currently at a bank interface. */
  readonly isBankOpen: boolean;
}

// ─── EconomyPlanner ────────────────────────────────────────────────────────

/** High-level economy decisions (buy/sell on GE, price evaluation). */
export interface EconomyPlanner {
  /**
   * Decide whether to sell an item based on current market conditions.
   * @param itemId The item to evaluate.
   * @returns A sell decision or null if the bot should hold.
   */
  evaluateSell(itemId: ItemId): SellDecision | null;

  /**
   * Decide whether to buy an item.
   * @param itemId The item to evaluate.
   * @returns A buy decision or null if the bot should not buy.
   */
  evaluateBuy(itemId: ItemId): BuyDecision | null;

  /**
   * Get the current market price for an item (cached).
   * @param itemId The item to price.
   * @returns The estimated price, or -1 if unknown.
   */
  getPrice(itemId: ItemId): number;

  /**
   * List items the bot currently wants to buy (ordered by priority).
   */
  readonly buyList: readonly ItemId[];

  /**
   * List items the bot currently wants to sell (ordered by priority).
   */
  readonly sellList: readonly ItemId[];
}

/** A sell decision returned by {@link EconomyPlanner.evaluateSell}. */
export interface SellDecision {
  readonly itemId: ItemId;
  readonly amount: number;
  readonly minPrice: number;
}

/** A buy decision returned by {@link EconomyPlanner.evaluateBuy}. */
export interface BuyDecision {
  readonly itemId: ItemId;
  readonly amount: number;
  readonly maxPrice: number;
}

// ─── Activity System ──────────────────────────────────────────────────────

/**
 * Discriminated union of all activity types a bot can execute.
 *
 * Each variant carries configuration specific to that activity.
 */
export type Activity =
  | MiningActivity
  | FishingActivity
  | WoodcuttingActivity
  | CombatActivity
  | SlayerActivity
  | TradingActivity
  | PkingActivity;

/** Discriminator for activity types. */
export type ActivityKind = Activity["kind"];

/** Lookup table of activities by kind. */
export type ActivityRegistry = {
  readonly [K in ActivityKind]: Activity & { kind: K };
};

/**
 * Base interface for all bot activities.
 *
 * Activities encapsulate a single repeatable task — mine rocks, fish spots,
 * kill NPCs, etc.
 */
export interface ActivityBase {
  /** Unique discriminator. */
  readonly kind: string;

  /** Human-readable label for debug. */
  readonly label: string;

  /**
   * Called once when the activity starts.
   * Use to walk to the area, gear up, etc.
   */
  readonly onStart: (player: SimulatedPlayer) => void;

  /**
   * Called every tick while this activity is active.
   * @returns A signal to the brain: continue, wait, or stop.
   */
  readonly onTick: (player: SimulatedPlayer) => ActivitySignal;

  /**
   * Called when the activity is interrupted or completes naturally.
   */
  readonly onStop: (player: SimulatedPlayer) => void;

  /** Whether this activity requires a specific tool or equipment set. */
  readonly requiredItems?: readonly ItemId[];

  /** Optional skill requirement to perform this activity. */
  readonly requiredLevel?: { readonly skill: SkillId; readonly level: number };
}

/** A signal from an activity tick to the bot brain. */
export type ActivitySignal =
  | { readonly signal: "continue" }
  | { readonly signal: "wait" }
  | { readonly signal: "stop"; readonly reason: string }
  | { readonly signal: "switch"; readonly nextGoal: string };

// ─── Concrete Activity Types ───────────────────────────────────────────────

/** Mining activity configuration. */
export interface MiningActivity extends ActivityBase {
  readonly kind: "mining";
  /** Rock object ids to mine, in priority order. */
  readonly rockIds: readonly number[];
  /** Ore received per successful mine. */
  readonly ore: ItemId;
  /** Minimum pickaxe tier required (e.g. "rune"). */
  readonly pickaxeTier?: string;
}

/** Fishing activity configuration. */
export interface FishingActivity extends ActivityBase {
  readonly kind: "fishing";
  /** Fishing spot NPC ids to target. */
  readonly spotIds: readonly number[];
  /** Fish caught. */
  readonly fish: ItemId;
  /** Fishing method (e.g. "net", "harpoon", "cage"). */
  readonly method?: "net" | "bait" | "lure" | "harpoon" | "cage";
  /** Required tool item. */
  readonly tool?: ItemId;
}

/** Woodcutting activity configuration. */
export interface WoodcuttingActivity extends ActivityBase {
  readonly kind: "woodcutting";
  /** Tree object ids, in priority order (normal → oak → willow → ...). */
  readonly treeIds: readonly number[];
  /** Logs produced. */
  readonly log: ItemId;
  /** Minimum axe tier required. */
  readonly axeTier?: string;
}

/** Combat activity configuration. */
export interface CombatActivity extends ActivityBase {
  readonly kind: "combat";
  /** NPC ids to target. */
  readonly npcIds: readonly number[];
  /** Combat style to use. */
  readonly style: CombatStyle;
  /**
   * Food item id to eat when health drops below {@link eatThreshold}.
   * If omitted the bot will not heal itself.
   */
  readonly foodId?: ItemId;
  /** HP percentage at which to eat food (default 0.5 = 50%). */
  readonly eatThreshold?: number;
  /** Whether to loot drops from killed NPCs. */
  readonly lootDrops: boolean;
  /** If true, bank all loot instead of keeping it. */
  readonly bankLoot: boolean;
}

/** Combat style selection. */
export type CombatStyle = "melee" | "ranged" | "magic";

/** Slayer activity configuration. */
export interface SlayerActivity extends ActivityBase {
  readonly kind: "slayer";
  /** Slayer master NPC id for task assignment. */
  readonly masterId: number;

  /**
   * Specific NPC ids to kill for the current task.
   * Populated by the slayer master interaction.
   */
  readonly taskNpcIds?: readonly number[];

  /** Food / gear preferences (same semantics as {@link CombatActivity}). */
  readonly foodId?: ItemId;
  readonly eatThreshold?: number;
}

/** Trading activity configuration. */
export interface TradingActivity extends ActivityBase {
  readonly kind: "trading";
  /** Items to sell. */
  readonly sellItems: readonly ItemId[];
  /** Items to buy. */
  readonly buyItems: readonly ItemId[];
  /** Trade location (e.g. GE area). */
  readonly tradeLocation: WorldPoint;
}

/** PKing (player killing) activity configuration. */
export interface PkingActivity extends ActivityBase {
  readonly kind: "pking";
  /** Combat style. */
  readonly style: CombatStyle;
  /** Wilderness level range to patrol. */
  readonly wildernessLevelMin: number;
  readonly wildernessLevelMax: number;
  /** Food for healing. */
  readonly foodId: ItemId;
  readonly eatThreshold: number;
  /** Gear set to use. */
  readonly gear: readonly EquipmentSlot[];
}
