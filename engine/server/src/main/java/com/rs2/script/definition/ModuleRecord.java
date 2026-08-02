package com.rs2.script.definition;

import org.graalvm.polyglot.Value;

import com.rs2.script.quest.QuestDefinition;

/**
 * One registered content module inside a candidate.
 *
 * <p>The module id is a bounded logical identifier, never a host path.
 * Optional {@code onLoad} and {@code onUnload} hooks are generation-owned
 * observer functions invoked by the runtime activation transaction.
 */
public final class ModuleRecord {

	private final String id;
	private final int schemaVersion;
	private final Value onLoad;
	private final Value onUnload;

	public ModuleRecord(String id, int schemaVersion, Value onLoad,
			Value onUnload) {
		this.id = id;
		this.schemaVersion = schemaVersion;
		this.onLoad = onLoad;
		this.onUnload = onUnload;
	}

	public String id() {
		return id;
	}

	public int schemaVersion() {
		return schemaVersion;
	}

	/** Active-generation observer, or {@code null} when not declared. */
	public Value onLoad() {
		return onLoad;
	}

	/** Old-generation observer, or {@code null} when not declared. */
	public Value onUnload() {
		return onUnload;
	}

	@Override
	public String toString() {
		return id + " (schema v" + schemaVersion + ")";
	}

}
