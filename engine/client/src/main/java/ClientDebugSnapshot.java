/**
 * Immutable snapshot of client layout and input state for the developer overlay.
 */
final class ClientDebugSnapshot {

	final int windowWidth;
	final int windowHeight;
	final int logicalUiWidth;
	final int logicalUiHeight;
	final double uiScale;
	final int mouseScreenX;
	final int mouseScreenY;
	final int mouseLogicalX;
	final int mouseLogicalY;
	final String activeScreen;
	final String panelBounds;
	final int fps;
	final int memoryMb;
	final int playerX;
	final int playerY;
	final int openInterfaceId;
	final int zoom;

	ClientDebugSnapshot(
			int windowWidth,
			int windowHeight,
			int logicalUiWidth,
			int logicalUiHeight,
			double uiScale,
			int mouseScreenX,
			int mouseScreenY,
			int mouseLogicalX,
			int mouseLogicalY,
			String activeScreen,
			String panelBounds,
			int fps,
			int memoryMb,
			int playerX,
			int playerY,
			int openInterfaceId,
			int zoom) {
		this.windowWidth = windowWidth;
		this.windowHeight = windowHeight;
		this.logicalUiWidth = logicalUiWidth;
		this.logicalUiHeight = logicalUiHeight;
		this.uiScale = uiScale;
		this.mouseScreenX = mouseScreenX;
		this.mouseScreenY = mouseScreenY;
		this.mouseLogicalX = mouseLogicalX;
		this.mouseLogicalY = mouseLogicalY;
		this.activeScreen = activeScreen;
		this.panelBounds = panelBounds;
		this.fps = fps;
		this.memoryMb = memoryMb;
		this.playerX = playerX;
		this.playerY = playerY;
		this.openInterfaceId = openInterfaceId;
		this.zoom = zoom;
	}

	static ClientDebugSnapshot fromGame(Game game) {
		Runtime runtime = Runtime.getRuntime();
		int memoryMb = (int) ((runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L);
		ViewportLayout layout = game.getViewportLayout();

		String activeScreen;
		if (!game.loggedIn) {
			activeScreen = "login:" + game.loginScreenState;
		} else {
			activeScreen = "game:tab=" + game.tabID;
		}

		String panelBounds = "world=" + layout.worldViewport.width + "x" + layout.worldViewport.height
				+ "@" + layout.worldViewport.x + "," + layout.worldViewport.y
				+ " login=" + layout.loginPanelScreen.toString()
				+ " navbar=" + layout.navbarLayout.bar.toString()
				+ " chat=" + layout.chatPanelScreen.toString()
				+ " footer=" + layout.chatFooterScreen.toString()
				+ " tab=" + layout.sidePanelLayout.tabContent.toString()
				+ " minimap=" + layout.minimapLayout.frame.toString();

		int playerX = 0;
		int playerY = 0;
		if (Game.myPlayer != null) {
			playerX = Game.myPlayer.smallX[0] + game.baseX;
			playerY = Game.myPlayer.smallY[0] + game.baseY;
		}

		return new ClientDebugSnapshot(
				game.myWidth,
				game.myHeight,
				layout.clientSize.logicalWidth,
				layout.clientSize.logicalHeight,
				layout.uiTransform.scale,
				game.inputRouter.screenX,
				game.inputRouter.screenY,
				game.inputRouter.logicalX,
				game.inputRouter.logicalY,
				activeScreen,
				panelBounds,
				game.fps,
				memoryMb,
				playerX,
				playerY,
				game.openInterfaceID,
				Game.zoom);
	}
}
