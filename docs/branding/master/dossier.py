"""La propuesta de marca, entera, en UNA pagina.

    cd docs/branding/master && python dossier.py   ->  ../propuesta.html

⛔ Sustituye a `gallery.html` y a `fluida-vs-compas.html`. Tener tres paginas que
cuentan cada una un trozo de la misma decision es la version en HTML del problema
que abrio este ticket -tres dibujos llamandose todos "el logo"-: quien la abre no
sabe cual mirar, y al cambiar algo se actualiza una y envejecen dos.

Todo lo que sale aqui se MIDE al generar: los anillos y el canal salen del maestro,
las esquinas vivas de la auditoria, y el solape de la Fluida de rasterizar su vector
contra su raster. Ninguna cifra esta escrita a mano en la plantilla.
"""
import io, os

import build_marks as B
import compare_fluida as C
import fluida as FR
import mockups as MK

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'propuesta.html')

GREEN, FOREST, INK = '#25F48C', '#009F5E', '#0D1117'


def svg_of(paths, W, H, px, fill, mirror=False):
    body = ''.join('<path d="%s"/>' % d for d in paths)
    if mirror:
        body = '<g transform="translate(%g,0) scale(-1,1)">%s</g>' % (W, body)
    return ('<svg width="%.0f" height="%d" viewBox="0 0 %g %g" fill="%s">%s</svg>'
            % (px * W / H, px, W, H, fill, body))


def variant_svg(sp, px, fill):
    mt = B.metrics(sp)
    return svg_of(mt['paths'], mt['w'], mt['h'], px, fill, mt['mirror'])


CSS = """
:root{color-scheme:dark}
*{box-sizing:border-box}
body{margin:0;padding:0 0 80px;background:#0D1117;color:#E6EDF3;
     font:15px/1.65 -apple-system,Segoe UI,Roboto,sans-serif}
.wrap{max-width:1040px;margin:0 auto;padding:0 40px}
header{padding:64px 0 8px}
h1{font-size:34px;margin:0 0 8px;letter-spacing:-.4px}
h2{font-size:21px;margin:56px 0 4px;letter-spacing:-.2px}
h3{font-size:16px;margin:30px 0 4px;color:#C9D1D9}
.sub{color:#8B949E;max-width:74ch;margin:0 0 6px}
.rule{height:1px;background:#21262D;margin:44px 0 0}
.hero{display:flex;gap:40px;align-items:center;flex-wrap:wrap;margin:26px 0 8px}
.dark{background:#0D1117;border:1px solid #21262D;border-radius:14px;padding:22px}
.light{background:#F0F4FB;border-radius:14px;padding:22px}
.row{display:flex;gap:30px;align-items:flex-end;flex-wrap:wrap;margin:18px 0}
.cell{text-align:center}
.cell .lab{color:#6E7681;font-size:12px;margin-top:9px}
table{border-collapse:collapse;font-size:14px;margin:12px 0 0;width:100%}
th,td{padding:7px 18px 7px 0;text-align:left;border-bottom:1px solid #21262D;vertical-align:top}
th{color:#8B949E;font-weight:600;font-size:13px}
td.n{font-variant-numeric:tabular-nums;white-space:nowrap}
.key{color:#25F48C}.warn{color:#F0B72F}.mut{color:#8B949E}
code{background:#161B22;padding:1px 5px;border-radius:4px;font-size:13px}
.var{border-top:1px solid #21262D;padding:22px 0}
.var .hd{display:flex;align-items:baseline;gap:12px}
.var .n{color:#6E7681;font-variant-numeric:tabular-nums}
.var .why{color:#8B949E;max-width:80ch;margin:4px 0 14px}
.pick{color:#25F48C;font-weight:600}
.badge{font-size:11px;border:1px solid #21262D;border-radius:999px;padding:2px 9px;color:#8B949E}
.ok{color:#25F48C;border-color:#1B4332}
img{max-width:100%;border-radius:12px;border:1px solid #21262D;display:block}
.legend span{display:inline-block;margin-right:20px;font-size:13px;color:#8B949E}
.dot{display:inline-block;width:10px;height:10px;border-radius:2px;margin-right:6px}
"""


def head_block():
    CB = B._by_slug(B.CHOSEN_SLUG)          # la elegida: la COMPLETA
    COMP = B._by_slug('compacta')           # la compacta, que no se eligio
    m = B.metrics(CB)
    sizes = ''.join(
        '<div class="cell"><div class="dark">%s</div><div class="lab">%d px</div></div>'
        % (variant_svg(CB, px, GREEN), px) for px in (96, 48, 32, 24))
    return """
<h2>1 &middot; La propuesta</h2>
<p class="sub">El <strong>Comp&aacute;s B+</strong>: la P de Paparcar dibujada a regla.
Tres lecturas a la vez &mdash; <strong>P</strong> de parking, el <strong>punto de
ubicaci&oacute;n</strong> en el hueco, y un <strong>coche</strong> de perfil. Es <em>generada</em>,
no dibujada: seis circunferencias, sus tangentes y sus acuerdos.</p>
<div class="hero">
  <div class="dark">%(big)s</div>
  <div class="light">%(big_l)s</div>
  <div>
    <table>
      <tr><td class="mut">lienzo</td><td class="n">%(w).0f &times; %(h).0f &middot; %(ratio).3f</td></tr>
      <tr><td class="mut">anillos (cabeza / rueda / pata)</td><td class="n">%(r0).0f / %(r1).0f / %(r2).1f</td></tr>
      <tr><td class="mut">canal de la rueda derecha</td><td class="n">%(channel)s u, igual por los dos lados</td></tr>
      <tr><td class="mut">esquinas vivas</td><td class="n"><span class="key">%(corn)d</span></td></tr>
      <tr><td class="mut">descripci&oacute;n</td><td class="n">%(chars)d caracteres de path</td></tr>
    </table>
  </div>
</div>
<div class="row">%(sizes)s</div>

<h3>Una sola marca, a prop&oacute;sito</h3>
<p class="sub">Esta es la forma <strong>completa</strong>, y es la &uacute;nica: la misma a todos los
tama&ntilde;os. Se descartan las dos reglas que hubo antes &mdash;dos dibujos con un umbral en
32&nbsp;dp, y quedarse con la compacta para todo&mdash;, y ninguna superficie tiene que saber en
qu&eacute; lado de nada est&aacute;. La rueda derecha va en <strong>anillo abierto</strong>,
separada del cuerpo por un canal de 10&nbsp;u igual por los dos lados: ese hueco es lo que hace que
el dibujo se lea como una <strong>P</strong> y no como dos ruedas gemelas.</p>
<p class="sub">&#9888; <strong>El precio, dicho, y se paga abajo.</strong> El canal y la ranura de la
repisa miden ~1&nbsp;px a los 51 y a los 24&nbsp;px de icono, o sea que por debajo se cierran solos:
a 24&nbsp;px el dibujo se desmiga en <strong>tres trozos</strong> &mdash;uno es una miga de
6&nbsp;px&mdash; y a 20&nbsp;px ya ha perdido una rueda. Donde muerde es el small icon de
notificaci&oacute;n y los chips. Se asume a cambio de leer la P donde la marca se mira de verdad.</p>
<p class="sub">Decidido el 24-09 mirando las dos formas <strong>rasterizadas a sus p&iacute;xeles
reales</strong> y ampliadas sin suavizar. Sobre el vector no vale: en una pantalla densa el
navegador lo pinta a 2&times; o 3&times; y eso favorece a la completa m&aacute;s de lo que la
favorece un icono de verdad.</p>
<div class="row">
  <div class="cell"><div class="dark">%(comp)s</div>
    <div class="lab">la COMPACTA, que no se eligi&oacute; &middot; ruedas gemelas, sin canal</div></div>
  <div class="cell"><div class="dark">%(comp24)s</div><div class="lab">compacta a 24 px</div></div>
  <div class="cell"><div class="dark">%(cb24)s</div><div class="lab">la elegida a 24 px</div></div>
</div>
""" % dict(big=variant_svg(CB, 210, GREEN), big_l=variant_svg(CB, 210, FOREST),
           w=m['w'], h=m['h'], ratio=m['ratio'], r0=m['rings'][0], r1=m['rings'][1],
           r2=m['rings'][2], corn=len(B.corners(CB)), chars=m['chars'], sizes=sizes,
           channel=m['channel'],
           comp=variant_svg(COMP, 150, GREEN), comp24=variant_svg(COMP, 24, GREEN),
           cb24=variant_svg(CB, 24, GREEN))


SURFACES = """
<h2>2 &middot; D&oacute;nde va</h2>
<p class="sub">Once superficies, todas <strong>generadas</strong> desde el mismo dibujo por
<code>master/export_surfaces.py</code>. Antes el path estaba copiado a mano en 25 ficheros; por eso
la regla de compensaci&oacute;n &oacute;ptica ten&iacute;a que ser un par de n&uacute;meros
m&aacute;gicos, no hab&iacute;a de d&oacute;nde derivarlos.</p>
<table>
<tr><th>Superficie</th><th>Fichero</th><th>Verde</th></tr>
<tr><td>Icono adaptativo</td><td class="mut"><code>ic_launcher_foreground.xml</code></td><td class="n">#25F48C</td></tr>
<tr><td>Splash d&iacute;a / noche</td><td class="mut"><code>splash_glyph.xml</code></td><td class="n">#009F5E / #25F48C</td></tr>
<tr><td>Small icon de notificaci&oacute;n</td><td class="mut"><code>ic_notification_logo.xml</code></td><td class="n">lo ti&ntilde;e el sistema</td></tr>
<tr><td>Marca en la app (oscuro / claro / tintable)</td><td class="mut"><code>paparcar_iconmark_*.xml</code></td><td class="n">#25F48C / #009F5E / blanco</td></tr>
<tr><td>Icono del Dev Catalog</td><td class="mut"><code>mock/&hellip;/ic_launcher_foreground.xml</code></td><td class="n">#DDD6FE</td></tr>
<tr><td>Favicon</td><td class="mut"><code>hosting/public/favicon.svg</code></td><td class="n">seg&uacute;n el tema</td></tr>
<tr><td>Web y documentaci&oacute;n</td><td class="mut"><code>web/lib/mark.generated.mjs</code> &middot; <code>docs/assets/</code></td><td class="n">#25F48C</td></tr>
<tr><td>Gr&aacute;ficos de Play</td><td class="mut"><code>play-listing/assets/{icon,feature}.html</code></td><td class="n">#25F48C</td></tr>
</table>
<p class="sub" style="margin-top:12px">&#9888; <strong>El lienzo no es cuadrado</strong> y la marca
es full bleed: toca los cuatro bordes. Las superficies que se encuadran por su c&iacute;rculo
envolvente no se enteran, pero meterla en una caja cuadrada a pelo le corta la rueda derecha.</p>
"""


def variants_block():
    out = ['<h2>3 &middot; C&oacute;mo se lleg&oacute; aqu&iacute;</h2>',
           '<p class="sub">Cada ejemplo dice qu&eacute; cambia respecto al anterior. Le&iacute;dos '
           'en orden son la historia del dibujo. La columna de <em>esquinas vivas</em> no es una '
           'impresi&oacute;n: <code>build_marks.py</code> aplana cada contorno y busca d&oacute;nde '
           'el recorrido gira de golpe (&gt; 20&deg; en un punto).</p>']
    for i, (slug, title, why, sp) in enumerate(B.VARIANTS, 1):
        mt = B.metrics(sp)
        ks = len(B.corners(sp))
        chosen = slug == B.CHOSEN_SLUG
        badge = ('<span class="badge ok">0 esquinas vivas</span>' if ks == 0
                 else '<span class="badge">%d esquina%s viva%s</span>' % (ks, '' if ks == 1 else 's',
                                                                         '' if ks == 1 else 's'))
        out.append(
            '<section class="var">'
            '<div class="hd"><span class="n">%02d</span>'
            '<span class="%s">%s</span>%s</div>'
            '<p class="why">%s</p>'
            '<div class="row">%s%s%s</div>'
            '<table><tr>'
            '<td class="mut">lienzo <span class="n">%.0f&times;%.0f</span></td>'
            '<td class="mut">anillos <span class="n">%.0f/%.0f/%.1f</span></td>'
            '<td class="mut">canal <span class="n">%s</span></td>'
            '<td class="mut">path <span class="n">%d car</span></td>'
            '</tr></table></section>'
            % (i, 'pick' if chosen else '', title, badge, why,
               '<div class="cell"><div class="dark">%s</div><div class="lab">150 px</div></div>'
               % variant_svg(sp, 150, GREEN),
               '<div class="cell"><div class="light">%s</div><div class="lab">claro</div></div>'
               % variant_svg(sp, 96, FOREST),
               '<div class="cell"><div class="dark">%s</div><div class="lab">32 px</div></div>'
               % variant_svg(sp, 32, GREEN),
               mt['w'], mt['h'], mt['rings'][0], mt['rings'][1], mt['rings'][2],
               mt['channel'], mt['chars']))
    return '\n'.join(out)


def fluida_block():
    d, FW, FH = C.fluida_vector()
    target = C.fluida_mask()
    img, pb, pt, pc = C.overlay(target, C.mask_of_path(d, FW, FH))
    iou = pb / (pb + pt + pc)
    assert abs(iou - C.CLAIMED_IOU) < 0.006, 'el solape medido (%.4f) no cuadra' % iou
    CB = B._by_slug(B.CHOSEN_SLUG)
    rd = FR.paths()
    return """
<h2>4 &middot; La Fluida, y por qu&eacute; no es &eacute;sta</h2>
<p class="sub">La <strong>Fluida</strong> fue la primera P: org&aacute;nica, con movimiento y aire.
Nunca se dibuj&oacute; en vectorial &mdash;su &uacute;nico original es un r&aacute;ster&mdash; y por
eso hac&iacute;a falta ponerla en limpio antes de poder compararla de verdad.</p>

<h3>Reproducida desde su r&aacute;ster</h3>
<p class="sub">Contorno subp&iacute;xel y c&uacute;bicas por m&iacute;nimos cuadrados con tolerancia
declarada. Solape medido: <span class="key">IoU %(iou)s</span>.</p>
<div class="row">
  <div class="cell"><div class="dark"><img src="%(raster)s" height="210"></div>
    <div class="lab">el original (lo &uacute;nico que hab&iacute;a)</div></div>
  <div class="cell"><div class="dark">%(fl)s</div><div class="lab">vectorizada</div></div>
</div>
<div class="legend" style="margin:6px 0 10px">
  <span><i class="dot" style="background:#25F48C"></i>coinciden &middot; %(pb)s&nbsp;%%</span>
  <span><i class="dot" style="background:#F43F5E"></i>s&oacute;lo el original &middot; %(pt)s&nbsp;%%</span>
  <span><i class="dot" style="background:#3882F6"></i>s&oacute;lo el vector &middot; %(pc)s&nbsp;%%</span>
</div>
<img src="%(ov)s" width="520">
<p class="sub" style="margin-top:12px">&#9888; <strong>Techo del original.</strong> Su borde trae
ruido de compresi&oacute;n y sus formas vienen con bultos &mdash;el ojo es un cuadrado redondeado, el
bajo del cuenco ondula&mdash;. Eso no es ruido filtrable: es el dibujo que hay. M&aacute;s limpio que
esto ser&iacute;a inventarse la Fluida.</p>

<h3>Y redibujada limpia, tom&aacute;ndola de inspiraci&oacute;n</h3>
<p class="sub">Otro encargo y otro m&eacute;todo, as&iacute; que otro fichero: su <strong>propio
modelo</strong> &mdash;cada pieza con su centro y su radio, la contraforma <strong>no</strong>
conc&eacute;ntrica con la cabeza, el ojo tampoco&mdash; y las piezas unidas con <strong>arcos
tangentes</strong> en vez de rectas. Ah&iacute; est&aacute; su movimiento: con una tangente
com&uacute;n el lomo sale a 47,9&deg; y la Fluida lleva 54,2 porque su borde <em>curva</em>.</p>
<div class="row">
  <div class="cell"><div class="dark">%(rd)s</div><div class="lab">Fluida redibujada</div></div>
  <div class="cell"><div class="dark">%(cb)s</div><div class="lab">Comp&aacute;s B+ &middot; la propuesta</div></div>
</div>

<h3>Las dos, comparadas</h3>
<table>
<tr><th>&nbsp;</th><th>Fluida (medida)</th><th>Comp&aacute;s B+</th></tr>
<tr><td>relaci&oacute;n ancho/alto</td><td class="n"><b>1,130</b></td><td class="n"><b>%(ratio).3f</b></td></tr>
<tr><td>cabeza</td><td class="n">r 76,3 &middot; RMS 4,2 (no es un c&iacute;rculo)</td><td class="n">r 84 &middot; exacto</td></tr>
<tr><td>espalda de la P</td><td class="n">54,2&deg;</td><td class="n">53,2&deg;</td></tr>
<tr><td>ojo</td><td class="n">r 14,8 &middot; <span class="warn">no conc&eacute;ntrico</span></td><td class="n">r 20 &middot; conc&eacute;ntrico</td></tr>
<tr><td>grosor del trazo</td><td class="n"><span class="warn">variable</span></td><td class="n">constante</td></tr>
<tr><td>enlace rueda / cuenco</td><td class="n">recta tangente</td><td class="n">acuerdo r 34</td></tr>
</table>
<p class="sub" style="margin-top:14px"><strong>Lo que las separa no es un remate, es la
proporci&oacute;n</strong>: la Fluida es mucho m&aacute;s ancha que alta (1,130) y el Comp&aacute;s B+
casi cuadrado (%(ratio).3f). Todo lo dem&aacute;s &mdash;anillos, ojo, remate&mdash; son grados. Eso
es un formato distinto, y de ah&iacute; que la decisi&oacute;n no sea &laquo;cu&aacute;l dibuja
mejor&raquo; sino <em>qu&eacute; proporci&oacute;n quiere la marca</em>.</p>
""" % dict(iou=('%.3f' % iou).replace('.', ','),
           pb=('%.1f' % (100 * pb)).replace('.', ','),
           pt=('%.1f' % (100 * pt)).replace('.', ','),
           pc=('%.1f' % (100 * pc)).replace('.', ','),
           raster=C.b64(os.path.abspath(C.RASTER)), ov=C.b64(img),
           fl=svg_of([d], FW, FH, 210, GREEN),
           rd=svg_of(rd, FR.W, FR.H, 190, GREEN),
           cb=variant_svg(CB, 190, GREEN),
           ratio=B.metrics(CB)['ratio'])


def mockups_block():
    """5 - Las maquetas: la elegida contra su alternativa, superficie a superficie."""
    A, Bx = B.CHOSEN_SLUG, 'ancestro'
    if A == Bx:
        Bx = 'punta'
    sa, sb = MK.surfaces(A), MK.surfaces(Bx)
    filas = []
    for (tit, nota, ia), (_t, _n, ib) in zip(sa, sb):
        filas.append("""
<h3>%(tit)s</h3>
<p class="sub">%(nota)s</p>
<div class="row">
  <div class="cell"><div class="dark"><img src="%(a)s" width="%(wa)d"></div>
    <div class="lab">%(sa)s &middot; la elegida</div></div>
  <div class="cell"><div class="dark"><img src="%(b)s" width="%(wb)d"></div>
    <div class="lab">%(sb)s</div></div>
</div>""" % dict(tit=tit, nota=nota, a=C.b64(ia), b=C.b64(ib),
                 wa=min(520, ia.width), wb=min(520, ib.width), sa=A, sb=Bx))

    pa, pb = MK.pieces_at(A), MK.pieces_at(Bx)
    tabla = ['<table><tr><th>la marca a&hellip;</th><th>%s</th><th>%s</th></tr>' % (A, Bx)]
    for (S, na), (_S, nb) in zip(pa, pb):
        cls = ' class="warn"'
        tabla.append('<tr><td>%d px de alto</td><td class="n">%s</td><td class="n">%s</td></tr>'
                     % (S,
                        ('%d' % na) if na == 3 else '<span%s>%d</span>' % (cls, na),
                        ('%d' % nb) if nb == 3 else '<span%s>%d</span>' % (cls, nb)))
    tabla.append('</table>')

    return """
<h2>5 &middot; Las maquetas &middot; d&oacute;nde vive la marca</h2>
<p class="sub">La elegida (<strong>%(A)s</strong>) contra la alternativa que sigue viva
(<strong>%(B)s</strong>, el ajuste puro al r&aacute;ster), en las superficies reales. Los iconos
peque&ntilde;os van <strong>rasterizados a su tama&ntilde;o real y ampliados sin suavizar</strong>:
a 48&nbsp;CSS&nbsp;px una pantalla densa pinta 96 o 144 p&iacute;xeles f&iacute;sicos y eso favorece
al dibujo con m&aacute;s detalle, que es justo el que de verdad se emborrona.</p>
%(filas)s

<h3>Cu&aacute;ntos trozos sobreviven</h3>
<p class="sub">Tres es el dibujo entero &mdash;cuerpo, rueda derecha y ojo&mdash;. <strong>Dos
significa que la rueda se ha fundido con el cuerpo</strong> y el canal ha desaparecido: la marca
sigue vi&eacute;ndose, pero deja de leerse como una P.</p>
%(tabla)s
<p class="sub" style="margin-top:12px">&#9888; <strong>Ah&iacute; abajo el canal mide ~1&nbsp;px, y
el resultado depende del rasterizador</strong>: la misma marca da dos o tres trozos seg&uacute;n la
cadena de render (supermuestreo contra reducci&oacute;n desde un r&aacute;ster grande), y Android usa
la suya. Lo que S&Iacute; aguanta el cambio de cadena es el <em>orden</em>: la elegida se rompe
<strong>un escal&oacute;n m&aacute;s tarde</strong> que la otra, porque tiene el trazo m&aacute;s
fino y el canal m&aacute;s ancho. Por debajo de ~24&nbsp;px la lectura est&aacute; al l&iacute;mite
en las dos.</p>
""" % dict(A=A, B=Bx, filas=''.join(filas), tabla=''.join(tabla))


def main():
    html = ("""<!doctype html>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Paparcar &middot; la marca</title>
<style>%s</style>
<div class="wrap">
<header>
  <h1>Paparcar &middot; la marca</h1>
  <p class="sub">La propuesta, c&oacute;mo se lleg&oacute; a ella y contra qu&eacute; se
  compar&oacute;. GENERADO por <code>docs/branding/master/dossier.py</code>: cada cifra de esta
  p&aacute;gina se mide al generarla, ninguna est&aacute; escrita en la plantilla.</p>
</header>
""" % CSS) + head_block() + '<div class="rule"></div>' + SURFACES \
        + '<div class="rule"></div>' + variants_block() \
        + '<div class="rule"></div>' + fluida_block()         + '<div class="rule"></div>' + mockups_block() + """
</div>
"""
    p = os.path.abspath(OUT)
    io.open(p, 'w', encoding='utf-8', newline='\n').write(html)
    print('  escrito  docs/branding/propuesta.html (%.0f KB)' % (len(html) / 1024.0))


if __name__ == '__main__':
    main()
