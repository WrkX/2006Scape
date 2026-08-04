import java.io.InputStream;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Application metadata exposed through the packaged client manifest.
 */
final class ClientMetadata {

	static final String APPLICATION_NAME = "2006Scape Client";
	static final String VENDOR = "2006Scape";
	static final String DESCRIPTION = "2006Scape desktop game client";
	static final String FALLBACK_VERSION = "1.0";

	private static final String VERSION = readVersion();

	private ClientMetadata() {
	}

	static String getVersion() {
		return VERSION;
	}

	private static String readVersion() {
		String packageVersion = ClientMetadata.class.getPackage() != null
				? ClientMetadata.class.getPackage().getImplementationVersion()
				: null;
		if (packageVersion != null && !packageVersion.isEmpty()) {
			return packageVersion;
		}

		try (InputStream input = ClientMetadata.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
			if (input != null) {
				Manifest manifest = new Manifest(input);
				Attributes attributes = manifest.getMainAttributes();
				String implementationVersion = attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
				if (implementationVersion != null && !implementationVersion.isEmpty()) {
					return implementationVersion;
				}
			}
		} catch (Exception exception) {
			// Fall back to the packaged default below.
		}

		return FALLBACK_VERSION;
	}
}
