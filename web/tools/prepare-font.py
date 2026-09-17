#!/usr/bin/env python3
"""Subset the app's OWN variable font for the web.

The site must be painted with the same letter as the app — `plus_jakarta_sans_variable.ttf`,
the single family behind the three voices of `PaparcarType.kt`. Self-hosted on purpose: pulling it
from a font CDN would add a third-party recipient of every visitor's IP, and §4 of the published
privacy policy names the recipients one by one. A page that quietly adds one makes that table wrong.

Keeps the `wght` axis whole (200-800) — the roles use 400/500/600/700/800 — and drops every
codepoint the nine site languages never print.

Requires: `pip install fonttools brotli`.
"""
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "shared/src/commonMain/composeResources/font/plus_jakarta_sans_variable.ttf"
DST = ROOT / "web/assets/font/plus-jakarta-sans.woff2"

# Basic Latin + Latin-1 Supplement + Latin Extended-A (pl/ro diacritics) + the punctuation the copy
# actually uses. No emoji: the site draws its symbols as SVG.
UNICODES = "U+0020-007E,U+00A0-00FF,U+0100-017F,U+0218-021B,U+2010-2015,U+2018-201F,U+2022,U+2026,U+20AC,U+2212"


def main() -> None:
    DST.parent.mkdir(parents=True, exist_ok=True)
    font = TTFont(str(SRC))
    options = subset.Options()
    options.flavor = "woff2"
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.notdef_outline = True
    options.desubroutinize = False
    options.retain_gids = False
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=subset.parse_unicodes(UNICODES))
    subsetter.subset(font)
    font.flavor = "woff2"
    font.save(str(DST))
    print(f"font: {DST.name} {DST.stat().st_size / 1024:.1f} KB "
          f"(from {SRC.stat().st_size / 1024:.1f} KB ttf)")


if __name__ == "__main__":
    main()
