"""Reconstruye analiticamente el lobulo inferior derecho de la marca 2026.

El original (41 segmentos aplanados) es, medido: arco exterior R56, arco de
media luna R91.393 centrado en la cabeza, arco del agujero r24, y una repisa
horizontal a y=176. Sus TRES terminaciones (los dos extremos del anillo abierto
y la punta de la repisa) acaban en cuchillo, dos de ellas con anchura CERO.

Aqui se emite el mismo lobulo con arcos exactos y con esas tres terminaciones
rematadas. `capT` = 0 reproduce el original; > 0 pone remate redondo.
"""
import math

L = (192.0, 184.0)      # centro de los lobulos inferiores
H = (164.0, 84.0)       # centro de la cabeza
R_OUT = 56.0            # radio exterior del lobulo
R_HOLE = 24.0           # agujero de la rueda
R_CRESC = 91.393        # media luna: radio DESDE LA CABEZA (hueco 7.393 sobre R84)
SHELF_Y = 176.0         # borde superior de la repisa


def circ_inter(c1, r1, c2, r2, pick=+1):
    """Interseccion de dos circunferencias. pick=+1 / -1 elige la rama."""
    dx, dy = c2[0] - c1[0], c2[1] - c1[1]
    d = math.hypot(dx, dy)
    a = (d * d + r1 * r1 - r2 * r2) / (2 * d)
    h2 = r1 * r1 - a * a
    if h2 < 0:
        raise ValueError('sin interseccion')
    h = math.sqrt(h2)
    bx, by = c1[0] + a * dx / d, c1[1] + a * dy / d
    return (bx + pick * h * dy / d, by - pick * h * dx / d)


def ang(c, p):
    return math.degrees(math.atan2(p[1] - c[1], p[0] - c[0])) % 360


def on(c, r, a):
    return (c[0] + r * math.cos(math.radians(a)), c[1] + r * math.sin(math.radians(a)))


def arc(p, r, large, sweep):
    return 'A%s,%s 0 %d,%d %s,%s' % (f(r), f(r), large, sweep, f(p[0]), f(p[1]))


def arc_c(p_from, p_to, c, r, ccw):
    """Arco de `p_from` a `p_to` sobre la circunferencia (c, r) CONOCIDA.

    ⛔ Los flags no se adivinan. Con dos extremos y un radio hay CUATRO arcos
    posibles (dos centros x dos sentidos); elegir large/sweep a ojo escoge a
    veces el otro centro y el arco sale a otro sitio. Aqui se derivan del
    centro real: `ccw` dice si el recorrido va en angulo creciente (sweep=1).
    """
    a0 = math.degrees(math.atan2(p_from[1] - c[1], p_from[0] - c[0]))
    a1 = math.degrees(math.atan2(p_to[1] - c[1], p_to[0] - c[0]))
    delta = (a1 - a0) % 360 if ccw else (a0 - a1) % 360
    return arc(p_to, r, 1 if delta > 180 else 0, 1 if ccw else 0)


def f(v):
    return ('%.3f' % v).rstrip('0').rstrip('.')


def build(cap_t=11.0, fillet_apex=6.0, fillet_tip=5.0,
          r_cresc=R_CRESC, shelf_y=SHELF_Y):
    """Devuelve el `d` del lobulo. cap_t=0 → sin remates (reproduce el original)."""
    out = []

    # ── puntos maestros ────────────────────────────────────────────────
    # A · punta de la repisa: donde la linea de la repisa corta el R56
    ax = L[0] - math.sqrt(R_OUT ** 2 - (shelf_y - L[1]) ** 2)
    A = (ax, shelf_y)
    # B · apice: R56 ∩ media luna
    B = circ_inter(L, R_OUT, H, r_cresc, pick=-1)
    # remate superior del anillo: donde el grosor (radial desde L) vale cap_t
    if cap_t > 0:
        # Remate redondo TANGENTE a las dos curvas que forman el anillo: por fuera
        # a la media luna (desde dentro) y por dentro al agujero (desde fuera).
        # Asi el remate no sobresale ni un pelo del contorno -> el hueco de 7,39 u
        # contra el cuerpo se conserva intacto. Un remate radial (centro en el punto
        # medio) SI sobresale, y deja la holgura en 3,1 u. Medido.
        cap_r = cap_t / 2
        cap_c = circ_inter(H, r_cresc + cap_r, L, R_HOLE + cap_r, pick=+1)
        # el material del lobulo esta FUERA de la media luna (dist(H) >= r_cresc):
        # el acuerdo es tangente EXTERIOR a ella, no interior.
        dh = math.hypot(cap_c[0] - H[0], cap_c[1] - H[1])
        P_out = (H[0] + (cap_c[0] - H[0]) * r_cresc / dh,
                 H[1] + (cap_c[1] - H[1]) * r_cresc / dh)
        dl = math.hypot(cap_c[0] - L[0], cap_c[1] - L[1])
        P_in = (L[0] + (cap_c[0] - L[0]) * R_HOLE / dl,
                L[1] + (cap_c[1] - L[1]) * R_HOLE / dl)
    else:
        P_out = P_in = circ_inter(H, r_cresc, L, R_HOLE, pick=+1)
        cap_r = 0

    # ── fillet del apice B (tangente interior a R56 y exterior a la media luna)
    if fillet_apex > 0:
        fc = circ_inter(L, R_OUT - fillet_apex, H, r_cresc + fillet_apex, pick=-1)
        b_out = on(L, R_OUT, ang(L, fc))                       # tangente en R56
        d_ = math.hypot(fc[0] - H[0], fc[1] - H[1])
        b_cr = (H[0] + (fc[0] - H[0]) * r_cresc / d_,
                H[1] + (fc[1] - H[1]) * r_cresc / d_)          # tangente en la media luna
    else:
        b_out = b_cr = B

    # ── fillet de la punta A (tangente interior a R56 y a la linea de repisa)
    if fillet_tip > 0:
        fcx = L[0] - math.sqrt((R_OUT - fillet_tip) ** 2 - (shelf_y + fillet_tip - L[1]) ** 2)
        fc2 = (fcx, shelf_y + fillet_tip)
        a_out = on(L, R_OUT, ang(L, fc2))                      # tangente en R56
        a_line = (fcx, shelf_y)                                # tangente en la repisa
    else:
        a_out = a_line = A

    # ── fillet donde la repisa muere contra el agujero (el 3er cuchillo)
    #    tangente a la linea y EXTERIOR al agujero
    rt = 4.0
    tcx = L[0] - math.sqrt((R_HOLE + rt) ** 2 - (shelf_y + rt - L[1]) ** 2)
    t_line = (tcx, shelf_y)
    tc = (tcx, shelf_y + rt)
    d_t = math.hypot(tc[0] - L[0], tc[1] - L[1])
    t_hole = (L[0] + (tc[0] - L[0]) * R_HOLE / d_t,
              L[1] + (tc[1] - L[1]) * R_HOLE / d_t)

    # ── recorrido ──────────────────────────────────────────────────────
    out.append('M%s,%s' % (f(a_line[0]), f(a_line[1])))
    if fillet_tip > 0:                                        # punta redondeada
        out.append(arc_c(a_line, a_out, fc2, fillet_tip, ccw=False))
    # exterior R56: de A hasta el apice, pasando por abajo y por la derecha
    # el exterior se recorre en angulo DECRECIENTE: A -> izquierda -> abajo ->
    # derecha -> apice (232.6 grados). Medido sobre el original.
    a1, a2 = ang(L, a_out), ang(L, b_out)
    delta = (a1 - a2) % 360
    out.append(arc(b_out, R_OUT, 1 if delta > 180 else 0, 0))
    if fillet_apex > 0:                                       # apice redondeado
        out.append(arc_c(b_out, b_cr, fc, fillet_apex, ccw=False))
    # media luna, hacia dentro
    out.append(arc(P_out, r_cresc, 0, 1))
    if cap_t > 0:                                             # remate superior redondo
        # ccw=False -> el arco MENOR, el que mira a la esquina. Verificado
        # barriendo las 4 combinaciones (2 centros x 2 sentidos): es la unica
        # que no deja pico y la unica que no se come el hueco contra el cuerpo.
        out.append(arc_c(P_out, P_in, cap_c, cap_r, ccw=False))
    # agujero r24, la vuelta larga
    a3, a4 = ang(L, P_in), ang(L, t_hole)
    d2 = (a4 - a3) % 360
    out.append(arc(t_hole, R_HOLE, 1 if d2 > 180 else 0, 1))
    # fillet contra la repisa + linea de vuelta
    out.append(arc_c(t_hole, t_line, tc, rt, ccw=False))
    out.append('L%s,%s' % (f(a_line[0]), f(a_line[1])))
    out.append('Z')

    info = dict(A=A, B=B, P_out=P_out, P_in=P_in, t_hole=t_hole,
                gap=r_cresc - 84.0, slot=shelf_y - 168.0)
    return ' '.join(out), info


if __name__ == '__main__':
    d0, i0 = build(cap_t=0, fillet_apex=0, fillet_tip=0)
    print('REPRODUCCION del original:')
    print('  A=(%.2f,%.2f)  B=(%.2f,%.2f)  cuchillo=(%.2f,%.2f)' %
          (i0['A'][0], i0['A'][1], i0['B'][0], i0['B'][1], i0['P_out'][0], i0['P_out'][1]))
    print('  hueco media luna %.2f u · ranura %.2f u · %d chars' % (i0['gap'], i0['slot'], len(d0)))
    d1, i1 = build()
    print('CORREGIDO: %d chars' % len(d1))
