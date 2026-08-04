import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class MinimapLayoutTest {

	@After
	public void resetPreferences() {
		ClientPreferences.resetToDefaults();
	}

	@Test
	public void classicReferenceResolutionMatchesHistoricalBounds() {
		MinimapLayout layout = MinimapLayout.forSidePanel(
				SidePanelLayout.forWindow(765, 503, SidePanelLayout.MODE_CLASSIC));

		assertEquals(550, layout.frame.x);
		assertEquals(4, layout.frame.y);
		assertEquals(172, layout.frame.width);
		assertTrue(layout.containsCompassNorth(551, 20));
	}

	@Test
	public void walkClickMappingMatchesHistoricalOffsets() {
		ClientPreferences.sidePanelMode = SidePanelLayout.MODE_OVERLAY;
		ViewportLayout viewport = ViewportLayout.forSize(765, 503, UiScale.Mode.PERCENT_100);
		MinimapLayout layout = viewport.minimapLayout;
		int clickX = layout.frame.x + 25 + 80;
		int clickY = 4 + 5 + 90;

		int[] offset = layout.mapWalkOffset(clickX, clickY);
		assertNotNull(offset);
		assertEquals(7, offset[0]);
		assertEquals(15, offset[1]);
	}

	@Test
	public void walkClickOutsideAreaReturnsNull() {
		ViewportLayout viewport = ViewportLayout.forSize(765, 503, UiScale.Mode.PERCENT_100);
		assertNull(viewport.minimapLayout.mapWalkOffset(10, 10));
	}

	@Test
	public void scalePresetMapsToExpectedZoomOffsets() {
		assertEquals(0, MinimapLayout.baseZoomOffset(0));
		assertEquals(50, MinimapLayout.baseZoomOffset(1));
		assertEquals(-50, MinimapLayout.baseZoomOffset(2));
	}

	@Test
	public void scaledWindowKeepsCompassHitsAligned() {
		ClientPreferences.sidePanelMode = SidePanelLayout.MODE_OVERLAY;
		UiScale.setMode(UiScale.Mode.PERCENT_200);
		ViewportLayout layout = ViewportLayout.forSize(2560, 1440, UiScale.Mode.PERCENT_200);
		InputRouter router = new InputRouter();
		MinimapLayout minimap = layout.minimapLayout;
		int screenX = minimap.compassNorth.x + 5;
		int screenY = minimap.compassNorth.y + 5;
		router.update(layout, screenX, screenY);

		assertTrue(minimap.containsCompassNorth(router.screenX, router.screenY));
		assertFalse(minimap.containsCompassNorth(router.screenX + 200, router.screenY));
	}

	@After
	public void resetScaleMode() {
		UiScale.setMode(UiScale.Mode.PERCENT_100);
	}
}
