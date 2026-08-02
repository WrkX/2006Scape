import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Small local preference store for display options that are not server varps.
 */
public final class ClientPreferences {

	public static int chatHeightPreset = 0;
	public static int chatFontPreset = 1;
	public static int sidePanelMode = 0;
	public static boolean chatOverlay = true;
	public static boolean chatHidden = false;

	private static final String CHAT_HEIGHT = "chatHeightPreset";
	private static final String CHAT_FONT = "chatFontPreset";
	private static final String SIDE_PANEL_MODE = "sidePanelMode";
	private static final String CHAT_OVERLAY = "chatOverlay";
	private static final String CHAT_HIDDEN = "chatHidden";

	private ClientPreferences() {
	}

	public static void load() {
		Properties properties = new Properties();
		File file = getFile();
		if (file.isFile()) {
			try (FileInputStream input = new FileInputStream(file)) {
				properties.load(input);
			} catch (IOException ignored) {
			}
		}

		chatHeightPreset = clamp(readInt(properties, CHAT_HEIGHT, 0), 0, 2);
		chatFontPreset = clamp(readInt(properties, CHAT_FONT, 1), 0, 2);
		sidePanelMode = clamp(readInt(properties, SIDE_PANEL_MODE, 0), 0, 2);
		chatOverlay = Boolean.parseBoolean(properties.getProperty(CHAT_OVERLAY, "true"));
		chatHidden = Boolean.parseBoolean(properties.getProperty(CHAT_HIDDEN, "false"));
	}

	public static void save() {
		File file = getFile();
		File parent = file.getParentFile();
		if (!parent.exists() && !parent.mkdirs()) {
			return;
		}

		Properties properties = new Properties();
		properties.setProperty(CHAT_HEIGHT, Integer.toString(chatHeightPreset));
		properties.setProperty(CHAT_FONT, Integer.toString(chatFontPreset));
		properties.setProperty(SIDE_PANEL_MODE, Integer.toString(sidePanelMode));
		properties.setProperty(CHAT_OVERLAY, Boolean.toString(chatOverlay));
		properties.setProperty(CHAT_HIDDEN, Boolean.toString(chatHidden));
		try (FileOutputStream output = new FileOutputStream(file)) {
			properties.store(output, "2006Scape client display settings");
		} catch (IOException ignored) {
		}
	}

	private static File getFile() {
		return new File(new File(System.getProperty("user.home"), ClientSettings.SERVER_NAME), "client.properties");
	}

	private static int readInt(Properties properties, String key, int fallback) {
		try {
			return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
