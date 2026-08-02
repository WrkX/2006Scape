package com.rs2.script.drop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.rs2.script.world.ScriptDropEntry;

/**
 * Immutable Java-owned named drop table.
 *
 * <p>Entries are canonical WP6 {@link ScriptDropEntry} values with copied
 * numeric ids only; no guest value, string id, or fractional weight survives
 * into the descriptor. The definition carries its bounded logical source and
 * declared schema version so diagnostics and duplicates identify the
 * registering module.
 */
public final class DropTableDefinition {

	private final String id;
	private final String source;
	private final int schemaVersion;
	private final List<ScriptDropEntry> entries;

	public DropTableDefinition(String id, String source, int schemaVersion,
			List<ScriptDropEntry> entries) {
		this.id = id;
		this.source = source;
		this.schemaVersion = schemaVersion;
		this.entries = Collections.unmodifiableList(
				new ArrayList<ScriptDropEntry>(entries));
	}

	public String id() {
		return id;
	}

	/** Bounded logical source module, or the legacy-unscoped marker. */
	public String source() {
		return source;
	}

	public int schemaVersion() {
		return schemaVersion;
	}

	public List<ScriptDropEntry> entries() {
		return entries;
	}

	@Override
	public String toString() {
		return "drop table '" + id + "' (source: " + source + ", schema v"
				+ schemaVersion + ")";
	}

}
