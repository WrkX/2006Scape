import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CredentialSanitizerTest {

	@Test
	public void cliPasswordFlagsAreRedactedInLogs() {
		String sanitized = CredentialSanitizer.sanitizeForLog("argv: -password leaked-secret -u Player");
		assertFalse(sanitized.contains("leaked-secret"));
		assertTrue(sanitized.contains(CredentialSanitizer.REDACTED));
	}

	@Test
	public void propertyPasswordValuesAreRedactedInLogs() {
		String sanitized = CredentialSanitizer.sanitizeForLog("password=topsecret rememberedPassword=also-secret");
		assertFalse(sanitized.contains("topsecret"));
		assertFalse(sanitized.contains("also-secret"));
		assertTrue(sanitized.contains("password=" + CredentialSanitizer.REDACTED));
		assertTrue(sanitized.contains("rememberedPassword=" + CredentialSanitizer.REDACTED));
	}

	@Test
	public void scrubPasswordValuesFromArgsRedactsFollowingValue() {
		String[] args = { "-dev", "-password", "secret-value", "-world", "2" };
		CredentialSanitizer.scrubPasswordValuesFromArgs(args);
		assertEquals(CredentialSanitizer.REDACTED, args[2]);
		assertEquals("2", args[4]);
	}
}
