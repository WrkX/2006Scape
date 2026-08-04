/**
 * Window and logical UI dimensions used for scaling calculations.
 */
final class ClientSize {

	final int windowWidth;
	final int windowHeight;
	final int logicalWidth;
	final int logicalHeight;

	ClientSize(int windowWidth, int windowHeight, int logicalWidth, int logicalHeight) {
		this.windowWidth = windowWidth;
		this.windowHeight = windowHeight;
		this.logicalWidth = logicalWidth;
		this.logicalHeight = logicalHeight;
	}

	static ClientSize of(int windowWidth, int windowHeight) {
		return new ClientSize(
				windowWidth,
				windowHeight,
				ClientPreferences.LOGICAL_UI_WIDTH,
				ClientPreferences.LOGICAL_UI_HEIGHT);
	}
}
