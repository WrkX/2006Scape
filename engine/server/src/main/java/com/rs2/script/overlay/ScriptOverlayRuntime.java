package com.rs2.script.overlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apollo.cache.def.EquipmentDefinition;
import org.apollo.cache.def.ItemDefinition;
import org.apollo.cache.def.ObjectDefinition;

import com.rs2.game.items.ItemConstants;
import com.rs2.game.items.ItemData;
import com.rs2.game.items.ItemDefinitions;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.npcs.NpcList;
import com.rs2.script.ScriptHost;
import com.rs2.util.LoggerUtils;

/**
 * Applies and reverts cache definition overlays on generation publish/close.
 *
 * <p>Overlays merge deterministically in ascending cache-id order and log each
 * merged record at activation time.
 */
public final class ScriptOverlayRuntime {

	private static final Logger logger =
			LoggerUtils.getLogger(ScriptOverlayRuntime.class);

	private static volatile ScriptOverlayRuntime INSTANCE =
			new ScriptOverlayRuntime();

	private final Map<Integer, ItemBaseline> itemBaselines =
			new HashMap<Integer, ItemBaseline>();
	private final Map<Integer, NpcBaseline> npcBaselines =
			new HashMap<Integer, NpcBaseline>();
	private final Map<Integer, ObjectBaseline> objectBaselines =
			new HashMap<Integer, ObjectBaseline>();
	private final Map<Long, Set<Integer>> generationItems =
			new HashMap<Long, Set<Integer>>();
	private final Map<Long, Set<Integer>> generationNpcs =
			new HashMap<Long, Set<Integer>>();
	private final Map<Long, Set<Integer>> generationObjects =
			new HashMap<Long, Set<Integer>>();

	private ScriptOverlayRuntime() {
	}

	public static ScriptOverlayRuntime getInstance() {
		return INSTANCE;
	}

	public static ScriptOverlayRuntime installForTesting() {
		ScriptOverlayRuntime runtime = new ScriptOverlayRuntime();
		INSTANCE = runtime;
		return runtime;
	}

	public void register(ItemOverlayDefinition definition) {
		if (definition == null) {
			throw new IllegalArgumentException(
					"item overlay definition must not be null");
		}
	}

	public void register(NpcOverlayDefinition definition) {
		if (definition == null) {
			throw new IllegalArgumentException(
					"npc overlay definition must not be null");
		}
	}

	public void register(ObjectOverlayDefinition definition) {
		if (definition == null) {
			throw new IllegalArgumentException(
					"object overlay definition must not be null");
		}
	}

	/**
	 * Applies overlays for one generation. Reverts any overlays recorded for
	 * {@code generation} before rethrowing when apply throws mid-publish.
	 */
	public void publishGeneration(long generation) {
		try {
			onGenerationPublished(generation);
		} catch (RuntimeException failure) {
			closeGeneration(generation);
			throw failure;
		}
	}

	public void onGenerationPublished(long generation) {
		List<String> mergeLog = new ArrayList<String>();
		Set<Integer> appliedItems = new HashSet<Integer>();
		Set<Integer> appliedNpcs = new HashSet<Integer>();
		Set<Integer> appliedObjects = new HashSet<Integer>();

		// Record the generation sets up front and add each id immediately after
		// its overlay is applied. If a later apply throws (e.g. "no free npc
		// list slot for overlay"), the publish hook is caught and logged by
		// ScriptHost, so nothing would revert an overlay whose baseline was
		// captured but whose id was never recorded. Progressive recording means
		// the next closeGeneration reverts exactly what was applied so far.
		generationItems.put(Long.valueOf(generation), appliedItems);
		generationNpcs.put(Long.valueOf(generation), appliedNpcs);
		generationObjects.put(Long.valueOf(generation), appliedObjects);

		Map<Integer, ItemOverlayDefinition> items = ScriptHost.getInstance()
				.readActiveRegistry(ItemOverlayDefinitionRegistry::all);
		for (ItemOverlayDefinition overlay
				: new TreeMap<Integer, ItemOverlayDefinition>(items)
						.values()) {
			// Record the id before applying: each apply captures its baseline on
			// its first line, so a mid-apply throw leaves the id recorded and the
			// next closeGeneration reverts even a partially-mutated definition.
			appliedItems.add(Integer.valueOf(overlay.itemId()));
			applyItemOverlay(overlay, mergeLog);
		}

		Map<Integer, NpcOverlayDefinition> npcs = ScriptHost.getInstance()
				.readActiveRegistry(NpcOverlayDefinitionRegistry::all);
		for (NpcOverlayDefinition overlay
				: new TreeMap<Integer, NpcOverlayDefinition>(npcs).values()) {
			appliedNpcs.add(Integer.valueOf(overlay.npcId()));
			applyNpcOverlay(overlay, mergeLog);
		}

		Map<Integer, ObjectOverlayDefinition> objects = ScriptHost.getInstance()
				.readActiveRegistry(ObjectOverlayDefinitionRegistry::all);
		for (ObjectOverlayDefinition overlay
				: new TreeMap<Integer, ObjectOverlayDefinition>(objects)
						.values()) {
			appliedObjects.add(Integer.valueOf(overlay.objectId()));
			applyObjectOverlay(overlay, mergeLog);
		}

		for (String line : mergeLog) {
			logger.log(Level.INFO, line);
		}
	}

	public void closeGeneration(long generation) {
		Set<Integer> items = generationItems.remove(Long.valueOf(generation));
		if (items != null) {
			for (Integer itemId : items) {
				revertItem(itemId.intValue());
			}
		}
		Set<Integer> npcs = generationNpcs.remove(Long.valueOf(generation));
		if (npcs != null) {
			for (Integer npcId : npcs) {
				revertNpc(npcId.intValue());
			}
		}
		Set<Integer> objects = generationObjects.remove(
				Long.valueOf(generation));
		if (objects != null) {
			for (Integer objectId : objects) {
				revertObject(objectId.intValue());
			}
		}
	}

	public void resetForTesting() {
		for (Long generation : new ArrayList<Long>(generationItems.keySet())) {
			closeGeneration(generation.longValue());
		}
		itemBaselines.clear();
		npcBaselines.clear();
		objectBaselines.clear();
	}

	private void applyItemOverlay(ItemOverlayDefinition overlay,
			List<String> mergeLog) {
		int itemId = overlay.itemId();
		captureItemBaselineIfNeeded(itemId);
		ItemDefinition definition = ItemDefinition.lookup(itemId);
		if (overlay.name() != null) {
			definition.setName(overlay.name());
		}
		if (overlay.examine() != null) {
			definition.setDescription(overlay.examine());
		}
		if (overlay.stackable() != null) {
			definition.setStackable(overlay.stackable().booleanValue());
		}
		if (overlay.equipSlot() != null) {
			ItemData.targetSlots[itemId] = equipSlotIndex(overlay.equipSlot());
		}
		if (overlay.requirements() != null || overlay.equipSlot() != null) {
			EquipmentDefinition equipment = EquipmentDefinition.ensure(itemId);
			if (overlay.equipSlot() != null) {
				equipment.setSlot(equipSlotIndex(overlay.equipSlot()));
			}
			if (overlay.requirements() != null) {
				// Merge only the skills the overlay declares over the existing
				// cache requirements, rather than replacing all seven.
				boolean[] present = overlay.requirementPresence();
				int[] levels = overlay.requirements();
				int[] merged = {
						equipment.getAttackLevel(), equipment.getStrengthLevel(),
						equipment.getDefenceLevel(), equipment.getHitpointsLevel(),
						equipment.getRangedLevel(), equipment.getPrayerLevel(),
						equipment.getMagicLevel()
				};
				if (present != null) {
					for (int index = 0; index < merged.length; index++) {
						if (index < present.length && present[index]) {
							merged[index] = levels[index];
						}
					}
				}
				equipment.setLevels(merged[0], merged[1], merged[2], merged[3],
						merged[4], merged[5], merged[6]);
			}
		}
		if (overlay.bonuses() != null) {
			// Merge each declared bonus over the current cache value. A declared
			// 0 is indistinguishable from absent and means "make it 0".
			int[] existing = ItemDefinitions.copyBonus(itemId);
			int[] overlayBonuses = overlay.bonuses();
			int[] merged = new int[overlayBonuses.length];
			for (int index = 0; index < merged.length; index++) {
				merged[index] = overlayBonuses[index] != 0
						? overlayBonuses[index] : existing[index];
			}
			ItemDefinitions.applyOverlay(itemId, merged);
		}
		mergeLog.add("[overlay] item " + itemId + " <- '" + overlay.id()
				+ "' (source: " + overlay.source() + ")");
	}

	private void applyNpcOverlay(NpcOverlayDefinition overlay,
			List<String> mergeLog) {
		int npcId = overlay.npcId();
		NpcList entry = captureNpcBaselineIfNeeded(npcId);
		if (overlay.name() != null) {
			entry.npcName = overlay.name();
		}
		if (overlay.combatLevel() != null) {
			entry.npcCombat = overlay.combatLevel().intValue();
		}
		if (overlay.hitpoints() != null) {
			entry.npcHealth = overlay.hitpoints().intValue();
		}
		mergeLog.add("[overlay] npc " + npcId + " <- '" + overlay.id()
				+ "' (source: " + overlay.source() + ")");
	}

	private void applyObjectOverlay(ObjectOverlayDefinition overlay,
			List<String> mergeLog) {
		int objectId = overlay.objectId();
		captureObjectBaselineIfNeeded(objectId);
		ObjectDefinition definition = ObjectDefinition.lookup(objectId);
		if (overlay.name() != null) {
			definition.setName(overlay.name());
		}
		if (overlay.examine() != null) {
			definition.setDescription(overlay.examine());
		}
		if (overlay.actions() != null) {
			String[] actions = overlay.actions();
			String[] merged = definition.getMenuActions();
			if (merged == null) {
				merged = new String[5];
			} else {
				merged = merged.clone();
			}
			for (int index = 0; index < actions.length; index++) {
				if (actions[index] != null) {
					merged[index] = actions[index];
				}
			}
			definition.setMenuActions(merged);
			definition.setInteractive(true);
		}
		mergeLog.add("[overlay] object " + objectId + " <- '" + overlay.id()
				+ "' (source: " + overlay.source() + ")");
	}

	private void captureItemBaselineIfNeeded(int itemId) {
		if (itemBaselines.containsKey(Integer.valueOf(itemId))) {
			return;
		}
		ItemDefinition definition = ItemDefinition.lookup(itemId);
		EquipmentDefinition equipment = EquipmentDefinition.lookup(itemId);
		ItemBaseline baseline = new ItemBaseline();
		baseline.name = definition.getName();
		baseline.description = definition.getDescription();
		baseline.stackable = definition.isStackable();
		baseline.targetSlot = ItemData.targetSlots[itemId];
		if (equipment != null) {
			baseline.hadEquipment = true;
			baseline.equipmentSlot = equipment.getSlot();
			baseline.levels = new int[] {
					equipment.getAttackLevel(), equipment.getStrengthLevel(),
					equipment.getDefenceLevel(), equipment.getHitpointsLevel(),
					equipment.getRangedLevel(), equipment.getPrayerLevel(),
					equipment.getMagicLevel()
			};
		}
		baseline.bonuses = ItemDefinitions.copyBonus(itemId);
		baseline.hadBonuses = ItemDefinitions.hasBonus(itemId);
		itemBaselines.put(Integer.valueOf(itemId), baseline);
	}

	private NpcList captureNpcBaselineIfNeeded(int npcId) {
		NpcBaseline baseline = npcBaselines.get(Integer.valueOf(npcId));
		if (baseline != null) {
			return baseline.entry;
		}
		NpcList existing = findNpcList(npcId);
		baseline = new NpcBaseline();
		if (existing != null) {
			baseline.existed = true;
			baseline.entry = existing;
			baseline.name = existing.npcName;
			baseline.combat = existing.npcCombat;
			baseline.health = existing.npcHealth;
		} else {
			baseline.existed = false;
			baseline.entry = allocateNpcList(npcId);
		}
		npcBaselines.put(Integer.valueOf(npcId), baseline);
		return baseline.entry;
	}

	private void captureObjectBaselineIfNeeded(int objectId) {
		if (objectBaselines.containsKey(Integer.valueOf(objectId))) {
			return;
		}
		ObjectDefinition definition = ObjectDefinition.lookup(objectId);
		ObjectBaseline baseline = new ObjectBaseline();
		baseline.name = definition.getName();
		baseline.description = definition.getDescription();
		String[] actions = definition.getMenuActions();
		baseline.actions = actions == null ? null : actions.clone();
		baseline.interactive = definition.isInteractive();
		objectBaselines.put(Integer.valueOf(objectId), baseline);
	}

	private void revertItem(int itemId) {
		ItemBaseline baseline = itemBaselines.remove(Integer.valueOf(itemId));
		if (baseline == null) {
			return;
		}
		ItemDefinition definition = ItemDefinition.lookup(itemId);
		definition.setName(baseline.name);
		definition.setDescription(baseline.description);
		definition.setStackable(baseline.stackable);
		ItemData.targetSlots[itemId] = baseline.targetSlot;
		if (baseline.hadEquipment) {
			EquipmentDefinition equipment = EquipmentDefinition.ensure(itemId);
			equipment.setSlot(baseline.equipmentSlot);
			equipment.setLevels(baseline.levels[0], baseline.levels[1],
					baseline.levels[2], baseline.levels[3], baseline.levels[4],
					baseline.levels[5], baseline.levels[6]);
		} else {
			EquipmentDefinition.remove(itemId);
		}
		if (baseline.hadBonuses) {
			ItemDefinitions.applyOverlay(itemId, baseline.bonuses);
		} else {
			ItemDefinitions.clearOverlay(itemId);
		}
	}

	private void revertNpc(int npcId) {
		NpcBaseline baseline = npcBaselines.remove(Integer.valueOf(npcId));
		if (baseline == null) {
			return;
		}
		if (!baseline.existed) {
			clearNpcList(npcId);
			return;
		}
		baseline.entry.npcName = baseline.name;
		baseline.entry.npcCombat = baseline.combat;
		baseline.entry.npcHealth = baseline.health;
	}

	private void revertObject(int objectId) {
		ObjectBaseline baseline = objectBaselines.remove(
				Integer.valueOf(objectId));
		if (baseline == null) {
			return;
		}
		ObjectDefinition definition = ObjectDefinition.lookup(objectId);
		definition.setName(baseline.name);
		definition.setDescription(baseline.description);
		definition.setMenuActions(baseline.actions);
		definition.setInteractive(baseline.interactive);
	}

	private static NpcList findNpcList(int npcId) {
		for (int index = 0; index < NpcHandler.maxListedNPCs; index++) {
			NpcList entry = NpcHandler.NpcList[index];
			if (entry != null && entry.npcId == npcId) {
				return entry;
			}
		}
		return null;
	}

	private static NpcList allocateNpcList(int npcId) {
		for (int index = 0; index < NpcHandler.maxListedNPCs; index++) {
			if (NpcHandler.NpcList[index] == null) {
				NpcList entry = new NpcList(npcId);
				NpcHandler.NpcList[index] = entry;
				return entry;
			}
		}
		throw new IllegalStateException("no free npc list slot for overlay");
	}

	private static void clearNpcList(int npcId) {
		for (int index = 0; index < NpcHandler.maxListedNPCs; index++) {
			NpcList entry = NpcHandler.NpcList[index];
			if (entry != null && entry.npcId == npcId) {
				NpcHandler.NpcList[index] = null;
				return;
			}
		}
	}

	private static int equipSlotIndex(String slot) {
		if ("hat".equals(slot)) {
			return ItemConstants.HAT;
		}
		if ("cape".equals(slot)) {
			return ItemConstants.CAPE;
		}
		if ("amulet".equals(slot)) {
			return ItemConstants.AMULET;
		}
		if ("weapon".equals(slot)) {
			return ItemConstants.WEAPON;
		}
		if ("chest".equals(slot)) {
			return ItemConstants.CHEST;
		}
		if ("shield".equals(slot)) {
			return ItemConstants.SHIELD;
		}
		if ("legs".equals(slot)) {
			return ItemConstants.LEGS;
		}
		if ("hands".equals(slot)) {
			return ItemConstants.HANDS;
		}
		if ("feet".equals(slot)) {
			return ItemConstants.FEET;
		}
		if ("ring".equals(slot)) {
			return ItemConstants.RING;
		}
		return ItemConstants.ARROWS;
	}

	private static final class ItemBaseline {
		private String name;
		private String description;
		private boolean stackable;
		private int targetSlot;
		private boolean hadEquipment;
		private int equipmentSlot;
		private int[] levels;
		private int[] bonuses;
		private boolean hadBonuses;
	}

	private static final class NpcBaseline {
		private boolean existed;
		private NpcList entry;
		private String name;
		private int combat;
		private int health;
	}

	private static final class ObjectBaseline {
		private String name;
		private String description;
		private String[] actions;
		private boolean interactive;
	}
}
