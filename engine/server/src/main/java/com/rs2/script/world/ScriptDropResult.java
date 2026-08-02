package com.rs2.script.world;

import java.util.Collections;

import org.graalvm.polyglot.HostAccess;

import com.rs2.script.ScriptArray;

/**
 * Immutable logical drop result. One handle owns the complete identity set of
 * the rolled entry; {@code groundItems()} exposes that single handle.
 */
public final class ScriptDropResult {

	private final int itemId;
	private final int amount;
	private final ScriptGroundItemHandle handle;

	public ScriptDropResult(int itemId, int amount, ScriptGroundItemHandle handle) {
		this.itemId = itemId;
		this.amount = amount;
		this.handle = handle;
	}

	@HostAccess.Export
	public int itemId() {
		return itemId;
	}

	@HostAccess.Export
	public int amount() {
		return amount;
	}

	@HostAccess.Export
	public ScriptArray groundItems() {
		return new ScriptArray(Collections.singletonList(handle));
	}
}
