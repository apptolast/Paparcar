"""Maestro parametrico de la marca Compas (la P geometrica).

Ingenieria inversa del SVG original: el dibujo entero son seis circunferencias,
dos rectas tangentes y dos acuerdos. Nada mas.

    cabeza   R 84 / r 44 en (164, 84)     ojo r 20 concentrico
    lobulos  R 56 / r 24 en (56,184) y (192,184)
    pata     tangente comun exterior R84 <-> R56 (borde superior)
             tangente a r44 desde la punta de la gota (borde inferior)
    bajo     recta tangente al R84 por abajo (y = 168), acuerdo r 16 al lobulo
    gota     base plana tangente al r44 por abajo + recta hasta la punta

Cambiar un parametro reconstruye todo de forma consistente. Eso es lo que el
path aplanado de 2.435 caracteres NO permitia.
"""
import math

def f(v):
    return ('%.3f' % v).rstrip('0').rstrip('.')


def arc_to(p, r, large, sweep):
    return 'A%s,%s 0 %d,%d %s,%s' % (f(r), f(r), large, sweep, f(p[0]), f(p[1]))


def ang(c, p):
    return math.degrees(math.atan2(p[1] - c[1], p[0] - c[0])) % 360


def on(c, r, a):
    return (c[0] + r * math.cos(math.radians(a)), c[1] + r * math.sin(math.radians(a)))


def sweep_arc(c, r, p_from, p_to, ccw):
    """Flags derivados del centro REAL. Con dos extremos y un radio hay cuatro
    arcos posibles; elegirlos a ojo escoge a veces el otro centro."""
    d = (ang(c, p_to) - ang(c, p_from)) % 360 if ccw else (ang(c, p_from) - ang(c, p_to)) % 360
    return arc_to(p_to, r, 1 if d > 180 else 0, 1 if ccw else 0)


def norm(v):
    m = math.hypot(v[0], v[1])
    return (v[0] / m, v[1] / m)


def fillet_corner(V, ray_a, ray_b, r):
    """Redondeo de radio `r` en el vertice V entre dos rayos UNITARIOS que salen de el.

    Devuelve (p_a, centro, p_b): donde deja el primer rayo, el centro del arco, y
    donde retoma el segundo.

    ⛔ Un acuerdo entre dos rectas siempre corta el sector NO REFLEJO -el de menos
    de 180 grados-, y por eso el centro va en la bisectriz de ESE sector y los dos
    contactos caen ANTES del vertice, sobre sus rayos. Lo intente con un atajo
    -poner el centro al lado del material, que es la izquierda del recorrido- y en
    la esquina del remate recto salio al reves: los contactos se iban PASADO el
    vertice y el contorno se doblaba sobre si mismo 179 grados. No se veia a
    tamano de icono y lo caza la auditoria de esquinas, no el ojo.
    """
    cos = max(-1.0, min(1.0, ray_a[0] * ray_b[0] + ray_a[1] * ray_b[1]))
    half = math.acos(cos) / 2.0
    back = r / math.tan(half)
    b = norm((ray_a[0] + ray_b[0], ray_a[1] + ray_b[1]))
    c = (V[0] + b[0] * r / math.sin(half), V[1] + b[1] * r / math.sin(half))
    return ((V[0] + ray_a[0] * back, V[1] + ray_a[1] * back), c,
            (V[0] + ray_b[0] * back, V[1] + ray_b[1] * back))


def minor_arc(c, r, p_from, p_to):
    """El arco CORTO entre dos puntos de una circunferencia conocida.

    ⛔ Sigue valiendo que los flags no se adivinan (ver `sweep_arc`), pero cuando
    lo que se quiere es redondear una esquina la respuesta es siempre el menor de
    los dos arcos: es el unico que corta la esquina en vez de rodear la pieza.
    """
    d = (ang(c, p_to) - ang(c, p_from)) % 360
    ccw = d < 180
    return arc_to(p_to, r, 0, 1 if ccw else 0)


def circ_inter(c1, r1, c2, r2, pick=+1):
    dx, dy = c2[0] - c1[0], c2[1] - c1[1]
    d = math.hypot(dx, dy)
    a = (d * d + r1 * r1 - r2 * r2) / (2 * d)
    h = math.sqrt(max(0.0, r1 * r1 - a * a))
    bx, by = c1[0] + a * dx / d, c1[1] + a * dy / d
    return (bx + pick * h * dy / d, by - pick * h * dx / d)


def outer_tangent(c1, r1, c2, r2, side=+1):
    """Tangente comun EXTERIOR: devuelve los dos puntos de tangencia."""
    dx, dy = c2[0] - c1[0], c2[1] - c1[1]
    d = math.hypot(dx, dy)
    # angulo del eje + desfase por la diferencia de radios
    base = math.atan2(dy, dx)
    off = math.acos(max(-1.0, min(1.0, (r1 - r2) / d)))
    a = base + side * off
    return ((c1[0] + r1 * math.cos(a), c1[1] + r1 * math.sin(a)),
            (c2[0] + r2 * math.cos(a), c2[1] + r2 * math.sin(a)))


def mec(circles):
    """Circulo minimo envolvente de un conjunto de circunferencias -> (r, (cx, cy)).

    ⛔ Existe porque `export_surfaces.py` llevaba ese radio y ese centro escritos a
    mano. Son la RELACION entre la marca y el hueco que la recibe -lo unico que
    decide a que tamano sale en cada superficie-, y una relacion escrita a mano
    sobrevive muda al dibujo que la produjo: mueve un lobulo y las once superficies
    siguen encuadrando la marca de antes, sin fallar al compilar.

    f(x) = max_i (|x - c_i| + r_i) es convexa: basta una busqueda de patron que
    encoge el paso. Determinista y sin dependencias.
    """
    def f(p):
        return max(math.hypot(p[0] - c[0], p[1] - c[1]) + r for c, r in circles)

    p = (sum(c[0] for c, _ in circles) / len(circles),
         sum(c[1] for c, _ in circles) / len(circles))
    step, best = 64.0, f(p)
    while step > 1e-9:
        moved = False
        for dx, dy in ((step, 0), (-step, 0), (0, step), (0, -step),
                       (step, step), (step, -step), (-step, step), (-step, -step)):
            q = (p[0] + dx, p[1] + dy)
            v = f(q)
            if v < best - 1e-12:
                p, best, moved = q, v, True
        if not moved:
            step /= 2
    return best, p


def tangent_from_point(p, c, r, side=+1):
    """Punto de tangencia de la recta que va de `p` a la circunferencia (c, r)."""
    dx, dy = c[0] - p[0], c[1] - p[1]
    d = math.hypot(dx, dy)
    base = math.atan2(dy, dx)
    off = math.asin(max(-1.0, min(1.0, r / d)))
    a = base + side * off                 # direccion de la recta desde p
    t = math.sqrt(max(0.0, d * d - r * r))
    return (p[0] + t * math.cos(a), p[1] + t * math.sin(a))


class Compas:
    def __init__(self, HC=(164.0, 84.0), R_HEAD=84.0, R_CNTR=44.0, R_EYE=20.0,
                 LC=(56.0, 184.0), RC=(192.0, 184.0), R_LOB=56.0, R_HOLE=24.0,
                 TIP_X=77.6, FILLET=16.0, LEG_BOW=0.0,
                 JOIN='fillet', CORNER_R=8.0, TIP_R=0.0, EYE_OFF=(0.0, 0.0)):
        self.HC, self.R_HEAD, self.R_CNTR, self.R_EYE = HC, R_HEAD, R_CNTR, R_EYE
        self.LC, self.RC, self.R_LOB, self.R_HOLE = LC, RC, R_LOB, R_HOLE
        self.TIP_X, self.FILLET, self.LEG_BOW = TIP_X, FILLET, LEG_BOW
        # Como el lobulo izquierdo se une al bajo del cuenco:
        #   'fillet'   → acuerdo concavo (la "S"): la rueda se funde en una curva
        #   'straight' → recta tangente al lobulo hasta el bajo (el remate de la Fluida)
        self.JOIN, self.CORNER_R = JOIN, CORNER_R
        self.TIP_R = TIP_R       # redondeo de la punta de la gota; 0 = pico
        # Desplazamiento del ojo respecto al centro de la cabeza. (0,0) = concentrico,
        # que es como esta dibujada NUESTRA marca y por eso es el defecto.
        # ⛔ Existe porque la FLUIDA no lo tiene concentrico: medido sobre su raster,
        # su ojo cae 6,4 u a la izquierda. Con el ojo libre el solape sube de 0,8827
        # a 0,8925. Un modelo que no puede expresar una asimetria no describe el
        # dibujo: le impone una regularidad y llama "error de ajuste" a la diferencia.
        self.EYE_OFF = EYE_OFF

    # ── derivados ────────────────────────────────────────────────────────
    @property
    def base_y(self):
        """Base plana de la gota: tangente al r44 por abajo."""
        return self.HC[1] + self.R_CNTR

    @property
    def under_y(self):
        """Bajo del cuerpo: tangente al R84 por abajo. ⛔ ES el fondo del cuenco
        de la P. Subirlo por encima de HC.y + R_HEAD se lo lleva por delante."""
        return self.HC[1] + self.R_HEAD

    @property
    def tip(self):
        return (self.TIP_X, self.base_y)

    def leg_edge(self):
        """Borde superior de la pata: tangente comun exterior cabeza<->lobulo izq."""
        return outer_tangent(self.HC, self.R_HEAD, self.LC, self.R_LOB, side=+1)

    def tail_tangent(self):
        """Borde inferior de la pata = recta de la punta de la gota, tangente al r44."""
        return tangent_from_point(self.tip, self.HC, self.R_CNTR, side=-1)

    def fillet_pts(self):
        """Acuerdo de cuarto de circulo entre el lobulo izquierdo y el bajo del cuerpo.

        ⛔ Su radio NO es libre: para que sea tangente vertical al lobulo (en su
        punto mas a la derecha) y tangente horizontal al bajo, vale exactamente
        `LC.y - under_y`. En el original salen 16 — y no porque nadie eligiera 16,
        sino porque 184 - 168 = 16. Mover el bajo o el lobulo lo recalcula solo.
        """
        k = self.LC[1] - self.under_y          # minimo geometrico (cuarto de circulo)
        r = max(k, self.FILLET)
        # acuerdo CONCAVO: centro fuera del material, tangente exterior al lobulo
        # y tangente a la recta del bajo. r = k da el cuarto de circulo del original;
        # r > k alarga la transicion -> es la "S" de la Fluida, sin salirse de la regla.
        cx = self.LC[0] + math.sqrt((self.R_LOB + r) ** 2 - (r - k) ** 2)
        c = (cx, self.under_y + r)
        dd = math.hypot(c[0] - self.LC[0], c[1] - self.LC[1])
        f_lob = (self.LC[0] + (c[0] - self.LC[0]) * self.R_LOB / dd,
                 self.LC[1] + (c[1] - self.LC[1]) * self.R_LOB / dd)
        return r, c, f_lob, (c[0], self.under_y)

    def tip_round(self):
        """Redondeo de la PUNTA de la gota — el ultimo remate en vivo del dibujo.

        La contraforma en gota es lo que hace de pin: base plana tangente al r50 y
        dos rectas que se cierran en punta. Esa punta son dos segmentos rectos que
        se cortan a **52,6 grados**, o sea un pico — y es el unico angulo vivo que
        le quedaba a una marca que es toda arcos y tangencias. Se ve en el hueco y
        se ve invertido en el verde que lo rodea, que forma la muesca opuesta.

        ⛔ El radio NO se compensa moviendo `TIP_X`. La punta virtual es lo que fija
        el borde INFERIOR de la pata: correrla para recuperar el largo perdido
        adelgaza la pata (~0,8 u de pata por cada u de punta). Se acepta que la gota
        se acorte `TIP_R/tan(theta/2)` y la pata se queda donde estaba.

        Devuelve (p1, centro, p2): donde deja la base plana, el centro del arco, y
        donde retoma la recta de la cola. `TIP_R = 0` deja el pico.
        """
        V = self.tip
        base_r = (self.HC[0], self.base_y)
        t_arc = self.tail_tangent()
        r = self.TIP_R
        if r <= 0:
            return V, None, V
        d1 = norm((base_r[0] - V[0], base_r[1] - V[1]))    # V -> base plana
        d2 = norm((t_arc[0] - V[0], t_arc[1] - V[1]))      # V -> cola
        return fillet_corner(V, d1, d2, r)

    def land_x(self):
        """Donde el enlace del lobulo izquierdo muere contra el bajo del cuenco.

        Es el MISMO punto para los dos modos de enlace, a proposito: cambiar de
        acuerdo a recta debe cambiar la CURVA, no dónde apoya. Así la comparación
        aísla una sola variable.
        """
        return self.fillet_pts()[1][0]

    def straight_pts(self):
        """Enlace RECTO: tangente al lobulo izquierdo que sube hasta el bajo.

        Es la particularidad de la Fluida: allí el bajo del cuenco no se funde con
        la rueda en una curva, la CORTA con una recta y gira en esquina. Medido
        sobre su contorno vectorizado, ese tramo recto sube a ~56 grados; aquí sale
        de la tangencia, no de copiar el angulo, y cae en ~52.

        ⛔ La tangencia no es un detalle: una recta que corte el circulo sin ser
        tangente deja un PICO donde la rueda se encuentra con ella. Con un extremo
        fijo (el apoyo en el bajo) y la condicion de tangencia, la recta y su punto
        de contacto quedan determinados: no hay angulo que elegir a ojo.

        Devuelve (t_lob, p_in, c_corner, p_out) — el contacto en el lobulo, donde
        empieza el redondeo de la esquina, su centro, y donde vuelve al bajo.
        `CORNER_R = 0` deja la esquina en vivo.
        """
        land = (self.land_x(), self.under_y)
        # ⛔ side=-1: con dos tangentes desde un punto, la otra rama toca el lobulo
        # por ARRIBA-izquierda y la recta sale cruzando el dibujo entero.
        t_lob = tangent_from_point(land, self.LC, self.R_LOB, side=-1)
        r = self.CORNER_R
        if r <= 0:
            return t_lob, land, None, land
        # los dos rayos SALEN del vertice: uno vuelve hacia el lobulo, el otro sigue
        # el bajo del cuenco hacia la derecha.
        back_to_lob = norm((t_lob[0] - land[0], t_lob[1] - land[1]))
        p_in, c, p_out = fillet_corner(land, back_to_lob, (1.0, 0.0), r)
        return t_lob, p_in, c, p_out

    # ── paths ────────────────────────────────────────────────────────────
    def body(self):
        HC, LC, RH, RL = self.HC, self.LC, self.R_HEAD, self.R_LOB
        p_head, p_lob = self.leg_edge()
        top = (HC[0], HC[1] - RH)
        under_r = (HC[0], self.under_y)
        r_f, c_f, f_lob, f_und = self.fillet_pts()

        d = 'M%s,%s ' % (f(top[0]), f(top[1]))
        d += sweep_arc(HC, RH, top, p_head, ccw=False) + ' '
        if self.LEG_BOW == 0:
            d += 'L%s,%s ' % (f(p_lob[0]), f(p_lob[1]))
        else:
            # la "S": el borde de la pata deja de ser recto y pasa a ser un arco
            # de radio enorme tangente a las dos circunferencias. bow>0 = panza
            # hacia fuera.
            ch = math.hypot(p_lob[0] - p_head[0], p_lob[1] - p_head[1])
            R = (ch * ch / 4 + self.LEG_BOW ** 2) / (2 * self.LEG_BOW)
            d += arc_to(p_lob, R, 0, 0 if self.LEG_BOW > 0 else 1) + ' '
        if self.JOIN == 'straight':
            # El remate de la Fluida: la rueda no se funde en curva con el bajo del
            # cuenco, la corta una RECTA tangente y el bajo gira en esquina. El
            # lobulo pierde su cuadrante superior derecho y el cuenco cierra con una
            # linea, que es lo que hace que se lea como P y no como rueda.
            t_lob, p_in, c_k, p_out = self.straight_pts()
            d += sweep_arc(LC, RL, p_lob, t_lob, ccw=False) + ' '
            d += 'L%s,%s ' % (f(p_in[0]), f(p_in[1]))
            if c_k is not None:
                d += minor_arc(c_k, self.CORNER_R, p_in, p_out) + ' '
        else:
            d += sweep_arc(LC, RL, p_lob, f_lob, ccw=False) + ' '
            d += sweep_arc(c_f, r_f, f_lob, f_und, ccw=True) + ' '
        d += 'L%s,%s ' % (f(under_r[0]), f(under_r[1]))
        d += sweep_arc(HC, RH, under_r, top, ccw=False) + 'Z'

        # agujero de la rueda izquierda. ⛔ sweep=1: el contorno exterior va en
        # angulo DECRECIENTE, asi que el agujero tiene que ir al reves o la regla
        # nonzero no abre nada (y el agujero sale relleno).
        h = LC[0], LC[1]
        d += ' M%s,%s' % (f(h[0]), f(h[1] + self.R_HOLE))
        d += arc_to((h[0], h[1] - self.R_HOLE), self.R_HOLE, 1, 1)
        d += arc_to((h[0], h[1] + self.R_HOLE), self.R_HOLE, 1, 1) + 'Z'

        # contraforma en gota
        t_arc = self.tail_tangent()
        base_r = (HC[0], self.base_y)
        p1, c_t, p2 = self.tip_round()
        d += ' M%s,%s' % (f(base_r[0]), f(base_r[1]))
        d += 'L%s,%s ' % (f(p1[0]), f(p1[1]))
        if c_t is not None:
            d += minor_arc(c_t, self.TIP_R, p1, p2) + ' '
            d += 'L%s,%s ' % (f(t_arc[0]), f(t_arc[1]))
        else:
            d += 'L%s,%s ' % (f(t_arc[0]), f(t_arc[1]))
        d += sweep_arc(HC, self.R_CNTR, t_arc, base_r, ccw=True) + 'Z'
        return d

    def eye(self):
        c = (self.HC[0] + self.EYE_OFF[0], self.HC[1] + self.EYE_OFF[1])
        r = self.R_EYE
        return ('M%s,%s' % (f(c[0]), f(c[1] - r)) +
                arc_to((c[0], c[1] + r), r, 1, 0) +
                arc_to((c[0], c[1] - r), r, 1, 0) + 'Z')

    def circles(self):
        """Las tres circunferencias que mandan en la silueta: cabeza y dos lobulos.

        Todo lo demas -pata, bajo, acuerdo, gota- cae dentro de su envolvente: la
        pata es la tangente comun de dos de ellas y una tangente nunca sale del
        casco convexo de las circunferencias que toca.
        """
        return ((self.HC, self.R_HEAD), (self.LC, self.R_LOB), (self.RC, self.R_LOB))

    def bbox(self):
        cs = self.circles()
        return (min(c[0] - r for c, r in cs), min(c[1] - r for c, r in cs),
                max(c[0] + r for c, r in cs), max(c[1] + r for c, r in cs))

    def mec(self):
        """(radio, centro) del circulo minimo envolvente. Lo que cada superficie
        necesita para encuadrar: ver `mec()` de nivel superior."""
        return mec(self.circles())

    def metrics(self):
        p_head, p_lob = self.leg_edge()
        t = self.tail_tangent()
        # grosor de la pata = distancia del borde inferior (recta punta-tangente) al superior
        ax, ay = p_head; bx, by = p_lob
        L = math.hypot(bx - ax, by - ay)
        dist = abs((by - ay) * self.tip[0] - (bx - ax) * self.tip[1] + bx * ay - by * ax) / L
        return dict(head_ring=self.R_HEAD - self.R_CNTR,
                    lob_ring=self.R_LOB - self.R_HOLE,
                    leg=dist, under_y=self.under_y, base_y=self.base_y,
                    hole_top=self.LC[1] - self.R_HOLE,
                    fillet=self.LC[1] - self.under_y)


    # ⛔ El lobulo derecho NO se genera aqui. Vive en `rebuild-lobe.py`, que es la
    # reconstruccion verificada del original (885 px de diferencia sobre 238.080
    # antes de corregir nada). Intente rehacerlo aqui y me costo cuatro intentos
    # fallidos por los flags de los arcos: con dos extremos y un radio hay CUATRO
    # arcos posibles, y elegirlos a ojo escoge a veces el otro centro. Se usa asi:
    #
    #     rebuild_lobe.L = RC ; rebuild_lobe.H = HC
    #     rebuild_lobe.R_OUT = R_LOB ; rebuild_lobe.R_HOLE = R_HOLE
    #     lobe, _ = rebuild_lobe.build(cap_t=10, r_cresc=R_HEAD + 7.393, shelf_y=...)
