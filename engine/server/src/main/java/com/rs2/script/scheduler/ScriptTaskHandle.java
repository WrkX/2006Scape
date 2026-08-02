package com.rs2.script.scheduler;

import org.graalvm.polyglot.HostAccess;

/**
 * Narrow cancellation handle exposed to a scheduled guest callback.
 */
public final class ScriptTaskHandle {

	private final ScriptScheduler scheduler;
	private final long taskId;
	private volatile boolean cancelled;
	private volatile boolean completed;

	ScriptTaskHandle(ScriptScheduler scheduler, long taskId) {
		this.scheduler = scheduler;
		this.taskId = taskId;
	}

	/** Non-null failure value for ScriptedPlayer.after/every. */
	public static ScriptTaskHandle rejected() {
		ScriptTaskHandle handle = new ScriptTaskHandle(null, 0L);
		handle.cancelled = true;
		return handle;
	}

	@HostAccess.Export
	public boolean cancel() {
		return scheduler != null && scheduler.cancel(taskId);
	}

	@HostAccess.Export
	public boolean isCancelled() {
		return cancelled;
	}

	void markCancelled() {
		cancelled = true;
	}

	void markCompleted() {
		completed = true;
	}

	/** Engine-only task ownership query; not exported to guest code. */
	public boolean isDoneInternal() {
		return cancelled || completed;
	}
}
