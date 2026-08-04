/**
 * UI scale modes and scale-factor resolution.
 *
 * World viewport size is independent; this only affects interface presentation.
 */
final class UiScale {

	enum Mode {
		AUTO,
		PERCENT_100,
		PERCENT_125,
		PERCENT_150,
		PERCENT_175,
		PERCENT_200,
		PIXEL_PERFECT
	}

	private static Mode currentMode = Mode.PERCENT_100;

	private UiScale() {
	}

	static Mode currentMode() {
		return currentMode;
	}

	static void setMode(Mode mode) {
		currentMode = mode;
	}

	static void applyFromPreferences() {
		setMode(parseMode(ClientPreferences.uiScaleMode));
	}

	static Mode parseMode(String value) {
		if (value == null || value.isEmpty()) {
			return Mode.PERCENT_100;
		}
		try {
			return Mode.valueOf(value);
		} catch (IllegalArgumentException ignored) {
			return Mode.PERCENT_100;
		}
	}

	static String sanitizeModeName(String value) {
		return parseMode(value).name();
	}

	static double resolveScale(Mode mode, ClientSize size) {
		switch (mode) {
			case AUTO:
				return Math.min(
						(double) size.windowWidth / size.logicalWidth,
						(double) size.windowHeight / size.logicalHeight);
			case PERCENT_100:
				return 1.0D;
			case PERCENT_125:
				return 1.25D;
			case PERCENT_150:
				return 1.5D;
			case PERCENT_175:
				return 1.75D;
			case PERCENT_200:
				return 2.0D;
			case PIXEL_PERFECT:
				int integerScale = Math.min(
						size.windowWidth / size.logicalWidth,
						size.windowHeight / size.logicalHeight);
				return Math.max(1, integerScale);
			default:
				return 1.0D;
		}
	}

	static double currentScale(ClientSize size) {
		return resolveScale(currentMode(), size);
	}
}
