"""Genera la marca elegida y TODOS los ejemplos, desde el maestro parametrico.

    python build_marks.py

Escribe:
    ../mark/paparcar-mark-compas-b.svg        <- la ELEGIDA (la que exporta a producto)
    ../explorations/NN-<slug>.svg             <- los ejemplos, numerados en orden

La hoja de contactos y la comparacion viven ahora en `../propuesta.html`, que
escribe `dossier.py`: una sola pagina con la propuesta, los ejemplos y la Fluida.

⛔ Todo eso es SALIDA. Si hay que cambiar algo del dibujo se cambia AQUI un
parametro y se vuelve a generar: editar el SVG a mano devuelve el problema que
este maestro existe para resolver -un path que nadie puede tocar.

⛔ `mark/` y `explorations/` estan SEPARADAS a proposito, y antes no lo estaban:
las dos vivian en `candidate/`. Ese nombre se quedo mintiendo el dia que la marca
entro en produccion -lo que hay en `mark/` no es un candidato, es lo que
`export_surfaces.py` escribe en once superficies-, y un directorio cuyo nombre
miente es como un fichero de ejemplo acaba exportado por error.

⛔ Y los ejemplos son una LISTA, no una sucesion de ediciones. Cada vuelta del
dibujo anterior se quedaba solo en el historial de git, asi que comparar dos era
un `git show` y un visor: por eso las decisiones se discutian de memoria. Aqui una
variante es una fila de `VARIANTS` -sus parametros y por que existe- y todas se
regeneran a la vez, con el mismo codigo, desde los mismos parametros.
"""
import io, os, importlib
import compas, rebuild_lobe
importlib.reload(compas); importlib.reload(rebuild_lobe)
from compas import Compas

# ── El dibujo, en parametros ────────────────────────────────────────────────
# La cabeza NO se deforma nunca: es R84 con su ojo concentrico. Todo lo que ha
# cambiado desde la Compas original son TRASLACIONES y ningun cambio de escala.
DX = 8.0             # cuanto se van a la derecha cabeza y lobulo derecho
CHANNEL = 10.0       # UN solo hueco alrededor de la rueda derecha (media luna = ranura)
CAP_T = 10.0         # remate del anillo abierto del lobulo derecho
TIP_R = 7.0          # redondeo de la punta de la gota; 0 = pico


def spec(dx=DX, lob_y=192.0, tip_x=71.0, channel=CHANNEL, join='fillet',
         corner_r=8.0, r_cntr=50.0, r_hole=24.0, r_lob=56.0, right='crescent',
         tip_r=TIP_R, mirror=False, r_eye=20.0, fillet=34.0, cap_t=CAP_T):
    """Una variante entera en una llamada. `channel` es un numero -un solo hueco-
    o el par (media luna, ranura) para reproducir los dibujos que los tenian
    distintos.

    ⛔ Los DOS centros de lobulo se DERIVAN, no se escriben. Estaban a mano
    (`LC=(56, y)`, `RC=(192+dx, y)`) y ese 56 era `R_LOB` disfrazado: en cuanto una
    variante adelgaza la rueda, el dibujo deja de tocar el borde izquierdo y la
    cabeza deja de enrasar con la rueda derecha, sin que nada falle. Las dos
    posiciones son consecuencias de dos invariantes:
      · full bleed por la izquierda → LC.x = R_LOB
      · enrase derecho cabeza/rueda → RC.x = HC.x + R_HEAD - R_LOB
    """
    gap, slot = channel if isinstance(channel, tuple) else (channel, channel)
    hc_x, r_head = 164.0 + dx, 84.0
    return dict(
        params=dict(HC=(hc_x, 84.0), R_HEAD=r_head, R_CNTR=r_cntr, R_EYE=r_eye,
                    LC=(r_lob, lob_y), RC=(hc_x + r_head - r_lob, lob_y),
                    R_LOB=r_lob, R_HOLE=r_hole,
                    TIP_X=tip_x, FILLET=fillet, JOIN=join, CORNER_R=corner_r, TIP_R=tip_r),
        gap=gap, shelf_y=168.0 + slot, right=right, mirror=mirror, cap_t=cap_t)


# ⛔ La ELEGIDA es la COMPLETA y es la UNICA: la misma forma a todos los tamanos.
# Esto descarta las DOS reglas anteriores, no una: ni dos dibujos con umbral en 32 dp
# -completa arriba, compacta abajo-, ni la compacta para todo.
#
# 🔑 Y desde el 25-09 sus numeros NO son los que se eligieron a ojo: son los del
# RASTER del que salio todo -`prueba_paparcar_icon_compact.png`, el dibujo que se
# vectorizo a mano y del que nacio este maestro-. Ajustando los mandos que ya
# existian, el solape con ese raster sube de **0,875 a 0,960** (mismo marco,
# normalizado por altura, alineacion por centroide + busqueda fina). Lo que movio
# cada mando, medido: ojo r20 -> 21,5 - contraforma r50 -> 46 - agujeros r24 -> 23
# - rueda R56 -> 53 - canal 10 -> 8 - acuerdo 34 -> 18 - remate del anillo 10 -> 4
# - cabeza y rueda derecha 4 u a la IZQUIERDA (dx 8 -> -4) - ruedas 9 u mas ARRIBA
# (lob_y 192 -> 183).
#
# 🔑 Todo eso junto es UNA sola cosa: el paso a regla habia VACIADO el dibujo, no
# adelgazado. Contra el raster, el Compas B+ llevaba un 7 % menos de tinta, el trazo
# mediano un 15 % mas fino, la modulacion p90/p10 en 1,11 contra 1,33 y el canal un
# 51 % mas ancho. Con estos valores la marca vuelve a sus cifras: tinta 0,546 contra
# 0,543, grosor 0,160 contra 0,157, modulacion 1,28 contra 1,33.
#
# ⛔ Lo que NO se copia del raster, y cuanto cuesta: su canal es DESIGUAL (6,5 de un
# lado, 5 del otro) y su punta de gota va casi en pico. Igualar el canal y dejar
# la punta en r7 cuesta 0,0019 de solape -0,9615 -> 0,9596, o sea nada- y conserva
# las dos correcciones que este ticket compro. Un defecto de trazado no se hereda
# solo porque este en el original.
#
# 🔑 El canal acabo en 8 u -el user pidio «un poquito mas de espacio entre la rueda
# derecha y la cabeza, como en el Compas B+», que llevaba 10-. No es solo aire: a 6
# u la rueda derecha FUNDIA con el cuerpo por debajo de ~28 px y la marca salia en
# dos trozos; medido, basta con 7 para que aguante las TRES piezas hasta 20 px. A 8
# el hueco mide 0,034 H contra los 0,042 del B+, y el solape con el raster no se
# entera (0,885 contra 0,885).
#
# ✂️ Y un paso mas, pedido por el user al verla en el lanzador: *«el trazo algo mas
# fino, que no parezca tan mancha»*, y luego *«un poco mas»*. Se adelgaza SIN tocar
# la silueta -el contorno exterior no se mueve- abriendo los huecos: contraforma
# r46 -> 51, agujeros r23 -> 26,5, y `tip_x` 70,5 -> 65 para que la pata no engorde
# al crecer la contraforma (crece: con r_cntr +3 la pata pasa de 37,0 a 38,7 sola).
# Anillos 38/30/37 -> 33/26,5/35,3, grosor mediano -12,5 %, tinta -9,0 %.
# ⚠️ El primer paso prometido -r50 / r26- se midio y daba -1,6 %: invisible. Un paso
# que no se ve no es un paso; por eso la vuelta buena es esta.
# ⚠️ Esto ALEJA a proposito del raster: el solape baja de 0,960 a 0,885. La marca ya
# no es «el raster», es el raster menos un adelgazado pedido. Dicho aqui para que
# nadie lea el 0,885 como un ajuste que salio peor.
# 📏 Referencia para no pasarse: el Compas B+ anterior iba a 0,136 H de grosor
# mediano y esta va a 0,140. Un escalon mas (r52 / r27 / tip_x 63,5) la deja
# exactamente en el peso de aquella, que es el suelo de este camino.
CHOSEN = spec(dx=-4.0, lob_y=183.0, tip_x=65.0, channel=8.0, r_cntr=51.0,
              r_hole=26.5, r_lob=53.0, r_eye=21.5, fillet=18.0, cap_t=4.0)
# Compatibilidad con export_surfaces.py, que solo necesita la geometria elegida.
PARAMS = CHOSEN['params']
GAP, SHELF_Y = CHOSEN['gap'], CHOSEN['shelf_y']


# ── Los ejemplos, en orden ──────────────────────────────────────────────────
# Cada fila dice QUE cambia respecto a la anterior. Leidas en orden, son la
# historia del dibujo; miradas juntas en ../propuesta.html, son la comparacion.
VARIANTS = [
    ('compas-b', 'Compás B+ · la tangencia',
     'El techo de los agujeros cae en el fondo del cuenco (y=168). Deja el lienzo '
     'cuadrado y el hueco de la rueda derecha DESIGUAL: 7,39 en la media luna, 10 en la ranura.',
     spec(dx=0.0, tip_x=68.0, channel=(7.393, 10.0), tip_r=0.0)),

    ('ancho', '+ anchura',
     'Tangenciar costó 8 u de alto: se devuelven 8 u de ancho moviendo cabeza y rueda '
     'derecha a la derecha, juntas. La rueda izquierda no se toca. TIP_X 68 -> 71 o la pata adelgaza.',
     spec(channel=(7.393, 10.0), tip_r=0.0)),

    ('canal', '+ canal alineado',
     'Un solo hueco de 10 u alrededor de la rueda derecha: la media luna se abre de '
     '7,39 a 10 y la ranura ya estaba en 10. Antes el canal cambiaba de ancho un 35 % de un lado al otro.',
     spec(tip_r=0.0)),

    ('punta', '+ punta de la gota redondeada',
     'El pin que hace de contraforma acababa en PICO: dos rectas cortandose a 52,6 grados, '
     'el unico angulo vivo que le quedaba a una marca que es toda arcos. Ahora r7, y no queda ni uno. '
     'Fue la ELEGIDA hasta el 25-09, cuando se midió contra el ráster del que salió todo.',
     spec()),

    ('recta', '+ remate RECTO (de la Fluida)',
     'La rueda de abajo deja de fundirse en curva con el bajo del cuenco: la corta una '
     'recta tangente y el cuenco cierra en esquina. Es lo que hace que se lea como P y no como rueda.',
     spec(join='straight')),

    ('recta-viva', '+ remate recto, esquina VIVA',
     'La misma recta sin redondear la esquina contra el bajo. Para ver cuánto aporta '
     'el radio de 8: la marca presume de no tener ni una esquina sin radio.',
     spec(join='straight', corner_r=0.0)),

    ('espejo', 'Espejo horizontal',
     'La elegida reflejada: la cabeza pasa a la izquierda y la pata baja hacia la derecha, '
     'con lo que el peso se va al lado por el que se empieza a leer. ⛔ Invierte la P: el cuenco de '
     'una P va a la DERECHA del asta, así que esto ya no es una P — es la misma figura leyendo al revés.',
     spec(mirror=True)),

    ('fino', 'Trazo más estrecho',
     'Anillos 34/32/33 -> 28/28/28, casi monolineal: la contraforma sube a r56, la rueda baja a '
     'R52 y la punta de la gota a 61. Los huecos respiran y la tangencia no se mueve — el agujero '
     'sigue en r24, que es lo que la sostiene. El lienzo se acorta a 244 y la marca sale más ancha.',
     spec(r_cntr=56.0, r_lob=52.0, tip_x=61.0)),

    ('compacta', 'Compacta',
     'No es la grande reducida: es la misma marca CON MENOS COSAS. Sin media luna y sin '
     'repisa, la rueda derecha es un anillo entero fusionado con el cuerpo y las dos ruedas quedan '
     'gemelas. Nació como variante para <= 32 dp, se probó como marca única y se descartó al verla '
     'junto a la completa a 48 y a 24 px: aguanta mejor abajo, pero la P se lee más floja a todos '
     'los tamaños. ⛔ Esta fila apuntaba a CHOSEN y el día que la elegida volvió a ser la completa '
     'dejó de dibujar una compacta sin que fallara nada: el ejemplo era un duplicado del 04 con el '
     'texto de otra forma. Ahora lleva sus propios parámetros.',
     spec(lob_y=196.0, tip_x=77.0, r_cntr=46.0, r_hole=28.0, right='ring')),

    ('ancestro', '+ ajustada al ráster del que salió todo',
     'La misma construcción con los valores que reproducen el ráster ancestro: el solape '
     'sube de 0,875 a 0,960. Vacía menos -más tinta, trazo más gordo y modulado, canal más '
     'estrecho, ojo mayor- y conserva el canal igualado y la punta redondeada, que el original '
     'no tenía y cuestan 0,002 de solape.',
     spec(dx=-4.0, lob_y=183.0, tip_x=70.5, channel=6.0, r_cntr=46.0,
          r_hole=23.0, r_lob=53.0, r_eye=21.5, fillet=18.0, cap_t=4.0)),

    ('pico', 'La elegida con la punta SIN redondear',
     'La misma, con `tip_r=0`: la punta de la gota acaba en ángulo vivo, como en el ráster. '
     'Sube el solape con el original de 0,9596 a 0,9615 -0,002, nada- y a cambio la marca deja '
     'de tener cero esquinas vivas. Está aquí para mirarla, no cableada.',
     spec(dx=-4.0, lob_y=183.0, tip_x=70.5, channel=6.0, r_cntr=46.0,
          r_hole=23.0, r_lob=53.0, r_eye=21.5, fillet=18.0, cap_t=4.0, tip_r=0.0)),

    ('adelgazada', '+ el trazo, un paso más fino',
     'El ajuste devolvía el grosor del ráster y en el lanzador se leía como mancha. Se adelgaza '
     'SIN tocar la silueta -el contorno exterior no se mueve-: se abren los huecos (contraforma '
     'r46 → 51, agujeros r23 → 26,5), se baja `tip_x` a 65 para que la pata no engorde al '
     'crecer la contraforma. Anillos 38/30/37 → 33/26,5/35,3, grosor mediano -12,5 %, tinta -9,0 %. '
     'y el canal se abre de 6 a 8 u -el ráster lo tiene más cerrado, pero por debajo de 7 la rueda '
     'derecha se funde con el cuerpo a tamaño de icono-. El solape con el ráster baja de 0,960 a '
     '0,883 A PROPÓSITO. Es la ELEGIDA desde el 25-09.',
     CHOSEN),
]

CHOSEN_SLUG = 'adelgazada'


# ── Construccion ────────────────────────────────────────────────────────────
def compas_of(sp):
    return Compas(**sp['params'])


def canvas(c):
    """Lienzo full bleed: el bbox de la marca, que TIENE que empezar en (0,0).

    ⛔ Y en unidades ENTERAS. La cabecera escribe el viewBox con %d, asi que un
    lienzo de 236,5 sale como 236 y RECORTA media unidad del dibujo sin que falle
    nada -el SVG es valido, la marca se queda sin su borde de abajo-. El alto lo
    ponen `lob_y + R_LOB` y el ancho la cabeza: si un ajuste los deja en medio
    punto, se corrigen ahi, no aqui.
    """
    x0, y0, x1, y1 = c.bbox()
    assert abs(x0) < 1e-9 and abs(y0) < 1e-9, 'la marca ya no toca los bordes: %s' % (c.bbox(),)
    for v in (x1, y1):
        assert abs(v - round(v)) < 1e-9, 'el lienzo no es entero y el viewBox lo truncaria: %s' % ((x1, y1),)
    return x1, y1


def ring(c, R, r):
    """Anillo completo: exterior + agujero al reves, para que nonzero lo abra."""
    from compas import f, arc_to
    d = 'M%s,%s' % (f(c[0]), f(c[1] - R)) + arc_to((c[0], c[1] + R), R, 1, 1) \
        + arc_to((c[0], c[1] - R), R, 1, 1) + 'Z'
    d += 'M%s,%s' % (f(c[0]), f(c[1] - r)) + arc_to((c[0], c[1] + r), r, 1, 0) \
        + arc_to((c[0], c[1] - r), r, 1, 0) + 'Z'
    return d


def paths_of(sp):
    """Los tres paths en el ORDEN en que se pintan. ⛔ El orden importa en la
    compacta: el cuerpo va primero y el anillo derecho encima -se unen por
    pintura, sin booleanas-, y funciona porque el agujero de esa rueda cae entero
    fuera del cuerpo."""
    c = compas_of(sp)
    P = sp['params']
    if sp['right'] == 'ring':
        return [c.body(), ring(P['RC'], P['R_LOB'], P['R_HOLE']), c.eye()], c
    rebuild_lobe.L = P['RC']
    rebuild_lobe.H = P['HC']
    rebuild_lobe.R_OUT = P['R_LOB']
    rebuild_lobe.R_HOLE = P['R_HOLE']
    lobe, _ = rebuild_lobe.build(cap_t=sp.get('cap_t', CAP_T),
                                 r_cresc=P['R_HEAD'] + sp['gap'],
                                 shelf_y=sp['shelf_y'])
    return [c.eye(), lobe, c.body()], c


def ink_centroid(sp=None):
    """Centro OPTICO: el centroide del AREA de tinta, en unidades de marca.

    ⛔ No es el centro del lienzo ni el del circulo envolvente. La marca tiene la
    cabeza y la rueda derecha a un lado y solo la pata al otro, asi que su masa cae
    +12,6 u a la DERECHA del centro geometrico -un 5,2 % del ancho-. No es cosa del
    ajuste al raster: el propio raster va a +4,9 % y la version anterior a +4,7 %.
    Encuadrar por geometria en las once superficies es lo que hace que el dibujo se
    vea escorado; lo vio el user en el lanzador antes que ninguna medida.

    Shoelace sobre los contornos aplanados. Los agujeros giran al reves, asi que su
    area sale negativa y se descuenta sola -misma regla nonzero con la que se pinta.
    """
    sp = sp or CHOSEN
    ds, _c = paths_of(sp)
    A = Cx = Cy = 0.0
    for d in ds:
        for sub in _flatten(d, 0.25):
            a = cx = cy = 0.0
            for (x0, y0), (x1, y1) in zip(sub, sub[1:]):
                cr = x0 * y1 - x1 * y0
                a += cr; cx += (x0 + x1) * cr; cy += (y0 + y1) * cr
            if abs(a) < 1e-12:
                continue
            a /= 2.0
            A += a; Cx += (cx / (6.0 * a)) * a; Cy += (cy / (6.0 * a)) * a
    return (Cx / A, Cy / A)


def optical_frame(sp=None):
    """(radio, centro) con el que encuadra CADA superficie: envolvente ALREDEDOR
    del centro optico.

    El minimo envolvente (`Compas.mec`) da el circulo mas pequeno, pero su centro es
    geometrico: usarlo centra la CAJA y deja la MASA a la derecha. Aqui se fija el
    centro en el centroide de tinta y se mide el radio que hace falta desde ahi, que
    por fuerza es mayor -151,58 contra 141,65-. Consecuencias, las dos buscadas:
    la marca queda optica-mente centrada y sale al 93,4 % del tamano anterior.

    ⚠️ Eso corre el calibrado de F_IN_CIRCLE: 0,70 se midio contra el envolvente
    GEOMETRICO, asi que en esa vara la marca pasa a ocupar 0,654 -entre el launcher
    de 2022 (0,643) y Photos (0,740)-. Sigue dentro del rango que se midio; si algun
    dia se vuelve a medir, hay que comparar la misma vara.
    """
    sp = sp or CHOSEN
    import math
    c = compas_of(sp)
    ctr = ink_centroid(sp)
    r = max(math.hypot(ctr[0] - q[0], ctr[1] - q[1]) + rr for q, rr in c.circles())
    return r, ctr


def path_names(sp):
    """Como se llama cada path, EN EL ORDEN en que `paths_of` los devuelve.

    ⛔ Va aqui y no en el consumidor. Las once superficies escriben el nombre de
    cada path como comentario, y lo hacian con una tupla fija en `export_surfaces`:
    al cambiar la marca elegida por una de anillo, el orden de pintado se invirtio
    -el cuerpo primero, el ojo al final- y los once ficheros generados se quedaron
    llamando "el ojo del pin" al cuerpo. No fallo nada, porque un comentario no se
    ejecuta. El nombre viaja con el orden o vuelve a mentir.
    """
    body = 'el cuerpo: cabeza + pata + rueda izquierda'
    if sp['right'] == 'ring':
        return (body, 'la rueda derecha', 'el ojo del pin')
    return ('el ojo del pin', 'la rueda derecha', body)


HEADER = """<svg xmlns="http://www.w3.org/2000/svg" width="%(W)d" height="%(H)d" viewBox="0 0 %(W)d %(H)d" fill="#25F48C">
  <!--
%(note)s
  -->
"""

NOTE_CHOSEN = """       Paparcar - la MARCA.  SALIDA de build_marks.py: no editar a mano.

       Es el Compas COMPLETO, y es la unica forma que existe: la misma a todos los
       tamanos. No hay variante pequena ni umbral de 32 dp que cada superficie tenga
       que saber en que lado esta.

       Sus valores no se eligieron a ojo: salen de AJUSTAR el maestro al raster del
       que nacio el dibujo -el que se vectorizo a mano antes de que existiera este
       fichero-. El ajuste dio 0,959 de solape contra ese original, frente al 0,875
       de la version anterior; despues el trazo se adelgazo un paso a peticion del
       user y el solape queda en 0,885. Cabeza R84 con su ojo r21,5 concentrico,
       contraforma r51, agujeros r26,5, ruedas R53, acuerdo r18 y punta de gota r7.
       Anillos 33 / 26,5 / 35,3: el trazo MODULA -engorda al girar-, y esa modulacion
       es lo que hace que la marca se lea solida en vez de como un trazado de ancho
       constante.

       Que la distingue de la compacta:
       · la rueda derecha es un ANILLO ABIERTO, separado del cuerpo por un canal de
         8 u igual por los dos lados. Ese hueco es lo que sostiene la lectura de la
         P: sin el, las dos ruedas quedan gemelas y la letra se afloja. Y no es solo
         aire: por debajo de 7 u la rueda se funde con el cuerpo a tamano de icono.
       · media luna y repisa, la ranura que el lobulo derecho le roba a la cabeza.

       ⚠️ El precio, dicho y medido a pixeles reales: con el canal en 6 u la rueda
       derecha FUNDE con el cuerpo por debajo de unos 28 px, asi que de 24 px para
       abajo la marca sale en dos trozos -silueta + ojo-. No se desmiga en tres como
       la version anterior, pero abajo se lee como la compacta. Donde importa es el
       small icon de notificacion y los chips.

       ⛔ Dos cosas del original NO se heredan, a proposito: su canal es desigual
       (6,5 / 5) y su punta de gota va casi en pico. Igualar el canal y redondear la
       punta cuesta 0,002 de solape y deja la marca sin una sola esquina viva.

       El lienzo NO es cuadrado y el dibujo toca los cuatro bordes: quien lo reciba
       tiene que respetar la relacion, no meterlo en una caja cuadrada."""


def bodyof(ds, W, mirror, indent='  '):
    """Los paths, envueltos en el espejo si la variante lo pide.

    ⛔ El espejo NO se hace con parametros. La construccion tiene MANO metida en
    todas partes -el `side` de cada tangente, el sentido de cada arco, la media
    luna que el lobulo derecho le roba a la cabeza-, asi que reflejar los centros
    no refleja el dibujo: lo rompe. Se refleja la SALIDA, que ademas es exacto y no
    toca ni un flag. Lo unico que hay que recordar es que el reflejo invierte la
    orientacion de los contornos, y por eso va como grupo y no reescribiendo los
    arcos a mano.
    """
    paths = ''.join('%s%s<path d="%s"/>\n' % (indent, '  ' if mirror else '', d) for d in ds)
    if not mirror:
        return paths
    return ('%s<g transform="translate(%g,0) scale(-1,1)">\n%s%s</g>\n'
            % (indent, W, paths, indent))


def svg_of(sp, note):
    ds, c = paths_of(sp)
    W, H = canvas(c)
    head = HEADER % dict(W=int(W), H=int(H), note=note)
    return head + bodyof(ds, W, sp.get('mirror', False)) + '</svg>\n', c


def build(sp=None):
    """La marca ELEGIDA. `export_surfaces.py` compara su salida con el fichero."""
    return svg_of(sp or CHOSEN, NOTE_CHOSEN)


def _by_slug(slug):
    for s, _t, _n, sp in VARIANTS:
        if s == slug:
            return sp
    raise KeyError(slug)


def metrics(sp):
    ds, c = paths_of(sp)
    m = c.metrics()
    W, H = canvas(c)
    gap, slot = sp['gap'], sp['shelf_y'] - 168.0
    return dict(w=W, h=H, ratio=W / H, rings=(m['head_ring'], m['lob_ring'], m['leg']),
                channel=('%.2f' % gap) if abs(gap - slot) < 1e-6 or sp['right'] == 'ring'
                        else '%.2f / %.2f' % (gap, slot),
                chars=sum(len(d) for d in ds), join=sp['params']['JOIN'],
                right=sp['right'], paths=ds, mirror=sp.get('mirror', False))


# ── Auditoria de esquinas ───────────────────────────────────────────────────
#
# 🔑 «No tiene ni un pico» dejaba de ser una impresion cada vez que alguien miraba
# el render, y volvia a serlo en cuanto se cambiaba un parametro. Esto lo mide: se
# aplana cada contorno y se busca donde el recorrido cambia de direccion de golpe.
#
# ⛔ El umbral NO puede ser cero: aplanar un arco en segmentos ya produce giros de
# uno o dos grados por segmento, asi que un arco perfecto daria mil «esquinas». Lo
# que distingue una esquina de una curva es que el giro se concentra en un punto:
# se compara el giro de UN segmento contra el de sus vecinos.
CORNER_DEG = 20.0


def corners(sp, step=0.25):
    """Esquinas vivas del dibujo: (x, y, grados de giro). Vacio = todo tangente."""
    import math
    import sys as _s
    _s.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    ds, _c = paths_of(sp)
    out = []
    for d in ds:
        for sub in _flatten(d, step):
            n = len(sub)
            if n < 4:
                continue
            closed = abs(sub[0][0] - sub[-1][0]) < 1e-6 and abs(sub[0][1] - sub[-1][1]) < 1e-6
            pts = sub[:-1] if closed else sub
            n = len(pts)
            for i in range(n if closed else n - 2):
                a, b, c = pts[i % n], pts[(i + 1) % n], pts[(i + 2) % n]
                t1 = math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))
                t2 = math.degrees(math.atan2(c[1] - b[1], c[0] - b[0]))
                turn = abs((t2 - t1 + 180) % 360 - 180)
                if turn > CORNER_DEG:
                    out.append((b[0], b[1], turn))
    return out


def _flatten(d, step):
    """Aplana M/L/A en polilineas con paso constante. No hay cubicas en el maestro."""
    import math
    from compas import ang
    subs, cur, pos, start = [], [], (0.0, 0.0), None
    import re
    toks = re.findall(r'([MLAZmlaz])|(-?\d*\.?\d+)', d)
    toks = [t[0] or t[1] for t in toks]
    i = 0
    while i < len(toks):
        t = toks[i]
        if t in 'ML':
            i += 1
            while i < len(toks) and not toks[i].isalpha():
                p = (float(toks[i]), float(toks[i + 1])); i += 2
                if t == 'M':
                    if cur:
                        subs.append(cur)
                    cur, start = [p], p
                    t = 'L'
                else:
                    cur.append(p)
                pos = p
        elif t == 'A':
            i += 1
            while i < len(toks) and not toks[i].isalpha():
                r = float(toks[i]); large = int(float(toks[i + 3])); sweep = int(float(toks[i + 4]))
                p = (float(toks[i + 5]), float(toks[i + 6])); i += 7
                cur.extend(_arc(pos, p, r, large, sweep, step))
                pos = p
        elif t in 'Zz':
            i += 1
            if cur:
                cur.append(start); subs.append(cur); cur = []
            pos = start
        else:
            i += 1
    if cur:
        subs.append(cur)
    # ⛔ Fuera los puntos repetidos ANTES de medir angulos. Un segmento de longitud
    # cero da atan2(0,0) = 0 y el giro contra su vecino sale 180: la primera version
    # de esto acusaba de pico al ojo -dos semicircunferencias cuya union es
    # perfectamente tangente- solo porque la `Z` vuelve a poner el punto de partida.
    clean = []
    for s in subs:
        q = [s[0]]
        for p in s[1:]:
            if abs(p[0] - q[-1][0]) > 1e-9 or abs(p[1] - q[-1][1]) > 1e-9:
                q.append(p)
        if len(q) > 2:
            clean.append(q)
    return clean


def _arc(p0, p1, r, large, sweep, step):
    import math
    dx, dy = (p1[0] - p0[0]) / 2.0, (p1[1] - p0[1]) / 2.0
    q = math.hypot(dx, dy)
    r = max(r, q)
    h = math.sqrt(max(0.0, r * r - q * q)) * (-1 if large == sweep else 1)
    cx = (p0[0] + p1[0]) / 2.0 - h * dy / q
    cy = (p0[1] + p1[1]) / 2.0 + h * dx / q
    a0 = math.atan2(p0[1] - cy, p0[0] - cx)
    a1 = math.atan2(p1[1] - cy, p1[0] - cx)
    da = a1 - a0
    if sweep and da < 0:
        da += 2 * math.pi
    if not sweep and da > 0:
        da -= 2 * math.pi
    n = max(6, int(abs(da) * r / step))
    return [(cx + r * math.cos(a0 + da * k / n), cy + r * math.sin(a0 + da * k / n))
            for k in range(1, n + 1)]


# ── Hoja de contactos ───────────────────────────────────────────────────────
GALLERY = """<!doctype html>
<meta charset="utf-8">
<title>Paparcar · marcas candidatas</title>
<style>
  :root { color-scheme: dark; }
  body { margin:0; padding:40px; background:#0D1117; color:#E6EDF3;
         font:15px/1.55 -apple-system,Segoe UI,Roboto,sans-serif; }
  h1 { font-size:26px; margin:0 0 6px; }
  .lead { color:#8B949E; max-width:70ch; margin:0 0 32px; }
  .v { border-top:1px solid #21262D; padding:28px 0; }
  .v h2 { font-size:19px; margin:0 0 4px; }
  .v h2 .n { color:#8B949E; font-weight:400; margin-right:10px; }
  .v .why { color:#8B949E; max-width:78ch; margin:0 0 18px; }
  .row { display:flex; align-items:flex-end; gap:34px; flex-wrap:wrap; }
  .cell { text-align:center; }
  .cell .lab { color:#6E7681; font-size:12px; margin-top:8px; }
  .light { background:#F0F4FB; padding:14px; border-radius:10px; }
  .dark  { background:#0D1117; padding:14px; border-radius:10px; border:1px solid #21262D; }
  table { border-collapse:collapse; margin-top:18px; font-size:13px; }
  td { padding:2px 18px 2px 0; color:#8B949E; }
  td b { color:#E6EDF3; font-weight:600; }
  .pick { color:#25F48C; }
</style>
<h1>Paparcar · marcas candidatas</h1>
<p class="lead">GENERADO por <code>docs/branding/master/build_marks.py</code>. Cada ejemplo es una
fila de <code>VARIANTS</code>: los mismos parámetros, el mismo código, regenerados a la vez. Van en
orden — cada uno dice qué cambia respecto al anterior. La marcada en verde es la que exporta a
producto.</p>
%(body)s
<p class="lead" style="margin-top:32px">Los tamaños pequeños se ven aquí a su tamaño real: si tu
pantalla es 2x, acércate en vez de hacer zoom — el zoom del navegador re-rasteriza y enseña un
detalle que el dispositivo no va a resolver.</p>
"""


def svg_inline(ds, W, H, px, fill, mirror=False):
    w = px * W / H
    body = ''.join('<path d="%s"/>' % d for d in ds)
    if mirror:
        body = '<g transform="translate(%g,0) scale(-1,1)">%s</g>' % (W, body)
    return ('<svg width="%.0f" height="%d" viewBox="0 0 %g %g" fill="%s">%s</svg>'
            % (w, px, W, H, fill, body))


def gallery():
    out = []
    for i, (slug, title, why, sp) in enumerate(VARIANTS, 1):
        mt = metrics(sp)
        pick = ' class="pick"' if slug == CHOSEN_SLUG else ''
        cells = []
        for px, bg in ((150, 'dark'), (150, 'light'), (48, 'dark'), (32, 'dark'), (24, 'dark')):
            fill = '#25F48C' if bg == 'dark' else '#009F5E'
            cells.append('<div class="cell"><div class="%s">%s</div><div class="lab">%d px%s</div></div>'
                         % (bg, svg_inline(mt['paths'], mt['w'], mt['h'], px, fill,
                                           mt['mirror']), px,
                            ' claro' if bg == 'light' else ''))
        out.append(
            '<section class="v">\n'
            '  <h2><span class="n">%02d</span><span%s>%s</span></h2>\n'
            '  <p class="why">%s</p>\n'
            '  <div class="row">%s</div>\n'
            '  <table>\n'
            '    <tr><td>lienzo <b>%.0f x %.0f</b> (%.3f)</td>'
            '<td>anillos <b>%.0f / %.0f / %.1f</b></td>'
            '<td>canal <b>%s</b></td>'
            '<td>enlace <b>%s</b></td>'
            '<td>path <b>%d car</b></td></tr>\n'
            '  </table>\n'
            '</section>' % (i, pick, title, why, ''.join(cells),
                            mt['w'], mt['h'], mt['ratio'], mt['rings'][0], mt['rings'][1],
                            mt['rings'][2], mt['channel'],
                            'recto' if mt['join'] == 'straight' else 'acuerdo r34',
                            mt['chars']))
    return GALLERY % dict(body='\n'.join(out))


# ── Escritura ───────────────────────────────────────────────────────────────
# ⛔ Se escribe DIRECTO donde lee el consumidor. Antes salia al lado del script y
# alguien tenia que copiarlo: un paso manual entre el generador y su unico
# consumidor es la forma mas barata de volver a tener un dibujo que nadie sabe de
# donde sale.
HERE = os.path.dirname(os.path.abspath(__file__))
MARK_DIR = os.path.join(HERE, '..', 'mark')            # lo que exporta a producto
EXPLORATIONS_DIR = os.path.join(HERE, '..', 'explorations')   # los ejemplos


def save(base, rel, text):
    p = os.path.abspath(os.path.join(base, rel))
    os.makedirs(os.path.dirname(p), exist_ok=True)
    io.open(p, 'w', encoding='utf-8', newline='\n').write(text)
    print('  escrito  %s' % os.path.relpath(p, os.path.join(HERE, '..', '..', '..')).replace('\\', '/'))


if __name__ == '__main__':
    svg, c = build()
    save(MARK_DIR, 'paparcar-mark-compas-b.svg', svg)
    m = c.metrics()
    r_mec, c_mec = c.mec()
    W, H = canvas(c)
    # ⛔ La compacta no tiene media luna ni repisa: decir su "canal" seria inventar
    # una cifra de una pieza que no existe en ella.
    extra = ('rueda derecha: anillo entero fusionado (sin media luna ni repisa)'
             if CHOSEN['right'] == 'ring' else 'canal %.2f u' % GAP)
    print('ELEGIDA    anillos %.0f / %.0f / %.1f · %s'
          % (m['head_ring'], m['lob_ring'], m['leg'], extra))
    print('           bajo y=%.0f == techo del agujero y=%.0f' % (m['under_y'], m['hole_top']))
    print('           lienzo %.0fx%.0f (ratio %.4f) · envolvente r=%.4f en (%.4f, %.4f)'
          % (W, H, W / H, r_mec, c_mec[0], c_mec[1]))

    print()
    # ⛔ Vaciar explorations/ ANTES de escribir. Los ejemplos van numerados, asi
    # que meter uno en medio renumera a los de despues y los ficheros con el
    # numero viejo se quedan: un directorio que dice ser la lista de ejemplos y
    # tiene dentro dos versiones del mismo dibujo con nombres distintos. Paso
    # justo al insertar `04-punta`, y colarian en el commit sin que nada fallara.
    # ⛔ Solo los NUMERADOS. Barrer todos los .svg de la carpeta se llevo por
    # delante `fluida-redibujada.svg`, que la escribe otro generador: un barrido
    # tiene que acotarse a lo que ese generador ESCRIBE, no a la carpeta entera.
    import re as _re
    vdir = os.path.abspath(EXPLORATIONS_DIR)
    if os.path.isdir(vdir):
        for fn in os.listdir(vdir):
            if _re.match(r'^\d\d-.*\.svg$', fn):
                os.remove(os.path.join(vdir, fn))
    for i, (slug, title, _why, sp) in enumerate(VARIANTS, 1):
        save(EXPLORATIONS_DIR, '%02d-%s.svg' % (i, slug),
             svg_of(sp, '       Paparcar - ejemplo %02d: %s. SALIDA de build_marks.py.' % (i, title))[0])

    print()
    print('Esquinas vivas (giro > %.0f grados en un punto):' % CORNER_DEG)
    for i, (slug, title, _why, sp) in enumerate(VARIANTS, 1):
        ks = corners(sp)
        mark = '' if not ks else '   <-- ' + ', '.join('(%.1f,%.1f) %.0f deg' % k for k in ks[:4])
        print('  %02d %-12s %d%s' % (i, slug, len(ks), mark))
