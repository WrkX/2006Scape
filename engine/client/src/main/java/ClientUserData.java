import java.io.File;

/**
 * Predictable on-disk locations for client logs, crash reports, and preferences.
 */
final class ClientUserData {

	static final String TEXT_ENCODING = "UTF-8";

	private ClientUserData() {
	}

	static File getDataDirectory() {
		return new File(System.getProperty("user.home"), ClientSettings.SERVER_NAME);
	}

	static File getLogsDirectory() {
		return new File(getDataDirectory(), "logs");
	}

	static File getLogFile() {
		return new File(getLogsDirectory(), "client.log");
	}

	static File getCrashReportsDirectory() {
		return new File(getDataDirectory(), "crash-reports");
	}

	static boolean ensureDirectory(File directory) {
		return directory.exists() || directory.mkdirs();
	}
}
