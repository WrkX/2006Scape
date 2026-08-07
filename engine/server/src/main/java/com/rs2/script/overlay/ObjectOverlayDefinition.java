package com.rs2.script.overlay;

/**
 * Immutable Java-owned schema-v1 object overlay descriptor.
 *
 * <p>Merges optional name, examine, and menu actions over an existing cache
 * object definition at script activation.
 */
public final class ObjectOverlayDefinition {

	private final String id;
	private final int objectId;
	private final String name;
	private final String examine;
	private final String[] actions;
	private final String source;
	private final int schemaVersion;

	public ObjectOverlayDefinition(String id, int objectId, String name,
			String examine, String[] actions, String source,
			int schemaVersion) {
		this.id = id;
		this.objectId = objectId;
		this.name = name;
		this.examine = examine;
		this.actions = actions;
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public String id() {
		return id;
	}

	public int objectId() {
		return objectId;
	}

	public String name() {
		return name;
	}

	public String examine() {
		return examine;
	}

	/**
	 * Up to five menu actions, or {@code null} when unset.
	 */
	public String[] actions() {
		return actions;
	}

	public String source() {
		return source;
	}

	public int schemaVersion() {
		return schemaVersion;
	}

	@Override
	public String toString() {
		return "object-overlay '" + id + "' (objectId: " + objectId
				+ ", source: " + source + ", schema v" + schemaVersion + ")";
	}
}
