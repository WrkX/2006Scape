import java.awt.Toolkit;
import java.awt.event.KeyEvent;

/**
 * Login screen interaction state shared by rendering and input handling.
 */
final class LoginController {

	static final int FOCUS_USERNAME = 0;
	static final int FOCUS_PASSWORD = 1;
	static final int FOCUS_REMEMBER = 2;
	static final int FOCUS_SHOW_PASSWORD = 3;
	static final int FOCUS_LOGIN = 4;
	static final int FOCUS_CANCEL = 5;
	static final int FOCUS_COUNT = 6;

	static final String VALID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!\"\243$%^&*()-_=+[{]};:'@#~,<.>/?\\| ";

	enum ClickTarget {
		NONE,
		NEW_USER,
		EXISTING_USER,
		USERNAME,
		PASSWORD,
		REMEMBER_USERNAME,
		SHOW_PASSWORD,
		WORLD_SELECTOR,
		LOGIN,
		CANCEL,
		CREATE_CANCEL
	}

	enum ButtonVisual {
		NORMAL,
		HOVER,
		PRESSED
	}

	boolean rememberUsername;
	boolean showPassword;
	boolean usernameSelectAll;
	boolean passwordSelectAll;

	ClickTarget hitTest(LoginLayout layout, int x, int y, int loginScreenState) {
		if (loginScreenState == 0) {
			if (layout.newUserButton.contains(x, y)) {
				return ClickTarget.NEW_USER;
			}
			if (layout.existingUserButton.contains(x, y)) {
				return ClickTarget.EXISTING_USER;
			}
			return ClickTarget.NONE;
		}
		if (loginScreenState == 2) {
			if (layout.usernameField.contains(x, y)) {
				return ClickTarget.USERNAME;
			}
			if (layout.passwordField.contains(x, y)) {
				return ClickTarget.PASSWORD;
			}
			if (layout.rememberUsernameCheckbox.contains(x, y)) {
				return ClickTarget.REMEMBER_USERNAME;
			}
			if (layout.showPasswordCheckbox.contains(x, y)) {
				return ClickTarget.SHOW_PASSWORD;
			}
			if (layout.worldSelector.contains(x, y)) {
				return ClickTarget.WORLD_SELECTOR;
			}
			if (layout.loginButton.contains(x, y)) {
				return ClickTarget.LOGIN;
			}
			if (layout.cancelButton.contains(x, y)) {
				return ClickTarget.CANCEL;
			}
			return ClickTarget.NONE;
		}
		if (loginScreenState == 3 && layout.createAccountCancelButton.contains(x, y)) {
			return ClickTarget.CREATE_CANCEL;
		}
		return ClickTarget.NONE;
	}

	boolean isOverClickable(LoginLayout layout, int x, int y, int loginScreenState) {
		return hitTest(layout, x, y, loginScreenState) != ClickTarget.NONE
				&& hitTest(layout, x, y, loginScreenState) != ClickTarget.WORLD_SELECTOR;
	}

	ButtonVisual buttonVisual(UiBounds bounds, int mouseX, int mouseY, boolean mouseDown) {
		if (!bounds.contains(mouseX, mouseY)) {
			return ButtonVisual.NORMAL;
		}
		if (mouseDown) {
			return ButtonVisual.PRESSED;
		}
		return ButtonVisual.HOVER;
	}

	int focusForClick(ClickTarget target) {
		switch (target) {
			case USERNAME:
				return FOCUS_USERNAME;
			case PASSWORD:
				return FOCUS_PASSWORD;
			case REMEMBER_USERNAME:
				return FOCUS_REMEMBER;
			case SHOW_PASSWORD:
				return FOCUS_SHOW_PASSWORD;
			case LOGIN:
				return FOCUS_LOGIN;
			case CANCEL:
				return FOCUS_CANCEL;
			default:
				return -1;
		}
	}

	int tabForward(int focus) {
		return (focus + 1) % FOCUS_COUNT;
	}

	int tabBackward(int focus) {
		return (focus + FOCUS_COUNT - 1) % FOCUS_COUNT;
	}

	boolean shouldShowCapsLockWarning(int focus, boolean capsLockOn) {
		return capsLockOn && focus == FOCUS_PASSWORD;
	}

	static boolean isCapsLockOn() {
		try {
			return Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_CAPS_LOCK);
		} catch (UnsupportedOperationException ignored) {
			return false;
		}
	}

	String displayPassword(String password, boolean reveal) {
		if (reveal) {
			return password;
		}
		return TextClass.passwordAsterisks(password);
	}

	void restoreRememberedUsername(Game game) {
		game.myUsername = rememberedUsernameToApply(game.myUsername);
	}

	String rememberedUsernameToApply(String currentUsername) {
		rememberUsername = ClientPreferences.rememberUsername;
		if (rememberUsername && (currentUsername == null || currentUsername.isEmpty())) {
			return ClientPreferences.rememberedUsername;
		}
		return currentUsername == null ? "" : currentUsername;
	}

	void persistRememberedUsername(String username) {
		ClientPreferences.rememberUsername = rememberUsername;
		if (rememberUsername) {
			ClientPreferences.rememberedUsername = username;
		} else {
			ClientPreferences.rememberedUsername = "";
		}
		ClientPreferences.saveImmediately();
	}

	void toggleRememberUsername(String username) {
		rememberUsername = !rememberUsername;
		persistRememberedUsername(username);
	}

	void toggleShowPassword() {
		showPassword = !showPassword;
	}

	boolean isValidChar(int code) {
		if (code < 0 || code >= 128) {
			return false;
		}
		return VALID_CHARS.indexOf((char) code) >= 0;
	}

	boolean handleUsernameKey(Game game, int key, boolean ctrlDown) {
		if (ctrlDown && (key == 'a' || key == 'A' || key == 1)) {
			usernameSelectAll = true;
			return true;
		}
		if (ctrlDown && (key == 'c' || key == 'C' || key == 3)) {
			copyToClipboard(game.myUsername);
			return true;
		}
		if (ctrlDown && (key == 'v' || key == 'V' || key == 22)) {
			pasteIntoUsername(game);
			return true;
		}
		if (key == 8) {
			if (usernameSelectAll) {
				game.myUsername = "";
				usernameSelectAll = false;
			} else if (game.myUsername.length() > 0) {
				game.myUsername = game.myUsername.substring(0, game.myUsername.length() - 1);
			}
			return true;
		}
		if (key == 1000) {
			usernameSelectAll = false;
			return true;
		}
		if (key == 1001) {
			usernameSelectAll = false;
			return true;
		}
		if (key == 1) {
			usernameSelectAll = false;
			return true;
		}
		if (key == 2) {
			usernameSelectAll = false;
			return true;
		}
		if (isValidChar(key)) {
			if (usernameSelectAll) {
				game.myUsername = "";
				usernameSelectAll = false;
			}
			game.myUsername += (char) key;
			if (game.myUsername.length() > 12) {
				game.myUsername = game.myUsername.substring(0, 12);
			}
			return true;
		}
		return false;
	}

	boolean handlePasswordKey(Game game, int key, boolean ctrlDown) {
		if (ctrlDown) {
			return true;
		}
		if (key == 8) {
			if (passwordSelectAll) {
				game.myPassword = "";
				passwordSelectAll = false;
			} else if (game.myPassword.length() > 0) {
				game.myPassword = game.myPassword.substring(0, game.myPassword.length() - 1);
			}
			return true;
		}
		if (key == 1000 || key == 1001 || key == 1 || key == 2) {
			passwordSelectAll = false;
			return true;
		}
		if (isValidChar(key)) {
			if (passwordSelectAll) {
				game.myPassword = "";
				passwordSelectAll = false;
			}
			game.myPassword += (char) key;
			if (game.myPassword.length() > 20) {
				game.myPassword = game.myPassword.substring(0, 20);
			}
			return true;
		}
		return false;
	}

	boolean activateFocusedControl(Game game, int focus) {
		switch (focus) {
			case FOCUS_REMEMBER:
				toggleRememberUsername(game.myUsername);
				return true;
			case FOCUS_SHOW_PASSWORD:
				toggleShowPassword();
				return true;
			case FOCUS_LOGIN:
				return canSubmit(game);
			case FOCUS_CANCEL:
				return true;
			default:
				return false;
		}
	}

	boolean canSubmit(Game game) {
		return canSubmit(game.myUsername, game.myPassword);
	}

	boolean canSubmit(String username, String password) {
		return username != null && password != null && username.length() > 0 && password.length() > 0;
	}

	String worldLabel() {
		return "World " + ClientSettings.SERVER_WORLD;
	}

	private static void copyToClipboard(String value) {
		if (value == null || value.isEmpty()) {
			return;
		}
		try {
			java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(value);
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
		} catch (Exception ignored) {
		}
	}

	private static void pasteIntoUsername(Game game) {
		try {
			java.awt.datatransfer.Transferable contents = Toolkit.getDefaultToolkit()
					.getSystemClipboard()
					.getContents(null);
			if (contents == null || !contents.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
				return;
			}
			String pasted = (String) contents.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
			if (pasted == null) {
				return;
			}
			StringBuilder filtered = new StringBuilder();
			for (int i = 0; i < pasted.length() && filtered.length() < 12; i++) {
				char c = pasted.charAt(i);
				if (VALID_CHARS.indexOf(c) >= 0) {
					filtered.append(c);
				}
			}
			game.myUsername = filtered.toString();
		} catch (Exception ignored) {
		}
	}
}
