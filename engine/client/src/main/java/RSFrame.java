// Decompiled by Jad v1.5.8f. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3)

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.Timer;

final class RSFrame extends Frame {

	public RSFrame(RSApplet applet) {
		rsApplet = applet;

		setTitle(ClientSettings.SERVER_NAME + " World: " + ClientSettings.SERVER_WORLD + ((ClientSettings.SERVER_IP.equals("localhost") || ClientSettings.SERVER_IP.equals("127.0.0.1")) ?  " [Local]" : ""));
		this.setResizable(ClientSettings.RESIZABLE);
		this.setBackground(Color.BLACK);
		this.setMinimumSize(new Dimension(765, 503));

		this.setLayout(new BorderLayout());
		this.add(applet, BorderLayout.CENTER);
		this.pack();

		if (ClientSettings.RESIZABLE) {
			Timer resizeTimer = new Timer(150, e -> {
				int w = rsApplet.getWidth();
				int h = rsApplet.getHeight();
				if (w >= 765 && h >= 503 && rsApplet instanceof Game) {
					((Game) rsApplet).onResize(w, h);
				}
			});
			resizeTimer.setRepeats(false);
			addComponentListener(new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent e) {
					resizeTimer.restart();
				}
			});
		}

		this.setLocationRelativeTo(null);
		this.setVisible(true);
		this.toFront();
		this.transferFocus();
	}

	private final RSApplet rsApplet;

}
