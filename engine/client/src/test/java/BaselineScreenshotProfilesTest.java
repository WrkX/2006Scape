import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

public class BaselineScreenshotProfilesTest {

	@Test
	public void profileNameUsesWidthByHeight() {
		assertEquals("765x503", BaselineScreenshotProfiles.profileName(765, 503));
		assertEquals("1920x1080", BaselineScreenshotProfiles.profileName(1920, 1080));
	}

	@Test
	public void screenshotPathsFollowResolutionAndScenario() {
		File repoRoot = new File("/tmp/rs-repo");
		File screenshot = BaselineScreenshotProfiles.getScreenshotFile(repoRoot, 1280, 720, "login");

		assertEquals(
				new File("/tmp/rs-repo/engine/client/baselines/1280x720/login.png"),
				screenshot);
	}

	@Test
	public void resolutionsIncludeFixedAndCommonDesktopSizes() {
		assertTrue(BaselineScreenshotProfiles.RESOLUTIONS.length >= 5);
		assertEquals(765, BaselineScreenshotProfiles.RESOLUTIONS[0][0]);
		assertEquals(503, BaselineScreenshotProfiles.RESOLUTIONS[0][1]);
	}
}
