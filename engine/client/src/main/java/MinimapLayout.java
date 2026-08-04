/**
 * Minimap bounds and click mapping in window coordinates.
 */
final class MinimapLayout {

	static final int FRAME_Y = 4;
	static final int COMPASS_X_OFFSET = 1;
	static final int COMPASS_Y = 7;
	static final int COMPASS_WIDTH = 26;
	static final int COMPASS_HEIGHT = 33;
	static final int WALK_CLICK_X_OFFSET = 25;
	static final int WALK_CLICK_Y_OFFSET = 5;
	static final int WALK_AREA_WIDTH = 146;
	static final int WALK_AREA_HEIGHT = 151;
	static final int WALK_CENTER_X = 73;
	static final int WALK_CENTER_Y = 75;

	static final int SCALE_NORMAL = 0;
	static final int SCALE_LARGE = 50;
	static final int SCALE_SMALL = -50;

	final UiBounds frame;
	final UiBounds compassNorth;
	final UiBounds walkClickArea;

	private MinimapLayout(UiBounds frame, UiBounds compassNorth, UiBounds walkClickArea) {
		this.frame = frame;
		this.compassNorth = compassNorth;
		this.walkClickArea = walkClickArea;
	}

	static MinimapLayout forSidePanel(SidePanelLayout sidePanel) {
		UiBounds frame = sidePanel.minimap;
		UiBounds compassNorth = new UiBounds(
				frame.x + COMPASS_X_OFFSET,
				COMPASS_Y,
				COMPASS_WIDTH,
				COMPASS_HEIGHT);
		UiBounds walkClickArea = new UiBounds(
				frame.x + WALK_CLICK_X_OFFSET,
				FRAME_Y + WALK_CLICK_Y_OFFSET,
				WALK_AREA_WIDTH,
				WALK_AREA_HEIGHT);
		return new MinimapLayout(frame, compassNorth, walkClickArea);
	}

	static int baseZoomOffset(int minimapScalePreset) {
		switch (minimapScalePreset) {
			case 1:
				return SCALE_LARGE;
			case 2:
				return SCALE_SMALL;
			default:
				return SCALE_NORMAL;
		}
	}

	boolean containsCompassNorth(int windowX, int windowY) {
		return compassNorth.contains(windowX, windowY);
	}

	/**
	 * Maps a window click to rotated minimap offsets used by walk packets.
	 * Returns null when the click is outside the walk area.
	 */
	int[] mapWalkOffset(int windowClickX, int windowClickY) {
		int localX = windowClickX - walkClickArea.x;
		int localY = windowClickY - walkClickArea.y;
		if (localX < 0 || localY < 0 || localX >= WALK_AREA_WIDTH || localY >= WALK_AREA_HEIGHT) {
			return null;
		}
		localX -= WALK_CENTER_X;
		localY -= WALK_CENTER_Y;
		return new int[] { localX, localY };
	}

	int applyZoom(int baseOffset, int dynamicOffset) {
		return baseOffset + dynamicOffset;
	}

	int zoomMultiplier(int minimapZoomOffset) {
		return 256 + minimapZoomOffset;
	}

	UiBounds[] debugRegions() {
		return new UiBounds[] { frame, compassNorth, walkClickArea };
	}
}
