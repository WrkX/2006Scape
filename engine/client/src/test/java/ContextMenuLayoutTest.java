import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class ContextMenuLayoutTest {

	@After
	public void resetPreferences() {
		ClientPreferences.resetToDefaults();
	}

	@Test
	public void placesMenuCenteredOnClickAndClampedInsideArea() {
		ContextMenuLayout.Placement placement = ContextMenuLayout.placeAtClick(
				ContextMenuLayout.ScreenArea.GAME,
				250,
				180,
				120,
				80,
				512,
				334);

		assertEquals(ContextMenuLayout.ScreenArea.GAME, placement.area);
		assertEquals(190, placement.offsetX);
		assertEquals(180, placement.offsetY);
	}

	@Test
	public void clampsMenuAgainstRightAndBottomEdges() {
		ContextMenuLayout.Placement placement = ContextMenuLayout.placeAtClick(
				ContextMenuLayout.ScreenArea.CHAT,
				450,
				90,
				120,
				80,
				479,
				96);

		assertEquals(359, placement.offsetX);
		assertEquals(16, placement.offsetY);
	}

	@Test
	public void detectsGameChatAndTabAreas() {
		ClientPreferences.sidePanelMode = SidePanelLayout.MODE_OVERLAY;
		ViewportLayout layout = ViewportLayout.forSize(765, 503, UiScale.Mode.PERCENT_100);

		assertEquals(
				ContextMenuLayout.ScreenArea.GAME,
				ContextMenuLayout.areaAt(layout, 200, 200, 200, 200, true));
		assertEquals(
				ContextMenuLayout.ScreenArea.CHAT,
				ContextMenuLayout.areaAt(layout, 100, 400, 100, 400, true));
		assertEquals(
				ContextMenuLayout.ScreenArea.TAB,
				ContextMenuLayout.areaAt(layout, 600, 220, 600, 220, true));
	}

	@Test
	public void gameMenuLocalsUseScreenCoordinatesNotLetterboxedLogical() {
		ViewportLayout layout = ViewportLayout.forSize(1920, 1080, UiScale.Mode.PERCENT_100);
		int screenX = layout.worldViewport.x + 250;
		int screenY = layout.worldViewport.y + 180;

		// UiTransform letterboxes the 765x503 logical canvas; menus must ignore that offset.
		assertTrue(layout.uiTransform.offsetX > 0);
		assertEquals(
				ContextMenuLayout.ScreenArea.GAME,
				ContextMenuLayout.areaAt(layout, -1, -1, screenX, screenY, true));
		assertEquals(250, ContextMenuLayout.menuLocalX(
				ContextMenuLayout.ScreenArea.GAME, layout, layout.chatLayout, -1, screenX));
		assertEquals(180, ContextMenuLayout.menuLocalY(
				ContextMenuLayout.ScreenArea.GAME, layout, layout.chatLayout, -1, screenY));

		ContextMenuLayout.Placement placement = ContextMenuLayout.placeAtClick(
				ContextMenuLayout.ScreenArea.GAME,
				ContextMenuLayout.menuLocalX(ContextMenuLayout.ScreenArea.GAME, layout, layout.chatLayout, -1, screenX),
				ContextMenuLayout.menuLocalY(ContextMenuLayout.ScreenArea.GAME, layout, layout.chatLayout, -1, screenY),
				120,
				80,
				layout.worldViewport.width,
				layout.worldViewport.height);
		assertEquals(190, placement.offsetX);
		assertEquals(180, placement.offsetY);
	}

	@Test
	public void outsideMenuUsesPadding() {
		ViewportLayout layout = ViewportLayout.forSize(765, 503, UiScale.Mode.PERCENT_100);
		ChatLayout chat = layout.chatLayout;

		assertTrue(ContextMenuLayout.isOutsideMenu(
				ContextMenuLayout.ScreenArea.CHAT,
				layout,
				chat,
				chat.panel.x + 5,
				chat.panel.y + 5,
				chat.panel.x + 5,
				chat.panel.y + 5,
				20,
				20,
				100,
				60));
		assertFalse(ContextMenuLayout.isOutsideMenu(
				ContextMenuLayout.ScreenArea.CHAT,
				layout,
				chat,
				chat.panel.x + 70,
				chat.panel.y + 50,
				chat.panel.x + 70,
				chat.panel.y + 50,
				20,
				20,
				100,
				60));
	}

	@Test
	public void tabMenuLocalsUseTabContentCoordinates() {
		ClientPreferences.sidePanelMode = SidePanelLayout.MODE_OVERLAY;
		ViewportLayout layout = ViewportLayout.forSize(765, 503, UiScale.Mode.PERCENT_100);
		int screenX = layout.sidePanelLayout.tabContent.x + 40;
		int screenY = layout.sidePanelLayout.tabContent.y + 55;

		assertEquals(40, ContextMenuLayout.menuLocalX(
				ContextMenuLayout.ScreenArea.TAB, layout, layout.chatLayout, -1, screenX));
		assertEquals(55, ContextMenuLayout.menuLocalY(
				ContextMenuLayout.ScreenArea.TAB, layout, layout.chatLayout, -1, screenY));
	}
}
