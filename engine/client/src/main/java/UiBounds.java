/**
 * Simple axis-aligned rectangle used for layout and hit testing.
 */
final class UiBounds {

	final int x;
	final int y;
	final int width;
	final int height;

	UiBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	static UiBounds centeredAt(int centerX, int centerY, int width, int height) {
		return new UiBounds(centerX - width / 2, centerY - height / 2, width, height);
	}

	int centerX() {
		return x + width / 2;
	}

	int centerY() {
		return y + height / 2;
	}

	int right() {
		return x + width;
	}

	int bottom() {
		return y + height;
	}

	boolean contains(int px, int py) {
		return px >= x && px < right() && py >= y && py < bottom();
	}

	UiBounds offset(int dx, int dy) {
		return new UiBounds(x + dx, y + dy, width, height);
	}

	@Override
	public String toString() {
		return x + "," + y + " " + width + "x" + height;
	}
}
