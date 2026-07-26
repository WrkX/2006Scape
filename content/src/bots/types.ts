/**
 * Bot system type barrel.
 *
 * Re-exports bot-related types for content authors working in the bots/
 * directory.
 *
 * @module bots/types
 */

export type {
  SimulatedPlayer,
  BotBrain,
  GoalSelector,
  Goal,
  Navigation,
  BankPlanner,
  EconomyPlanner,
  SellDecision,
  BuyDecision,
  Activity,
  ActivityKind,
  ActivityRegistry,
  ActivityBase,
  ActivitySignal,
  MiningActivity,
  FishingActivity,
  WoodcuttingActivity,
  CombatActivity,
  CombatStyle,
  SlayerActivity,
  TradingActivity,
  PkingActivity,
} from "../core/bot.js";
