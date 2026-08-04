// Decompiled by Jad v1.5.8f. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3) 

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.net.URI;
import java.util.Map;

public class RSApplet implements Runnable, MouseListener, MouseWheelListener, MouseMotionListener, KeyListener, FocusListener, WindowListener {

	public static boolean ctrlDown = false;
	public static boolean shiftDown = false;
	private int mouseWheelX = 0;
	private int mouseWheelY = 0;
	public static boolean removeShiftDropOnMenuOpen;

	final void createClientFrame(int i, int j) {
		startClient(i, j);
	}

	final void startClient(int height, int width) {
		myWidth = width;
		myHeight = height;
		gameCanvas = new GameCanvas(this, width, height);
		presentation = new CanvasPresentation(gameCanvas);
		clientWindow = new ClientWindow(this, gameCanvas);
		fullGameScreen = new RSImageProducer(myWidth, myHeight, gameCanvas);

		if (ClientSettings.SHOW_NAVBAR) {
			try {
				java.awt.Font arial = new java.awt.Font("Arial", java.awt.Font.PLAIN, 11);
				Map attributes = arial.getAttributes();
				attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);

				JLayeredPane layers = new JLayeredPane();
				layers.setPreferredSize(new Dimension(765, 25));

				ImageIcon backgroundImg = new ImageIcon(this.getClass().getResource("navbar.gif"));
				ImageIcon menuImg = new ImageIcon(this.getClass().getResource("navbar_mainmenu.gif"));
				ImageIcon companyImg = new ImageIcon(this.getClass().getResource("navbar_jagex.gif"));
				ImageIcon worldmapImg = new ImageIcon(this.getClass().getResource("navbar_worldmap.gif"));
				ImageIcon manualImg = new ImageIcon(this.getClass().getResource("navbar_manual.gif"));
				ImageIcon rulesImg = new ImageIcon(this.getClass().getResource("navbar_rules.gif"));

				JLabel background = new JLabel(backgroundImg);
				background.setBounds(0, 0, backgroundImg.getIconWidth(), backgroundImg.getIconHeight());

				JLabel company = new JLabel(companyImg);
				company.setBounds(5, 0, companyImg.getIconWidth(), companyImg.getIconHeight());

				JLabel mainMenu = new JLabel(menuImg);
				mainMenu.setBounds(126, 0, menuImg.getIconWidth(), menuImg.getIconHeight());
				mainMenu.addMouseListener(new MouseAdapter() {
					public void mouseClicked(MouseEvent e) {
						try {
							Desktop.getDesktop().browse(URI.create(ClientSettings.NAV_MAINMENU_LINK));
						} catch (Exception ex) {
							ClientLogger.warn("Failed to open navbar link: " + ClientSettings.NAV_MAINMENU_LINK, ex);
						}
					}
				});
				JLabel mainMenuText = new JLabel();
				mainMenuText.setForeground(Color.WHITE);
				mainMenuText.setFont(arial.deriveFont(attributes));
				mainMenuText.setBounds(126 + menuImg.getIconWidth() + 4, 0, 75, 25);
				mainMenuText.setText("Main Menu");
				mainMenuText.addMouseListener(mainMenu.getMouseListeners()[0]);
				mainMenuText.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseEntered(MouseEvent e) {
						mainMenuText.setForeground(Color.LIGHT_GRAY);
						mainMenuText.setCursor(new Cursor(Cursor.HAND_CURSOR));
					}

					@Override
					public void mouseExited(MouseEvent e) {
						mainMenuText.setForeground(Color.WHITE);
						mainMenuText.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
					}
				});

				JLabel worldSelect = new JLabel(menuImg);
				worldSelect.setBounds(250, 0, menuImg.getIconWidth(), menuImg.getIconHeight());
				worldSelect.addMouseListener(new MouseAdapter() {
					public void mouseClicked(MouseEvent e) {
					}
				});
				JLabel worldSelectText = new JLabel();
				worldSelectText.setForeground(Color.WHITE);
				worldSelectText.setFont(arial.deriveFont(attributes));
				worldSelectText.setBounds(250 + menuImg.getIconWidth() + 4, 0, 75, 25);
				worldSelectText.setText("World Select");
				worldSelectText.addMouseListener(worldSelect.getMouseListeners()[0]);
				worldSelectText.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseEntered(MouseEvent e) {
						worldSelectText.setForeground(Color.LIGHT_GRAY);
						worldSelectText.setCursor(new Cursor(Cursor.HAND_CURSOR));
					}

					@Override
					public void mouseExited(MouseEvent e) {
						worldSelectText.setForeground(Color.WHITE);
						worldSelectText.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
					}
				});

				JLabel worldmap = new JLabel(worldmapImg);
				worldmap.setBounds(387, 0, worldmapImg.getIconWidth(), worldmapImg.getIconHeight());
				worldmap.addMouseListener(new MouseAdapter() {
					public void mouseClicked(MouseEvent e) {
						try {
							Desktop.getDesktop().browse(URI.create(ClientSettings.NAV_WORLDMAP_LINK));
						} catch (Exception ex) {
							ClientLogger.warn("Failed to open navbar link: " + ClientSettings.NAV_MAINMENU_LINK, ex);
						}
					}
				});
				JLabel worldmapText = new JLabel();
				worldmapText.setForeground(Color.WHITE);
				worldmapText.setFont(arial.deriveFont(attributes));
				worldmapText.setBounds(387 + worldmapImg.getIconWidth() + 4, 0, 75, 25);
				worldmapText.setText("World Map");
				worldmapText.addMouseListener(worldmap.getMouseListeners()[0]);
				worldmapText.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseEntered(MouseEvent e) {
						worldmapText.setForeground(Color.LIGHT_GRAY);
						worldmapText.setCursor(new Cursor(Cursor.HAND_CURSOR));
					}

					@Override
					public void mouseExited(MouseEvent e) {
						worldmapText.setForeground(Color.WHITE);
						worldmapText.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
					}
				});

				JLabel manual = new JLabel(manualImg);
				manual.setBounds(520, 0, manualImg.getIconWidth(), manualImg.getIconHeight());
				manual.addMouseListener(new MouseAdapter() {
					public void mouseClicked(MouseEvent e) {
						try {
							Desktop.getDesktop().browse(URI.create(ClientSettings.NAV_MANUAL_LINK));
						} catch (Exception ex) {
							ClientLogger.warn("Failed to open navbar link: " + ClientSettings.NAV_MAINMENU_LINK, ex);
						}
					}
				});
				JLabel manualText = new JLabel();
				manualText.setForeground(Color.WHITE);
				manualText.setFont(arial.deriveFont(attributes));
				manualText.setBounds(520 + manualImg.getIconWidth() + 4, 0, 50, 25);
				manualText.setText("Manual");
				manualText.addMouseListener(manual.getMouseListeners()[0]);
				manualText.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseEntered(MouseEvent e) {
						manualText.setForeground(Color.LIGHT_GRAY);
						manualText.setCursor(new Cursor(Cursor.HAND_CURSOR));
					}

					@Override
					public void mouseExited(MouseEvent e) {
						manualText.setForeground(Color.WHITE);
						manualText.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
					}
				});

				JLabel rules = new JLabel(rulesImg);
				rules.setBounds(636, 0, rulesImg.getIconWidth(), rulesImg.getIconHeight());
				rules.addMouseListener(new MouseAdapter() {
					public void mouseClicked(MouseEvent e) {
						try {
							Desktop.getDesktop().browse(URI.create(ClientSettings.NAV_RULES_LINK));
						} catch (Exception ex) {
							ClientLogger.warn("Failed to open navbar link: " + ClientSettings.NAV_MAINMENU_LINK, ex);
						}
					}
				});
				JLabel rulesText = new JLabel();
				rulesText.setForeground(Color.WHITE);
				rulesText.setFont(arial.deriveFont(attributes));
				rulesText.setBounds(636 + rulesImg.getIconWidth() + 4, 0, 100, 25);
				rulesText.setText("Rules & Security");
				rulesText.addMouseListener(rules.getMouseListeners()[0]);
				rulesText.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseEntered(MouseEvent e) {
						rulesText.setForeground(Color.LIGHT_GRAY);
						rulesText.setCursor(new Cursor(Cursor.HAND_CURSOR));
					}

					@Override
					public void mouseExited(MouseEvent e) {
						rulesText.setForeground(Color.WHITE);
						rulesText.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
					}
				});

				layers.add(background, 0);
				layers.add(company, 0);
				layers.add(mainMenu, 0);
				layers.add(mainMenuText, 0);
				layers.add(worldSelect, 0);
				layers.add(worldSelectText, 0);
				layers.add(worldmap, 0);
				layers.add(worldmapText, 0);
				layers.add(manual, 0);
				layers.add(manualText, 0);
				layers.add(rules, 0);
				layers.add(rulesText, 0);
				clientWindow.add(layers, BorderLayout.NORTH);
				clientWindow.pack();
				ClientPreferences.applyWindowState(clientWindow);
			} catch (Exception ex) {
				ClientLogger.warn("Failed to initialize client navbar", ex);
			}
		}

		startRunnable(this, 1);
		gameCanvas.requestFocusInWindow();
	}

	final void initClientFrame(int i, int j) {
		myWidth = j;
		myHeight = i;
		Component host = getGameComponent();
		if (host == null) {
			return;
		}
		if (host instanceof Canvas && presentation == null) {
			presentation = new CanvasPresentation((Canvas) host);
		}
		fullGameScreen = new RSImageProducer(myWidth, myHeight, host);
		startRunnable(this, 1);
	}

	void onCanvasDisplayable() {
		if (presentation != null) {
			presentation.initialize();
		}
	}

	void onWindowClosing() {
		if (clientWindow != null) {
			ClientPreferences.captureWindowState(clientWindow);
		}
		ClientPreferences.captureSelectedWorld();
		ClientPreferences.saveImmediately();
		requestShutdown();
	}

	public final void requestShutdown() {
		lifecycle.requestShutdown();
	}

	void suspendClient() {
		lifecycle.beginGracefulShutdown(4000 / delayTime);
	}

	void resumeClient() {
		lifecycle.startClient();
	}

	@Override
	public void run() {
		Component gameComponent = getGameComponent();
		if (gameComponent == null) {
			return;
		}
		gameComponent.addMouseListener(this);
		gameComponent.addMouseMotionListener(this);
		gameComponent.addMouseWheelListener(this);
		gameComponent.addKeyListener(this);
		gameComponent.addFocusListener(this);
		if (clientWindow != null) {
			clientWindow.addWindowListener(this);
		}
		drawLoadingText(0, "Loading...");
		startUp();
		int i = 0;
		int j = 256;
		int k = 1;
		int i1 = 0;
		int j1 = 0;
		for (int k1 = 0; k1 < 10; k1++) {
			aLongArray7[k1] = System.currentTimeMillis();
		}

		System.currentTimeMillis();
		while (lifecycle.isRunning()) {
			if (lifecycle.tickShutdownCountdown()) {
				shutdown();
				return;
			}
			int i2 = j;
			int j2 = k;
			j = 300;
			k = 1;
			long l1 = System.currentTimeMillis();
			if (aLongArray7[i] == 0L) {
				j = i2;
				k = j2;
			} else if (l1 > aLongArray7[i]) {
				j = (int) (2560 * delayTime / (l1 - aLongArray7[i]));
			}
			if (j < 25) {
				j = 25;
			}
			if (j > 256) {
				j = 256;
				k = (int) (delayTime - (l1 - aLongArray7[i]) / 10L);
			}
			if (k > delayTime) {
				k = delayTime;
			}
			aLongArray7[i] = l1;
			i = (i + 1) % 10;
			if (k > 1) {
				for (int k2 = 0; k2 < 10; k2++) {
					if (aLongArray7[k2] != 0L) {
						aLongArray7[k2] += k;
					}
				}

			}
			if (k < minDelay) {
				k = minDelay;
			}
			try {
				Thread.sleep(k);
			} catch (InterruptedException _ex) {
				j1++;
			}
			for (; i1 < 256; i1 += j) {
				clickMode3 = clickMode1;
				saveClickX = clickX;
				saveClickY = clickY;
				aLong29 = clickTime;
				clickMode1 = 0;
				processGameLoop();
				readIndex = writeIndex;
			}

			i1 &= 0xff;
			if (delayTime > 0) {
				fps = 1000 * j / (delayTime * 256);
			}
			presentFrame();
			if (shouldDebug) {
				System.out.println("ntime:" + l1);
				for (int l2 = 0; l2 < 10; l2++) {
					int i3 = (i - l2 - 1 + 20) % 10;
					System.out.println("otim" + i3 + ":" + aLongArray7[i3]);
				}

				System.out.println("fps:" + fps + " ratio:" + j + " count:" + i1);
				System.out.println("del:" + k + " deltime:" + delayTime + " mindel:" + minDelay);
				System.out.println("intex:" + j1 + " opos:" + i);
				shouldDebug = false;
				j1 = 0;
			}
		}
		if (lifecycle.isShutdownRequested()) {
			shutdown();
		}
	}

	void presentFrame() {
		if (presentation != null) {
			graphics = presentation.beginFrame();
			if (graphics != null) {
				processDrawing();
				presentation.endFrame();
				return;
			}
		}
		graphics = getGameComponent() != null ? getGameComponent().getGraphics() : null;
		if (graphics != null) {
			processDrawing();
			graphics.dispose();
			graphics = null;
		}
	}

	/**
	 * Acquires a graphics context for one-off draws outside the main frame loop
	 * (for example startup loading text before the first {@link #presentFrame()}).
	 */
	Graphics beginStandaloneFrame() {
		if (presentation != null) {
			return presentation.beginFrame();
		}
		Component component = getGameComponent();
		return component != null ? component.getGraphics() : null;
	}

	void endStandaloneFrame(boolean usedPresentation, Graphics frameGraphics) {
		if (usedPresentation && presentation != null) {
			presentation.endFrame();
			return;
		}
		if (frameGraphics != null) {
			frameGraphics.dispose();
		}
	}

	void shutdown() {
		lifecycle.markShutdownComplete();
		if (presentation != null) {
			presentation.dispose();
			presentation = null;
		}
		cleanUpForQuit();
		Signlink.shutdown();
		if (clientWindow != null) {
			try {
				Thread.sleep(100L);
			} catch (Exception _ex) {
			}
			clientWindow.dispose();
			clientWindow = null;
		}
		try {
			System.exit(0);
		} catch (Throwable _ex) {
		}
	}

	final void method4(int i) {
		delayTime = 1000 / i;
	}

	public boolean mouseWheelDown;

	@Override
	public final void mousePressed(MouseEvent mouseevent) {
		int i = mouseevent.getX();
		int j = mouseevent.getY();
		idleTime = 0;
		clickX = i;
		clickY = j;
		clickTime = System.currentTimeMillis();
		if (mouseevent.getButton() == MouseEvent.BUTTON2) {
			mouseWheelDown = true;
			mouseWheelX = mouseevent.getX();
			mouseWheelY = mouseevent.getY();
			return;
		}

		if (mouseevent.getButton() == MouseEvent.BUTTON3) {
			clickMode1 = 2;
			clickMode2 = 2;
		} else {
			clickMode1 = 1;
			clickMode2 = 1;
		}

	}

	@Override
	public final void mouseReleased(MouseEvent mouseevent) {
		idleTime = 0;
		clickMode2 = 0;
		mouseWheelDown = false;
	}

	@Override
	public final void mouseClicked(MouseEvent mouseevent) {
	}

	@Override
	public final void mouseEntered(MouseEvent mouseevent) {
	}

	@Override
	public final void mouseExited(MouseEvent mouseevent) {
		idleTime = 0;
		mouseX = -1;
		mouseY = -1;
	}

	public final void mouseDragged(MouseEvent e) {
		int x = e.getX();
		int y = e.getY();
		if (mouseWheelDown) {
			y = mouseWheelX - e.getX();
			int k = mouseWheelY - e.getY();
			mouseWheelDragged(y, -k);
			mouseWheelX = e.getX();
			mouseWheelY = e.getY();
			return;
		}
		idleTime = 0;
		mouseX = x;
		mouseY = y;
	}
	void mouseWheelDragged(int param1, int param2) {

	}

	@Override
	public void mouseMoved(MouseEvent mouseevent) {
		int i = mouseevent.getX();
		int j = mouseevent.getY();
		idleTime = 0;
		mouseX = i;
		mouseY = j;
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {}

	@Override
	public void keyPressed(KeyEvent keyevent) {
		idleTime = 0;
		int i = keyevent.getKeyCode();
		int j = keyevent.getKeyChar();
		switch (keyevent.getKeyCode())
		{
			case KeyEvent.VK_SHIFT:
				shiftDown = true;
				break;
			case KeyEvent.VK_CONTROL:
				ctrlDown = true;
				break;
		}
		if (j < 30) {
			j = 0;
		}
		if (i == 37) {
			j = 1;
		}
		if (i == 39) {
			j = 2;
		}
		if (i == 38) {
			j = 3;
		}
		if (i == 40) {
			j = 4;
		}
		if (i == 17) {
			j = 5;
		}
		if (i == 8) {
			j = 8;
		}
		if (i == 127) {
			j = 8;
		}
		if (i == 9) {
			j = 9;
		}
		if (i == 10) {
			j = 10;
		}
		if (i >= 112 && i <= 123) {
			j = 1008 + i - 112;
		}
		if (i == 36) {
			j = 1000;
		}
		if (i == 35) {
			j = 1001;
		}
		if (i == 33) {
			j = 1002;
		}
		if (i == 34) {
			j = 1003;
		}
		if (j > 0 && j < 128) {
			keyArray[j] = 1;
		}
		if (j > 4) {
			charQueue[writeIndex] = j;
			writeIndex = writeIndex + 1 & 0x7f;
		}
	}

	@Override
	public final void keyReleased(KeyEvent keyevent) {
		idleTime = 0;
		int i = keyevent.getKeyCode();
		char c = keyevent.getKeyChar();
		switch (keyevent.getKeyCode())
		{
			case KeyEvent.VK_SHIFT:
				shiftDown = false;
				break;
			case KeyEvent.VK_CONTROL:
				ctrlDown = false;
				break;
		}
		if (c < '\036') {
			c = '\0';
		}
		if (i == 37) {
			c = '\001';
		}
		if (i == 39) {
			c = '\002';
		}
		if (i == 38) {
			c = '\003';
		}
		if (i == 40) {
			c = '\004';
		}
		if (i == 17) {
			c = '\005';
		}
		if (i == 8) {
			c = '\b';
		}
		if (i == 127) {
			c = '\b';
		}
		if (i == 9) {
			c = '\t';
		}
		if (i == 10) {
			c = '\n';
		}
		if (c > 0 && c < '\200') {
			keyArray[c] = 0;
		}
	}

	@Override
	public final void keyTyped(KeyEvent keyevent) {
	}

	final int readChar(int dummy) {
		while (dummy >= 0) {
			for (int j = 1; j > 0; j++) {
				;
			}
		}
		int k = -1;
		if (writeIndex != readIndex) {
			k = charQueue[readIndex];
			readIndex = readIndex + 1 & 0x7f;
		}
		return k;
	}

	@Override
	public final void focusGained(FocusEvent focusevent) {
		awtFocus = true;
		shouldClearScreen = true;
		raiseWelcomeScreen();
	}

	@Override
	public final void focusLost(FocusEvent focusevent) {
		awtFocus = false;
		for (int i = 0; i < 128; i++) {
			keyArray[i] = 0;
		}

	}

	@Override
	public final void windowActivated(WindowEvent windowevent) {
	}

	@Override
	public final void windowClosed(WindowEvent windowevent) {
	}

	@Override
	public final void windowClosing(WindowEvent windowevent) {
		onWindowClosing();
	}

	@Override
	public final void windowDeactivated(WindowEvent windowevent) {
	}

	@Override
	public final void windowDeiconified(WindowEvent windowevent) {
		resumeClient();
	}

	@Override
	public final void windowIconified(WindowEvent windowevent) {
		suspendClient();
	}

	@Override
	public final void windowOpened(WindowEvent windowevent) {
	}

	void startUp() {
	}

	void processGameLoop() {
	}

	void cleanUpForQuit() {
	}

	void processDrawing() {
	}

	void raiseWelcomeScreen() {
	}

	Component getGameComponent() {
		if (gameCanvas != null) {
			return gameCanvas;
		}
		if (Signlink.mainapp != null) {
			return Signlink.mainapp;
		}
		return null;
	}

	void recreatePresentationBuffers() {
		if (presentation != null) {
			presentation.recreateBuffers();
		}
	}

	public void startRunnable(Runnable runnable, int priority) {
		Thread thread = new Thread(runnable);
		thread.start();
		thread.setPriority(priority);
	}

	void publishPendingResize(int width, int height) {
		pendingResizePublisher.publish(width, height);
	}

	void drawLoadingText(int i, String s) {
		long deadline = System.currentTimeMillis() + 30_000L;
		while (graphics == null && System.currentTimeMillis() < deadline) {
			if (presentation != null) {
				graphics = presentation.beginFrame();
			} else if (getGameComponent() != null) {
				graphics = getGameComponent().getGraphics();
			}
			if (graphics == null) {
				try {
					Thread.sleep(20L);
				} catch (Exception _ex) {
				}
				continue;
			}
		}
		if (graphics == null) {
			return;
		}
		Font font = new Font("Helvetica", 1, 13);
		FontMetrics fontmetrics = getGameComponent().getFontMetrics(font);
		Font font1 = new Font("Helvetica", 0, 13);
		getGameComponent().getFontMetrics(font1);
		if (shouldClearScreen) {
			graphics.setColor(Color.black);
			graphics.fillRect(0, 0, myWidth, myHeight);
			shouldClearScreen = false;
		}
		Color color = new Color(140, 17, 17);
		int j = myHeight / 2 - 18;
		graphics.setColor(color);
		graphics.drawRect(myWidth / 2 - 152, j, 304, 34);
		graphics.fillRect(myWidth / 2 - 150, j + 2, i * 3, 30);
		graphics.setColor(Color.black);
		graphics.fillRect(myWidth / 2 - 150 + i * 3, j + 2, 300 - i * 3, 30);
		graphics.setFont(font);
		graphics.setColor(Color.white);
		graphics.drawString(s, (myWidth - fontmetrics.stringWidth(s)) / 2, j + 22);
		if (presentation != null) {
			presentation.endFrame();
		} else {
			graphics.dispose();
		}
		graphics = null;
	}

	RSApplet() {
		delayTime = 20;
		minDelay = 1;
		aLongArray7 = new long[10];
		shouldDebug = false;
		shouldClearScreen = true;
		awtFocus = true;
		keyArray = new int[128];
		charQueue = new int[128];
	}

	final ClientLifecycle lifecycle = new ClientLifecycle();
	private int delayTime;
	int minDelay;
	private final long[] aLongArray7;
	int fps;
	boolean shouldDebug;
	int myWidth;
	int myHeight;
	int dynamicGameAreaW = 512;
	int dynamicGameAreaH = 334;
	volatile boolean layoutDirty = true;
	final PendingResizePublisher pendingResizePublisher = new PendingResizePublisher();
	Graphics graphics;
	RSImageProducer fullGameScreen;
	ClientWindow clientWindow;
	GameCanvas gameCanvas;
	CanvasPresentation presentation;
	private boolean shouldClearScreen;
	boolean awtFocus;
	int idleTime;
	int clickMode2;
	public int mouseX;
	public int mouseY;
	private int clickMode1;
	private int clickX;
	private int clickY;
	private long clickTime;
	int clickMode3;
	int saveClickX;
	int saveClickY;
	long aLong29;
	final int[] keyArray;
	private final int[] charQueue;
	private int readIndex;
	private int writeIndex;
	public static int anInt34;
}
