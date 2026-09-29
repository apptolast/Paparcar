"""Mide la Fluida vectorizada contra su raster. Lo usa `dossier.py`.

    cd docs/branding/master && python compare_fluida.py   # solo imprime la medida

⛔ Ya NO escribe pagina propia. La escribia -`fluida-vs-compas.html`- y acabo
habiendo tres HTML contando cada uno un trozo de la misma decision, que es la
version en web del problema que abrio este ticket. Ahora aporta la MEDIDA y la
pagina es una: `../propuesta.html`.

⛔ Esto vive AQUI y no en un script suelto. La pagina es salida, igual que los SVG,
y un generador que vive fuera del repo produce exactamente el fichero que nadie
sabe de donde sale -que es el problema entero que este maestro existe para tapar.

⛔ Y el solape se MIDE aqui en cada pasada, no se copia. Era un `FLUIDA_IOU = 0.8925`
escrito a mano, o sea la misma familia que el `MEC_R` de `export_surfaces`: un
numero que sobrevive mudo al dibujo que lo produjo. Ahora se recalcula y se compara
con lo que el doc promete; si alguien mueve un parametro de `FLUIDA`, salta.

Unica dependencia fuera de la stdlib: PIL, para leer el raster de la Fluida — que
es la razon de ser del fichero, porque ese raster es su unico original.
"""
import base64, io, math, os, re
from PIL import Image

from build_marks import _flatten
# ⛔ La Fluida es todo CUBICAS y `_flatten` del maestro solo entiende M/L/A -los
# paths del Compas no tienen otra cosa-. Reusarlo aqui corrompia la mascara en
# silencio: el solape bajaba a 0,956 y parecia un problema del trazado. Lo cazo el
# assert de abajo, no el ojo.
from vectorize_fluida import _poly_of

FLUIDA_SVG = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                          '..', 'reference', 'paparcar-mark-fluida.svg')

HERE = os.path.dirname(os.path.abspath(__file__))
RASTER = os.path.join(HERE, '..', 'reference', 'source', 'paparcar-mark-fluida-master.png')

# ⛔ El lienzo de la Fluida se LEE de su SVG, no se escribe aqui. Lo tenia a mano
# (219,49 contra los 219,54 reales) y bastaba ese medio punto para que esta pagina
# y `vectorize_fluida` dieran dos solapes distintos -0,9659 y 0,9712- para la misma
# pregunta. Dos numeros para lo mismo es peor que no tener ninguno.
PX = 300          # el MISMO grid que usa vectorize_fluida


def _canvas():
    t = io.open(os.path.abspath(FLUIDA_SVG), encoding='utf-8').read()
    vb = [float(v) for v in re.search(r'viewBox="([-\d. ]+)"', t).group(1).split()]
    return vb[2], vb[3]


FW, FH = _canvas()
GW, GH = PX, int(round(PX * FH / FW))

# Lo que el doc promete. Si el ajuste cambia, esto salta en vez de envejecer callado.
CLAIMED_IOU = 0.9621   # ESTA es la cifra publicable: se mide aqui y aqui se publica


def fluida_mask():
    """La Fluida, remuestreada a la rejilla de comparacion."""
    im = Image.open(RASTER)
    a = im.split()[3]
    W, H = im.size
    px = a.load()
    on = [[px[x, y] > 128 for x in range(W)] for y in range(H)]
    xs = [x for y in range(H) for x in range(W) if on[y][x]]
    ys = [y for y in range(H) for x in range(W) if on[y][x]]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    out = []
    for j in range(GH):
        sy = int(y0 + (j + 0.5) * (y1 - y0 + 1) / GH)
        out.append([on[sy][int(x0 + (i + 0.5) * (x1 - x0 + 1) / GW)] for i in range(GW)])
    return out


def fluida_vector():
    """La Fluida VECTORIZADA (un path de cubicas) y su lienzo."""
    t = io.open(os.path.abspath(FLUIDA_SVG), encoding='utf-8').read()
    d = re.findall(r'<path d="([^"]+)"', t)[0]
    vb = [float(v) for v in re.search(r'viewBox="([-\d. ]+)"', t).group(1).split()]
    return d, vb[2], vb[3]


def mask_of_path(d, CW, CH):
    """El candidato, rasterizado en EL MISMO marco."""
    sx, sy = GW / CW, GH / CH
    segs = []
    for _ in (0,):
        for sub in _poly_of(d):
            q = [(x * sx, y * sy) for x, y in sub]
            for a, b in zip(q, q[1:]):
                if a[1] != b[1]:
                    segs.append((a[0], a[1], b[0], b[1]))
    out = [[False] * GW for _ in range(GH)]
    for j in range(GH):
        y = j + 0.5
        xs = []
        for X0, Y0, X1, Y1 in segs:
            if (Y0 <= y < Y1) or (Y1 <= y < Y0):
                t = (y - Y0) / (Y1 - Y0)
                xs.append((X0 + t * (X1 - X0), 1 if Y1 > Y0 else -1))
        if not xs:
            continue
        xs.sort()
        w, prev, row = 0, None, out[j]
        for x, dirn in xs:
            if w == 0:
                prev = x
            w += dirn
            if w == 0:
                a, b = int(math.ceil(prev - 0.5)), int(math.floor(x - 0.5))
                for i in range(max(0, a), min(GW - 1, b) + 1):
                    row[i] = True
    return out


def overlay(target, cand, k=4):
    """Verde: coinciden. Rojo: solo el original. Azul: solo el redibujo."""
    img = Image.new('RGB', (GW * k, GH * k), (13, 17, 23))
    px = img.load()
    both = only_t = only_c = 0
    for j in range(GH):
        for i in range(GW):
            t, q = target[j][i], cand[j][i]
            if t and q:
                col = (37, 244, 140); both += 1
            elif t:
                col = (244, 63, 94); only_t += 1
            elif q:
                col = (56, 130, 246); only_c += 1
            else:
                continue
            for a in range(k):
                for b in range(k):
                    px[i * k + a, j * k + b] = col
    tot = float(both + only_t + only_c)
    return img, both / tot, only_t / tot, only_c / tot


def b64(img_or_path):
    buf = io.BytesIO()
    if isinstance(img_or_path, str):
        return 'data:image/png;base64,' + base64.b64encode(open(img_or_path, 'rb').read()).decode()
    img_or_path.save(buf, 'PNG')
    return 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()


def svg(sp, px_h, fill):
    ds, c = paths_of(sp)
    W, H = canvas(c)
    return ('<svg width="%.0f" height="%d" viewBox="0 0 %g %g" fill="%s">%s</svg>'
            % (px_h * W / H, px_h, W, H, fill, ''.join('<path d="%s"/>' % d for d in ds)))


def main():
    """Mide y reporta. No escribe nada: la pagina la monta `dossier.py`."""
    d, FCW, FCH = fluida_vector()
    target = fluida_mask()
    img, pb, pt, pc = overlay(target, mask_of_path(d, FCW, FCH))
    iou = pb / (pb + pt + pc)
    print('solape del VECTOR con su raster: IoU %.4f   (verde %.1f %% / rojo %.1f %% / azul %.1f %%)'
          % (iou, 100 * pb, 100 * pt, 100 * pc))
    assert abs(iou - CLAIMED_IOU) < 0.006, (
        'el solape medido (%.4f) ya no es el que promete el doc (%.4f)' % (iou, CLAIMED_IOU))


if __name__ == '__main__':
    main()
