import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class SidePanelLayoutTest {

	@After
	public void resetPreferences() {
		ClientPreferences.resetToDefaults();
	}

	@Test
	public void classicReferenceResolutionMatchesHistoricalBounds() {
		SidePanelLayout layout = SidePanelLayout.forWindow(765, 503, SidePanelLayout.MODE_CLASSIC);

		assertEquals(553, layout.tabContent.x);
		assertEquals(205, layout.tabContent.y);
		assertEquals(550, layout.minimap.x);
		assertEquals(516, layout.topTabBar.x);
		assertEquals(160, layout.topTabBar.y);
	}

	@Test
	public void overlayModeAnchorsPanelToBottomRight() {
		SidePanelLayout layout = SidePanelLayout.forWindow(1280, 720, SidePanelLayout.MODE_OVERLAY);

		assertEquals(1280 - SidePanelLayout.TAB_CONTENT_WIDTH - SidePanelLayout.RIGHT_UI_MARGIN,
				layout.tabContent.x);
		assertTrue(layout.tabContent.bottom() <= 720);
		assertTrue(layout.minimap.right() <= 1280);
		assertTrue(layout.topTabBar.y < layout.bottomTabBar.y);
	}

	@Test
	public void modernModePlacesTabBarAlongBottomEdge() {
		SidePanelLayout layout = SidePanelLayout.forWindow(1920, 1080, SidePanelLayout.MODE_MODERN);

		assertEquals(1920 - SidePanelLayout.MODERN_TAB_BAR_WIDTH - SidePanelLayout.RIGHT_UI_MARGIN,
				layout.modernTabBar.x);
		assertEquals(1080 - SidePanelLayout.MODERN_TAB_BAR_HEIGHT - SidePanelLayout.RIGHT_UI_MARGIN,
				layout.modernTabBar.y);
		assertTrue(layout.tabContent.bottom() <= layout.modernTabBar.y);
	}

	@Test
	public void recommendedModePrefersOverlayOnSmallWindows() {
		assertEquals(SidePanelLayout.MODE_OVERLAY, SidePanelLayout.recommendedMode(1024, 768));
		assertEquals(SidePanelLayout.MODE_MODERN, SidePanelLayout.recommendedMode(1920, 1080));
	}

	@Test
	public void rightUiHitTestExcludesClassicMode() {
		SidePanelLayout classic = SidePanelLayout.forWindow(765, 503, SidePanelLayout.MODE_CLASSIC);
		SidePanelLayout overlay = SidePanelLayout.forWindow(1280, 720, SidePanelLayout.MODE_OVERLAY);

		assertFalse(classic.containsRightUi(classic.minimap.x + 10, classic.minimap.y + 10));
		assertTrue(overlay.containsRightUi(overlay.minimap.x + 10, overlay.minimap.y + 10));
	}
}
