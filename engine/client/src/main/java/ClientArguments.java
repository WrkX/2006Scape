final class ClientArguments {

	static final class Credentials {
		String username;
		String password;
	}

	private ClientArguments() {
	}

	static boolean isDevelopmentMode(String[] args) {
		for (String arg : args) {
			if ("-dev".equals(arg) || "-local".equals(arg) || "-offline".equals(arg)) {
				return true;
			}
		}
		return false;
	}

	static boolean isPasswordFlag(String flag) {
		return "-p".equals(flag) || "-pass".equals(flag) || "-password".equals(flag);
	}

	static Credentials parseCredentials(String[] args, boolean developmentMode) {
		Credentials credentials = new Credentials();
		for (int i = 0; i < args.length; i++) {
			if (!args[i].startsWith("-") || i + 1 >= args.length || args[i + 1].startsWith("-")) {
				continue;
			}
			switch (args[i]) {
				case "-u":
				case "-user":
				case "-username":
					credentials.username = args[++i];
					break;
				case "-p":
				case "-pass":
				case "-password":
					if (developmentMode) {
						credentials.password = args[++i];
					} else {
						System.err.println("Ignoring password command-line argument.");
						i++;
					}
					break;
				default:
					break;
			}
		}
		CredentialSanitizer.scrubPasswordValuesFromArgs(args);
		return credentials;
	}
}
