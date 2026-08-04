import java.awt.Color;

/**
 * Lightweight developer overlay for scaling and layout work.
 */
final class ClientDebugOverlay {

	private static final int LINE_HEIGHT = 15;
	private static final int HEADER_HEIGHT = 25;

	private ClientDebugOverlay() {
	}

	static void draw(TextDrawingArea textArea, ClientDebugSnapshot snapshot, int x, int y) {
		String[] labels = {
				"Window:",
				"Logical UI:",
				"UI scale:",
				"Mouse (screen):",
				"Mouse (logical):",
				"Active screen:",
				"Panel bounds:",
				"Memory:",
				"Coords:",
				"Interface:",
				"Zoom level:"
		};
		String[] values = {
				snapshot.windowWidth + "x" + snapshot.windowHeight,
				snapshot.logicalUiWidth + "x" + snapshot.logicalUiHeight,
				formatUiScale(snapshot.uiScale),
				snapshot.mouseScreenX + ", " + snapshot.mouseScreenY,
				snapshot.mouseLogicalX + ", " + snapshot.mouseLogicalY,
				snapshot.activeScreen,
				snapshot.panelBounds,
				snapshot.memoryMb + "mb",
				snapshot.playerX + ", " + snapshot.playerY,
				Integer.toString(snapshot.openInterfaceId),
				Integer.toString(snapshot.zoom)
		};

		int itemCount = labels.length;
		int width = 320;
		int height = HEADER_HEIGHT + (itemCount * LINE_HEIGHT);
		int fill = 0x5d5447;
		int fill2 = Color.BLACK.hashCode();
		int opacity = 140;

		DrawingArea.fillArea(fill, y, width, height, opacity, x);
		DrawingArea.fillArea(fill2, y + 1, width - 2, 16, opacity, x + 1);
		DrawingArea.fillPixels(y + 18, height - 19, fill2, x + 1, width - 2);
		textArea.textLeft(Color.WHITE.darker().hashCode(), "Debug Info", y + 14, x + 3);
		int fpsColor = snapshot.fps > 40
				? Color.YELLOW.hashCode()
				: snapshot.fps > 25 ? Color.ORANGE.hashCode() : Color.RED.hashCode();
		textArea.textLeft(fpsColor, snapshot.fps + "fps", y + 14,
				x + width - textArea.getTextWidth(snapshot.fps + "fps") - 3);

		int rowY = y + HEADER_HEIGHT - 2;
		for (int i = 0; i < labels.length; i++) {
			textArea.textLeftShadow(true, x + 4, Color.WHITE.hashCode(), labels[i], rowY);
			textArea.textRightShadow(true, x + width - 4, Color.YELLOW.hashCode(), values[i], rowY);
			rowY += LINE_HEIGHT;
		}
	}

	private static String formatUiScale(double uiScale) {
		return String.format(java.util.Locale.ROOT, "%.0f%%", uiScale * 100.0D);
	}
}
