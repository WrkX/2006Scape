/**
 * Runtime bridge types.
 *
 * These interfaces describe the Java wrapper objects that GraalJS passes to
 * executable NPC, object, and command handlers. This is the **only** player
 * surface available inside `onNpc` / `onObject` / `onItem` / lifecycle
 * callbacks. Do not type those handlers with the aspirational domain
 * {@link import("./player.js").Player} model — it is not what the host
 * injects.
 *
 * @module core/runtime
 */

import type { DefineArea } from "../areas/types.js";
import type { DefineQuest } from "../quests/types.js";
import type { DefineBoss } from "./boss.js";
import type { DefineRaid } from "./raid.js";
import type { DefineShop } from "./shop.js";
import type { ItemId } from "./types.js";

/** Immutable copied Java collection exposed to guest code. */
export interface ScriptArray<T> {
  length(): number;
  get(index: number): T | null;
}

/** Position wrapper exposed by the Java bridge. */
export interface ScriptedPosition {
  readonly x: number;
  readonly y: number;
  readonly plane: number;
}

/** Dialogue builder exposed through {@link ScriptedPlayer.getDialogue}. */
export interface ScriptedDialogue {
  npc(
    npcId: number,
    line: string,
    line2?: string,
    line3?: string,
    line4?: string,
  ): this;
  player(
    line: string,
    line2?: string,
    line3?: string,
    line4?: string,
  ): this;
  statement(
    line: string,
    line2?: string,
    line3?: string,
    line4?: string,
  ): this;
  /**
   * Show between two and five choices.
   *
   * The callback receives a zero-based index in the range `0..lines.length-1`.
   */
  options(lines: string[], callback: (choice: number) => void): this;
  itemDialogue(itemId: number, header: string, lines: string[]): this;
  end(): void;
}

/** Skill access exposed by the runtime player wrapper. */
export interface ScriptedSkills {
  getLevel(id: number): number;
  getCurrentLevel(id: number): number;
  getBaseLevel(id: number): number;
  getExperience(id: number): number;
  addExperience(id: number, amount: number): boolean;
  setLevel(id: number, level: number): void;
}

/** Inventory access exposed by the runtime player wrapper. */
export interface ScriptedInventory {
  add(id: ItemId, amount: number): boolean;
  canRemove(id: ItemId, amount: number): boolean;
  remove(id: ItemId, amount: number): boolean;
  has(id: ItemId, amount: number): boolean;
  count(id: ItemId): number;
  getCapacity(): number;
  getFreeSlots(): number;
}

/** Bank access exposed by the runtime player wrapper. */
export interface ScriptedBank {
  add(id: ItemId, amount: number): void;
  remove(id: ItemId, amount: number): boolean;
  has(id: ItemId, amount: number): boolean;
  count(id: ItemId): number;
  getCapacity(): number;
}

/** Opaque encounter-owned action or movement lock. */
export interface ScriptLockHandle {
  token(): string;
  isActive(): boolean;
  release(): boolean;
}

/** One guest-authored drop-table row; validated and copied by the Java parser. */
export interface ScriptDropEntry {
  readonly itemId: number;
  readonly minAmount: number;
  readonly maxAmount: number;
  readonly weight: number;
  readonly always: boolean;
}

/**
 * One canonical named drop-table row. String item ids resolve once at
 * candidate load through the exact item-name resolver; runtime
 * transactions use copied numeric ids only.
 */
export interface DropTableEntry {
  readonly itemId: number | string;
  readonly minAmount: number;
  readonly maxAmount: number;
  readonly weight: number;
  readonly always: boolean;
}

/** Immutable logical drop result; one handle owns the complete identity set. */
export interface ScriptDropResult {
  itemId(): number;
  amount(): number;
  groundItems(): ScriptArray<ScriptGroundItemHandle>;
}

/** Equipment slot names used by the Java runtime facade. */
export type RuntimeEquipmentSlot =
  | "hat" | "cape" | "amulet" | "weapon" | "chest" | "shield"
  | "legs" | "hands" | "feet" | "ring" | "arrows";
export type ScriptAudience = "self" | "nearby";

export interface ScriptCameraSession {
  token(): string;
  isActive(): boolean;
  position(localX: number, localY: number, height: number, speed: number, angle: number): boolean;
  lookAt(localX: number, localY: number, height: number, speed: number, angle: number): boolean;
  shake(axis: number, intensity: number, speed: number, frequency: number): boolean;
  release(): boolean;
}

export interface ScriptedEquipment {
  get(slot: RuntimeEquipmentSlot): number | null;
  amount(slot: RuntimeEquipmentSlot): number;
  /** Equip an inventory item by id through the host wear pipeline. */
  equip(itemId: number): boolean;
  /** Unequip the item in one canonical slot into the inventory. */
  unequip(slot: RuntimeEquipmentSlot): boolean;
  /**
   * Recalculated equipment bonus for one index (`0..11`: stab through prayer).
   */
  bonus(index: EquipmentBonusIndex): number;
  /** Display name for one equipment bonus index. */
  bonusName(index: EquipmentBonusIndex): string | null;
}

/** Equipment bonus indexes matching the legacy combat bonus array. */
export type EquipmentBonusIndex =
  | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11;

export interface ScriptedCombat {
  hp(): number;
  maxHp(): number;
  inCombat(): boolean;
  /** True when another entity has recently attacked this player. */
  underAttack(): boolean;
  /** True when poison damage or mask is active. */
  poisoned(): boolean;
  damage(amount: number): number;
  heal(amount: number): number;
}

/**
 * Spell rune and level checks keyed by client spell button ids
 * (`MagicData.MAGIC_SPELLS[i][0]`).
 */
export interface ScriptedMagic {
  /** Spell table index for a button id, or `-1` when unknown. */
  findIndex(spellButtonId: number): number;
  /** Silent inventory/staff rune check for the spell. */
  hasRunes(spellButtonId: number): boolean;
  /** Deletes runes for the spell when mutation is allowed. */
  consumeRunes(spellButtonId: number): boolean;
  /** Required magic level, or `-1` when the spell is unknown. */
  requiredLevel(spellButtonId: number): number;
  /** True when current magic meets the spell requirement. */
  hasLevel(spellButtonId: number): boolean;
}

/** Prayer book indexes 0..25 matching the legacy client prayer order. */
export interface ScriptedPrayer {
  isActive(prayer: number): boolean;
  activate(prayer: number): boolean;
  deactivate(prayer: number): boolean;
  deactivateAll(): boolean;
  name(prayer: number): string | null;
  requiredLevel(prayer: number): number;
}

/** WP3 action-lock capability. */
export interface ScriptedActions {
  lock(ticks: number): ScriptLockHandle | null;
}

/**
 * WP3 movement-lock capability.
 *
 * Movement operations are added by WP5; this interface intentionally exposes
 * only the Java methods callable in the current runtime.
 */
export interface ScriptedMovement {
  face(x: number, y: number): boolean;
  walkTo(x: number, y: number): boolean;
  teleport(x: number, y: number, plane: number): boolean;
  runEnergy(): number;
  setRunEnergy(amount: number): boolean;
  lock(ticks: number): ScriptLockHandle | null;
}

export interface ScriptedPresentation {
  animate(animationId: number, delay: number): boolean;
  graphic(graphicId: number, height: "low" | "high"): boolean;
  forcedChat(text: string): boolean;
  sound(soundId: number, volume: number, delay: number): boolean;
  showInterface(interfaceId: number): boolean;
  closeInterfaces(): boolean;
  setText(componentId: number, text: string): boolean;
  setItemModel(componentId: number, itemId: number, zoom: number): boolean;
  setConfig(configId: number, state: number): boolean;
  setChildHidden(componentId: number, hidden: boolean): boolean;
  openStaticShop(shopId: number): boolean;
  /**
   * Opens one Java-owned scripted shop definition by its stable string id.
   * The shop must be active in the current generation and the player must
   * be a valid live identity outside trade/duel.
   */
  openScriptShop(shopId: string): boolean;
  stillGraphic(graphicId: number, x: number, y: number, plane: number, height: number, delay: number, audience: ScriptAudience): boolean;
  projectile(graphicId: number, fromX: number, fromY: number, toX: number, toY: number, plane: number, angle: number, speed: number, startHeight: number, endHeight: number, delay: number, audience: ScriptAudience): boolean;
  beginCamera(ticks: number): ScriptCameraSession | null;
  resetCamera(): boolean;
}

/** WP3 encounter ownership and scheduling surface. */
export interface ScriptEncounterHandle {
  id(): string;
  owner(): ScriptedPlayer;
  isOpen(): boolean;
  addParticipant(player: ScriptedPlayer): boolean;
  removeParticipant(player: ScriptedPlayer): boolean;
  participants(): ScriptArray<ScriptedPlayer>;
  spawnNpc(
    npcId: number,
    x: number,
    y: number,
    plane: number,
    hp: number,
    maxHit: number,
    attack: number,
    defence: number,
  ): ScriptNpcHandle | null;
  onNpcDeath(
    npc: ScriptNpcHandle,
    callback: (context: EncounterNpcDeathScriptContext) => void,
  ): boolean;
  replaceObject(x: number, y: number, plane: number, expectedId: number, expectedType: number, expectedRotation: number, replacementId: number, replacementType: number, replacementRotation: number): ScriptObjectHandle | null;
  removeObject(x: number, y: number, plane: number, expectedId: number, expectedType: number, expectedRotation: number): ScriptObjectHandle | null;
  dropFor(player: ScriptedPlayer, itemId: number, amount: number, x: number, y: number, plane: number): ScriptGroundItemHandle | null;
  after(
    ticks: number,
    handler: ScheduledHandler,
  ): ScriptTaskHandle | null;
  every(
    ticks: number,
    handler: ScheduledHandler,
  ): ScriptTaskHandle | null;
  contains(x: number, y: number, plane: number): boolean;
  /** Bounded roll 0..bound-1; invalid bounds return -1 without advance. */
  nextInt(bound: number): number;
  /** Rational chance; invalid input returns false without advance. */
  chance(numerator: number, denominator: number): boolean;
  /**
   * Rolls every always entry and exactly one weighted entry as one
   * transaction; the returned results are detached private rewards.
   */
  rollDrops(
    player: ScriptedPlayer,
    x: number,
    y: number,
    plane: number,
    privateTicks: number,
    entries: readonly ScriptDropEntry[],
  ): ScriptArray<ScriptDropResult>;
  /** Chebyshev distance between two valid same-plane positions, else -1. */
  distance(first: ScriptedPosition, second: ScriptedPosition): number;
  /** Authoritative clipping result for a loaded in-area cell. */
  isWalkable(x: number, y: number, plane: number): boolean;
  /** Authoritative straight-line projectile clipping between two in-area cells. */
  hasProjectilePath(
    fromX: number,
    fromY: number,
    toX: number,
    toY: number,
    plane: number,
  ): boolean;
  close(): boolean;
}

/**
 * Narrow player wrapper available to executable runtime handlers.
 *
 * This is not the richer domain {@link import("./player.js").Player} model.
 */
export interface ScriptedPlayer {
  getUsername(): string;
  getX(): number;
  getY(): number;
  getPlane(): number;
  getCombatLevel(): number;
  getRights(): number;
  getPosition(): ScriptedPosition;
  message(text: string): void;
  teleport(x: number, y: number, plane?: number): void;
  getSkills(): ScriptedSkills;
  getInventory(): ScriptedInventory;
  getBank(): ScriptedBank;
  getDialogue(): ScriptedDialogue;
  animate(animationId: number): boolean;
  graphic(graphicId: number): boolean;
  sound(soundId: number): boolean;
  closeInterfaces(): void;
  showInterface(interfaceId: number): boolean;
  /** Run once after `ticks` game cycles. */
  after(ticks: number, handler: ScheduledHandler): ScriptTaskHandle;
  /** Run every `ticks` game cycles until cancelled or the callback throws. */
  every(ticks: number, handler: ScheduledHandler): ScriptTaskHandle;
  state(namespace: string): PlayerStateNamespace;
  quest(id: string): ScriptedQuest | null;
  questPoints(): number;
  getEquipment(): ScriptedEquipment;
  getCombat(): ScriptedCombat;
  getMagic(): ScriptedMagic;
  getPrayer(): ScriptedPrayer;
  getActions(): ScriptedActions;
  getMovement(): ScriptedMovement;
  getPresentation(): ScriptedPresentation;
  /** Opens the bank UI (bank-area and pin rules apply). */
  openBank(): boolean;
  beginEncounter(
    id: string,
    minX: number,
    minY: number,
    maxX: number,
    maxY: number,
    plane: number,
  ): ScriptEncounterHandle | null;
  /**
   * Grants the named reward through the shared player-local transaction.
   * The reward must be registered in the active generation; the result is
   * a narrow facade (reward id plus result code), never a registry map or
   * inventory array.
   */
  grantReward(rewardId: string): RewardGrantResult;
}

/** Closed wire union of one named-reward grant outcome. */
export type RewardGrantCode =
  | "rewarded"
  | "not_found"
  | "inventory_full"
  | "xp_cap"
  | "quest_points_overflow"
  | "reward_failed";

/** Narrow result-shaped facade of one named-reward grant. */
export interface RewardGrantResult {
  rewardId(): string;
  code(): RewardGrantCode;
}

/** Canonical schema-v1 named drop table passed to `defineDropTable`. */
export interface DropTableDefinition {
  readonly id: string;
  readonly entries: readonly DropTableEntry[];
}

/** Canonical schema-v1 named reward passed to `defineReward`. */
export interface RewardDefinition {
  readonly id: string;
  readonly items: readonly RewardItem[];
  readonly experience: readonly RewardExperience[];
  readonly questPoints: number;
  readonly state: readonly RewardStateMutation[];
}

/** Canonical schema-v1 gathering resource passed to `defineGatheringResource`. */
export interface GatheringResourceDefinition {
  readonly id: string;
  readonly name: string;
  /** Object target id; exactly one of objectId or npcId is required. */
  readonly objectId?: number;
  /** NPC target id for fishing-style spots; mutually exclusive with objectId. */
  readonly npcId?: number;
  readonly action: "first" | "second" | "third" | "fourth";
  readonly skill: string;
  readonly level: number;
  readonly tools: readonly GatheringResourceTool[];
  readonly animation: number;
  readonly intervalTicks: number;
  readonly successChance: {
    readonly numerator: number;
    readonly denominator: number;
  };
  readonly rewards: readonly GatheringResourceReward[];
  readonly experience: number;
  /** When false the session stays open after each success (fishing). Default true for objects. */
  readonly depletes?: boolean;
  readonly depletedObjectId?: number;
  readonly respawnTicks?: number;
}

/**
 * Canonical schema-v1 processing skill passed to `defineProcessingSkill`.
 *
 * Proven by the shrimp-on-range cooking port: input item on a cook object,
 * tick interval, burn-style success curve, product/XP on success, optional
 * fail product and cooking-gauntlet stop-burn override.
 */
export interface ProcessingSkillDefinition {
  readonly id: string;
  readonly name: string;
  readonly skill: string;
  readonly level: number;
  readonly inputItemId: number | string;
  readonly objectId: number;
  readonly productItemId: number | string;
  readonly failProductItemId?: number | string;
  readonly experience: number;
  readonly animation: number;
  readonly sound?: number;
  readonly intervalTicks: number;
  readonly stopBurnLevel: number;
  readonly stopBurnLevelWithGloves?: number;
  readonly glovesItemId?: number | string;
  readonly burnBonus?: number;
}

export interface GatheringResourceTool {
  readonly itemId: number | string;
  readonly consume?: boolean;
}

export interface GatheringResourceReward {
  readonly itemId: number | string;
  readonly amount: number;
}

export interface RewardItem {
  readonly id: number | string;
  readonly amount: number;
}

export interface RewardExperience {
  readonly skill: string;
  readonly amount: number;
}

export interface RewardStateMutation {
  readonly namespace: string;
  readonly key: string;
  readonly value: boolean | number | string;
}

export type DefineDropTable = (definition: DropTableDefinition) => void;
export type DefineReward = (definition: RewardDefinition) => void;
export type DefineGatheringResource = (
  definition: GatheringResourceDefinition,
) => void;

export type DefineProcessingSkill = (
  definition: ProcessingSkillDefinition,
) => void;

/**
 * Canonical schema-v1 world mob passed to `defineMob`.
 *
 * World-spawned NPCs replacing `NpcCombat` switch cases: declarative
 * aggression radius, combat style, attack speed, and max hit. Optional
 * callbacks override behavior beyond the Java-owned AI. Arena bosses use
 * `defineBoss` instead.
 */
export interface MobDefinition {
  readonly id: string;
  readonly npcId: number;
  readonly name?: string;
  /** Aggression radius in tiles; 0 means retaliate-only. */
  readonly aggression: number;
  readonly combatStyle: "melee" | "ranged" | "magic";
  /** Ticks between attacks. */
  readonly attackSpeed: number;
  readonly maxHit: number;
  /** Optional attack animation override; omit to use the cache emote. */
  readonly animation?: number;
  readonly onSpawn?: (ctx: MobRuntimeContext) => void;
  readonly onTick?: (ctx: MobRuntimeContext) => void;
  readonly onDeath?: (ctx: MobRuntimeContext) => void;
}

/**
 * Narrow runtime context for declarative world-mob callbacks.
 */
export interface MobRuntimeContext {
  id(): string;
  npcId(): number;
  position(): ScriptedPosition;
  hp(): number;
  maxHp(): number;
  alive(): boolean;
  /** Killer on death callbacks; null for spawn/tick. */
  killer(): ScriptedPlayer | null;
  say(text: string): boolean;
  face(x: number, y: number): boolean;
  animate(animationId: number, delay: number): boolean;
}

export type DefineMob = (definition: MobDefinition) => void;

/** Skill requirements for {@link ItemOverlayDefinition}. */
export interface ItemOverlayRequirements {
  readonly attack?: number;
  readonly strength?: number;
  readonly defence?: number;
  readonly hitpoints?: number;
  readonly ranged?: number;
  readonly prayer?: number;
  readonly magic?: number;
}

/** Combat bonuses for {@link ItemOverlayDefinition}. */
export interface ItemOverlayBonuses {
  readonly attackStab?: number;
  readonly attackSlash?: number;
  readonly attackCrush?: number;
  readonly attackMagic?: number;
  readonly attackRange?: number;
  readonly defenceStab?: number;
  readonly defenceSlash?: number;
  readonly defenceCrush?: number;
  readonly defenceMagic?: number;
  readonly defenceRange?: number;
  readonly strength?: number;
  readonly prayer?: number;
}

/**
 * Canonical schema-v1 item overlay passed to `defineItemOverlay`.
 *
 * Merges optional metadata and equipment stats over a loaded cache item id
 * at script activation. Requires the cache pack to include the target id.
 */
export interface ItemOverlayDefinition {
  readonly id: string;
  readonly itemId: number;
  readonly name?: string;
  readonly examine?: string;
  readonly stackable?: boolean;
  readonly equipSlot?: RuntimeEquipmentSlot;
  readonly requirements?: ItemOverlayRequirements;
  readonly bonuses?: ItemOverlayBonuses;
}

/**
 * Canonical schema-v1 NPC overlay passed to `defineNpcOverlay`.
 */
export interface NpcOverlayDefinition {
  readonly id: string;
  readonly npcId: number;
  readonly name?: string;
  readonly combatLevel?: number;
  readonly hitpoints?: number;
}

/**
 * Canonical schema-v1 object overlay passed to `defineObjectOverlay`.
 */
export interface ObjectOverlayDefinition {
  readonly id: string;
  readonly objectId: number;
  readonly name?: string;
  readonly examine?: string;
  /** Up to five menu actions; sparse indices are allowed. */
  readonly actions?: readonly string[];
}

export type DefineItemOverlay = (definition: ItemOverlayDefinition) => void;
export type DefineNpcOverlay = (definition: NpcOverlayDefinition) => void;
export type DefineObjectOverlay = (definition: ObjectOverlayDefinition) => void;

/**
 * Canonical schema-v1 interface hook passed to `defineInterfaceHook`.
 *
 * Button handlers run only while the hook's interface is the player's main
 * frame. Use presentation helpers in {@link onOpen} to populate text.
 */
export interface InterfaceHookDefinition {
  readonly id: string;
  readonly interfaceId: number;
  readonly buttons?: Readonly<Record<number, (context: ButtonScriptContext) => void>>;
  readonly onOpen?: (context: InterfaceHookScriptContext) => void;
  readonly onClose?: (context: InterfaceHookScriptContext) => void;
}

/** Lifecycle context for {@link InterfaceHookDefinition} open/close hooks. */
export interface InterfaceHookScriptContext {
  readonly player: ScriptedPlayer;
  readonly target: null;
  readonly action: "open" | "close";
  readonly interfaceId: number;
  readonly hookId: string;
}

export type DefineInterfaceHook = (definition: InterfaceHookDefinition) => void;

export interface PlayerStateNamespace {
  has(key: string): boolean;
  getBoolean(key: string): boolean | null;
  getBooleanOr(key: string, fallback: boolean): boolean;
  setBoolean(key: string, value: boolean): boolean;
  getNumber(key: string): number | null;
  getNumberOr(key: string, fallback: number): number;
  setNumber(key: string, value: number): boolean;
  getString(key: string): string | null;
  getStringOr(key: string, fallback: string): string;
  setString(key: string, value: string): boolean;
  remove(key: string): boolean;
}

export type ScriptQuestState = "not_started" | "in_progress" | "completed";

export type QuestResultCode =
  | "can_start"
  | "started"
  | "already_completed"
  | "already_started"
  | "requirements_not_met"
  | "state_failed"
  | "not_in_progress"
  | "stage_mismatch"
  | "invalid_stage"
  | "advanced"
  | "not_final_stage"
  | "quest_points_overflow"
  | "inventory_full"
  | "xp_cap"
  | "reward_failed"
  | "completed";

export interface QuestResult {
  ok(): boolean;
  changed(): boolean;
  code(): QuestResultCode;
}

export interface ScriptedQuest {
  id(): string;
  name(): string;
  summary(): string;
  state(): ScriptQuestState;
  stage(): number | null;
  objective(): string | null;
  canStart(): QuestResult;
  start(): QuestResult;
  setStage(expectedCurrent: number, nextStage: number): QuestResult;
  advance(expectedCurrent: number): QuestResult;
  complete(expectedFinalStage: number): QuestResult;
}

/** Cancellation surface passed to scheduled callbacks. */
export interface ScriptTaskHandle {
  cancel(): boolean;
  isCancelled(): boolean;
}

export type ScheduledHandler = (handle: ScriptTaskHandle) => void;

/** Defensive item-definition wrapper exposed to item handlers. */
export interface ScriptedItem {
  getId(): number;
  getName(): string;
  isStackable(): boolean;
  isNoted(): boolean;
}

/** NPC wrapper used as the target of an NPC interaction. */
export interface ScriptedNpc {
  getId(): number;
  getName(): string;
  getX(): number;
  getY(): number;
  getPlane(): number;
  getHp(): number;
  getMaxHp(): number;
  isDead(): boolean;
  getCombatLevel(): number;
  forceChat(text: string): void;
  getPosition(): ScriptedPosition;
}

/** Immutable snapshot exposed by encounter-owned NPC death callbacks. */
export interface ScriptNpcSnapshot {
  id(): number;
  name(): string;
  position(): ScriptedPosition;
  maxHp(): number;
}

export type GraphicHeight = "low" | "high";

/** Capability handle for one exact encounter-owned NPC allocation. */
export interface ScriptNpcHandle {
  token(): string;
  id(): number;
  position(): ScriptedPosition;
  hp(): number;
  maxHp(): number;
  isAlive(): boolean;
  setTarget(player: ScriptedPlayer): boolean;
  clearTarget(): boolean;
  face(x: number, y: number): boolean;
  walkTo(x: number, y: number): boolean;
  damage(amount: number, source: ScriptedPlayer | null): number;
  heal(amount: number): number;
  animate(animationId: number, delay: number): boolean;
  graphic(graphicId: number, height: GraphicHeight): boolean;
  forcedChat(text: string): boolean;
  despawn(): boolean;
}

/** Object wrapper used as the target of an object interaction. */
export interface ScriptedObject {
  getId(): number;
  getName(): string;
  getX(): number;
  getY(): number;
  getPlane(): number;
  getType(): number;
  getRotation(): number;
  getPosition(): ScriptedPosition;
}

export interface ScriptObjectHandle {
  token(): string;
  id(): number;
  position(): ScriptedPosition;
  type(): number;
  rotation(): number;
  isActive(): boolean;
  remove(): boolean;
}

export interface ScriptGroundItemHandle {
  token(): string;
  id(): number;
  amount(): number;
  position(): ScriptedPosition;
  identityCount(): number;
  isAttached(): boolean;
  isClaimed(): boolean;
  detach(privateTicks: number): boolean;
  remove(): boolean;
}

/** The single argument passed to every executable runtime handler. */
export interface ScriptContext<
  TTarget = unknown,
  TAction extends string = string,
> {
  readonly player: ScriptedPlayer;
  readonly target: TTarget;
  readonly action: TAction;
}

/** NPC interaction slots currently dispatched by the engine. */
export type NpcAction = "first" | "second" | "third";

export type NpcScriptContext = ScriptContext<
  ScriptedNpc | null,
  NpcAction
>;

export interface CommandScriptContext extends ScriptContext<null, string> {
  getName(): string;
  getRawInput(): string;
  getArguments(): readonly string[];
  getRights(): number;
}

export type ItemAction = "first" | "second" | "third";

export interface ItemClickScriptContext
  extends ScriptContext<ScriptedItem, ItemAction> {
  readonly item: ScriptedItem;
  readonly slot: number;
}

export interface ItemOnItemScriptContext
  extends ScriptContext<ScriptedItem, "item-on-item"> {
  readonly usedItem: ScriptedItem;
  readonly usedSlot: number;
  readonly targetItem: ScriptedItem;
  readonly targetSlot: number;
}

export interface ItemOnObjectScriptContext
  extends ScriptContext<ScriptedObject, "item-on-object"> {
  readonly item: ScriptedItem;
  readonly slot: number;
}

export interface ItemOnNpcScriptContext
  extends ScriptContext<ScriptedNpc, "item-on-npc"> {
  readonly item: ScriptedItem;
  readonly slot: number;
}

export interface LoginScriptContext extends ScriptContext<null, "login"> {}

export interface LogoutScriptContext extends ScriptContext<null, "logout"> {}

export interface NpcDeathScriptContext {
  readonly npc: ScriptedNpc;
  readonly killer: ScriptedPlayer | null;
  readonly position: ScriptedPosition;
  readonly action: "death";
}

export interface EncounterNpcDeathScriptContext {
  readonly encounter: ScriptEncounterHandle;
  readonly npc: ScriptNpcSnapshot;
  readonly killer: ScriptedPlayer | null;
  readonly position: ScriptedPosition;
  readonly action: "encounter-npc-death";
}

export interface ItemPickupScriptContext
  extends ScriptContext<ScriptedItem, "pickup"> {
  readonly item: ScriptedItem;
  readonly amount: number;
  readonly position: ScriptedPosition;
}

/** Inclusive rectangular boundary. Omit `plane` to match every plane. */
export interface ScriptAreaDescriptor {
  readonly id: string;
  readonly minX: number;
  readonly minY: number;
  readonly maxX: number;
  readonly maxY: number;
  readonly plane?: number;
}

export interface ScriptArea {
  getId(): string;
  getMinX(): number;
  getMinY(): number;
  getMaxX(): number;
  getMaxY(): number;
  getPlane(): number | null;
}

export interface AreaTransitionScriptContext
  extends ScriptContext<ScriptArea, "enter" | "leave"> {
  readonly area: ScriptArea;
  readonly from: ScriptedPosition;
  readonly to: ScriptedPosition;
}

export interface ScriptedGroundItemView {
  token(): string;
  id(): number;
  amount(): number;
  position(): ScriptedPosition;
  isPrivateToPlayer(): boolean;
}

export interface ScriptPlayerSnapshot {
  username(): string;
  position(): ScriptedPosition;
  combatLevel(): number;
  rights(): number;
}

export interface ButtonScriptContext {
  readonly player: ScriptedPlayer;
  readonly target: null;
  readonly action: "button";
  readonly buttonId: number;
}

export interface ItemOnGroundItemScriptContext {
  readonly player: ScriptedPlayer;
  readonly target: ScriptedGroundItemView;
  readonly action: "item-on-ground-item";
  readonly item: ScriptedItem;
  readonly slot: number;
}

export interface ItemOnPlayerScriptContext {
  readonly player: ScriptedPlayer;
  readonly target: ScriptedPlayer;
  readonly action: "item-on-player";
  readonly item: ScriptedItem;
  readonly slot: number;
}

export interface MagicOnItemScriptContext {
  readonly player: ScriptedPlayer;
  readonly target: ScriptedItem;
  readonly action: "magic-on-item";
  readonly spellId: number;
  readonly slot: number;
}

export interface MagicOnObjectScriptContext {
  readonly player: ScriptedPlayer;
  readonly target: ScriptedObject;
  readonly action: "magic-on-object";
  readonly spellId: number;
}

export interface MagicOnNpcScriptContext {
  readonly player: ScriptedPlayer;
  readonly target: ScriptedNpc;
  readonly action: "magic-on-npc";
  readonly spellId: number;
}

export interface MagicOnPlayerScriptContext {
  readonly player: ScriptedPlayer;
  readonly target: ScriptedPlayer;
  readonly action: "magic-on-player";
  readonly spellId: number;
}

export interface PlayerDeathScriptContext {
  readonly player: ScriptPlayerSnapshot;
  readonly killer: ScriptPlayerSnapshot | null;
  readonly position: ScriptedPosition;
  readonly action: "death";
}

export type OnNpc = (
  npcId: number,
  action: NpcAction,
  handler: (context: NpcScriptContext) => void,
) => void;

export type OnCommand = (
  name: string,
  handler: (context: CommandScriptContext) => void,
) => void;

export type OnItem = (
  itemId: number,
  action: ItemAction,
  handler: (context: ItemClickScriptContext) => void,
) => void;

export type OnItemOnItem = (
  firstItemId: number,
  secondItemId: number,
  handler: (context: ItemOnItemScriptContext) => void,
) => void;

export type OnItemOnObject = (
  itemId: number,
  objectId: number,
  handler: (context: ItemOnObjectScriptContext) => void,
) => void;

export type OnItemOnNpc = (
  itemId: number,
  npcId: number,
  handler: (context: ItemOnNpcScriptContext) => void,
) => void;

export type OnLogin = (
  handler: (context: LoginScriptContext) => void,
) => void;

export type OnLogout = (
  handler: (context: LogoutScriptContext) => void,
) => void;

export type OnNpcDeath = (
  npcId: number,
  handler: (context: NpcDeathScriptContext) => void,
) => void;

export type OnItemPickup = (
  itemId: number,
  handler: (context: ItemPickupScriptContext) => void,
) => void;

export type OnAreaTransition = (
  area: ScriptAreaDescriptor,
  handler: (context: AreaTransitionScriptContext) => void,
) => void;

export type OnButton = (
  buttonId: number,
  handler: (context: ButtonScriptContext) => void,
) => void;

export type OnItemOnGroundItem = (
  itemId: number,
  groundItemId: number,
  handler: (context: ItemOnGroundItemScriptContext) => void,
) => void;

export type OnItemOnPlayer = (
  itemId: number,
  handler: (context: ItemOnPlayerScriptContext) => void,
) => void;

export type OnMagicOnItem = (
  spellId: number,
  itemId: number,
  handler: (context: MagicOnItemScriptContext) => void,
) => void;

export type OnMagicOnObject = (
  spellId: number,
  objectId: number,
  handler: (context: MagicOnObjectScriptContext) => void,
) => void;

export type OnMagicOnNpc = (
  spellId: number,
  npcId: number,
  handler: (context: MagicOnNpcScriptContext) => void,
) => void;

export type OnMagicOnPlayer = (
  spellId: number,
  handler: (context: MagicOnPlayerScriptContext) => void,
) => void;

export type OnPlayerDeath = (
  handler: (context: PlayerDeathScriptContext) => void,
) => void;

/**
 * Logical content-module envelope accepted by the Java module registrar.
 *
 * The id is a bounded logical identifier (never a host path) and
 * `schemaVersion` is the declared schema version of every definition the
 * module registers. Optional `onLoad`/`onUnload` hooks run as contained,
 * non-vetoing observers around the activation commit.
 */
export interface ContentModuleDescriptor {
  readonly id: string;
  readonly schemaVersion: number;
  readonly onLoad?: () => void;
  readonly onUnload?: () => void;
}

export type RegisterContentModule = (
  descriptor: ContentModuleDescriptor,
  scope: () => void,
) => void;

declare global {
  const onNpc: OnNpc;
  const onCommand: OnCommand;
  const onItem: OnItem;
  const onItemOnItem: OnItemOnItem;
  const onItemOnObject: OnItemOnObject;
  const onItemOnNpc: OnItemOnNpc;
  const onLogin: OnLogin;
  const onLogout: OnLogout;
  const onNpcDeath: OnNpcDeath;
  const onItemPickup: OnItemPickup;
  const onEnterArea: OnAreaTransition;
  const onLeaveArea: OnAreaTransition;
  const onButton: OnButton;
  const onItemOnGroundItem: OnItemOnGroundItem;
  const onItemOnPlayer: OnItemOnPlayer;
  const onMagicOnItem: OnMagicOnItem;
  const onMagicOnObject: OnMagicOnObject;
  const onMagicOnNpc: OnMagicOnNpc;
  const onMagicOnPlayer: OnMagicOnPlayer;
  const onPlayerDeath: OnPlayerDeath;
  const registerContentModule: RegisterContentModule;
  const defineBoss: DefineBoss;
  const defineQuest: DefineQuest;
  const defineRaid: DefineRaid;
  const defineArea: DefineArea;
  const defineShop: DefineShop;
  const defineDropTable: DefineDropTable;
  const defineReward: DefineReward;
  const defineGatheringResource: DefineGatheringResource;
  const defineProcessingSkill: DefineProcessingSkill;
  const defineMob: DefineMob;
  const defineItemOverlay: DefineItemOverlay;
  const defineNpcOverlay: DefineNpcOverlay;
  const defineObjectOverlay: DefineObjectOverlay;
  const defineInterfaceHook: DefineInterfaceHook;
}
