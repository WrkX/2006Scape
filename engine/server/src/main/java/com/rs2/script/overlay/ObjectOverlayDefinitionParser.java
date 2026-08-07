package com.rs2.script.overlay;

import org.graalvm.polyglot.Value;
import org.apollo.cache.def.ObjectDefinition;

import com.rs2.script.ScriptEntityLimits;
import com.rs2.script.registries.RegistryStore;

/**
 * Strict one-way parser for {@code defineObjectOverlay} schema-v1.
 */
public final class ObjectOverlayDefinitionParser {

	private final String source;
	private final int schemaVersion;

	public ObjectOverlayDefinitionParser(String source, int schemaVersion) {
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public ObjectOverlayDefinition parse(Value value) {
		if (value == null || !value.hasMembers()) {
			throw describe("definition must be an object");
		}
		String label = "defineObjectOverlay";
		OverlayParserSupport.only(value, label, "id", "objectId", "name",
				"examine", "actions");
		String id = OverlayParserSupport.requireId(value, label);
		int objectId = OverlayParserSupport.integral(
				OverlayParserSupport.required(value, label, "objectId"), label,
				"objectId", 0, ScriptEntityLimits.MAX_OBJECT_ID);
		requireLoadedObject(objectId);
		String name = OverlayParserSupport.optionalBoundedString(
				value.getMember("name"), label, "name", 64);
		String examine = OverlayParserSupport.optionalBoundedString(
				value.getMember("examine"), label, "examine", 256);
		String[] actions = OverlayParserSupport.optionalActions(
				value.getMember("actions"), label);
		if (name == null && examine == null && actions == null) {
			throw describe("overlay must set at least one field");
		}
		rejectDuplicateStableId(id, objectId);
		return new ObjectOverlayDefinition(id, objectId, name, examine, actions,
				source, schemaVersion);
	}

	private void requireLoadedObject(int objectId) {
		ObjectDefinition[] definitions = ObjectDefinition.getDefinitions();
		if (definitions == null || objectId >= definitions.length
				|| definitions[objectId] == null
				|| definitions[objectId].getId() != objectId) {
			throw describe("object id " + objectId
					+ " has no loaded definition");
		}
	}

	private void rejectDuplicateStableId(String id, int objectId) {
		if (!RegistryStore.isStagingActive()) {
			return;
		}
		for (ObjectOverlayDefinition existing
				: ObjectOverlayDefinitionRegistry.all(
						RegistryStore.writable()).values()) {
			if (existing.id().equals(id) && existing.objectId() != objectId) {
				throw describe("duplicate overlay id '" + id
						+ "' already registered by " + existing.source());
			}
		}
	}

	private IllegalArgumentException describe(String message) {
		return OverlayParserSupport.failure("defineObjectOverlay (source: "
				+ source + ")", message);
	}
}
