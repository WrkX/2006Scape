import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class TabLayoutTest {

	@After
	public void resetPreferences() {
		ClientPreferences.resetToDefaults();
		UiScale.setMode(UiScale.Mode.PERCENT_100);
	}

	@Test
	public void overlayTopTabHitboxesMatchHistoricalCoordinates() {
		ClientPreferences.sidePanelMode = SidePanelLayout.MODE_OVERLAY;
		ViewportLayout layout = ViewportLayout.forSize(765, 503, UiScale.Mode.PERCENT_100);

		assertEquals(0, TabLayout.hitTest(layout.tabLayout, layout.sidePanelLayout, 540, 168));
		assertEquals(1, TabLayout.hitTest(layout.tabLayout, layout.sidePanelLayout, 569, 168));
		assertEquals(6, TabLayout.hitTest(layout.tabLayout, layout.sidePanelLayout, 722, 169));
	}

	@Test
	public void overlayBottomTabHitboxesMatchHistoricalCoordinates() {
		ClientPreferences.sidePanelMode = SidePanelLayout.MODE_OVERLAY;
		ViewportLayout layout = ViewportLayout.forSize(765, 503, UiScale.Mode.PERCENT_100);

		assertEquals(8, TabLayout.hitTest(layout.tabLayout, layout.sidePanelLayout, 580, 465));
		assertEquals(13, TabLayout.hitTest(layout.tabLayout, layout.sidePanelLayout, 724, 465));
	}

	@Test
	public void modernTabBarUsesFixedButtonWidth() {
		ClientPreferences.sidePanelMode = SidePanelLayout.MODE_MODERN;
		ViewportLayout layout = ViewportLayout.forSize(1920, 1080, UiScale.Mode.PERCENT_100);
		int barX = layout.sidePanelLayout.modernTabBar.x;
		int barY = layout.sidePanelLayout.modernTabBar.y + 10;

		assertEquals(0, TabLayout.hitTest(layout.tabLayout, layout.sidePanelLayout, barX + 5, barY));
		assertEquals(5, TabLayout.hitTest(layout.tabLayout, layout.sidePanelLayout,
				barX + SidePanelLayout.MODERN_TAB_BUTTON_WIDTH * 5 + 5, barY));
	}

	@Test
	public void scaledWindowKeepsTabHitsAlignedToScreenCoordinates() {
		ClientPreferences.sidePanelMode = SidePanelLayout.MODE_OVERLAY;
		UiScale.setMode(UiScale.Mode.PERCENT_150);
		ViewportLayout layout = ViewportLayout.forSize(1920, 1080, UiScale.Mode.PERCENT_150);
		InputRouter router = new InputRouter();
		int screenX = layout.sidePanelLayout.topTabBar.x + 30;
		int screenY = layout.sidePanelLayout.topTabBar.y + 12;
		router.update(layout, screenX, screenY);

		assertEquals(0, router.hitTab(layout));
		assertTrue(layout.tabLayout.containsAnyTab(router.screenX, router.screenY, layout.sidePanelLayout));
	}

	@Test
	public void wheelTargetUsesTabScrollOverSidePanel() {
		ClientPreferences.sidePanelMode = SidePanelLayout.MODE_OVERLAY;
		ViewportLayout layout = ViewportLayout.forSize(1280, 720, UiScale.Mode.PERCENT_100);
		InputRouter router = new InputRouter();
		int screenX = layout.sidePanelLayout.topTabBar.x + 30;
		int screenY = layout.sidePanelLayout.topTabBar.y + 12;
		router.update(layout, screenX, screenY);

		assertEquals(InputRouter.WheelTarget.TAB_SCROLL, router.wheelTarget(layout, false));
	}
}
