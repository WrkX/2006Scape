/**
 * Immutable data accepted by the Java-owned quest registry.
 */

import type { SkillId } from "../core/types.js";

export interface QuestStage {
  readonly stage: number;
  readonly objective: string;
}

export interface QuestSkillRequirement {
  readonly skill: SkillId;
  readonly level: number;
}

export interface QuestItemAmount {
  readonly itemId: number;
  readonly amount: number;
}

export interface QuestExperienceReward {
  readonly skill: SkillId;
  readonly amount: number;
}

export interface QuestRequirements {
  readonly questPoints?: number;
  readonly completedQuests?: readonly string[];
  readonly skills?: readonly QuestSkillRequirement[];
  readonly items?: readonly QuestItemAmount[];
}

export interface QuestRewards {
  readonly questPoints?: number;
  readonly items?: readonly QuestItemAmount[];
  readonly experience?: readonly QuestExperienceReward[];
}

export interface QuestDefinition {
  readonly id: string;
  readonly name: string;
  readonly summary: string;
  readonly stages: readonly QuestStage[];
  readonly requirements?: QuestRequirements;
  readonly rewards?: QuestRewards;
}

export type DefineQuest = (definition: QuestDefinition) => void;

declare global {
  const defineQuest: DefineQuest;
}
