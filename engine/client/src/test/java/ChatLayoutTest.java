import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class ChatLayoutTest {

	@After
	public void resetPreferences() {
		ClientPreferences.resetToDefaults();
	}

	@Test
	public void referenceResolutionMatchesHistoricalChatBounds() {
		ChatLayout layout = ChatLayout.logical(503, 503, true, false, 0);

		assertEquals(17, layout.panel.x);
		assertEquals(367, layout.panel.y);
		assertEquals(479, layout.panel.width);
		assertEquals(96, layout.panel.height);
		assertEquals(463, layout.footer.y);
		assertEquals(496, layout.footer.width);
	}

	@Test
	public void footerButtonsHitTestAtReferenceResolution() {
		ChatLayout layout = ChatLayout.logical(503, 503, true, false, 0);

		assertEquals(ChatLayout.FooterButton.PUBLIC_CHAT, ChatLayout.hitFooter(layout, 20, 470));
		assertEquals(ChatLayout.FooterButton.PRIVATE_CHAT, ChatLayout.hitFooter(layout, 140, 470));
		assertEquals(ChatLayout.FooterButton.TRADE_CHAT, ChatLayout.hitFooter(layout, 280, 470));
		assertEquals(ChatLayout.FooterButton.REPORT_ABUSE, ChatLayout.hitFooter(layout, 420, 470));
		assertEquals(ChatLayout.FooterButton.COLLAPSE, ChatLayout.hitFooter(layout, 10, 470));
	}

	@Test
	public void hiddenOverlayChatIsNotVisible() {
		ChatLayout layout = ChatLayout.logical(503, 503, true, true, 0);

		assertFalse(layout.visible);
		assertFalse(layout.containsPanel(100, 400));
		assertTrue(layout.footer.contains(100, 470));
	}

	@Test
	public void tallPresetIncreasesPanelHeight() {
		ChatLayout normal = ChatLayout.logical(1280, 1280, true, false, 1);
		ChatLayout tall = ChatLayout.logical(1280, 1280, true, false, 2);

		assertTrue(tall.panel.height > normal.panel.height);
		assertEquals(normal.panel.y - ChatLayout.MAX_EXTRA_HEIGHT, tall.panel.y);
	}

	@After
	public void resetScaleMode() {
		UiScale.setMode(UiScale.Mode.PERCENT_100);
	}

	@Test
	public void footerHitTestUsesWindowCoordinates() {
		ViewportLayout layout = ViewportLayout.forSize(1280, 720, UiScale.Mode.PERCENT_100);
		InputRouter router = new InputRouter();
		router.update(layout, 20, layout.chatLayout.footer.y + 7);

		assertEquals(ChatLayout.FooterButton.PUBLIC_CHAT, router.hitChatFooter(layout));
	}
}
