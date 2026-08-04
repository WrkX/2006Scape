/**
 * Window-space layout for the side panel, minimap, and tab bars.
 */
final class SidePanelLayout {

	static final int TAB_CONTENT_HEIGHT = 261;
	static final int TAB_CONTENT_WIDTH = 190;
	static final int MINIMAP_WIDTH = 172;
	static final int MINIMAP_HEIGHT = 156;
	static final int TOP_TAB_BAR_WIDTH = 249;
	static final int TOP_TAB_BAR_HEIGHT = 45;
	static final int BOTTOM_TAB_BAR_WIDTH = 269;
	static final int BOTTOM_TAB_BAR_HEIGHT = 37;
	static final int MODERN_TAB_BUTTON_WIDTH = 36;
	static final int MODERN_TAB_BAR_WIDTH = MODERN_TAB_BUTTON_WIDTH * 14;
	static final int MODERN_TAB_BAR_HEIGHT = 40;
	static final int RIGHT_UI_MARGIN = 4;
	static final int OVERLAY_PANEL_LEFT_EDGE_WIDTH = 12;

	static final int MODE_CLASSIC = 0;
	static final int MODE_OVERLAY = 1;
	static final int MODE_MODERN = 2;

	static final int SMALL_WINDOW_WIDTH = 1024;
	static final int MEDIUM_WINDOW_WIDTH = 1366;

	final int windowWidth;
	final int windowHeight;
	final int sidePanelMode;
	final UiBounds tabContent;
	final UiBounds minimap;
	final UiBounds topTabBar;
	final UiBounds bottomTabBar;
	final UiBounds modernTabBar;
	final UiBounds overlayPanelLeftEdge;

	private SidePanelLayout(
			int windowWidth,
			int windowHeight,
			int sidePanelMode,
			UiBounds tabContent,
			UiBounds minimap,
			UiBounds topTabBar,
			UiBounds bottomTabBar,
			UiBounds modernTabBar,
			UiBounds overlayPanelLeftEdge) {
		this.windowWidth = windowWidth;
		this.windowHeight = windowHeight;
		this.sidePanelMode = sidePanelMode;
		this.tabContent = tabContent;
		this.minimap = minimap;
		this.topTabBar = topTabBar;
		this.bottomTabBar = bottomTabBar;
		this.modernTabBar = modernTabBar;
		this.overlayPanelLeftEdge = overlayPanelLeftEdge;
	}

	static SidePanelLayout forWindow(int windowWidth, int windowHeight, int sidePanelMode) {
		boolean classic = sidePanelMode == MODE_CLASSIC;
		boolean modern = sidePanelMode == MODE_MODERN;

		int tabContentX = classic
				? windowWidth - 212
				: windowWidth - TAB_CONTENT_WIDTH - RIGHT_UI_MARGIN;
		int tabContentY;
		int topIconsX;
		int topIconsY;
		int botIconsX;
		int botIconsY;
		int modernTabBarX = Math.max(RIGHT_UI_MARGIN, windowWidth - MODERN_TAB_BAR_WIDTH - RIGHT_UI_MARGIN);
		int modernTabBarY = windowHeight - MODERN_TAB_BAR_HEIGHT - RIGHT_UI_MARGIN;

		if (classic) {
			tabContentY = 205;
			topIconsX = windowWidth - TOP_TAB_BAR_WIDTH;
			topIconsY = 160;
			botIconsX = windowWidth - BOTTOM_TAB_BAR_WIDTH;
			botIconsY = tabContentY + TAB_CONTENT_HEIGHT;
		} else if (modern) {
			topIconsX = Math.max(0, windowWidth - MODERN_TAB_BAR_WIDTH - RIGHT_UI_MARGIN);
			topIconsY = modernTabBarY;
			tabContentY = Math.max(4, topIconsY - TAB_CONTENT_HEIGHT);
			botIconsX = topIconsX + TOP_TAB_BAR_WIDTH;
			botIconsY = windowHeight - BOTTOM_TAB_BAR_HEIGHT - RIGHT_UI_MARGIN;
		} else {
			topIconsX = windowWidth - TOP_TAB_BAR_WIDTH;
			topIconsY = windowHeight - BOTTOM_TAB_BAR_HEIGHT - RIGHT_UI_MARGIN
					- TAB_CONTENT_HEIGHT - TOP_TAB_BAR_HEIGHT;
			tabContentY = topIconsY + TOP_TAB_BAR_HEIGHT;
			botIconsX = windowWidth - BOTTOM_TAB_BAR_WIDTH;
			botIconsY = tabContentY + TAB_CONTENT_HEIGHT;
		}

		int minimapX = classic ? windowWidth - 215 : windowWidth - MINIMAP_WIDTH - RIGHT_UI_MARGIN;
		UiBounds tabContentBounds = new UiBounds(tabContentX, tabContentY, TAB_CONTENT_WIDTH, TAB_CONTENT_HEIGHT);
		UiBounds minimapBounds = new UiBounds(minimapX, 4, MINIMAP_WIDTH, MINIMAP_HEIGHT);
		UiBounds topTabBarBounds = new UiBounds(topIconsX, topIconsY, TOP_TAB_BAR_WIDTH, TOP_TAB_BAR_HEIGHT);
		UiBounds bottomTabBarBounds = new UiBounds(botIconsX, botIconsY, BOTTOM_TAB_BAR_WIDTH, BOTTOM_TAB_BAR_HEIGHT);
		UiBounds modernTabBarBounds = new UiBounds(modernTabBarX, modernTabBarY, MODERN_TAB_BAR_WIDTH, MODERN_TAB_BAR_HEIGHT);
		int overlayLeft = tabContentX - (modern ? 0 : OVERLAY_PANEL_LEFT_EDGE_WIDTH);
		UiBounds overlayLeftEdge = new UiBounds(overlayLeft, tabContentY, OVERLAY_PANEL_LEFT_EDGE_WIDTH, TAB_CONTENT_HEIGHT);

		return new SidePanelLayout(
				windowWidth,
				windowHeight,
				sidePanelMode,
				tabContentBounds,
				minimapBounds,
				topTabBarBounds,
				bottomTabBarBounds,
				modernTabBarBounds,
				overlayLeftEdge);
	}

	static int recommendedMode(int windowWidth, int windowHeight) {
		if (windowWidth < SMALL_WINDOW_WIDTH || windowHeight < 600) {
			return MODE_OVERLAY;
		}
		if (windowWidth >= MEDIUM_WINDOW_WIDTH && windowHeight >= 720) {
			return MODE_MODERN;
		}
		return MODE_OVERLAY;
	}

	boolean isClassic() {
		return sidePanelMode == MODE_CLASSIC;
	}

	boolean isModern() {
		return sidePanelMode == MODE_MODERN;
	}

	boolean containsTabContent(int windowX, int windowY) {
		return tabContent.contains(windowX, windowY);
	}

	boolean containsRightUi(int windowX, int windowY) {
		if (isClassic()) {
			return false;
		}
		if (minimap.contains(windowX, windowY)) {
			return true;
		}
		int panelLeft = isModern() ? tabContent.x : overlayPanelLeftEdge.x;
		if (windowX >= panelLeft && windowX < tabContent.right()
				&& windowY >= tabContent.y && windowY < tabContent.bottom()) {
			return true;
		}
		if (isModern()) {
			return modernTabBar.contains(windowX, windowY);
		}
		if (topTabBar.contains(windowX, windowY)) {
			return true;
		}
		return bottomTabBar.contains(windowX, windowY);
	}

	int tabContentLocalX(int windowX) {
		return windowX - tabContent.x;
	}

	int tabContentLocalY(int windowY) {
		return windowY - tabContent.y;
	}

	UiBounds[] debugRegions() {
		if (isClassic()) {
			return new UiBounds[] { tabContent, minimap };
		}
		if (isModern()) {
			return new UiBounds[] { minimap, tabContent, modernTabBar };
		}
		return new UiBounds[] { minimap, overlayPanelLeftEdge, tabContent, topTabBar, bottomTabBar };
	}
}
