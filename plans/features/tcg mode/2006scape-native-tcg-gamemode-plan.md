# Native TCG Game Mode Plan for 2006Scape

## Verdict

**Build this as a native, server-authoritative `TCG` game mode, not as a RuneLite-style plugin.**

The TypeScript bridge is a strong fit for card definitions, packs, reward tables, aliases, balancing, and content rules. Java should retain authority over account mode, persistence, action blocking, transactions, and networking.

Lazy operation is also practical:

- Standard players instantiate no TCG collection or session.
- Standard-player action checks return after one enum comparison.
- No TCG logic runs every game tick.
- The client creates no TCG panel, cache, or overlays unless the server announces that the account is in TCG mode.
- TypeScript definitions are converted into immutable Java lookup tables during content loading.
- Java does not call GraalJS every time somebody attacks an NPC or clicks an item.

The `main` branch is a consolidated SingleScape workspace with an `engine` containing client and server modules, plus a separate TypeScript content project. The server already loads compiled TypeScript through GraalVM before loading its older Java plugin system.

---

# 1. What should be ported

The two upstream projects provide different halves of the idea:

- **OSRS TCG** provides the collection loop: earn credits through gameplay, spend credits on packs, and grow a card collection.
- **Bronzeman TCG** reads that collection and blocks combat, loot, equipment, shops, gathering, processing, Slayer, Farming, Runecrafting, and other actions until the required card is owned.

Do not try to run either RuneLite plugin against your client. Instead:

1. Port the **card-collection concept**.
2. Port the **restriction rules that make sense in a 2006 server**.
3. Replace client-side blocking with server-side policy checks.
4. Create a native client UI for packs, collection progress, unlock notifications, and locked-action feedback.

Both repositories use the BSD 2-Clause license, so code can be adapted provided the copyright notices and license terms are retained. Card artwork and externally sourced images must be reviewed separately. The OSRS TCG project states that its card images are loaded from the OSRS Wiki, which has its own licensing requirements.

---

# 2. Target gameplay definition

## Account mode

```java
public enum GameMode {
    STANDARD,
    TCG
}
```

The selection is:

- Made after Tutorial Island.
- Explicitly confirmed.
- Permanent for normal players.
- Changeable only through an offline administrative migration tool.
- Stored in the normal character save, independently of TypeScript content.

Existing accounts with no saved mode migrate automatically to `STANDARD`.

## Core loop

A TCG player:

1. Earns credits by playing.
2. Buys booster packs.
3. Opens packs containing five cards.
4. Uses owned cards to unlock related content.
5. Converts duplicates into dust or partial credit.
6. Progresses toward collection milestones and specialized packs.

Useful mechanics to adapt include:

- Credits.
- Charged booster packs.
- Five-card pack results.
- Regional card pools.
- Rarity tiers.
- Foil rolls.
- Rare special pack behavior.
- Duplicate conversion.
- Collection milestones.

The server should implement its own deterministic, auditable RNG service.

## Initial restriction preset

For the first playable version:

- NPC combat requires an NPC card.
- Ground-item pickup and telegrab require an item card.
- Equipping an item requires its card.
- Using or consuming an item requires its card.
- Buying an item from a shop requires its card.
- Withdrawing an item from the bank requires its card.
- Gathering requires resource, tool, or output cards according to the selected rule.
- Processing requires the relevant input and output cards.
- Direct player trading is disabled for TCG accounts initially.
- TCG accounts may not pick up player-dropped items until transfer rules are explicitly designed.

Avoid porting OSRS-only systems such as Sailing, rumours, modern Slayer masters, modern bosses, and items that do not exist in the 2006 cache.

---

# 3. Lazy-loading architecture

## Global state

Only one immutable catalog is global:

```java
public final class TcgCatalog {
    private final CardDefinition[] cardsById;
    private final Int2IntMap itemToCard;
    private final Int2IntMap npcToCard;
    private final Map<RecipeKey, UnlockRequirement> recipes;
    private final Map<String, BoosterPackDefinition> packs;
}
```

It should be produced by the TypeScript bridge and published atomically when content loads or reloads.

The catalog can be loaded either:

- At server startup, because it should be relatively small and immutable; or
- On the first TCG login using an initialization-on-demand holder.

Startup loading is probably simpler. The important optimization is avoiding per-player allocation and per-action JavaScript calls.

## Per-player state

A standard player has:

```java
gameMode = GameMode.STANDARD;
tcgSession = null;
```

A TCG player lazily receives:

```java
TcgSession {
    BitSet unlockedCards;
    Map<Integer, Integer> duplicateCounts;
    long credits;
    int packsOpened;
    PityState pity;
    RecentUnlockBuffer recentUnlocks;
}
```

Hydrate this object only when:

- A TCG account logs in.
- An administrator inspects an offline TCG account.
- A migration explicitly loads its TCG data.

Release temporary session caches on logout.

## Hot-path check

Every central policy check starts like this:

```java
public PolicyDecision canAttack(Player player, int npcId) {
    if (player.getGameMode() != GameMode.TCG) {
        return PolicyDecision.ALLOW;
    }

    return tcgSession(player).canAttack(npcId);
}
```

For standard players this causes:

- One field read.
- One comparison.
- No collection lookup.
- No allocation.
- No TypeScript call.
- No client synchronization.

## Event-driven rather than tick-driven

Never loop over TCG players every 600 milliseconds.

Trigger logic only on:

- Login or logout.
- NPC death.
- XP gain.
- Quest completion.
- Item acquisition.
- Pack purchase.
- Collection mutation.
- A restricted action attempt.
- Explicit client panel requests.

The bridge already exposes event-style hooks such as NPC death and item pickup, along with scheduled callbacks when content actually needs them.

## Recommended lazy service pattern

```java
public final class TcgModeService {
    private final TcgCatalogProvider catalogProvider;
    private final Map<Integer, TcgSession> activeSessions = new HashMap<>();

    public boolean isActive(Player player) {
        return player.getGameMode() == GameMode.TCG;
    }

    public TcgSession session(Player player) {
        if (!isActive(player)) {
            throw new IllegalStateException("TCG session requested for standard player");
        }

        return activeSessions.computeIfAbsent(
            player.playerId,
            ignored -> loadSession(player)
        );
    }

    public void logout(Player player) {
        activeSessions.remove(player.playerId);
    }
}
```

Do not attempt to dynamically load and unload Java classes per player. Keep the service registered globally and make the standard-account path a cheap no-op.

---

# 4. Java–TypeScript responsibility split

## Java owns

Java must own anything that could be bypassed or corrupt an account:

- `GameMode`.
- Tutorial selection transaction.
- TCG persistence codec.
- Collection ownership.
- Credit balances.
- Pack purchase and RNG.
- Duplicate conversion.
- Item, NPC, and recipe restriction checks.
- Inventory, bank, shop, combat, and reward transactions.
- Anti-cheat validation.
- Client protocol.
- Rate limits.
- Administrative tools.
- Audit logging.
- Migration and recovery.

## TypeScript owns

TypeScript should own data and content behavior:

- Card definitions.
- Card display names and descriptions.
- NPC-card mappings.
- Item-card mappings.
- Alias groups.
- Booster-pack definitions.
- Region and skill categories.
- Rule presets.
- Credit sources and tuning.
- Collection milestones.
- Starter-card set.
- Pack-shop NPC dialogue.
- Tutorial explanation text after the Java-owned selection point.
- Seasonal or custom card sets.
- Content-specific unlock requirements.

The bridge already supports:

- Compiled TypeScript modules.
- Exact NPC, item, and object routes.
- Dialogue helpers.
- Persistent namespaced state.
- Atomic content reload behavior.
- Java-owned registries and projections.

## Critical performance rule

**TypeScript defines policies; Java evaluates them.**

Do not do this on every attack:

```java
graalContext.eval("canAttack(player, npc)");
```

Instead, extend the bridge so TypeScript registers validated definitions during loading:

```ts
defineCard({
  id: 120,
  key: "npc.goblin",
  name: "Goblin",
  category: "npc",
  rarityScore: 10,
  npcIds: [100, 101, 102],
});

defineBoosterPack({
  id: "lumbridge",
  name: "Lumbridge Pack",
  price: 100,
  categories: ["lumbridge"],
});
```

During activation, Java projects those values into `TcgCatalog`.

The existing bridge already uses staged registries and atomic runtime publication, so a TCG catalog projection fits the architecture well.

---

# 5. Suggested source layout

## Server

```text
engine/server/src/main/java/com/rs2/game/mode/
    GameMode.java
    GameModeService.java

engine/server/src/main/java/com/rs2/game/tcg/
    TcgModeService.java
    TcgCatalog.java
    TcgCatalogProjection.java
    TcgSession.java
    TcgAccountState.java
    TcgStateCodec.java
    TcgPolicyService.java
    TcgRestriction.java
    TcgRequirement.java
    TcgCardOwnership.java
    TcgCreditService.java
    TcgPackService.java
    TcgRewardService.java
    TcgAuditLogger.java
    TcgMigrationService.java

engine/server/src/main/java/com/rs2/game/tcg/protocol/
    TcgPacketSender.java
    TcgClientPacketHandler.java
    TcgProtocolConstants.java
```

## TypeScript content

```text
content/src/tcg/
    index.ts
    cards/
        items.ts
        npcs.ts
        resources.ts
        quests.ts
    packs/
        starter.ts
        lumbridge.ts
        varrock.ts
        skills.ts
    rules/
        standard-preset.ts
        aliases.ts
        recipes.ts
    rewards/
        credits.ts
        milestones.ts
```

Add this to `content/src/loader.ts`:

```ts
import "./tcg/index.js";
```

The current loader explicitly imports each content family.

## Client

```text
engine/client/src/main/java/
    TcgClientState.java
    TcgProtocolHandler.java
    TcgCollectionPanel.java
    TcgPackReveal.java
    TcgUnlockToast.java
    TcgLockedOverlay.java
    TcgCardAssets.java
```

---

# 6. Tutorial Island integration

The existing Tutorial Island ending is straightforward to hook.

Current flow:

1. Magic Instructor offers `Mainland` or `Stay here`.
2. Choosing mainland enters dialogue `3112`.
3. Dialogue `3115` sets `tutorialProgress = 36`.
4. Dialogue `3116` moves into XP-rate or appearance selection.
5. Tutorial NPC interactions are already controlled by `tutorialProgress`, including the final instructor states.

## Recommended new flow

```text
3111: Mainland / Stay here
  ↓ Mainland
3112–3114: existing final explanation
  ↓
New dialogue: Choose your game mode
  ├─ Standard mode
  ├─ TCG mode
  └─ Explain TCG mode
  ↓ TCG
Confirmation:
“TCG mode permanently locks content behind collectible cards.”
  ├─ Confirm TCG
  └─ Go back
  ↓ confirmed
Atomic finalizeTutorialSelection()
  ↓
3115: Welcome to Lumbridge
  ↓
3116: XP-rate/appearance flow
```

## Atomic finalization

Do not set `tutorialProgress = 36` before a mode has been selected.

```java
public void finalizeTutorialSelection(Player player, GameMode selected) {
    if (player.tutorialProgress != 35) {
        return;
    }

    if (player.hasSelectedGameMode()) {
        return;
    }

    player.setGameMode(selected);

    if (selected == GameMode.TCG) {
        tcgModeService.initializeNewAccount(player);
    }

    player.tutorialProgress = 36;
    PlayerSave.saveGame(player);
    player.getPacketSender().sendGameModeState();
    player.getDialogueHandler().sendDialogues(3115, player.talkingNpc);
}
```

This protects against:

- Disconnecting during the selection dialogue.
- Clicking twice.
- Sending a forged option packet.
- Getting Tutorial Island completion without a mode.
- Receiving starter cards more than once.

## Starter collection

TCG mode starts with a carefully chosen starter set:

- Coins.
- Bronze axe.
- Tinderbox.
- Small fishing net.
- Raw shrimp and cooked shrimp.
- Bronze pickaxe.
- Bronze dagger or sword.
- Basic runes.
- Essential Lumbridge NPCs.
- Essential tutorial-output items.

Restrictions should not be active during Tutorial Island itself. They activate only after selection and completion, avoiding tutorial softlocks.

## Tutorial-disabled servers

`firstTimeTutorial()` also supports bypassing Tutorial Island when the server setting disables it. That branch currently gives starters and moves the player directly to the mainland.

Add a mandatory game-mode selection before granting the final mainland starter state.

---

# 7. Persistence design

## Save fields

Add these to `PlayerSave`:

```text
game-mode = STANDARD
game-mode-selected = true
tcg-state-version = 1
tcg-state = <encoded payload>
```

The current save format already persists `tutorial-progress` and a versionable `character-script-state` payload.

## Do not store the full collection as card names

Use stable integer card IDs.

Recommended payload:

```java
final class TcgAccountState {
    int schemaVersion;
    BitSet unlocked;
    Int2IntMap duplicates;
    long credits;
    long totalCreditsEarned;
    int packsOpened;
    Map<String, Integer> pityCounters;
    int[] recentUnlocks;
}
```

Encoding:

- BitSet to byte array to Base64.
- Duplicate counts as sparse integer pairs.
- Include a schema version.
- Include a checksum.
- Impose strict decoded-size and entry-count limits.
- Save through the same atomic replacement pattern used by the existing player-save implementation.

Names and descriptions can change without invalidating ownership because ownership uses immutable numeric IDs.

## Script state use

The existing `player.state(namespace)` bridge is suitable for small content flags such as:

- First TCG explanation viewed.
- Selected panel tab.
- Milestone claim flags.
- One-time NPC interactions.
- Pack-shop quest progress.

It should not be the authoritative home of a potentially large card collection.

The bridge’s namespaced state persists through `character-script-state`, but Java needs collection access even when content scripts fail to load.

---

# 8. Restriction enforcement matrix

Every restriction must be checked at the central transaction boundary, not just in a packet handler.

| Action | Server enforcement point | Requirement |
|---|---|---|
| Attack NPC | Combat initiation | NPC card |
| Spell on NPC | Before rune deletion or cast start | NPC card and optionally spell or input cards |
| Item on NPC | Before item handler | NPC card and item card |
| Pick up ground item | Before ownership removal | Item card |
| Telegrab | Before rune deletion | Item card |
| Equip | Before inventory or equipment mutation | Item card |
| Consume | Before item deletion or effect | Item card |
| Item on item | Before recipe mutation | Recipe inputs and output |
| Item on object | Before legacy or script route | Item, tool, or output rule |
| Buy from shop | Before coin deletion | Purchased-item card |
| Bank withdraw | Before bank mutation | Item card |
| Trade | Before trade session opens | Initially deny |
| Player drop transfer | Before another TCG account picks it up | Initially deny |
| Woodcutting | Before animation or session begins | Tree, tool, or log rule |
| Mining | Before mining session begins | Rock, tool, or ore rule |
| Fishing | Before fishing session begins | Spot, tool, or catch rule |
| Cooking | Before item deletion | Raw and/or cooked card |
| Smithing or smelting | Before bars or ores are deleted | Inputs and output |
| Crafting or fletching | Before material deletion | Inputs and output |
| Quest start | Optional later phase | Quest-required cards |
| Slayer assignment | Later phase | Master or monster cards |

The RuneLite Bronzeman plugin mostly blocks menu actions and documents that keyboard-driven interface defaults can bypass its checks. That is precisely why the server version must validate at transaction boundaries instead.

## One policy interface

```java
public interface GameModePolicy {
    PolicyDecision attackNpc(Player player, int npcId);
    PolicyDecision acquireItem(
        Player player,
        int itemId,
        int amount,
        ItemSource source
    );
    PolicyDecision equipItem(Player player, int itemId);
    PolicyDecision useItem(
        Player player,
        int itemId,
        ItemUseContext context
    );
    PolicyDecision process(Player player, RecipeKey recipe);
    PolicyDecision withdraw(Player player, int itemId, int amount);
    PolicyDecision buy(
        Player player,
        int itemId,
        int amount,
        int shopId
    );
}
```

Return structured decisions:

```java
record PolicyDecision(
    boolean allowed,
    RestrictionCode code,
    int requiredCardId,
    String message
) {}
```

The same decision can drive:

- Server rejection.
- Chat text.
- Client lock toast.
- Collection-panel navigation.
- Audit logs.

## Avoid scattered mode checks

Do not add hundreds of unrelated checks like:

```java
if (player.getGameMode() == GameMode.TCG) {
    // special case
}
```

Instead, centralize them:

```java
PolicyDecision decision = gameModePolicy.attackNpc(player, npcId);
if (!decision.allowed()) {
    policyFeedback.send(player, decision);
    return;
}
```

This keeps the mode maintainable and testable.

---

# 9. Credits and card rewards

## Credit sources

Recommended initial credit sources:

- A bounded amount per XP earned.
- First kill of an NPC species.
- Normal NPC kills with diminishing returns.
- Quest completion.
- Achievement or collection milestones.
- Boss kills.
- Minigame completion.
- First-time item discoveries.
- Daily caps only where farming becomes abusive.

Do not reward raw client events. Credits must come from completed server transactions.

## Starter economy

A safe starting balance should buy several packs immediately.

The exact numbers belong in TypeScript tuning, not Java constants.

Example:

```ts
defineCreditTuning({
  xpPerCredit: 250,
  firstNpcKillBonus: 10,
  questPointBonus: 50,
  duplicateRefundPercent: 20,
});
```

## Pack system

Implement:

- Five cards per pack.
- Stable pack IDs.
- Region and skill packs.
- Rarity-tier weights.
- Duplicate handling.
- Pity counters.
- Optional foils as cosmetic variants.
- Transaction ID for every opening.
- Server-generated result sent to the client only after payment succeeds.

Atomic flow:

```text
Validate request
→ Check credits
→ Resolve immutable pack definition
→ Seed server RNG transaction
→ Roll all cards
→ Deduct credits
→ Apply cards and duplicates
→ Persist
→ Send reveal packet
→ Audit
```

Never let the client provide rolled cards, rarity, or RNG seeds.

## Duplicate model

Recommended initial behavior:

- First copy unlocks the card.
- Additional copies increment a duplicate count.
- Duplicates can be converted into dust.
- Dust can purchase targeted packs or rerolls.
- Duplicate conversion is server-side and atomic.
- Foil and normal copies may share unlock authority while remaining distinct cosmetics.

---

# 10. Native client protocol

Use dedicated packets rather than overloading chat strings.

## Server-to-client

```text
TCG_MODE_STATE
  mode
  catalogRevision
  credits
  unlockedCount
  totalCardCount

TCG_COLLECTION_PAGE
  page
  totalPages
  card summaries

TCG_UNLOCK
  cardId
  rarity
  reason

TCG_PACK_RESULT
  transactionId
  packId
  five card results
  creditsRemaining

TCG_ACTION_BLOCKED
  restrictionCode
  requiredCardId
  targetId
```

## Client-to-server

```text
TCG_OPEN_COLLECTION
TCG_REQUEST_PAGE
TCG_REQUEST_CARD_DETAILS
TCG_BUY_PACK
TCG_REQUEST_PACK_LIST
TCG_CONVERT_DUPLICATES
```

Validate:

- Exact packet size.
- Valid enum ranges.
- Valid page bounds.
- Existing pack ID.
- Request throttling.
- Logged-in state.
- TCG account state.
- No concurrent pack transaction.

Your packet handlers already use strict packet-length and common-player validation in newer bridge-enabled handlers. The TCG protocol should follow the same pattern.

## Protocol versioning

Add:

```text
TCG_PROTOCOL_VERSION
TCG_CATALOG_REVISION
```

On mismatch:

- Server authority remains active.
- Optional client visuals are disabled.
- The player receives a clear update-required message.
- Pack purchases should be disabled if the client cannot safely display the result.

---

# 11. Client UI rollout

## First client version

Ship these first:

1. A game-mode icon next to the account name or settings area.
2. Collection summary interface.
3. Paginated card list.
4. Booster-pack shop.
5. Pack-opening reveal.
6. Unlock notification.
7. Clear locked-action message.

Do not begin with outlines for every NPC and inventory item. Those are visually attractive but require more invasive rendering work and synchronization.

## Second client version

Add:

- Faded locked inventory items.
- Locked-card icon overlay.
- Grey NPC outline.
- Collection search.
- Recent unlocks.
- Readiness pages for quests and bosses.
- “View required card” navigation from a blocked action.

## Resource loading

For standard accounts:

```java
if (!tcgClientState.isActive()) {
    return;
}
```

The TCG UI should not:

- Instantiate card panels.
- Decode card art.
- Allocate search indexes.
- Build collection rows.
- Subscribe to rendering hooks.

until `TCG_MODE_STATE` reports `TCG`.

Request collection pages on demand. Do not send the entire collection UI model on login.

## Client maintenance issue

`Game.java` currently warns that changes must also be copied to `LocalGame.java` for local Parabot compatibility.

Any packet-decoding addition therefore needs either:

- Synchronized changes in both classes; or
- A refactor that moves custom packet handling into a shared helper called by both.

The `main` client is built as a Java 17 module, while the server remains Java 8-targeted. Shared source code must either remain Java 8-compatible or be separated by module.

---

# 12. TypeScript bridge additions

Add author-facing APIs:

```ts
defineCard(definition);
defineCardAlias(definition);
defineBoosterPack(definition);
defineTcgRecipe(definition);
defineCreditSource(definition);
defineCollectionMilestone(definition);
defineTcgPreset(definition);
```

## Suggested definition shape

```ts
interface CardDefinition {
  id: number;
  key: string;
  name: string;
  category: "item" | "npc" | "resource" | "quest";
  rarityScore: number;
  itemIds?: readonly number[];
  npcIds?: readonly number[];
  aliases?: readonly string[];
  regions?: readonly string[];
  tags?: readonly string[];
}
```

## Booster definition

```ts
interface BoosterPackDefinition {
  id: string;
  name: string;
  price: number;
  size: number;
  categories?: readonly string[];
  regions?: readonly string[];
  includedCardIds?: readonly number[];
  excludedCardIds?: readonly number[];
  rarityWeights: Readonly<Record<CardRarity, number>>;
}
```

## Recipe requirement

```ts
interface TcgRecipeDefinition {
  id: string;
  inputItems: readonly number[];
  outputItems: readonly number[];
  requiredCards: readonly number[];
  mode: "inputs" | "outputs" | "both" | "any";
}
```

## Validation during reload

- ID is positive and unique.
- Key is unique.
- Every item or NPC ID maps unambiguously.
- Alias groups do not cycle.
- Booster packs reference existing categories or cards.
- Rarity values are bounded.
- Starter cards exist.
- No pack has an empty pool.
- No recipe references an unknown card.
- Existing persistent IDs cannot silently change identity.
- Card definitions cannot exceed configured count limits.
- Strings have maximum lengths.
- Duplicate mappings fail the candidate generation.

A bad TCG definition should reject the candidate content generation and preserve the last known-good catalog, matching the bridge’s existing atomic-reload model.

---

# 13. Migration and compatibility

## Existing accounts

On first login after deployment:

```text
missing game-mode
→ tutorialProgress >= 36
→ assign STANDARD
→ mark migration version
→ save
```

Never force existing players into a restrictive mode.

## Existing new accounts mid-tutorial

- Preserve their current `tutorialProgress`.
- Offer game-mode selection only at the final instructor.
- Do not initialize TCG state before confirmation.

## Removed or renamed cards

- Card ID remains reserved permanently.
- Renaming changes display metadata only.
- Removed cards become `retired`.
- Owned retired cards remain in the account.
- Requirements must migrate to replacement IDs explicitly.

## Catalog revision

Create a deterministic catalog revision hash from:

- Card IDs.
- Mappings.
- Pack definitions.
- Alias groups.
- Recipe rules.

Send it to the client. A mismatch disables optional visual prediction but never weakens server enforcement.

## Administrative migration tool

Provide an offline command or utility:

```text
tcg-migrate-account <username> <STANDARD|TCG>
tcg-reset-state <username>
tcg-grant-card <username> <card-id>
tcg-revoke-card <username> <card-id>
tcg-set-credits <username> <amount>
tcg-verify-save <username>
```

Mode changes should:

- Require the player to be offline.
- Create a timestamped backup.
- Be recorded in an audit log.
- Require an explicit reason.
- Never be exposed as a normal in-game command.

---

# 14. Anti-exploit requirements

Before public release, cover these bypasses:

- Spell-on-NPC packets.
- Item-on-NPC packets.
- Item-on-object.
- Item-on-item.
- Interface “make one”, “make all”, and “make X” buttons.
- Repeated packets during an animation.
- Banking through every interface.
- Shop buy-one, five, ten, and X.
- Telegrab.
- Ground items created by deaths, drops, minigames, and scripts.
- Loot entering inventory directly instead of through ground pickup.
- Quest rewards.
- Player trading.
- Duel staking.
- Death-item recovery.
- Shops opened through TypeScript.
- Scripted rewards and drop tables.
- Admin item grants.
- Bot accounts.
- Rollback after a failed save.
- Disconnect during pack opening.
- Duplicate purchase packets.
- Catalog reload during an action.
- Auto-retaliate initiating combat.
- Poison or recoil kills.
- Pets or summons killing NPCs.
- Multi-combat kill credit.
- Inventory actions started before a card is revoked.
- Make-X loops continuing after policy state changes.

Every item-producing path should identify an `ItemSource`:

```java
enum ItemSource {
    NPC_DROP,
    GROUND_PICKUP,
    SHOP,
    BANK,
    TRADE,
    QUEST_REWARD,
    MINIGAME,
    SKILLING,
    SCRIPT_REWARD,
    ADMIN
}
```

That lets you make deliberate rules rather than accidentally treating every acquisition identically.

## Transaction generation guard

For long-running actions:

```java
record TcgActionLease(
    long catalogGeneration,
    long accountRevision,
    int requiredCardId
) {}
```

Before each repeated production step, verify that the lease is still valid.

This prevents a reload or administrative card revocation from allowing an old action loop to continue indefinitely.

---

# 15. Testing plan

## Unit tests

- Standard mode always allows normal gameplay.
- Standard mode creates no TCG session.
- Every rule correctly resolves owned and unowned cards.
- Aliases unlock all mapped variants.
- Starter cards cannot be omitted.
- BitSet serialization round-trips.
- Duplicate data survives saves.
- Invalid payloads are quarantined.
- Pack rolls are deterministic with a test seed.
- Credit deduction and collection mutation are atomic.
- Pity counters behave correctly.
- Catalog validation rejects duplicate IDs.
- Catalog reload keeps the previous valid generation on failure.

## Tutorial integration tests

Test disconnect and relogin:

- Before the mode screen.
- While explanation is open.
- While confirmation is open.
- Immediately after confirmation.
- Before save completes.
- After mode saved but before client state is sent.
- After Tutorial Island completion.

Expected invariant:

```text
tutorialProgress == 36
implies
gameModeSelected == true
```

Also test:

- Repeated confirmation packets.
- Invalid dialogue button IDs.
- Standard selection.
- TCG selection.
- Tutorial-disabled configuration.
- Existing account migration.
- Mid-tutorial account migration.

## Restriction integration tests

For each action:

1. Standard player.
2. TCG player without card.
3. TCG player with card.
4. TCG player with alias card.
5. Malformed packet.
6. Repeated packet.
7. Content reload between validation and execution.
8. Disconnect during execution.
9. Full inventory.
10. Direct server-side reward path.

## Performance acceptance criteria

For standard accounts:

- Zero TCG allocations during ordinary actions.
- No Graal calls on action paths.
- No TCG per-tick work.
- No client TCG render work.
- No collection payload on login.

Benchmark:

- Combat initiation.
- NPC processing.
- Item clicks.
- Bank withdraws.
- Shop purchases.
- Ground-item pickup.
- Server login.
- World tick duration.

Record before and after measurements.

---

# 16. Observability and administration

Add bounded metrics:

```text
tcg.active_sessions
tcg.policy_checks
tcg.policy_denials
tcg.pack_purchases
tcg.pack_failures
tcg.cards_unlocked
tcg.duplicate_conversions
tcg.save_failures
tcg.catalog_revision
tcg.catalog_reload_failures
```

Audit events:

```text
MODE_SELECTED
MODE_ADMIN_CHANGED
CARD_GRANTED
CARD_REVOKED
PACK_PURCHASED
PACK_RESULT_APPLIED
CREDITS_CHANGED
STATE_MIGRATED
STATE_RECOVERED
POLICY_DENIED
```

Avoid logging every successful policy check. Log denials only with throttling or aggregation to prevent spam.

Staff inspection command:

```text
::tcginfo <player>
```

Should display:

- Mode.
- TCG state version.
- Credits.
- Unlocked cards.
- Duplicate count.
- Packs opened.
- Catalog revision.
- Active session status.
- Last save status.

Do not expose secret RNG state.

---

# 17. Implementation sequence

## PR 1 — Game-mode foundation

Add:

- `GameMode`.
- Player field and accessors.
- Save and load fields.
- Existing-account migration.
- Admin inspection command.
- Unit tests.

No gameplay restrictions yet.

## PR 2 — Tutorial selection

Modify:

- Final Magic Instructor flow.
- Tutorial-disabled first-login flow.
- Atomic selection service.
- Reconnect recovery.
- Standard and TCG confirmation text.
- Starter TCG-state initialization.

The current completion boundary is dialogue `3115`, where progress changes to 36, followed by dialogue `3116`.

## PR 3 — TCG persistence and catalog bridge

Add:

- `TcgAccountState`.
- BitSet codec.
- Catalog definitions in TypeScript.
- Registry validation.
- Atomic Java projection.
- Starter card set.
- Catalog revision.

## PR 4 — Policy framework

Add:

- `TcgPolicyService`.
- Structured denial results.
- Audit events.
- Standard no-op fast path.
- TCG session lifecycle.

Do not enforce anything yet except in tests.

## PR 5 — Core restrictions

Enable:

- NPC attack.
- Item pickup.
- Equip.
- Consume or use.
- Shop buying.
- Bank withdrawal.

These produce the first playable restricted mode.

## PR 6 — Credits and packs

Add:

- Gameplay credit sources.
- Pack definitions.
- Server RNG.
- Duplicate conversion.
- Pity.
- Audit logs.
- Transaction recovery.

## PR 7 — Native client UI

Add:

- Mode-state packet.
- Collection panel.
- Pack shop.
- Pack reveal.
- Unlock notification.
- Locked-action response.

Keep visuals simple.

## PR 8 — Gathering and processing

Integrate:

- Woodcutting.
- Mining.
- Fishing.
- Cooking.
- Smithing and smelting.
- Crafting.
- Fletching.
- Herblore.
- Firemaking.
- Runecrafting.

Build an explicit recipe catalog instead of scattering special cases.

## PR 9 — Advanced UI and content

Add:

- Locked inventory marker.
- NPC outline.
- Search.
- Quest readiness.
- Boss readiness.
- Region packs.
- Collection achievements.
- Statistics.

## PR 10 — Hardening and beta rollout

Add:

- Migration tooling.
- Restore tooling.
- Performance benchmarks.
- Anti-bypass test suite.
- Telemetry counters.
- Staff audit commands.
- Feature flag.
- TCG-only beta account allowlist.
- Final license notices.

---

# 18. Recommended first-release scope

The first public version should contain:

- Permanent Tutorial Island opt-in.
- 2006-specific card catalog.
- Starter cards.
- Credits.
- Five-card packs.
- NPC combat locks.
- Ground loot locks.
- Equipment locks.
- Shop locks.
- Bank-withdraw locks.
- Basic collection UI.
- Unlock and denial messages.
- No trading for TCG accounts.
- No visual NPC outlines yet.
- No advanced quest or Slayer readiness yet.

That is enough to make the mode distinct and playable without trying to reproduce every feature from a modern OSRS RuneLite plugin.

---

# 19. Definition of done

The first production release is complete when:

- Existing accounts automatically remain standard.
- New accounts must select a mode before leaving Tutorial Island.
- TCG selection survives disconnects and repeated packets.
- A standard player experiences no behavior changes.
- A standard player allocates no TCG session.
- A TCG player receives starter cards and an initial pack budget.
- Locked NPC attacks are rejected by the server.
- Locked item pickup is rejected by the server.
- Locked equip, shop, and bank actions are rejected by the server.
- Pack results are generated and applied only by the server.
- Collection state survives restart and migration.
- A broken TypeScript TCG catalog cannot replace the active valid catalog.
- The client UI is optional presentation, never the authority.
- All adapted upstream code retains required BSD notices.
- Performance benchmarks show negligible impact on standard gameplay.
- The anti-bypass integration suite passes.

---

# Final architectural decision

```text
Java:
authoritative mode + collection + transactions + restrictions + packets

TypeScript:
cards + packs + mappings + rewards + balancing + content dialogue

Client:
presentation only

Standard player:
one enum check, otherwise no TCG work

TCG player:
event-driven session loaded on login
```

This creates a native game mode that remains secure even with a modified client, uses the TypeScript bridge where it is strongest, and leaves normal accounts effectively unaffected.
