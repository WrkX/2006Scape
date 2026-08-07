package com.rs2.script.overlay;

import org.graalvm.polyglot.Value;
import org.apollo.cache.def.ItemDefinition;

import com.rs2.script.ScriptEntityLimits;
import com.rs2.script.definition.ModuleScope;
import com.rs2.script.registries.RegistryStore;

/**
 * Strict one-way parser for {@code defineItemOverlay} schema-v1.
 */
public final class ItemOverlayDefinitionParser {

	private final String source;
	private final int schemaVersion;

	public ItemOverlayDefinitionParser(String source, int schemaVersion) {
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public ItemOverlayDefinition parse(Value value) {
		if (value == null || !value.hasMembers()) {
			throw describe("definition must be an object");
		}
		String label = "defineItemOverlay";
		OverlayParserSupport.only(value, label, "id", "itemId", "name",
				"examine", "stackable", "equipSlot", "requirements", "bonuses");
		String id = OverlayParserSupport.requireId(value, label);
		int itemId = OverlayParserSupport.integral(
				OverlayParserSupport.required(value, label, "itemId"), label,
				"itemId", 0, ScriptEntityLimits.MAX_ITEM_ID);
		requireLoadedItem(itemId);
		String name = OverlayParserSupport.optionalBoundedString(
				value.getMember("name"), label, "name", 64);
		String examine = OverlayParserSupport.optionalBoundedString(
				value.getMember("examine"), label, "examine", 256);
		Boolean stackable = OverlayParserSupport.optionalBoolean(
				value.getMember("stackable"), label, "stackable");
		String equipSlot = OverlayParserSupport.requireEquipSlot(
				OverlayParserSupport.optionalBoundedString(
						value.getMember("equipSlot"), label, "equipSlot", 16),
				label);
		int[] requirements = OverlayParserSupport.optionalRequirementLevels(
				value.getMember("requirements"), label);
		int[] bonuses = OverlayParserSupport.optionalBonuses(
				value.getMember("bonuses"), label);
		if (name == null && examine == null && stackable == null
				&& equipSlot == null && requirements == null
				&& bonuses == null) {
			throw describe("overlay must set at least one field");
		}
		rejectDuplicateStableId(id, itemId);
		return new ItemOverlayDefinition(id, itemId, name, examine, stackable,
				equipSlot, requirements, bonuses, source, schemaVersion);
	}

	private void requireLoadedItem(int itemId) {
		if (!ItemDefinition.exists(itemId)) {
			throw describe("item id " + itemId + " has no loaded definition");
		}
	}

	private void rejectDuplicateStableId(String id, int itemId) {
		if (!RegistryStore.isStagingActive()) {
			return;
		}
		for (ItemOverlayDefinition existing : ItemOverlayDefinitionRegistry
				.all(RegistryStore.writable()).values()) {
			if (existing.id().equals(id) && existing.itemId() != itemId) {
				throw describe("duplicate overlay id '" + id
						+ "' already registered by " + existing.source());
			}
		}
	}

	private IllegalArgumentException describe(String message) {
		return OverlayParserSupport.failure("defineItemOverlay (source: "
				+ source + ")", message);
	}
}
