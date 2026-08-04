/**
 * Context menu placement in logical UI coordinates.
 */
final class ContextMenuLayout {

	static final int DISMISS_PADDING = 10;

	enum ScreenArea {
		NONE(-1),
		GAME(0),
		TAB(1),
		CHAT(2);

		final int legacyId;

		ScreenArea(int legacyId) {
			this.legacyId = legacyId;
		}

		static ScreenArea fromLegacyId(int legacyId) {
			for (ScreenArea area : values()) {
				if (area.legacyId == legacyId) {
					return area;
				}
			}
			return NONE;
		}
	}

	static final class Placement {
		final ScreenArea area;
		final int offsetX;
		final int offsetY;

		Placement(ScreenArea area, int offsetX, int offsetY) {
			this.area = area;
			this.offsetX = offsetX;
			this.offsetY = offsetY;
		}
	}

	private ContextMenuLayout() {
	}

	static Placement placeAtClick(
			ScreenArea area,
			int localClickX,
			int localClickY,
			int menuWidth,
			int menuHeight,
			int areaWidth,
			int areaHeight) {
		int offsetX = localClickX - menuWidth / 2;
		if (offsetX + menuWidth > areaWidth) {
			offsetX = areaWidth - menuWidth;
		}
		if (offsetX < 0) {
			offsetX = 0;
		}

		int offsetY = localClickY;
		if (offsetY + menuHeight > areaHeight) {
			offsetY = areaHeight - menuHeight;
		}
		if (offsetY < 0) {
			offsetY = 0;
		}

		return new Placement(area, offsetX, offsetY);
	}

	static ScreenArea areaAt(
			ViewportLayout layout,
			int logicalX,
			int logicalY,
			int screenX,
			int screenY,
			boolean chatVisible) {
		if (screenX < 0 || screenY < 0) {
			return ScreenArea.NONE;
		}
		// World, side panel, and chat are laid out in window/screen pixels.
		// Do not use UiTransform logical coords here — that letterboxed 765x503
		// space only applies to login/navbar presentation.
		if (layout.worldViewport.contains(screenX, screenY)) {
			return ScreenArea.GAME;
		}
		if (layout.sidePanelLayout.containsTabContent(screenX, screenY)) {
			return ScreenArea.TAB;
		}
		if (chatVisible && layout.chatLayout.containsPanel(screenX, screenY)) {
			return ScreenArea.CHAT;
		}
		return ScreenArea.NONE;
	}

	static int menuLocalX(ScreenArea area, ViewportLayout layout, ChatLayout chat, int logicalX, int screenX) {
		switch (area) {
			case GAME:
				return screenX - layout.worldViewport.x;
			case TAB:
				return layout.sidePanelLayout.tabContentLocalX(screenX);
			case CHAT:
				return screenX - chat.panel.x;
			default:
				return screenX;
		}
	}

	static int menuLocalY(ScreenArea area, ViewportLayout layout, ChatLayout chat, int logicalY, int screenY) {
		switch (area) {
			case GAME:
				return screenY - layout.worldViewport.y;
			case TAB:
				return layout.sidePanelLayout.tabContentLocalY(screenY);
			case CHAT:
				return screenY - chat.panel.y;
			default:
				return screenY;
		}
	}

	static boolean isOutsideMenu(
			ScreenArea area,
			ViewportLayout layout,
			ChatLayout chat,
			int logicalX,
			int logicalY,
			int screenX,
			int screenY,
			int menuOffsetX,
			int menuOffsetY,
			int menuWidth,
			int menuHeight) {
		int localX = menuLocalX(area, layout, chat, logicalX, screenX);
		int localY = menuLocalY(area, layout, chat, logicalY, screenY);
		return localX < menuOffsetX - DISMISS_PADDING
				|| localX > menuOffsetX + menuWidth + DISMISS_PADDING
				|| localY < menuOffsetY - DISMISS_PADDING
				|| localY > menuOffsetY + menuHeight + DISMISS_PADDING;
	}
}
