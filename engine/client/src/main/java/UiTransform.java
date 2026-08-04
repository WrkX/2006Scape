/**
 * Maps between screen coordinates and the fixed logical UI coordinate space.
 */
final class UiTransform {

	final double scale;
	final int offsetX;
	final int offsetY;
	final int logicalWidth;
	final int logicalHeight;

	UiTransform(double scale, int offsetX, int offsetY, int logicalWidth, int logicalHeight) {
		this.scale = scale;
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.logicalWidth = logicalWidth;
		this.logicalHeight = logicalHeight;
	}

	static UiTransform forPresentation(ClientSize size, UiScale.Mode mode) {
		double scale = UiScale.resolveScale(mode, size);
		int scaledWidth = (int) Math.round(size.logicalWidth * scale);
		int scaledHeight = (int) Math.round(size.logicalHeight * scale);
		int offsetX = Math.max(0, (size.windowWidth - scaledWidth) / 2);
		int offsetY = Math.max(0, (size.windowHeight - scaledHeight) / 2);
		return new UiTransform(scale, offsetX, offsetY, size.logicalWidth, size.logicalHeight);
	}

	int toLogicalX(int screenX) {
		if (screenX < offsetX) {
			return -1;
		}
		return (int) Math.floor((screenX - offsetX) / scale);
	}

	int toLogicalY(int screenY) {
		if (screenY < offsetY) {
			return -1;
		}
		return (int) Math.floor((screenY - offsetY) / scale);
	}

	int toScreenX(int logicalX) {
		return offsetX + (int) Math.round(logicalX * scale);
	}

	int toScreenY(int logicalY) {
		return offsetY + (int) Math.round(logicalY * scale);
	}

	UiBounds toScreen(UiBounds logicalBounds) {
		return new UiBounds(
				toScreenX(logicalBounds.x),
				toScreenY(logicalBounds.y),
				scaledSize(logicalBounds.width),
				scaledSize(logicalBounds.height));
	}

	boolean isInsideLogicalCanvas(int screenX, int screenY) {
		int logicalX = toLogicalX(screenX);
		int logicalY = toLogicalY(screenY);
		return logicalX >= 0
				&& logicalY >= 0
				&& logicalX < logicalWidth
				&& logicalY < logicalHeight;
	}

	private int scaledSize(int logicalSize) {
		return Math.max(1, (int) Math.round(logicalSize * scale));
	}
}
