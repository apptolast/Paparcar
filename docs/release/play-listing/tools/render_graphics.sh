#!/bin/bash
# [PLAY-THE-LISTING-ART-STILL-SHOWS-THE-2022-MARK-001]
# Renders the two GRAPHICS of the listing — the 512 app icon and the 1024x500 feature graphic in
# every locale — out of `assets/icon.html` and `assets/feature.html`.
#
# usage: render_graphics.sh [icon|feature|lockup|apple|favicon|all] [locale ...]  (default: all)
#
# `lockup` no es de la ficha de Play: es el IMAGOTIPO que pega la tarjeta social de la web
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
# ⛔ El alto es un argumento OPCIONAL y por defecto es el ancho, porque hasta el
# imagotipo todo lo que pasaba por aqui era cuadrado. El imagotipo es 5,484:1, y
# rasterizarlo en una caja cuadrada lo deja a una quinta parte del tamano con cuatro
# quintos de PNG vacio -o aplastado, si se le fuerzan los dos lados-.
svg_shot() { # svg_shot <repo-relative svg> <width> <out-win-path> [height]
  local H="${4:-$2}"
  printf '%s\n' \
    '<!doctype html><meta charset="utf-8">' \
    "<style>html,body{margin:0;padding:0;background:transparent}" \
    "img{display:block;width:$2px;height:${H}px}</style>" \
    "<img src=\"file:///$WINREPO/$1\">" \
    > "$TMP/svg_shot.html"
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
    --force-device-scale-factor=1 --virtual-time-budget=2000 \
    --default-background-color=00000000 \
    --window-size="$2,$H" --screenshot="$3" "file:///$WINTMP/svg_shot.html" >/dev/null 2>&1
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

if [ "$WHAT" = favicon ] || [ "$WHAT" = all ]; then
  # El icono de pestana [WEB-THE-FAVICON-IS-THE-ONE-ASSET-THAT-CAN-GO-STALE-001].
  #
  # El .ico lleva TRES tamanos dentro (16/32/48) porque eso es lo que el formato permite y lo que
  # los navegadores eligen segun el contexto: 16 en la pestana, 32 en la barra de marcadores, 48
  # en accesos directos del escritorio. Rasterizar uno y dejar que escalen da bordes sucios a 16.
  echo "favicon (.ico 16/32/48 + baldosas 192/512 del manifest)"
  for px in 16 32 48; do
    svg_shot docs/branding/apple/favicon-ico.svg "$px" "$WINTMP/fav-$px.png"
  done
  svg_shot docs/branding/apple/web-icon-192.svg 192 "$WINTMP/web-icon-192.png"
  svg_shot docs/branding/apple/web-icon-512.svg 512 "$WINTMP/web-icon-512.png"

  python - "$TMP" "$REPO" <<'PY'
import sys
from PIL import Image

tmp, repo = sys.argv[1], sys.argv[2]


def mark_share(im):
    """Que fraccion del icono pinta la marca. 0 % = render vacio."""
    px = im.load()
    step = max(1, im.width // 64)
    mark = tot = 0
    for y in range(0, im.height, step):
        for x in range(0, im.width, step):
            r, g, b, a = px[x, y]
            tot += 1
            if a > 128 and g > 90 and r < 140 and g - b > 30:
                mark += 1
    return 100.0 * mark / tot


# El .ico: los tres tamanos en UN fichero. Pillow los guarda desde la imagen mas grande.
frames = []
for px in (16, 32, 48):
    im = Image.open("%s/fav-%d.png" % (tmp, px)).convert("RGBA")
    if im.size != (px, px):
        sys.exit("  favicon %d: %dx%d, se esperaba %dx%d" % (px, im.size[0], im.size[1], px, px))
    share = mark_share(im)
    if share < 10.0:
        sys.exit("  favicon %d: solo %.1f%% de marca - el glifo no esta" % (px, share))
    frames.append(im)
    print("  favicon %-3d  marca %.0f%%" % (px, share))

ico = "%s/hosting/public/favicon.ico" % repo
frames[-1].save(ico, format="ICO", sizes=[(16, 16), (32, 32), (48, 48)])
# Se vuelve a ABRIR para comprobar que el fichero escrito trae de verdad los tres tamanos:
# guardar sin error no prueba que esten dentro.
with Image.open(ico) as check:
    got = sorted(check.info.get("sizes", []))
    if got != [(16, 16), (32, 32), (48, 48)]:
        sys.exit("  favicon.ico: trae %s, se esperaban los tres tamanos" % (got,))
print("  favicon.ico  16/32/48 dentro de un solo fichero")

for px in (192, 512):
    im = Image.open("%s/web-icon-%d.png" % (tmp, px)).convert("RGBA")
    if im.size != (px, px):
        sys.exit("  icon-%d: %dx%d" % (px, im.size[0], im.size[1]))
    share = mark_share(im)
    if share < 4.0:
        sys.exit("  icon-%d: solo %.1f%% de marca" % (px, share))
    flat = Image.new("RGB", im.size, (0, 0, 0))
    flat.paste(im, mask=im.split()[-1])
    dst = "%s/web/assets/img/icon-%d.png" % (repo, px)
    flat.save(dst, "PNG", optimize=True)
    print("  icon-%-3d     %dx%d  marca %.0f%%" % (px, px, px, share))
PY
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

if [ "$WHAT" = lockup ] || [ "$WHAT" = all ]; then
  # El IMAGOTIPO de la web: simbolo y nombre, un solo dibujo. Se rasteriza CON ALFA
  # porque la tarjeta social compone encima de su propio fondo.
  #
  # Aqui se rasterizaba el EMBLEMA -la marca sobre un disco verde- y `prepare_social()`
  # le escribia "Paparcar" al lado con Pillow: dos piezas y dos criterios para una sola
  # marca. Ahora el nombre viaja DENTRO del PNG, contorneado, asi que la tarjeta no
  # necesita tener la fuente ni decidir el tamano del texto
  # [BRAND-THE-MARK-GAINS-ITS-WORDMARK-001].
  #
  # Antes de eso, `prepare_social()` pegaba `play-icon-512.png`, el icono de OTRA
  # superficie -una baldosa opaca-, y por eso la tarjeta arrastro la marca de 2022 dentro
  # de un JPEG mientras el resto del repo ya tenia la de 2026. Un PNG generado si vale;
  # un PNG prestado de otra superficie, no.
  # ⛔ El alto SE DERIVA del viewBox del SVG. Estuvo cableado a 187 y duro exactamente
  # hasta que el lockup cambio de relacion al quitarle el margen de exportacion: el
  # <img> lleva los dos lados forzados, asi que un alto viejo no recorta ni avisa, ESTIRA
  # el logo y el PNG sigue teniendo el tamano que se pidio.
  # [BRAND-THE-IMAGOTIPO-MUST-FILL-ITS-WIDTH-001]
  LOCKUP_W=1024
  LOCKUP_H=$(sed -n 's/.*viewBox="0 0 \([0-9.]*\) \([0-9.]*\)".*/\1 \2/p' \
    "$REPO/docs/assets/paparcar-imagotipo-dark.svg" | head -1 \
    | awk -v w=$LOCKUP_W '{printf "%d", (w*$2/$1)+0.5}')
  [ -n "$LOCKUP_H" ] && [ "$LOCKUP_H" -gt 0 ] || { echo "  imagotipo: no pude leer el viewBox"; exit 1; }
  echo "imagotipo de la web (${LOCKUP_W}x${LOCKUP_H}, con alfa)"
  svg_shot docs/assets/paparcar-imagotipo-dark.svg "$LOCKUP_W" "$WINTMP/lockup.png" "$LOCKUP_H"
  # Se COMPRUEBA el contenido, no el exit code ni el tamano: un PNG del tamano pedido
  # puede estar vacio, y ya paso una vez con estos mismos graficos.
  python - "$TMP/lockup.png" "$REPO/docs/assets/paparcar-imagotipo-1024.png" \
      "$LOCKUP_W" "$LOCKUP_H" <<'PY'
import sys
from PIL import Image

# Se mide por PROPORCIONES y por POSICION, no sondeando pixeles concretos: acertar
# donde cae un trazo es fragil -el centroide de la tinta del pin cae dentro de su ojo,
# que es hueco, y asi fallo el primer intento de la version con disco-.
#
# Lo que se comprueba aqui es justo lo que el imagotipo puede romper y un PNG del
# tamano correcto no delataria: que esten LAS DOS piezas y que esten en su sitio. El
# verde a la izquierda y el casi-blanco a su derecha, sin solaparse. Un lockup al que
# le falte la palabra, o cuyos dos grupos caigan uno encima del otro -lo que pasaria
# si los dos translate salieran iguales-, muere aqui.
src, dst = sys.argv[1], sys.argv[2]
want = (int(sys.argv[3]), int(sys.argv[4]))
im = Image.open(src).convert("RGBA")
W, H = im.size
if (W, H) != want:
    sys.exit("  imagotipo: %dx%d, se esperaba %dx%d" % (W, H, want[0], want[1]))
px = im.load()
if px[2, 2][3] != 0:
    sys.exit("  imagotipo: la esquina no es transparente, falta el alfa")
green = white = clear = 0
gx = wx = 0
for y in range(0, H, 2):
    for x in range(0, W, 2):
        r, g, b, a = px[x, y]
        if a < 128:
            clear += 1
        elif g > 180 and r < 120:
            green += 1
            gx += x
        elif min(r, g, b) > 190:
            white += 1
            wx += x
tot = (W // 2) * (H // 2)
if green == 0:
    sys.exit("  imagotipo: ni un pixel verde, el simbolo no esta")
if white == 0:
    sys.exit("  imagotipo: ni un pixel casi-blanco, la PALABRA no esta")
gc, wc = gx / green / W, wx / white / W
if gc > 0.16:
    sys.exit("  imagotipo: el verde esta centrado en %.0f%% del ancho, se esperaba a la "
             "izquierda (<16%%)" % (100 * gc))
if wc < 0.45:
    sys.exit("  imagotipo: la palabra esta centrada en %.0f%% del ancho, se esperaba a la "
             "derecha del simbolo (>45%%)" % (100 * wc))
# Medido 0,59 sobre el lockup YA RECORTADO (antes 0,67, con el margen de exportacion
# dentro). El suelo no persigue ese numero: lo que caza es un PNG con fondo opaco, que
# daria ~0. Con holgura, para que recortar o acercar las piezas no lo rompa de nuevo.
if clear / tot < 0.45:
    sys.exit("  imagotipo: solo %.0f%% transparente; un lockup deja mucho aire, esto parece "
             "un fondo" % (100 * clear / tot))
im.save(dst, "PNG", optimize=True)
print("  paparcar-imagotipo-1024.png  %dx%d  RGBA  simbolo %.1f%% en x=%.0f%%, palabra "
      "%.1f%% en x=%.0f%%" % (W, H, 100 * green / tot, 100 * gc, 100 * white / tot, 100 * wc))
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
