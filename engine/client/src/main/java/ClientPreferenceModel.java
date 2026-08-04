/**
 * Canonical client preference values with validation and clamping.
 */
final class ClientPreferenceModel {

	static final int VERSION = 1;

	static final int MIN_WINDOW_WIDTH = 765;
	static final int MIN_WINDOW_HEIGHT = 503;
	static final int MAX_WINDOW_WIDTH = 7680;
	static final int MAX_WINDOW_HEIGHT = 4320;
	static final int MIN_WORLD = 1;
	static final int MAX_WORLD = 999;

	int chatHeightPreset = 0;
	int chatFontPreset = 1;
	int sidePanelMode = 0;
	int minimapScalePreset = 0;
	boolean chatOverlay = true;
	boolean chatHidden = false;
	float chatOpacity = 0.88F;
	String uiScaleMode = UiScale.Mode.PERCENT_100.name();
	boolean rememberUsername = false;
	String rememberedUsername = "";

	int windowWidth = MIN_WINDOW_WIDTH;
	int windowHeight = MIN_WINDOW_HEIGHT;
	int windowX;
	int windowY;
	boolean windowMaximized;
	boolean windowFullscreen;
	boolean hasWindowState;

	int lastWorld = 1;

	ClientPreferenceModel copy() {
		ClientPreferenceModel copy = new ClientPreferenceModel();
		copy.chatHeightPreset = chatHeightPreset;
		copy.chatFontPreset = chatFontPreset;
		copy.sidePanelMode = sidePanelMode;
		copy.minimapScalePreset = minimapScalePreset;
		copy.chatOverlay = chatOverlay;
		copy.chatHidden = chatHidden;
		copy.chatOpacity = chatOpacity;
		copy.uiScaleMode = uiScaleMode;
		copy.rememberUsername = rememberUsername;
		copy.rememberedUsername = rememberedUsername;
		copy.windowWidth = windowWidth;
		copy.windowHeight = windowHeight;
		copy.windowX = windowX;
		copy.windowY = windowY;
		copy.windowMaximized = windowMaximized;
		copy.windowFullscreen = windowFullscreen;
		copy.hasWindowState = hasWindowState;
		copy.lastWorld = lastWorld;
		return copy;
	}

	void applyFrom(ClientPreferenceModel source) {
		chatHeightPreset = source.chatHeightPreset;
		chatFontPreset = source.chatFontPreset;
		sidePanelMode = source.sidePanelMode;
		minimapScalePreset = source.minimapScalePreset;
		chatOverlay = source.chatOverlay;
		chatHidden = source.chatHidden;
		chatOpacity = source.chatOpacity;
		uiScaleMode = source.uiScaleMode;
		rememberUsername = source.rememberUsername;
		rememberedUsername = source.rememberedUsername;
		windowWidth = source.windowWidth;
		windowHeight = source.windowHeight;
		windowX = source.windowX;
		windowY = source.windowY;
		windowMaximized = source.windowMaximized;
		windowFullscreen = source.windowFullscreen;
		hasWindowState = source.hasWindowState;
		lastWorld = source.lastWorld;
	}

	void clamp() {
		chatHeightPreset = ClientPreferences.clamp(chatHeightPreset, 0, 2);
		chatFontPreset = ClientPreferences.clamp(chatFontPreset, 0, 2);
		sidePanelMode = ClientPreferences.clamp(sidePanelMode, 0, 2);
		minimapScalePreset = ClientPreferences.clamp(minimapScalePreset, 0, 2);
		chatOpacity = ClientPreferences.clamp(chatOpacity, 0.2F, 1.0F);
		uiScaleMode = UiScale.sanitizeModeName(uiScaleMode);
		rememberedUsername = ClientPreferences.sanitizeUsername(rememberedUsername);
		windowWidth = ClientPreferences.clamp(windowWidth, MIN_WINDOW_WIDTH, MAX_WINDOW_WIDTH);
		windowHeight = ClientPreferences.clamp(windowHeight, MIN_WINDOW_HEIGHT, MAX_WINDOW_HEIGHT);
		lastWorld = ClientPreferences.clamp(lastWorld, MIN_WORLD, MAX_WORLD);
	}
}
