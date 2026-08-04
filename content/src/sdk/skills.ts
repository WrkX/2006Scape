/**
 * Canonical script skill names.
 *
 * The Java quest and reward parsers accept exactly these 21 lower-case
 * names and map them to the legacy engine skill indexes
 * (`com.rs2.script.quest.QuestSkill`). The SDK keeps one authoritative
 * table so requirement predicates and reward builders validate against the
 * same names the engine accepts.
 *
 * @module sdk/skills
 */

/** Canonical skill name accepted by script definitions. */
export type ScriptSkillName =
  | "attack" | "defence" | "strength" | "hitpoints" | "ranged"
  | "prayer" | "magic" | "cooking" | "woodcutting" | "fletching"
  | "fishing" | "firemaking" | "crafting" | "smithing" | "mining"
  | "herblore" | "agility" | "thieving" | "slayer" | "farming"
  | "runecraft";

/** The 21 canonical skill names in legacy engine index order. */
export const SCRIPT_SKILLS: readonly ScriptSkillName[] = [
  "attack", "defence", "strength", "hitpoints", "ranged", "prayer",
  "magic", "cooking", "woodcutting", "fletching", "fishing", "firemaking",
  "crafting", "smithing", "mining", "herblore", "agility", "thieving",
  "slayer", "farming", "runecraft",
] as const;

const SKILL_INDEX: ReadonlyMap<string, number> = new Map(
  SCRIPT_SKILLS.map((name, index) => [name, index] as const),
);

/**
 * Resolve one canonical skill name to its legacy engine skill index
 * (`0..20`), or throw a bounded diagnostic for any other name.
 *
 * @param name  The lower-case script skill name.
 * @returns The engine skill index.
 */
export function skillIndex(name: string): number {
  const index = SKILL_INDEX.get(name);
  if (index === undefined) {
    throw new Error(
      `[sdk/skills] unknown skill "${name}": expected one of ` +
        SCRIPT_SKILLS.join(", "),
    );
  }
  return index;
}

/**
 * True when {@link name} is one of the 21 canonical script skill names.
 */
export function isScriptSkill(name: string): name is ScriptSkillName {
  return SKILL_INDEX.has(name);
}
