import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ClientCrashReportTest {

	private File previousHome;
	private File tempHome;
	private String previousRememberedUsername;

	@Before
	public void setUp() throws Exception {
		tempHome = Files.createTempDirectory("client-crash-report-test").toFile();
		previousHome = new File(System.getProperty("user.home"));
		System.setProperty("user.home", tempHome.getAbsolutePath());
		previousRememberedUsername = ClientPreferences.rememberedUsername;
		ClientPreferences.rememberedUsername = "SecretPlayer";
		ClientPreferences.sidePanelMode = SidePanelLayout.MODE_MODERN;
		ClientPreferences.uiScaleMode = UiScale.Mode.PERCENT_150.name();
		ClientLogger.shutdown();
		ClientLogger.initialize();
	}

	@After
	public void tearDown() {
		ClientPreferences.rememberedUsername = previousRememberedUsername;
		ClientLogger.shutdown();
		if (previousHome != null) {
			System.setProperty("user.home", previousHome.getAbsolutePath());
		}
	}

	@Test
	public void buildIncludesVersionDisplayAndInterfaceMode() {
		String report = ClientCrashReport.build("startup failed", new IllegalStateException("boom"));

		assertTrue(report.contains("clientVersion: " + ClientMetadata.getVersion()));
		assertTrue(report.contains("javaVersion:"));
		assertTrue(report.contains("osName:"));
		assertTrue(report.contains("displayCount:"));
		assertTrue(report.contains("interfaceMode: Modern"));
		assertTrue(report.contains("uiScaleMode: PERCENT_150"));
		assertTrue(report.contains("message: startup failed"));
		assertTrue(report.contains("IllegalStateException"));
	}

	@Test
	public void sanitizeMessageRedactsPasswordsAndRememberedUsername() {
		String sanitized = ClientCrashReport.sanitizeMessage(
				"password=topsecret user SecretPlayer -password leaked");

		assertFalse(sanitized.contains("topsecret"));
		assertFalse(sanitized.contains("leaked"));
		assertFalse(sanitized.contains("SecretPlayer"));
		assertTrue(sanitized.contains(CredentialSanitizer.REDACTED));
	}

	@Test
	public void writeCreatesCrashReportFile() throws Exception {
		File reportFile = ClientCrashReport.write("packet mismatch", new RuntimeException("bad packet"));

		assertNotNull(reportFile);
		assertTrue(reportFile.isFile());
		String contents = new String(Files.readAllBytes(reportFile.toPath()), StandardCharsets.UTF_8);
		assertTrue(contents.contains("packet mismatch"));
		assertEquals(new File(tempHome, "2006Scape/crash-reports").getAbsolutePath(),
				reportFile.getParentFile().getAbsolutePath());
	}
}
