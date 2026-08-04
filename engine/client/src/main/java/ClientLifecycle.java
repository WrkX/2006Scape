/**
 * Explicit client lifecycle state used by the game loop.
 * Replaces the implicit Applet start/stop/destroy counters.
 */
final class ClientLifecycle {

	static final int RUNNING = 0;
	static final int SHUTDOWN_REQUESTED = -1;
	static final int SHUTDOWN_COMPLETE = -2;

	private int state = RUNNING;

	boolean isRunning() {
		return state >= RUNNING;
	}

	boolean isShutdownRequested() {
		return state == SHUTDOWN_REQUESTED;
	}

	boolean isShutdownComplete() {
		return state == SHUTDOWN_COMPLETE;
	}

	void startClient() {
		if (state > RUNNING) {
			state = RUNNING;
		}
	}

	void requestShutdown() {
		if (state >= RUNNING) {
			state = SHUTDOWN_REQUESTED;
		}
	}

	void beginGracefulShutdown(int frameCount) {
		if (state >= RUNNING) {
			state = frameCount;
		}
	}

	void markShutdownComplete() {
		state = SHUTDOWN_COMPLETE;
	}

	/**
	 * @return true when the game loop should exit
	 */
	boolean tickShutdownCountdown() {
		if (state > RUNNING) {
			state--;
			return state == RUNNING;
		}
		return state == SHUTDOWN_REQUESTED;
	}

	int state() {
		return state;
	}

}
