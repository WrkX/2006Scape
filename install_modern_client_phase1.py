#!/usr/bin/env python3
"""
Installs the first modernization phase into the current WrkX/2006Scape client.

Changes:
  * explicit world RenderTarget binding
  * allocation-free frame profiling
  * central F-key tab bindings
  * resize no-op guard
  * cached ground-item number formatter
  * consistent visual-fixes state
  * rate-limited render exception logging

The installer:
  * creates a .bak copy of Game.java
  * fails loudly if expected source markers are missing
  * is idempotent
"""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path


JAVA_DIR = Path("engine/client/src/main/java")
GAME_PATH = JAVA_DIR / "Game.java"

NEW_FILES = {
    "RenderTarget.java": r"""/**
 * Explicit binding for one software-rendering surface.
 *
 * The legacy client stores its active pixel buffer, scanline offsets and
 * projection centre in global static fields. Keeping these values together
 * prevents chat/tab rendering from leaking into the world renderer.
 */
public final class RenderTarget {

	private final RSImageProducer producer;
	private final int[] lineOffsets;
	private final int width;
	private final int height;

	public RenderTarget(RSImageProducer producer, int[] lineOffsets, int width, int height) {
		if (producer == null) {
			throw new IllegalArgumentException("producer");
		}
		if (lineOffsets == null) {
			throw new IllegalArgumentException("lineOffsets");
		}
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("Invalid render target size: " + width + "x" + height);
		}
		this.producer = producer;
		this.lineOffsets = lineOffsets;
		this.width = width;
		this.height = height;
	}

	public void bind() {
		producer.initDrawingArea();
		Texture.lineOffsets = lineOffsets;
		Texture.textureInt1 = width / 2;
		Texture.textureInt2 = height / 2;
		DrawingArea.defaultDrawingAreaSize();
	}

	public boolean matches(int expectedWidth, int expectedHeight) {
		return width == expectedWidth && height == expectedHeight;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}
}
""",
    "FrameProfiler.java": r"""import java.util.EnumMap;

/**
 * Tiny allocation-free frame profiler suitable for the old software client.
 */
public final class FrameProfiler {

	public enum Section {
		FRAME,
		WORLD,
		UI
	}

	private static final double NANOS_TO_MILLIS = 1.0D / 1_000_000.0D;
	private static final double SMOOTHING = 0.10D;

	private final EnumMap<Section, Long> starts = new EnumMap<Section, Long>(Section.class);
	private final EnumMap<Section, Double> averages = new EnumMap<Section, Double>(Section.class);
	private final EnumMap<Section, Double> maximums = new EnumMap<Section, Double>(Section.class);

	public long begin(Section section) {
		long now = System.nanoTime();
		starts.put(section, now);
		return now;
	}

	public void end(Section section) {
		Long start = starts.get(section);
		if (start == null) {
			return;
		}
		record(section, System.nanoTime() - start.longValue());
	}

	public void record(Section section, long elapsedNanos) {
		double millis = elapsedNanos * NANOS_TO_MILLIS;
		Double previous = averages.get(section);
		double average = previous == null
				? millis
				: previous.doubleValue() + (millis - previous.doubleValue()) * SMOOTHING;
		averages.put(section, average);

		Double maximum = maximums.get(section);
		if (maximum == null || millis > maximum.doubleValue()) {
			maximums.put(section, millis);
		}
	}

	public double getAverageMillis(Section section) {
		Double value = averages.get(section);
		return value == null ? 0.0D : value.doubleValue();
	}

	public double getMaximumMillis(Section section) {
		Double value = maximums.get(section);
		return value == null ? 0.0D : value.doubleValue();
	}

	public String formatAverage(Section section) {
		return String.format(java.util.Locale.ROOT, "%.2f ms", getAverageMillis(section));
	}

	public void resetMaximums() {
		maximums.clear();
	}
}
""",
    "ClientKeyBindings.java": r"""import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Central keyboard-action mapping.
 *
 * This phase keeps the existing F-key defaults while removing the tab mapping
 * from the large Game.keyPressed switch. Persistence/remapping can be layered
 * onto this class later without touching rendering or packet code.
 */
public final class ClientKeyBindings {

	private final Map<Integer, Integer> tabByKey;

	private ClientKeyBindings(Map<Integer, Integer> tabByKey) {
		this.tabByKey = Collections.unmodifiableMap(new HashMap<Integer, Integer>(tabByKey));
	}

	public static ClientKeyBindings defaults() {
		Map<Integer, Integer> bindings = new HashMap<Integer, Integer>();
		bindings.put(KeyEvent.VK_F1, 3);
		bindings.put(KeyEvent.VK_F2, 4);
		bindings.put(KeyEvent.VK_F3, 5);
		bindings.put(KeyEvent.VK_F4, 6);
		bindings.put(KeyEvent.VK_F5, 0);
		bindings.put(KeyEvent.VK_F6, 1);
		bindings.put(KeyEvent.VK_F7, 2);
		bindings.put(KeyEvent.VK_F8, 8);
		bindings.put(KeyEvent.VK_F9, 11);
		return new ClientKeyBindings(bindings);
	}

	public Integer getTabForKey(int keyCode) {
		return tabByKey.get(keyCode);
	}

	public Map<Integer, Integer> asMap() {
		return tabByKey;
	}
}
""",
    "ClientLog.java": r"""import java.util.HashMap;
import java.util.Map;

/**
 * Rate-limited diagnostics for places where the old client previously swallowed
 * rendering exceptions.
 */
public final class ClientLog {

	private static final long REPEAT_WINDOW_MILLIS = 5_000L;
	private static final Map<String, Long> lastLogTimes = new HashMap<String, Long>();

	private ClientLog() {
	}

	public static synchronized void renderError(String key, String message, Throwable error) {
		long now = System.currentTimeMillis();
		Long previous = lastLogTimes.get(key);
		if (previous != null && now - previous.longValue() < REPEAT_WINDOW_MILLIS) {
			return;
		}
		lastLogTimes.put(key, now);
		System.err.println("[render] " + message);
		if (error != null) {
			error.printStackTrace(System.err);
		}
	}
}
""",
}


def replace_once(text: str, old: str, new: str, description: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(
            f"{description}: expected exactly one source marker, found {count}"
        )
    return text.replace(old, new, 1)


def patch_game(source: str) -> str:
    result = source

    result = replace_once(
        result,
        "\tprivate boolean graphicsEnabled = true;\n",
        "\tprivate boolean graphicsEnabled = true;\n"
        "\tprivate RenderTarget worldRenderTarget;\n"
        "\tprivate final FrameProfiler frameProfiler = new FrameProfiler();\n"
        "\tprivate final ClientKeyBindings keyBindings = ClientKeyBindings.defaults();\n"
        "\tprivate static final DecimalFormat GROUND_ITEM_AMOUNT_FORMAT = createGroundItemFormatter();\n",
        "client modernization fields",
    )

    formatter_anchor = "\tpublic static String intToKOrMilLongName(int i) {\n"
    formatter_method = (
        "\tprivate static DecimalFormat createGroundItemFormatter() {\n"
        "\t\tDecimalFormatSymbols symbols = new DecimalFormatSymbols();\n"
        "\t\tsymbols.setGroupingSeparator(',');\n"
        "\t\treturn new DecimalFormat(\"#,###,###,###\", symbols);\n"
        "\t}\n\n"
    )
    if "createGroundItemFormatter()" not in result[result.find(formatter_anchor)-500:result.find(formatter_anchor)]:
        result = replace_once(
            result,
            formatter_anchor,
            formatter_method + formatter_anchor,
            "ground-item formatter helper",
        )

    result = replace_once(
        result,
        "\tboolean customSettingVisualFixes = true;",
        "\tboolean customSettingVisualFixes =\n"
        "\t\t\tClientSettings.FIX_TRANSPARENCY_OVERFLOW\n"
        "\t\t\t&& ClientSettings.FULL_512PX_VIEWPORT;",
        "visual-fixes initial state",
    )

    result = replace_once(
        result,
        "\tpublic void onResize(int w, int h) {\n"
        "\t\tif (w < 765) w = 765;\n"
        "\t\tif (h < 503) h = 503;\n",
        "\tpublic void onResize(int w, int h) {\n"
        "\t\tif (w < 765) w = 765;\n"
        "\t\tif (h < 503) h = 503;\n"
        "\t\tif (w == super.myWidth && h == super.myHeight) {\n"
        "\t\t\treturn;\n"
        "\t\t}\n",
        "resize no-op guard",
    )

    result = replace_once(
        result,
        "\t\tgameScreenOffsets = Texture.method365_ret(super.myWidth, super.myHeight);\n",
        "\t\tgameScreenOffsets = Texture.method365_ret(super.myWidth, super.myHeight);\n"
        "\t\tworldRenderTarget = new RenderTarget(\n"
        "\t\t\t\taRSImageProducer_1165, chatBoxAreaOffsets, gameW, gameH);\n",
        "world render target creation",
    )

    old_apply_tail = (
        "\t\taRSImageProducer_1165.initDrawingArea();\n"
        "\t\tTexture.lineOffsets = chatBoxAreaOffsets;\n"
        "\t}\n\n"
        "\tint getGameAreaWidth()"
    )
    new_apply_tail = (
        "\t\tbindWorldViewport();\n"
        "\t}\n\n"
        "\tprivate void bindWorldViewport() {\n"
        "\t\tif (worldRenderTarget == null\n"
        "\t\t\t\t|| !worldRenderTarget.matches(getGameAreaWidth(), getGameAreaHeight())) {\n"
        "\t\t\tworldRenderTarget = new RenderTarget(\n"
        "\t\t\t\t\taRSImageProducer_1165,\n"
        "\t\t\t\t\tchatBoxAreaOffsets,\n"
        "\t\t\t\t\tgetGameAreaWidth(),\n"
        "\t\t\t\t\tgetGameAreaHeight());\n"
        "\t\t}\n"
        "\t\tworldRenderTarget.bind();\n"
        "\t}\n\n"
        "\tint getGameAreaWidth()"
    )
    result = replace_once(
        result,
        old_apply_tail,
        new_apply_tail,
        "world viewport binding",
    )

    result = replace_once(
        result,
        "\tpublic void method146() {\n"
        "\t\tanInt1265++;\n",
        "\tpublic void method146() {\n"
        "\t\tframeProfiler.begin(FrameProfiler.Section.FRAME);\n"
        "\t\tbindWorldViewport();\n"
        "\t\tanInt1265++;\n",
        "frame start and viewport bind",
    )

    old_world = (
        "\t\tif(graphicsEnabled){\n"
        "\t\t\tworldController.method313(xCameraPos, yCameraPos, xCameraCurve, zCameraPos, j, yCameraCurve);\n"
        "\t\t\tworldController.clearObj5Cache();\n"
        "\t\t\tupdateEntities();\n"
        "\t\t\tdrawHeadIcon();\n"
        "\t\t}\n"
    )
    new_world = (
        "\t\tframeProfiler.begin(FrameProfiler.Section.WORLD);\n"
        "\t\ttry {\n"
        "\t\t\tif(graphicsEnabled){\n"
        "\t\t\t\tworldController.method313(xCameraPos, yCameraPos, xCameraCurve, zCameraPos, j, yCameraCurve);\n"
        "\t\t\t\tworldController.clearObj5Cache();\n"
        "\t\t\t\tupdateEntities();\n"
        "\t\t\t\tdrawHeadIcon();\n"
        "\t\t\t}\n"
        "\t\t} finally {\n"
        "\t\t\tframeProfiler.end(FrameProfiler.Section.WORLD);\n"
        "\t\t}\n"
    )
    result = replace_once(result, old_world, new_world, "world profiler")

    old_frame_end = (
        "\t\tif(graphicsEnabled) {\n"
        "\t\t\txCameraPos = l;\n"
        "\t\t\tzCameraPos = i1;\n"
        "\t\t\tyCameraPos = j1;\n"
        "\t\t\tyCameraCurve = k1;\n"
        "\t\t\txCameraCurve = l1;\n"
        "\t\t}\n"
        "\t}\n\n"
        "\tpublic void closeOpenInterfaces()"
    )
    new_frame_end = (
        "\t\tif(graphicsEnabled) {\n"
        "\t\t\txCameraPos = l;\n"
        "\t\t\tzCameraPos = i1;\n"
        "\t\t\tyCameraPos = j1;\n"
        "\t\t\tyCameraCurve = k1;\n"
        "\t\t\txCameraCurve = l1;\n"
        "\t\t}\n"
        "\t\tframeProfiler.end(FrameProfiler.Section.FRAME);\n"
        "\t}\n\n"
        "\tpublic void closeOpenInterfaces()"
    )
    result = replace_once(result, old_frame_end, new_frame_end, "frame profiler end")

    old_formatter = (
        "\t\t\t\t\tDecimalFormatSymbols separator = new DecimalFormatSymbols();\n"
        "\t\t\t\t\tseparator.setGroupingSeparator(',');\n"
        "\t\t\t\t\tDecimalFormat formatter = new DecimalFormat(\"#,###,###,###\", separator);\n"
        "\t\t\t\t\ttext += formatter.format(item.amount) + \" x \";"
    )
    new_formatter = (
        "\t\t\t\t\ttext += GROUND_ITEM_AMOUNT_FORMAT.format(item.amount) + \" x \";"
    )
    result = replace_once(result, old_formatter, new_formatter, "cached item formatter")

    key_anchor = (
        "\tpublic void keyPressed(KeyEvent keyevent)\n"
        "\t{\n"
        "\t\tsuper.keyPressed(keyevent);\n"
    )
    key_new = (
        "\tpublic void keyPressed(KeyEvent keyevent)\n"
        "\t{\n"
        "\t\tsuper.keyPressed(keyevent);\n"
        "\t\tInteger boundTab = keyBindings.getTabForKey(keyevent.getKeyCode());\n"
        "\t\tif (boundTab != null) {\n"
        "\t\t\tneedDrawTabArea = true;\n"
        "\t\t\ttabID = boundTab.intValue();\n"
        "\t\t\ttabAreaAltered = true;\n"
        "\t\t\treturn;\n"
        "\t\t}\n"
    )
    result = replace_once(result, key_anchor, key_new, "central key bindings")

    # Add profiler data to the existing debug overlay without changing normal UI.
    result = result.replace(
        "\t\t\tint debugItems = 5;",
        "\t\t\tint debugItems = 8;",
        1,
    )
    zoom_line = (
        "\t\t\tchatTextDrawingArea.textRightShadow(true, debugX + debugWidth - 4, "
        "Color.YELLOW.hashCode(), \"\" + zoom, debugY);\n"
    )
    profiler_lines = (
        zoom_line
        + "\t\t\tchatTextDrawingArea.textLeftShadow(true, debugX + 4, Color.WHITE.hashCode(), \"Frame:\", debugY += 15);\n"
        + "\t\t\tchatTextDrawingArea.textRightShadow(true, debugX + debugWidth - 4, Color.YELLOW.hashCode(), frameProfiler.formatAverage(FrameProfiler.Section.FRAME), debugY);\n"
        + "\t\t\tchatTextDrawingArea.textLeftShadow(true, debugX + 4, Color.WHITE.hashCode(), \"World:\", debugY += 15);\n"
        + "\t\t\tchatTextDrawingArea.textRightShadow(true, debugX + debugWidth - 4, Color.YELLOW.hashCode(), frameProfiler.formatAverage(FrameProfiler.Section.WORLD), debugY);\n"
        + "\t\t\tchatTextDrawingArea.textLeftShadow(true, debugX + 4, Color.WHITE.hashCode(), \"World max:\", debugY += 15);\n"
        + "\t\t\tchatTextDrawingArea.textRightShadow(true, debugX + debugWidth - 4, Color.YELLOW.hashCode(), String.format(java.util.Locale.ROOT, \"%.2f ms\", frameProfiler.getMaximumMillis(FrameProfiler.Section.WORLD)), debugY);\n"
    )
    result = replace_once(result, zoom_line, profiler_lines, "profiler debug overlay")

    # Turn the settings-tab empty catch into a useful, rate-limited diagnostic.
    result = result.replace(
        "\t\t\t\t\t} catch (Exception e) { }\n",
        "\t\t\t\t\t} catch (Exception e) {\n"
        "\t\t\t\t\t\tClientLog.renderError(\"settings-tab\", \"Failed to render custom settings tab\", e);\n"
        "\t\t\t\t\t}\n",
        1,
    )

    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "repo",
        nargs="?",
        default=".",
        help="Path to the root of the WrkX/2006Scape checkout",
    )
    parser.add_argument("--check", action="store_true", help="Validate only; write nothing")
    args = parser.parse_args()

    repo = Path(args.repo).expanduser().resolve()
    game_path = repo / GAME_PATH
    if not game_path.is_file():
        raise SystemExit(f"Game.java not found: {game_path}")

    source = game_path.read_text(encoding="utf-8")
    patched = patch_game(source)

    if args.check:
        print("Patch markers validated successfully.")
        return 0

    backup = game_path.with_suffix(".java.bak")
    if not backup.exists():
        shutil.copy2(game_path, backup)

    game_path.write_text(patched, encoding="utf-8", newline="")

    java_dir = repo / JAVA_DIR
    java_dir.mkdir(parents=True, exist_ok=True)
    for name, content in NEW_FILES.items():
        (java_dir / name).write_text(content, encoding="utf-8", newline="")

    print("Installed modern client phase 1.")
    print(f"Backup: {backup}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
