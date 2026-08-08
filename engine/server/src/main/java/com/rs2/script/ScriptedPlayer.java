package com.rs2.script;

import com.rs2.game.items.DeprecatedItems;
import com.rs2.game.items.ItemAssistant;
import com.rs2.game.items.ItemConstants;
import com.rs2.game.players.Player;
import org.apollo.cache.def.ItemDefinition;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import com.rs2.script.scheduler.ScriptTaskHandle;
import com.rs2.script.capability.ScriptedActions;
import com.rs2.script.capability.ScriptedCombat;
import com.rs2.script.capability.ScriptedEquipment;
import com.rs2.script.capability.ScriptedMagic;
import com.rs2.script.capability.ScriptedMovement;
import com.rs2.script.capability.ScriptedPresentation;
import com.rs2.script.capability.ScriptedPrayer;
import com.rs2.script.quest.ScriptedQuest;
import com.rs2.script.registries.QuestRegistry;
import com.rs2.script.state.PlayerStateNamespace;
import com.rs2.script.world.ScriptEncounterHandle;
import com.rs2.script.world.ScriptEncounterService;

public class ScriptedPlayer {

    private final Player player;
    private final long generation;
    private final long facadeEpoch;
    private ScriptedDialogue cachedDialogue;
    private int cachedPosX = Integer.MIN_VALUE;
    private int cachedPosY = Integer.MIN_VALUE;
    private int cachedPosPlane = Integer.MIN_VALUE;
    private ScriptedPosition cachedPosition;

    public ScriptedPlayer(Player player) {
        this(player, ScriptHost.getInstance().getActiveGeneration());
    }

    public ScriptedPlayer(Player player, long generation) {
        this.player = player;
        this.generation = generation;
        this.facadeEpoch = ScriptEncounterService.getInstance()
                .captureFacadeEpoch(player);
    }

    @HostAccess.Export
    public String getUsername() {
        return player.playerName;
    }

    @HostAccess.Export
    public int getX() {
        return player.absX;
    }

    @HostAccess.Export
    public int getY() {
        return player.absY;
    }

    @HostAccess.Export
    public int getPlane() {
        return player.heightLevel;
    }

    @HostAccess.Export
    public int getCombatLevel() {
        return player.calculateCombatLevel();
    }

    @HostAccess.Export
    public int getRights() {
        return player.playerRights;
    }

    @HostAccess.Export
    public ScriptedPosition getPosition() {
        int x = player.absX;
        int y = player.absY;
        int plane = player.heightLevel;
        if (cachedPosition != null && x == cachedPosX && y == cachedPosY
                && plane == cachedPosPlane) {
            return cachedPosition;
        }
        cachedPosX = x;
        cachedPosY = y;
        cachedPosPlane = plane;
        cachedPosition = new ScriptedPosition(x, y, plane);
        return cachedPosition;
    }

    @HostAccess.Export
    public void message(String text) {
        if (!BridgeValidation.hasText(text) || !canMutate()) {
            return;
        }
        player.getPacketSender().sendMessage(text);
    }

    @HostAccess.Export
    public void teleport(int x, int y, int plane) {
        if (!canMove()) {
            return;
        }
        player.getPlayerAssistant().movePlayer(x, y, plane);
        invalidatePositionCache();
    }

    @HostAccess.Export
    public void teleport(int x, int y) {
        if (!canMove()) {
            return;
        }
        player.getPlayerAssistant().movePlayer(x, y, 0);
        invalidatePositionCache();
    }

    private void invalidatePositionCache() {
        cachedPosX = Integer.MIN_VALUE;
        cachedPosY = Integer.MIN_VALUE;
        cachedPosPlane = Integer.MIN_VALUE;
        cachedPosition = null;
    }

    @HostAccess.Export
    public SkillView getSkills() {
        return new SkillView();
    }

    @HostAccess.Export
    public InventoryView getInventory() {
        return new InventoryView();
    }

    @HostAccess.Export
    public BankView getBank() {
        return new BankView();
    }

    @HostAccess.Export
    public ScriptedDialogue getDialogue() {
        if (cachedDialogue == null) {
            cachedDialogue = new ScriptedDialogue(player, generation,
                    facadeEpoch,
                    this::canMutate);
        }
        return cachedDialogue;
    }

    @HostAccess.Export
    public boolean animate(int animationId) {
        if (animationId < -1 || animationId > 65535) {
            return false;
        }
        if (!canMutate()) {
            return false;
        }
        player.startAnimation(animationId, 0);
        return true;
    }

    @HostAccess.Export
    public boolean graphic(int graphicId) {
        if (graphicId < 0 || graphicId > 65535) {
            return false;
        }
        if (!canMutate()) {
            return false;
        }
        player.gfx0(graphicId);
        return true;
    }

    @HostAccess.Export
    public boolean sound(int soundId) {
        if (soundId < 0 || soundId > 65535) {
            return false;
        }
        if (!canMutate()) {
            return false;
        }
        player.getPacketSender().sendSound(soundId, 100, 0);
        return true;
    }

    @HostAccess.Export
    public void closeInterfaces() {
        if (!canMutate()) {
            return;
        }
        com.rs2.script.interfacehook.ScriptInterfaceHookRuntime.getInstance()
                .notifyClosingMainFrame(player);
        player.scriptHookArmed = false;
        player.getPacketSender().closeAllWindows();
    }

    @HostAccess.Export
    public boolean showInterface(int interfaceId) {
        if (interfaceId < 0 || interfaceId > 65535) {
            return false;
        }
        if (!canMutate()) {
            return false;
        }
        int previous = player.lastMainFrameInterface;
        if (previous >= 0 && previous != interfaceId && player.scriptHookArmed) {
            // A scripted interface replacing another scripted one must run the
            // old hook's onClose teardown.
            com.rs2.script.interfacehook.ScriptInterfaceHookRuntime
                    .getInstance().notifyClose(player, previous);
        }
        player.getPacketSender().showInterface(interfaceId);
        boolean shown = player.lastMainFrameInterface == interfaceId;
        if (shown) {
            // Only interfaces shown through this scripted path arm their hook
            // buttons; a legacy showInterface of the same id does not.
            player.scriptHookArmed = true;
            com.rs2.script.interfacehook.ScriptInterfaceHookRuntime
                    .getInstance().notifyOpen(player, interfaceId);
        }
        return shown;
    }

    @HostAccess.Export
    public ScriptTaskHandle after(int ticks, Value callback) {
        return ScriptEncounterService.getInstance().scheduleParticipantTask(
                player, generation, facadeEpoch, ticks, false, callback);
    }

    @HostAccess.Export
    public ScriptTaskHandle every(int ticks, Value callback) {
        return ScriptEncounterService.getInstance().scheduleParticipantTask(
                player, generation, facadeEpoch, ticks, true, callback);
    }

    @HostAccess.Export
    public PlayerStateNamespace state(String namespace) {
        return new PlayerStateNamespace(player.getScriptState(), namespace,
                this::canMutate);
    }

    @HostAccess.Export
    public ScriptedQuest quest(String id) {
        return QuestRegistry.get(id) == null ? null
                : new ScriptedQuest(player, id, this::canMutate);
    }

    @HostAccess.Export
    public int questPoints() {
        return player.questPoints;
    }

    @HostAccess.Export
    public ScriptedActions getActions() {
        return new ScriptedActions(player, generation, facadeEpoch);
    }

    @HostAccess.Export
    public ScriptedMovement getMovement() {
        return new ScriptedMovement(player, generation, facadeEpoch);
    }

    @HostAccess.Export
    public ScriptedEquipment getEquipment() {
        return new ScriptedEquipment(player, generation, facadeEpoch);
    }

    @HostAccess.Export
    public ScriptedCombat getCombat() {
        return new ScriptedCombat(player, generation, facadeEpoch);
    }

    @HostAccess.Export
    public ScriptedPrayer getPrayer() {
        return new ScriptedPrayer(player, generation, facadeEpoch);
    }

    @HostAccess.Export
    public ScriptedMagic getMagic() {
        return new ScriptedMagic(player, generation, facadeEpoch);
    }

    @HostAccess.Export
    public ScriptedPresentation getPresentation() {
        return new ScriptedPresentation(player, generation, facadeEpoch);
    }

    /**
     * Opens the bank interface through the host packet sender (bank-area and
     * pin rules apply).
     */
    @HostAccess.Export
    public boolean openBank() {
        if (!canMutate() || player.getOutStream() == null) {
            return false;
        }
        player.getPacketSender().openUpBank();
        return player.isBanking;
    }

    @HostAccess.Export
    public ScriptEncounterHandle beginEncounter(String id, double minX,
            double minY, double maxX, double maxY, double plane) {
        return ScriptEncounterService.getInstance().begin(
                this, id, minX, minY, maxX, maxY, plane);
    }

    /**
     * Grants the named reward through the shared player-local transaction.
     * The reward must be registered in the active generation; the result is
     * a narrow facade (reward id plus result code), never a registry map or
     * inventory array.
     */
    @HostAccess.Export
    public com.rs2.script.reward.RewardGrantResult grantReward(String rewardId) {
        if (!canMutate()) {
            return new com.rs2.script.reward.RewardGrantResult(rewardId,
                    com.rs2.script.reward.RewardGrantResult.Code.REWARD_FAILED);
        }
        com.rs2.script.reward.RewardDefinition reward =
                com.rs2.script.reward.RewardRegistry.get(rewardId);
        if (reward == null) {
            return new com.rs2.script.reward.RewardGrantResult(rewardId,
                    com.rs2.script.reward.RewardGrantResult.Code.NOT_FOUND);
        }
        com.rs2.script.reward.PlayerRewardTransaction.Result result =
                com.rs2.script.reward.PlayerRewardTransaction.apply(player,
                        reward, null, null);
        return new com.rs2.script.reward.RewardGrantResult(rewardId,
                toGrantCode(result));
    }

    private static com.rs2.script.reward.RewardGrantResult.Code toGrantCode(
            com.rs2.script.reward.PlayerRewardTransaction.Result result) {
        switch (result) {
            case OK:
                return com.rs2.script.reward.RewardGrantResult.Code.REWARDED;
            case INVENTORY_FULL:
                return com.rs2.script.reward.RewardGrantResult.Code.INVENTORY_FULL;
            case XP_CAP:
                return com.rs2.script.reward.RewardGrantResult.Code.XP_CAP;
            case QUEST_POINTS_OVERFLOW:
                return com.rs2.script.reward.RewardGrantResult.Code.QUEST_POINTS_OVERFLOW;
            default:
                return com.rs2.script.reward.RewardGrantResult.Code.REWARD_FAILED;
        }
    }

    /** Engine-only backing identity; HostAccess.EXPLICIT keeps it guest-hidden. */
    public Player backingPlayer() {
        return player;
    }

    /** Engine-only generation identity; HostAccess.EXPLICIT keeps it guest-hidden. */
    public long generation() {
        return generation;
    }

    /** Engine-only lifecycle epoch; HostAccess.EXPLICIT keeps it guest-hidden. */
    public long facadeEpoch() {
        return facadeEpoch;
    }

    private boolean canMutate() {
        return ScriptEncounterService.getInstance().canMutate(
                player, generation, facadeEpoch);
    }

    private boolean canMove() {
        return ScriptEncounterService.getInstance().canMoveFacade(
                player, generation, facadeEpoch);
    }

    public class SkillView {

        @HostAccess.Export
        public int getLevel(int id) {
            return getCurrentLevel(id);
        }

        @HostAccess.Export
        public int getCurrentLevel(int id) {
            if (id < 0 || id >= player.playerLevel.length) {
                return 0;
            }
            return player.playerLevel[id];
        }

        @HostAccess.Export
        public int getBaseLevel(int id) {
            if (!validSkill(id)) {
                return 0;
            }
            return com.rs2.game.players.PlayerAssistant.getLevelForXP(player.playerXP[id]);
        }

        @HostAccess.Export
        public int getExperience(int id) {
            return validSkill(id) ? player.playerXP[id] : 0;
        }

        @HostAccess.Export
        public boolean addExperience(int id, double amount) {
            if (!validSkill(id) || Double.isNaN(amount) || Double.isInfinite(amount)
                    || amount <= 0 || amount > 200000000 || !canMutate()) {
                return false;
            }
            return player.getPlayerAssistant().addSkillXP(amount, id);
        }

        @HostAccess.Export
        public void setLevel(int id, int lvl) {
            if (id < 0 || id >= player.playerLevel.length || lvl < 0 || lvl > 255
                    || !canMutate()) {
                return;
            }
            player.playerLevel[id] = lvl;
            player.getPacketSender().setSkillLevel(id, lvl, player.playerXP[id]);
            player.getPlayerAssistant().refreshSkill(id);
        }

        private boolean validSkill(int id) {
            return id >= 0 && id < player.playerLevel.length && id < player.playerXP.length;
        }
    }

    public class InventoryView {

        private final ItemAssistant items = player.getItemAssistant();

        @HostAccess.Export
        public boolean add(int id, int amount) {
            if (!validItemAmount(id, amount) || !canMutate()) {
                return false;
            }
            ItemDefinition definition = itemDefinition(id);
            if (definition == null) {
                return false;
            }
            int beforeAmount = items.getItemAmount(id);
            if (definition.isStackable()) {
                long resultingAmount = (long) beforeAmount + amount;
                if (resultingAmount > ItemConstants.MAX_ITEM_AMOUNT
                        || beforeAmount == 0 && items.freeSlots() < 1) {
                    return false;
                }
            } else if (items.freeSlots() < amount) {
                return false;
            }

            int[] itemSnapshot = player.playerItems.clone();
            int[] amountSnapshot = player.playerItemsN.clone();
            double weightSnapshot = player.weight;
            boolean added = false;
            try {
                added = items.addItem(id, amount);
                if (added && items.getItemAmount(id) - beforeAmount == amount) {
                    return true;
                }
            } catch (RuntimeException e) {
                System.err.println("[ScriptedPlayer] add(inventory) failed: " + e.getMessage());
            }

            System.arraycopy(itemSnapshot, 0, player.playerItems, 0, itemSnapshot.length);
            System.arraycopy(amountSnapshot, 0, player.playerItemsN, 0, amountSnapshot.length);
            player.weight = weightSnapshot;
            items.resetItems(3214);
            player.getPacketSender().writeWeight((int) player.weight);
            return false;
        }

        @HostAccess.Export
        public boolean add(String name, int amount) {
            int id = DeprecatedItems.getItemId(name);
            if (id < 0) {
                System.out.println("[ScriptedPlayer] add(inventory): unknown item name '" + name + "'");
                return false;
            }
            return add(id, amount);
        }

        @HostAccess.Export
        public boolean canRemove(int id, int amount) {
            return validItemAmount(id, amount) && items.getItemAmount(id) >= amount;
        }

        @HostAccess.Export
        public boolean canRemove(String name, int amount) {
            int id = DeprecatedItems.getItemId(name);
            return id >= 0 && canRemove(id, amount);
        }

        @HostAccess.Export
        public boolean remove(int id, int amount) {
            if (!canMutate() || !canRemove(id, amount)) {
                return false;
            }
            items.deleteItem(id, amount);
            return true;
        }

        @HostAccess.Export
        public boolean remove(String name, int amount) {
            int id = DeprecatedItems.getItemId(name);
            if (id < 0) {
                System.out.println("[ScriptedPlayer] remove(inventory): unknown item name '" + name + "'");
                return false;
            }
            return remove(id, amount);
        }

        @HostAccess.Export
        public boolean has(int id, int amount) {
            return validItemAmount(id, amount) && items.playerHasItem(id, amount);
        }

        @HostAccess.Export
        public boolean has(String name, int amount) {
            int id = DeprecatedItems.getItemId(name);
            if (id < 0) {
                System.out.println("[ScriptedPlayer] has(inventory): unknown item name '" + name + "'");
                return false;
            }
            return has(id, amount);
        }

        @HostAccess.Export
        public int count(int id) {
            return validItemId(id) ? items.getItemAmount(id) : 0;
        }

        @HostAccess.Export
        public int count(String name) {
            int id = DeprecatedItems.getItemId(name);
            if (id < 0) {
                System.out.println("[ScriptedPlayer] count(inventory): unknown item name '" + name + "'");
                return 0;
            }
            return count(id);
        }

        @HostAccess.Export
        public int getCapacity() {
            return player.playerItems.length;
        }

        @HostAccess.Export
        public int getFreeSlots() {
            return items.freeSlots();
        }
    }

    public class BankView {

        private final ItemAssistant items = player.getItemAssistant();

        @HostAccess.Export
        public void add(int id, int amount) {
            if (validItemAmount(id, amount) && canMutate()) {
                items.addItemToBank(id, amount);
            }
        }

        @HostAccess.Export
        public void add(String name, int amount) {
            int id = DeprecatedItems.getItemId(name);
            if (id < 0) {
                System.out.println("[ScriptedPlayer] add(bank): unknown item name '" + name + "'");
                return;
            }
            add(id, amount);
        }

        @HostAccess.Export
        public boolean remove(int id, int amount) {
            if (!canMutate() || !validItemAmount(id, amount)
                    || count(id) < amount) {
                return false;
            }
            items.removeItemFromBank(id, amount);
            return true;
        }

        @HostAccess.Export
        public boolean remove(String name, int amount) {
            int id = DeprecatedItems.getItemId(name);
            if (id < 0) {
                System.out.println("[ScriptedPlayer] remove(bank): unknown item name '" + name + "'");
                return false;
            }
            return remove(id, amount);
        }

        @HostAccess.Export
        public boolean has(int id, int amount) {
            return validItemAmount(id, amount) && count(id) >= amount;
        }

        @HostAccess.Export
        public boolean has(String name, int amount) {
            int id = DeprecatedItems.getItemId(name);
            if (id < 0) {
                System.out.println("[ScriptedPlayer] has(bank): unknown item name '" + name + "'");
                return false;
            }
            return has(id, amount);
        }

        @HostAccess.Export
        public int count(int id) {
            if (!validItemId(id)) {
                return 0;
            }
            int stored = id + 1;
            int total = 0;
            int[] bankItems = player.bankItems;
            int[] bankItemsN = player.bankItemsN;
            for (int i = 0; i < ItemConstants.BANK_SIZE; i++) {
                if (bankItems[i] == stored) {
                    total += bankItemsN[i];
                }
            }
            return total;
        }

        @HostAccess.Export
        public int count(String name) {
            int id = DeprecatedItems.getItemId(name);
            if (id < 0) {
                System.out.println("[ScriptedPlayer] count(bank): unknown item name '" + name + "'");
                return 0;
            }
            return count(id);
        }

        @HostAccess.Export
        public int getCapacity() {
            return ItemConstants.BANK_SIZE;
        }
    }

    private static boolean validItemId(int id) {
        return id >= 0 && id < ItemConstants.ITEM_LIMIT;
    }

    private static ItemDefinition itemDefinition(int id) {
        ItemDefinition[] definitions = ItemDefinition.getDefinitions();
        if (definitions == null || id < 0 || id >= definitions.length) {
            return null;
        }
        ItemDefinition definition = definitions[id];
        return definition != null && definition.getId() == id ? definition : null;
    }

    private static boolean validItemAmount(int id, int amount) {
        return validItemId(id) && amount > 0;
    }
}
