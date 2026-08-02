package com.rs2.script.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Value;

import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.ScriptExecutor;
import com.rs2.script.ScriptHost;

/**
 * Deterministic, game-cycle scheduler for player-owned script callbacks.
 *
 * <p>The scheduler never holds its monitor while entering guest code. Context
 * access is guarded by {@link ScriptHost}'s generation execution lease.
 */
public final class ScriptScheduler {

	public static final int MAX_TICKS = 100000;
	private static final ScriptScheduler INSTANCE = new ScriptScheduler();

	private final Map<Long, Task> tasks = new HashMap<>();
	private long currentTick;
	private long nextTaskId = 1;

	public static ScriptScheduler getInstance() {
		return INSTANCE;
	}

	public synchronized ScriptTaskHandle schedule(Player player, long generation,
			int ticks, boolean repeating, Value callback) {
		return schedule(player, generation, ticks, repeating, callback, null);
	}

	/**
	 * Schedules a callback with an engine-owned failure continuation. The
	 * continuation never enters guest code and runs after scheduler state has
	 * been finalized.
	 */
	public synchronized ScriptTaskHandle schedule(Player player, long generation,
			int ticks, boolean repeating, Value callback,
			Runnable failureAction) {
		if (player == null) {
			throw new IllegalArgumentException("scheduled task requires a player");
		}
		if (ticks < 1 || ticks > MAX_TICKS) {
			throw new IllegalArgumentException(
					"scheduled ticks must be between 1 and " + MAX_TICKS);
		}
		if (callback == null || callback.isNull() || !callback.canExecute()) {
			throw new IllegalArgumentException("scheduled callback must be executable");
		}
		long taskId = nextTaskId++;
		ScriptTaskHandle handle = new ScriptTaskHandle(this, taskId);
		tasks.put(taskId, new Task(taskId, player, generation,
				currentTick + ticks, repeating ? ticks : 0, callback, handle,
				failureAction));
		return handle;
	}

	public void processGameTick() {
		List<Task> due;
		synchronized (this) {
			currentTick++;
			due = new ArrayList<>();
			for (Task task : tasks.values()) {
				if (!task.cancelled && !task.running && task.dueTick <= currentTick) {
					task.running = true;
					due.add(task);
				}
			}
		}
		Collections.sort(due, new Comparator<Task>() {
			@Override
			public int compare(Task first, Task second) {
				int dueOrder = Long.compare(first.dueTick, second.dueTick);
				return dueOrder != 0 ? dueOrder : Long.compare(first.id, second.id);
			}
		});

		for (Task task : due) {
			final Task scheduled = task;
			final boolean[] claimed = { false };
			final boolean[] succeeded = { false };
			boolean leased = ScriptHost.getInstance().executeIfGenerationActive(
					task.generation, new Runnable() {
						@Override
						public void run() {
							Value callback = claim(scheduled);
							if (callback == null) {
								return;
							}
							claimed[0] = true;
							succeeded[0] = ScriptExecutor.executeChecked(
									callback, "task", Long.toString(scheduled.id),
									scheduled.interval > 0 ? "every" : "after",
									scheduled.handle);
						}
					});
			if (!leased || !claimed[0]) {
				releaseSkippedSnapshot(task);
				continue;
			}
			finish(task, succeeded[0]);
			if (!succeeded[0] && task.failureAction != null) {
				try {
					task.failureAction.run();
				} catch (RuntimeException ignored) {
					// Engine cleanup is best-effort after scheduler state is safe.
				}
			}
		}
	}

	private synchronized Value claim(Task task) {
		Task active = tasks.get(task.id);
		if (active != task || task.cancelled || !task.running
				|| task.dueTick > currentTick || !isLive(task.player)) {
			return null;
		}
		return task.callback;
	}

	private synchronized void releaseSkippedSnapshot(Task task) {
		Task active = tasks.get(task.id);
		if (active == task) {
			task.running = false;
			if (task.cancelled || task.callback == null || !isLive(task.player)) {
				removeTask(task, true);
			}
		}
	}

	private synchronized void finish(Task task, boolean succeeded) {
		Task active = tasks.get(task.id);
		if (active != task) {
			return;
		}
		task.running = false;
		if (task.cancelled || !succeeded || task.interval == 0) {
			if (task.cancelled || !succeeded) {
				task.handle.markCancelled();
			} else {
				task.handle.markCompleted();
			}
			tasks.remove(task.id);
			task.callback = null;
			return;
		}
		task.dueTick += task.interval;
	}

	public synchronized boolean cancel(long taskId) {
		Task task = tasks.remove(taskId);
		if (task == null || task.cancelled) {
			return false;
		}
		task.cancelled = true;
		task.handle.markCancelled();
		task.callback = null;
		return true;
	}

	public synchronized boolean isCancelled(long taskId) {
		Task task = tasks.get(taskId);
		return task != null && task.cancelled;
	}

	public synchronized void cancelGeneration(long generation) {
		Iterator<Task> iterator = tasks.values().iterator();
		while (iterator.hasNext()) {
			Task task = iterator.next();
			if (task.generation == generation) {
				task.cancelled = true;
				task.handle.markCancelled();
				task.callback = null;
				iterator.remove();
			}
		}
	}

	public synchronized void cancelPlayer(Player player) {
		Iterator<Task> iterator = tasks.values().iterator();
		while (iterator.hasNext()) {
			Task task = iterator.next();
			if (task.player == player) {
				task.cancelled = true;
				task.handle.markCancelled();
				task.callback = null;
				iterator.remove();
			}
		}
	}

	private static boolean isLive(Player player) {
		int playerId = player.playerId;
		return playerId >= 0 && playerId < PlayerHandler.players.length
				&& PlayerHandler.players[playerId] == player
				&& player.isActive && !player.disconnected;
	}

	private void removeTask(Task task, boolean cancelled) {
		tasks.remove(task.id);
		task.cancelled = cancelled;
		if (cancelled) {
			task.handle.markCancelled();
		}
		task.callback = null;
	}

	private static final class Task {
		private final long id;
		private final Player player;
		private final long generation;
		private long dueTick;
		private final int interval;
		private Value callback;
		private final ScriptTaskHandle handle;
		private final Runnable failureAction;
		private boolean running;
		private boolean cancelled;

		private Task(long id, Player player, long generation, long dueTick,
				int interval, Value callback, ScriptTaskHandle handle,
				Runnable failureAction) {
			this.id = id;
			this.player = player;
			this.generation = generation;
			this.dueTick = dueTick;
			this.interval = interval;
			this.callback = callback;
			this.handle = handle;
			this.failureAction = failureAction;
		}
	}

	private ScriptScheduler() {
	}
}
