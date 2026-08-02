package com.rs2.script.quest;

import org.graalvm.polyglot.HostAccess;

/**
 * Stable, method-shaped result returned to guest quest callbacks.
 */
public final class QuestResult {

	private final boolean ok;
	private final boolean changed;
	private final QuestResultCode code;

	private QuestResult(boolean ok, boolean changed, QuestResultCode code) {
		this.ok = ok;
		this.changed = changed;
		this.code = code;
	}

	static QuestResult changed(QuestResultCode code) {
		return new QuestResult(true, true, code);
	}

	static QuestResult unchanged(boolean ok, QuestResultCode code) {
		return new QuestResult(ok, false, code);
	}

	@HostAccess.Export
	public boolean ok() {
		return ok;
	}

	@HostAccess.Export
	public boolean changed() {
		return changed;
	}

	@HostAccess.Export
	public String code() {
		return code.wireCode();
	}
}
