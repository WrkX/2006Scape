import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Versioned local preference store for display, account convenience, and window state.
 *
 * Account storage policy: remembered username and last world are persisted when enabled;
 * passwords are never written to client.properties, command-line arguments in production,
 * or diagnostic output. Legacy password keys are ignored on load.
 */
public final class ClientPreferences {

	public static final int LOGICAL_UI_WIDTH = 765;
	public static final int LOGICAL_UI_HEIGHT = 503;
	public static final int CURRENT_VERSION = ClientPreferenceModel.VERSION;

	public static int chatHeightPreset = 0;
	public static int chatFontPreset = 1;
	public static int sidePanelMode = 0;
	public static int minimapScalePreset = 0;
	public static boolean chatOverlay = true;
	public static boolean chatHidden = false;
	public static float chatOpacity = 0.88F;
	public static String uiScaleMode = UiScale.Mode.PERCENT_100.name();
	public static boolean rememberUsername = false;
	public static String rememberedUsername = "";

	public static int windowWidth = ClientPreferenceModel.MIN_WINDOW_WIDTH;
	public static int windowHeight = ClientPreferenceModel.MIN_WINDOW_HEIGHT;
	public static int windowX;
	public static int windowY;
	public static boolean windowMaximized;
	public static boolean windowFullscreen;
	public static boolean hasWindowState;
	public static int lastWorld = 1;

	private static final String VERSION_KEY = "preferences.version";
	private static final String CHAT_HEIGHT = "chatHeightPreset";
	private static final String CHAT_FONT = "chatFontPreset";
	private static final String SIDE_PANEL_MODE = "sidePanelMode";
	private static final String MINIMAP_SCALE_PRESET = "minimapScalePreset";
	private static final String CHAT_OVERLAY = "chatOverlay";
	private static final String CHAT_HIDDEN = "chatHidden";
	private static final String CHAT_OPACITY = "chatOpacity";
	private static final String UI_SCALE_MODE = "uiScaleMode";
	private static final String REMEMBER_USERNAME = "rememberUsername";
	private static final String REMEMBERED_USERNAME = "rememberedUsername";
	private static final String WINDOW_WIDTH = "windowWidth";
	private static final String WINDOW_HEIGHT = "windowHeight";
	private static final String WINDOW_X = "windowX";
	private static final String WINDOW_Y = "windowY";
	private static final String WINDOW_MAXIMIZED = "windowMaximized";
	private static final String WINDOW_FULLSCREEN = "windowFullscreen";
	private static final String HAS_WINDOW_STATE = "hasWindowState";
	private static final String LAST_WORLD = "lastWorld";

	private static final Object SAVE_LOCK = new Object();
	private static final ScheduledExecutorService SAVE_EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "client-preferences-save");
		thread.setDaemon(true);
		return thread;
	});

	private static final ClientPreferenceModel model = new ClientPreferenceModel();
	private static File preferencesFileOverride;
	private static long saveDebounceMs = 500L;
	private static ScheduledFuture<?> pendingSave;

	private ClientPreferences() {
	}

	public static void load() {
		Properties properties = new Properties();
		File file = getFile();
		if (file.isFile()) {
			try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
				properties.load(reader);
			} catch (IOException exception) {
				logFailure("load", file, exception);
			}
		}

		int version = readInt(properties, VERSION_KEY, 0);
		if (version > CURRENT_VERSION) {
			ClientLogger.warn("Client preferences version " + version
					+ " is newer than supported version " + CURRENT_VERSION + "; using safe defaults where needed.");
		}

		ClientPreferenceModel loaded = fromProperties(properties, version);
		loaded.clamp();
		applyModel(loaded);
	}

	public static void save() {
		synchronized (SAVE_LOCK) {
			if (pendingSave != null) {
				pendingSave.cancel(false);
			}
			pendingSave = SAVE_EXECUTOR.schedule(ClientPreferences::saveNow, saveDebounceMs, TimeUnit.MILLISECONDS);
		}
	}

	static void saveImmediately() {
		synchronized (SAVE_LOCK) {
			if (pendingSave != null) {
				pendingSave.cancel(false);
				pendingSave = null;
			}
		}
		saveNow();
	}

	static void saveNow() {
		synchronized (SAVE_LOCK) {
			pendingSave = null;
		}

		syncModelFromFields();
		Properties properties = toProperties(model);
		File file = getFile();
		try {
			writeAtomically(properties, file);
		} catch (IOException exception) {
			logFailure("save", file, exception);
		}
	}

	static boolean isSavePending() {
		synchronized (SAVE_LOCK) {
			return pendingSave != null && !pendingSave.isDone();
		}
	}

	static void captureWindowState(Frame frame) {
		if (frame == null) {
			return;
		}

		hasWindowState = true;
		windowMaximized = (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
		windowFullscreen = false;

		if (windowMaximized) {
			Rectangle bounds = frame.getBounds();
			windowWidth = bounds.width;
			windowHeight = bounds.height;
			windowX = bounds.x;
			windowY = bounds.y;
		} else {
			windowWidth = frame.getWidth();
			windowHeight = frame.getHeight();
			windowX = frame.getX();
			windowY = frame.getY();
		}
	}

	static void applyWindowState(Frame frame) {
		if (frame == null) {
			return;
		}
		if (!hasWindowState) {
			frame.setLocationRelativeTo(null);
			return;
		}

		int width = clamp(windowWidth, ClientPreferenceModel.MIN_WINDOW_WIDTH, ClientPreferenceModel.MAX_WINDOW_WIDTH);
		int height = clamp(windowHeight, ClientPreferenceModel.MIN_WINDOW_HEIGHT, ClientPreferenceModel.MAX_WINDOW_HEIGHT);
		if (ClientSettings.RESIZABLE) {
			frame.setSize(width, height);
		}

		if (windowMaximized) {
			frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
			return;
		}

		if (isWindowPositionOnScreen(windowX, windowY, frame.getWidth(), frame.getHeight())) {
			frame.setLocation(windowX, windowY);
		} else {
			frame.setLocationRelativeTo(null);
		}
	}

	static boolean isWindowPositionOnScreen(int x, int y, int width, int height) {
		return windowIntersectsMonitor(x, y, width, height, getMonitorBounds());
	}

	static boolean windowIntersectsMonitor(int x, int y, int width, int height, Rectangle[] monitorBounds) {
		if (width <= 0 || height <= 0) {
			return false;
		}
		Rectangle window = new Rectangle(x, y, width, height);
		for (Rectangle monitor : monitorBounds) {
			if (monitor.intersects(window)) {
				return true;
			}
		}
		return false;
	}

	static Rectangle[] getMonitorBounds() {
		GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] devices = environment.getScreenDevices();
		int configurationCount = 0;
		for (GraphicsDevice device : devices) {
			configurationCount += device.getConfigurations().length;
		}

		Rectangle[] bounds = new Rectangle[configurationCount];
		int index = 0;
		for (GraphicsDevice device : devices) {
			for (GraphicsConfiguration configuration : device.getConfigurations()) {
				bounds[index++] = configuration.getBounds();
			}
		}
		return bounds;
	}

	static void applyAccountSettings() {
		if (lastWorld >= ClientPreferenceModel.MIN_WORLD) {
			ClientSettings.SERVER_WORLD = lastWorld;
		}
	}

	static void captureSelectedWorld() {
		lastWorld = clamp(ClientSettings.SERVER_WORLD, ClientPreferenceModel.MIN_WORLD, ClientPreferenceModel.MAX_WORLD);
	}

	static void setPreferencesFileForTesting(File file) {
		preferencesFileOverride = file;
	}

	static void clearPreferencesFileForTesting() {
		preferencesFileOverride = null;
	}

	static void setSaveDebounceMsForTesting(long debounceMs) {
		saveDebounceMs = debounceMs;
	}

	static void resetSaveDebounceMsForTesting() {
		saveDebounceMs = 500L;
	}

	static void resetToDefaults() {
		applyModel(new ClientPreferenceModel());
	}

	static void flushPendingSaveForTesting() {
		saveImmediately();
	}

	static String sanitizeUsername(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		StringBuilder filtered = new StringBuilder();
		for (int i = 0; i < value.length() && filtered.length() < 12; i++) {
			char c = value.charAt(i);
			if (LoginController.VALID_CHARS.indexOf(c) >= 0) {
				filtered.append(c);
			}
		}
		return filtered.toString();
	}

	static int readInt(Properties properties, String key, int fallback) {
		try {
			return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	static float readFloat(Properties properties, String key, float fallback) {
		try {
			return Float.parseFloat(properties.getProperty(key, Float.toString(fallback)));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static ClientPreferenceModel fromProperties(Properties properties, int version) {
		ClientPreferenceModel loaded = new ClientPreferenceModel();
		loaded.chatHeightPreset = readInt(properties, CHAT_HEIGHT, loaded.chatHeightPreset);
		loaded.chatFontPreset = readInt(properties, CHAT_FONT, loaded.chatFontPreset);
		loaded.sidePanelMode = readInt(properties, SIDE_PANEL_MODE, loaded.sidePanelMode);
		loaded.minimapScalePreset = readInt(properties, MINIMAP_SCALE_PRESET, loaded.minimapScalePreset);
		loaded.chatOverlay = Boolean.parseBoolean(properties.getProperty(CHAT_OVERLAY, "true"));
		loaded.chatHidden = Boolean.parseBoolean(properties.getProperty(CHAT_HIDDEN, "false"));
		loaded.chatOpacity = readFloat(properties, CHAT_OPACITY, loaded.chatOpacity);
		loaded.uiScaleMode = properties.getProperty(UI_SCALE_MODE, loaded.uiScaleMode);
		loaded.rememberUsername = Boolean.parseBoolean(properties.getProperty(REMEMBER_USERNAME, "false"));
		loaded.rememberedUsername = sanitizeUsername(properties.getProperty(REMEMBERED_USERNAME, ""));

		if (properties.containsKey("password") || properties.containsKey("rememberedPassword")) {
			System.err.println("Ignoring legacy password entries in client preferences; passwords are never stored.");
		}

		if (version >= 1) {
			loaded.windowWidth = readInt(properties, WINDOW_WIDTH, loaded.windowWidth);
			loaded.windowHeight = readInt(properties, WINDOW_HEIGHT, loaded.windowHeight);
			loaded.windowX = readInt(properties, WINDOW_X, loaded.windowX);
			loaded.windowY = readInt(properties, WINDOW_Y, loaded.windowY);
			loaded.windowMaximized = Boolean.parseBoolean(properties.getProperty(WINDOW_MAXIMIZED, "false"));
			loaded.windowFullscreen = Boolean.parseBoolean(properties.getProperty(WINDOW_FULLSCREEN, "false"));
			loaded.hasWindowState = Boolean.parseBoolean(properties.getProperty(HAS_WINDOW_STATE, "false"));
			loaded.lastWorld = readInt(properties, LAST_WORLD, loaded.lastWorld);
		}

		return migrate(loaded, version);
	}

	private static ClientPreferenceModel migrate(ClientPreferenceModel loaded, int version) {
		if (version >= CURRENT_VERSION) {
			return loaded;
		}

		// Version 0 files omit preferences.version and window/world keys.
		return loaded;
	}

	private static Properties toProperties(ClientPreferenceModel source) {
		Properties properties = new Properties();
		properties.setProperty(VERSION_KEY, Integer.toString(CURRENT_VERSION));
		properties.setProperty(CHAT_HEIGHT, Integer.toString(source.chatHeightPreset));
		properties.setProperty(CHAT_FONT, Integer.toString(source.chatFontPreset));
		properties.setProperty(SIDE_PANEL_MODE, Integer.toString(source.sidePanelMode));
		properties.setProperty(MINIMAP_SCALE_PRESET, Integer.toString(source.minimapScalePreset));
		properties.setProperty(CHAT_OVERLAY, Boolean.toString(source.chatOverlay));
		properties.setProperty(CHAT_HIDDEN, Boolean.toString(source.chatHidden));
		properties.setProperty(CHAT_OPACITY, Float.toString(source.chatOpacity));
		properties.setProperty(UI_SCALE_MODE, source.uiScaleMode);
		properties.setProperty(REMEMBER_USERNAME, Boolean.toString(source.rememberUsername));
		properties.setProperty(REMEMBERED_USERNAME, source.rememberedUsername);
		properties.setProperty(WINDOW_WIDTH, Integer.toString(source.windowWidth));
		properties.setProperty(WINDOW_HEIGHT, Integer.toString(source.windowHeight));
		properties.setProperty(WINDOW_X, Integer.toString(source.windowX));
		properties.setProperty(WINDOW_Y, Integer.toString(source.windowY));
		properties.setProperty(WINDOW_MAXIMIZED, Boolean.toString(source.windowMaximized));
		properties.setProperty(WINDOW_FULLSCREEN, Boolean.toString(source.windowFullscreen));
		properties.setProperty(HAS_WINDOW_STATE, Boolean.toString(source.hasWindowState));
		properties.setProperty(LAST_WORLD, Integer.toString(source.lastWorld));
		return properties;
	}

	private static void applyModel(ClientPreferenceModel source) {
		model.applyFrom(source);
		chatHeightPreset = model.chatHeightPreset;
		chatFontPreset = model.chatFontPreset;
		sidePanelMode = model.sidePanelMode;
		minimapScalePreset = model.minimapScalePreset;
		chatOverlay = model.chatOverlay;
		chatHidden = model.chatHidden;
		chatOpacity = model.chatOpacity;
		uiScaleMode = model.uiScaleMode;
		rememberUsername = model.rememberUsername;
		rememberedUsername = model.rememberedUsername;
		windowWidth = model.windowWidth;
		windowHeight = model.windowHeight;
		windowX = model.windowX;
		windowY = model.windowY;
		windowMaximized = model.windowMaximized;
		windowFullscreen = model.windowFullscreen;
		hasWindowState = model.hasWindowState;
		lastWorld = model.lastWorld;
	}

	private static void syncModelFromFields() {
		model.chatHeightPreset = chatHeightPreset;
		model.chatFontPreset = chatFontPreset;
		model.sidePanelMode = sidePanelMode;
		model.minimapScalePreset = minimapScalePreset;
		model.chatOverlay = chatOverlay;
		model.chatHidden = chatHidden;
		model.chatOpacity = chatOpacity;
		model.uiScaleMode = uiScaleMode;
		model.rememberUsername = rememberUsername;
		model.rememberedUsername = sanitizeUsername(rememberedUsername);
		model.windowWidth = windowWidth;
		model.windowHeight = windowHeight;
		model.windowX = windowX;
		model.windowY = windowY;
		model.windowMaximized = windowMaximized;
		model.windowFullscreen = windowFullscreen;
		model.hasWindowState = hasWindowState;
		model.lastWorld = lastWorld;
		model.clamp();
		applyModel(model);
	}

	private static void writeAtomically(Properties properties, File file) throws IOException {
		File parent = file.getParentFile();
		if (parent != null && !parent.exists() && !parent.mkdirs()) {
			throw new IOException("Failed to create preferences directory: " + parent);
		}

		File tempFile = new File(parent, file.getName() + ".tmp");
		try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
			properties.store(writer, "2006Scape client display settings");
			writer.flush();
		}

		try {
			Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException exception) {
			if (!tempFile.delete()) {
				tempFile.deleteOnExit();
			}
			throw exception;
		}
	}

	private static File getFile() {
		if (preferencesFileOverride != null) {
			return preferencesFileOverride;
		}
		return new File(new File(System.getProperty("user.home"), ClientSettings.SERVER_NAME), "client.properties");
	}

	private static void logFailure(String operation, File file, IOException exception) {
		ClientLogger.error(CredentialSanitizer.sanitizeForLog(
				"Failed to " + operation + " client preferences at " + file.getAbsolutePath() + ": "
						+ exception.getMessage()), exception);
	}
}
