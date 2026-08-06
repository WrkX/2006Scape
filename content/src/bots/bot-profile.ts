/**
 * Bot profile creation helpers — factory functions and builders for
 * configuring simulated players with goals, activities, equipment, and
 * behavioural presets.
 *
 * **Not wired to the Java bridge.** Profiles build aspirational
 * SimulatedPlayer configs only; they do not spawn or tick bots on the live
 * server. Prefer real ScriptedPlayer content APIs for shipped gameplay.
 *
 * @module bots/bot-profile
 *
 * @example Creating a skiller bot
 * ```ts
 * import { createSkiller } from "./bot-profile.js";
 *
 * const wcBot = createSkiller("willow_chopper", {
 *   username: "WC_Bot_1",
 *   location: { x: 3000, y: 3500, plane: 0 },
 *   skill: "woodcutting",
 *   targetLevel: 99,
 *   treeIds: [1, 2, 3], // normal, oak, willow
 *   logItem: "willow_logs",
 *   axeTier: "rune",
 *   bankWhenFull: true,
 * });
 * ```
 *
 * @example Creating a PvM bot
 * ```ts
 * import { createPvmer } from "./bot-profile.js";
 *
 * const greenDragonBot = createPvmer("dragon_slayer_bot", {
 *   username: "DS_Bot_1",
 *   location: { x: 3100, y: 4900, plane: 0 },
 *   npcIds: [50], // green dragons
 *   combatStyle: "melee",
 *   foodId: "lobster",
 *   eatThreshold: 0.4,
 *   lootDrops: true,
 *   bankLoot: true,
 *   gearList: ["weapon", "body", "legs", "shield"],
 * });
 * ```
 *
 * @example Full bot profile builder
 * ```ts
 * import { botProfile } from "./bot-profile.js";
 *
 * const profile = botProfile("elite_pvmer")
 *   .username("EliteBot")
 *   .location({ x: 3000, y: 5000, plane: 0 })
 *   .combatLevel(120)
 *   .goal({
 *     id: "slayer_task",
 *     label: "Slayer Task",
 *     priority: 0,
 *     condition: (p) => p.inventory.contains("slayer_gem"),
 *     action: (p) => ({ kind: "slayer", ... }),
 *   })
 *   .goal({
 *     id: "bank_loot",
 *     label: "Bank Loot",
 *     priority: 10,
 *     condition: (p) => p.inventory.size >= 24,
 *     action: () => ({ kind: "trading", ... }),
 *   })
 *   .equipment({ weapon: "abyssal_whip", body: "bandos_chestplate" })
 *   .inventory("lobster", 20)
 *   .inventory("prayer_potion", 4)
 *   .build();
 * ```
 */

import type {
  SimulatedPlayer,
  Goal,
  Activity,
  CombatStyle,
} from "../core/bot.js";
import type {
  WorldPoint,
  ItemId,
  SkillId,
  EquipmentSlot,
} from "../core/types.js";

// ─── Internal helpers ─────────────────────────────────────────────────────────

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(`[bot-profile] ${message}`);
  }
}

// ─── Bot profile definition ───────────────────────────────────────────────────

/**
 * Complete configuration for a bot profile.
 *
 * This is the internal representation — the engine consumes this to
 * instantiate a {@link SimulatedPlayer} with the listed goals, activities,
 * and initial state.
 */
export interface BotProfile {
  /** Unique identifier for this profile (used for referencing in dev tools). */
  readonly id: string;

  /** Display name the bot will use in-game. */
  readonly username: string;

  /** World coordinates where the bot spawns. */
  readonly location: WorldPoint;

  /** Goals the bot pursues, in priority order (lowest priority = highest urgency). */
  readonly goals: readonly Goal[];

  /** Equipment to equip on spawn. */
  readonly equipment: Partial<Record<EquipmentSlot, ItemId>>;

  /** Items to place in the bot's inventory on spawn. */
  readonly inventory: readonly { id: ItemId; amount: number }[];

  /** If provided, set the bot's combat level. */
  readonly combatLevel?: number;

  /** If provided, set skill levels on spawn. */
  readonly skillLevels?: Partial<Record<SkillId, number>>;

  /** Optional description for documentation / debug purposes. */
  readonly description?: string;
}

// ─── Profile builder ──────────────────────────────────────────────────────────

/**
 * Fluent builder for constructing a {@link BotProfile}.
 *
 * Accumulates goals, equipment, inventory, and skills through chainable
 * methods, then validates and freezes the result.
 *
 * @example
 * ```ts
 * botProfile("my_bot")
 *   .username("MyBot")
 *   .location({ x: 0, y: 0, plane: 0 })
 *   .goal(combatGoal)
 *   .goal(bankingGoal)
 *   .equip("weapon", "rune_scimitar")
 *   .inventory("lobster", 15)
 *   .build();
 * ```
 */
export class BotProfileBuilder {
  private _id: string;
  private _username: string | null = null;
  private _location: WorldPoint | null = null;
  private _goals: Goal[] = [];
  private _goalIds: Set<string> = new Set();
  private _equipment: Partial<Record<EquipmentSlot, ItemId>> = {};
  private _inventory: { id: ItemId; amount: number }[] = [];
  private _combatLevel: number | undefined;
  private _skillLevels: Partial<Record<SkillId, number>> = {};
  private _description: string | undefined;

  constructor(id: string) {
    assert(typeof id === "string" && id.length > 0,
      "Bot profile id must be a non-empty string");
    this._id = id;
  }

  /** Set the in-game display name. */
  username(name: string): this {
    assert(typeof name === "string" && name.length > 0 &&
      name.length <= 12,
      "Bot username must be 1-12 characters");
    this._username = name;
    return this;
  }

  /** Set the world spawn location. */
  location(point: WorldPoint): this {
    assert(point !== undefined &&
      Number.isFinite(point.x) && Number.isFinite(point.y) &&
      Number.isFinite(point.plane ?? 0),
      "Bot location must have finite x, y, and plane values");
    this._location = point;
    return this;
  }

  /** Set the bot's combat level. */
  combatLevel(level: number): this {
    assert(Number.isInteger(level) && level >= 3 && level <= 126,
      `Bot combat level must be 3-126, got ${level}`);
    this._combatLevel = level;
    return this;
  }

  /** Add a goal to the bot's priority list. */
  goal(g: Goal): this {
    assert(typeof g.id === "string" && g.id.length > 0,
      "Goal must have a non-empty id");
    assert(!this._goalIds.has(g.id),
      `Duplicate goal id "${g.id}"`);
    assert(typeof g.label === "string" && g.label.length > 0,
      `Goal "${g.id}": label must be a non-empty string`);
    assert(typeof g.priority === "number" && g.priority >= 0,
      `Goal "${g.id}": priority must be >= 0, got ${g.priority}`);
    assert(typeof g.condition === "function",
      `Goal "${g.id}": condition must be a function`);
    assert(typeof g.action === "function",
      `Goal "${g.id}": action must be a function`);

    this._goalIds.add(g.id);
    this._goals.push(g);

    // Keep goals sorted by priority (ascending)
    this._goals.sort((a, b) => a.priority - b.priority);

    return this;
  }

  /** Equip an item to a specific slot. */
  equip(slot: EquipmentSlot, item: ItemId): this {
    const validSlots: ReadonlySet<string> = new Set([
      "head", "cape", "neck", "weapon", "body", "shield",
      "legs", "hands", "feet", "ring", "ammo",
    ]);
    assert(validSlots.has(slot),
      `Unknown equipment slot "${slot}"`);
    assert(
      (typeof item === "number" && item > 0) ||
        (typeof item === "string" && item.length > 0),
      `equip: invalid item id for slot "${slot}"`,
    );
    this._equipment[slot] = item;
    return this;
  }

  /** Set multiple equipment slots at once. */
  equipment(gear: Partial<Record<EquipmentSlot, ItemId>>): this {
    for (const [slot, item] of Object.entries(gear)) {
      this.equip(slot as EquipmentSlot, item as ItemId);
    }
    return this;
  }

  /** Add items to the bot's starting inventory. */
  inventory(id: ItemId, amount: number = 1): this {
    assert(
      (typeof id === "number" && id > 0) ||
        (typeof id === "string" && id.length > 0),
      "inventory: invalid item id",
    );
    assert(Number.isInteger(amount) && amount >= 1,
      `inventory: amount must be >= 1, got ${amount}`);
    this._inventory.push({ id, amount });
    return this;
  }

  /** Set a skill level for the bot on spawn. */
  skillLevel(skill: SkillId, level: number): this {
    const validSkills: ReadonlySet<string> = new Set([
      "attack", "defence", "strength", "hitpoints", "ranged", "prayer",
      "magic", "cooking", "woodcutting", "fletching", "fishing", "firemaking",
      "crafting", "smithing", "mining", "herblore", "agility", "thieving",
      "slayer", "farming", "runecraft",
    ]);
    assert(validSkills.has(skill),
      `Unknown skill "${skill}"`);
    assert(Number.isInteger(level) && level >= 1 && level <= 99,
      `skillLevel: "${skill}" must be 1-99, got ${level}`);
    this._skillLevels[skill] = level;
    return this;
  }

  /** Set a description for documentation. */
  description(text: string): this {
    this._description = text;
    return this;
  }

  /** Build the validated {@link BotProfile}. */
  build(): BotProfile {
    assert(this._username !== null,
      "Bot username is required (call .username())");
    assert(this._location !== null,
      "Bot location is required (call .location())");

    return Object.freeze({
      id: this._id,
      username: this._username!,
      location: this._location!,
      goals: this._goals,
      equipment: this._equipment,
      inventory: this._inventory,
      combatLevel: this._combatLevel,
      skillLevels: Object.keys(this._skillLevels).length > 0
        ? this._skillLevels : undefined,
      description: this._description,
    });
  }
}

/**
 * Entry point for the bot profile builder.
 *
 * @param id  Unique profile identifier.
 * @returns A new {@link BotProfileBuilder}.
 */
export function botProfile(id: string): BotProfileBuilder {
  return new BotProfileBuilder(id);
}

// ─── Archetype factories ──────────────────────────────────────────────────────

/**
 * Configuration for creating a skilling bot.
 */
export interface SkillerConfig {
  /** In-game display name. */
  readonly username: string;

  /** World spawn location. */
  readonly location: WorldPoint;

  /** The skill this bot trains. */
  readonly skill: SkillId;

  /** The target level (bot stops when reached). */
  readonly targetLevel: number;

  /** Object/NPC ids to interact with for this skill. */
  readonly objectIds: readonly number[];

  /** The resource item produced. */
  readonly resourceItem: ItemId;

  /** Optional tool tier required (e.g. "rune"). */
  readonly toolTier?: string;

  /** Whether to bank resources when inventory is full. */
  readonly bankWhenFull: boolean;

  /** Optional unique profile id override. */
  readonly id?: string;
}

/**
 * Configuration for creating a skilling bot (legacy variant with specific fields).
 * @deprecated Use {@link SkillerConfig} with `createSkiller` instead.
 */
export interface LegacySkillerConfig {
  readonly username: string;
  readonly location: WorldPoint;
  readonly skill: SkillId;
  readonly targetLevel: number;
  readonly treeIds?: readonly number[];
  readonly logItem?: ItemId;
  readonly axeTier?: string;
  readonly rockIds?: readonly number[];
  readonly ore?: ItemId;
  readonly pickaxeTier?: string;
  readonly spotIds?: readonly number[];
  readonly fish?: ItemId;
  readonly method?: "net" | "bait" | "lure" | "harpoon" | "cage";
  readonly tool?: ItemId;
  readonly bankWhenFull: boolean;
  readonly id?: string;
}

function inferObjectIds(config: LegacySkillerConfig): {
  objectIds: readonly number[];
  resourceItem: ItemId;
  toolTier?: string;
  kind: string;
} {
  if (config.treeIds && config.logItem) {
    return {
      objectIds: config.treeIds,
      resourceItem: config.logItem,
      toolTier: config.axeTier,
      kind: "woodcutting",
    };
  }
  if (config.rockIds && config.ore) {
    return {
      objectIds: config.rockIds,
      resourceItem: config.ore,
      toolTier: config.pickaxeTier,
      kind: "mining",
    };
  }
  if (config.spotIds && config.fish) {
    return {
      objectIds: config.spotIds,
      resourceItem: config.fish,
      kind: "fishing",
    };
  }
  throw new Error(
    "[bot-profile] createSkiller: must provide treeIds+logItem, rockIds+ore, or spotIds+fish",
  );
}

/**
 * Create a bot profile for a skilling bot.
 *
 * Supports woodcutting, mining, and fishing through the legacy config
 * or the generic {@link SkillerConfig}.
 *
 * @param id      Profile identifier.
 * @param config  Skiller configuration.
 * @returns A validated {@link BotProfile}.
 */
export function createSkiller(
  id: string,
  config: LegacySkillerConfig | SkillerConfig,
): BotProfile {
  const usedId = config.id ?? id;

  // Handle modern SkillerConfig
  if ("objectIds" in config && "resourceItem" in config) {
    const sc = config as SkillerConfig;
    return botProfile(usedId)
      .username(sc.username)
      .location(sc.location)
      .skillLevel(sc.skill, sc.targetLevel > 40 ? 40 : 1)
      .goal({
        id: "train_skill",
        label: `Train ${sc.skill} to ${sc.targetLevel}`,
        priority: 0,
        condition: (p) => p.skills.getBase(sc.skill) < sc.targetLevel,
        action: (_p) => {
          // The engine maps this goal to the appropriate activity
          // based on skill type.  We return a minimal activity stub.
          return {
            kind: "woodcutting",
            label: `Cutting ${String(sc.resourceItem)}`,
            treeIds: sc.objectIds,
            log: sc.resourceItem,
            axeTier: sc.toolTier,
            onStart: () => {},
            onTick: () => ({ signal: "continue" }),
            onStop: () => {},
          } as Activity;
        },
      })
      .goal({
        id: "bank_resources",
        label: "Bank resources",
        priority: 10,
        condition: (p) => sc.bankWhenFull && p.inventory.size >= 27,
        action: (_p) => ({
          kind: "trading",
          label: "Banking",
          sellItems: [sc.resourceItem],
          buyItems: [],
          tradeLocation: { x: 0, y: 0, plane: 0 },
          onStart: () => {},
          onTick: () => ({ signal: "continue" }),
          onStop: () => {},
        } as Activity),
      })
      .build();
  }

  // Handle legacy config
  const legacy = config as LegacySkillerConfig;
  const { objectIds, resourceItem, toolTier, kind } = inferObjectIds(legacy);

  return botProfile(usedId)
    .username(legacy.username)
    .location(legacy.location)
    .skillLevel(legacy.skill, legacy.targetLevel > 40 ? 40 : 1)
    .goal({
      id: "train_skill",
      label: `Train ${legacy.skill} to ${legacy.targetLevel}`,
      priority: 0,
      condition: (p) => p.skills.getBase(legacy.skill) < legacy.targetLevel,
      action: (_p) => ({
        kind,
        label: `Training ${legacy.skill}`,
        treeIds: kind === "woodcutting" ? objectIds : undefined,
        rockIds: kind === "mining" ? objectIds : undefined,
        spotIds: kind === "fishing" ? objectIds : undefined,
        log: kind === "woodcutting" ? resourceItem : undefined,
        ore: kind === "mining" ? resourceItem : undefined,
        fish: kind === "fishing" ? resourceItem : undefined,
        axeTier: kind === "woodcutting" ? toolTier : undefined,
        pickaxeTier: kind === "mining" ? toolTier : undefined,
        method: kind === "fishing" ? legacy.method : undefined,
        tool: kind === "fishing" ? legacy.tool : undefined,
        onStart: () => {},
        onTick: () => ({ signal: "continue" }),
        onStop: () => {},
      } as Activity),
    })
    .goal({
      id: "bank_resources",
      label: "Bank resources",
      priority: 10,
      condition: (p) => legacy.bankWhenFull && p.inventory.size >= 27,
      action: (_p) => ({
        kind: "trading",
        label: "Banking",
        sellItems: [resourceItem],
        buyItems: [],
        tradeLocation: { x: 0, y: 0, plane: 0 },
        onStart: () => {},
        onTick: () => ({ signal: "continue" }),
        onStop: () => {},
      } as Activity),
    })
    .build();
}

// ─── PvM bot ──────────────────────────────────────────────────────────────────

/**
 * Configuration for creating a PvM combat bot.
 */
export interface PvmerConfig {
  readonly username: string;
  readonly location: WorldPoint;
  readonly npcIds: readonly number[];
  readonly combatStyle: CombatStyle;
  readonly foodId?: ItemId;
  readonly eatThreshold?: number;
  readonly lootDrops: boolean;
  readonly bankLoot: boolean;
  readonly gearList?: readonly EquipmentSlot[];
  readonly id?: string;
}

/**
 * Create a bot profile for a PvM combat bot.
 *
 * @param id      Profile identifier.
 * @param config  PvM bot configuration.
 * @returns A validated {@link BotProfile}.
 */
export function createPvmer(
  id: string,
  config: PvmerConfig,
): BotProfile {
  const usedId = config.id ?? id;
  const eatThreshold = config.eatThreshold ?? 0.5;

  return botProfile(usedId)
    .username(config.username)
    .location(config.location)
    .combatLevel(60)
    .goal({
      id: "combat",
      label: `Kill NPCs`,
      priority: 0,
      condition: (_p) => true,
      action: (_p) => ({
        kind: "combat",
        label: "Combat",
        npcIds: config.npcIds,
        style: config.combatStyle,
        foodId: config.foodId,
        eatThreshold,
        lootDrops: config.lootDrops,
        bankLoot: config.bankLoot,
        onStart: () => {},
        onTick: () => ({ signal: "continue" }),
        onStop: () => {},
      } as Activity),
    })
    .goal({
      id: "bank_loot",
      label: "Bank loot when full",
      priority: 10,
      condition: (p) => config.bankLoot && p.inventory.size >= 26,
      action: (_p) => ({
        kind: "trading",
        label: "Banking loot",
        sellItems: [],
        buyItems: [],
        tradeLocation: { x: 0, y: 0, plane: 0 },
        onStart: () => {},
        onTick: () => ({ signal: "continue" }),
        onStop: () => {},
      } as Activity),
    })
    .goal({
      id: "eat_food",
      label: "Eat food when low HP",
      priority: 5,
      condition: (p) => config.foodId !== undefined && p.hpPercent < eatThreshold,
      action: (_p) => ({
        kind: "combat",
        label: "Healing",
        npcIds: config.npcIds,
        style: config.combatStyle,
        foodId: config.foodId,
        eatThreshold,
        lootDrops: config.lootDrops,
        bankLoot: config.bankLoot,
        onStart: () => {},
        onTick: () => ({ signal: "continue" }),
        onStop: () => {},
      } as Activity),
    })
    .build();
}

// ─── Merchant bot ─────────────────────────────────────────────────────────────

/**
 * Configuration for creating a merchant / trading bot.
 */
export interface MerchantConfig {
  readonly username: string;
  readonly location: WorldPoint;
  readonly buyItems: readonly ItemId[];
  readonly sellItems: readonly ItemId[];
  readonly tradeLocation: WorldPoint;
  readonly id?: string;
}

/**
 * Create a bot profile for a merchant bot that buys and sells items.
 *
 * @param id      Profile identifier.
 * @param config  Merchant configuration.
 * @returns A validated {@link BotProfile}.
 */
export function createMerchant(
  id: string,
  config: MerchantConfig,
): BotProfile {
  const usedId = config.id ?? id;

  return botProfile(usedId)
    .username(config.username)
    .location(config.location)
    .goal({
      id: "trade",
      label: "Buy and sell items",
      priority: 0,
      condition: (_p) => true,
      action: (_p) => ({
        kind: "trading",
        label: "Trading",
        sellItems: config.sellItems,
        buyItems: config.buyItems,
        tradeLocation: config.tradeLocation,
        onStart: () => {},
        onTick: () => ({ signal: "continue" }),
        onStop: () => {},
      } as Activity),
    })
    .build();
}

// ─── PKer bot ─────────────────────────────────────────────────────────────────

/**
 * Configuration for creating a PKing bot.
 */
export interface PkerConfig {
  readonly username: string;
  readonly location: WorldPoint;
  readonly combatStyle: CombatStyle;
  readonly wildernessLevelMin: number;
  readonly wildernessLevelMax: number;
  readonly foodId: ItemId;
  readonly eatThreshold: number;
  readonly gear: readonly EquipmentSlot[];
  readonly id?: string;
}

/**
 * Create a bot profile for a player-killing bot.
 *
 * @param id      Profile identifier.
 * @param config  PKer configuration.
 * @returns A validated {@link BotProfile}.
 */
export function createPker(
  id: string,
  config: PkerConfig,
): BotProfile {
  const usedId = config.id ?? id;

  return botProfile(usedId)
    .username(config.username)
    .location(config.location)
    .combatLevel(100)
    .goal({
      id: "pk",
      label: "Player killing",
      priority: 0,
      condition: (_p) => true,
      action: (_p) => ({
        kind: "pking",
        label: "PKing",
        style: config.combatStyle,
        wildernessLevelMin: config.wildernessLevelMin,
        wildernessLevelMax: config.wildernessLevelMax,
        foodId: config.foodId,
        eatThreshold: config.eatThreshold,
        gear: config.gear,
        onStart: () => {},
        onTick: () => ({ signal: "continue" }),
        onStop: () => {},
      } as Activity),
    })
    .goal({
      id: "eat_food",
      label: "Eat food when low HP",
      priority: 5,
      condition: (p) => p.hpPercent < config.eatThreshold,
      action: (_p) => ({
        kind: "pking",
        label: "Healing",
        style: config.combatStyle,
        wildernessLevelMin: config.wildernessLevelMin,
        wildernessLevelMax: config.wildernessLevelMax,
        foodId: config.foodId,
        eatThreshold: config.eatThreshold,
        gear: config.gear,
        onStart: () => {},
        onTick: () => ({ signal: "continue" }),
        onStop: () => {},
      } as Activity),
    })
    .goal({
      id: "bank_loot",
      label: "Bank PK loot",
      priority: 15,
      condition: (p) => p.inventory.size >= 24,
      action: (_p) => ({
        kind: "trading",
        label: "Banking PK loot",
        sellItems: [],
        buyItems: [],
        tradeLocation: { x: 0, y: 0, plane: 0 },
        onStart: () => {},
        onTick: () => ({ signal: "continue" }),
        onStop: () => {},
      } as Activity),
    })
    .build();
}
