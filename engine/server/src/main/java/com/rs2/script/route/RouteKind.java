package com.rs2.script.route;

/**
 * Executable route families handled by the unified route registry.
 *
 * <p>A route is a registration that owns a consumed-versus-legacy decision
 * for one exact packet or command key. Observational login, logout, NPC
 * death, item pickup, and area callbacks are deliberately not routes: they
 * never suppress legacy behavior.
 */
public enum RouteKind {

	/** Player command by canonical lower-case name. */
	COMMAND,

	/** Static object interaction by object id and ordinal action. */
	OBJECT,

	/** NPC interaction by npc id and ordinal action. */
	NPC,

	/** Inventory item click by item id and ordinal action. */
	ITEM,

	/** Order-insensitive exact item pair. */
	ITEM_ON_ITEM,

	/** Exact item-on-object pair. */
	ITEM_ON_OBJECT,

	/** Exact item-on-NPC pair. */
	ITEM_ON_NPC,

	/** Sparse decodable button key. */
	BUTTON,

	/** Exact item-on-ground-item pair. */
	ITEM_ON_GROUND_ITEM,

	/** Exact item-on-player id. */
	ITEM_ON_PLAYER,

	/** Exact magic-on-item pair. */
	MAGIC_ON_ITEM,

	/** Exact magic-on-object pair. */
	MAGIC_ON_OBJECT

}
