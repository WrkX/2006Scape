import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LoginLayoutTest {

	@Test
	public void logicalLayoutUsesReferencePanelPosition() {
		LoginLayout layout = LoginLayout.logical();

		assertEquals(202, layout.panelX);
		assertEquals(171, layout.panelY);
		assertEquals(new UiBounds(202, 171, 360, 200).toString(), layout.panel.toString());
	}

	@Test
	public void buttonsStayInsidePanelAtReferenceResolution() {
		LoginLayout layout = LoginLayout.logical();

		assertBoundsInside(layout.panel, layout.newUserButton);
		assertBoundsInside(layout.panel, layout.existingUserButton);
		assertBoundsInside(layout.panel, layout.loginButton);
		assertBoundsInside(layout.panel, layout.cancelButton);
		assertBoundsInside(layout.panel, layout.createAccountCancelButton);
	}

	@Test
	public void credentialFieldsAlignWithPanelAtReferenceResolution() {
		LoginLayout layout = LoginLayout.logical();

		assertEquals(171 + 70, layout.usernameField.y);
		assertEquals(171 + 85, layout.passwordField.y);
		assertEquals(202 + 90, layout.usernameField.x);
		assertEquals(180, layout.usernameField.width);
		assertEquals(15, layout.usernameField.height);
	}

	@Test
	public void welcomeButtonsMatchHistoricalHitCentersAtReferenceResolution() {
		LoginLayout layout = LoginLayout.logical();

		assertEquals(302, layout.newUserButton.centerX());
		assertEquals(291, layout.newUserButton.centerY());
		assertEquals(462, layout.existingUserButton.centerX());
		assertEquals(291, layout.existingUserButton.centerY());
	}

	@Test
	public void loginButtonsSitBelowOptionRowWithoutOverlap() {
		LoginLayout layout = LoginLayout.logical();

		assertTrue(layout.passwordField.bottom() <= layout.rememberUsernameCheckbox.y);
		assertTrue(layout.loginButton.y >= layout.rememberUsernameCheckbox.bottom());
		assertTrue(layout.cancelButton.y >= layout.showPasswordCheckbox.bottom());
		assertTrue(layout.worldSelector.y >= layout.loginButton.bottom());
		assertEquals(302, layout.loginButton.centerX());
		assertEquals(171 + 150, layout.loginButton.centerY());
		assertEquals(462, layout.cancelButton.centerX());
		assertEquals(171 + 150, layout.cancelButton.centerY());
	}

	@Test
	public void optionControlsStayInsidePanelAtReferenceResolution() {
		LoginLayout layout = LoginLayout.logical();

		assertBoundsInside(layout.panel, layout.rememberUsernameCheckbox);
		assertBoundsInside(layout.panel, layout.showPasswordCheckbox);
		assertBoundsInside(layout.panel, layout.worldSelector);
		assertTrue(layout.showPasswordCheckbox.x >= layout.rememberUsernameCheckbox.right());
		assertEquals(layout.panelCenterX(), layout.worldSelector.centerX());
	}

	private static void assertBoundsInside(UiBounds outer, UiBounds inner) {
		assertTrue(inner.x >= outer.x);
		assertTrue(inner.y >= outer.y);
		assertTrue(inner.right() <= outer.right());
		assertTrue(inner.bottom() <= outer.bottom());
	}
}
