package com.rs2.script;

import org.graalvm.polyglot.HostAccess;

/** Immutable metadata for an observed NPC death. */
public final class NpcDeathScriptContext {

	@HostAccess.Export
	public final ScriptedNpc npc;
	@HostAccess.Export
	public final ScriptedPlayer killer;
	@HostAccess.Export
	public final ScriptedPosition position;
	@HostAccess.Export
	public final String action = "death";

	public NpcDeathScriptContext(ScriptedNpc npc, ScriptedPlayer killer,
			ScriptedPosition position) {
		this.npc = npc;
		this.killer = killer;
		this.position = position;
	}
}
