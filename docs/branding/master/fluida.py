"""La FLUIDA redibujada limpia. Su propio modelo, no el del Compas.

    cd docs/branding/master && python fluida.py

⛔ Esto NO reproduce el raster: lo toma de inspiracion. Reproducirlo ya se hizo
-`vectorize_fluida.py`, IoU 0,971- y ahi se llego al techo del original: su borde
trae ruido de compresion y sus formas vienen con bultos (el ojo es un cuadrado
redondeado, el bajo del cuenco ondula). Mas limpio que aquello seria inventarsela,
asi que si hay que inventar, se dice: esto es un REDIBUJO.

⛔ Y no es el Compas con otros numeros. Ese camino se probo tres veces y no puede:
alli la cabeza hace TRES trabajos con un solo radio -enrasar arriba, poner el bajo
del cuenco (2*R_HEAD) y dar el angulo de la espalda por tangencia con la rueda- y
la Fluida no cumple esa atadura. Aqui cada pieza tiene su centro y su radio, y lo
que las une son ARCOS TANGENTES, no rectas: eso es lo que le da el movimiento que
el Compas no tiene.

De `compas.py` se reusan las primitivas de geometria -tangentes, interseccion de
circunferencias, arcos con los flags derivados del centro real- porque son
aritmetica, no doctrina. La CONSTRUCCION es otra.

Las cifras salen de medir su raster (circunferencias por minimos cuadrados sobre
cada pieza, angulos por RANSAC); lo que no se podia medir se dibuja.
"""
import io, math, os

from compas import (arc_to, ang, on, sweep_arc, circ_inter, outer_tangent,
                    tangent_from_point, norm, fillet_corner, minor_arc, f)

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'explorations', 'fluida-redibujada.svg')

# ── las piezas, medidas sobre el original ───────────────────────────────────
W, H = 248.0, 219.5

HC, R_HEAD = (167.8, 76.3), 76.3        # cabeza. Enrasa arriba: HC.y = R_HEAD
BC, R_BOWL = (160.7, 77.3), 43.3        # contraforma. ⛔ NO concentrica con la cabeza
EC, R_EYE = (162.4, 74.0), 14.8         # ojo. Tampoco concentrico: su propio centro
LC, R_LOB = (52.3, 168.4), 51.5         # rueda izquierda
LH, R_HOLE = (52.7, 167.2), 23.2        # su agujero
RC, R_RLOB = (198.1, 170.8), 49.9       # rueda derecha, suelta
R_RHOLE = 21.5
GAP = 9.4                               # canal entre la rueda derecha y el cuerpo
UNDER_Y = 148.0                         # bajo del cuenco
TIP = (95.0, 118.0)                     # punta de la gota
TIP_R = 11.0                            # su redondeo
LAND_X = 135.0                          # donde el remate recto apoya en el bajo
CORNER_R = 10.0                         # redondeo de esa esquina

# ⛔ El LOMO es un ARCO, no una recta, y ahi esta la diferencia entera con el
# Compas. Con una tangente comun el angulo lo mandan los dos radios y sale 47,9
# grados; la Fluida lleva 54,2 porque su borde CURVA. `BOW` es la flecha del arco
# en unidades: 0 lo devuelve a recta.
BOW_BACK = 9.0                          # lomo (borde exterior de la pata)
BOW_FRONT = 5.0                         # borde interior, el que mira a la gota


def bowed(p, q, bow, ccw=False):
    """Arco de flecha `bow` entre dos puntos. Tangente por construccion a lo que
    venga antes y despues si los extremos son puntos de tangencia."""
    ch = math.hypot(q[0] - p[0], q[1] - p[1])
    if abs(bow) < 1e-9:
        return 'L%s,%s ' % (f(q[0]), f(q[1]))
    R = (ch * ch / 4 + bow * bow) / (2 * abs(bow))
    return arc_to(q, R, 0, 1 if ccw else 0) + ' '


def body():
    """El cuerpo: cabeza + lomo + rueda izquierda + bajo del cuenco.

    ⛔ El recorrido va en el MISMO sentido que `compas.body()`: top -> lomo -> rueda
    -> remate recto -> bajo -> cabeza. No es capricho: los helpers de arco traen sus
    convenciones de ahi, y recorrerlo al reves hace que cada `ccw` signifique lo
    contrario. Lo hice al reves la primera vez y la cabeza salio rodeada por el lado
    que no era -un pegote, no una cabeza.

    ⛔ Regla NONZERO, como el resto del repo -y como los VectorDrawable de Android,
    que no tienen otra-. Probe evenodd para no pensar en el sentido de los agujeros
    y sali escaldado por otro lado: mi rasterizador de comprobacion no lo aplica, asi
    que el render me decia que estaba bien algo que no podia comprobar. Una salida
    que solo es correcta en un visor que no uso no es una salida correcta.
    El sentido no se razona: lo comprueba `check_windings()`.
    """
    top = (HC[0], HC[1] - R_HEAD)
    dy = UNDER_Y - HC[1]
    under_r = (HC[0] + math.sqrt(max(0.0, R_HEAD ** 2 - dy * dy)), UNDER_Y)
    p_head, p_lob = outer_tangent(HC, R_HEAD, LC, R_LOB, side=+1)
    land = (LAND_X, UNDER_Y)
    t_lob = tangent_from_point(land, LC, R_LOB, side=-1)
    to_lob = norm((t_lob[0] - land[0], t_lob[1] - land[1]))
    p_in, c_k, p_out = fillet_corner(land, to_lob, (1.0, 0.0), CORNER_R)

    d = 'M%s,%s ' % (f(top[0]), f(top[1]))
    d += sweep_arc(HC, R_HEAD, top, p_head, ccw=False) + ' '
    d += bowed(p_head, p_lob, BOW_BACK)                       # EL LOMO, abombado
    d += sweep_arc(LC, R_LOB, p_lob, t_lob, ccw=False) + ' '  # rueda izquierda
    d += 'L%s,%s ' % (f(p_in[0]), f(p_in[1]))                 # el remate RECTO
    d += minor_arc(c_k, CORNER_R, p_in, p_out) + ' '
    d += 'L%s,%s ' % (f(under_r[0]), f(under_r[1]))           # bajo del cuenco
    d += sweep_arc(HC, R_HEAD, under_r, top, ccw=False) + 'Z'

    # agujero de la rueda izquierda
    d += ' M%s,%s' % (f(LH[0]), f(LH[1] + R_HOLE))
    d += arc_to((LH[0], LH[1] - R_HOLE), R_HOLE, 1, 1)
    d += arc_to((LH[0], LH[1] + R_HOLE), R_HOLE, 1, 1) + 'Z'

    # contraforma en gota: su PROPIA circunferencia (no la de la cabeza) + la cola
    t1 = tangent_from_point(TIP, BC, R_BOWL, side=+1)
    t2 = tangent_from_point(TIP, BC, R_BOWL, side=-1)
    d1 = norm((t1[0] - TIP[0], t1[1] - TIP[1]))
    d2 = norm((t2[0] - TIP[0], t2[1] - TIP[1]))
    a1, ck, a2 = fillet_corner(TIP, d1, d2, TIP_R)
    # ⛔ Se recorre de a2 hacia a1, no al reves: con nonzero la gota tiene que girar
    # al contrario que el contorno, y lo dice `check_windings()`, no mi intuicion.
    d += ' M%s,%s' % (f(a2[0]), f(a2[1]))
    d += 'L%s,%s ' % (f(t2[0]), f(t2[1]))
    d += sweep_arc(BC, R_BOWL, t2, t1, ccw=True) + ' '
    d += 'L%s,%s ' % (f(a1[0]), f(a1[1]))
    d += minor_arc(ck, TIP_R, a1, a2) + 'Z'
    return d


def right_lobe():
    """La rueda derecha: anillo abierto, separado por el canal.

    ⛔ Se reusa `rebuild_lobe`, que es la reconstruccion ya verificada de esa pieza
    -media luna centrada en la cabeza, remates tangentes, repisa- y esta
    parametrizada por sus circunferencias. Volver a derivarla aqui seria tener dos
    ruedas derechas en el repo, que es como se acaba con dos que no coinciden.
    """
    import rebuild_lobe
    rebuild_lobe.L = RC
    rebuild_lobe.H = HC
    rebuild_lobe.R_OUT = R_RLOB
    rebuild_lobe.R_HOLE = R_RHOLE
    d, _info = rebuild_lobe.build(cap_t=9.0, r_cresc=R_HEAD + GAP,
                                  shelf_y=UNDER_Y + 11.0)
    return d


def eye():
    return ('M%s,%s' % (f(EC[0]), f(EC[1] - R_EYE)) +
            arc_to((EC[0], EC[1] + R_EYE), R_EYE, 1, 0) +
            arc_to((EC[0], EC[1] - R_EYE), R_EYE, 1, 0) + 'Z')


SVG = """<svg xmlns="http://www.w3.org/2000/svg" width="%(W)d" height="%(H)d" viewBox="0 0 %(W)g %(H)g" fill="#25F48C">
  <!-- Paparcar - la FLUIDA REDIBUJADA. SALIDA de master/fluida.py: no editar a mano.

       ⛔ Es un REDIBUJO, no una reproduccion. La Fluida reproducida vive en
       reference/paparcar-mark-fluida.svg (IoU 0,971 contra su raster) y ahi se
       llego al techo del original: su borde trae ruido de compresion y sus formas
       vienen con bultos. Esto la toma de inspiracion y la dibuja limpia.

       ⛔ Tampoco es el Compas con otros numeros. Cada pieza lleva su propio centro
       -la contraforma NO es concentrica con la cabeza, el ojo tampoco- y lo que une
       las piezas son ARCOS TANGENTES, no rectas: con una tangente comun el lomo
       sale a 47,9 grados y la Fluida lleva 54,2 porque su borde CURVA. Esa es la
       diferencia entera. -->
%(P)s</svg>
"""


def check_windings():
    """Los agujeros tienen que girar al REVES que el contorno que los contiene.

    ⛔ Esto no es una comprobacion de estilo: con nonzero, un agujero que gira igual
    que su contorno sale RELLENO, y el dibujo se ve mal sin que nada falle. Me paso
    dos veces hoy -aqui y en la Fluida vectorizada-, las dos por razonar el sentido
    en vez de medirlo. El area con signo lo dice en una linea.
    """
    import build_marks
    subs = build_marks._flatten(body(), 0.5)
    def signed(p):
        a = 0.0
        for i in range(len(p)):
            x1, y1 = p[i]; x2, y2 = p[(i + 1) % len(p)]
            a += x1 * y2 - x2 * y1
        return a / 2
    areas = [signed(s) for s in subs]
    assert len(areas) == 3, 'el cuerpo deberia tener 3 subpaths, tiene %d' % len(areas)
    outer = areas[0]
    for i, a in enumerate(areas[1:], 1):
        assert a * outer < 0, (
            'el subpath %d gira igual que el contorno (%.0f vs %.0f): con nonzero '
            'saldria RELLENO en vez de hueco' % (i, a, outer))


def paths():
    return [eye(), right_lobe(), body()]


def main():
    ps = paths()
    check_windings()
    txt = ''.join('  <path d="%s"/>' % d + chr(10) for d in ps)
    p = os.path.abspath(OUT)
    io.open(p, 'w', encoding='utf-8', newline=chr(10)).write(
        SVG % dict(W=int(round(W)), H=int(round(H)), P=txt))
    print('  escrito  docs/branding/explorations/fluida-redibujada.svg (%d car de path)'
          % sum(len(d) for d in ps))


if __name__ == '__main__':
    main()
