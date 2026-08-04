import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ClientLoggerTest {

	private File previousHome;
	private File tempHome;

	@Before
	public void setUp() throws Exception {
		tempHome = Files.createTempDirectory("client-logger-test").toFile();
		previousHome = new File(System.getProperty("user.home"));
		System.setProperty("user.home", tempHome.getAbsolutePath());
		ClientLogger.shutdown();
	}

	@After
	public void tearDown() {
		ClientLogger.shutdown();
		if (previousHome != null) {
			System.setProperty("user.home", previousHome.getAbsolutePath());
		}
	}

	@Test
	public void initializeCreatesPredictableLogFile() throws Exception {
		ClientLogger.initialize();

		File logFile = ClientLogger.getLogFile();
		assertNotNull(logFile);
		assertEquals(new File(tempHome, "2006Scape/logs/client.log"), logFile);
		assertTrue(logFile.isFile());

		ClientLogger.info("structured log entry");
		List<String> lines = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8);
		assertFalse(lines.isEmpty());
		assertTrue(lines.get(lines.size() - 1).contains("[INFO] structured log entry"));
	}

	@Test
	public void errorLogsSanitizedPasswords() throws Exception {
		ClientLogger.initialize();
		ClientLogger.error("argv: -password leaked-secret");

		List<String> lines = Files.readAllLines(ClientLogger.getLogFile().toPath(), StandardCharsets.UTF_8);
		String lastLine = lines.get(lines.size() - 1);
		assertFalse(lastLine.contains("leaked-secret"));
		assertTrue(lastLine.contains(CredentialSanitizer.REDACTED));
	}
}
