import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PendingResizeRequestTest {

	@Test
	public void ofClampsBelowMinimumDimensions() {
		PendingResizeRequest request = PendingResizeRequest.of(640, 480);

		assertEquals(ClientPreferenceModel.MIN_WINDOW_WIDTH, request.width);
		assertEquals(ClientPreferenceModel.MIN_WINDOW_HEIGHT, request.height);
	}

	@Test
	public void ofClampsAboveMaximumDimensions() {
		PendingResizeRequest request = PendingResizeRequest.of(9000, 9000);

		assertEquals(ClientPreferenceModel.MAX_WINDOW_WIDTH, request.width);
		assertEquals(ClientPreferenceModel.MAX_WINDOW_HEIGHT, request.height);
	}

	@Test
	public void ofPreservesValidDimensions() {
		PendingResizeRequest request = PendingResizeRequest.of(1920, 1080);

		assertEquals(1920, request.width);
		assertEquals(1080, request.height);
		assertTrue(request.isValid());
	}

	@Test
	public void matchesDimensionsComparesWidthAndHeight() {
		PendingResizeRequest request = PendingResizeRequest.of(1024, 768);

		assertTrue(request.matchesDimensions(1024, 768));
		assertFalse(request.matchesDimensions(1024, 720));
		assertFalse(request.matchesDimensions(1280, 768));
	}
}
