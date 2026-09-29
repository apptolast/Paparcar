"""Vectoriza la FLUIDA desde su unico original: un raster.

    cd docs/branding/master && python vectorize_fluida.py

    contorno SUBPIXEL (marching squares sobre el alfa, umbral 50 %)
      -> remuestreo a paso constante
      -> filtro del temblor del antialiasing
      -> deteccion de esquinas por giro acumulado
      -> ajuste de cubicas (Schneider 1990) con tolerancia DECLARADA
      -> un solo path, regla nonzero

⛔ SIN el Compas de por medio, y eso es el encargo. Hubo tres vueltas metiendo la
Fluida en el modelo parametrico y todas dieron el mismo dibujo, porque el modelo no
puede representarla: alli la cabeza hace TRES trabajos con un solo radio -enrasar
arriba, poner el bajo del cuenco (2*R_HEAD) y dar el angulo de la espalda por
tangencia con la rueda- y la Fluida no cumple esa atadura. Medido: su espalda va a
54,2 grados y el modelo se quedaba en 46,9; forzarlo dejaba la pared del cuenco en
2,2 u de 33. **Un modelo que no puede representar un dibujo no es el camino para
reproducirlo.** Aqui no hay modelo: hay contorno.

⛔ Y el resultado es UN path con los cinco contornos dentro, no cinco paths. La
orientacion que devuelve marching squares ya es opuesta en los agujeros, asi que la
regla nonzero los abre sola. Emitirlos sueltos los UNE -los agujeros salen
rellenos- y el solape se desploma de 0,984 a 0,786. Lo aprendi midiendo, porque a
ojo el fallo se ve pero no se sabe cuanto.

Unica dependencia fuera de la stdlib: PIL, para leer el raster.
"""
import io, math, os
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, '..', 'reference', 'source', 'paparcar-mark-fluida-master.png')
OUT = os.path.join(HERE, '..', 'reference', 'paparcar-mark-fluida.svg')

T = 127.5              # el borde: alfa al 50 %
UNITS = 248.0          # ancho del lienzo, para hablar en las unidades del resto del branding
STEP = 0.55            # remuestreo
MEDIAN = 5             # limpieza del alfa ANTES de trazar
SMOOTH_W, SMOOTH_P = 0, 0   # suavizado del contorno: DESACTIVADO, ver abajo
CORNER_DEG, CORNER_WIN = 42.0, 5
TOL = 1.80

# ⛔ La TOLERANCIA es lo que fairea la curva, y por eso es alta. Con 0,30 salian 174
# cubicas -mas que los 185 puntos del calco que esto sustituye- persiguiendo el ruido
# del borde; con 1,80 son ~38 y las curvas son limpias. Las cifras se recalculan en
# cada pasada, no se copian.
#
# ⚠️⚠️ TECHO DEL ORIGINAL. Por encima de esto no se puede ir con este fichero: su
# borde trae ruido de compresion y sus formas ya vienen con bultos -el ojo es un
# cuadrado redondeado, el bajo del cuenco ondula-. Eso NO es ruido que se pueda
# filtrar, es el dibujo que hay. Cualquier version mas limpia que esta seria yo
# inventandome la Fluida, no reproduciendola. Con un original mejor -mas resolucion,
# sin compresion, o el vectorial si aparece- este mismo pipeline da otra cosa.


def alpha():
    """El alfa del original, con la mota quitada ANTES de trazar.

    ⛔ El borde de este raster no esta pixelado: esta COMPRIMIDO. Al 300 % se ven
    manchas blancas y magenta sueltas metidas en el canal alfa, y trazar el umbral
    del 50 % sobre eso persigue artefactos de compresion, no un dibujo. Una mediana
    los borra y NO desplaza el borde -no es una media-, que es justo lo que hace
    falta: los agujeros conservan su radio al centesimo (ojo 14,93 · rueda 23,11).

    ⛔⛔ Lo que NO se hace es suavizar el contorno despues. Se probo y se midio: una
    media movil sobre una curva cerrada tira hacia DENTRO, y con ventana 14 el ojo
    perdia el 17 % de su radio (14,94 -> 12,36). El ajuste por minimos cuadrados
    sobre tramos largos suaviza sin ese sesgo, asi que el suavizado esta a cero y
    quien hace el trabajo es la TOLERANCIA."""
    im = Image.open(SRC)
    a = im.split()[3]
    if MEDIAN:
        a = a.filter(ImageFilter.MedianFilter(MEDIAN))
    W, H = im.size
    px = a.load()
    return [[float(px[x, y]) for x in range(W)] for y in range(H)], W, H


def _ip(v0, v1):
    """Donde cruza el umbral entre dos muestras vecinas."""
    if v1 == v0:
        return 0.5
    return (T - v0) / (v1 - v0)


def segments(A, W, H):
    """Marching squares con interpolacion. Devuelve segmentos (p, q) orientados
    con la TINTA a la izquierda, para que los contornos salgan coherentes."""
    segs = []
    for y in range(H - 1):
        for x in range(W - 1):
            a, b = A[y][x], A[y][x + 1]
            d, c = A[y + 1][x], A[y + 1][x + 1]
            k = (1 if a > T else 0) | (2 if b > T else 0) | (4 if c > T else 0) | (8 if d > T else 0)
            if k == 0 or k == 15:
                continue
            top = (x + _ip(a, b), y)
            right = (x + 1, y + _ip(b, c))
            bottom = (x + _ip(d, c), y + 1)
            left = (x, y + _ip(a, d))
            E = {1: (left, top), 2: (top, right), 3: (left, right),
                 4: (right, bottom), 6: (top, bottom), 7: (left, bottom),
                 8: (bottom, left), 9: (bottom, top), 11: (bottom, right),
                 12: (right, left), 13: (right, top), 14: (top, left)}
            if k in (5, 10):
                # silla de montar: se resuelve con el centro, no a ojo
                mid = (a + b + c + d) / 4.0
                if (k == 5) == (mid > T):
                    segs.append((left, top)); segs.append((right, bottom))
                else:
                    segs.append((left, bottom)); segs.append((right, top))
                continue
            segs.append(E[k])
    return segs


def chains(segs, eps=1e-6):
    """Encadena los segmentos en curvas CERRADAS."""
    def key(p):
        return (round(p[0], 4), round(p[1], 4))
    nxt = {}
    for p, q in segs:
        nxt.setdefault(key(p), []).append((p, q))
    loops = []
    used = set()
    for i, (p0, q0) in enumerate(segs):
        if i in used:
            continue
        # recorre
        loop = [p0, q0]
        cur = q0
        guard = 0
        while guard < 400000:
            guard += 1
            cands = nxt.get(key(cur))
            if not cands:
                break
            nxtseg = None
            for s in cands:
                j = segs.index(s) if False else None
                nxtseg = s
                break
            if nxtseg is None:
                break
            cands.remove(nxtseg)
            cur = nxtseg[1]
            if key(cur) == key(p0):
                loop.append(cur)
                break
            loop.append(cur)
        if len(loop) > 20:
            loops.append(loop)
    loops.sort(key=len, reverse=True)
    return loops


def sub(a, b): return (a[0] - b[0], a[1] - b[1])
def add(a, b): return (a[0] + b[0], a[1] + b[1])
def mul(a, s): return (a[0] * s, a[1] * s)
def dot(a, b): return a[0] * b[0] + a[1] * b[1]
def norm(a):
    m = math.hypot(a[0], a[1])
    return (a[0] / m, a[1] / m) if m else (0.0, 0.0)


def B(u):
    v = 1 - u
    return (v * v * v, 3 * v * v * u, 3 * v * u * u, u * u * u)


def bez_at(c, u):
    b = B(u)
    return (sum(c[i][0] * b[i] for i in range(4)), sum(c[i][1] * b[i] for i in range(4)))


def bez_d(c, u):
    v = 1 - u
    return (3 * v * v * (c[1][0] - c[0][0]) + 6 * v * u * (c[2][0] - c[1][0])
            + 3 * u * u * (c[3][0] - c[2][0]),
            3 * v * v * (c[1][1] - c[0][1]) + 6 * v * u * (c[2][1] - c[1][1])
            + 3 * u * u * (c[3][1] - c[2][1]))


def chord_param(pts):
    u = [0.0]
    for i in range(1, len(pts)):
        u.append(u[-1] + math.hypot(*sub(pts[i], pts[i - 1])))
    L = u[-1] or 1.0
    return [v / L for v in u]


def generate(pts, u, t1, t2):
    """Minimos cuadrados para las dos distancias de los tiradores."""
    n = len(pts)
    C = [[0.0, 0.0], [0.0, 0.0]]
    X = [0.0, 0.0]
    for i in range(n):
        b = B(u[i])
        a1 = mul(t1, b[1])
        a2 = mul(t2, b[2])
        C[0][0] += dot(a1, a1); C[0][1] += dot(a1, a2)
        C[1][0] += dot(a1, a2); C[1][1] += dot(a2, a2)
        tmp = sub(pts[i], (pts[0][0] * (b[0] + b[1]) + pts[-1][0] * (b[2] + b[3]),
                           pts[0][1] * (b[0] + b[1]) + pts[-1][1] * (b[2] + b[3])))
        X[0] += dot(a1, tmp); X[1] += dot(a2, tmp)
    det = C[0][0] * C[1][1] - C[1][0] * C[0][1]
    d1 = d2 = 0.0
    if abs(det) > 1e-12:
        d1 = (X[0] * C[1][1] - X[1] * C[0][1]) / det
        d2 = (C[0][0] * X[1] - C[1][0] * X[0]) / det
    seg = math.hypot(*sub(pts[-1], pts[0]))
    if d1 < 1e-6 or d2 < 1e-6:
        d1 = d2 = seg / 3.0
    return [pts[0], add(pts[0], mul(t1, d1)), add(pts[-1], mul(t2, d2)), pts[-1]]


def max_error(pts, c, u):
    worst, idx = 0.0, len(pts) // 2
    for i in range(1, len(pts) - 1):
        d = math.hypot(*sub(bez_at(c, u[i]), pts[i]))
        if d > worst:
            worst, idx = d, i
    return worst, idx


def reparam(pts, c, u):
    out = []
    for i, p in enumerate(pts):
        ui = u[i]
        d = sub(bez_at(c, ui), p)
        d1 = bez_d(c, ui)
        v = 1 - ui
        d2 = (6 * v * (c[2][0] - 2 * c[1][0] + c[0][0]) + 6 * ui * (c[3][0] - 2 * c[2][0] + c[1][0]),
              6 * v * (c[2][1] - 2 * c[1][1] + c[0][1]) + 6 * ui * (c[3][1] - 2 * c[2][1] + c[1][1]))
        den = dot(d1, d1) + dot(d, d2)
        out.append(ui if abs(den) < 1e-12 else ui - dot(d, d1) / den)
    return out


def fit(pts, t1, t2, tol, depth=0):
    """t1/t2 apuntan HACIA DENTRO de la curva desde cada extremo."""
    if len(pts) < 3:
        seg = math.hypot(*sub(pts[-1], pts[0])) / 3.0
        return [[pts[0], add(pts[0], mul(t1, seg)), add(pts[-1], mul(t2, seg)), pts[-1]]]
    u = chord_param(pts)
    c = generate(pts, u, t1, t2)
    err, idx = max_error(pts, c, u)
    if err < tol:
        return [c]
    if err < tol * 4 and depth < 24:
        for _ in range(12):
            u = reparam(pts, c, u)
            c = generate(pts, u, t1, t2)
            err, idx = max_error(pts, c, u)
            if err < tol:
                return [c]
    if depth > 26 or idx <= 0 or idx >= len(pts) - 1:
        return [c]
    ct = norm(sub(pts[idx - 1], pts[idx + 1]))
    return (fit(pts[:idx + 1], t1, ct, tol, depth + 1)
            + fit(pts[idx:], (-ct[0], -ct[1]), t2, tol, depth + 1))


def resample(poly, step):
    """Paso constante. El contorno de marching squares tiene muestras muy juntas
    en las diagonales y separadas en las rectas; sin esto, la deteccion de
    esquinas mide densidad de muestreo en vez de curvatura."""
    out = [poly[0]]
    acc = 0.0
    for i in range(1, len(poly)):
        a, b = poly[i - 1], poly[i]
        d = math.hypot(b[0] - a[0], b[1] - a[1])
        if d < 1e-12:
            continue
        while acc + d >= step:
            t = (step - acc) / d
            a = (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
            out.append(a)
            d = math.hypot(b[0] - a[0], b[1] - a[1])
            acc = 0.0
        acc += d
    return out


def smooth(poly, w=3, passes=2):
    """Media movil a lo largo del contorno cerrado.

    ⛔ No es cosmetica: el raster viene antialiased y su borde TIEMBLA una fraccion
    de pixel. Ajustar cubicas a ese temblor con tolerancia fina gasta segmentos en
    reproducir ruido -174 cubicas para cinco contornos- y ademas deja ondulaciones
    que el dibujo original no tiene. El filtro es simetrico, asi que no desplaza el
    contorno: solo le quita la frecuencia que el papel no lleva."""
    n = len(poly)
    out = poly
    for _ in range(passes):
        cur = out
        out = []
        for i in range(n):
            sx = sy = 0.0
            for k in range(-w, w + 1):
                p = cur[(i + k) % n]
                sx += p[0]; sy += p[1]
            out.append((sx / (2 * w + 1), sy / (2 * w + 1)))
    return out


def corners(poly, deg=CORNER_DEG, w=CORNER_WIN):
    """Esquina = giro acumulado en una ventana corta. Mirar un solo vertice mide
    ruido de remuestreo; la ventana mide si el giro se CONCENTRA."""
    n = len(poly)
    out = []
    for i in range(n):
        a = poly[(i - w) % n]; b = poly[i]; c = poly[(i + w) % n]
        v1 = (b[0] - a[0], b[1] - a[1]); v2 = (c[0] - b[0], c[1] - b[1])
        m1 = math.hypot(*v1); m2 = math.hypot(*v2)
        if m1 < 1e-9 or m2 < 1e-9:
            continue
        cos = max(-1.0, min(1.0, (v1[0] * v2[0] + v1[1] * v2[1]) / (m1 * m2)))
        if math.degrees(math.acos(cos)) > deg:
            out.append(i)
    # agrupa los indices contiguos y se queda con uno por grupo
    if not out:
        return []
    groups, cur = [], [out[0]]
    for i in out[1:]:
        if i - cur[-1] <= w:
            cur.append(i)
        else:
            groups.append(cur); cur = [i]
    groups.append(cur)
    if len(groups) > 1 and (groups[0][0] + n - groups[-1][-1]) <= w:
        groups[0] = groups[-1] + groups[0]; groups.pop()
    return sorted(g[len(g) // 2] % n for g in groups)


def tangent(poly, i, w=4):
    n = len(poly)
    a = poly[(i - w) % n]; b = poly[(i + w) % n]
    return norm((b[0] - a[0], b[1] - a[1]))


def fit_loop(poly, tol=TOL):
    """Devuelve la lista de cubicas de un contorno cerrado."""
    n = len(poly)
    br = corners(poly)
    if len(br) < 2:
        br = [0, n // 4, n // 2, 3 * n // 4]     # sin esquinas: cuatro tramos
    out = []
    for k in range(len(br)):
        i, j = br[k], br[(k + 1) % len(br)]
        pts = poly[i:j + 1] if j > i else poly[i:] + poly[:j + 1]
        if len(pts) < 3:
            continue
        t1 = norm((pts[1][0] - pts[0][0], pts[1][1] - pts[0][1]))
        t2 = norm((pts[-2][0] - pts[-1][0], pts[-2][1] - pts[-1][1]))
        # en una esquina el tangente es el del propio tramo; en un corte
        # artificial, el del contorno, que es lo que da G1.
        if len(br) == 4 and br == [0, n // 4, n // 2, 3 * n // 4]:
            t1 = tangent(poly, i)
            t2 = norm((-tangent(poly, j)[0], -tangent(poly, j)[1]))
        out.extend(fit(pts, t1, t2, tol))
    return out


def deviation(cubics, poly):
    """Cuanto se aparta el ajuste del contorno REAL. Es la cifra que dice si esto
    es una vectorizacion o un dibujo parecido."""
    samples = []
    for c in cubics:
        for k in range(1, 13):
            samples.append(bez_at(c, k / 13.0))
    worst = 0.0; tot = 0.0
    step = max(1, len(poly) // 1500)
    P = poly[::step]
    for s in samples:
        d = min(math.hypot(s[0] - p[0], s[1] - p[1]) for p in P)
        worst = max(worst, d); tot += d * d
    return worst, math.sqrt(tot / len(samples))


def f(v):
    return ('%.2f' % v).rstrip('0').rstrip('.')


def path_of(cubics):
    d = 'M%s,%s' % (f(cubics[0][0][0]), f(cubics[0][0][1]))
    for c in cubics:
        d += 'C%s,%s %s,%s %s,%s' % (f(c[1][0]), f(c[1][1]), f(c[2][0]), f(c[2][1]),
                                     f(c[3][0]), f(c[3][1]))
    return d + 'Z'




SVG = """<svg xmlns="http://www.w3.org/2000/svg" width="%(W)d" height="%(H)d" viewBox="0 0 %(W)g %(H)g" fill="#25F48C">
  <!-- Paparcar - la FLUIDA, vectorizada. SALIDA de master/vectorize_fluida.py.

       La Fluida nunca se dibujo en vectorial: su unico original es un raster, y lo
       que habia antes era un CALCO (marching squares + Douglas-Peucker +
       Catmull-Rom, 185 puntos repartidos por el trazador, sin tolerancia declarada).
       Esto lo sustituye: %(N)d cubicas ajustadas por minimos cuadrados con una
       tolerancia de %(TOL)s u y desvio maximo medido %(MAX).2f u contra el contorno
       real: %(PX).1f px del propio raster, por debajo de su desenfoque.

       ⛔ El SOLAPE no se cita aqui. Lo mide y lo publica `compare_fluida.py`, que es
       quien dibuja la pagina de comparacion. Tenerlo en los dos sitios daba dos
       cifras distintas para la misma pregunta -0,968 y 0,962, por como cada uno
       remuestrea la rejilla- y dos numeros para lo mismo es peor que uno solo.

       ⛔ NO se construyo con el modelo parametrico del Compas. Se intento tres
       veces y no puede: alli la cabeza hace tres trabajos con un solo radio y la
       Fluida no cumple esa atadura -su espalda va a 54,2 grados y el modelo se
       quedaba en 46,9-. Esto es su contorno, no una aproximacion con nuestras
       reglas.

       ⛔ UN path con los cinco contornos y regla nonzero: el cuerpo, la rueda
       derecha, el ojo, la contraforma en gota y el agujero de la rueda izquierda.
       Emitirlos sueltos los une y los agujeros salen rellenos. -->
  <path d="%(D)s"/>
</svg>
"""


def main():
    res, CW, CH, polys = build()
    S_PX = UNITS / 1044.0
    one = ' '.join(path_of(c) for _n, c, _w, _r, _s in res)
    n = sum(len(c) for _n, c, _w, _r, _s in res)
    mx = max(w for _n, _c, w, _r, _s in res)
    iou = overlap(one, CW, CH)
    print('lienzo %.2f x %.2f (ratio %.4f)' % (CW, CH, CW / CH))
    for name, c, w, r, _s in res:
        print('   %-34s %3d cubicas · desvio max %.3f u · RMS %.3f u' % (name, len(c), w, r))
    print('   TOTAL %d cubicas · %d caracteres de path' % (n, len(one)))
    print('   desvio maximo %.3f u (%.1f px del raster) · solape con el original %.4f'
          % (mx, mx / S_PX, iou))
    # Guarda de cordura, no cifra publicable: si esto cae, el trazado se rompio.
    assert iou > 0.94, 'el solape cayo a %.4f: algo se rompio en el trazado' % iou
    p = os.path.abspath(OUT)
    io.open(p, 'w', encoding='utf-8', newline='\n').write(
        SVG % dict(W=int(round(CW)), H=int(round(CH)), N=n, TOL=('%g' % TOL),
                   MAX=mx, PX=mx / S_PX, IOU=iou, D=one))
    print('  escrito  docs/branding/reference/paparcar-mark-fluida.svg')


def build():
    A, W, H = alpha()
    loops = chains(segments(A, W, H))
    xs = [p[0] for L in loops for p in L]; ys = [p[1] for L in loops for p in L]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    # ⛔ `+1`: el bbox de un raster son PIXELES, y de x0 a x1 hay (x1-x0+1) de ancho.
    # Normalizar por (x1-x0) y muestrear por (x1-x0+1) es medio pixel de incoherencia
    # -bastaba para que este generador y `compare_fluida` dieran dos solapes distintos
    # (0,9712 y 0,9659) a la misma pregunta.
    S = UNITS / (x1 - x0 + 1)
    CW, CH = UNITS, (y1 - y0 + 1) * S
    names = ['el cuerpo', 'la rueda derecha', 'la contraforma en gota',
             'el agujero de la rueda izquierda', 'el ojo']
    res, polys = [], []
    for k, L in enumerate(loops):
        poly = [((p[0] - x0) * S, (p[1] - y0) * S) for p in L]
        if math.hypot(poly[0][0] - poly[-1][0], poly[0][1] - poly[-1][1]) < 1e-6:
            poly = poly[:-1]
        rs = resample(poly, STEP)
        if SMOOTH_W:
            rs = smooth(rs, SMOOTH_W, SMOOTH_P)
        cub = fit_loop(rs)
        w, r = deviation(cub, poly)
        res.append((names[k] if k < len(names) else 'pieza %d' % k, cub, w, r, len(rs)))
        polys.append(poly)
    return res, CW, CH, polys


def overlap(d, CW, CH, G=300):
    """Solape con el raster. Es la cifra que separa una vectorizacion de un dibujo
    parecido, y por eso se mide aqui y no se copia."""
    im = Image.open(os.path.abspath(SRC))
    a = im.split()[3]; W, H = im.size; px = a.load()
    on = [[px[x, y] > 128 for x in range(W)] for y in range(H)]
    xs = [x for y in range(H) for x in range(W) if on[y][x]]
    ys = [y for y in range(H) for x in range(W) if on[y][x]]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    GH = int(round(G * (y1 - y0 + 1) / float(x1 - x0 + 1)))
    segs = []
    sx, sy = G / CW, GH / CH
    for sub in _poly_of(d):
        q = [(p[0] * sx, p[1] * sy) for p in sub]
        for u, v in zip(q, q[1:]):
            if u[1] != v[1]:
                segs.append((u[0], u[1], v[0], v[1]))
    inter = union = 0
    for j in range(GH):
        y = j + 0.5
        hits = []
        for X0, Y0, X1, Y1 in segs:
            if (Y0 <= y < Y1) or (Y1 <= y < Y0):
                t = (y - Y0) / (Y1 - Y0)
                hits.append((X0 + t * (X1 - X0), 1 if Y1 > Y0 else -1))
        hits.sort()
        row = [False] * G
        w, prev = 0, None
        for x, dn in hits:
            if w == 0:
                prev = x
            w += dn
            if w == 0:
                for i in range(max(0, int(math.ceil(prev - .5))), min(G - 1, int(math.floor(x - .5))) + 1):
                    row[i] = True
        ry = on[int(y0 + (j + .5) * (y1 - y0 + 1) / GH)]
        for i in range(G):
            t = ry[int(x0 + (i + .5) * (x1 - x0 + 1) / G)]
            if t or row[i]:
                union += 1
                if t and row[i]:
                    inter += 1
    return inter / float(union)


def _poly_of(d, n=16):
    """Aplana el path de cubicas emitido aqui mismo. Solo M/C/Z."""
    import re
    toks = re.findall(r'([MCZ])|(-?\d*\.?\d+)', d)
    toks = [t[0] or t[1] for t in toks]
    subs, cur, pos, start = [], [], (0.0, 0.0), None
    i = 0
    while i < len(toks):
        t = toks[i]
        if t == 'M':
            if cur:
                subs.append(cur)
            pos = (float(toks[i + 1]), float(toks[i + 2])); cur = [pos]; start = pos; i += 3
        elif t == 'C':
            i += 1
            while i < len(toks) and not toks[i].isalpha():
                v = [float(x) for x in toks[i:i + 6]]; i += 6
                p0 = pos
                for k in range(1, n + 1):
                    u = k / float(n); w_ = 1 - u
                    cur.append((w_**3 * p0[0] + 3 * w_*w_*u * v[0] + 3 * w_*u*u * v[2] + u**3 * v[4],
                                w_**3 * p0[1] + 3 * w_*w_*u * v[1] + 3 * w_*u*u * v[3] + u**3 * v[5]))
                pos = (v[4], v[5])
        else:
            i += 1
            if cur:
                cur.append(start); subs.append(cur); cur = []
            pos = start
    if cur:
        subs.append(cur)
    return subs


if __name__ == '__main__':
    main()
