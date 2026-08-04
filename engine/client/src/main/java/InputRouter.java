/**
 * Routes raw screen mouse coordinates through the active viewport transform.
 */
final class InputRouter {

	enum WheelTarget {
		NONE,
		WORLD_ZOOM,
		CHAT_SCROLL,
		TAB_SCROLL
	}

	int screenX;
	int screenY;
	int logicalX;
	int logicalY;

	void update(ViewportLayout layout, int rawScreenX, int rawScreenY) {
		screenX = rawScreenX;
		screenY = rawScreenY;
		if (rawScreenX < 0 || rawScreenY < 0) {
			logicalX = -1;
			logicalY = -1;
			return;
		}
		logicalX = layout.uiTransform.toLogicalX(rawScreenX);
		logicalY = layout.uiTransform.toLogicalY(rawScreenY);
	}

	int logicalClickX(ViewportLayout layout, int rawClickX) {
		return layout.uiTransform.toLogicalX(rawClickX);
	}

	int logicalClickY(ViewportLayout layout, int rawClickY) {
		return layout.uiTransform.toLogicalY(rawClickY);
	}

	NavbarLayout.Item hitNavbar(ViewportLayout layout) {
		if (logicalX < 0 || logicalY < 0) {
			return NavbarLayout.Item.NONE;
		}
		return NavbarLayout.hitTest(layout.navbarLayout, logicalX, logicalY);
	}

	ChatLayout.FooterButton hitChatFooter(ViewportLayout layout) {
		if (screenX < 0 || screenY < 0) {
			return ChatLayout.FooterButton.NONE;
		}
		return ChatLayout.hitFooter(layout.chatLayout, screenX, screenY);
	}

	boolean isOverChat(ViewportLayout layout) {
		if (screenX < 0 || screenY < 0) {
			return false;
		}
		return layout.chatLayout.containsPanelOrFooter(screenX, screenY);
	}

	boolean isOverChatMessages(ViewportLayout layout) {
		if (screenX < 0 || screenY < 0) {
			return false;
		}
		return layout.chatLayout.containsMessages(screenX, screenY);
	}

	boolean isOverChatScrollbar(ViewportLayout layout) {
		if (screenX < 0 || screenY < 0) {
			return false;
		}
		return layout.chatLayout.containsScrollbar(screenX, screenY);
	}

	boolean isOverTabContent(ViewportLayout layout) {
		if (screenX < 0 || screenY < 0) {
			return false;
		}
		return layout.sidePanelLayout.containsTabContent(screenX, screenY);
	}

	boolean isOverMinimap(ViewportLayout layout) {
		if (screenX < 0 || screenY < 0) {
			return false;
		}
		return layout.minimapLayout.frame.contains(screenX, screenY);
	}

	boolean isOverRightUi(ViewportLayout layout) {
		if (screenX < 0 || screenY < 0) {
			return false;
		}
		return layout.sidePanelLayout.containsRightUi(screenX, screenY);
	}

	int hitTab(ViewportLayout layout) {
		if (screenX < 0 || screenY < 0) {
			return -1;
		}
		return TabLayout.hitTest(layout.tabLayout, layout.sidePanelLayout, screenX, screenY);
	}

	int menuLocalX(ViewportLayout layout, ContextMenuLayout.ScreenArea area) {
		return ContextMenuLayout.menuLocalX(area, layout, layout.chatLayout, logicalX, screenX);
	}

	int menuLocalY(ViewportLayout layout, ContextMenuLayout.ScreenArea area) {
		return ContextMenuLayout.menuLocalY(area, layout, layout.chatLayout, logicalY, screenY);
	}

	int menuLocalClickX(ViewportLayout layout, ContextMenuLayout.ScreenArea area, int rawClickX) {
		return ContextMenuLayout.menuLocalX(
				area,
				layout,
				layout.chatLayout,
				logicalClickX(layout, rawClickX),
				rawClickX);
	}

	int menuLocalClickY(ViewportLayout layout, ContextMenuLayout.ScreenArea area, int rawClickY) {
		return ContextMenuLayout.menuLocalY(
				area,
				layout,
				layout.chatLayout,
				logicalClickY(layout, rawClickY),
				rawClickY);
	}

	WheelTarget wheelTarget(ViewportLayout layout, boolean openInterfaceBlocksZoom) {
		if (screenX < 0 || screenY < 0) {
			return WheelTarget.NONE;
		}
		if (layout.chatLayout.containsPanelOrFooter(screenX, screenY)) {
			return WheelTarget.CHAT_SCROLL;
		}
		if (layout.tabLayout.containsAnyTab(screenX, screenY, layout.sidePanelLayout)) {
			return WheelTarget.TAB_SCROLL;
		}
		if (!openInterfaceBlocksZoom && layout.worldViewport.contains(logicalX, logicalY)) {
			return WheelTarget.WORLD_ZOOM;
		}
		return WheelTarget.NONE;
	}
}
