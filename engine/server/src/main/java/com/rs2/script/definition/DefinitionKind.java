package com.rs2.script.definition;

/**
 * Definition families registered by TypeScript content modules.
 *
 * <p>Each kind is consumed by exactly one runtime owner (for example the
 * quest runtime for {@link #QUEST}). A kind that has no consumer yet remains
 * a data-only legacy record owned by its future consumer package; this class
 * is extended only by the work package that owns the consumer.
 */
public enum DefinitionKind {

	/** Declarative boss definitions keyed by NPC id. */
	BOSS,

	/** Declarative raid definitions keyed by stable string id. */
	RAID,

	/** Declarative area definitions keyed by stable string id. */
	AREA,

	/** Java-owned quest descriptors keyed by stable string id. */
	QUEST,

	/** Java-owned named drop tables keyed by stable string id. */
	DROP_TABLE,

	/** Java-owned named rewards keyed by stable string id. */
	REWARD,

	/** Java-owned scripted shop definitions keyed by stable string id. */
	SHOP

}
