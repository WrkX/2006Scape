package com.rs2.script.definition;

import org.graalvm.polyglot.Value;

import com.rs2.script.drop.DropTableDefinition;
import com.rs2.script.quest.QuestDefinition;
import com.rs2.script.reward.RewardDefinition;

/**
 * Immutable common envelope of one registered content definition.
 *
 * <p>Every definition family is recorded with its kind, canonical stable key,
 * declared schema version, bounded logical source module, and exactly one
 * payload: the generation-owned legacy guest value or a Java-owned typed
 * descriptor. Final per-family schemas, callback contracts, loaded-id rules,
 * and cross-references are owned by the work package that consumes the
 * family; this envelope carries no family-specific member validation.
 */
public final class DefinitionRecord {

	private final DefinitionKind kind;
	private final String key;
	private final int schemaVersion;
	private final String source;
	private final Value guestPayload;
	private final Object typedPayload;

	public DefinitionRecord(DefinitionKind kind, String key, int schemaVersion,
			String source, Value guestPayload, Object typedPayload) {
		if (kind == null) {
			throw new IllegalArgumentException("definition kind must not be null");
		}
		if (key == null || key.trim().isEmpty()) {
			throw new IllegalArgumentException("definition key must not be empty");
		}
		if (schemaVersion < 0 || schemaVersion > 255) {
			throw new IllegalArgumentException(
					"definition schema version must be between 0 and 255");
		}
		if (source == null || source.trim().isEmpty()) {
			throw new IllegalArgumentException("definition source must not be empty");
		}
		if ((guestPayload == null) == (typedPayload == null)) {
			throw new IllegalArgumentException(
					"definition must carry exactly one payload");
		}
		this.kind = kind;
		this.key = key;
		this.schemaVersion = schemaVersion;
		this.source = source;
		this.guestPayload = guestPayload;
		this.typedPayload = typedPayload;
	}

	public static DefinitionRecord legacyGuest(DefinitionKind kind, String key,
			Value guestPayload) {
		return new DefinitionRecord(kind, key, 0, ModuleScope.LEGACY_SOURCE,
				guestPayload, null);
	}

	public static DefinitionRecord guest(DefinitionKind kind, String key,
			int schemaVersion, String source, Value guestPayload) {
		return new DefinitionRecord(kind, key, schemaVersion, source,
				guestPayload, null);
	}

	public static DefinitionRecord typed(DefinitionKind kind, String key,
			int schemaVersion, String source, Object typedPayload) {
		return new DefinitionRecord(kind, key, schemaVersion, source, null,
				typedPayload);
	}

	public static DefinitionRecord quest(int schemaVersion, String source,
			QuestDefinition questPayload) {
		return new DefinitionRecord(DefinitionKind.QUEST,
				questPayload.getId(), schemaVersion, source, null, questPayload);
	}

	public DefinitionKind kind() {
		return kind;
	}

	public String key() {
		return key;
	}

	public int schemaVersion() {
		return schemaVersion;
	}

	/** Bounded logical source module id, or {@link ModuleScope#LEGACY_SOURCE}. */
	public String source() {
		return source;
	}

	/** Generation-owned legacy guest payload; {@code null} for typed records. */
	public Value guestPayload() {
		return guestPayload;
	}

	public boolean isGuestPayload() {
		return guestPayload != null;
	}

	/** Java-owned typed descriptor; {@code null} for legacy guest records. */
	public Object typedPayload() {
		return typedPayload;
	}

	public QuestDefinition questPayload() {
		return (QuestDefinition) typedPayload;
	}

	public DropTableDefinition dropTablePayload() {
		return (DropTableDefinition) typedPayload;
	}

	public RewardDefinition rewardPayload() {
		return (RewardDefinition) typedPayload;
	}

	public com.rs2.script.boss.BossDefinition bossPayload() {
		return (com.rs2.script.boss.BossDefinition) typedPayload;
	}

	public com.rs2.script.area.AreaDefinition areaPayload() {
		return (com.rs2.script.area.AreaDefinition) typedPayload;
	}

	public com.rs2.script.shop.ShopDefinition shopPayload() {
		return (com.rs2.script.shop.ShopDefinition) typedPayload;
	}

	public com.rs2.script.raid.RaidDefinition raidPayload() {
		return (com.rs2.script.raid.RaidDefinition) typedPayload;
	}

	public com.rs2.script.minigame.MinigameDefinition minigamePayload() {
		return (com.rs2.script.minigame.MinigameDefinition) typedPayload;
	}

	@Override
	public String toString() {
		return kind.name().toLowerCase(java.util.Locale.ROOT) + ":" + key
				+ " (source: " + source + ", schema v" + schemaVersion + ")";
	}

}
