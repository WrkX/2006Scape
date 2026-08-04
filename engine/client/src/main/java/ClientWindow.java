import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import javax.swing.JFrame;
import javax.swing.Timer;

/**
 * Desktop window hosting the game canvas.
 */
@SuppressWarnings("serial")
final class ClientWindow extends JFrame {

	ClientWindow(RSApplet client, GameCanvas canvas) {
		setTitle(ClientSettings.SERVER_NAME + " World: " + ClientSettings.SERVER_WORLD
				+ ((ClientSettings.SERVER_IP.equals("localhost")
						|| ClientSettings.SERVER_IP.equals("127.0.0.1")) ? " [Local]" : ""));
		setResizable(ClientSettings.RESIZABLE);
		setBackground(Color.BLACK);
		setMinimumSize(new Dimension(765, 503));
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setLayout(new BorderLayout());
		add(canvas, BorderLayout.CENTER);
		pack();
		ClientPreferences.applyWindowState(this);

		if (ClientSettings.RESIZABLE) {
			Timer resizeTimer = new Timer(150, e -> client.publishPendingResize(
					canvas.getWidth(), canvas.getHeight()));
			resizeTimer.setRepeats(false);
			Timer windowStateTimer = new Timer(500, e -> {
				ClientPreferences.captureWindowState(ClientWindow.this);
				ClientPreferences.save();
			});
			windowStateTimer.setRepeats(false);
			addComponentListener(new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent e) {
					resizeTimer.restart();
					windowStateTimer.restart();
				}

				@Override
				public void componentMoved(ComponentEvent e) {
					windowStateTimer.restart();
				}
			});
		}

		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				client.onWindowClosing();
			}
		});
		addWindowFocusListener(new WindowFocusListener() {
			@Override
			public void windowGainedFocus(WindowEvent e) {
				canvas.requestFocusInWindow();
			}

			@Override
			public void windowLostFocus(WindowEvent e) {
			}
		});

		if (!ClientPreferences.hasWindowState) {
			setLocationRelativeTo(null);
		}
		setVisible(true);
		toFront();
		canvas.requestFocusInWindow();
	}

}
