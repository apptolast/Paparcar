#!/usr/bin/env python3
"""Turn the app's OWN assets into web assets. Run on demand, not on every build.

Three jobs, all of them "reuse what the product already has" rather than invent:

  1. SCREENS  — the raw device captures from the Play session ([PLAY-SHOTS-EVERY-LANGUAGE-...-001])
                cropped free of the status bar and re-encoded as WebP at two widths.
                Each locale keeps ITS OWN capture: a Spanish page showing an English phone is the
                exact mistake the Play ticket existed to fix.
  2. ART      — the Compose `illus_*` VectorDrawables converted to plain SVG, so the site's
                illustrations ARE the app's illustrations, not lookalikes.
  3. SOCIAL   — the Open Graph card, drawn with the shipped Plus Jakarta Sans variable font.

Output lands in `web/assets/`, which is committed: the build script only copies it.
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / "web"
OUT_IMG = WEB / "assets" / "img"
DRAWABLE = ROOT / "shared/src/commonMain/composeResources/drawable"
FONT = ROOT / "shared/src/commonMain/composeResources/font/plus_jakarta_sans_variable.ttf"

# The status bar is app chrome, not product: "9:30 · 3G" adds nothing and, on the two mid-scroll
# captures, content scrolls UNDER it and reads as a rendering glitch. Cropped off all eight so the
# set stays one shape.
STATUS_BAR_PX = 120

# Three widths, and the last one is the capture's NATIVE width.
#
# The first version stopped at 640, and on any 2x or 3x display that is an upscale: the hero phone is
# ~300 CSS px wide, which a 3x phone renders at ~900 device px, so the browser was stretching a 640
# to fill it and the app's UI text went soft. 1080 is the raw capture — served as-is it cannot be
# blurry, only downscaled. The smaller two exist so a phone on mobile data does not pull 150 KB per
# screenshot; `sizes` in `phone()` is what decides which one each viewport asks for.
WIDTHS = (320, 640, 1080)

# Downscaling a dense UI screenshot softens hairlines and 12sp labels no matter how good the filter
# is. A light unsharp pass puts the edges back without the halo that a heavier one would leave on
# the map tiles. The native width is never resampled, so it is never sharpened either.
UNSHARP = dict(radius=0.6, percent=70, threshold=3)

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


# ── 1 · screens ───────────────────────────────────────────────────────────────

def prepare_screens(sources: dict[str, Path]) -> None:
    for lang, folder in sources.items():
        for n in range(1, 9):
            src = folder / f"shot{n}.png"
            if not src.is_file():
                sys.exit(f"missing capture: {src}")
            img = Image.open(src).convert("RGB")
            img = img.crop((0, STATUS_BAR_PX, img.width, img.height))
            for w in WIDTHS:
                dst = OUT_IMG / f"screen-{n}-{lang}-{w}.webp"
                if w >= img.width:
                    out = img
                else:
                    h = round(img.height * w / img.width)
                    out = img.resize((w, h), Image.LANCZOS).filter(ImageFilter.UnsharpMask(**UNSHARP))
                out.save(dst, "WEBP", quality=88, method=6)
        print(f"screens: {lang} ok")


# ── 2 · VectorDrawable → SVG ──────────────────────────────────────────────────

def _colour(value: str | None) -> str | None:
    """`#AARRGGBB` / `#RRGGBB` → CSS. Fully transparent fills become `none`."""
    if not value:
        return None
    v = value.lstrip("#")
    if len(v) == 8:
        alpha, rgb = int(v[:2], 16), v[2:]
        if alpha == 0:
            return "none"
        return f"#{rgb}" if alpha == 255 else f"#{rgb}"
    return f"#{v}"


def _group_transform(el: ET.Element) -> str:
    """VectorDrawable group attrs → one SVG transform, in the order VD applies them."""
    g = lambda k, d=0.0: float(el.get(ANDROID_NS + k, d))  # noqa: E731
    px, py = g("pivotX"), g("pivotY")
    parts = [f"translate({g('translateX')} {g('translateY')})"]
    if px or py:
        parts.append(f"translate({px} {py})")
    if g("rotation"):
        parts.append(f"rotate({g('rotation')})")
    sx, sy = g("scaleX", 1.0), g("scaleY", 1.0)
    if (sx, sy) != (1.0, 1.0):
        parts.append(f"scale({sx} {sy})")
    if px or py:
        parts.append(f"translate({-px} {-py})")
    return " ".join(p for p in parts if p != "translate(0.0 0.0)")


def _path(el: ET.Element) -> str:
    a = {
        "d": el.get(ANDROID_NS + "pathData"),
        "fill": _colour(el.get(ANDROID_NS + "fillColor")) or "none",
        "fill-opacity": el.get(ANDROID_NS + "fillAlpha"),
        "stroke": _colour(el.get(ANDROID_NS + "strokeColor")),
        "stroke-opacity": el.get(ANDROID_NS + "strokeAlpha"),
        "stroke-width": el.get(ANDROID_NS + "strokeWidth"),
        "stroke-linecap": el.get(ANDROID_NS + "strokeLineCap"),
        "stroke-linejoin": el.get(ANDROID_NS + "strokeLineJoin"),
        "fill-rule": {"evenOdd": "evenodd"}.get(el.get(ANDROID_NS + "fillType") or ""),
    }
    attrs = " ".join(f'{k}="{v}"' for k, v in a.items() if v not in (None, ""))
    return f"<path {attrs}/>"


def _children(el: ET.Element) -> str:
    out = []
    for child in el:
        tag = child.tag.split("}")[-1]
        if tag == "path":
            out.append(_path(child))
        elif tag == "group":
            t = _group_transform(child)
            out.append(f'<g transform="{t}">{_children(child)}</g>' if t else f"<g>{_children(child)}</g>")
    return "".join(out)


def vector_to_svg(src: Path, title: str) -> str:
    root = ET.parse(src).getroot()
    vw = root.get(ANDROID_NS + "viewportWidth")
    vh = root.get(ANDROID_NS + "viewportHeight")
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {vw} {vh}" role="img" '
        f'aria-label="{title}">{_children(root)}</svg>'
    )


def prepare_art(items: dict[str, tuple[str, str]]) -> None:
    for name, (source, title) in items.items():
        svg = vector_to_svg(DRAWABLE / f"{source}.xml", title)
        (OUT_IMG / f"{name}.svg").write_text(svg, encoding="utf-8")
    print(f"art: {len(items)} svg ok")


# ── 3 · social card ───────────────────────────────────────────────────────────

INK = (13, 17, 23)
INK_HIGH = (26, 34, 50)
NEON = (37, 244, 140)
ON_DARK = (235, 242, 239)
MUTED = (142, 160, 180)


def _font(size: int, weight: int) -> ImageFont.FreeTypeFont:
    f = ImageFont.truetype(str(FONT), size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:  # static fallback, should not happen with the shipped variable file
        pass
    return f


def prepare_social(shot: Path) -> None:
    W, H = 1200, 630
    card = Image.new("RGB", (W, H), INK)
    d = ImageDraw.Draw(card)

    # A soft brand wash behind the phone, built from concentric rounded rects rather than a
    # gradient image: the doctrine keeps gradients scarce, and this stays crisp at any scale.
    for i in range(26):
        t = i / 25
        box = (740 - i * 26, 60 - i * 18, 1180 + i * 8, 700 + i * 18)
        shade = tuple(round(INK[c] + (INK_HIGH[c] - INK[c]) * (1 - t) * 0.5) for c in range(3))
        d.rounded_rectangle(box, radius=320, fill=shade)

    phone = Image.open(shot).convert("RGB")
    phone = phone.crop((0, STATUS_BAR_PX, phone.width, phone.height))
    ph = 700
    phone = phone.resize((round(phone.width * ph / phone.height), ph), Image.LANCZOS)
    mask = Image.new("L", phone.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, phone.width, phone.height), radius=46, fill=255)
    card.paste(phone, (812, 96), mask)
    d.rounded_rectangle((812, 96, 812 + phone.width, 96 + ph), radius=46, outline=(59, 74, 94), width=3)

    logo = Image.open(ROOT / "docs/release/play-listing/assets/play-icon-512.png").convert("RGBA")
    logo = logo.resize((84, 84), Image.LANCZOS)
    card.paste(logo, (80, 74), logo)
    d.text((182, 92), "Paparcar", font=_font(46, 800), fill=ON_DARK)

    d.text((80, 232), "¿Dónde aparcaste?", font=_font(62, 800), fill=ON_DARK)
    d.text((80, 308), "Paparcar lo recuerda", font=_font(62, 800), fill=NEON)
    d.text((80, 384), "por ti.", font=_font(62, 800), fill=NEON)
    d.text(
        (80, 486),
        "Detecta que has aparcado y guarda\ndónde está tu coche. Sin sacar el móvil.",
        font=_font(27, 500), fill=MUTED, spacing=12,
    )
    card.save(OUT_IMG / "og-card.jpg", "JPEG", quality=88, optimize=True)
    card.resize((600, 315), Image.LANCZOS).save(OUT_IMG / "og-card-small.jpg", "JPEG", quality=86)
    print("social: og card ok")


def prepare_touch_icon() -> None:
    icon = Image.open(ROOT / "docs/release/play-listing/assets/play-icon-512.png").convert("RGB")
    icon.resize((180, 180), Image.LANCZOS).save(OUT_IMG / "apple-touch-icon.png", "PNG", optimize=True)
    print("icon: apple-touch ok")


if __name__ == "__main__":
    OUT_IMG.mkdir(parents=True, exist_ok=True)
    if len(sys.argv) < 3:
        sys.exit("usage: prepare-assets.py <es-raw-dir> <en-raw-dir>")
    es_dir, en_dir = Path(sys.argv[1]), Path(sys.argv[2])
    prepare_screens({"es": es_dir, "en": en_dir})
    prepare_art({
        "art-drive": ("illus_onb_how_dark", "Coche circulando con la detección activa"),
        "art-nearby": ("illus_onb_nearby_dark", "Plazas libres cerca en el mapa"),
        "art-privacy": ("illus_onb_privacy_dark", "Zona privada protegida"),
        "art-automatic": ("illus_automation_dark", "Detección automática confirmada"),
        "art-welcome": ("illus_onb_welcome_dark", "Coche aparcado y localizado"),
        "art-empty": ("empty_records_dark", "Sin registros todavía"),
    })
    prepare_social(es_dir / "shot1.png")
    prepare_touch_icon()
