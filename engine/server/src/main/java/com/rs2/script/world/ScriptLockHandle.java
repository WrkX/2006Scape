package com.rs2.script.world;

import org.graalvm.polyglot.HostAccess;

/** Opaque, encounter-owned action or movement lock lease. */
public final class ScriptLockHandle {

	private final ScriptEncounterService service;
	private final long token;

	ScriptLockHandle(ScriptEncounterService service, long token) {
		this.service = service;
		this.token = token;
	}

	@HostAccess.Export
	public String token() {
		return Long.toUnsignedString(token);
	}

	@HostAccess.Export
	public boolean isActive() {
		return service.isLockActive(token);
	}

	@HostAccess.Export
	public boolean release() {
		return service.releaseLock(token);
	}
}
