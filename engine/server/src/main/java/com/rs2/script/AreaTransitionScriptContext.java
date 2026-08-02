package com.rs2.script;

import org.graalvm.polyglot.HostAccess;

import com.rs2.script.registries.ScriptArea;

/** Immutable metadata for an area boundary transition. */
public final class AreaTransitionScriptContext extends ScriptContext {

	@HostAccess.Export
	public final ScriptArea area;
	@HostAccess.Export
	public final ScriptedPosition from;
	@HostAccess.Export
	public final ScriptedPosition to;

	public AreaTransitionScriptContext(ScriptedPlayer player, ScriptArea area,
			ScriptedPosition from, ScriptedPosition to, String action) {
		super(player, area, action);
		this.area = area;
		this.from = from;
		this.to = to;
	}
}
