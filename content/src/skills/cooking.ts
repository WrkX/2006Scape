/**
 * Processing skill module for cooking.
 *
 * Shrimp on cooking range 114 via {@link registerProcessingSkill}. Only this
 * item+object pair is scripted; other foods and cook spots keep legacy Java.
 *
 * @module skills/cooking
 */

import { registerModule, registerProcessingSkill } from "../sdk/index.js";

registerModule({ id: "cooking-skills", schemaVersion: 1 }, () => {
  /**
   * Raw shrimps (item 317) on a cooking range (object 114). Level 1, 30 XP,
   * burn stop at 34 (30 with cooking gauntlets). The Java processing runtime
   * cooks one shrimp every four ticks until the inventory runs out.
   */
  registerProcessingSkill({
    id: "cook-shrimp-range",
    name: "shrimp",
    skill: "cooking",
    level: 1,
    inputItemId: 317,
    objectId: 114,
    productItemId: 315,
    failProductItemId: 7954,
    experience: 30,
    animation: 896,
    sound: 357,
    intervalTicks: 4,
    stopBurnLevel: 34,
    stopBurnLevelWithGloves: 30,
    glovesItemId: 775,
  });
});
