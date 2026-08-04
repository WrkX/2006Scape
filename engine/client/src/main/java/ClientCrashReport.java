import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Crash diagnostics with credential and username redaction.
 */
final class ClientCrashReport {

	private static final DateTimeFormatter FILE_TIMESTAMP =
			DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT);

	private ClientCrashReport() {
	}

	static String sanitizeMessage(String message) {
		if (message == null || message.isEmpty()) {
			return message;
		}

		String sanitized = CredentialSanitizer.sanitizeForLog(message);
		String rememberedUsername = ClientPreferences.rememberedUsername;
		if (rememberedUsername != null && !rememberedUsername.isEmpty()) {
			sanitized = sanitized.replace(rememberedUsername, CredentialSanitizer.REDACTED);
		}
		return sanitized;
	}

	static String sanitizeStackTrace(Throwable throwable) {
		if (throwable == null) {
			return "";
		}

		StringWriter stringWriter = new StringWriter();
		throwable.printStackTrace(new PrintWriter(stringWriter));
		return sanitizeMessage(stringWriter.toString());
	}

	static String build(String message, Throwable throwable) {
		StringBuilder report = new StringBuilder();
		report.append("2006Scape Client Crash Report").append(System.lineSeparator());
		report.append("timestamp: ").append(LocalDateTime.now()).append(System.lineSeparator());
		report.append("clientVersion: ").append(ClientMetadata.getVersion()).append(System.lineSeparator());
		report.append("protocolVersion: ").append(Signlink.clientversion).append(System.lineSeparator());
		report.append("javaVersion: ").append(System.getProperty("java.version", "unknown")).append(System.lineSeparator());
		report.append("javaVendor: ").append(System.getProperty("java.vendor", "unknown")).append(System.lineSeparator());
		report.append("osName: ").append(System.getProperty("os.name", "unknown")).append(System.lineSeparator());
		report.append("osVersion: ").append(System.getProperty("os.version", "unknown")).append(System.lineSeparator());
		report.append("osArch: ").append(System.getProperty("os.arch", "unknown")).append(System.lineSeparator());
		appendDisplayConfiguration(report);
		report.append("interfaceMode: ").append(describeInterfaceMode()).append(System.lineSeparator());
		report.append("uiScaleMode: ").append(ClientPreferences.uiScaleMode).append(System.lineSeparator());
		report.append("resizable: ").append(ClientSettings.RESIZABLE).append(System.lineSeparator());
		report.append("message: ").append(sanitizeMessage(message)).append(System.lineSeparator());
		if (throwable != null) {
			report.append(System.lineSeparator());
			report.append("stackTrace:").append(System.lineSeparator());
			report.append(sanitizeStackTrace(throwable));
		}
		return report.toString();
	}

	static File write(String message, Throwable throwable) {
		File directory = ClientUserData.getCrashReportsDirectory();
		if (!ClientUserData.ensureDirectory(directory)) {
			ClientLogger.error("Failed to create crash report directory: " + directory.getAbsolutePath());
			return null;
		}

		File reportFile = new File(directory, "crash-" + FILE_TIMESTAMP.format(LocalDateTime.now()) + ".txt");
		String report = build(message, throwable);
		try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(reportFile),
				StandardCharsets.UTF_8))) {
			writer.write(report);
		} catch (IOException exception) {
			ClientLogger.error("Failed to write crash report: " + reportFile.getAbsolutePath(), exception);
			return null;
		}
		return reportFile;
	}

	private static void appendDisplayConfiguration(StringBuilder report) {
		report.append("windowWidth: ").append(ClientPreferences.windowWidth).append(System.lineSeparator());
		report.append("windowHeight: ").append(ClientPreferences.windowHeight).append(System.lineSeparator());
		report.append("windowMaximized: ").append(ClientPreferences.windowMaximized).append(System.lineSeparator());
		report.append("windowFullscreen: ").append(ClientPreferences.windowFullscreen).append(System.lineSeparator());

		GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] devices = environment.getScreenDevices();
		report.append("displayCount: ").append(devices.length).append(System.lineSeparator());
		for (int i = 0; i < devices.length; i++) {
			GraphicsConfiguration configuration = devices[i].getDefaultConfiguration();
			Rectangle bounds = configuration.getBounds();
			report.append("display").append(i).append(": ")
					.append(bounds.width).append('x').append(bounds.height)
					.append(" @ ").append(bounds.x).append(',').append(bounds.y)
					.append(System.lineSeparator());
		}
	}

	private static String describeInterfaceMode() {
		switch (ClientPreferences.sidePanelMode) {
			case SidePanelLayout.MODE_OVERLAY:
				return "Overlay";
			case SidePanelLayout.MODE_MODERN:
				return "Modern";
			default:
				return "Classic";
		}
	}
}
