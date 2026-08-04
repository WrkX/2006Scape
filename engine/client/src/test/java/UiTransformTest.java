import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class UiTransformTest {

	@After
	public void resetScaleMode() {
		UiScale.setMode(UiScale.Mode.PERCENT_100);
	}

	@Test
	public void referenceResolutionAt100PercentHasNoOffset() {
		UiTransform transform = transformFor(765, 503, UiScale.Mode.PERCENT_100);

		assertEquals(0, transform.offsetX);
		assertEquals(0, transform.offsetY);
		assertEquals(1.0D, transform.scale, 0.001D);
		assertEquals(202, transform.toScreenX(202));
		assertEquals(171, transform.toScreenY(171));
	}

	@Test
	public void largerWindowCentersLogicalCanvasAt100Percent() {
		UiTransform transform = transformFor(1024, 768, UiScale.Mode.PERCENT_100);

		assertEquals(129, transform.offsetX);
		assertEquals(132, transform.offsetY);
		assertEquals(331, transform.toScreenX(202));
		assertEquals(303, transform.toScreenY(171));
	}

	@Test
	public void scale150ExpandsPresentation() {
		UiScale.setMode(UiScale.Mode.PERCENT_150);
		UiTransform transform = transformFor(1920, 1080, UiScale.Mode.PERCENT_150);

		assertEquals(1.5D, transform.scale, 0.001D);
		assertEquals(689, transform.toScreenX(202));
		assertEquals(419, transform.toScreenY(171));
	}

	@Test
	public void scale200DoublesLogicalCoordinates() {
		UiScale.setMode(UiScale.Mode.PERCENT_200);
		UiTransform transform = transformFor(2560, 1440, UiScale.Mode.PERCENT_200);

		assertEquals(2.0D, transform.scale, 0.001D);
		assertEquals(919, transform.toScreenX(202));
		assertEquals(559, transform.toScreenY(171));
	}

	@Test
	public void toLogicalReversesToScreen() {
		UiTransform transform = transformFor(1280, 720, UiScale.Mode.PERCENT_100);

		int screenX = transform.toScreenX(302);
		int screenY = transform.toScreenY(321);
		assertEquals(302, transform.toLogicalX(screenX));
		assertEquals(321, transform.toLogicalY(screenY));
	}

	@Test
	public void toScreenBoundsScalesWidthAndHeight() {
		UiScale.setMode(UiScale.Mode.PERCENT_150);
		UiTransform transform = transformFor(1920, 1080, UiScale.Mode.PERCENT_150);
		UiBounds screen = transform.toScreen(new UiBounds(202, 171, 360, 200));

		assertEquals(689, screen.x);
		assertEquals(419, screen.y);
		assertEquals(540, screen.width);
		assertEquals(300, screen.height);
	}

	@Test
	public void commonResolutionsKeepLoginPanelInsideWindowAt100Percent() {
		assertLoginPanelInsideWindow(765, 503);
		assertLoginPanelInsideWindow(1024, 768);
		assertLoginPanelInsideWindow(1280, 720);
		assertLoginPanelInsideWindow(1920, 1080);
		assertLoginPanelInsideWindow(2560, 1440);
	}

	private static void assertLoginPanelInsideWindow(int width, int height) {
		ViewportLayout layout = ViewportLayout.forSize(width, height, UiScale.Mode.PERCENT_100);
		UiBounds panel = layout.loginPanelScreen;

		assertTrue(panel.x >= 0);
		assertTrue(panel.y >= 0);
		assertTrue(panel.right() <= width);
		assertTrue(panel.bottom() <= height);
	}

	private static UiTransform transformFor(int width, int height, UiScale.Mode mode) {
		ClientSize size = ClientSize.of(width, height);
		return UiTransform.forPresentation(size, mode);
	}
}
