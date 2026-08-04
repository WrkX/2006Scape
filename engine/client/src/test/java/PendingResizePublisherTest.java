import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class PendingResizePublisherTest {

	@Test
	public void pollReturnsNullWhenNothingPublished() {
		PendingResizePublisher publisher = new PendingResizePublisher();

		assertNull(publisher.poll());
	}

	@Test
	public void pollClearsPendingRequest() {
		PendingResizePublisher publisher = new PendingResizePublisher();
		publisher.publish(1280, 720);

		PendingResizeRequest first = publisher.poll();
		assertNotNull(first);
		assertEquals(1280, first.width);
		assertEquals(720, first.height);
		assertNull(publisher.poll());
	}

	@Test
	public void publishReplacesEarlierPendingRequest() {
		PendingResizePublisher publisher = new PendingResizePublisher();
		publisher.publish(1024, 768);
		publisher.publish(1920, 1080);

		PendingResizeRequest request = publisher.poll();
		assertNotNull(request);
		assertEquals(1920, request.width);
		assertEquals(1080, request.height);
		assertNull(publisher.poll());
	}

	@Test
	public void publishIgnoresInvalidDimensions() {
		PendingResizePublisher publisher = new PendingResizePublisher();
		publisher.publish(100, 100);

		assertNull(publisher.poll());
	}
}
