#!/usr/bin/env python3
"""
Patch WrkX/2006Scape Game.java to re-bind the world rasterizer before each world render.

Usage:
    python fix_game_rendering.py path/to/Game.java

Outputs:
    path/to/Game.fixed.java

The input file is never overwritten.
"""

from __future__ import annotations

import sys
from pathlib import Path


BIND_METHOD = r"""
	private void bindWorldViewport() {
		if (aRSImageProducer_1165 == null) {
			return;
		}

		aRSImageProducer_1165.initDrawingArea();

		// This legacy field name is misleading: in the resizable client it stores
		// the scanline offsets for the dynamically sized world/game producer.
		Texture.lineOffsets = chatBoxAreaOffsets;

		Texture.textureInt1 = getGameAreaWidth() / 2;
		Texture.textureInt2 = getGameAreaHeight() / 2;

		// Restore clipping to the complete active world producer.
		DrawingArea.defaultDrawingAreaSize();
	}
""".strip("\n")


def patch_game(source: str) -> str:
    result = source

    method_marker = "\tpublic void method146() {\n"
    if method_marker not in result:
        raise RuntimeError("Could not find method146() in Game.java")

    if "private void bindWorldViewport()" not in result:
        result = result.replace(
            method_marker,
            BIND_METHOD + "\n\n" + method_marker,
            1,
        )

    method_start = "\tpublic void method146() {\n"
    bound_start = "\tpublic void method146() {\n\t\tbindWorldViewport();\n"
    if bound_start not in result:
        result = result.replace(method_start, bound_start, 1)

    old_setting = "\tboolean customSettingVisualFixes = true;"
    new_setting = (
        "\tboolean customSettingVisualFixes =\n"
        "\t\t\tClientSettings.FIX_TRANSPARENCY_OVERFLOW\n"
        "\t\t\t&& ClientSettings.FULL_512PX_VIEWPORT;"
    )
    if old_setting in result:
        result = result.replace(old_setting, new_setting, 1)

    return result


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: python fix_game_rendering.py path/to/Game.java", file=sys.stderr)
        return 2

    input_path = Path(sys.argv[1]).expanduser().resolve()
    if not input_path.is_file():
        print(f"Input file does not exist: {input_path}", file=sys.stderr)
        return 2

    source = input_path.read_text(encoding="utf-8")
    fixed = patch_game(source)

    output_path = input_path.with_name("Game.fixed.java")
    output_path.write_text(fixed, encoding="utf-8", newline="")

    print(f"Wrote: {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
