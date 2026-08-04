import java.net.UnknownHostException;

public final class Main {

	/*

	DEAR DEVELOPER!

	If you want to run the client locally, the easiest way to do that is run the class "Client.java" instead!
	If you REALLY want to use this class, add program arguments "-s localhost".
	 */

	public static void main(String[] args) {
		try {
			ClientLogger.initialize();
			// Account storage policy: username may be remembered in client.properties when
			// the player opts in; passwords are never stored locally, accepted from CLI in
			// production, or written to logs or crash reports. Future credential convenience
			// should use OS keychains or server-issued tokens, not reversible local encryption.
			ClientPreferences.load();
			UiScale.applyFromPreferences();
			ClientPreferences.applyAccountSettings();

			boolean developmentMode = ClientArguments.isDevelopmentMode(args);

			// Process client arguments to connect to
			for (int i = 0; i < args.length; i++) {
				switch(args[i]) {
					case "-dev"	:
					case "-local":
					case "-offline":
						ClientSettings.SERVER_IP = "localhost";
						ClientSettings.CHECK_CRC = false;
						break;
					case "-no-crc":
					case "-no-cache-crc":
						ClientSettings.CHECK_CRC = false;
						break;
					case "-qol":
					case "-fixes":
						ClientSettings.CUSTOM_SETTINGS_TAB = true;
						ClientSettings.BILINEAR_MINIMAP_FILTERING = true;
						ClientSettings.FIX_TRANSPARENCY_OVERFLOW = true;
						ClientSettings.FULL_512PX_VIEWPORT = true;
						ClientSettings.CONTROL_KEY_ZOOMING = true;
						break;
					case "-no-nav":
					case "-disable-nav":
						ClientSettings.SHOW_NAVBAR = false;
						break;
					case"-no-snow":
					case"-hide-snow":
					case"-disable-snow":
						ClientSettings.SNOW_FLOOR_ENABLED = false;
						ClientSettings.SNOW_FLOOR_FORCE_ENABLED = false;
						ClientSettings.SNOW_OVERLAY_FORCE_ENABLED = false;
						ClientSettings.SNOW_OVERLAY_ENABLED = false;
						break;
					case"-no-roofs":
					case"-hide-roofs":
					case"-disable-roofs":
						ClientSettings.HIDE_ROOFS = true;
						break;
					case"-show-zoom":
						ClientSettings.SHOW_ZOOM_LEVEL_MESSAGES = true;
						break;
					case"-screenshots":
					case"-enable-screenshots":
						ClientSettings.SCREENSHOTS_ENABLED = true;
						break;
					case"-auto-screenshots":
					case"-enable-auto-screenshots":
						ClientSettings.AUTOMATIC_SCREENSHOTS_ENABLED = true;
						break;
					case"-no-resize":
					case"-fixed":
						ClientSettings.RESIZABLE = false;
						break;
				}
				if (args[i].startsWith("-") && (i + 1) < args.length  && !args[i + 1].startsWith("-")) {
					switch(args[i]) {
						case "-s":
						case "-server":
						case "-ip":
							ClientSettings.SERVER_IP = args[++i];
							break;
					}
				}
			}

			Game game = new Game();

			ClientArguments.Credentials credentials = ClientArguments.parseCredentials(args, developmentMode);
			if (credentials.username != null) {
				game.myUsername = credentials.username;
			}
			if (credentials.password != null) {
				game.myPassword = credentials.password;
			}

			for (int i = 0; i < args.length; i++) {
				if (args[i].startsWith("-") && (i + 1) < args.length && !args[i + 1].startsWith("-")) {
					switch (args[i]) {
						case "-w":
						case "-world":
							ClientSettings.SERVER_WORLD = Integer.parseInt(args[++i]);
							break;
						default:
							break;
					}
				}
			}

			ClientApplication.start(game);
		} catch (UnknownHostException e) {
			ClientLogger.error("Failed to resolve local host address", e);
		}
	}
}
