import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CanvasPresentationTest {

	@Test
	public void usesDoubleBufferingByDefault() {
		assertEquals(2, CanvasPresentation.BUFFER_COUNT);
	}

}
