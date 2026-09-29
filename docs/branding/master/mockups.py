# -*- coding: utf-8 -*-
"""Las maquetas: la marca en las superficies donde de verdad se mira.

    cd docs/branding/master && python mockups.py   ->  ../explorations/maquetas.png

Lo consume `dossier.py`, que las incrusta en `../propuesta.html`. Aqui no se decide
nada: se DIBUJA lo que el maestro genera, en los tamanos y los fondos reales.

⛔ Los iconos pequenos van rasterizados a su tamano REAL y ampliados sin suavizar.
Es la unica comparacion honesta, y ya costo una decision equivocada: sobre el vector
-o sobre un <img> a 48 CSS px, que en una pantalla densa se pinta a 96 o 144 px
fisicos- gana siempre el dibujo con mas detalle, que es justo el que a 48 px de
verdad se emborrona.

Unica dependencia fuera de la stdlib: PIL.
"""
import math
import os

from PIL import Image, ImageDraw, ImageFont

import build_marks as B
from build_marks import _flatten

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'explorations', 'maquetas.png')
FONT = os.path.join(HERE, '..', '..', '..', 'shared', 'src', 'commonMain',
                    'composeResources', 'font', 'plus_jakarta_sans_variable.ttf')

DARK, LIGHT = (13, 17, 23), (240, 244, 251)
GREEN, FOREST, WHITE = (37, 244, 140), (0, 159, 94), (255, 255, 255)
PAGE = (11, 14, 18)
SS = 4                 # supermuestreo antes de bajar al tamano real
F_CIRCLE = 0.70        # el mismo que usa export_surfaces para el circulo visible


def _geo(slug):
    """Los contornos de una variante y su encuadre OPTICO."""
    sp = B._by_slug(slug)
    subs = [s for d in B.paths_of(sp)[0] for s in _flatten(d, 0.2)]
    r, ctr = B.optical_frame(sp)
    return subs, r, ctr


def _mask(subs, W, H, scale, tx, ty):
    """Scanline nonzero. Devuelve filas de booleanos."""
    edges = []
    for sp in subs:
        q = [(x * scale + tx, y * scale + ty) for x, y in sp]
        if q[0] != q[-1]:
            q.append(q[0])
        for a, b in zip(q, q[1:]):
            if a[1] != b[1]:
                edges.append((a[1], b[1], a[0], (b[0] - a[0]) / (b[1] - a[1])))
    out = [[False] * W for _ in range(H)]
    for j in range(H):
        y = j + 0.5
        xs = []
        for y0, y1, x0, m in edges:
            if (y0 <= y < y1) or (y1 <= y < y0):
                xs.append((x0 + (y - y0) * m, 1 if y1 > y0 else -1))
        if not xs:
            continue
        xs.sort()
        w, prev, row = 0, None, out[j]
        for x, d in xs:
            if w == 0:
                prev = x
            w += d
            if w == 0:
                lo = max(0, int(math.ceil(prev - 0.5)))
                hi = min(W - 1, int(math.floor(x - 0.5)))
                for i in range(lo, hi + 1):
                    row[i] = True
    return out


def draw(img, slug, cx, cy, r_px, color):
    """La marca centrada por su centro OPTICO en (cx, cy), con envolvente r_px."""
    subs, r, ctr = _geo(slug)
    W, H = img.size
    sc = r_px / r * SS
    m = _mask(subs, W * SS, H * SS, sc, cx * SS - ctr[0] * sc, cy * SS - ctr[1] * sc)
    lay = Image.new('L', (W * SS, H * SS), 0)
    px = lay.load()
    for y, row in enumerate(m):
        for x, v in enumerate(row):
            if v:
                px[x, y] = 255
    img.paste(Image.new('RGB', img.size, color), (0, 0),
              lay.resize((W, H), Image.LANCZOS))


def _font(size, weight=700):
    try:
        f = ImageFont.truetype(FONT, size)
        try:
            f.set_variation_by_axes([weight])
        except Exception:
            pass
        return f
    except Exception:
        return ImageFont.load_default()


def _tile(size, bg):
    img = Image.new('RGB', (size, size), PAGE)
    ImageDraw.Draw(img).rounded_rectangle([0, 0, size - 1, size - 1],
                                          radius=int(size * 0.23), fill=bg)
    return img


def _zoom(img, k):
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


def surfaces(slug):
    """Todas las maquetas de UNA variante: [(titulo, nota, imagen)]."""
    out = []

    t = _tile(48, DARK)
    draw(t, slug, 24, 24, 48 * F_CIRCLE / 2, GREEN)
    out.append(('Icono de app', '48 px reales, ampliado x5 sin suavizar', _zoom(t, 5)))

    a = _tile(240, DARK)
    draw(a, slug, 120, 120, 240 * F_CIRCLE / 2, GREEN)
    b = _tile(240, LIGHT)
    draw(b, slug, 120, 120, 240 * F_CIRCLE / 2, FOREST)
    duo = Image.new('RGB', (240 * 2 + 16, 240), PAGE)
    duo.paste(a, (0, 0))
    duo.paste(b, (256, 0))
    out.append(('Icono grande', 'tema oscuro y claro, cada uno con su verde de relleno', duo))

    n = Image.new('RGB', (24, 24), (24, 26, 30))
    draw(n, slug, 12, 12, 24 * 0.92 / 2, WHITE)
    out.append(('Small icon de notificacion', '24 px reales, monocromo, x8', _zoom(n, 8)))

    row = Image.new('RGB', (16 * 8 + 14 + 32 * 8, 32 * 8), PAGE)
    for i, s in enumerate((16, 32)):
        f = Image.new('RGB', (s, s), PAGE)
        draw(f, slug, s / 2.0, s / 2.0, s * 0.92 / 2, GREEN)
        z = _zoom(f, 8)
        row.paste(z, (0 if i == 0 else 16 * 8 + 14, (32 * 8 - z.height) // 2))
    out.append(('Favicon', '16 y 32 px reales, x8', row))

    sp_img = Image.new('RGB', (300, 420), LIGHT)
    draw(sp_img, slug, 150, 195, 64 * F_CIRCLE, FOREST)
    out.append(('Splash', 'fondo #F0F4FB, glifo en PapGreenLight', sp_img))

    lg = Image.new('RGB', (300, 420), DARK)
    draw(lg, slug, 150, 150, 58 * F_CIRCLE, GREEN)
    d = ImageDraw.Draw(lg)
    f = _font(34, 800)
    d.text(((300 - d.textlength('paparcar', font=f)) / 2, 232), 'paparcar', font=f, fill=WHITE)
    f2 = _font(14, 400)
    s2 = 'Aparca mas facil. Vive mejor.'
    d.text(((300 - d.textlength(s2, font=f2)) / 2, 280), s2, font=f2, fill=(150, 165, 180))
    d.rounded_rectangle([40, 340, 260, 382], radius=21, fill=GREEN)
    f3 = _font(16, 700)
    s3 = 'Comenzar'
    d.text(((300 - d.textlength(s3, font=f3)) / 2, 352), s3, font=f3, fill=(10, 30, 20))
    out.append(('Login', 'la marca con el wordmark y el CTA', lg))

    bn = Image.new('RGB', (560, 250), (8, 12, 16))
    draw(bn, slug, 120, 125, 62 * F_CIRCLE, GREEN)
    d = ImageDraw.Draw(bn)
    d.text((215, 82), 'paparcar', font=_font(46, 800), fill=WHITE)
    d.text((218, 140), 'Encuentra plaza. Ahorra tiempo.', font=_font(17, 500),
           fill=(140, 230, 185))
    out.append(('Branding publicitario', 'lockup horizontal sobre fondo oscuro', bn))
    return out


def pieces_at(slug, sizes=(48, 32, 24, 20, 16)):
    """Cuantos trozos SUELTOS quedan con la marca a N px de ALTO.

    Tres es el dibujo entero -cuerpo, rueda derecha y ojo-; dos significa que la
    rueda se ha fundido con el cuerpo y el canal ha desaparecido. La altura es la
    medida honesta porque es la que fija cada superficie; el ancho lo pone la
    proporcion del dibujo.
    """
    sp = B._by_slug(slug)
    subs, _r, _c = _geo(slug)
    CW, CH = B.canvas(B.compas_of(sp))
    out = []
    for S in sizes:
        sc = float(S) / CH * SS
        W = int(round(CW * S / CH))
        m = _mask(subs, W * SS, S * SS, sc, 0.0, 0.0)
        lay = Image.new('L', (W * SS, S * SS), 0)
        px = lay.load()
        for y, row in enumerate(m):
            for x, v in enumerate(row):
                if v:
                    px[x, y] = 255
        small = lay.resize((W, S), Image.LANCZOS).load()
        on = [[small[x, y] > 128 for x in range(W)] for y in range(S)]
        seen = [[False] * W for _ in range(S)]
        n = 0
        for y in range(S):
            for x in range(W):
                if not on[y][x] or seen[y][x]:
                    continue
                n += 1
                st = [(x, y)]
                seen[y][x] = True
                while st:
                    cx, cy = st.pop()
                    for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                        if 0 <= nx < W and 0 <= ny < S and on[ny][nx] and not seen[ny][nx]:
                            seen[ny][nx] = True
                            st.append((nx, ny))
        out.append((S, n))
    return out


def sheet(slugs=('adelgazada', 'ancestro')):
    """Las dos columnas, una fila por superficie, en un PNG."""
    cols = [surfaces(s) for s in slugs]
    PAD, LBL = 26, 24
    Wc = max(im.width for c in cols for _t, _n, im in c)
    W = Wc * len(cols) + PAD * (len(cols) + 1)
    hs = [max(c[i][2].height for c in cols) + LBL + PAD for i in range(len(cols[0]))]
    img = Image.new('RGB', (W, sum(hs) + PAD), PAGE)
    dr = ImageDraw.Draw(img)
    y = PAD
    for i, h in enumerate(hs):
        for j, c in enumerate(cols):
            tit, _nota, im = c[i]
            x = PAD + j * (Wc + PAD)
            img.paste(im, (x, y))
            dr.text((x, y + im.height + 6), '%s  %s' % (slugs[j], tit),
                    font=_font(14, 700), fill=(200, 212, 224))
        y += h
    return img


def main():
    img = sheet()
    p = os.path.abspath(OUT)
    img.save(p)
    print('  escrito  docs/branding/explorations/maquetas.png (%d x %d)' % img.size)


if __name__ == '__main__':
    main()
