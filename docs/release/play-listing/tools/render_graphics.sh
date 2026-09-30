#!/bin/bash
# [PLAY-THE-LISTING-ART-STILL-SHOWS-THE-2022-MARK-001]
# Renders the two GRAPHICS of the listing — the 512 app icon and the 1024x500 feature graphic in
# every locale — out of `assets/icon.html` and `assets/feature.html`.
#
# usage: render_graphics.sh [icon|feature|emblem|apple|all] [locale ...]  (default: all)
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
REPO=$(cd "$HERE/../../.." && pwd)
WINREPO=$(cygpath -m "$REPO" 2>/dev/null) || WINREPO="$REPO"

shoot() { # shoot <out-win-path> <w> <h> <url>
  # `--virtual-time-budget` is not decoration: `feature.html` loads Plus Jakarta Sans from
  # `fonts/` over file:// and paints the tagline from a script. Without it the shot can land on
  # the fallback family, which looks like a render that worked.
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
    --force-device-scale-factor=1 --virtual-time-budget=4000 \
    --window-size="$2,$3" --screenshot="$1" "$4" >/dev/null 2>&1
}

# Rasterise ONE svg at NxN. Chrome opens a bare .svg with the body's margin, which would shift
# the drawing without the PNG's size ever betraying it — hence the zero-margin <img> wrapper.
# `--default-background-color=00000000` keeps the alpha; the caller decides whether to flatten.
svg_shot() { # svg_shot <repo-relative svg> <size> <out-win-path>
  printf '%s\n' \
    '<!doctype html><meta charset="utf-8">' \
    "<style>html,body{margin:0;padding:0;background:transparent}" \
    "img{display:block;width:$2px;height:$2px}</style>" \
    "<img src=\"file:///$WINREPO/$1\">" \
    > "$TMP/svg_shot.html"
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
    --force-device-scale-factor=1 --virtual-time-budget=2000 \
    --default-background-color=00000000 \
    --window-size="$2,$2" --screenshot="$3" "file:///$WINTMP/svg_shot.html" >/dev/null 2>&1
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

if [ "$WHAT" = apple ] || [ "$WHAT" = all ]; then
  # Las superficies de Apple [BRAND-THE-APPLE-SURFACES-ARE-OUTSIDE-THE-MASTER-001]. Hasta este
  # ticket `iosApp/` estaba ENTERO fuera del generador, y por eso su icono siguio dibujando la
  # marca DESCARTADA -disco con ranura y pie- mientras el resto del repo ya tenia la de 2026.
  # Ningun grep lo delataba: el dibujo vivia como pixeles.
  #
  # Las tres variantes de iOS 18 no son decoracion: si faltan, el sistema usa la clara tambien en
  # modo oscuro y tenido. La oscura y la tenida van SIN fondo a proposito (el telon lo pone iOS).
  echo "Apple (3 iconos de iOS + el touch icon de la web)"
  IOS="$REPO/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"

  svg_shot docs/branding/apple/ios-app-icon.svg        1024 "$WINTMP/ios-light.png"
  svg_shot docs/branding/apple/ios-app-icon-dark.svg   1024 "$WINTMP/ios-dark.png"
  svg_shot docs/branding/apple/ios-app-icon-tinted.svg 1024 "$WINTMP/ios-tinted.png"
  svg_shot docs/branding/apple/web-touch-icon.svg       180 "$WINTMP/touch.png"

  # Se COMPRUEBA el contenido, no el exit code: un PNG del tamano pedido puede estar vacio, y en
  # este mismo pipeline ya paso. Se mide por PROPORCIONES -cuanta marca, cuanto fondo, cuanta
  # transparencia- porque sondear un pixel exige acertar donde cae un trazo.
  python - "$TMP" "$IOS" "$REPO/web/assets/img/apple-touch-icon.png" <<'PY'
import sys
from PIL import Image

tmp, ios, touch = sys.argv[1], sys.argv[2], sys.argv[3]


def survey(path):
    """Devuelve (lado, %marca verde o clara, %fondo opaco, %transparente)."""
    im = Image.open(path).convert("RGBA")
    px = im.load()
    step = max(1, im.width // 128)
    mark = solid = clear = 0
    for y in range(0, im.height, step):
        for x in range(0, im.width, step):
            r, g, b, a = px[x, y]
            if a < 16:
                clear += 1
            elif g > 150 and r < 140 and g - b > 40:      # PapGreen
                mark += 1
            elif r > 180 and g > 180 and b > 180:          # gris claro de la tenida
                mark += 1
            else:
                solid += 1
    tot = mark + solid + clear
    return im, im.width, 100.0 * mark / tot, 100.0 * solid / tot, 100.0 * clear / tot


def save(im, dst, opaque):
    if opaque:
        flat = Image.new("RGB", im.size, (0, 0, 0))
        flat.paste(im, mask=im.split()[-1])
        im = flat
    im.save(dst, "PNG", optimize=True)


# Los suelos salen de MEDIR, no de estimar. A la fraccion de tienda la marca cubre 7,9 % de su
# baldosa -exactamente lo que mide el play-icon-512 publicado, que es la confirmacion de que iOS
# y Play dibujan lo mismo-; el touch icon, mas grande, cubre 17,0 %. El suelo se pone holgado por
# debajo: lo que tiene que cazar es "no hay glifo" (0 %), no una decima.
jobs = [
    # fichero temporal, destino, opaco, marca minima, transparencia esperada
    ("ios-light.png",  ios + "/app-icon-1024.png",         True,   5.0,  0.0),
    ("ios-dark.png",   ios + "/app-icon-1024-dark.png",    False,  5.0, 92.0),
    ("ios-tinted.png", ios + "/app-icon-1024-tinted.png",  False,  5.0, 92.0),
    ("touch.png",      touch,                              True,  12.0,  0.0),
]
for name, dst, opaque, min_mark, want_clear in jobs:
    im, side, mark, solid, clear = survey(tmp + "/" + name)
    label = dst.rsplit("/", 1)[-1]
    if mark < min_mark:
        sys.exit("  %s: solo %.1f%% de marca, se esperaba >=%.0f%% - el glifo no esta"
                 % (label, mark, min_mark))
    if abs(clear - want_clear) > 12.0:
        sys.exit("  %s: %.1f%% transparente, se esperaba ~%.0f%% - el fondo no es el pedido"
                 % (label, clear, want_clear))
    save(im, dst, opaque)
    print("  %-26s %dx%d  marca %.0f%%  fondo %.0f%%  transparente %.0f%%"
          % (label, side, side, mark, solid, clear))
PY
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
  svg_shot docs/assets/paparcar-emblem.svg 512 "$WINTMP/emblem.png"
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
