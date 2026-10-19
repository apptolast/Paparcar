"""Escribe la marca en TODAS las superficies del producto, desde el maestro.

    cd docs/branding/master && python export_surfaces.py

⛔ Esta es la pieza que faltaba. Antes de esto, `grep -rl "M92.28,57.77"` devolvia
25 ficheros con el path del logo escrito a mano dentro: seis VectorDrawable de
`:app`, cinco de `composeResources`, dos HTML de Play, el favicon, `icons.mjs`,
`docs/assets` y diez paginas generadas. Por eso la regla de compensacion optica de
las ruedas tuvo que documentarse como una pareja de numeros magicos en CLAUDE.md:
no habia de donde derivarlos.

Ahora hay UNA fuente. Si el dibujo cambia, se cambia en `build_marks.py` y se
vuelve a correr esto.

⛔⛔ SIN DISCO. La marca de 2022 necesitaba un disco oscuro detras para
sostenerse; la de 2026 no, y eso se nota en cada superficie:
  · `paparcar_logo` / `paparcar_logo_dark` dejan de existir: eran el glifo MAS el
    disco, y sin disco son exactamente `paparcar_iconmark_*`.
  · el small icon de notificacion deja de ser un knockout (disco con la forma
    recortada en evenOdd) y pasa a ser lo que Android espera: la silueta a secas.
  · el splash deja de hornear el disco.
Lo que NO se quita son los FONDOS de lienzo -el del icono adaptativo, el del
apple-touch-icon, el 512 de Play-: esos no son el disco de la marca, son la
baldosa que el sistema exige opaca.

⛔⛔ Y el disco ya no tiene NINGUNA excepcion: desde
BRAND-THE-MARK-GAINS-ITS-WORDMARK-001 esta borrado del repo. Hubo dos, las dos del
user -el banner de Play [PLAY-THE-BANNER-MARK-KEEPS-ITS-DISC-001] y la web
[WEB-THE-MARK-WEARS-ITS-DISC-ON-THE-SITE-001]-, y las dos se sostenian sobre UNA
razon medida: la marca a F_EMBLEM se quedaba en ~46 px al lado de un wordmark de 82
y FLOTABA. El disco era la muleta de una composicion que nadie habia disenado.

Ahora esa composicion existe y viene medida -el IMAGOTIPO: simbolo y palabra a
1:5,8 con 14 u de canal-, asi que la razon desaparecio y con ella el disco. Sus
tres consumidores (cabecera y pie de la web, banner, tarjeta social) eran
exactamente las tres superficies que componian simbolo + texto a mano, y ahora
las tres piden `lockup()`. Regla unica, sin excepciones que recordar:

    la marca sola -> ISOTIPO, sin disco.  La marca diciendo su nombre -> IMAGOTIPO.

⛔ La PALABRA no se dibuja aqui ni se puede derivar: es un entregable contorneado y
vive en `wordmark.py`, su fuente unica. Este script la compone, nunca la escribe.

⛔ El verde no es uno, son dos, y la regla ya existia en el proyecto: sobre
oscuro `PapGreen` #25F48C; sobre claro `PapGreenLight` #009F5E. Y no es gusto:
#25F48C sobre blanco da 1,42:1 — medido. Es justo el sitio donde antes tapaba el
disco.
"""
import io
import os
import re

NL = chr(10)

import build_marks
import wordmark

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
MARK = os.path.join(HERE, '..', 'mark', 'paparcar-mark-compas-b.svg')
WORD = os.path.join(HERE, '..', 'mark', 'paparcar-wordmark.svg')

GREEN = '#25F48C'          # PapGreen        — sobre oscuro
GREEN_LIGHT = '#009F5E'    # PapGreenLight   — sobre claro (3,01:1)
INK = '#0D1117'            # PapInk
MOCK = '#DDD6FE'           # violeta del Dev Catalog
# El casi-blanco de la PALABRA sobre oscuro. Viene del entregable y NO es un token
# del proyecto: el mas cercano, `PapSurfaceLight` #F4F7FC, es una superficie azulada
# con otro papel. El imagotipo es Nivel 3 -ilustracion- y Nivel 3 trae su color.
# Medido: 17,60:1 sobre `PapInk`, el espejo del 18,99:1 que da la tinta sobre blanco.
WORD_ON_DARK = '#F4F7F4'


def paths():
    """Los tres paths de la marca, en el orden en que se pintan.

    ⛔ Como se llama cada uno lo dice `build_marks.path_names()`, no una tupla de
    aqui: el orden de pintado cambia con la forma elegida y el nombre tiene que
    viajar con el orden.

    ⛔ Se lee del SVG de `mark/` PERO se comprueba contra lo que el maestro
    produce ahora mismo. El SVG es salida; si alguien lo ha editado a mano, o si
    build_marks.py ha cambiado y nadie lo ha vuelto a correr, las once superficies
    saldrian con un dibujo que ya no es el maestro -y ninguna fallaria al compilar.
    """
    svg = io.open(MARK, encoding='utf-8').read()
    fresh, _ = build_marks.build()
    assert svg == fresh, (
        '%s no coincide con build_marks.build(). El SVG es SALIDA: corre '
        '`python build_marks.py` antes de exportar.' % os.path.relpath(MARK, REPO))
    d = re.findall(r'<path d="([^"]+)"/>', svg)
    assert len(d) == 3, 'se esperaban 3 paths en %s' % MARK
    return d


def word_paths():
    """Los ocho glifos de la PALABRA, en orden de lectura.

    Mismo contrato que `paths()`, y por el mismo motivo: el SVG de `mark/` es
    SALIDA y se comprueba contra lo que `wordmark.py` produce ahora mismo. Si
    alguien lo edita a mano -o sustituye los glifos y no vuelve a correrlo- las seis
    superficies del imagotipo saldrian con una palabra que ya no es la del maestro, y
    ninguna fallaria al compilar.

    `wordmark.check()` ademas mide la caja: el lienzo del lockup se calcula con
    `WORD_W`, asi que una palabra mas ancha de lo declarado descentraria el
    imagotipo en las seis a la vez.
    """
    wordmark.check()
    svg = io.open(WORD, encoding='utf-8').read()
    assert svg == wordmark.build(), (
        '%s no coincide con wordmark.build(). El SVG es SALIDA: corre '
        '`python wordmark.py` antes de exportar.' % os.path.relpath(WORD, REPO))
    d = re.findall(r'<path d="([^"]+)"/>', svg)
    assert len(d) == 8, 'se esperaban 8 glifos en %s' % WORD
    return d


def write(rel, text):
    # ⛔ '--' es ILEGAL dentro de un comentario XML, y los VectorDrawable y los SVG
    # son XML. Un guion doble en una nota rompe el parseo de AAPT con un
    # "Failed to parse XML file" que no dice por que. Se corta aqui, no en la nota.
    if rel.endswith(('.xml', '.svg')):
        head = text.split('-->')[0]
        assert '--' not in head.replace('<!--', ''), 'guion doble en el comentario de %s' % rel
    p = os.path.join(REPO, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    io.open(p, 'w', encoding='utf-8', newline='\n').write(text)
    print('  escrito  %s' % rel)


def drop(rel):
    p = os.path.join(REPO, rel)
    if os.path.exists(p):
        os.remove(p)
        print('  BORRADO  %s' % rel)


# ── Encuadre ────────────────────────────────────────────────────────────────
#
# ⛔ El radio y el centro del circulo envolvente SE DERIVAN del maestro. Estuvieron
# escritos a mano (146,5917 en (124, 132,1435)) y son justo lo que no puede
# escribirse a mano: la RELACION entre la marca y el hueco que la recibe. Mueve un
# lobulo y esos dos numeros siguen encuadrando el dibujo anterior, en las once
# superficies a la vez, sin que nada falle al compilar. Es el mismo fallo que ya
# costo el splash del Pixel 8, una capa mas abajo.
#
# ⛔ Y el centro es el OPTICO, no el geometrico. El minimo envolvente da el circulo
# mas pequeno, pero su centro es el de la CAJA: como la marca lleva cabeza y rueda
# derecha a un lado y solo la pata al otro, su masa cae +12,6 u a la derecha -5,2 %
# del ancho- y las once superficies la sacaban escorada. `optical_frame()` fija el
# centro en el centroide de tinta y mide el radio desde ahi (151,58 contra 141,65):
# la marca queda centrada de masa y sale al 93,4 % del tamano anterior. Las dos
# cosas a la vez, de un solo criterio y sin un numero a mano.
_C = build_marks.Compas(**build_marks.PARAMS)
MEC_R, MEC_C = build_marks.optical_frame()
CANVAS_W, CANVAS_H = build_marks.canvas(_C)


def frame(fraction):
    """Lienzo centrado en el envolvente OPTICO de la marca, ocupando `fraction`.

    ⛔ El maestro es FULL BLEED: la marca toca los cuatro bordes de su lienzo y su
    circulo envolvente mide mas que el lado, un 117 % de el. Usarlo tal cual en una
    superficie tiene DOS consecuencias, y las dos se vieron en el Pixel 8:
      · sale al doble de tamano que la marca de 2022, que iba al 57 % de su caja;
      · y si la superficie enmascara a un circulo -el splash de Android lo hace-
        le corta las esquinas y la deja irreconocible.
    Por eso cada superficie declara que fraccion de su caja ocupa el envolvente.
    """
    V = 2 * MEC_R / fraction
    return V, V / 2 - MEC_C[0], V / 2 - MEC_C[1]


# ── Cuanto ocupa la marca DENTRO de un circulo visible ──────────────────────
#
# 🔑 Un solo numero para la unica pregunta que se repite: *la marca metida en un
# circulo*. Launcher, splash y la cresta de Plus lo heredan, asi que no pueden
# desalinearse entre si.
#
# ⛔ NO sale de la safe zone de Android. Ese es el error que hubo que corregir:
# la safe zone (r 33 dp de 36) es el limite que ninguna mascara de OEM recorta, y
# tomarla por objetivo dejaba el envolvente en 0,917 del radio -o sea, pegado al
# borde-. El user lo vio en el lanzador antes que ninguna medida.
#
# Sale de MEDIR los iconos del mismo lanzador (radio envolvente del glifo entre
# radio de la baldosa, Pixel 8, 21-09-2026): Photos 0,740 · YouTube 0,786 ·
# Gmail 0,843 · Play Store 0,939 (la bolita que sobresale lo infla).
#
# El comparable honesto es Photos: glifo redondo y denso, como el nuestro. Y aun
# asi 0,70 se queda por DEBAJO de su 0,740, porque el molinete tiene aire entre
# aspa y aspa y el nuestro no: a igual envolvente, el nuestro pesa mas. 0,70 cae
# entre Photos y el propio launcher de 2022 -0,643, que sobrevive como escala
# optica en docs/release/play-listing/assets/icon.html-, y simulado sobre la
# captura real del lanzador es el que casa en peso con Photos.
#
# ⚠️ Al medir el lanzador, descontar el estado "recien instalado": encoge la
# baldosa de radio 90 a 72 y dibuja el aro claro en el anillo que libera. Medir
# ahi da una fraccion falsa (0,727 en vez de 0,917).
F_IN_CIRCLE = 0.70

# El splash de Android 12+ dibuja el icono a 288 dp y solo ENSENA los dos tercios
# interiores (192 dp), recortando en circulo: ESE es su circulo visible.
F_SPLASH = F_IN_CIRCLE * 192.0 / 288.0

# Encuadre del icono adaptativo: su circulo visible mide 72 dp.
ADAPTIVE_R_DP = F_IN_CIRCLE * 72.0 / 2.0     # 25,2 dp — holgado dentro de la safe zone
ADAPTIVE_SCALE = ADAPTIVE_R_DP / MEC_R
ADAPTIVE_TX = 54 - MEC_C[0] * ADAPTIVE_SCALE
ADAPTIVE_TY = 54 - MEC_C[1] * ADAPTIVE_SCALE
# Las tres iconmark las usan PaparcarLogo (96 dp), PaparcarIconmark (48) y
# PaparcarMark (24). Nadie las enmascara, asi que el envolvente llena la caja: sin
# disco detras, la marca TIENE que ocupar lo que ocupaba el disco o se ve enana.
F_MARK = 1.0
# Small icon: Android escala a 24 dp y espera algo de aire alrededor.
F_NOTIF = 0.92


# -- El IMAGOTIPO: la marca diciendo su nombre -------------------------------
#
# El caso contrario a F_MARK, y ahora el criterio cabe en una linea sin excepciones:
# *la marca sola es el ISOTIPO; la marca con su palabra al lado es el IMAGOTIPO*.
# Donde antes se componia simbolo + texto a mano -seis superficies, seis criterios,
# la palabra con tres tratamientos tipograficos distintos- va este lockup.
#
# ⛔ Aqui vivia el EMBLEMA: la marca sobre un disco, con dos pinturas segun el fondo.
# Esta borrado, y no por limpieza sino porque se quedo sin trabajo. Sus tres
# consumidores -cabecera y pie de la web, banner de Play, tarjeta social- eran
# exactamente las tres superficies que no tenian imagotipo que poner, y el disco era
# la muleta de eso: la marca a 0,52896 se quedaba en ~46 px junto a un wordmark de 82
# y flotaba. Con la composicion ya medida, no hay nada que apuntalar.
# [BRAND-THE-MARK-GAINS-ITS-WORDMARK-001] retira las dos excepciones del user
# [PLAY-THE-BANNER-MARK-KEEPS-ITS-DISC-001] [WEB-THE-MARK-WEARS-ITS-DISC-ON-THE-SITE-001].
#
# ⛔ Las TRES constantes estan MEDIDAS sobre el entregable del user
# (`reference/paparcar-imagotipo-2026.svg`), no elegidas aqui. Y el lienzo NO se
# declara: se DERIVA de ellas mas el maestro, que es lo que hace que mover un lobulo
# en build_marks.py re-encuadre el imagotipo en las seis superficies a la vez.
# La prueba de que el modelo reproduce el entregable es el ancho:
#   9 + 45,43377 + 14,03723 + 262,20469 + 9 = 339,67569   contra 339.676 del fichero.
BOX = 108.0                 # lienzo cuadrado de las baldosas y de los graficos de Play
LOCKUP_PAD = 9.0            # aire alrededor del conjunto
LOCKUP_GAP = 14.03723       # el canal entre simbolo y palabra
LOCKUP_MARK_H = 43.94414    # alto de TINTA del simbolo (236 u del maestro x 0,18620398)

# La geometria del imagotipo es UNA; la PINTURA la decide el fondo que lo recibe, y
# es contraste medido, no gusto:
#   · sobre oscuro -> simbolo PapGreen, palabra en el casi-blanco del entregable.
#     17,60:1 sobre PapInk.
#   · sobre claro  -> simbolo PapGreenLight, palabra en PapInk. 18,99:1 sobre blanco.
# El verde no se decide aqui: PapGreen sobre blanco da 1,42:1, y por eso existe
# PapGreenLight [BRAND-A-NEW-MARK-EARNS-ITS-SURFACES-001]. Lo que se anade es la
# tinta de la palabra, que sale de un token que ya existia.
LOCKUP_DARK = {'mark': GREEN, 'word': WORD_ON_DARK}
LOCKUP_LIGHT = {'mark': GREEN_LIGHT, 'word': INK}


def lockup_box():
    """Lienzo del imagotipo y la escala del simbolo. DERIVADOS, no declarados.

    Devuelve `(W, H, escala del simbolo, ancho del simbolo)`. El alto lo fija el
    elemento mas alto -el simbolo, 43,94 contra los 42 de la palabra- mas el aire.
    """
    s = LOCKUP_MARK_H / CANVAS_H
    mw = CANVAS_W * s
    W = 2 * LOCKUP_PAD + mw + LOCKUP_GAP + wordmark.WORD_W
    H = 2 * LOCKUP_PAD + max(LOCKUP_MARK_H, wordmark.WORD_H)
    return W, H, s, mw


def lockup_parts():
    """Las dos piezas ya colocadas: `(transform del simbolo, transform de la palabra)`.

    Los dos van CENTRADOS en el eje del lienzo. El entregable deja la palabra
    exactamente centrada (10,0) y el simbolo a 9, a 0,028 del centro -tres
    centesimas, por debajo de lo que cualquier rasterizador distingue-, asi que
    centrar los dos reproduce el original y ademas es la regla que se puede explicar.
    """
    W, H, s, mw = lockup_box()
    mark = (LOCKUP_PAD, (H - LOCKUP_MARK_H) / 2.0, s)
    word = (LOCKUP_PAD + mw + LOCKUP_GAP, (H - wordmark.WORD_H) / 2.0)
    return mark, word


# -- Baldosas: las superficies que el SISTEMA enmascara ----------------------
#
# Ni disco ni emblema. Una baldosa es lo que el OS exige opaco y luego recorta a
# su gusto -cuadrado redondeado en iOS, circulo o squircle en Android-, y CLAUDE.md
# ya la excepciona: *no es el disco de la marca, es la baldosa que el sistema exige*.
# Lo que comparten con el resto de superficies es el ENCUADRE, no el soporte.
#
# ⛔ Ninguna de estas fracciones se elige aqui. Las dos estan MEDIDAS:
#   · F_STORE_ICON es la del icono de Play vivo, que ya estaba declarada en
#     PLAY_FRACTIONS. El icono de iOS la reutiliza tal cual: es la misma app en dos
#     tiendas, y dos tamanos opticos distintos no tendrian a que responder.
#   · F_TOUCH sale de MEDIR el apple-touch-icon que el user aprobo (bbox del verde
#     110x109 px sobre 180), corregida por el sesgo del propio metodo -0,2 %, medido
#     sobre el icono de Play, cuya fraccion se conoce-. Es mas grande que la de
#     tienda a proposito: ese icono no compite con otros en una rejilla de store.
F_STORE_ICON = 0.51458
F_TOUCH = 0.7595

# Fondo de cada baldosa. `None` = SIN fondo: iOS 18 pone su propio telon detras de
# la variante oscura y tine la variante monocroma, asi que una baldosa propia
# competiria con el sistema en vez de acompanarlo.
TILE_STORE = {'bg': ('#141918', '#0B0F0E'), 'mark': GREEN}   # el degradado de icon.html
TILE_IOS_DARK = {'bg': None, 'mark': GREEN}
TILE_IOS_TINTED = {'bg': None, 'mark': '#E8EDEB'}            # gris claro; el sistema lo tine
TILE_TOUCH = {'bg': (INK, INK), 'mark': GREEN}               # PapInk PLANO, no el degradado


def tile_svg(note, paint, fraction, size):
    """Una baldosa cuadrada con la marca encuadrada dentro.

    El lienzo es 108 como el de los graficos de Play, para que las fracciones de
    los dos sean directamente comparables; `size` solo fija a que pixeles se
    rasteriza.
    """
    tx, ty, sc = box_transform(fraction)
    L = ['<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" '
         'viewBox="0 0 %g %g">' % (size, size, BOX, BOX),
         '  <!--', note, '  -->']
    bg = paint['bg']
    if bg:
        top, bottom = bg
        if top == bottom:
            L.append('  <rect width="%g" height="%g" fill="%s"/>' % (BOX, BOX, top))
        else:
            L += ['  <defs>',
                  '    <linearGradient id="tile" x1="%g" y1="0" x2="%g" y2="%g" '
                  'gradientUnits="userSpaceOnUse">' % (BOX / 2, BOX / 2, BOX),
                  '      <stop offset="0" stop-color="%s"/>' % top,
                  '      <stop offset="1" stop-color="%s"/>' % bottom,
                  '    </linearGradient>',
                  '  </defs>',
                  '  <rect width="%g" height="%g" fill="url(#tile)"/>' % (BOX, BOX)]
    L.append('  <g transform="translate(%.4f %.4f) scale(%.5f)" fill="%s">'
             % (tx, ty, sc, paint['mark']))
    L += ['    <path d="%s"/>' % x for x in paths()]
    L += ['  </g>', '</svg>']
    return NL.join(L) + NL


def box_transform(fraction, box=BOX):
    """`translate` + `scale` que centran la marca en un lienzo cuadrado de lado `box`.

    `fraction` es el diametro del circulo envolvente OPTICO sobre el lado. Lo usan
    las baldosas y el icono de Play, cada uno con su fraccion medida.

    ⛔ `fraction` ya no tiene valor por defecto. Lo tuvo -`F_EMBLEM`, la del
    emblema- y era el unico sitio del script donde el tamano optico de una
    superficie podia salir de un descuido en vez de de una medida.
    """
    s = fraction * box / 2.0 / MEC_R
    return box / 2 - MEC_C[0] * s, box / 2 - MEC_C[1] * s, s


# ── VectorDrawable ──────────────────────────────────────────────────────────
def vd(note, color, width=248, height=248, group=None, fraction=None):
    d = paths()
    if fraction is not None:
        vp, tx, ty = frame(fraction)
        group = (1.0, 1.0, tx, ty)
    else:
        vp = max(CANVAS_W, CANVAS_H) if group is None else 108
    L = []
    L.append('<?xml version="1.0" encoding="utf-8"?>')
    L.append('<!--')
    L.append(note)
    L.append('-->')
    L.append('<vector xmlns:android="http://schemas.android.com/apk/res/android"')
    L.append('    android:width="%ddp"' % width)
    L.append('    android:height="%ddp"' % height)
    L.append('    android:viewportWidth="%.3f"' % vp)
    L.append('    android:viewportHeight="%.3f">' % vp)
    L.append('')
    ind = '    '
    if group:
        L.append('    <group')
        L.append('        android:scaleX="%.5f"' % group[0])
        L.append('        android:scaleY="%.5f"' % group[1])
        L.append('        android:translateX="%.4f"' % group[2])
        L.append('        android:translateY="%.4f">' % group[3])
        ind = '        '
    for name, path in zip(build_marks.path_names(build_marks.CHOSEN), d):
        L.append('%s<!-- %s -->' % (ind, name))
        L.append('%s<path' % ind)
        L.append('%s    android:fillColor="%s"' % (ind, color))
        L.append('%s    android:pathData="%s" />' % (ind, path))
        L.append('')
    if group:
        L.append('    </group>')
    L.append('</vector>')
    return '\n'.join(L) + '\n'


ADAPT = (ADAPTIVE_SCALE, ADAPTIVE_SCALE, ADAPTIVE_TX, ADAPTIVE_TY)

NOTE_ADAPT = """  Paparcar · capa foreground del icono adaptativo. GENERADO por
  docs/branding/master/export_surfaces.py — no editar a mano.

  El <group> lleva el envolvente OPTICO de la marca a radio 25,2 dp sobre el
  lienzo 108: el 70 % del circulo visible del icono adaptativo (72 dp), holgado
  dentro de la safe zone de r 33 dp que no recorta ninguna mascara de OEM
  (circulo, squircle, redondeado, teardrop).

  ⛔ OPTICO, no geometrico: el centro del grupo es el centroide de TINTA, no el
  del circulo minimo. La marca lleva cabeza y rueda derecha a un lado y solo la
  pata al otro, asi que centrarla por geometria la dejaba escorada a la derecha.

  Sin disco. La marca de 2022 llevaba uno detras para sostenerse; esta no lo
  necesita. El fondo de la baldosa lo pone ic_launcher_background."""

NOTE_SPLASH_DAY = """  Paparcar · icono del splash, variante DIA. GENERADO — no editar a mano.

  El fondo del splash es @color/splash_background, que en dia vale #F0F4FB. Sobre
  ese claro, PapGreen (#25F48C) da 1,42:1 — invisible. Por eso el glifo va en
  PapGreenLight (#009F5E, 3,01:1), que es el verde que el proyecto ya usa como
  RELLENO en tema claro.

  Ya no se hornea el disco. Antes hacia falta porque el glifo de 2022 no se
  sostenia solo sobre el fondo del splash; este si."""

NOTE_SPLASH_NIGHT = """  Paparcar · icono del splash, variante NOCHE. GENERADO — no editar a mano.

  Fondo #0D1117: aqui manda PapGreen. Sin disco, igual que la variante dia."""

NOTE_NOTIF = """  Paparcar · small icon de notificacion y de la barra de estado. GENERADO — no
  editar a mano. 17 usos en AppNotificationManagerImpl + el widget.

  ⛔ Ya NO es un knockout. La version de 2022 era un disco solido con la forma
  recortada en evenOdd, y existia porque el coche suelto no se leia en la barra.
  Esta marca si: asi que es lo que Android espera de un small icon, una silueta
  plana que el sistema tinta. El color de aqui es irrelevante — lo pisa el tinte
  del sistema-; se deja blanco por convencion."""

NOTE_MOCK = """  Dev Catalog (flavor mock) · foreground del icono adaptativo. GENERADO.
  Mismo encuadre que produccion, violeta en vez de verde para distinguir el APK
  de pruebas en el cajon de aplicaciones."""


# ── SVG para web y documentacion ────────────────────────────────────────────
def svg_mark(note, color=None, themed=False, fraction=F_MARK):
    d = paths()
    V, tx, ty = frame(fraction)
    fill = color or GREEN
    style = []
    if themed:
        # El favicon vive en una pestana que no controlamos: verde de marca sobre
        # chrome oscuro, verde profundo sobre claro. Sin disco y sin JS.
        fill = GREEN_LIGHT
        style = ['  <style>',
                 '    @media (prefers-color-scheme: dark) { path { fill: %s; } }' % GREEN,
                 '  </style>']
    L = ['<svg xmlns="http://www.w3.org/2000/svg" width="%.0f" height="%.0f" '
         'viewBox="%.3f %.3f %.3f %.3f" fill="%s">' % (V, V, -tx, -ty, V, V, fill),
         '  <!--', note, '  -->'] + style
    L += ['  <path d="%s"/>' % p for p in d]
    L.append('</svg>')
    return '\n'.join(L) + '\n'


def lockup_groups(paint, indent='  ', classed=False):
    """Los dos grupos del imagotipo -simbolo y palabra- ya colocados y pintados.

    Los comparten el SVG suelto, el VectorDrawable y el banner de Play, para que no
    puedan desalinearse: si el maestro se mueve, se mueven los tres o ninguno.

    `classed` le pone `class="mark"` / `class="word"` a cada grupo, que es lo que
    necesita la variante temada para repintarlos desde un `<style>`.
    """
    (mx, my, ms), (wx, wy) = lockup_parts()
    cm = ' class="mark"' if classed else ''
    cw = ' class="word"' if classed else ''
    L = ['%s<g%s transform="translate(%.4f %.4f) scale(%.6f)" fill="%s">'
         % (indent, cm, mx, my, ms, paint['mark'])]
    L += ['%s  <path d="%s"/>' % (indent, d) for d in paths()]
    L.append('%s</g>' % indent)
    L.append('%s<g%s transform="translate(%.4f %.4f)" fill="%s">'
             % (indent, cw, wx, wy, paint['word']))
    L += ['%s  <path d="%s"/>' % (indent, d) for d in word_paths()]
    L.append('%s</g>' % indent)
    return L


def lockup_svg(note, paint=None, themed=False, width=None):
    """El IMAGOTIPO como fichero suelto. GENERADO.

    `themed=True` deja que `prefers-color-scheme` elija la variante, para las dos
    superficies cuyo tema no controlamos: el README -que se lee en GitHub- y
    cualquier visor de SVG. El `fill` base es el de la variante CLARA, asi que si un
    sanitizador se come el `<style>` el peor caso es la variante clara en los dos
    temas -legible en ambos-, nunca el 1,42:1 de PapGreen sobre blanco. Es la misma
    apuesta que ya acepta el favicon.

    ⛔ El lienzo NO es cuadrado (5,48:1) y el dibujo lo llena. Meterlo en una caja
    cuadrada lo encoge a una quinta parte o lo deforma, asi que `width` fija el ancho
    y el alto SALE de la relacion, nunca al contrario.
    """
    W, H, _s, _mw = lockup_box()
    paint = paint or (LOCKUP_LIGHT if themed else LOCKUP_DARK)
    w = width or W
    L = ['<svg xmlns="http://www.w3.org/2000/svg" width="%.3f" height="%.3f" '
         'viewBox="0 0 %.5f %.5f">' % (w, w * H / W, W, H),
         '  <!--', note, '  -->']
    if themed:
        L += ['  <style>',
              '    @media (prefers-color-scheme: dark) {',
              '      .mark { fill: %s; } .word { fill: %s; }' % (GREEN, WORD_ON_DARK),
              '    }',
              '  </style>']
        L += lockup_groups(LOCKUP_LIGHT, classed=True)
    else:
        L += lockup_groups(paint)
    L.append('</svg>')
    return NL.join(L) + NL


def lockup_svg_inline(paint, indent='', cls=None):
    """El IMAGOTIPO como elemento `<svg>` para incrustar en un HTML ajeno.

    Sin `width`/`height`: el tamano en px lo pone el CSS de la pagina que lo recibe,
    que es quien sabe en que composicion cae. Lo que viaja desde aqui es el `viewBox`
    -o sea la RELACION, lo que impide que alguien aplaste el logo- y el dibujo.
    """
    W, H, _s, _mw = lockup_box()
    c = ' class="%s"' % cls if cls else ''
    L = ['<svg%s viewBox="0 0 %.5f %.5f" xmlns="http://www.w3.org/2000/svg">' % (c, W, H)]
    L += lockup_groups(paint, indent='  ')
    L.append('</svg>')
    return (NL + indent).join(L)


def lockup_vd(note, paint, width=170):
    """El IMAGOTIPO como VectorDrawable, para `composeResources`.

    ⛔ Dos `fillColor` distintos en el mismo drawable, y a proposito: el imagotipo es
    Nivel 3 -ilustracion, trae su color- asi que NO se tine desde el call site. Un
    `ColorFilter.tint` encima lo dejaria de un solo color y se perderia justo lo que
    distingue el simbolo de la palabra.

    ⛔ `viewportWidth` y `viewportHeight` NO son iguales: 5,48:1. El alto en dp sale
    de la relacion, no se declara, porque declararlo es como se aplasta un logo.
    """
    W, H, _s, _mw = lockup_box()
    # ⛔ El alto en dp es ENTERO y el viewport no, asi que redondear deforma. Con 168
    # dp el error era del 1,2 % -la relacion salia 5,419 contra 5,484- y eso es
    # exactamente lo que se ve como un logo ligeramente aplastado, sin que nada falle.
    # 170 lo deja en 0,004 %. No se elige mirando: se COMPRUEBA, para que cambiar este
    # ancho por comodidad no reintroduzca la deformacion en silencio.
    height = round(width * H / W)
    skew = abs((width / float(height)) / (W / H) - 1.0)
    assert skew < 0.002, (
        'width=%d dp da %d dp de alto: relacion %.4f contra %.4f del maestro (%.2f %% de '
        'deformacion). Elige un ancho cuyo alto caiga mas cerca de un entero.'
        % (width, height, width / float(height), W / H, 100 * skew))
    L = ['<?xml version="1.0" encoding="utf-8"?>',
         '<!--', note, '-->',
         '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
         '    android:width="%ddp"' % width,
         '    android:height="%ddp"' % height,
         '    android:viewportWidth="%.5f"' % W,
         '    android:viewportHeight="%.5f">' % H,
         '']
    (mx, my, ms), (wx, wy) = lockup_parts()
    L += ['    <group',
          '        android:scaleX="%.6f"' % ms,
          '        android:scaleY="%.6f"' % ms,
          '        android:translateX="%.4f"' % mx,
          '        android:translateY="%.4f">' % my]
    for name, d in zip(build_marks.path_names(build_marks.CHOSEN), paths()):
        L += ['        <!-- %s -->' % name,
              '        <path',
              '            android:fillColor="%s"' % paint['mark'],
              '            android:pathData="%s" />' % d,
              '']
    L += ['    </group>',
          '',
          '    <group',
          '        android:translateX="%.4f"' % wx,
          '        android:translateY="%.4f">' % wy]
    for name, d in zip(wordmark.glyph_names(), word_paths()):
        L += ['        <!-- %s -->' % name,
              '        <path',
              '            android:fillColor="%s"' % paint['word'],
              '            android:pathData="%s" />' % d,
              '']
    L += ['    </group>', '</vector>']
    return NL.join(L) + NL


# ── Graficos de Play ────────────────────────────────────────────────────────
#
# `icon.html` y `feature.html` no son plantillas -traen fondo, degradados y
# maquetacion propia-, pero el DIBUJO que llevan dentro si es nuestro. Iba a mano,
# y a la tercera edicion seguida quedo claro lo que eso es: el mismo agujero que
# el ticket existe para tapar, escondido en un fichero que nadie asocia a la marca.
# Aqui se reescribe SOLO el grupo del glifo, y su encuadre sale del maestro.
#
# ⛔ Las fracciones NO son nuevas: son las que ya tenian esos graficos, expresadas
# como diametro del envolvente sobre el lienzo 108. Se conservan a proposito — el
# icono de Play esta publicado y esto cambia el dibujo, no su tamano optico.
PLAY_FRACTIONS = {
    'docs/release/play-listing/assets/icon.html': (0.42894, F_STORE_ICON, F_STORE_ICON),
}

# El IMAGOTIPO del banner. Antes `feature.html` estaba en PLAY_FRACTIONS con la
# fraccion del emblema y ademas declaraba un disco; ahora lleva el lockup entero, asi
# que lo que se reescribe no es un grupo dentro de un SVG ajeno sino el SVG completo
# -con su viewBox, que se DERIVA del maestro y de la caja de la palabra-.
#
# ⛔ Se sustituye el elemento entero, `viewBox` incluido, y no solo los grupos de
# dentro: si el lienzo del imagotipo cambiase de relacion y el viewBox se quedase, el
# banner saldria recortado o con aire de sobra, y el HTML no fallaria en nada.
# El HTML y el CSS de alrededor siguen siendo a mano: este script escribe el DIBUJO.
PLAY_LOCKUP = re.compile(r'<svg class="lockup"[^>]*>.*?</svg>', re.S)
PLAY_LOCKUPS = {'docs/release/play-listing/assets/feature.html': LOCKUP_DARK}

MARK_GROUP = re.compile(
    r'<g transform="translate\([\d.]+ [\d.]+\) scale\([\d.]+\)" fill="#25F48C">'
    r'(?:\s*<path d="[^"]*"/>){3}\s*</g>')


def play_group(fraction, indent):
    """El grupo del glifo, encuadrado por el circulo envolvente dentro del 108."""
    tx, ty, sc = box_transform(fraction)
    L = ['<g transform="translate(%.4f %.4f) scale(%.5f)" fill="%s">' % (tx, ty, sc, GREEN)]
    L += ['%s  <path d="%s"/>' % (indent, p) for p in paths()]
    L.append('%s</g>' % indent)
    return '\n'.join(L)


def write_play():
    for rel, fracs in PLAY_FRACTIONS.items():
        p = os.path.join(REPO, rel)
        text = io.open(p, encoding='utf-8').read()
        found = MARK_GROUP.findall(text)
        assert len(found) == len(fracs), \
            '%s: %d grupos de marca, %d fracciones declaradas' % (rel, len(found), len(fracs))
        it = iter(fracs)

        def sub(m):
            indent = ' ' * (m.start() - text.rfind('\n', 0, m.start()) - 1)
            return play_group(next(it), indent)

        text = MARK_GROUP.sub(sub, text)
        io.open(p, 'w', encoding='utf-8', newline='\n').write(text)
        print('  escrito  %s  (%d grupos de marca)' % (rel, len(fracs)))

    for rel, paint in PLAY_LOCKUPS.items():
        p = os.path.join(REPO, rel)
        text = io.open(p, encoding='utf-8').read()
        found = PLAY_LOCKUP.findall(text)
        assert len(found) == 1, \
            '%s: %d <svg class="lockup">, se esperaba 1' % (rel, len(found))
        m = PLAY_LOCKUP.search(text)
        pad = ' ' * (m.start() - text.rfind(NL, 0, m.start()) - 1)
        # El ancho en px lo manda el HTML por CSS (.lockup), no este SVG: aqui solo
        # viajan la relacion y el dibujo.
        svg = lockup_svg_inline(paint, pad, cls='lockup')
        text = text[:m.start()] + svg + text[m.end():]
        io.open(p, 'w', encoding='utf-8', newline='\n').write(text)
        print('  escrito  %s  (imagotipo)' % rel)


# ── Web ─────────────────────────────────────────────────────────────────────
def mark_mjs():
    """El dibujo, como modulo para `web/lib/icons.mjs`.

    ⛔ `logoMark()` tenia los tres paths y el `viewBox` escritos dentro, con una
    nota que ya avisaba: *si cambia el dibujo, se regenera alli — no se reescribe
    aqui a mano*. Una nota no es un sistema: lo que hace falta es que no HAYA donde
    reescribirlo. La web es un consumidor mas de la marca, igual que el launcher.
    """
    d = paths()
    L = ['// GENERADO por docs/branding/master/export_surfaces.py — no editar a mano.',
         '// La marca es FULL BLEED: toca los cuatro bordes de su lienzo, y ese lienzo',
         '// NO es cuadrado. Quien la dibuje tiene que respetar la relacion.',
         'export const MARK_W = %g;' % CANVAS_W,
         'export const MARK_H = %g;' % CANVAS_H,
         'export const MARK_PATHS = [']
    for name, path in zip(build_marks.path_names(build_marks.CHOSEN), d):
        L.append('  // %s' % name)
        L.append("  '%s'," % path)
    L.append('];')

    # La PALABRA. La web no la re-deriva ni la escribe como texto: recibe los mismos
    # ocho contornos que el resto de superficies. Que sean contornos y no un <span>
    # es lo que hace que el logo no dependa de que la fuente haya cargado.
    L += ['',
          '// La PALABRA del imagotipo: ocho glifos CONTORNEADOS, no texto. Minuscula a',
          '// proposito — el logo escribe paparcar. Su caja NO es la del simbolo.',
          'export const WORD_W = %g;' % wordmark.WORD_W,
          'export const WORD_H = %g;' % wordmark.WORD_H,
          'export const WORD_PATHS = [']
    for name, path in zip(wordmark.glyph_names(), word_paths()):
        L.append('  // %s' % name)
        L.append("  '%s'," % path)
    L.append('];')

    # El IMAGOTIPO ya compuesto. La web recibe los dos `transform` calculados por la
    # MISMA funcion que compone el banner de Play y el VectorDrawable de la app, para
    # que las tres no puedan desalinearse.
    #
    # ⛔ Aqui vivian EMBLEM_DISC_FILL / EMBLEM_MARK_FILL / EMBLEM_TRANSFORM: el disco.
    # Lo llevaban la cabecera, el pie, las dos paginas legales y la tarjeta social, y
    # las cuatro eran simbolo + <span> compuestos a mano. Ahora las cuatro piden el
    # imagotipo, el disco se quedo sin un solo call site y esta borrado.
    W, H, _s, _mw = lockup_box()
    (mx, my, ms), (wx, wy) = lockup_parts()
    L += ['',
          '// El IMAGOTIPO: simbolo y palabra, ya colocados. Lo llevan la cabecera, el pie,',
          '// las dos paginas legales y la tarjeta social — es decir, todo sitio donde la',
          '// marca dice su nombre. El favicon no (glifo de pestana) ni el apple-touch-icon',
          '// (baldosa que el sistema exige opaca): alli la marca va sola.',
          '// ⛔ El lienzo NO es cuadrado: %.3f:1. Fija el ANCHO y deja que el alto salga de'
          % (W / H),
          '// la relacion; declarar los dos es como se aplasta un logo.',
          'export const LOCKUP_W = %.5f;' % W,
          'export const LOCKUP_H = %.5f;' % H,
          "export const LOCKUP_MARK_TRANSFORM = 'translate(%.4f %.4f) scale(%.6f)';"
          % (mx, my, ms),
          "export const LOCKUP_WORD_TRANSFORM = 'translate(%.4f %.4f)';" % (wx, wy),
          '',
          '// Las dos pinturas, y es contraste medido: 17,60:1 sobre PapInk y 18,99:1 sobre',
          '// blanco. El sitio es tinta plana, asi que usa la oscura; el README, temada.',
          "export const LOCKUP_DARK = { mark: '%s', word: '%s' };"
          % (LOCKUP_DARK['mark'], LOCKUP_DARK['word']),
          "export const LOCKUP_LIGHT = { mark: '%s', word: '%s' };"
          % (LOCKUP_LIGHT['mark'], LOCKUP_LIGHT['word'])]
    return '\n'.join(L) + '\n'


def main():
    d = paths()
    print('Marca: %s (%d caracteres de path)' % (os.path.relpath(MARK, REPO), sum(len(x) for x in d)))
    print('       lienzo %.0fx%.0f · envolvente OPTICO r=%.4f en (%.4f, %.4f) — DERIVADOS del maestro'
          % (CANVAS_W, CANVAS_H, MEC_R, MEC_C[0], MEC_C[1]))
    w = word_paths()
    W, H, s, mw = lockup_box()
    print('Palabra: %s (%d glifos, %d caracteres) · caja %g x %g'
          % (os.path.relpath(WORD, REPO), len(w), sum(len(x) for x in w),
             wordmark.WORD_W, wordmark.WORD_H))
    print('Imagotipo: lienzo %.5f x %.5f (%.3f:1) · simbolo x%.6f — DERIVADOS de las tres medidas'
          % (W, H, W / H, s))
    print()
    print('Android · :app')
    write('app/src/main/res/drawable/ic_launcher_foreground.xml', vd(NOTE_ADAPT, GREEN, 108, 108, ADAPT))
    write('app/src/main/res/drawable/splash_glyph.xml', vd(NOTE_SPLASH_DAY, GREEN_LIGHT, fraction=F_SPLASH))
    write('app/src/main/res/drawable-night/splash_glyph.xml', vd(NOTE_SPLASH_NIGHT, GREEN, fraction=F_SPLASH))
    write('app/src/main/res/drawable/ic_notification_logo.xml', vd(NOTE_NOTIF, '#FFFFFF', 24, 24, fraction=F_NOTIF))
    write('app/src/mock/res/drawable/ic_launcher_foreground.xml', vd(NOTE_MOCK, MOCK, 108, 108, ADAPT))

    print()
    print('Android · :shared (composeResources)')
    write('shared/src/commonMain/composeResources/drawable/paparcar_iconmark_green.xml',
          vd('  Paparcar · la marca en PapGreen. GENERADO — no editar a mano.\n'
             '  Variante de tema OSCURO. Sin disco: la marca se sostiene sola.', GREEN, fraction=F_MARK))
    write('shared/src/commonMain/composeResources/drawable/paparcar_iconmark_forest.xml',
          vd('  Paparcar · la marca en PapGreenLight (#009F5E). GENERADO.\n'
             '  Variante de tema CLARO: PapGreen sobre blanco da 1,42:1, medido.', GREEN_LIGHT, fraction=F_MARK))
    write('shared/src/commonMain/composeResources/drawable/paparcar_iconmark_white.xml',
          vd('  Paparcar · la marca en blanco, la variante TINTABLE. GENERADO.\n'
             '  Es la unica que se pinta con colorFilter desde el call site; las otras dos\n'
             '  traen su relleno y no se tiñen (Nivel 3 de CLAUDE.md).', '#FFFFFF', fraction=F_MARK))
    write('shared/src/commonMain/composeResources/drawable/paparcar_imagotipo_dark.xml',
          lockup_vd('  Paparcar · el IMAGOTIPO: la marca diciendo su nombre. GENERADO.\n'
                    '  Variante de tema OSCURO. Lo pide `PaparcarImagotipo`, que es lo que va\n'
                    '  donde antes se componia la marca mas un Text con el nombre al lado.\n'
                    '  Dos fillColor a proposito: Nivel 3 trae su color y no se tiñe.',
                    LOCKUP_DARK))
    write('shared/src/commonMain/composeResources/drawable/paparcar_imagotipo_light.xml',
          lockup_vd('  Paparcar · el IMAGOTIPO, variante de tema CLARO. GENERADO.\n'
                    '  El simbolo en PapGreenLight porque PapGreen sobre blanco da 1,42:1, y\n'
                    '  la palabra en PapInk: 18,99:1, el espejo del 17,60:1 de la variante\n'
                    '  oscura. Ninguno de los dos se elige aqui.',
                    LOCKUP_LIGHT))

    print()
    print('Web y documentacion')
    # El favicon se escribe DOS veces a proposito, y no es duplicidad:
    #   · `web/assets/img/` es la FUENTE, que `publishAssets()` publica con hash de
    #     contenido. Es lo que referencia el <link>, y es lo unico que garantiza que un
    #     cambio de dibujo llegue al usuario: URL nueva
    #     [WEB-THE-FAVICON-IS-THE-ONE-ASSET-THAT-CAN-GO-STALE-001].
    #   · `hosting/public/favicon.svg` es la copia de raiz, para quien la pida a pelo.
    favicon = svg_mark('    Paparcar · favicon. GENERADO por docs/branding/master/export_surfaces.py.\n'
                       '    Sin disco. La pestaña del navegador no la controlamos, asi que el color\n'
                       '    lo decide prefers-color-scheme: verde de marca sobre chrome oscuro,\n'
                       '    verde profundo sobre claro.', themed=True)
    write('web/assets/img/favicon.svg', favicon)
    write('hosting/public/favicon.svg', favicon)

    # ⛔ La fuente del .ico NO puede ser la de arriba: un .ico no entiende de
    # prefers-color-scheme, asi que necesita UN color que aguante las dos pestanas.
    # Medido: PapGreen da 1,31:1 sobre pestana clara -invisible- y una baldosa oscura se
    # funde con la pestana oscura (1,32:1). PapGreenLight es el unico que no se hunde en
    # ningun lado (3,08-3,43:1 en clara, 3,31-4,18:1 en oscura), y ademas es el fill BASE
    # que ya usa el favicon SVG: en pestana clara los dos salen identicos.
    write('docs/branding/apple/favicon-ico.svg',
          svg_mark('    Paparcar · fuente del favicon.ico. GENERADO.\n'
                   '    En PapGreenLight fijo: un .ico no puede ser tematico, y este es el\n'
                   '    unico verde que aguanta pestana clara y oscura a la vez.',
                   GREEN_LIGHT))

    # Baldosas de la web: aterrizan sobre un fondo ajeno (pantalla de inicio, lista de
    # apps), asi que llevan el suyo, igual que el icono de iOS y el de Play.
    for px in (192, 512):
        write('docs/branding/apple/web-icon-%d.svg' % px,
              tile_svg('    Paparcar · icono %d de la web (manifest). GENERADO.\n'
                       '    Baldosa, no glifo suelto: esto cae sobre el fondo de pantalla de\n'
                       '    alguien. Mismo fondo y misma fraccion que el icono de iOS.' % px,
                       TILE_STORE, F_STORE_ICON, px))
    # ⛔ El logo de la documentacion va TEMADO, por la misma razon que el favicon: el
    # README de la raiz se lee en GitHub, y el tema del lector no lo controlamos. Con
    # PapGreen horneado daba 1,42:1 sobre el blanco del tema claro — justo el caso para
    # el que existe PapGreenLight (3,01:1), y el unico ajuste por superficie que la
    # marca admite [BRAND-A-NEW-MARK-EARNS-ITS-SURFACES-001].
    # Lo declaro abierto DOCS-THE-README-MUST-MATCH-THE-TREE-001 y lo cierra
    # [BRAND-THE-README-MARK-NEEDS-ITS-LIGHT-GREEN-001].
    #
    # ⚠️ Degrada BIEN si el sanitizador de GitHub se come el `<style>`: el `fill` del
    # `<svg>` ya es el verde profundo, asi que el peor caso es verde profundo en los dos
    # temas — legible en ambos — y nunca el 1,42:1 de antes. Es la misma apuesta que ya
    # acepta el favicon, y no se puede comprobar desde aqui: hay que mirarlo en GitHub.
    write('docs/assets/paparcar-logo.svg',
          svg_mark('    Paparcar · la marca, para documentacion. GENERADO.\n'
                   '    El README se lee en GitHub y su tema no lo controlamos, asi que el color\n'
                   '    lo decide prefers-color-scheme: verde de marca sobre oscuro, verde\n'
                   '    profundo sobre claro. PapGreen sobre blanco da 1,42:1.', themed=True))
    # El imagotipo de la documentacion va TEMADO, por lo mismo que el logo de arriba:
    # el README se lee en GitHub y su tema no lo controlamos.
    write('docs/assets/paparcar-imagotipo.svg',
          lockup_svg('    Paparcar · el IMAGOTIPO, para documentacion. GENERADO.\n'
                     '    El README se lee en GitHub y su tema no lo controlamos, asi que el\n'
                     '    color lo decide prefers-color-scheme. Sustituye al par\n'
                     '    logo + h1 con el nombre escrito al lado.', themed=True))
    # La fuente rasterizable de la tarjeta social. Variante OSCURA porque esa tarjeta
    # es tinta plana, igual que el sitio. Antes aqui habia un EMBLEMA sobre disco y la
    # tarjeta le escribia "Paparcar" al lado con Pillow: dos piezas, dos criterios.
    write('docs/assets/paparcar-imagotipo-dark.svg',
          lockup_svg('    Paparcar · el IMAGOTIPO sobre oscuro, para rasterizar. GENERADO.\n'
                     '    Lo convierte en PNG `render_graphics.sh lockup` y lo pega la\n'
                     '    tarjeta social, que ya no escribe el nombre por su cuenta.',
                     LOCKUP_DARK))
    # La variante PLANA sobre claro. No la pide ninguna superficie del repo -las que
    # van sobre claro son temadas o VectorDrawable-, y existe porque un imagotipo que
    # solo se puede entregar en su version oscura obliga a quien lo necesite sobre
    # blanco a inventarse los colores, que es exactamente de donde venia el problema.
    write('docs/assets/paparcar-imagotipo-light.svg',
          lockup_svg('    Paparcar · el IMAGOTIPO sobre claro. GENERADO.\n'
                     '    Simbolo en PapGreenLight y palabra en PapInk: 18,99:1 sobre\n'
                     '    blanco. Para entregar la marca a cualquier soporte claro sin\n'
                     '    que nadie tenga que elegir los colores por su cuenta.',
                     LOCKUP_LIGHT))
    write('web/lib/mark.generated.mjs', mark_mjs())

    print()
    print('Apple (baldosas; las rasteriza `render_graphics.sh apple`)')
    write('docs/branding/apple/ios-app-icon.svg',
          tile_svg('    Paparcar · icono de la app iOS, variante CLARA. GENERADO.\n'
                   '    Mismo fondo y misma fraccion que el icono de Play: es la misma app en\n'
                   '    dos tiendas, asi que no hay dos tamanos opticos que justificar.',
                   TILE_STORE, F_STORE_ICON, 1024))
    write('docs/branding/apple/ios-app-icon-dark.svg',
          tile_svg('    Paparcar · icono de la app iOS, variante OSCURA (iOS 18). GENERADO.\n'
                   '    SIN fondo: el sistema pone su propio telon detras. Una baldosa propia\n'
                   '    competiria con el en vez de acompanarlo.',
                   TILE_IOS_DARK, F_STORE_ICON, 1024))
    write('docs/branding/apple/ios-app-icon-tinted.svg',
          tile_svg('    Paparcar · icono de la app iOS, variante TENIDA (iOS 18). GENERADO.\n'
                   '    Sin fondo y en gris claro: el color lo pone el usuario desde los ajustes\n'
                   '    del sistema, asi que aqui solo se entrega la forma.',
                   TILE_IOS_TINTED, F_STORE_ICON, 1024))
    write('docs/branding/apple/web-touch-icon.svg',
          tile_svg('    Paparcar · apple-touch-icon de la web (180). GENERADO.\n'
                   '    Fondo PapInk PLANO y marca grande (F_TOUCH), que es lo que esta\n'
                   '    publicado. Antes se derivaba del icono de Play y por eso correr\n'
                   '    prepare-assets.py lo encogia sin que nadie lo pidiera.',
                   TILE_TOUCH, F_TOUCH, 180))

    print()
    print('Graficos de Play (solo el grupo del glifo; el resto del HTML es a mano)')
    write_play()

    print()
    print('Muertos (se borran, no se regeneran)')
    # minSdk es 26, asi que mipmap-anydpi-v26/ic_launcher.xml gana SIEMPRE y los
    # PNG de respaldo no se cargan nunca. Eran diez ficheros de icono antiguo.
    for dpi in ('mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi'):
        drop('app/src/main/res/mipmap-%s/ic_launcher.png' % dpi)
        drop('app/src/main/res/mipmap-%s/ic_launcher_round.png' % dpi)
    # huerfano: ningun setLargeIcon en todo el repo, y ademas era el disco azul de 2022
    drop('app/src/main/res/drawable/ic_logo_color.xml')
    # el glifo + disco: sin disco son identicos a paparcar_iconmark_*
    drop('shared/src/commonMain/composeResources/drawable/paparcar_logo.xml')
    drop('shared/src/commonMain/composeResources/drawable/paparcar_logo_dark.xml')
    # El EMBLEMA y su PNG: la marca sobre un disco. Sus cuatro consumidores -cabecera
    # y pie de la web, paginas legales, tarjeta social- componian simbolo + texto a
    # mano, y ahora piden el imagotipo. Sin un solo call site
    # [BRAND-THE-MARK-GAINS-ITS-WORDMARK-001].
    drop('docs/assets/paparcar-emblem.svg')
    drop('docs/assets/paparcar-emblem-512.png')

    print()
    # Sin emoji: la consola de Windows va en cp1252 y un print con emoji revienta el
    # script ENTERO por UnicodeEncodeError -despues de haber escrito ya los ficheros,
    # que es la peor forma de fallar: parece que no se genero nada y si se genero.
    print('OJO, despues de esto: `node web/build.mjs` - hosting/public/*.html llevan el')
    print('   logo horneado y se regeneran desde web/lib/icons.mjs, que ya lee el maestro.')
    print('   Y los PNG de Play se re-renderizan con Chrome headless desde esos dos HTML')
    print('   (comando en docs/release/play-listing/README.md).')


if __name__ == '__main__':
    main()
