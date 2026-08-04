import java.awt.Canvas;
import java.awt.Dimension;

/**
 * Desktop rendering surface for the software rasterizer.
 */
@SuppressWarnings("serial")
final class GameCanvas extends Canvas {

	private final RSApplet client;

	GameCanvas(RSApplet client, int width, int height) {
		this.client = client;
		setBackground(java.awt.Color.BLACK);
		setIgnoreRepaint(true);
		setFocusable(true);
		setPreferredSize(new Dimension(width, height));
		setSize(width, height);
	}

	void updateLogicalSize(int width, int height) {
		setPreferredSize(new Dimension(width, height));
		setSize(width, height);
	}

	@Override
	public void addNotify() {
		super.addNotify();
		client.onCanvasDisplayable();
	}

}
