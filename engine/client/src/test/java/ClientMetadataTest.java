import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClientMetadataTest {

	@Test
	public void exposesApplicationMetadata() {
		assertEquals("2006Scape Client", ClientMetadata.APPLICATION_NAME);
		assertEquals("2006Scape", ClientMetadata.VENDOR);
		assertFalse(ClientMetadata.DESCRIPTION.isEmpty());
	}

	@Test
	public void getVersionReturnsNonEmptyValue() {
		String version = ClientMetadata.getVersion();
		assertFalse(version.isEmpty());
		assertTrue(version.matches("\\d+(\\.\\d+)*"));
	}
}
