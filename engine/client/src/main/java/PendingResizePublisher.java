import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds a single pending resize request for game-thread consumption.
 * Publishing replaces any earlier unpublished request.
 */
final class PendingResizePublisher {

	private final AtomicReference<PendingResizeRequest> pending =
			new AtomicReference<>();

	void publish(int width, int height) {
		if (width < ClientPreferenceModel.MIN_WINDOW_WIDTH
				|| height < ClientPreferenceModel.MIN_WINDOW_HEIGHT) {
			return;
		}
		pending.set(PendingResizeRequest.of(width, height));
	}

	PendingResizeRequest poll() {
		return pending.getAndSet(null);
	}
}
