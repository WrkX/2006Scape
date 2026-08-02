package com.rs2.script.definition;

/**
 * Immutable identity of one registered definition inside a candidate.
 *
 * <p>The key is the canonical stable key of the kind: the numeric boss NPC
 * id as its decimal string, or the stable string id for quests, raids, and
 * areas.
 */
public final class DefinitionKey {

	private final DefinitionKind kind;
	private final String key;

	public DefinitionKey(DefinitionKind kind, String key) {
		if (kind == null) {
			throw new IllegalArgumentException("definition kind must not be null");
		}
		if (key == null || key.trim().isEmpty()) {
			throw new IllegalArgumentException("definition key must be non-empty");
		}
		this.kind = kind;
		this.key = key;
	}

	public static DefinitionKey of(DefinitionKind kind, String key) {
		return new DefinitionKey(kind, key);
	}

	public DefinitionKind kind() {
		return kind;
	}

	public String key() {
		return key;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof DefinitionKey)) {
			return false;
		}
		DefinitionKey that = (DefinitionKey) other;
		return kind == that.kind && key.equals(that.key);
	}

	@Override
	public int hashCode() {
		return 31 * kind.hashCode() + key.hashCode();
	}

	@Override
	public String toString() {
		return kind.name().toLowerCase(java.util.Locale.ROOT) + ":" + key;
	}

}
