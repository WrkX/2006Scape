import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class ViewportLayoutTest {

	@After
	public void resetScaleMode() {
		UiScale.setMode(UiScale.Mode.PERCENT_100);
	}

	@Test
	public void referenceResolutionPreservesHistoricalLoginPanelScreenBounds() {
		ViewportLayout layout = ViewportLayout.forSize(765, 503, UiScale.Mode.PERCENT_100);

		assertEquals(202, layout.loginPanelScreen.x);
		assertEquals(171, layout.loginPanelScreen.y);
		assertEquals(360, layout.loginPanelScreen.width);
		assertEquals(200, layout.loginPanelScreen.height);
	}

	@Test
	public void navbarHitTestUsesLogicalCoordinates() {
		ViewportLayout layout = ViewportLayout.forSize(765, 503, UiScale.Mode.PERCENT_100);
		InputRouter router = new InputRouter();
		router.update(layout, 150, 12);

		assertEquals(NavbarLayout.Item.MAIN_MENU, router.hitNavbar(layout));
	}

	@Test
	public void navbarHitTestTransformsScreenCoordinatesAtLargerWindow() {
		ViewportLayout layout = ViewportLayout.forSize(1024, 768, UiScale.Mode.PERCENT_100);
		InputRouter router = new InputRouter();
		int screenX = layout.uiTransform.toScreenX(150);
		int screenY = layout.uiTransform.toScreenY(12);
		router.update(layout, screenX, screenY);

		assertEquals(NavbarLayout.Item.MAIN_MENU, router.hitNavbar(layout));
	}

	@Test
	public void loginHitTestUsesLogicalCoordinatesAfterTransform() {
		ViewportLayout layout = ViewportLayout.forSize(765, 503, UiScale.Mode.PERCENT_100);
		LoginController controller = new LoginController();
		InputRouter router = new InputRouter();
		router.update(layout, 302, 321);

		LoginController.ClickTarget target = controller.hitTest(
				layout.loginLayout,
				router.logicalX,
				router.logicalY,
				2);
		assertEquals(LoginController.ClickTarget.LOGIN, target);
	}

	@Test
	public void resolutionsAt150PercentKeepPanelInsideWindow() {
		UiScale.setMode(UiScale.Mode.PERCENT_150);
		assertPanelInsideWindow(1024, 768);
		assertPanelInsideWindow(1280, 720);
		assertPanelInsideWindow(1920, 1080);
		assertPanelInsideWindow(2560, 1440);
	}

	@Test
	public void resolutionsAt200PercentKeepPanelInsideWindow() {
		UiScale.setMode(UiScale.Mode.PERCENT_200);
		assertPanelInsideWindow(1920, 1080);
		assertPanelInsideWindow(2560, 1440);
	}

	@Test
	public void chatRegionsStayInsideWindowAtMultipleScales() {
		assertChatInsideWindow(765, 503, UiScale.Mode.PERCENT_100);
		assertChatInsideWindow(1024, 768, UiScale.Mode.PERCENT_100);
		assertChatInsideWindow(1920, 1080, UiScale.Mode.PERCENT_150);
		assertChatInsideWindow(2560, 1440, UiScale.Mode.PERCENT_200);
	}

	@Test
	public void sidePanelRegionsStayInsideWindowAtMultipleScales() {
		assertSidePanelInsideWindow(765, 503, UiScale.Mode.PERCENT_100, SidePanelLayout.MODE_CLASSIC);
		assertSidePanelInsideWindow(765, 503, UiScale.Mode.PERCENT_100, SidePanelLayout.MODE_OVERLAY);
		assertSidePanelInsideWindow(1280, 720, UiScale.Mode.PERCENT_100, SidePanelLayout.MODE_OVERLAY);
		assertSidePanelInsideWindow(1920, 1080, UiScale.Mode.PERCENT_150, SidePanelLayout.MODE_MODERN);
		assertSidePanelInsideWindow(2560, 1440, UiScale.Mode.PERCENT_200, SidePanelLayout.MODE_MODERN);
	}

	private static void assertChatInsideWindow(int width, int height, UiScale.Mode mode) {
		ViewportLayout layout = ViewportLayout.forSize(width, height, mode);
		assertTrue(layout.chatFooterScreen.x >= 0);
		assertTrue(layout.chatFooterScreen.y >= 0);
		assertTrue(layout.chatFooterScreen.right() <= width);
		assertTrue(layout.chatFooterScreen.bottom() <= height);
		if (layout.chatLayout.visible) {
			assertTrue(layout.chatPanelScreen.right() <= width);
			assertTrue(layout.chatPanelScreen.bottom() <= layout.chatFooterScreen.y);
		}
	}

	private static void assertPanelInsideWindow(int width, int height) {
		ViewportLayout layout = ViewportLayout.forSize(width, height, UiScale.currentMode());
		assertTrue(layout.loginPanelScreen.x >= 0);
		assertTrue(layout.loginPanelScreen.y >= 0);
		assertTrue(layout.loginPanelScreen.right() <= width);
		assertTrue(layout.loginPanelScreen.bottom() <= height);
	}

	private static void assertSidePanelInsideWindow(int width, int height, UiScale.Mode mode, int sidePanelMode) {
		ViewportLayout layout = ViewportLayout.forSize(width, height, mode,
				ChatLayout.logical(
						ClientPreferences.LOGICAL_UI_HEIGHT,
						height,
						ClientPreferences.chatOverlay,
						ClientPreferences.chatHidden,
						ClientPreferences.chatHeightPreset),
				sidePanelMode);
		SidePanelLayout sidePanel = layout.sidePanelLayout;
		assertTrue(sidePanel.tabContent.right() <= width);
		assertTrue(sidePanel.tabContent.bottom() <= height);
		assertTrue(sidePanel.minimap.right() <= width);
		assertTrue(sidePanel.minimap.bottom() <= height);
		if (!sidePanel.isClassic()) {
			assertTrue(sidePanel.containsRightUi(sidePanel.minimap.x + 1, sidePanel.minimap.y + 1));
		}
	}
}
