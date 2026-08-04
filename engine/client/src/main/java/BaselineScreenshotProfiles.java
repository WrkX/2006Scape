import java.io.File;

/**
 * Baseline screenshot resolution presets and output paths for visual regression work.
 */
public final class BaselineScreenshotProfiles {

	public static final int[][] RESOLUTIONS = {
			{765, 503},
			{1024, 768},
			{1280, 720},
			{1920, 1080},
			{2560, 1440}
	};

	private BaselineScreenshotProfiles() {
	}

	public static String profileName(int width, int height) {
		return width + "x" + height;
	}

	public static File getRepositoryBaselinesDir(File repoRoot) {
		return new File(repoRoot, "engine/client/baselines");
	}

	public static File getProfileDirectory(File repoRoot, int width, int height) {
		return new File(getRepositoryBaselinesDir(repoRoot), profileName(width, height));
	}

	public static File getScreenshotFile(File repoRoot, int width, int height, String scenario) {
		return new File(getProfileDirectory(repoRoot, width, height), scenario + ".png");
	}
}
