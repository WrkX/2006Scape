import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RSAppletLifecycleTest {

	@Test
	public void requestShutdownStopsRunningLifecycle() {
		RSApplet client = new RSApplet();
		assertTrue(client.lifecycle.isRunning());
		client.requestShutdown();
		assertFalse(client.lifecycle.isRunning());
		assertTrue(client.lifecycle.isShutdownRequested());
	}

}
