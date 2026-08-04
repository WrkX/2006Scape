/**
 * Per-frame layout snapshot shared by rendering, input routing, and debug overlays.
 */
final class ViewportLayout {

	final ClientSize clientSize;
	final UiTransform uiTransform;
	final LoginLayout loginLayout;
	final NavbarLayout navbarLayout;
	final ChatLayout chatLayout;
	final SidePanelLayout sidePanelLayout;
	final TabLayout tabLayout;
	final MinimapLayout minimapLayout;
	final UiBounds worldViewport;
	final UiBounds loginPanelScreen;
	final UiBounds chatPanelScreen;
	final UiBounds chatFooterScreen;

	ViewportLayout(
			ClientSize clientSize,
			UiTransform uiTransform,
			LoginLayout loginLayout,
			NavbarLayout navbarLayout,
			ChatLayout chatLayout,
			SidePanelLayout sidePanelLayout,
			TabLayout tabLayout,
			MinimapLayout minimapLayout,
			UiBounds worldViewport,
			UiBounds loginPanelScreen,
			UiBounds chatPanelScreen,
			UiBounds chatFooterScreen) {
		this.clientSize = clientSize;
		this.uiTransform = uiTransform;
		this.loginLayout = loginLayout;
		this.navbarLayout = navbarLayout;
		this.chatLayout = chatLayout;
		this.sidePanelLayout = sidePanelLayout;
		this.tabLayout = tabLayout;
		this.minimapLayout = minimapLayout;
		this.worldViewport = worldViewport;
		this.loginPanelScreen = loginPanelScreen;
		this.chatPanelScreen = chatPanelScreen;
		this.chatFooterScreen = chatFooterScreen;
	}

	static ViewportLayout forSize(int windowWidth, int windowHeight, UiScale.Mode scaleMode) {
		return forSize(
				windowWidth,
				windowHeight,
				scaleMode,
				ChatLayout.logical(
						windowHeight,
						windowHeight,
						ClientPreferences.chatOverlay,
						ClientPreferences.chatHidden,
						ClientPreferences.chatHeightPreset),
				ClientPreferences.sidePanelMode);
	}

	static ViewportLayout forSize(
			int windowWidth,
			int windowHeight,
			UiScale.Mode scaleMode,
			ChatLayout chatLayout) {
		return forSize(windowWidth, windowHeight, scaleMode, chatLayout, ClientPreferences.sidePanelMode);
	}

	static ViewportLayout forSize(
			int windowWidth,
			int windowHeight,
			UiScale.Mode scaleMode,
			ChatLayout chatLayout,
			int sidePanelMode) {
		ClientSize clientSize = ClientSize.of(windowWidth, windowHeight);
		UiTransform uiTransform = UiTransform.forPresentation(clientSize, scaleMode);
		LoginLayout loginLayout = LoginLayout.logical();
		NavbarLayout navbarLayout = NavbarLayout.logical();
		SidePanelLayout sidePanelLayout = SidePanelLayout.forWindow(windowWidth, windowHeight, sidePanelMode);
		TabLayout tabLayout = TabLayout.forSidePanel(sidePanelLayout);
		MinimapLayout minimapLayout = MinimapLayout.forSidePanel(sidePanelLayout);
		UiBounds worldViewport = new UiBounds(4, 4, 512, 334);
		UiBounds loginPanelScreen = uiTransform.toScreen(loginLayout.panel);
		UiBounds chatPanelScreen = chatLayout.visible
				? chatLayout.panel
				: new UiBounds(0, 0, 0, 0);
		UiBounds chatFooterScreen = chatLayout.footer;
		return new ViewportLayout(
				clientSize,
				uiTransform,
				loginLayout,
				navbarLayout,
				chatLayout,
				sidePanelLayout,
				tabLayout,
				minimapLayout,
				worldViewport,
				loginPanelScreen,
				chatPanelScreen,
				chatFooterScreen);
	}

	static ViewportLayout forGame(Game game) {
		ClientSize clientSize = ClientSize.of(game.myWidth, game.myHeight);
		UiTransform uiTransform = UiTransform.forPresentation(clientSize, UiScale.currentMode());
		LoginLayout loginLayout = LoginLayout.logical();
		NavbarLayout navbarLayout = NavbarLayout.logical();
		ChatLayout chatLayout = ChatLayout.forGame(game);
		SidePanelLayout sidePanelLayout = SidePanelLayout.forWindow(
				game.myWidth,
				game.myHeight,
				ClientPreferences.sidePanelMode);
		TabLayout tabLayout = TabLayout.forSidePanel(sidePanelLayout);
		MinimapLayout minimapLayout = MinimapLayout.forSidePanel(sidePanelLayout);
		UiBounds worldViewport = new UiBounds(
				4,
				4,
				game.getGameAreaWidth(),
				game.getGameAreaHeight());
		UiBounds loginPanelScreen = uiTransform.toScreen(loginLayout.panel);
		UiBounds chatPanelScreen = chatLayout.visible
				? chatLayout.panel
				: new UiBounds(0, 0, 0, 0);
		UiBounds chatFooterScreen = chatLayout.footer;
		return new ViewportLayout(
				clientSize,
				uiTransform,
				loginLayout,
				navbarLayout,
				chatLayout,
				sidePanelLayout,
				tabLayout,
				minimapLayout,
				worldViewport,
				loginPanelScreen,
				chatPanelScreen,
				chatFooterScreen);
	}
}
