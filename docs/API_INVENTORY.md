# SingleScape TypeScript SDK — API Inventory

> Generated from the TypeScript sources by
> `content/scripts/api-inventory.mjs` — do not edit by hand.
> `pnpm --filter @singlescape/content test` fails when this
> document is stale.

## Runtime globals installed by the Java bridge

Every global below is provided by the host at runtime and is
declared for the compiler by the listed module. Content never
imports these names; it calls them directly.

| Global | Declared in |
|--------|-------------|
| `defineArea` | `core/runtime.ts` |
| `defineBoss` | `core/runtime.ts` |
| `defineDropTable` | `core/runtime.ts` |
| `defineGatheringResource` | `core/runtime.ts` |
| `defineQuest` | `core/runtime.ts` |
| `defineRaid` | `core/runtime.ts` |
| `defineReward` | `core/runtime.ts` |
| `defineShop` | `core/runtime.ts` |
| `dev` | `core/dev.ts` |
| `log` | `core/dev.ts` |
| `onButton` | `core/runtime.ts` |
| `onCommand` | `core/runtime.ts` |
| `onEnterArea` | `core/runtime.ts` |
| `onItem` | `core/runtime.ts` |
| `onItemOnGroundItem` | `core/runtime.ts` |
| `onItemOnItem` | `core/runtime.ts` |
| `onItemOnNpc` | `core/runtime.ts` |
| `onItemOnObject` | `core/runtime.ts` |
| `onItemOnPlayer` | `core/runtime.ts` |
| `onItemPickup` | `core/runtime.ts` |
| `onLeaveArea` | `core/runtime.ts` |
| `onLogin` | `core/runtime.ts` |
| `onLogout` | `core/runtime.ts` |
| `onMagicOnItem` | `core/runtime.ts` |
| `onMagicOnObject` | `core/runtime.ts` |
| `onNpc` | `core/runtime.ts` |
| `onNpcDeath` | `core/runtime.ts` |
| `onObject` | `core/object.ts` |
| `onPlayerDeath` | `core/runtime.ts` |
| `registerContentModule` | `core/runtime.ts` |

## Public SDK barrel (`content/src/sdk/index.ts`)

| Module | Exports |
|--------|---------|
| `areas/area-builder.ts` | createArea, registerArea |
| `areas/types.ts` | AreaBounds, AreaDefinition, AreaNpcSpawn, AreaObject, AreaObjectDrop, DefineArea |
| `bosses/boss-builder.ts` | BossOptions, createBoss, registerBoss |
| `core/boss.ts` | BossArena, BossCleanupPolicy, BossDefinition, BossEntryTeleport, BossObjectEntry, BossPhase, BossRuntimeContext, BossSpawn, BossSpecial, BossSpecials, DefineBoss |
| `core/raid.ts` | DefineRaid, RaidBossRoom, RaidBounds, RaidDefinition, RaidEntrance, RaidMuster, RaidRoomContext, RaidRoomDefinition, RoomResult |
| `core/runtime.ts` | AreaTransitionScriptContext, ButtonScriptContext, CommandScriptContext, ContentModuleDescriptor, DefineDropTable, DefineGatheringResource, DefineReward, DropTableDefinition, DropTableEntry, EncounterNpcDeathScriptContext, GatheringResourceDefinition, GatheringResourceReward, GatheringResourceTool, GraphicHeight, ItemAction, ItemClickScriptContext, ItemOnGroundItemScriptContext, ItemOnItemScriptContext, ItemOnNpcScriptContext, ItemOnObjectScriptContext, ItemOnPlayerScriptContext, ItemPickupScriptContext, LoginScriptContext, LogoutScriptContext, MagicOnItemScriptContext, MagicOnObjectScriptContext, NpcAction, NpcDeathScriptContext, NpcScriptContext, OnAreaTransition, OnButton, OnCommand, OnItem, OnItemOnGroundItem, OnItemOnItem, OnItemOnNpc, OnItemOnObject, OnItemOnPlayer, OnItemPickup, OnLogin, OnLogout, OnMagicOnItem, OnMagicOnObject, OnNpc, OnNpcDeath, OnPlayerDeath, PlayerDeathScriptContext, PlayerStateNamespace, QuestResult, QuestResultCode, RegisterContentModule, RewardDefinition, RewardExperience, RewardGrantCode, RewardGrantResult, RewardItem, RewardStateMutation, RuntimeEquipmentSlot, ScheduledHandler, ScriptArea, ScriptAreaDescriptor, ScriptArray, ScriptAudience, ScriptCameraSession, ScriptContext, ScriptDropEntry, ScriptDropResult, ScriptedActions, ScriptedBank, ScriptedCombat, ScriptedDialogue, ScriptedEquipment, ScriptedGroundItemView, ScriptedInventory, ScriptedItem, ScriptedMovement, ScriptedNpc, ScriptedObject, ScriptedPlayer, ScriptedPosition, ScriptedPresentation, ScriptedQuest, ScriptedSkills, ScriptEncounterHandle, ScriptGroundItemHandle, ScriptLockHandle, ScriptNpcHandle, ScriptNpcSnapshot, ScriptObjectHandle, ScriptPlayerSnapshot, ScriptQuestState, ScriptTaskHandle |
| `core/shop.ts` | DefineShop, ShopDefinition, ShopStockEntry |
| `core/types.ts` | CardinalDirection, ItemId, ItemStack, QuestEntry, QuestState, Result, SkillId, SkillStat, WorldPoint, WorldRegion |
| `manifest.ts` | registerModule |
| `quests/quest-builder.ts` | createQuest, createStage, registerQuest |
| `quests/types.ts` | DefineQuest, QuestDefinition, QuestExperienceReward, QuestItemAmount, QuestRequirements, QuestRewards, QuestSkillRequirement, QuestStage |
| `raids/raid-builder.ts` | BossRoomOptions, createBossRoom, createRaid, createRaidRoom, DefineRaid, raidBuilder, RaidBuilder, RaidOptions, RaidRoomOptions, registerRaid |
| `sdk/dialogue.ts` | CameraMove, cancelCutscene, cancelCutscenesFor, CutscenePlan, CutscenePlayer, CutsceneSession, CutsceneStep, DialoguePlayer, endDialogue, runCutscene, sayNpc, sayOptions, sayPlayer, sayStatement |
| `sdk/drop-tables.ts` | COMMON_WEIGHT, createDropTable, dropTable, DropTableBuilder, DropTableDefinition, DropTableEntry, RARE_WEIGHT, UNCOMMON_WEIGHT |
| `sdk/equipment.ts` | EQUIPMENT_SLOTS, equipmentSummary, equipped, hasEquipped, isEquipmentSlot, normalizeSlot, RuntimeEquipmentSlot |
| `sdk/gathering.ts` | createGatheringResource, GatheringResourceDefinition, registerGatheringResource |
| `sdk/requirements.ts` | all, always, any, hasCompletedQuest, hasItem, hasNotStartedQuest, hasQuestInProgress, hasQuestPoints, hasSkillLevel, not, Requirement, RequirementView, unmetReason |
| `sdk/rewards.ts` | createReward, grantReward, isRewarded, registerReward |
| `sdk/shops.ts` | createShop, openShop, registerShop, scriptedShop, ShopReference, staticShop |
| `sdk/skills.ts` | isScriptSkill, SCRIPT_SKILLS, ScriptSkillName, skillIndex |

