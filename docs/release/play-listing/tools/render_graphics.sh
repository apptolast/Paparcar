#!/bin/bash
# [PLAY-THE-LISTING-ART-STILL-SHOWS-THE-2022-MARK-001]
# Renders the two GRAPHICS of the listing — the 512 app icon and the 1024x500 feature graphic in
# every locale — out of `assets/icon.html` and `assets/feature.html`.
#
# usage: render_graphics.sh [icon|feature|emblem|all] [locale ...]  (default: all, 9 locales)
#
# `emblem` no es de la ficha de Play: es el emblema que pega la tarjeta social de la web
# [WEB-THE-MARK-WEARS-ITS-DISC-ON-THE-SITE-001]. Vive aqui porque este es el rasterizador
# del repo -no hay uno en Python: svglib parsea, pero reportlab no trae backend- y porque
# lo que rasteriza es la MARCA, que es de lo que este script ya se ocupa.
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

if [ "$WHAT" = emblem ] || [ "$WHAT" = all ]; then
  # El emblema de la web: la marca sobre su disco VERDE (sobre la tinta plana del sitio,
  # un disco de tinta da 1,00:1 — ver export_surfaces.py). Se rasteriza CON ALFA: fuera
  # del disco no hay nada, y la tarjeta social compone encima de su propio fondo.
  #
  # Antes `prepare_social()` pegaba `play-icon-512.png`, el icono de OTRA superficie —una
  # baldosa opaca—, y por eso la tarjeta arrastro la marca de 2022 dentro de un JPEG
  # mientras el resto del repo ya tenia la de 2026. Un PNG generado si vale; un PNG
  # prestado de otra superficie, no.
  echo "emblema de la web (512x512, con alfa)"
  REPO=$(cd "$HERE/../../.." && pwd)
  WINREPO=$(cygpath -m "$REPO" 2>/dev/null) || WINREPO="$REPO"
  # Un <img> con margen cero: Chrome abre un SVG suelto con el margen del body, que
  # descentraria el disco sin que el tamano del PNG lo delate.
  printf '%s\n' \
    '<!doctype html><meta charset="utf-8">' \
    '<style>html,body{margin:0;padding:0;background:transparent}' \
    'img{display:block;width:512px;height:512px}</style>' \
    "<img src=\"file:///$WINREPO/docs/assets/paparcar-emblem.svg\">" \
    > "$TMP/emblem.html"
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
    --force-device-scale-factor=1 --virtual-time-budget=2000 \
    --default-background-color=00000000 \
    --window-size=512,512 --screenshot="$WINTMP/emblem.png" \
    "file:///$WINTMP/emblem.html" >/dev/null 2>&1
  # Se COMPRUEBA el contenido, no el exit code ni el tamano: un PNG del tamano pedido
  # puede estar vacio, y ya paso una vez con estos mismos graficos.
  python - "$TMP/emblem.png" "$REPO/docs/assets/paparcar-emblem-512.png" <<'PY'
import math
import sys
from PIL import Image

# Se mide por PROPORCIONES, no por pixeles concretos. Sondear un punto exige acertar
# donde cae un trazo, y el primer intento fallo: el centroide de la tinta cae dentro
# del OJO del pin, que es hueco. Las proporciones no dependen de acertar nada, y
# cazan los dos fallos que importan: un disco sin marca dentro, y un PNG vacio del
# tamano correcto -que es como este mismo pipeline enganio una vez-.
src, dst = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGBA")
if im.size != (512, 512):
    sys.exit("  emblema: %dx%d, se esperaba 512x512" % im.size)
px = im.load()
ink = green = clear = 0
for y in range(0, 512, 4):
    for x in range(0, 512, 4):
        r, g, b, a = px[x, y]
        if a == 0:
            clear += 1
        elif max(r, g, b) < 80:
            ink += 1
        elif g > 180 and r < 120:
            green += 1
tot = (512 // 4) ** 2
if px[4, 4][3] != 0:
    sys.exit("  emblema: la esquina no es transparente, falta el alfa")
# Un circulo inscrito en su caja deja fuera 1 - pi/4 = 21,5 %.
want = 1 - math.pi / 4
if abs(clear / tot - want) > 0.03:
    sys.exit("  emblema: %.1f%% transparente, se esperaba ~%.1f%%: el disco no llena la caja"
             % (100 * clear / tot, 100 * want))
if green / tot < 0.50:
    sys.exit("  emblema: solo %.1f%% verde, el disco no esta" % (100 * green / tot))
if not 0.04 < ink / tot < 0.20:
    sys.exit("  emblema: %.1f%% de tinta, la marca no esta dibujada dentro del disco"
             % (100 * ink / tot))
im.save(dst, "PNG", optimize=True)
print("  paparcar-emblem-512.png  512x512  RGBA  disco %.0f%% verde, marca %.0f%% tinta, "
      "esquina %.0f%% limpia" % (100 * green / tot, 100 * ink / tot, 100 * clear / tot))
PY
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
