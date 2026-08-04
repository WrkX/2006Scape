final class CredentialSanitizer {

	static final String REDACTED = "[redacted]";

	private static final String CLI_PASSWORD_PATTERN = "(?i)(-p|-pass|-password)(=|\\s+)(\\S+)";
	private static final String PROPERTY_PASSWORD_PATTERN = "(?i)(password|rememberedPassword)=([^\\s,&]+)";

	private CredentialSanitizer() {
	}

	static String sanitizeForLog(String message) {
		if (message == null || message.isEmpty()) {
			return message;
		}
		String sanitized = message.replaceAll(CLI_PASSWORD_PATTERN, "$1$2" + REDACTED);
		return sanitized.replaceAll(PROPERTY_PASSWORD_PATTERN, "$1=" + REDACTED);
	}

	static void scrubPasswordValuesFromArgs(String[] args) {
		if (args == null) {
			return;
		}
		for (int i = 0; i < args.length; i++) {
			if (ClientArguments.isPasswordFlag(args[i]) && i + 1 < args.length) {
				args[i + 1] = REDACTED;
			}
		}
	}
}
