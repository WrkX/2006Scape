/**
 * Tab icon hitboxes in window coordinates.
 */
final class TabLayout {

	static final int TAB_COUNT = 14;

	private static final int[][] TOP_TAB_OFFSETS = {
			{ 23, 9, 57, 45 },
			{ 53, 8, 83, 45 },
			{ 81, 8, 111, 45 },
			{ 109, 8, 153, 43 },
			{ 150, 8, 180, 45 },
			{ 178, 8, 208, 45 },
			{ 206, 9, 240, 45 }
	};

	private static final int[][] BOTTOM_TAB_OFFSETS = {
			{ 44, 0, 78, 36 },
			{ 76, 0, 106, 37 },
			{ 103, 0, 133, 37 },
			{ 131, 1, 175, 36 },
			{ 173, 0, 203, 37 },
			{ 200, 0, 230, 37 },
			{ 228, 0, 262, 36 }
	};

	final UiBounds[] tabs;
	final UiBounds modernTabBar;

	private TabLayout(UiBounds[] tabs, UiBounds modernTabBar) {
		this.tabs = tabs;
		this.modernTabBar = modernTabBar;
	}

	static TabLayout forSidePanel(SidePanelLayout sidePanel) {
		UiBounds[] tabs = new UiBounds[TAB_COUNT];
		for (int index = 0; index < 7; index++) {
			int[] offsets = TOP_TAB_OFFSETS[index];
			tabs[index] = new UiBounds(
					sidePanel.topTabBar.x + offsets[0],
					sidePanel.topTabBar.y + offsets[1],
					offsets[2] - offsets[0],
					offsets[3] - offsets[1]);
		}
		for (int index = 0; index < 7; index++) {
			int[] offsets = BOTTOM_TAB_OFFSETS[index];
			tabs[index + 7] = new UiBounds(
					sidePanel.bottomTabBar.x + offsets[0],
					sidePanel.bottomTabBar.y + offsets[1],
					offsets[2] - offsets[0],
					offsets[3] - offsets[1]);
		}
		return new TabLayout(tabs, sidePanel.modernTabBar);
	}

	static int hitTest(TabLayout layout, SidePanelLayout sidePanel, int windowX, int windowY) {
		if (sidePanel.isModern()) {
			if (!layout.modernTabBar.contains(windowX, windowY)) {
				return -1;
			}
			int localX = windowX - layout.modernTabBar.x;
			return localX / SidePanelLayout.MODERN_TAB_BUTTON_WIDTH;
		}
		for (int index = TAB_COUNT - 1; index >= 0; index--) {
			if (layout.tabs[index].contains(windowX, windowY)) {
				return index;
			}
		}
		return -1;
	}

	boolean containsAnyTab(int windowX, int windowY, SidePanelLayout sidePanel) {
		if (sidePanel.isModern()) {
			return modernTabBar.contains(windowX, windowY);
		}
		return sidePanel.topTabBar.contains(windowX, windowY)
				|| sidePanel.bottomTabBar.contains(windowX, windowY);
	}

	UiBounds[] debugRegions(SidePanelLayout sidePanel) {
		if (sidePanel.isModern()) {
			return new UiBounds[] { modernTabBar };
		}
		return tabs;
	}
}
