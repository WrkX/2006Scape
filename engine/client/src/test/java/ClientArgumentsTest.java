import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClientArgumentsTest {

	@Test
	public void developmentModeRequiresDevLocalOrOfflineFlag() {
		assertTrue(ClientArguments.isDevelopmentMode(new String[] { "-dev" }));
		assertTrue(ClientArguments.isDevelopmentMode(new String[] { "-local" }));
		assertTrue(ClientArguments.isDevelopmentMode(new String[] { "-offline" }));
		assertFalse(ClientArguments.isDevelopmentMode(new String[] { "-s", "localhost" }));
	}

	@Test
	public void passwordArgumentRejectedWithoutDevelopmentMode() {
		String[] args = { "-u", "Player", "-password", "secret" };
		ClientArguments.Credentials credentials = ClientArguments.parseCredentials(args, false);

		assertEquals("Player", credentials.username);
		assertNull(credentials.password);
		assertEquals(CredentialSanitizer.REDACTED, args[3]);
	}

	@Test
	public void passwordArgumentAcceptedWithDevelopmentMode() {
		String[] args = { "-dev", "-u", "Player", "-p", "secret" };
		ClientArguments.Credentials credentials = ClientArguments.parseCredentials(args, true);

		assertEquals("Player", credentials.username);
		assertEquals("secret", credentials.password);
		assertEquals(CredentialSanitizer.REDACTED, args[4]);
	}

	@Test
	public void passwordFlagsAreRecognized() {
		assertTrue(ClientArguments.isPasswordFlag("-p"));
		assertTrue(ClientArguments.isPasswordFlag("-pass"));
		assertTrue(ClientArguments.isPasswordFlag("-password"));
	}
}
