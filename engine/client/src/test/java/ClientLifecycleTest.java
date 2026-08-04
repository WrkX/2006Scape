import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientLifecycleTest {

	@Test
	public void startsRunning() {
		ClientLifecycle lifecycle = new ClientLifecycle();
		assertTrue(lifecycle.isRunning());
		assertFalse(lifecycle.isShutdownRequested());
	}

	@Test
	public void requestShutdownStopsGameLoop() {
		ClientLifecycle lifecycle = new ClientLifecycle();
		lifecycle.requestShutdown();
		assertFalse(lifecycle.isRunning());
		assertTrue(lifecycle.isShutdownRequested());
	}

	@Test
	public void gracefulShutdownCountsDownToExit() {
		ClientLifecycle lifecycle = new ClientLifecycle();
		lifecycle.beginGracefulShutdown(3);
		assertTrue(lifecycle.isRunning());
		assertFalse(lifecycle.tickShutdownCountdown());
		assertFalse(lifecycle.tickShutdownCountdown());
		assertTrue(lifecycle.tickShutdownCountdown());
	}

	@Test
	public void resumeClearsGracefulShutdown() {
		ClientLifecycle lifecycle = new ClientLifecycle();
		lifecycle.beginGracefulShutdown(10);
		lifecycle.startClient();
		assertTrue(lifecycle.isRunning());
		assertFalse(lifecycle.tickShutdownCountdown());
	}

	@Test
	public void markShutdownComplete() {
		ClientLifecycle lifecycle = new ClientLifecycle();
		lifecycle.markShutdownComplete();
		assertTrue(lifecycle.isShutdownComplete());
		assertFalse(lifecycle.isRunning());
	}

}
