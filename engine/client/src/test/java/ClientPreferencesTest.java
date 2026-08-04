import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ClientPreferencesTest {

	private File preferencesFile;

	@Before
	public void setUp() throws Exception {
		preferencesFile = File.createTempFile("client-preferences", ".properties");
		preferencesFile.delete();
		ClientPreferences.clearPreferencesFileForTesting();
		ClientPreferences.setPreferencesFileForTesting(preferencesFile);
		ClientPreferences.setSaveDebounceMsForTesting(50L);
		ClientPreferences.resetToDefaults();
	}

	@After
	public void tearDown() {
		ClientPreferences.flushPendingSaveForTesting();
		ClientPreferences.clearPreferencesFileForTesting();
		ClientPreferences.resetSaveDebounceMsForTesting();
		ClientPreferences.resetToDefaults();
		if (preferencesFile != null) {
			preferencesFile.delete();
			new File(preferencesFile.getParentFile(), preferencesFile.getName() + ".tmp").delete();
		}
	}

	@Test
	public void loadUsesDefaultsWhenFileMissing() {
		preferencesFile.delete();

		ClientPreferences.load();

		assertEquals(0, ClientPreferences.chatHeightPreset);
		assertEquals(1, ClientPreferences.chatFontPreset);
		assertEquals(0, ClientPreferences.sidePanelMode);
		assertTrue(ClientPreferences.chatOverlay);
		assertFalse(ClientPreferences.chatHidden);
		assertEquals(1, ClientPreferences.lastWorld);
		assertFalse(ClientPreferences.hasWindowState);
	}

	@Test
	public void saveAndLoadRoundTrip() {
		ClientPreferences.chatHeightPreset = 2;
		ClientPreferences.chatFontPreset = 0;
		ClientPreferences.sidePanelMode = 1;
		ClientPreferences.chatOverlay = false;
		ClientPreferences.chatHidden = true;
		ClientPreferences.lastWorld = 3;
		ClientPreferences.hasWindowState = true;
		ClientPreferences.windowWidth = 1280;
		ClientPreferences.windowHeight = 720;
		ClientPreferences.windowX = 120;
		ClientPreferences.windowY = 80;
		ClientPreferences.windowMaximized = true;

		ClientPreferences.saveImmediately();
		ClientPreferences.resetToDefaults();
		ClientPreferences.load();

		assertEquals(2, ClientPreferences.chatHeightPreset);
		assertEquals(0, ClientPreferences.chatFontPreset);
		assertEquals(1, ClientPreferences.sidePanelMode);
		assertFalse(ClientPreferences.chatOverlay);
		assertTrue(ClientPreferences.chatHidden);
		assertEquals(3, ClientPreferences.lastWorld);
		assertTrue(ClientPreferences.hasWindowState);
		assertEquals(1280, ClientPreferences.windowWidth);
		assertEquals(720, ClientPreferences.windowHeight);
		assertEquals(120, ClientPreferences.windowX);
		assertEquals(80, ClientPreferences.windowY);
		assertTrue(ClientPreferences.windowMaximized);
	}

	@Test
	public void loadClampsOutOfRangeValues() throws Exception {
		Properties properties = new Properties();
		properties.setProperty("preferences.version", "1");
		properties.setProperty("chatHeightPreset", "9");
		properties.setProperty("chatFontPreset", "-1");
		properties.setProperty("sidePanelMode", "5");
		properties.setProperty("windowWidth", "100");
		properties.setProperty("windowHeight", "100");
		properties.setProperty("lastWorld", "0");
		try (FileOutputStream output = new FileOutputStream(preferencesFile)) {
			properties.store(output, "test");
		}

		ClientPreferences.load();

		assertEquals(2, ClientPreferences.chatHeightPreset);
		assertEquals(0, ClientPreferences.chatFontPreset);
		assertEquals(2, ClientPreferences.sidePanelMode);
		assertEquals(765, ClientPreferences.windowWidth);
		assertEquals(503, ClientPreferences.windowHeight);
		assertEquals(1, ClientPreferences.lastWorld);
	}

	@Test
	public void loadFallsBackWhenValuesAreInvalid() throws Exception {
		Properties properties = new Properties();
		properties.setProperty("chatHeightPreset", "not-a-number");
		properties.setProperty("chatFontPreset", "also-bad");
		try (FileOutputStream output = new FileOutputStream(preferencesFile)) {
			properties.store(output, "test");
		}

		ClientPreferences.load();

		assertEquals(0, ClientPreferences.chatHeightPreset);
		assertEquals(1, ClientPreferences.chatFontPreset);
	}

	@Test
	public void rememberUsernameRoundTrip() {
		ClientPreferences.rememberUsername = true;
		ClientPreferences.rememberedUsername = "PlayerOne";

		ClientPreferences.saveImmediately();
		ClientPreferences.resetToDefaults();
		ClientPreferences.load();

		assertTrue(ClientPreferences.rememberUsername);
		assertEquals("PlayerOne", ClientPreferences.rememberedUsername);
	}

	@Test
	public void rememberedUsernameIsSanitizedOnLoad() throws Exception {
		Properties properties = new Properties();
		properties.setProperty("rememberUsername", "true");
		properties.setProperty("rememberedUsername", "Player\tName");
		try (FileOutputStream output = new FileOutputStream(preferencesFile)) {
			properties.store(output, "test");
		}

		ClientPreferences.load();

		assertEquals("PlayerName", ClientPreferences.rememberedUsername);
	}

	@Test
	public void saveWritesVersionedPropertiesAtomically() throws Exception {
		ClientPreferences.chatHeightPreset = 1;
		ClientPreferences.saveImmediately();

		Properties properties = new Properties();
		try (java.io.FileInputStream input = new java.io.FileInputStream(preferencesFile)) {
			properties.load(input);
		}

		assertEquals("1", properties.getProperty("preferences.version"));
		assertEquals("1", properties.getProperty("chatHeightPreset"));
		assertFalse(new File(preferencesFile.getParentFile(), preferencesFile.getName() + ".tmp").exists());
	}

	@Test
	public void legacyPropertiesWithoutVersionRemainCompatible() throws Exception {
		Properties properties = new Properties();
		properties.setProperty("chatHeightPreset", "2");
		properties.setProperty("chatFontPreset", "0");
		properties.setProperty("sidePanelMode", "1");
		properties.setProperty("chatOverlay", "false");
		properties.setProperty("chatHidden", "true");
		try (FileOutputStream output = new FileOutputStream(preferencesFile)) {
			properties.store(output, "legacy");
		}

		ClientPreferences.load();

		assertEquals(2, ClientPreferences.chatHeightPreset);
		assertEquals(0, ClientPreferences.chatFontPreset);
		assertEquals(1, ClientPreferences.sidePanelMode);
		assertFalse(ClientPreferences.chatOverlay);
		assertTrue(ClientPreferences.chatHidden);
		assertFalse(ClientPreferences.hasWindowState);
	}

	@Test
	public void passwordKeysAreNeverLoadedFromProperties() throws Exception {
		Properties properties = new Properties();
		properties.setProperty("rememberedUsername", "Player");
		properties.setProperty("password", "secret");
		properties.setProperty("rememberedPassword", "secret");
		try (FileOutputStream output = new FileOutputStream(preferencesFile)) {
			properties.store(output, "legacy");
		}

		ClientPreferences.load();

		assertEquals("Player", ClientPreferences.rememberedUsername);
		ClientPreferences.saveImmediately();
		String saved = new String(Files.readAllBytes(preferencesFile.toPath()));
		assertFalse(saved.toLowerCase().contains("password"));
	}

	@Test
	public void windowPositionOffScreenIsRejected() {
		Rectangle[] monitors = { new Rectangle(0, 0, 1920, 1080) };

		assertFalse(ClientPreferences.windowIntersectsMonitor(3000, 3000, 765, 503, monitors));
		assertTrue(ClientPreferences.windowIntersectsMonitor(100, 100, 765, 503, monitors));
		assertTrue(ClientPreferences.windowIntersectsMonitor(-100, 50, 765, 503, monitors));
	}

	@Test
	public void debouncedSaveWritesOnlyAfterDelay() throws Exception {
		ClientPreferences.chatHeightPreset = 2;
		ClientPreferences.save();
		assertTrue(ClientPreferences.isSavePending());
		assertFalse(preferencesFile.exists());

		TimeUnit.MILLISECONDS.sleep(120L);
		ClientPreferences.flushPendingSaveForTesting();

		assertFalse(ClientPreferences.isSavePending());
		assertTrue(preferencesFile.exists());
		Properties properties = new Properties();
		try (java.io.FileInputStream input = new java.io.FileInputStream(preferencesFile)) {
			properties.load(input);
		}
		assertEquals("2", properties.getProperty("chatHeightPreset"));
	}

	@Test
	public void immediateSaveBypassesDebounce() {
		ClientPreferences.chatOverlay = false;
		ClientPreferences.saveImmediately();

		assertFalse(ClientPreferences.isSavePending());
		assertTrue(preferencesFile.exists());
	}

	@Test
	public void applyAccountSettingsRestoresLastWorld() {
		ClientPreferences.lastWorld = 4;
		ClientSettings.SERVER_WORLD = 1;

		ClientPreferences.applyAccountSettings();

		assertEquals(4, ClientSettings.SERVER_WORLD);
		ClientSettings.SERVER_WORLD = 1;
	}

	@Test
	public void chatOpacityAndUiScaleRoundTrip() {
		ClientPreferences.chatOpacity = 0.65F;
		ClientPreferences.uiScaleMode = UiScale.Mode.PERCENT_150.name();

		ClientPreferences.saveImmediately();
		ClientPreferences.resetToDefaults();
		ClientPreferences.load();

		assertEquals(0.65F, ClientPreferences.chatOpacity, 0.001F);
		assertEquals(UiScale.Mode.PERCENT_150.name(), ClientPreferences.uiScaleMode);
	}

	@Test
	public void chatOpacityIsClampedOnLoad() throws Exception {
		Properties properties = new Properties();
		properties.setProperty("preferences.version", "1");
		properties.setProperty("chatOpacity", "0.05");
		properties.setProperty("uiScaleMode", "NOT_A_REAL_MODE");
		try (FileOutputStream output = new FileOutputStream(preferencesFile)) {
			properties.store(output, "test");
		}

		ClientPreferences.load();

		assertEquals(0.2F, ClientPreferences.chatOpacity, 0.001F);
		assertEquals(UiScale.Mode.PERCENT_100.name(), ClientPreferences.uiScaleMode);
	}
}
