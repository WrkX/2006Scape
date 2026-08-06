package com.rs2.script;

import com.rs2.game.items.ItemConstants;

/**
 * Canonical entity-id ceilings for the TypeScript content bridge and related
 * host validators.
 *
 * <p>Item capacity follows {@link ItemConstants#ITEM_LIMIT}. NPC and object
 * ceilings use unsigned 16-bit protocol bounds so OSRS-era cache packs can
 * register content without artificial 15k clamps.
 */
public final class ScriptEntityLimits {

	/** Inclusive maximum item id accepted by script validators. */
	public static final int MAX_ITEM_ID = ItemConstants.ITEM_LIMIT - 1;

	/** Inclusive maximum NPC type id accepted by script validators. */
	public static final int MAX_NPC_ID = 65535;

	/** Inclusive maximum object type id accepted by script validators. */
	public static final int MAX_OBJECT_ID = 65535;

	private ScriptEntityLimits() {
	}
}
