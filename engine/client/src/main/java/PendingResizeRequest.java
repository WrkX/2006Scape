/**
 * Immutable window resize dimensions published from the Swing event thread
 * and applied on the game thread.
 */
final class PendingResizeRequest {

	final int width;
	final int height;

	private PendingResizeRequest(int width, int height) {
		this.width = width;
		this.height = height;
	}

	static PendingResizeRequest of(int width, int height) {
		int clampedWidth = ClientPreferences.clamp(
				width,
				ClientPreferenceModel.MIN_WINDOW_WIDTH,
				ClientPreferenceModel.MAX_WINDOW_WIDTH);
		int clampedHeight = ClientPreferences.clamp(
				height,
				ClientPreferenceModel.MIN_WINDOW_HEIGHT,
				ClientPreferenceModel.MAX_WINDOW_HEIGHT);
		return new PendingResizeRequest(clampedWidth, clampedHeight);
	}

	boolean isValid() {
		return width >= ClientPreferenceModel.MIN_WINDOW_WIDTH
				&& height >= ClientPreferenceModel.MIN_WINDOW_HEIGHT;
	}

	boolean matchesDimensions(int otherWidth, int otherHeight) {
		return width == otherWidth && height == otherHeight;
	}
}
