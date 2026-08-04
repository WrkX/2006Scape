/**
 * Logical chat layout at the reference 765x503 presentation.
 */
final class ChatLayout {

	static final int CHAT_X = 17;
	static final int STANDARD_WIDTH = 479;
	static final int FOOTER_WIDTH = 496;
	static final int FOOTER_HEIGHT = 40;
	static final int BASE_HEIGHT = 96;
	static final int INPUT_HEIGHT = 19;
	static final int SCROLLBAR_WIDTH = 16;
	static final int MESSAGE_RIGHT_MARGIN = 53;
	static final int MAX_EXTRA_HEIGHT = 48;

	final UiBounds panel;
	final UiBounds footer;
	final UiBounds collapseToggle;
	final UiBounds publicChatButton;
	final UiBounds privateChatButton;
	final UiBounds tradeChatButton;
	final UiBounds reportAbuseButton;
	final UiBounds scrollbar;
	final UiBounds messages;
	final boolean visible;
	final boolean overlayMode;

	private ChatLayout(
			UiBounds panel,
			UiBounds footer,
			UiBounds collapseToggle,
			UiBounds publicChatButton,
			UiBounds privateChatButton,
			UiBounds tradeChatButton,
			UiBounds reportAbuseButton,
			UiBounds scrollbar,
			UiBounds messages,
			boolean visible,
			boolean overlayMode) {
		this.panel = panel;
		this.footer = footer;
		this.collapseToggle = collapseToggle;
		this.publicChatButton = publicChatButton;
		this.privateChatButton = privateChatButton;
		this.tradeChatButton = tradeChatButton;
		this.reportAbuseButton = reportAbuseButton;
		this.scrollbar = scrollbar;
		this.messages = messages;
		this.visible = visible;
		this.overlayMode = overlayMode;
	}

	static ChatLayout logical(int logicalHeight, int windowHeight, boolean chatOverlay, boolean chatHidden,
			int chatHeightPreset) {
		int extraHeight = extraHeightFor(windowHeight, chatOverlay, chatHeightPreset);
		int panelHeight = BASE_HEIGHT + extraHeight;
		int footerY = logicalHeight - FOOTER_HEIGHT;
		int panelY = footerY - panelHeight;
		boolean visible = !chatOverlay || !chatHidden;

		UiBounds panel = new UiBounds(CHAT_X, panelY, STANDARD_WIDTH, panelHeight);
		UiBounds footer = new UiBounds(0, footerY, FOOTER_WIDTH, FOOTER_HEIGHT);
		UiBounds collapseToggle = new UiBounds(0, footerY, 19, FOOTER_HEIGHT);
		UiBounds publicChatButton = new UiBounds(19, footerY, 116, FOOTER_HEIGHT);
		UiBounds privateChatButton = new UiBounds(135, footerY, 138, FOOTER_HEIGHT);
		UiBounds tradeChatButton = new UiBounds(273, footerY, 139, FOOTER_HEIGHT);
		UiBounds reportAbuseButton = new UiBounds(412, footerY, 84, FOOTER_HEIGHT);
		int scrollbarX = CHAT_X + STANDARD_WIDTH - SCROLLBAR_WIDTH;
		UiBounds scrollbar = new UiBounds(scrollbarX, panelY, SCROLLBAR_WIDTH, panelHeight - INPUT_HEIGHT);
		UiBounds messages = new UiBounds(
				CHAT_X,
				panelY,
				STANDARD_WIDTH - MESSAGE_RIGHT_MARGIN,
				panelHeight - INPUT_HEIGHT);

		return new ChatLayout(
				panel,
				footer,
				collapseToggle,
				publicChatButton,
				privateChatButton,
				tradeChatButton,
				reportAbuseButton,
				scrollbar,
				messages,
				visible,
				chatOverlay);
	}

	static ChatLayout forGame(Game game) {
		return logical(
				game.myHeight,
				game.myHeight,
				ClientPreferences.chatOverlay,
				ClientPreferences.chatHidden,
				ClientPreferences.chatHeightPreset);
	}

	static int extraHeightFor(int windowHeight, boolean chatOverlay, int chatHeightPreset) {
		int availableResizeHeight = Math.max(0, windowHeight - ClientPreferences.LOGICAL_UI_HEIGHT);
		int requestedExtraHeight;
		if (chatHeightPreset == 1) {
			requestedExtraHeight = 0;
		} else if (chatHeightPreset == 2) {
			requestedExtraHeight = MAX_EXTRA_HEIGHT;
		} else {
			requestedExtraHeight = Math.min(MAX_EXTRA_HEIGHT, availableResizeHeight / 4);
		}
		if (chatOverlay) {
			return requestedExtraHeight;
		}
		return Math.min(requestedExtraHeight, availableResizeHeight);
	}

	enum FooterButton {
		NONE,
		COLLAPSE,
		PUBLIC_CHAT,
		PRIVATE_CHAT,
		TRADE_CHAT,
		REPORT_ABUSE
	}

	static FooterButton hitFooter(ChatLayout layout, int logicalX, int logicalY) {
		if (!layout.footer.contains(logicalX, logicalY)) {
			return FooterButton.NONE;
		}
		if (layout.overlayMode && layout.collapseToggle.contains(logicalX, logicalY)) {
			return FooterButton.COLLAPSE;
		}
		if (layout.publicChatButton.contains(logicalX, logicalY)) {
			return FooterButton.PUBLIC_CHAT;
		}
		if (layout.privateChatButton.contains(logicalX, logicalY)) {
			return FooterButton.PRIVATE_CHAT;
		}
		if (layout.tradeChatButton.contains(logicalX, logicalY)) {
			return FooterButton.TRADE_CHAT;
		}
		if (layout.reportAbuseButton.contains(logicalX, logicalY)) {
			return FooterButton.REPORT_ABUSE;
		}
		return FooterButton.NONE;
	}

	boolean containsPanelOrFooter(int logicalX, int logicalY) {
		if (logicalX < 0 || logicalY < 0) {
			return false;
		}
		if (footer.contains(logicalX, logicalY)) {
			return true;
		}
		return visible && panel.contains(logicalX, logicalY);
	}

	boolean containsPanel(int logicalX, int logicalY) {
		return visible && panel.contains(logicalX, logicalY);
	}

	boolean containsMessages(int logicalX, int logicalY) {
		return visible && messages.contains(logicalX, logicalY);
	}

	boolean containsScrollbar(int logicalX, int logicalY) {
		return visible && scrollbar.contains(logicalX, logicalY);
	}

	int panelLocalX(int logicalX) {
		return logicalX - panel.x;
	}

	int panelLocalY(int logicalY) {
		return logicalY - panel.y;
	}

	UiBounds[] debugRegions() {
		return new UiBounds[] {
				panel,
				footer,
				collapseToggle,
				publicChatButton,
				privateChatButton,
				tradeChatButton,
				reportAbuseButton,
				scrollbar,
				messages
		};
	}
}
