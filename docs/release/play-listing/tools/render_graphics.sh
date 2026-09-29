#!/bin/bash
# [PLAY-THE-LISTING-ART-STILL-SHOWS-THE-2022-MARK-001]
# Renders the two GRAPHICS of the listing — the 512 app icon and the 1024x500 feature graphic in
# every locale — out of `assets/icon.html` and `assets/feature.html`.
#
# usage: render_graphics.sh [icon|feature|all] [locale ...]     (default: all, the 9 locales)
#
# Why a script and not the copy-pasted Chrome command the README had: these two HTML carry the
# MARK, and the mark is generated (`docs/branding/master/export_surfaces.py` rewrites their glyph
# groups). A one-off command that nobody can re-run is exactly how `play-icon-512.png` spent five
# weeks showing the 2022 logo while its own source already had the 2026 one.
#
# ⛔ Run `export_surfaces.py` FIRST if the drawing changed: this only rasterises what the HTML says.
set -eu
WHAT="${1:-all}"; [ $# -gt 0 ] && shift || true
LOCALES=("$@")
[ ${#LOCALES[@]} -eq 0 ] && LOCALES=(en es it pt fr de nl pl ro)

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS="$HERE/assets"
# Chrome needs a Windows path for both the page and the output (same reason as render.sh).
# `cygpath -m` is the Windows path with forward slashes, which is what a file:// URL wants.
WINASSETS=$(cygpath -m "$ASSETS" 2>/dev/null) || WINASSETS="$ASSETS"
CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
TMP="${TMPDIR:-/tmp}/paparcar-play-render"
mkdir -p "$TMP"
WINTMP=$(cygpath -m "$TMP" 2>/dev/null) || WINTMP="$TMP"

shoot() { # shoot <out-win-path> <w> <h> <url>
  # `--virtual-time-budget` is not decoration: `feature.html` loads Plus Jakarta Sans from
  # `fonts/` over file:// and paints the tagline from a script. Without it the shot can land on
  # the fallback family, which looks like a render that worked.
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
    --force-device-scale-factor=1 --virtual-time-budget=4000 \
    --window-size="$2,$3" --screenshot="$1" "$4" >/dev/null 2>&1
}

# Play wants both graphics opaque, and Chrome writes RGBA. Flattening here (instead of trusting the
# page's own background) keeps a transparent corner from reaching the Console as a black one.
flatten() { python - "$@" <<'PY'
import sys
from PIL import Image
src, dst = sys.argv[1], sys.argv[2]
box = [int(v) for v in sys.argv[3:7]] if len(sys.argv) > 3 else None
im = Image.open(src)
if box:
    im = im.crop(box)
if im.mode in ("RGBA", "LA", "P"):
    im = im.convert("RGBA")
    flat = Image.new("RGB", im.size, (0, 0, 0))
    flat.paste(im, mask=im.split()[-1])
    im = flat
else:
    im = im.convert("RGB")
im.save(dst, "PNG", optimize=True)
print(f"  {dst.rsplit('/', 1)[-1]}  {im.size[0]}x{im.size[1]}  {im.mode}")
PY
}

if [ "$WHAT" = icon ] || [ "$WHAT" = all ]; then
  # One shot of the three cells side by side (3 x 512), then cut. The cells are NOT three takes on
  # the same idea: B is the live Play icon (glyph at 0.72) and A is the launcher's 0.60 kept as the
  # small-glyph alternative — the two numbers differ ON PURPOSE, see the README.
  echo "icono (1536x512 -> 3 celdas de 512)"
  shoot "$WINTMP/icon-row.png" 1536 512 "file:///$WINASSETS/icon.html"
  flatten "$TMP/icon-row.png" "$ASSETS/alt-icon-512-small-glyph.png"    0 0  512 512
  flatten "$TMP/icon-row.png" "$ASSETS/play-icon-512.png"             512 0 1024 512
  flatten "$TMP/icon-row.png" "$ASSETS/alt-icon-512-glow.png"        1024 0 1536 512
fi

if [ "$WHAT" = feature ] || [ "$WHAT" = all ]; then
  echo "grafico de funciones (1024x500, por idioma)"
  for L in "${LOCALES[@]}"; do
    mkdir -p "$ASSETS/screenshots/$L"
    shoot "$WINTMP/fg-$L.png" 1024 500 "file:///$WINASSETS/feature.html?lang=$L"
    # The locale goes in the FILE NAME, not just the folder (same reason as the 72 shots).
    flatten "$TMP/fg-$L.png" "$ASSETS/screenshots/$L/$L-feature-graphic-1024x500.png"
    # EN is also the graphic of the DEFAULT listing, and that one is versioned: it is the only
    # feature graphic that survives a fresh clone.
    [ "$L" = en ] && flatten "$TMP/fg-$L.png" "$ASSETS/play-feature-graphic-1024x500.png"
  done
fi

echo "done"
