package com.rs2.script.overlay;

import org.graalvm.polyglot.Value;

import com.rs2.game.npcs.NpcHandler;
import com.rs2.script.ScriptEntityLimits;
import com.rs2.script.registries.RegistryStore;

/**
 * Strict one-way parser for {@code defineNpcOverlay} schema-v1.
 */
public final class NpcOverlayDefinitionParser {

	private final String source;
	private final int schemaVersion;

	public NpcOverlayDefinitionParser(String source, int schemaVersion) {
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public NpcOverlayDefinition parse(Value value) {
		if (value == null || !value.hasMembers()) {
			throw describe("definition must be an object");
		}
		String label = "defineNpcOverlay";
		OverlayParserSupport.only(value, label, "id", "npcId", "name",
				"combatLevel", "hitpoints");
		String id = OverlayParserSupport.requireId(value, label);
		int npcId = OverlayParserSupport.integral(
				OverlayParserSupport.required(value, label, "npcId"), label,
				"npcId", 0, ScriptEntityLimits.MAX_NPC_ID);
		requireLoadedNpc(npcId);
		String name = OverlayParserSupport.optionalBoundedString(
				value.getMember("name"), label, "name", 64);
		Integer combatLevel = OverlayParserSupport.hasMember(value,
				"combatLevel")
				? Integer.valueOf(OverlayParserSupport.integral(
						value.getMember("combatLevel"), label, "combatLevel",
						1, 65535))
				: null;
		Integer hitpoints = OverlayParserSupport.hasMember(value, "hitpoints")
				? Integer.valueOf(OverlayParserSupport.integral(
						value.getMember("hitpoints"), label, "hitpoints", 1,
						32767))
				: null;
		if (name == null && combatLevel == null && hitpoints == null) {
			throw describe("overlay must set at least one field");
		}
		rejectDuplicateStableId(id, npcId);
		return new NpcOverlayDefinition(id, npcId, name, combatLevel,
				hitpoints, source, schemaVersion);
	}

	private void requireLoadedNpc(int npcId) {
		if (!NpcHandler.hasNpcDefinition(npcId)) {
			throw describe("npc id " + npcId + " has no loaded definition");
		}
	}

	private void rejectDuplicateStableId(String id, int npcId) {
		if (!RegistryStore.isStagingActive()) {
			return;
		}
		for (NpcOverlayDefinition existing : NpcOverlayDefinitionRegistry
				.all(RegistryStore.writable()).values()) {
			if (existing.id().equals(id) && existing.npcId() != npcId) {
				throw describe("duplicate overlay id '" + id
						+ "' already registered by " + existing.source());
			}
		}
	}

	private IllegalArgumentException describe(String message) {
		return OverlayParserSupport.failure("defineNpcOverlay (source: "
				+ source + ")", message);
	}
}
