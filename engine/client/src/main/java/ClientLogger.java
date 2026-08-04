import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Structured client logging to a predictable user-data location.
 */
final class ClientLogger {

	private static final DateTimeFormatter TIMESTAMP_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);

	private static final Object LOCK = new Object();
	private static PrintWriter writer;
	private static File logFile;

	private ClientLogger() {
	}

	static void initialize() {
		synchronized (LOCK) {
			if (writer != null) {
				return;
			}

			logFile = ClientUserData.getLogFile();
			if (!ClientUserData.ensureDirectory(logFile.getParentFile())) {
				System.err.println("Failed to create client log directory: " + logFile.getParentFile());
				return;
			}

			try {
				BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(
						new FileOutputStream(logFile, true),
						StandardCharsets.UTF_8));
				writer = new PrintWriter(bufferedWriter, true);
				info("Client logger initialized (version=" + ClientMetadata.getVersion() + ")");
			} catch (IOException exception) {
				System.err.println("Failed to open client log file: " + logFile.getAbsolutePath());
				System.err.println(CredentialSanitizer.sanitizeForLog(exception.getMessage()));
			}
		}
	}

	static void info(String message) {
		log("INFO", message, null);
	}

	static void warn(String message) {
		log("WARN", message, null);
	}

	static void warn(String message, Throwable throwable) {
		log("WARN", message, throwable);
	}

	static void error(String message) {
		log("ERROR", message, null);
	}

	static void error(String message, Throwable throwable) {
		log("ERROR", message, throwable);
	}

	static File getLogFile() {
		return logFile;
	}

	static void shutdown() {
		synchronized (LOCK) {
			if (writer != null) {
				writer.flush();
				writer.close();
				writer = null;
			}
		}
	}

	private static void log(String level, String message, Throwable throwable) {
		String sanitizedMessage = ClientCrashReport.sanitizeMessage(message);
		String line = TIMESTAMP_FORMAT.format(LocalDateTime.now())
				+ " [" + level + "] "
				+ sanitizedMessage;

		synchronized (LOCK) {
			if (writer != null) {
				writer.println(line);
				if (throwable != null) {
					throwable.printStackTrace(writer);
				}
				writer.flush();
				return;
			}
		}

		System.err.println(line);
		if (throwable != null) {
			throwable.printStackTrace(System.err);
		}
	}
}
