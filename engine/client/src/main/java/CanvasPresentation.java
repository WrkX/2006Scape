import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.image.BufferStrategy;

/**
 * Presents software-rendered frames through a {@link BufferStrategy}
 * instead of calling {@link Canvas#getGraphics()} every frame.
 */
final class CanvasPresentation {

	static final int BUFFER_COUNT = 2;

	private final Canvas canvas;
	private BufferStrategy bufferStrategy;
	private Graphics frameGraphics;

	CanvasPresentation(Canvas canvas) {
		this.canvas = canvas;
	}

	void initialize() {
		if (!canvas.isDisplayable()) {
			return;
		}
		if (bufferStrategy == null) {
			canvas.createBufferStrategy(BUFFER_COUNT);
			bufferStrategy = canvas.getBufferStrategy();
		}
	}

	void recreateBuffers() {
		disposeFrameGraphics();
		bufferStrategy = null;
		initialize();
	}

	Graphics beginFrame() {
		if (!canvas.isDisplayable()) {
			return null;
		}
		if (bufferStrategy == null) {
			initialize();
		}
		if (bufferStrategy == null) {
			return null;
		}

		do {
			if (bufferStrategy.contentsLost()) {
				recreateBuffers();
				if (bufferStrategy == null) {
					return null;
				}
			}
			disposeFrameGraphics();
			frameGraphics = bufferStrategy.getDrawGraphics();
		} while (bufferStrategy.contentsRestored());

		return frameGraphics;
	}

	void endFrame() {
		disposeFrameGraphics();
		if (bufferStrategy != null) {
			bufferStrategy.show();
		}
	}

	void dispose() {
		disposeFrameGraphics();
		bufferStrategy = null;
	}

	boolean hasActiveStrategy() {
		return bufferStrategy != null;
	}

	private void disposeFrameGraphics() {
		if (frameGraphics != null) {
			frameGraphics.dispose();
			frameGraphics = null;
		}
	}

}
