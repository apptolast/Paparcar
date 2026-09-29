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

⛔ Y hay UNA excepcion, decidida por el user el 30-09: el grafico de funciones de
Play (`feature.html`) SI lleva disco detras de la marca
[PLAY-THE-BANNER-MARK-KEEPS-ITS-DISC-001]. Coherente con lo de arriba, no contra:
su fraccion es 0.52896 -un emblema dentro de una composicion ajena, con el
wordmark al lado-, mientras que en la app es F_MARK = 1.0, donde la marca ocupa lo
que ocupaba el disco y por eso no lo necesita. Este script no lo toca: reescribe
SOLO el grupo del glifo, y los dos <circle> del disco estan fuera de MARK_GROUP.

⛔ El verde no es uno, son dos, y la regla ya existia en el proyecto: sobre
oscuro `PapGreen` #25F48C; sobre claro `PapGreenLight` #009F5E. Y no es gusto:
#25F48C sobre blanco da 1,42:1 — medido. Es justo el sitio donde antes tapaba el
disco.
"""
import io
import os
import re

import build_marks

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
MARK = os.path.join(HERE, '..', 'mark', 'paparcar-mark-compas-b.svg')

GREEN = '#25F48C'          # PapGreen        — sobre oscuro
GREEN_LIGHT = '#009F5E'    # PapGreenLight   — sobre claro (3,01:1)
INK = '#0D1117'            # PapInk
MOCK = '#DDD6FE'           # violeta del Dev Catalog


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
    'docs/release/play-listing/assets/icon.html': (0.42894, 0.51458, 0.51458),
    'docs/release/play-listing/assets/feature.html': (0.52896,),
}

MARK_GROUP = re.compile(
    r'<g transform="translate\([\d.]+ [\d.]+\) scale\([\d.]+\)" fill="#25F48C">'
    r'(?:\s*<path d="[^"]*"/>){3}\s*</g>')


def play_group(fraction, indent):
    """El grupo del glifo, encuadrado por el circulo envolvente dentro del 108."""
    s = fraction * 108.0 / 2.0 / MEC_R
    L = ['<g transform="translate(%.4f %.4f) scale(%.5f)" fill="%s">'
         % (54 - MEC_C[0] * s, 54 - MEC_C[1] * s, s, GREEN)]
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

        io.open(p, 'w', encoding='utf-8', newline='\n').write(MARK_GROUP.sub(sub, text))
        print('  escrito  %s  (%d grupos)' % (rel, len(fracs)))


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
    return '\n'.join(L) + '\n'


def main():
    d = paths()
    print('Marca: %s (%d caracteres de path)' % (os.path.relpath(MARK, REPO), sum(len(x) for x in d)))
    print('       lienzo %.0fx%.0f · envolvente OPTICO r=%.4f en (%.4f, %.4f) — DERIVADOS del maestro'
          % (CANVAS_W, CANVAS_H, MEC_R, MEC_C[0], MEC_C[1]))
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

    print()
    print('Web y documentacion')
    write('hosting/public/favicon.svg',
          svg_mark('    Paparcar · favicon. GENERADO por docs/branding/master/export_surfaces.py.\n'
                   '    Sin disco. La pestaña del navegador no la controlamos, asi que el color lo\n'
                   '    decide prefers-color-scheme: verde de marca sobre chrome oscuro, verde\n'
                   '    profundo sobre claro.', themed=True))
    write('docs/assets/paparcar-logo.svg',
          svg_mark('    Paparcar · la marca, para documentacion. GENERADO.', GREEN))
    write('web/lib/mark.generated.mjs', mark_mjs())

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
