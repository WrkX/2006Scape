/**
 * Login screen layout in logical UI coordinates (765x503 reference space).
 *
 * Screen mapping is performed by {@link UiTransform}; bounds here are shared by
 * rendering and hit testing after coordinate conversion.
 */
final class LoginLayout {

	static final int PANEL_WIDTH = 360;
	static final int PANEL_HEIGHT = 200;
	static final int REFERENCE_WIDTH = 765;
	static final int REFERENCE_HEIGHT = 503;
	static final int REFERENCE_PANEL_X = 202;
	static final int REFERENCE_PANEL_Y = 171;

	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 40;
	private static final int FIELD_LEFT_X = PANEL_WIDTH / 2 - 90;
	private static final int FIELD_WIDTH = 180;
	private static final int FIELD_HEIGHT = 15;
	private static final int REMEMBER_CHECKBOX_WIDTH = 150;
	private static final int SHOW_PASSWORD_CHECKBOX_WIDTH = 110;
	private static final int SHOW_PASSWORD_CHECKBOX_GAP = 158;

	final int panelX;
	final int panelY;
	final UiBounds panel;
	final UiBounds newUserButton;
	final UiBounds existingUserButton;
	final UiBounds usernameField;
	final UiBounds passwordField;
	final UiBounds loginButton;
	final UiBounds cancelButton;
	final UiBounds createAccountCancelButton;
	final UiBounds rememberUsernameCheckbox;
	final UiBounds showPasswordCheckbox;
	final UiBounds worldSelector;

	private LoginLayout(int panelX, int panelY) {
		this.panelX = panelX;
		this.panelY = panelY;
		panel = new UiBounds(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);

		int welcomeButtonCenterY = PANEL_HEIGHT / 2 + 20;
		newUserButton = offsetToScreen(panelX, panelY, UiBounds.centeredAt(
				PANEL_WIDTH / 2 - 80, welcomeButtonCenterY, BUTTON_WIDTH, BUTTON_HEIGHT));
		existingUserButton = offsetToScreen(panelX, panelY, UiBounds.centeredAt(
				PANEL_WIDTH / 2 + 80, welcomeButtonCenterY, BUTTON_WIDTH, BUTTON_HEIGHT));

		// Keep classic field/button rhythm; place option toggles in the gap between
		// password and buttons so focus underlines never cut through checkbox labels.
		int usernameY = 70;
		usernameField = offsetToScreen(panelX, panelY, new UiBounds(FIELD_LEFT_X, usernameY, FIELD_WIDTH, FIELD_HEIGHT));
		passwordField = offsetToScreen(panelX, panelY, new UiBounds(FIELD_LEFT_X, usernameY + 15, FIELD_WIDTH, FIELD_HEIGHT));

		int optionRowY = 105;
		rememberUsernameCheckbox = offsetToScreen(panelX, panelY,
				new UiBounds(FIELD_LEFT_X, optionRowY, REMEMBER_CHECKBOX_WIDTH, FIELD_HEIGHT));
		showPasswordCheckbox = offsetToScreen(panelX, panelY,
				new UiBounds(FIELD_LEFT_X + SHOW_PASSWORD_CHECKBOX_GAP, optionRowY,
						SHOW_PASSWORD_CHECKBOX_WIDTH, FIELD_HEIGHT));

		int credentialsButtonCenterY = 150;
		loginButton = offsetToScreen(panelX, panelY, UiBounds.centeredAt(
				PANEL_WIDTH / 2 - 80, credentialsButtonCenterY, BUTTON_WIDTH, BUTTON_HEIGHT));
		cancelButton = offsetToScreen(panelX, panelY, UiBounds.centeredAt(
				PANEL_WIDTH / 2 + 80, credentialsButtonCenterY, BUTTON_WIDTH, BUTTON_HEIGHT));

		createAccountCancelButton = offsetToScreen(panelX, panelY, UiBounds.centeredAt(
				PANEL_WIDTH / 2, credentialsButtonCenterY, BUTTON_WIDTH, BUTTON_HEIGHT));

		worldSelector = offsetToScreen(panelX, panelY, UiBounds.centeredAt(
				PANEL_WIDTH / 2, PANEL_HEIGHT - 18, FIELD_WIDTH, FIELD_HEIGHT));
	}

	static LoginLayout logical() {
		return new LoginLayout(REFERENCE_PANEL_X, REFERENCE_PANEL_Y);
	}

	int panelCenterX() {
		return panelX + PANEL_WIDTH / 2;
	}

	int welcomeTextY() {
		return panelY + PANEL_HEIGHT / 2 - 20;
	}

	int statusTextY() {
		return panelY + PANEL_HEIGHT / 2 + 80;
	}

	int credentialsHeaderY() {
		return panelY + 40;
	}

	int usernameTextY() {
		return usernameField.y;
	}

	int passwordTextY() {
		return passwordField.y;
	}

	int createAccountHeaderY() {
		return panelY + PANEL_HEIGHT / 2 - 60;
	}

	int createAccountBodyY() {
		return panelY + PANEL_HEIGHT / 2 - 35;
	}

	int capsLockWarningLocalY() {
		return PANEL_HEIGHT - 8;
	}

	UiBounds toLocal(UiBounds absoluteBounds) {
		return absoluteBounds.offset(-panelX, -panelY);
	}

	/**
	 * Text baseline for field labels — near the bottom of the hit box so it sits
	 * on the focus underline.
	 */
	int localTextY(UiBounds field) {
		return toLocal(field).y + 12;
	}

	int localFieldX(UiBounds field) {
		return toLocal(field).x;
	}

	UiBounds[] debugRegions() {
		return new UiBounds[] {
				panel,
				newUserButton,
				existingUserButton,
				usernameField,
				passwordField,
				loginButton,
				cancelButton,
				createAccountCancelButton,
				rememberUsernameCheckbox,
				showPasswordCheckbox,
				worldSelector
		};
	}

	private static UiBounds offsetToScreen(int panelX, int panelY, UiBounds localBounds) {
		return localBounds.offset(panelX, panelY);
	}
}
