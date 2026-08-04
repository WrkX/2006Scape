import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LoginControllerTest {

	private final LoginController controller = new LoginController();
	private final LoginLayout layout = LoginLayout.logical();
	private File preferencesFile;

	@Before
	public void setUp() throws Exception {
		preferencesFile = File.createTempFile("login-controller", ".properties");
		ClientPreferences.clearPreferencesFileForTesting();
		ClientPreferences.setPreferencesFileForTesting(preferencesFile);
		ClientPreferences.resetToDefaults();
	}

	@After
	public void tearDown() {
		ClientPreferences.clearPreferencesFileForTesting();
		ClientPreferences.resetToDefaults();
		if (preferencesFile != null) {
			preferencesFile.delete();
		}
	}

	@Test
	public void hitTestMapsWelcomeButtons() {
		assertEquals(LoginController.ClickTarget.NEW_USER,
				controller.hitTest(layout, layout.newUserButton.centerX(), layout.newUserButton.centerY(), 0));
		assertEquals(LoginController.ClickTarget.EXISTING_USER,
				controller.hitTest(layout, layout.existingUserButton.centerX(), layout.existingUserButton.centerY(), 0));
	}

	@Test
	public void hitTestMapsCredentialControls() {
		assertEquals(LoginController.ClickTarget.USERNAME,
				controller.hitTest(layout, layout.usernameField.centerX(), layout.usernameField.centerY(), 2));
		assertEquals(LoginController.ClickTarget.PASSWORD,
				controller.hitTest(layout, layout.passwordField.centerX(), layout.passwordField.centerY(), 2));
		assertEquals(LoginController.ClickTarget.REMEMBER_USERNAME,
				controller.hitTest(layout, layout.rememberUsernameCheckbox.centerX(), layout.rememberUsernameCheckbox.centerY(), 2));
		assertEquals(LoginController.ClickTarget.SHOW_PASSWORD,
				controller.hitTest(layout, layout.showPasswordCheckbox.centerX(), layout.showPasswordCheckbox.centerY(), 2));
		assertEquals(LoginController.ClickTarget.LOGIN,
				controller.hitTest(layout, layout.loginButton.centerX(), layout.loginButton.centerY(), 2));
	}

	@Test
	public void buttonVisualReflectsMouseState() {
		UiBounds button = layout.loginButton;
		assertEquals(LoginController.ButtonVisual.NORMAL,
				controller.buttonVisual(button, -1, -1, false));
		assertEquals(LoginController.ButtonVisual.HOVER,
				controller.buttonVisual(button, button.centerX(), button.centerY(), false));
		assertEquals(LoginController.ButtonVisual.PRESSED,
				controller.buttonVisual(button, button.centerX(), button.centerY(), true));
	}

	@Test
	public void tabNavigationWrapsAcrossFocusTargets() {
		assertEquals(LoginController.FOCUS_PASSWORD, controller.tabForward(LoginController.FOCUS_USERNAME));
		assertEquals(LoginController.FOCUS_USERNAME, controller.tabBackward(LoginController.FOCUS_PASSWORD));
		assertEquals(LoginController.FOCUS_USERNAME, controller.tabForward(LoginController.FOCUS_CANCEL));
	}

	@Test
	public void showPasswordOnlyRevealsWhileEnabled() {
		assertEquals("****", controller.displayPassword("test", false));
		assertEquals("test", controller.displayPassword("test", true));
	}

	@Test
	public void capsLockWarningOnlyShowsForPasswordFocus() {
		assertTrue(controller.shouldShowCapsLockWarning(LoginController.FOCUS_PASSWORD, true));
		assertFalse(controller.shouldShowCapsLockWarning(LoginController.FOCUS_USERNAME, true));
	}

	@Test
	public void rememberUsernamePersistsWithoutPassword() throws Exception {
		controller.rememberUsername = true;
		controller.persistRememberedUsername("TestUser");

		ClientPreferences.resetToDefaults();
		ClientPreferences.load();

		assertTrue(ClientPreferences.rememberUsername);
		assertEquals("TestUser", ClientPreferences.rememberedUsername);
		String saved = new String(Files.readAllBytes(preferencesFile.toPath()));
		assertFalse(saved.toLowerCase().contains("password"));
	}

	@Test
	public void restoreRememberedUsernameDoesNotLoadWhenDisabled() {
		ClientPreferences.rememberUsername = false;
		ClientPreferences.rememberedUsername = "SavedUser";
		assertEquals("", controller.rememberedUsernameToApply(""));
	}

	@Test
	public void restoreRememberedUsernameLoadsSavedValueWhenEnabled() {
		ClientPreferences.rememberUsername = true;
		ClientPreferences.rememberedUsername = "SavedUser";
		assertEquals("SavedUser", controller.rememberedUsernameToApply(""));
	}

	@Test
	public void canSubmitRequiresBothFields() {
		assertFalse(controller.canSubmit("user", ""));
		assertTrue(controller.canSubmit("user", "pass"));
	}

	@Test
	public void worldLabelUsesConfiguredWorld() {
		ClientSettings.SERVER_WORLD = 3;
		assertEquals("World 3", controller.worldLabel());
		ClientSettings.SERVER_WORLD = 1;
	}
}
